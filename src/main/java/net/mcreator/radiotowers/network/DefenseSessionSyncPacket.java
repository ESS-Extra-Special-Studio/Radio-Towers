package net.mcreator.radiotowers.network;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.integration.DefenseSessionClientMirror;


/**
 * Server -> Client: whether a RadioTowers defense wave session is active in this dimension (for NMS mixin on client).
 */
public class DefenseSessionSyncPacket implements CustomPacketPayload {
    public static final Type<DefenseSessionSyncPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RadiotowersMod.MODID, "defense_session_sync"));
    public static final StreamCodec<FriendlyByteBuf, DefenseSessionSyncPacket> STREAM_CODEC = StreamCodec.of((buf, msg) -> DefenseSessionSyncPacket.encode(msg, buf), DefenseSessionSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }



    private final Identifier dimensionId;
    private final boolean active;

    public DefenseSessionSyncPacket(Identifier dimensionId, boolean active) {
        this.dimensionId = dimensionId;
        this.active = active;
    }

    public DefenseSessionSyncPacket(FriendlyByteBuf buf) {
        this.dimensionId = buf.readIdentifier();
        this.active = buf.readBoolean();
    }

    public static void encode(DefenseSessionSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeIdentifier(msg.dimensionId);
        buf.writeBoolean(msg.active);
    }

    public static void handle(DefenseSessionSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, msg.dimensionId);
            DefenseSessionClientMirror.set(key, msg.active);
        });
    }
}
