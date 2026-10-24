package net.mcreator.radiotowers.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.SafeClientCalls;

/**
 * Server → Client: Dead Air unlock flags for Boss Mode / Endless Mode airdrop tabs.
 * Both false when Dead Air is absent or the player has not progressed.
 */
public class AirdropModeUnlockPacket implements CustomPacketPayload {
    public static final Type<AirdropModeUnlockPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(RadiotowersMod.MODID, "airdrop_mode_unlock"));
    public static final StreamCodec<FriendlyByteBuf, AirdropModeUnlockPacket> STREAM_CODEC =
        StreamCodec.of((buf, msg) -> AirdropModeUnlockPacket.encode(msg, buf), AirdropModeUnlockPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final boolean bossModeUnlocked;
    private final boolean endlessModeUnlocked;

    public AirdropModeUnlockPacket(boolean bossModeUnlocked, boolean endlessModeUnlocked) {
        this.bossModeUnlocked = bossModeUnlocked;
        this.endlessModeUnlocked = endlessModeUnlocked;
    }

    public AirdropModeUnlockPacket(FriendlyByteBuf buf) {
        this.bossModeUnlocked = buf.readBoolean();
        this.endlessModeUnlocked = buf.readBoolean();
    }

    public static void encode(AirdropModeUnlockPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.bossModeUnlocked);
        buf.writeBoolean(msg.endlessModeUnlocked);
    }

    public static void handle(AirdropModeUnlockPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            SafeClientCalls.applyAirdropModeUnlockPacket(msg.bossModeUnlocked, msg.endlessModeUnlocked));
    }

    public boolean isBossModeUnlocked() { return bossModeUnlocked; }
    public boolean isEndlessModeUnlocked() { return endlessModeUnlocked; }
}
