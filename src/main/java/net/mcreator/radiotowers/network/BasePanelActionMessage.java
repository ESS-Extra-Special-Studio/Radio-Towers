package net.mcreator.radiotowers.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;
import net.mcreator.radiotowers.RadiotowersMod;

import java.util.function.Supplier;

/**
 * Client → server: claim / unset / query player base for this radio panel (Dead Air soft-compat).
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class BasePanelActionMessage {
    public static final int SET = 0;
    public static final int UNSET = 1;
    public static final int QUERY = 2;

    private final BlockPos pos;
    private final int action;

    public BasePanelActionMessage(BlockPos pos, int action) {
        this.pos = pos;
        this.action = action;
    }

    public BasePanelActionMessage(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.action = buf.readVarInt();
    }

    public static void encode(BasePanelActionMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.action);
    }

    public static void handle(BasePanelActionMessage msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.getDirection().getReceptionSide().isServer()) return;
            ServerPlayer sender = ctx.getSender();
            if (sender == null || !ModList.get().isLoaded("dead_air")) return;
            try {
                Class<?> api = Class.forName("uk.co.extraspecialstudio.dead_air.api.DeadAirAPI");
                if (msg.action == SET) {
                    api.getMethod("setPlayerBase", ServerPlayer.class, BlockPos.class)
                        .invoke(null, sender, msg.pos);
                } else if (msg.action == UNSET) {
                    api.getMethod("unsetPlayerBase", ServerPlayer.class).invoke(null, sender);
                }
                Object info = api.getMethod("getPlayerBaseGuiInfo", ServerPlayer.class, BlockPos.class)
                    .invoke(null, sender, msg.pos);
                if (info instanceof Object[] arr && arr.length >= 2) {
                    String line = arr[0] instanceof String s ? s : String.valueOf(arr[0]);
                    boolean isThis = arr[1] instanceof Boolean b && b;
                    RadiotowersMod.PACKET_HANDLER.send(new BaseStatusPacket(line, isThis), PacketDistributor.PLAYER.with(sender));
                }
            } catch (Throwable t) {
                RadiotowersMod.LOGGER.error("Could not call Dead Air base API at {}: {}", msg.pos, t.getMessage(), t);
            }
        });
        ctx.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        RadiotowersMod.addNetworkMessage(BasePanelActionMessage.class, BasePanelActionMessage::encode,
            BasePanelActionMessage::new, BasePanelActionMessage::handle);
    }
}
