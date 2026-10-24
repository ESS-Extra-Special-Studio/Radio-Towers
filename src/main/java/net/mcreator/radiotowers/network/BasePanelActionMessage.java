package net.mcreator.radiotowers.network;

import net.minecraft.util.math.BlockPos;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;
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

    public BasePanelActionMessage(PacketBuffer buf) {
        this.pos = buf.readBlockPos();
        this.action = buf.readVarInt();
    }

    public static void encode(BasePanelActionMessage msg, PacketBuffer buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.action);
    }

    public static void handle(BasePanelActionMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (!ctx.getDirection().getReceptionSide().isServer()) return;
            ServerPlayerEntity sender = ctx.getSender();
            if (sender == null || !ModList.get().isLoaded("dead_air")) return;
            try {
                Class<?> api = Class.forName("uk.co.extraspecialstudio.dead_air.api.DeadAirAPI");
                if (msg.action == SET) {
                    api.getMethod("setPlayerBase", ServerPlayerEntity.class, BlockPos.class)
                        .invoke(null, sender, msg.pos);
                } else if (msg.action == UNSET) {
                    api.getMethod("unsetPlayerBase", ServerPlayerEntity.class).invoke(null, sender);
                }
                Object info = api.getMethod("getPlayerBaseGuiInfo", ServerPlayerEntity.class, BlockPos.class)
                    .invoke(null, sender, msg.pos);
                    Object[] arr = (info instanceof Object[]) ? (Object[]) info : null;
                if (arr != null && arr.length >= 2) {
                Object s$src = arr[0]; String s = (s$src instanceof String) ? (String) s$src : null;
                    String line = s != null ? s : String.valueOf(arr[0]);
                    Object b$src = arr[1]; Boolean b = (b$src instanceof Boolean) ? (Boolean) b$src : null;
                    boolean isThis = b != null && b;
                    RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> sender),
                        new BaseStatusPacket(line, isThis));
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
