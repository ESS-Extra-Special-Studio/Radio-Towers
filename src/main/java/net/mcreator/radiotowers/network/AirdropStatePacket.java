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
 * Server -> Client: current airdrop button state (wave in progress, cooldown end).
 */
public class AirdropStatePacket implements CustomPacketPayload {
    public static final Type<AirdropStatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RadiotowersMod.MODID, "airdrop_state"));
    public static final StreamCodec<FriendlyByteBuf, AirdropStatePacket> STREAM_CODEC = StreamCodec.of((buf, msg) -> AirdropStatePacket.encode(msg, buf), AirdropStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }



    private final boolean waveInProgress;
    private final long cooldownEndGameTime;

    public AirdropStatePacket(boolean waveInProgress, long cooldownEndGameTime) {
        this.waveInProgress = waveInProgress;
        this.cooldownEndGameTime = cooldownEndGameTime;
    }

    public AirdropStatePacket(FriendlyByteBuf buf) {
        this.waveInProgress = buf.readBoolean();
        this.cooldownEndGameTime = buf.readVarLong();
    }

    public static void encode(AirdropStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.waveInProgress);
        buf.writeVarLong(msg.cooldownEndGameTime);
    }

    public static void handle(AirdropStatePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (true) {
                SafeClientCalls.applyAirdropStatePacket(msg.waveInProgress, msg.cooldownEndGameTime);
            }
        });
    }

    public boolean isWaveInProgress() { return waveInProgress; }
    public long getCooldownEndGameTime() { return cooldownEndGameTime; }
}
