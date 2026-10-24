package net.mcreator.radiotowers.network;

import net.minecraft.util.math.BlockPos;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;

import net.mcreator.radiotowers.RadiotowersMod;

import java.util.function.Supplier;

/**
 * Sent when the player chooses a station and clicks "Turn on" in the panel settings GUI.
 * Server calls Dead Air's API to activate the panel at the given pos with the chosen station (if Dead Air is loaded).
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ActivatePanelWithStationMessage {

    private final BlockPos pos;
    private final String stationId;

    public ActivatePanelWithStationMessage(BlockPos pos, String stationId) {
        this.pos = pos;
        this.stationId = stationId != null ? stationId : "";
    }

    public ActivatePanelWithStationMessage(PacketBuffer buf) {
        this.pos = buf.readBlockPos();
        this.stationId = buf.readUtf(256);
    }

    public static void encode(ActivatePanelWithStationMessage msg, PacketBuffer buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.stationId);
    }

    public static void handle(ActivatePanelWithStationMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (!ctx.getDirection().getReceptionSide().isServer()) return;
            net.minecraft.entity.player.ServerPlayerEntity sender = ctx.getSender();
            if (sender == null) return;
            ServerWorld level = sender.getLevel();
            if (level == null) return;
            if (!ModList.get().isLoaded("dead_air") || msg.stationId == null || msg.stationId.isEmpty()) return;
            ResourceLocation id = msg.stationId != null && ResourceLocation.isValidResourceLocation(msg.stationId) ? new ResourceLocation(msg.stationId) : null;
            if (id == null) return;
            try {
                // Load via context CL so Dead Air's package resolves (RadioTowers module CL can miss sibling classes)
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl == null) cl = ActivatePanelWithStationMessage.class.getClassLoader();
                Class<?> api = Class.forName("uk.co.extraspecialstudio.dead_air.api.DeadAirAPI", true, cl);
                // Prefer 4-arg overload so the triggering player gets "New station discovered!" if applicable
                try {
                    java.lang.reflect.Method method = api.getMethod("activatePanelAt", ServerWorld.class, BlockPos.class, ResourceLocation.class, ServerPlayerEntity.class);
                    method.invoke(null, level, msg.pos, id, sender);
                } catch (NoSuchMethodException e) {
                    java.lang.reflect.Method method = api.getMethod("activatePanelAt", ServerWorld.class, BlockPos.class, ResourceLocation.class);
                    method.invoke(null, level, msg.pos, id);
                }
                java.lang.reflect.Method syncMethod = api.getMethod("syncKnownTowersToPlayer", ServerPlayerEntity.class);
                syncMethod.invoke(null, sender);
            } catch (Throwable t) {
                RadiotowersMod.LOGGER.error("Could not call Dead Air to activate panel at {} with station {}: {}", msg.pos, msg.stationId, t.getMessage(), t);
            }
        });
        ctx.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        RadiotowersMod.addNetworkMessage(ActivatePanelWithStationMessage.class, ActivatePanelWithStationMessage::encode, ActivatePanelWithStationMessage::new, ActivatePanelWithStationMessage::handle);
    }
}
