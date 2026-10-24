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
 * Server → Client: Dead Air unlock flags for Boss Mode / Endless Mode airdrop tabs.
 * Both false when Dead Air is absent or the player has not progressed.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class AirdropModeUnlockPacket {

    private final boolean bossModeUnlocked;
    private final boolean endlessModeUnlocked;

    public AirdropModeUnlockPacket(boolean bossModeUnlocked, boolean endlessModeUnlocked) {
        this.bossModeUnlocked = bossModeUnlocked;
        this.endlessModeUnlocked = endlessModeUnlocked;
    }

    public AirdropModeUnlockPacket(FriendlyByteBuf buf) {
        this.bossModeUnlocked = buf.readBoolean();
        this.endlessModeUnlocked = buf.readBoolean();
    }

    public static void encode(AirdropModeUnlockPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.bossModeUnlocked);
        buf.writeBoolean(msg.endlessModeUnlocked);
    }

    public static void handle(AirdropModeUnlockPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide().isClient()) {
                SafeClientCalls.applyAirdropModeUnlockPacket(msg.bossModeUnlocked, msg.endlessModeUnlocked);
            }
        });
        ctx.setPacketHandled(true);
    }

    public boolean isBossModeUnlocked() { return bossModeUnlocked; }
    public boolean isEndlessModeUnlocked() { return endlessModeUnlocked; }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        RadiotowersMod.addNetworkMessage(AirdropModeUnlockPacket.class, AirdropModeUnlockPacket::encode,
            AirdropModeUnlockPacket::new, AirdropModeUnlockPacket::handle);
    }
}
