package net.mcreator.radiotowers.network;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.integration.DefenseSessionClientMirror;

import java.util.function.Supplier;

/**
 * Server -> Client: whether a RadioTowers defense wave session is active in this dimension (for NMS mixin on client).
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class DefenseSessionSyncPacket {

    private final ResourceLocation dimensionId;
    private final boolean active;

    public DefenseSessionSyncPacket(ResourceLocation dimensionId, boolean active) {
        this.dimensionId = dimensionId;
        this.active = active;
    }

    public DefenseSessionSyncPacket(FriendlyByteBuf buf) {
        this.dimensionId = buf.readResourceLocation();
        this.active = buf.readBoolean();
    }

    public static void encode(DefenseSessionSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.dimensionId);
        buf.writeBoolean(msg.active);
    }

    public static void handle(DefenseSessionSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (!ctx.getDirection().getReceptionSide().isClient()) return;
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, msg.dimensionId);
            DefenseSessionClientMirror.set(key, msg.active);
        });
        ctx.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        RadiotowersMod.addNetworkMessage(DefenseSessionSyncPacket.class, DefenseSessionSyncPacket::encode, DefenseSessionSyncPacket::new, DefenseSessionSyncPacket::handle);
    }
}
