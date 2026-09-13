package net.mcreator.radiotowers.network;
import net.mcreator.radiotowers.network.RadiotowersNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.integration.AirdropCooldown;
import net.mcreator.radiotowers.integration.EslWaveIntegration;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;


/**
 * Client -> Server: request current airdrop state for a specific tower (when opening Call Airdrop screen).
 * Server responds with AirdropStatePacket. Wave-in-progress is only true if this player has a pending airdrop at this panel.
 */
public class RequestAirdropStatePacket implements CustomPacketPayload {
    public static final Type<RequestAirdropStatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RadiotowersMod.MODID, "request_airdrop_state"));
    public static final StreamCodec<FriendlyByteBuf, RequestAirdropStatePacket> STREAM_CODEC = StreamCodec.of((buf, msg) -> RequestAirdropStatePacket.encode(msg, buf), RequestAirdropStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }



    private final BlockPos panelPos;

    public RequestAirdropStatePacket(BlockPos panelPos) {
        this.panelPos = panelPos != null ? panelPos.immutable() : BlockPos.ZERO;
    }

    public RequestAirdropStatePacket(FriendlyByteBuf buf) {
        this.panelPos = buf.readBlockPos();
    }

    public static void encode(RequestAirdropStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.panelPos);
    }

    public static void handle(RequestAirdropStatePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer)) return;
            ServerPlayer player = (ctx.player() instanceof net.minecraft.server.level.ServerPlayer _sp ? _sp : null);
            if (player == null || player.serverLevel() == null) return;
            var serverLevel = player.serverLevel();
            long cooldownEnd = AirdropCooldown.getCooldownEndGameTime(serverLevel, player.getUUID());
            boolean waveBackend = ZombieWavesAPILoader.isZombieWavesAPILoaded()
                    || EslWaveIntegration.isEslWaveAvailable();
            if (!waveBackend) {
                RadiotowersNetwork.sendToPlayer(player,
                    new AirdropStatePacket(false, cooldownEnd));
                return;
            }
            // Only show "called in" at this tower if this exact panel has a pending airdrop for this player
            var pending = PendingAirdropStorage.getPendingForPanel(serverLevel, msg.panelPos);
            boolean waveInProgress = pending != null && (player.getUUID().equals(pending.playerWhoStarted)
                || pending.effectiveMembers().contains(player.getUUID()));
            RadiotowersNetwork.sendToPlayer(player,
                new AirdropStatePacket(waveInProgress, cooldownEnd));
        });
    }
}
