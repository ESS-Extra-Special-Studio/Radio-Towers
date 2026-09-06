package net.mcreator.radiotowers.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.SafeClientCalls;


/**
 * Server -> Client: current wave progress (wave number, total waves, zombies remaining, time left).
 * Sent periodically while a wave is active so the Call Airdrop screen can show status.
 * Also carries a flag so the client keeps NMS suppression in sync even if {@link DefenseSessionSyncPacket} is missed.
 */
public class WaveStatePacket implements CustomPacketPayload {
    public static final Type<WaveStatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RadiotowersMod.MODID, "wave_state"));
    public static final StreamCodec<FriendlyByteBuf, WaveStatePacket> STREAM_CODEC = StreamCodec.of((buf, msg) -> WaveStatePacket.encode(msg, buf), WaveStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }



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

    public static void handle(WaveStatePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (true) {
                SafeClientCalls.applyWaveStatePacket(msg.currentWave, msg.totalWaves, msg.zombiesRemaining, msg.secondsRemaining, msg.nmsSuppressActive);
            }
        });
    }
}
