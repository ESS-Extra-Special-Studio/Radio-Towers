package net.mcreator.radiotowers.client.lobby;

import net.mcreator.radiotowers.client.gui.AirdropLobbyScreen;
import net.mcreator.radiotowers.client.gui.CallAirdropScreen;
import net.mcreator.radiotowers.network.lobby.LobbyPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

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

    public record OrderSnapshot(int totalDifficulty, List<String> itemIds, List<Integer> quantities) {
        public OrderSnapshot {
            itemIds = itemIds != null ? List.copyOf(itemIds) : List.of();
            quantities = quantities != null ? List.copyOf(quantities) : List.of();
        }
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
        OrderSnapshot order = pendingOrder != null ? pendingOrder : new OrderSnapshot(0, List.of(), List.of());
        BlockPos pos = panelPos != null ? panelPos : pendingPanelPos;
        mc.setScreen(new AirdropLobbyScreen(parent, pos, lobbyId, order));
    }

    public static void applySync(LobbyPackets.SyncLobby sync) {
        lastSync = sync;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen instanceof AirdropLobbyScreen screen) {
            screen.onSync(sync);
        }
    }

    public static void applyCountdown(LobbyPackets.Countdown countdown) {
        countdownSeconds = countdown.secondsRemaining();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen instanceof AirdropLobbyScreen screen) {
            screen.onCountdown(countdown.secondsRemaining());
        }
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
