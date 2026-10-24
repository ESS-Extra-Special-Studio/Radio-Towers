package net.mcreator.radiotowers.network.lobby;

import net.minecraft.util.math.BlockPos;
import net.minecraft.network.PacketBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ESN lobby packet payloads for RadioTowers (mod id {@code radiotowers}, protocol {@code 1}).
 * Keep fields simple: lobby/player UUIDs, strings, ints, block pos, order lists.
 */
public final class LobbyPackets {
    private LobbyPackets() {}

    public static class Open {
        private final BlockPos panelPos;
        public Open(BlockPos panelPos) {
            this.panelPos = panelPos;
        }
        public BlockPos panelPos() { return this.panelPos; }

        public static void encode(Open msg, PacketBuffer buf) {
            buf.writeBlockPos(msg.panelPos);
        }

        public static Open decode(PacketBuffer buf) {
            return new Open(buf.readBlockPos());
        }
    
    }

    public static class Invite {
        private final UUID lobbyId;
        private final String targetName;
        public Invite(UUID lobbyId, String targetName) {
            this.lobbyId = lobbyId;
            this.targetName = targetName;
        }
        public UUID lobbyId() { return this.lobbyId; }
        public String targetName() { return this.targetName; }

        public static void encode(Invite msg, PacketBuffer buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeUtf(msg.targetName == null ? "" : msg.targetName, 64);
        }

        public static Invite decode(PacketBuffer buf) {
            return new Invite(buf.readUUID(), buf.readUtf(64));
        }
    
    }

    public static class Accept {
        private final UUID lobbyId;
        public Accept(UUID lobbyId) {
            this.lobbyId = lobbyId;
        }
        public UUID lobbyId() { return this.lobbyId; }

        public static void encode(Accept msg, PacketBuffer buf) {
            buf.writeUUID(msg.lobbyId);
        }

        public static Accept decode(PacketBuffer buf) {
            return new Accept(buf.readUUID());
        }
    
    }

    public static class Decline {
        private final UUID lobbyId;
        public Decline(UUID lobbyId) {
            this.lobbyId = lobbyId;
        }
        public UUID lobbyId() { return this.lobbyId; }

        public static void encode(Decline msg, PacketBuffer buf) {
            buf.writeUUID(msg.lobbyId);
        }

        public static Decline decode(PacketBuffer buf) {
            return new Decline(buf.readUUID());
        }
    
    }

    public static class Ready {
        private final UUID lobbyId;
        private final boolean ready;
        public Ready(UUID lobbyId, boolean ready) {
            this.lobbyId = lobbyId;
            this.ready = ready;
        }
        public UUID lobbyId() { return this.lobbyId; }
        public boolean ready() { return this.ready; }

        public static void encode(Ready msg, PacketBuffer buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeBoolean(msg.ready);
        }

        public static Ready decode(PacketBuffer buf) {
            return new Ready(buf.readUUID(), buf.readBoolean());
        }
    
    }

    public static class Kick {
        private final UUID lobbyId;
        private final UUID targetUuid;
        public Kick(UUID lobbyId, UUID targetUuid) {
            this.lobbyId = lobbyId;
            this.targetUuid = targetUuid;
        }
        public UUID lobbyId() { return this.lobbyId; }
        public UUID targetUuid() { return this.targetUuid; }

        public static void encode(Kick msg, PacketBuffer buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeUUID(msg.targetUuid);
        }

        public static Kick decode(PacketBuffer buf) {
            return new Kick(buf.readUUID(), buf.readUUID());
        }
    
    }

    public static class CancelInvite {
        private final UUID lobbyId;
        private final UUID targetUuid;
        public CancelInvite(UUID lobbyId, UUID targetUuid) {
            this.lobbyId = lobbyId;
            this.targetUuid = targetUuid;
        }
        public UUID lobbyId() { return this.lobbyId; }
        public UUID targetUuid() { return this.targetUuid; }

        public static void encode(CancelInvite msg, PacketBuffer buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeUUID(msg.targetUuid);
        }

        public static CancelInvite decode(PacketBuffer buf) {
            return new CancelInvite(buf.readUUID(), buf.readUUID());
        }
    
    }

    public static class Leave {
        private final UUID lobbyId;
        public Leave(UUID lobbyId) {
            this.lobbyId = lobbyId;
        }
        public UUID lobbyId() { return this.lobbyId; }

        public static void encode(Leave msg, PacketBuffer buf) {
            buf.writeUUID(msg.lobbyId);
        }

        public static Leave decode(PacketBuffer buf) {
            return new Leave(buf.readUUID());
        }
    
    }

    /** Host Go: same order payload shape as CallAirdropMessage. */
    public static class Go {
        private final UUID lobbyId;
        private final BlockPos panelPos;
        private final int totalDifficulty;
        private final List<String> itemIds;
        private final List<Integer> quantities;
        public Go(UUID lobbyId, BlockPos panelPos, int totalDifficulty, List<String> itemIds, List<Integer> quantities) {
            this.lobbyId = lobbyId;
            this.panelPos = panelPos;
            this.totalDifficulty = totalDifficulty;
            this.itemIds = itemIds;
            this.quantities = quantities;
        }
        public UUID lobbyId() { return this.lobbyId; }
        public BlockPos panelPos() { return this.panelPos; }
        public int totalDifficulty() { return this.totalDifficulty; }
        public List<String> itemIds() { return this.itemIds; }
        public List<Integer> quantities() { return this.quantities; }

        public static void encode(Go msg, PacketBuffer buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeBlockPos(msg.panelPos);
            buf.writeVarInt(msg.totalDifficulty);
            List<String> ids = msg.itemIds != null ? msg.itemIds : java.util.Collections.emptyList();
            buf.writeVarInt(ids.size());
            for (String id : ids) buf.writeUtf(id, 256);
            List<Integer> qty = msg.quantities != null ? msg.quantities : java.util.Collections.emptyList();
            for (int i = 0; i < ids.size(); i++) {
                buf.writeVarInt(i < qty.size() ? qty.get(i) : 0);
            }
        }

        public static Go decode(PacketBuffer buf) {
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

    public static class MemberSnapshot {
        private final UUID uuid;
        private final String name;
        private final String status;
        public MemberSnapshot(UUID uuid, String name, String status) {
            this.uuid = uuid;
            this.name = name;
            this.status = status;
        }
        public UUID uuid() { return this.uuid; }
        public String name() { return this.name; }
        public String status() { return this.status; }
    }

    public static class SyncLobby {
        private final UUID lobbyId;
        private final UUID hostUuid;
        private final BlockPos panelPos;
        private final String state;
        private final String blockReason;
        private final boolean canStart;
        private final List<MemberSnapshot> members;
        public SyncLobby(UUID lobbyId, UUID hostUuid, BlockPos panelPos, String state, String blockReason, boolean canStart, List<MemberSnapshot> members) {
            this.lobbyId = lobbyId;
            this.hostUuid = hostUuid;
            this.panelPos = panelPos;
            this.state = state;
            this.blockReason = blockReason;
            this.canStart = canStart;
            this.members = members;
        }
        public UUID lobbyId() { return this.lobbyId; }
        public UUID hostUuid() { return this.hostUuid; }
        public BlockPos panelPos() { return this.panelPos; }
        public String state() { return this.state; }
        public String blockReason() { return this.blockReason; }
        public boolean canStart() { return this.canStart; }
        public List<MemberSnapshot> members() { return this.members; }

        public static void encode(SyncLobby msg, PacketBuffer buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeUUID(msg.hostUuid);
            buf.writeBlockPos(msg.panelPos);
            buf.writeUtf(msg.state == null ? "" : msg.state, 32);
            buf.writeUtf(msg.blockReason == null ? "" : msg.blockReason, 128);
            buf.writeBoolean(msg.canStart);
            List<MemberSnapshot> list = msg.members != null ? msg.members : java.util.Collections.emptyList();
            buf.writeVarInt(list.size());
            for (MemberSnapshot m : list) {
                buf.writeUUID(m.uuid());
                buf.writeUtf(m.name() == null ? "" : m.name(), 64);
                buf.writeUtf(m.status() == null ? "" : m.status(), 16);
            }
        }

        public static SyncLobby decode(PacketBuffer buf) {
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

    public static class Countdown {
        private final UUID lobbyId;
        private final int secondsRemaining;
        public Countdown(UUID lobbyId, int secondsRemaining) {
            this.lobbyId = lobbyId;
            this.secondsRemaining = secondsRemaining;
        }
        public UUID lobbyId() { return this.lobbyId; }
        public int secondsRemaining() { return this.secondsRemaining; }

        public static void encode(Countdown msg, PacketBuffer buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeVarInt(msg.secondsRemaining);
        }

        public static Countdown decode(PacketBuffer buf) {
            return new Countdown(buf.readUUID(), buf.readVarInt());
        }
    
    }

    /** Client opens lobby UI after server creates/syncs. */
    public static class OpenScreen {
        private final UUID lobbyId;
        private final BlockPos panelPos;
        public OpenScreen(UUID lobbyId, BlockPos panelPos) {
            this.lobbyId = lobbyId;
            this.panelPos = panelPos;
        }
        public UUID lobbyId() { return this.lobbyId; }
        public BlockPos panelPos() { return this.panelPos; }

        public static void encode(OpenScreen msg, PacketBuffer buf) {
            buf.writeUUID(msg.lobbyId);
            buf.writeBlockPos(msg.panelPos);
        }

        public static OpenScreen decode(PacketBuffer buf) {
            return new OpenScreen(buf.readUUID(), buf.readBlockPos());
        }
    
    }

    /** Lobby ended (Go finished, host left, cancel) — clients clear cache and close the screen. */
    public static class Closed {
        private final UUID lobbyId;
        public Closed(UUID lobbyId) {
            this.lobbyId = lobbyId;
        }
        public UUID lobbyId() { return this.lobbyId; }

        public static void encode(Closed msg, PacketBuffer buf) {
            buf.writeUUID(msg.lobbyId);
        }

        public static Closed decode(PacketBuffer buf) {
            return new Closed(buf.readUUID());
        }
    
    }
}
