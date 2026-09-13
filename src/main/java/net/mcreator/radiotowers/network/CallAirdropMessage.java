package net.mcreator.radiotowers.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.integration.AirdropCooldown;
import net.mcreator.radiotowers.integration.AirdropDifficultyTier;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;
import net.mcreator.radiotowers.procedures.RadioPanelOnBlockRightClickedProcedure;
import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.init.RadiotowersModSounds;

import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sent when the player clicks START in the Call Airdrop GUI.
 * totalDifficulty = sum of selected items' difficulty points (catalog total); this value is used directly
 * as the zombie wave difficulty when the Zombie Waves API is present.
 * With Zombie Waves API: start wave at tower ground, store pending airdrop; plane spawns when wave completes.
 * Without API: spawn plane at panel immediately.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class CallAirdropMessage {

    private final BlockPos pos;
    /** Airdrop catalog total (selected items' difficulty points). Used directly as zombie wave difficulty. */
    private final int totalDifficulty;
    private final List<String> itemIds;
    private final List<Integer> quantities;

    public CallAirdropMessage(BlockPos pos, int totalDifficulty, List<String> itemIds, List<Integer> quantities) {
        this.pos = pos;
        this.totalDifficulty = totalDifficulty;
        this.itemIds = itemIds != null ? new ArrayList<>(itemIds) : new ArrayList<>();
        this.quantities = quantities != null ? new ArrayList<>(quantities) : new ArrayList<>();
    }

    public CallAirdropMessage(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.totalDifficulty = buf.readVarInt();
        int n = buf.readVarInt();
        this.itemIds = new ArrayList<>();
        for (int i = 0; i < n; i++) itemIds.add(buf.readUtf(256));
        this.quantities = new ArrayList<>();
        for (int i = 0; i < n; i++) quantities.add(buf.readVarInt());
    }

    public static void encode(CallAirdropMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.totalDifficulty);
        buf.writeVarInt(msg.itemIds.size());
        for (String id : msg.itemIds) buf.writeUtf(id, 256);
        for (int q : msg.quantities) buf.writeVarInt(q);
    }

    public static void handle(CallAirdropMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (!ctx.getDirection().getReceptionSide().isServer()) return;
            var sender = ctx.getSender();
            if (sender == null || sender.serverLevel() == null) return;
            if (net.mcreator.radiotowers.lobby.AirdropLobbyService.hasGuestsBlockingSoloStart(sender.getUUID())) {
                sender.sendSystemMessage(Component.literal(
                    "[RadioTowers] Guests are in your airdrop lobby — use Lobby → Go (everyone must be ready)."));
                return;
            }
            boolean standardAirdrop = (msg.totalDifficulty == 0 && (msg.itemIds == null || msg.itemIds.isEmpty()));
            if (standardAirdrop && !AirdropConfig.ENABLE_STANDARD_AIRDROP.get()) {
                sender.sendSystemMessage(Component.literal("[RadioTowers] Standard airdrops are disabled in config."));
                return;
            }
            if (!standardAirdrop && !AirdropConfig.ENABLE_CATALOG_AIRDROP.get()) {
                sender.sendSystemMessage(Component.literal("[RadioTowers] Catalog airdrops are disabled in config."));
                return;
            }
            if (!standardAirdrop && !AirdropDifficultyTier.canStart(msg.totalDifficulty)) return; // require 10-point increments unless standard airdrop (loot table)
            ServerLevel level = sender.serverLevel();
            // Wave integration priority: ESL > Berezka > immediate
            boolean hasWaveBackend = !standardAirdrop
                    && net.mcreator.radiotowers.config.AirdropConfig.shouldUseZombieWavesForAirdrop()
                    && (net.mcreator.radiotowers.integration.EslWaveIntegration.isEslWaveAvailable()
                        || net.mcreator.radiotowers.integration.ZombieWavesAPILoader.isZombieWavesAPILoaded());
            if (hasWaveBackend) {
                if (PendingAirdropStorage.hasPendingForPlayer(level, sender.getUUID())) {
                    // Wave already running: do NOT change its loot order (keep what was selected when this wave started)
                    RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> sender),
                        new AirdropStatePacket(true, AirdropCooldown.getCooldownEndGameTime(level, sender.getUUID())));
                    return;
                }
                if (AirdropCooldown.isOnCooldown(level, sender.getUUID())) {
                    RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> sender),
                        new AirdropStatePacket(false, AirdropCooldown.getCooldownEndGameTime(level, sender.getUUID())));
                    return;
                }
                AirdropDifficultyTier.Tier tier = AirdropDifficultyTier.getTier(msg.totalDifficulty);
                if (tier == null) return;
                BlockPos panelPos = msg.pos.immutable();
                GroundResult groundResult = getGroundPositionForWave(level, panelPos);
                BlockPos waveStartPos = groundResult.pos;
                // Before starting a new wave, hard-purge any leftover wave residue from a previous run/session
                // so "ghost/frozen" stragglers can't poison the next wave's counts/state.
                ZombieWavesAPILoader.purgeWaveEntityResidue(level, waveStartPos);
                PendingAirdropStorage.store(level, waveStartPos, panelPos, msg.itemIds, msg.quantities, msg.totalDifficulty, sender.getUUID(), tier.numWaves, tier.numWaves, tier.apiDifficulty);
                // Priority: ESL waves first, then Berezka fallback
                boolean waveStarted = false;
                if (net.mcreator.radiotowers.integration.EslWaveIntegration.isEslWaveAvailable()) {
                    waveStarted = net.mcreator.radiotowers.integration.EslWaveIntegration.startWaveAt(
                        level, waveStartPos, panelPos, tier.apiDifficulty, tier.numWaves,
                        AirdropDifficultyTier.getZombiesPerWaveForTier(tier.apiDifficulty), sender);
                    if (waveStarted) RadiotowersMod.LOGGER.info("[Airdrop] Using ESL Wave API for defense waves");
                }
                if (!waveStarted && ZombieWavesAPILoader.isZombieWavesAPILoaded()) {
                    waveStarted = ZombieWavesAPILoader.startWaveAt(level, waveStartPos, tier.apiDifficulty, sender);
                    if (waveStarted) RadiotowersMod.LOGGER.info("[Airdrop] Using Berezka legacy for defense waves");
                }
                if (!waveStarted) {
                    PendingAirdropStorage.removePending(level, waveStartPos);
                    RadioPanelOnBlockRightClickedProcedure.execute(level, msg.pos.getX(), msg.pos.getY(), msg.pos.getZ(), msg.itemIds, msg.quantities, msg.totalDifficulty);
                } else {
                    // Alarm at the tower panel the player used (same X/Z as wave anchor; Y is the actual block on the structure).
                    level.playSound(null, panelPos, RadiotowersModSounds.SIREN.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
                }
                RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> sender),
                    new AirdropStatePacket(true, AirdropCooldown.getCooldownEndGameTime(level, sender.getUUID())));
            } else {
                if (AirdropCooldown.isOnCooldown(level, sender.getUUID())) {
                    RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> sender),
                        new AirdropStatePacket(false, AirdropCooldown.getCooldownEndGameTime(level, sender.getUUID())));
                    return;
                }
                RadioPanelOnBlockRightClickedProcedure.execute(level, msg.pos.getX(), msg.pos.getY(), msg.pos.getZ(), msg.itemIds, msg.quantities, msg.totalDifficulty);
                AirdropCooldown.setCooldownAfterStandardAirdrop(level, sender.getUUID());
                RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> sender),
                    new AirdropStatePacket(false, AirdropCooldown.getCooldownEndGameTime(level, sender.getUUID())));
            }
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Cardinal offsets (blocks) from panel XZ to sample terrain height. A narrow tower can make the center column's
     * heightmap read the roof; sampling around the footprint still targets this tower's locale.
     */
    private static final int SURFACE_SAMPLE_OFFSET = 8;

    static final class GroundResult {
        final BlockPos pos;
        final String source;
        GroundResult(BlockPos pos, String source) { this.pos = pos; this.source = source; }
    }

    /** Outdoor surface air Y at this column (same notion as vanilla spawn / getHeight). */
    private static int surfaceAirYFromHeightmap(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return Math.max(level.getMinBuildHeight(), Math.min(h, level.getMaxBuildHeight() - 1));
    }

    /**
     * Outdoor surface for the wave anchor (API + pending storage). Uses the real heightmap only — never scans down
     * from an artificial Y cap (that matched cave ceilings under hills when surface was above Y=80).
     * Median of center + cardinals at {@link #SURFACE_SAMPLE_OFFSET} avoids one column reading only the tower roof
     * while staying near the structure.
     */
    private static GroundResult getGroundPositionForWave(ServerLevel level, BlockPos pos) {
        int cx = pos.getX();
        int cz = pos.getZ();
        int o = SURFACE_SAMPLE_OFFSET;
        int[] samples = new int[] {
            surfaceAirYFromHeightmap(level, cx, cz),
            surfaceAirYFromHeightmap(level, cx + o, cz),
            surfaceAirYFromHeightmap(level, cx - o, cz),
            surfaceAirYFromHeightmap(level, cx, cz + o),
            surfaceAirYFromHeightmap(level, cx, cz - o)
        };
        Arrays.sort(samples);
        int medianSurfaceAir = samples[samples.length / 2];
        return new GroundResult(new BlockPos(cx, medianSurfaceAir, cz), "heightmap");
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        RadiotowersMod.addNetworkMessage(CallAirdropMessage.class, CallAirdropMessage::encode, CallAirdropMessage::new, CallAirdropMessage::handle);
    }
}
