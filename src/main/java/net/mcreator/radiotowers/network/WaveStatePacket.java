package net.mcreator.radiotowers.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.SafeClientCalls;

import java.util.function.Supplier;

/**
 * Server -> Client: current wave progress (wave number, total waves, zombies remaining, time left).
 * Sent periodically while a wave is active so the Call Airdrop screen can show status.
 * Also carries a flag so the client keeps NMS suppression in sync even if {@link DefenseSessionSyncPacket} is missed.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class WaveStatePacket {

    private final int currentWave;
    private final int totalWaves;
    private final int zombiesRemaining;
    private final int secondsRemaining;
    /** True while this HUD update represents an active RadioTowers defense wave (suppress NMS TacZ on client). */
    private final boolean nmsSuppressActive;

    public WaveStatePacket(int currentWave, int totalWaves, int zombiesRemaining, int secondsRemaining, boolean nmsSuppressActive) {
        this.currentWave = currentWave;
        this.totalWaves = totalWaves;
        this.zombiesRemaining = zombiesRemaining;
        this.secondsRemaining = secondsRemaining;
        this.nmsSuppressActive = nmsSuppressActive;
    }

    public WaveStatePacket(FriendlyByteBuf buf) {
        this.currentWave = buf.readVarInt();
        this.totalWaves = buf.readVarInt();
        this.zombiesRemaining = buf.readVarInt();
        this.secondsRemaining = buf.readVarInt();
        this.nmsSuppressActive = buf.readBoolean();
    }

    public static void encode(WaveStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.currentWave);
        buf.writeVarInt(msg.totalWaves);
        buf.writeVarInt(msg.zombiesRemaining);
        buf.writeVarInt(msg.secondsRemaining);
        buf.writeBoolean(msg.nmsSuppressActive);
    }

    public static void handle(WaveStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide().isClient()) {
                SafeClientCalls.applyWaveStatePacket(msg.currentWave, msg.totalWaves, msg.zombiesRemaining, msg.secondsRemaining, msg.nmsSuppressActive);
            }
        });
        ctx.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        RadiotowersMod.addNetworkMessage(WaveStatePacket.class, WaveStatePacket::encode, WaveStatePacket::new, WaveStatePacket::handle);
    }
}
