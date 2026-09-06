package net.mcreator.radiotowers.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerLevel;

import net.mcreator.radiotowers.RadiotowersMod;


/**
 * Sent when the player clicks "Turn on radio station" in the panel GUI.
 * Server calls Dead Air's API to activate the panel at the given pos (if Dead Air is loaded).
 */
public class ActivatePanelForDeadAirMessage implements CustomPacketPayload {
    public static final Type<ActivatePanelForDeadAirMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RadiotowersMod.MODID, "activate_panel_deadair"));
    public static final StreamCodec<FriendlyByteBuf, ActivatePanelForDeadAirMessage> STREAM_CODEC = StreamCodec.of((buf, msg) -> ActivatePanelForDeadAirMessage.encode(msg, buf), ActivatePanelForDeadAirMessage::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }



    private final BlockPos pos;

    public ActivatePanelForDeadAirMessage(BlockPos pos) {
        this.pos = pos;
    }

    public ActivatePanelForDeadAirMessage(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public static void encode(ActivatePanelForDeadAirMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static void handle(ActivatePanelForDeadAirMessage msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer)) return;
            var sender = (ctx.player() instanceof net.minecraft.server.level.ServerPlayer _sp ? _sp : null);
            if (sender == null) return;
            ServerLevel level = sender.serverLevel();
            if (level == null) return;
            if (!ModList.get().isLoaded("dead_air")) return;
            try {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl == null) cl = ActivatePanelForDeadAirMessage.class.getClassLoader();
                Class<?> api = Class.forName("uk.co.extraspecialstudio.dead_air.api.DeadAirAPI", true, cl);
                var method = api.getMethod("activatePanelAt", ServerLevel.class, BlockPos.class);
                method.invoke(null, level, msg.pos);
            } catch (Throwable t) {
                RadiotowersMod.LOGGER.error("Could not call Dead Air to activate panel at {}: {}", msg.pos, t.getMessage(), t);
            }
        });
    }
}
