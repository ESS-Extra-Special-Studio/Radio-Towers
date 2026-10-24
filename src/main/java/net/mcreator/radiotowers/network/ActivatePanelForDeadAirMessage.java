package net.mcreator.radiotowers.network;

import net.minecraft.util.math.BlockPos;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.minecraft.world.server.ServerWorld;

import net.mcreator.radiotowers.RadiotowersMod;

import java.util.function.Supplier;

/**
 * Sent when the player clicks "Turn on radio station" in the panel GUI.
 * Server calls Dead Air's API to activate the panel at the given pos (if Dead Air is loaded).
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ActivatePanelForDeadAirMessage {

    private final BlockPos pos;

    public ActivatePanelForDeadAirMessage(BlockPos pos) {
        this.pos = pos;
    }

    public ActivatePanelForDeadAirMessage(PacketBuffer buf) {
        this.pos = buf.readBlockPos();
    }

    public static void encode(ActivatePanelForDeadAirMessage msg, PacketBuffer buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static void handle(ActivatePanelForDeadAirMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (!ctx.getDirection().getReceptionSide().isServer()) return;
            net.minecraft.entity.player.ServerPlayerEntity sender = ctx.getSender();
            if (sender == null) return;
            ServerWorld level = sender.getLevel();
            if (level == null) return;
            if (!ModList.get().isLoaded("dead_air")) return;
            try {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl == null) cl = ActivatePanelForDeadAirMessage.class.getClassLoader();
                Class<?> api = Class.forName("uk.co.extraspecialstudio.dead_air.api.DeadAirAPI", true, cl);
                java.lang.reflect.Method method = api.getMethod("activatePanelAt", ServerWorld.class, BlockPos.class);
                method.invoke(null, level, msg.pos);
            } catch (Throwable t) {
                RadiotowersMod.LOGGER.error("Could not call Dead Air to activate panel at {}: {}", msg.pos, t.getMessage(), t);
            }
        });
        ctx.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        RadiotowersMod.addNetworkMessage(ActivatePanelForDeadAirMessage.class, ActivatePanelForDeadAirMessage::encode, ActivatePanelForDeadAirMessage::new, ActivatePanelForDeadAirMessage::handle);
    }
}
