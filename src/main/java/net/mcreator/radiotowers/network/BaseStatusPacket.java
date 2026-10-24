package net.mcreator.radiotowers.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkEvent;
import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.client.gui.CallAirdropScreen;

import java.util.function.Supplier;

/** Server → client: player base status line for the Base tab. */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class BaseStatusPacket {
    private final String statusLine;
    private final boolean isThisPanel;

    public BaseStatusPacket(String statusLine, boolean isThisPanel) {
        this.statusLine = statusLine != null ? statusLine : "";
        this.isThisPanel = isThisPanel;
    }

    public BaseStatusPacket(FriendlyByteBuf buf) {
        this.statusLine = buf.readUtf(512);
        this.isThisPanel = buf.readBoolean();
    }

    public static void encode(BaseStatusPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.statusLine, 512);
        buf.writeBoolean(msg.isThisPanel);
    }

    public static void handle(BaseStatusPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> CallAirdropScreen.applyBaseStatus(msg.statusLine, msg.isThisPanel));
        ctx.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        RadiotowersMod.addNetworkMessage(BaseStatusPacket.class, BaseStatusPacket::encode,
            BaseStatusPacket::new, BaseStatusPacket::handle);
    }
}
