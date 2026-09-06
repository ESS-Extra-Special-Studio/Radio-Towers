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
 * Sent when the player clicks "Turn off" in the panel settings GUI.
 * Server calls Dead Air's API to deactivate the panel at the given pos (if Dead Air is loaded).
 */
public class DeactivatePanelMessage implements CustomPacketPayload {
    public static final Type<DeactivatePanelMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RadiotowersMod.MODID, "deactivate_panel"));
    public static final StreamCodec<FriendlyByteBuf, DeactivatePanelMessage> STREAM_CODEC = StreamCodec.of((buf, msg) -> DeactivatePanelMessage.encode(msg, buf), DeactivatePanelMessage::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }



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

    public static void handle(DeactivatePanelMessage msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer)) return;
            var sender = (ctx.player() instanceof net.minecraft.server.level.ServerPlayer _sp ? _sp : null);
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
    }
}
