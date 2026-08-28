package net.mcreator.radiotowers.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.integration.AirdropCooldown;
import net.mcreator.radiotowers.integration.EslWaveIntegration;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;

import java.util.function.Supplier;

/**
 * Client -> Server: request current airdrop state for a specific tower (when opening Call Airdrop screen).
 * Server responds with AirdropStatePacket. Wave-in-progress is only true if this player has a pending airdrop at this panel.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class RequestAirdropStatePacket {

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

    public static void handle(RequestAirdropStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (!ctx.getDirection().getReceptionSide().isServer()) return;
            ServerPlayer player = ctx.getSender();
            if (player == null || player.serverLevel() == null) return;
            var serverLevel = player.serverLevel();
            long cooldownEnd = AirdropCooldown.getCooldownEndGameTime(serverLevel, player.getUUID());
            boolean waveBackend = ZombieWavesAPILoader.isZombieWavesAPILoaded()
                    || EslWaveIntegration.isEslWaveAvailable();
            if (!waveBackend) {
                RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player),
                    new AirdropStatePacket(false, cooldownEnd));
                return;
            }
            // Only show "called in" at this tower if this exact panel has a pending airdrop for this player
            var pending = PendingAirdropStorage.getPendingForPanel(serverLevel, msg.panelPos);
            boolean waveInProgress = pending != null && player.getUUID().equals(pending.playerWhoStarted);
            RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player),
                new AirdropStatePacket(waveInProgress, cooldownEnd));
        });
        ctx.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        RadiotowersMod.addNetworkMessage(RequestAirdropStatePacket.class, RequestAirdropStatePacket::encode, buf -> new RequestAirdropStatePacket(buf), RequestAirdropStatePacket::handle);
    }
}
