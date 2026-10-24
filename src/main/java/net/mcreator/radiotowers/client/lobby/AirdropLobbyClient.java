package net.mcreator.radiotowers.client.lobby;

import net.mcreator.radiotowers.client.gui.AirdropLobbyScreen;
import net.mcreator.radiotowers.client.gui.CallAirdropScreen;
import net.mcreator.radiotowers.network.lobby.LobbyPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client-side lobby sync cache + screen open helpers. */
public final class AirdropLobbyClient {
    private static LobbyPackets.SyncLobby lastSync;
    private static int countdownSeconds;
    @Nullable
    private static CallAirdropScreen pendingParent;
    @Nullable
    private static BlockPos pendingPanelPos;
    @Nullable
    private static OrderSnapshot pendingOrder;

    public static class OrderSnapshot {
        private final int totalDifficulty;
        private final List<String> itemIds;
        private final List<Integer> quantities;
        public OrderSnapshot(int totalDifficulty, List<String> itemIds, List<Integer> quantities) {
            itemIds = itemIds != null ? new java.util.ArrayList<>(itemIds) : java.util.Collections.emptyList();
            quantities = quantities != null ? new java.util.ArrayList<>(quantities) : java.util.Collections.emptyList();
            this.totalDifficulty = totalDifficulty;
            this.itemIds = itemIds;
            this.quantities = quantities;
        }
        public int totalDifficulty() { return this.totalDifficulty; }
        public List<String> itemIds() { return this.itemIds; }
        public List<Integer> quantities() { return this.quantities; }
    }

    private AirdropLobbyClient() {}

    public static void prepareOpen(CallAirdropScreen parent, BlockPos panelPos, OrderSnapshot order) {
        pendingParent = parent;
        pendingPanelPos = panelPos;
        pendingOrder = order;
    }

    public static void openScreen(UUID lobbyId, BlockPos panelPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        CallAirdropScreen parent = pendingParent;
        OrderSnapshot order = pendingOrder != null ? pendingOrder : new OrderSnapshot(0, java.util.Collections.emptyList(), java.util.Collections.emptyList());
        BlockPos pos = panelPos != null ? panelPos : pendingPanelPos;
        mc.setScreen(new AirdropLobbyScreen(parent, pos, lobbyId, order));
    }

    public static void applySync(LobbyPackets.SyncLobby sync) {
        lastSync = sync;
        Minecraft mc = Minecraft.getInstance();
        AirdropLobbyScreen screen = (mc.screen instanceof AirdropLobbyScreen) ? (AirdropLobbyScreen) mc.screen : null;
        if (mc != null && screen != null) {
            screen.onSync(sync);
        }
    }

    public static void applyCountdown(LobbyPackets.Countdown countdown) {
        countdownSeconds = countdown.secondsRemaining();
        Minecraft mc = Minecraft.getInstance();
        AirdropLobbyScreen screen = (mc.screen instanceof AirdropLobbyScreen) ? (AirdropLobbyScreen) mc.screen : null;
        if (mc != null && screen != null) {
            screen.onCountdown(countdown.secondsRemaining());
        }
    }

    /** Lobby ended on the server — clear cache and close the lobby screen. */
    public static void applyClosed(LobbyPackets.Closed closed) {
        UUID closedId = closed != null ? closed.lobbyId() : null;
        Minecraft mc = Minecraft.getInstance();
        AirdropLobbyScreen screen = (mc.screen instanceof AirdropLobbyScreen) ? (AirdropLobbyScreen) mc.screen : null;
        if (mc != null && screen != null) {
            if (closedId == null || closedId.equals(screen.lobbyId())) {
                mc.setScreen(screen.getParent());
            }
        }
        clear();
    }

    @Nullable
    public static LobbyPackets.SyncLobby lastSync() {
        return lastSync;
    }

    public static int countdownSeconds() {
        return countdownSeconds;
    }

    public static void clear() {
        lastSync = null;
        countdownSeconds = 0;
        pendingParent = null;
        pendingPanelPos = null;
        pendingOrder = null;
    }
}
