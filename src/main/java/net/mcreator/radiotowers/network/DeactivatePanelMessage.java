package net.mcreator.radiotowers.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.minecraft.server.level.ServerLevel;

import net.mcreator.radiotowers.RadiotowersMod;

import java.util.function.Supplier;

/**
 * Sent when the player clicks "Turn off" in the panel settings GUI.
 * Server calls Dead Air's API to deactivate the panel at the given pos (if Dead Air is loaded).
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class DeactivatePanelMessage {

    private final BlockPos pos;

    public DeactivatePanelMessage(BlockPos pos) {
        this.pos = pos;
    }

    public DeactivatePanelMessage(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public static void encode(DeactivatePanelMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static void handle(DeactivatePanelMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (!ctx.getDirection().getReceptionSide().isServer()) return;
            var sender = ctx.getSender();
            if (sender == null) return;
            ServerLevel level = sender.serverLevel();
            if (level == null) return;
            if (!ModList.get().isLoaded("dead_air")) return;
            try {
                Class<?> api = Class.forName("uk.co.extraspecialstudio.dead_air.api.DeadAirAPI");
                var method = api.getMethod("deactivatePanelAt", ServerLevel.class, BlockPos.class);
                method.invoke(null, level, msg.pos);
                var syncMethod = api.getMethod("syncKnownTowersToPlayer", net.minecraft.server.level.ServerPlayer.class);
                syncMethod.invoke(null, sender);
            } catch (Throwable t) {
                RadiotowersMod.LOGGER.error("Could not call Dead Air to deactivate panel at {}: {}", msg.pos, t.getMessage(), t);
            }
        });
        ctx.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        RadiotowersMod.addNetworkMessage(DeactivatePanelMessage.class, DeactivatePanelMessage::encode, DeactivatePanelMessage::new, DeactivatePanelMessage::handle);
    }
}
