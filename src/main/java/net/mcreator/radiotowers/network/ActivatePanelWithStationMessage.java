package net.mcreator.radiotowers.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.mcreator.radiotowers.RadiotowersMod;


/**
 * Sent when the player chooses a station and clicks "Turn on" in the panel settings GUI.
 * Server calls Dead Air's API to activate the panel at the given pos with the chosen station (if Dead Air is loaded).
 */
public class ActivatePanelWithStationMessage implements CustomPacketPayload {
    public static final Type<ActivatePanelWithStationMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RadiotowersMod.MODID, "activate_panel_station"));
    public static final StreamCodec<FriendlyByteBuf, ActivatePanelWithStationMessage> STREAM_CODEC = StreamCodec.of((buf, msg) -> ActivatePanelWithStationMessage.encode(msg, buf), ActivatePanelWithStationMessage::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }



    private final BlockPos pos;
    private final String stationId;

    public ActivatePanelWithStationMessage(BlockPos pos, String stationId) {
        this.pos = pos;
        this.stationId = stationId != null ? stationId : "";
    }

    public ActivatePanelWithStationMessage(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.stationId = buf.readUtf(256);
    }

    public static void encode(ActivatePanelWithStationMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.stationId);
    }

    public static void handle(ActivatePanelWithStationMessage msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer)) return;
            var sender = (ctx.player() instanceof net.minecraft.server.level.ServerPlayer _sp ? _sp : null);
            if (sender == null) return;
            ServerLevel level = sender.serverLevel();
            if (level == null) return;
            if (!ModList.get().isLoaded("dead_air") || msg.stationId == null || msg.stationId.isEmpty()) return;
            ResourceLocation id = ResourceLocation.tryParse(msg.stationId);
            if (id == null) return;
            try {
                // Load via context CL so Dead Air's package resolves (RadioTowers module CL can miss sibling classes)
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl == null) cl = ActivatePanelWithStationMessage.class.getClassLoader();
                Class<?> api = Class.forName("uk.co.extraspecialstudio.dead_air.api.DeadAirAPI", true, cl);
                // Prefer 4-arg overload so the triggering player gets "New station discovered!" if applicable
                try {
                    var method = api.getMethod("activatePanelAt", ServerLevel.class, BlockPos.class, ResourceLocation.class, ServerPlayer.class);
                    method.invoke(null, level, msg.pos, id, sender);
                } catch (NoSuchMethodException e) {
                    var method = api.getMethod("activatePanelAt", ServerLevel.class, BlockPos.class, ResourceLocation.class);
                    method.invoke(null, level, msg.pos, id);
                }
                var syncMethod = api.getMethod("syncKnownTowersToPlayer", ServerPlayer.class);
                syncMethod.invoke(null, sender);
            } catch (Throwable t) {
                RadiotowersMod.LOGGER.error("Could not call Dead Air to activate panel at {} with station {}: {}", msg.pos, msg.stationId, t.getMessage(), t);
            }
        });
    }
}
