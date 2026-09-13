package net.mcreator.radiotowers.network.lobby;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ESN lobby packet payloads for RadioTowers (mod id {@code radiotowers}, protocol {@code 1}).
 * Keep fields simple: lobby/player UUIDs, strings, ints, block pos, order lists.
 */
public final class LobbyPackets {
    private LobbyPackets() {}

    public record Open(BlockPos panelPos) {
        public static void encode(Open msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.panelPos);
        }

        public static Open decode(FriendlyByteBuf buf) {
            return new Open(buf.readBlockPos());
        }
    }

    public record Invite(UUID lobbyId, String targetName) {
        public static void encode(Invite msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeUtf(msg.targetName == null ? "" : msg.targetName, 64);
        }

        public static Invite decode(FriendlyByteBuf buf) {
            return new Invite(buf.readUUID(), buf.readUtf(64));
        }
    }

    public record Accept(UUID lobbyId) {
        public static void encode(Accept msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.lobbyId);
        }

        public static Accept decode(FriendlyByteBuf buf) {
            return new Accept(buf.readUUID());
        }
    }

    public record Decline(UUID lobbyId) {
        public static void encode(Decline msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.lobbyId);
        }

        public static Decline decode(FriendlyByteBuf buf) {
            return new Decline(buf.readUUID());
        }
    }

    public record Ready(UUID lobbyId, boolean ready) {
        public static void encode(Ready msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeBoolean(msg.ready);
        }

        public static Ready decode(FriendlyByteBuf buf) {
            return new Ready(buf.readUUID(), buf.readBoolean());
        }
    }

    public record Kick(UUID lobbyId, UUID targetUuid) {
        public static void encode(Kick msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeUUID(msg.targetUuid);
        }

        public static Kick decode(FriendlyByteBuf buf) {
            return new Kick(buf.readUUID(), buf.readUUID());
        }
    }

    public record CancelInvite(UUID lobbyId, UUID targetUuid) {
        public static void encode(CancelInvite msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeUUID(msg.targetUuid);
        }

        public static CancelInvite decode(FriendlyByteBuf buf) {
            return new CancelInvite(buf.readUUID(), buf.readUUID());
        }
    }

    public record Leave(UUID lobbyId) {
        public static void encode(Leave msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.lobbyId);
        }

        public static Leave decode(FriendlyByteBuf buf) {
            return new Leave(buf.readUUID());
        }
    }

    /** Host Go: same order payload shape as CallAirdropMessage. */
    public record Go(UUID lobbyId, BlockPos panelPos, int totalDifficulty, List<String> itemIds, List<Integer> quantities) {
        public static void encode(Go msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeBlockPos(msg.panelPos);
            buf.writeVarInt(msg.totalDifficulty);
            List<String> ids = msg.itemIds != null ? msg.itemIds : List.of();
            buf.writeVarInt(ids.size());
            for (String id : ids) buf.writeUtf(id, 256);
            List<Integer> qty = msg.quantities != null ? msg.quantities : List.of();
            for (int i = 0; i < ids.size(); i++) {
                buf.writeVarInt(i < qty.size() ? qty.get(i) : 0);
            }
        }

        public static Go decode(FriendlyByteBuf buf) {
            UUID lobbyId = buf.readUUID();
            BlockPos pos = buf.readBlockPos();
            int total = buf.readVarInt();
            int n = buf.readVarInt();
            List<String> ids = new ArrayList<>(n);
            for (int i = 0; i < n; i++) ids.add(buf.readUtf(256));
            List<Integer> qty = new ArrayList<>(n);
            for (int i = 0; i < n; i++) qty.add(buf.readVarInt());
            return new Go(lobbyId, pos, total, ids, qty);
        }
    }

    public record MemberSnapshot(UUID uuid, String name, String status) {}

    public record SyncLobby(
        UUID lobbyId,
        UUID hostUuid,
        BlockPos panelPos,
        String state,
        String blockReason,
        boolean canStart,
        List<MemberSnapshot> members
    ) {
        public static void encode(SyncLobby msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeUUID(msg.hostUuid);
            buf.writeBlockPos(msg.panelPos);
            buf.writeUtf(msg.state == null ? "" : msg.state, 32);
            buf.writeUtf(msg.blockReason == null ? "" : msg.blockReason, 128);
            buf.writeBoolean(msg.canStart);
            List<MemberSnapshot> list = msg.members != null ? msg.members : List.of();
            buf.writeVarInt(list.size());
            for (MemberSnapshot m : list) {
                buf.writeUUID(m.uuid());
                buf.writeUtf(m.name() == null ? "" : m.name(), 64);
                buf.writeUtf(m.status() == null ? "" : m.status(), 16);
            }
        }

        public static SyncLobby decode(FriendlyByteBuf buf) {
            UUID lobbyId = buf.readUUID();
            UUID hostUuid = buf.readUUID();
            BlockPos panelPos = buf.readBlockPos();
            String state = buf.readUtf(32);
            String blockReason = buf.readUtf(128);
            boolean canStart = buf.readBoolean();
            int n = buf.readVarInt();
            List<MemberSnapshot> members = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                members.add(new MemberSnapshot(buf.readUUID(), buf.readUtf(64), buf.readUtf(16)));
            }
            return new SyncLobby(lobbyId, hostUuid, panelPos, state, blockReason, canStart, members);
        }
    }

    public record Countdown(UUID lobbyId, int secondsRemaining) {
        public static void encode(Countdown msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeVarInt(msg.secondsRemaining);
        }

        public static Countdown decode(FriendlyByteBuf buf) {
            return new Countdown(buf.readUUID(), buf.readVarInt());
        }
    }

    /** Client opens lobby UI after server creates/syncs. */
    public record OpenScreen(UUID lobbyId, BlockPos panelPos) {
        public static void encode(OpenScreen msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeBlockPos(msg.panelPos);
        }

        public static OpenScreen decode(FriendlyByteBuf buf) {
            return new OpenScreen(buf.readUUID(), buf.readBlockPos());
        }
    }
}
