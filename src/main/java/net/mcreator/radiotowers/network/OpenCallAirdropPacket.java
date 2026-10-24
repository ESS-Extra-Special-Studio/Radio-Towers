package net.mcreator.radiotowers.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.network.CustomPayloadEvent;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.SafeClientCalls;

import java.util.function.Supplier;

/**
 * Server → Client: force-open {@code CallAirdropScreen} at a panel pos (debug / OP).
 * Bypasses the "already has a screen open" guard on block right-click.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class OpenCallAirdropPacket {

    private final BlockPos panelPos;

    public OpenCallAirdropPacket(BlockPos panelPos) {
        this.panelPos = panelPos != null ? panelPos.immutable() : BlockPos.ZERO;
    }

    public OpenCallAirdropPacket(FriendlyByteBuf buf) {
        this.panelPos = buf.readBlockPos();
    }

    public static void encode(OpenCallAirdropPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.panelPos);
    }

    public static void handle(OpenCallAirdropPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide().isClient()) {
                SafeClientCalls.forceOpenCallAirdrop(msg.panelPos);
            }
        });
        ctx.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        RadiotowersMod.addNetworkMessage(OpenCallAirdropPacket.class, OpenCallAirdropPacket::encode,
            OpenCallAirdropPacket::new, OpenCallAirdropPacket::handle);
    }
}
