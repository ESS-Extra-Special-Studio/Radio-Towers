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
 * Server -> Client: current airdrop button state (wave in progress, cooldown end).
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class AirdropStatePacket {

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

    public static void handle(AirdropStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide().isClient()) {
                SafeClientCalls.applyAirdropStatePacket(msg.waveInProgress, msg.cooldownEndGameTime);
            }
        });
        ctx.setPacketHandled(true);
    }

    public boolean isWaveInProgress() { return waveInProgress; }
    public long getCooldownEndGameTime() { return cooldownEndGameTime; }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        RadiotowersMod.addNetworkMessage(AirdropStatePacket.class, AirdropStatePacket::encode, AirdropStatePacket::new, AirdropStatePacket::handle);
    }
}
