package net.mcreator.radiotowers.client.gui;

import net.mcreator.radiotowers.client.lobby.AirdropLobbyClient;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.network.lobby.AirdropLobbyNetwork;
import net.mcreator.radiotowers.network.lobby.LobbyPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import uk.co.extraspecialstudio.extraspecial.esc.theme.EscFrameStyle;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscInsets;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscOwnsBackNav;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscPanel;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscScreen;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscSearchBox;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscSimpleScrollList;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscText;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Optional multiplayer airdrop lobby. Host invites from the online list or a name;
 * members ready or leave. Go stays blocked, with a banner reason, until the party can start.
 */
public class AirdropLobbyScreen extends EscScreen implements EscOwnsBackNav {

    private static final int PAD = 8;
    private static final int COL_HEADER = 16;
    private static final int GAP = 8;

    private final Screen parent;
    private final BlockPos panelPos;
    private final UUID lobbyId;
    private final AirdropLobbyClient.OrderSnapshot order;

    private LobbyPackets.SyncLobby sync;
    private int countdownSeconds;
    private EscSearchBox inviteBox;
    private Button goButton;
    private Button readyButton;
    private EscSimpleScrollList<MemberRow> memberList;
    private EscSimpleScrollList<OnlineRow> onlineList;
    private EscRect headerRect;
    private EscRect statusRect;
    private EscRect inviteBarRect;
    private EscRect onlineCard;
    private EscRect partyCard;
    private EscRect memberViewport;
    private EscRect onlineViewport;
    private EscRect footerBounds;
    private String nameFilter = "";
    private int onlineRefreshTicks;

    private record MemberRow(UUID uuid, String name, String status, boolean host, String action) {}
    private record OnlineRow(UUID uuid, String name) {}
    private record Chip(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public AirdropLobbyScreen(@Nullable Screen parent, BlockPos panelPos, UUID lobbyId, AirdropLobbyClient.OrderSnapshot order) {
        super(Component.translatable("screen.radiotowers.airdrop_lobby.title"));
        this.parent = parent;
        this.panelPos = panelPos;
        this.lobbyId = lobbyId;
        this.order = order != null ? order : new AirdropLobbyClient.OrderSnapshot(0, List.of(), List.of());
        this.sync = AirdropLobbyClient.lastSync();
        this.countdownSeconds = AirdropLobbyClient.countdownSeconds();
    }

    public void onSync(LobbyPackets.SyncLobby sync) {
        this.sync = sync;
        rebuild();
    }

    public void onCountdown(int seconds) {
        this.countdownSeconds = seconds;
    }

    public void onLobbySynced() {
        rebuild();
    }

    private void rebuild() {
        this.clearWidgets();
        buildLayout();
    }

    @Override
    protected void buildLayout() {
        EscRect content = contentRect();
        footerBounds = EscPanel.footer(content, style);
        boolean host = isHost();

        int innerX = content.x() + PAD;
        int innerW = Math.max(40, content.width() - PAD * 2);
        int y = content.y() + PAD;
        headerRect = new EscRect(innerX, y, innerW, 14);
        y = headerRect.bottom() + 6;
        statusRect = new EscRect(innerX, y, innerW, 16);
        y = statusRect.bottom() + GAP;

        inviteBarRect = null;
        if (host) {
            int boxH = uk.co.extraspecialstudio.extraspecial.esc.ui.EscSearchBox.recommendedHeight(font);
            int btnH = 20;
            int btnW = 72;
            int barH = Math.max(boxH, btnH) + 6;
            inviteBarRect = new EscRect(innerX, y, innerW, barH);
            int boxW = Math.max(80, inviteBarRect.width() - btnW - 14);
            int boxY = inviteBarRect.y() + (barH - boxH) / 2;
            int btnY = inviteBarRect.y() + (barH - btnH) / 2;
            inviteBox = addSearchBox(
                new EscRect(inviteBarRect.x() + 4, boxY, boxW, boxH),
                Component.translatable("screen.radiotowers.airdrop_lobby.name_hint"));
            inviteBox.setMaxLength(32);
            if (!nameFilter.isEmpty()) inviteBox.setValue(nameFilter);
            addButton(
                Component.translatable("screen.radiotowers.airdrop_lobby.invite"),
                new EscRect(inviteBarRect.right() - btnW - 4, btnY, btnW, btnH),
                b -> onInvite());
            y = inviteBarRect.bottom() + GAP;
        }

        int colH = Math.max(48, footerBounds.y() - GAP - y);
        if (host && innerW >= 280) {
            int half = (innerW - GAP) / 2;
            onlineCard = new EscRect(innerX, y, half, colH);
            partyCard = new EscRect(onlineCard.right() + GAP, y, innerW - half - GAP, colH);
            onlineViewport = cardBody(onlineCard);
            memberViewport = cardBody(partyCard);
            onlineList = new EscSimpleScrollList<>(style, this::renderOnlineRow);
            onlineList.setViewport(onlineViewport);
        } else {
            onlineCard = null;
            onlineList = null;
            partyCard = new EscRect(innerX, y, innerW, colH);
            memberViewport = cardBody(partyCard);
        }

        memberList = new EscSimpleScrollList<>(style, this::renderMemberRow);
        memberList.setViewport(memberViewport);

        int by = footerBounds.y() + Math.max(0, (footerBounds.height() - 20) / 2);
        addButton(Component.translatable("screen.radiotowers.airdrop_lobby.back"),
            new EscRect(footerBounds.x() + PAD, by, 64, 20), b -> onBack());
        addButton(Component.translatable("screen.radiotowers.airdrop_lobby.leave"),
            new EscRect(footerBounds.x() + PAD + 70, by, 64, 20), b -> onLeave());
        EscRect primary = new EscRect(footerBounds.right() - PAD - 96, by, 96, 20);
        if (host) {
            goButton = addButton(Component.translatable("screen.radiotowers.airdrop_lobby.go"), primary, b -> onGo());
            readyButton = null;
        } else {
            goButton = null;
            readyButton = addButton(Component.translatable("screen.radiotowers.airdrop_lobby.ready"), primary, b -> onReady());
        }

        refreshMembers();
        refreshOnlinePlayers();
    }

    private static EscRect cardBody(EscRect card) {
        return new EscRect(card.x() + 4, card.y() + COL_HEADER + 3, Math.max(8, card.width() - 12), Math.max(8, card.height() - COL_HEADER - 7));
    }

    private boolean isHost() {
        return sync != null && minecraft != null && minecraft.player != null
            && minecraft.player.getUUID().equals(sync.hostUuid());
    }

    private void refreshMembers() {
        List<MemberRow> rows = new ArrayList<>();
        boolean selfReady = false;
        if (sync != null && sync.members() != null) {
            boolean host = isHost();
            UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
            for (LobbyPackets.MemberSnapshot m : sync.members()) {
                boolean hostRow = m.uuid().equals(sync.hostUuid());
                String action = "";
                if (host && !hostRow) {
                    action = "INVITED".equals(m.status()) ? "CANCEL" : "KICK";
                }
                if (self != null && self.equals(m.uuid()) && "READY".equals(m.status())) {
                    selfReady = true;
                }
                rows.add(new MemberRow(m.uuid(), m.name() == null ? "" : m.name(), m.status() == null ? "" : m.status(), hostRow, action));
            }
            if (goButton != null) {
                goButton.active = sync.canStart() && countdownSeconds <= 0;
            }
        }
        if (readyButton != null) {
            readyButton.active = sync != null && !selfReady && countdownSeconds <= 0;
        }
        if (memberList != null) memberList.replaceItems(rows);
    }

    private void refreshOnlinePlayers() {
        if (onlineList == null) return;
        List<OnlineRow> rows = new ArrayList<>();
        if (minecraft != null && minecraft.getConnection() != null && minecraft.player != null) {
            UUID self = minecraft.player.getUUID();
            Set<UUID> inLobby = new HashSet<>();
            if (sync != null && sync.members() != null) {
                for (LobbyPackets.MemberSnapshot m : sync.members()) inLobby.add(m.uuid());
            }
            String q = nameFilter == null ? "" : nameFilter.trim().toLowerCase(Locale.ROOT);
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                if (info == null || info.getProfile() == null) continue;
                UUID id = info.getProfile().getId();
                String name = info.getProfile().getName();
                if (id == null || name == null || name.isBlank()) continue;
                if (id.equals(self) || inLobby.contains(id)) continue;
                if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q)) continue;
                rows.add(new OnlineRow(id, name));
            }
            rows.sort(Comparator.comparing(OnlineRow::name, String.CASE_INSENSITIVE_ORDER));
        }
        onlineList.replaceItems(rows);
    }

    private void renderMemberRow(GuiGraphics g, MemberRow row, int index, int left, int top, int width, int height,
                                 int mouseX, int mouseY, boolean hovered, float partialTick) {
        paintRow(g, left, top, width, height, hovered, index);
        String badge = row.host ? tr("screen.radiotowers.airdrop_lobby.host") : statusLabel(row.status);
        int badgeRgb = row.host || "READY".equals(row.status) ? rgb(style.accentColor()) : "INVITED".equals(row.status) ? rgb(style.secondaryColor()) : rgb(style.mutedColor());
        Chip badgeChip = placeChip(badge, left + width - 4, top, height);
        if (!row.action.isEmpty()) {
            Chip action = placeChip(actionLabel(row.action), badgeChip.x - 4, top, height);
            boolean hot = hovered && action.contains(mouseX, mouseY);
            drawChip(g, action, actionLabel(row.action), hot ? rgb(style.focusBorderColor()) : rgb(style.textColorBody()), hot);
            badgeChip = placeChip(badge, action.x - 4, top, height);
        }
        drawChip(g, badgeChip, badge, badgeRgb, false);
        int nameMax = Math.max(8, badgeChip.x - left - 10);
        EscText.drawScrollingString(g, font, row.name, left + 6, top + (height - font.lineHeight) / 2, nameMax, titleRgb());
    }

    private void renderOnlineRow(GuiGraphics g, OnlineRow row, int index, int left, int top, int width, int height,
                                 int mouseX, int mouseY, boolean hovered, float partialTick) {
        paintRow(g, left, top, width, height, hovered, index);
        String invite = tr("screen.radiotowers.airdrop_lobby.invite");
        Chip chip = placeChip(invite, left + width - 4, top, height);
        drawChip(g, chip, invite, rgb(style.accentColor()), hovered && chip.contains(mouseX, mouseY));
        int nameMax = Math.max(8, chip.x - left - 10);
        EscText.drawScrollingString(g, font, row.name, left + 6, top + (height - font.lineHeight) / 2, nameMax, titleRgb());
    }

    private void paintRow(GuiGraphics g, int left, int top, int width, int height, boolean hovered, int index) {
        int fill = hovered ? argb(style.accentColor(), 0x33) : ((index & 1) == 1 ? 0x22000000 : 0);
        if (fill != 0) g.fill(left, top + 1, left + width, top + height - 1, fill);
    }

    private Chip placeChip(String label, int right, int rowTop, int rowHeight) {
        int w = EscText.width(font, label, EscFonts.DEFAULT) + 10;
        int h = Math.min(rowHeight - 4, font.lineHeight + 4);
        int x = right - w;
        int y = rowTop + (rowHeight - h) / 2;
        return new Chip(x, y, w, h);
    }

    private void drawChip(GuiGraphics g, Chip chip, String label, int rgb, boolean hot) {
        g.fill(chip.x, chip.y, chip.x + chip.w, chip.y + chip.h, argb(rgb, hot ? 0x66 : 0x28));
        EscPanel.border(g, new EscRect(chip.x, chip.y, chip.w, chip.h), 0xFF000000 | (rgb & 0xFFFFFF), 1);
        EscText.drawString(g, font, label, chip.x + 5, chip.y + (chip.h - font.lineHeight) / 2,
            0xFF000000 | (rgb & 0xFFFFFF), false, EscFonts.DEFAULT);
    }

    private void onBack() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private void onInvite() {
        if (!AirdropLobbyNetwork.isAvailable()) return;
        String name = inviteBox != null ? inviteBox.getValue() : "";
        AirdropLobbyNetwork.channel().sendToServer(new LobbyPackets.Invite(lobbyId, name));
    }

    private void inviteByName(String name) {
        if (!AirdropLobbyNetwork.isAvailable() || name == null || name.isBlank()) return;
        if (inviteBox != null) inviteBox.setValue(name);
        nameFilter = name;
        AirdropLobbyNetwork.channel().sendToServer(new LobbyPackets.Invite(lobbyId, name));
    }

    private void onReady() {
        if (!AirdropLobbyNetwork.isAvailable()) return;
        AirdropLobbyNetwork.channel().sendToServer(new LobbyPackets.Ready(lobbyId, true));
    }

    private void onGo() {
        if (!AirdropLobbyNetwork.isAvailable()) return;
        AirdropLobbyNetwork.channel().sendToServer(new LobbyPackets.Go(
            lobbyId, panelPos, order.totalDifficulty(), order.itemIds(), order.quantities()));
    }

    private void onLeave() {
        if (!AirdropLobbyNetwork.isAvailable()) return;
        AirdropLobbyNetwork.channel().sendToServer(new LobbyPackets.Leave(lobbyId));
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void tick() {
        super.tick();
        if (inviteBox != null) {
            String next = inviteBox.getValue() == null ? "" : inviteBox.getValue();
            if (!next.equals(nameFilter)) {
                nameFilter = next;
                refreshOnlinePlayers();
            }
        }
        if (onlineList != null && ++onlineRefreshTicks >= 20) {
            onlineRefreshTicks = 0;
            refreshOnlinePlayers();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        EscRect content = contentRect();
        EscPanel.renderPanel(guiGraphics, content, style);
        drawHeader(guiGraphics);
        drawStatusBanner(guiGraphics);
        if (inviteBarRect != null) {
            EscPanel.fill(guiGraphics, inviteBarRect, EscPanel.withOpacity(0xFF000000, 0.22f));
            EscPanel.renderFrame(guiGraphics, inviteBarRect, style.panelBorderColor(), 1, EscFrameStyle.SQUARE);
        }
        if (onlineCard != null) {
            int n = onlineList == null ? 0 : onlineList.items().size();
            drawCard(guiGraphics, onlineCard, tr("screen.radiotowers.airdrop_lobby.online"), Integer.toString(n));
        }
        if (partyCard != null) {
            int n = sync == null || sync.members() == null ? 0 : sync.members().size();
            drawCard(guiGraphics, partyCard, tr("screen.radiotowers.airdrop_lobby.party"), Integer.toString(n));
        }
        if (onlineList != null) onlineList.render(guiGraphics, mouseX, mouseY, partialTick);
        if (memberList != null) memberList.render(guiGraphics, mouseX, mouseY, partialTick);
        drawEmpty(guiGraphics, onlineViewport, onlineList, onlineEmpty());
        drawEmpty(guiGraphics, memberViewport, memberList, tr("screen.radiotowers.airdrop_lobby.empty_party"));
        if (onlineList != null) drawScrollbar(guiGraphics, onlineList, onlineViewport);
        if (memberList != null) drawScrollbar(guiGraphics, memberList, memberViewport);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        markPrimary(guiGraphics);
    }

    private void drawHeader(GuiGraphics g) {
        if (headerRect == null) return;
        String title = tr("screen.radiotowers.airdrop_lobby.title");
        int y = headerRect.y() + (headerRect.height() - font.lineHeight) / 2;
        g.fill(headerRect.x(), headerRect.y() + 2, headerRect.x() + 2, headerRect.bottom() - 2, opaque(style.accentColor()));
        EscText.drawString(g, font, title, headerRect.x() + 8, y, titleRgb(), false, EscFonts.DEFAULT);
        String count = partyCount(sync == null || sync.members() == null ? 0 : sync.members().size());
        int cw = EscText.width(font, count, EscFonts.DEFAULT);
        EscText.drawString(g, font, count, headerRect.right() - cw, y, opaque(style.mutedColor()), false, EscFonts.DEFAULT);
        int rule = argb(style.accentColor(), 0xAA);
        g.fill(headerRect.x(), headerRect.bottom() - 1, headerRect.x() + Math.min(headerRect.width(), EscText.width(font, title, EscFonts.DEFAULT) + 16), headerRect.bottom(), rule);
    }

    private void drawStatusBanner(GuiGraphics g) {
        if (statusRect == null) return;
        Banner banner = banner();
        EscPanel.fill(g, statusRect, argb(banner.rgb, banner.tone == BannerTone.COUNT ? 0x44 : 0x22));
        EscPanel.renderFrame(g, statusRect, opaque(banner.rgb), 1, EscFrameStyle.SQUARE);
        EscText.drawInRect(g, font, Component.literal(banner.text), statusRect.inset(EscInsets.of(6, 2)),
            opaque(banner.rgb), EscFonts.DEFAULT, true, 0.7f);
    }

    private void drawCard(GuiGraphics g, EscRect card, String title, String meta) {
        EscPanel.fill(g, card, EscPanel.withOpacity(0xFF000000, 0.28f));
        EscPanel.renderFrame(g, card, style.panelBorderColor(), 1, EscFrameStyle.SQUARE);
        EscRect head = new EscRect(card.x() + 1, card.y() + 1, card.width() - 2, COL_HEADER);
        EscPanel.fill(g, head, EscPanel.withOpacity(opaque(style.accentColor()), 0.18f));
        g.fill(card.x() + 1, card.y() + 1, card.x() + 3, card.y() + 1 + COL_HEADER, opaque(style.accentColor()));
        int y = head.y() + (COL_HEADER - font.lineHeight) / 2;
        EscText.drawString(g, font, title, head.x() + 8, y, titleRgb(), false, EscFonts.DEFAULT);
        int metaW = EscText.width(font, meta, EscFonts.DEFAULT);
        EscText.drawString(g, font, meta, head.right() - metaW - 6, y, opaque(style.mutedColor()), false, EscFonts.DEFAULT);
    }

    private void drawEmpty(GuiGraphics g, EscRect viewport, EscSimpleScrollList<?> list, String text) {
        if (viewport == null || list == null || !list.items().isEmpty() || text == null || text.isEmpty()) return;
        int y = viewport.y() + Math.max(4, (viewport.height() - font.lineHeight) / 2);
        EscText.drawCentered(g, font, Component.literal(text), viewport.x() + viewport.width() / 2, y, opaque(style.mutedColor()), EscFonts.DEFAULT);
    }

    private void drawScrollbar(GuiGraphics g, EscSimpleScrollList<?> list, EscRect viewport) {
        if (viewport == null) return;
        int max = list.maxScrollPixels();
        if (max <= 0 || viewport.height() <= 8) return;
        int x = viewport.right() + 2;
        int thumbH = Math.max(8, viewport.height() * viewport.height() / (viewport.height() + max));
        int travel = Math.max(0, viewport.height() - thumbH);
        int thumbY = viewport.y() + (int) (list.scrollPercent() * travel);
        g.fill(x, viewport.y(), x + 2, viewport.bottom(), argb(style.mutedColor(), 0x55));
        g.fill(x, thumbY, x + 2, thumbY + thumbH, opaque(style.accentColor()));
    }

    private void markPrimary(GuiGraphics g) {
        Button primary = goButton != null ? goButton : readyButton;
        if (primary == null || !primary.active || !primary.visible) return;
        g.fill(primary.getX() + 1, primary.getY() + primary.getHeight() - 2,
            primary.getX() + primary.getWidth() - 1, primary.getY() + primary.getHeight() - 1,
            opaque(style.accentColor()));
    }

    private String onlineEmpty() {
        return nameFilter == null || nameFilter.isBlank()
            ? tr("screen.radiotowers.airdrop_lobby.empty_online")
            : tr("screen.radiotowers.airdrop_lobby.empty_filter");
    }

    private Banner banner() {
        if (countdownSeconds > 0) {
            return new Banner(BannerTone.COUNT, tr("screen.radiotowers.airdrop_lobby.status.countdown", countdownSeconds), rgb(style.accentColor()));
        }
        if (sync == null) {
            return new Banner(BannerTone.SYNC, tr("screen.radiotowers.airdrop_lobby.status.syncing"), rgb(style.mutedColor()));
        }
        String block = sync.blockReason() == null ? "" : sync.blockReason();
        if (!block.isEmpty()) {
            return new Banner(BannerTone.WAIT, block, rgb(style.secondaryColor()));
        }
        if (sync.canStart()) {
            return new Banner(BannerTone.READY, tr("screen.radiotowers.airdrop_lobby.status.ready"), rgb(style.accentColor()));
        }
        return new Banner(BannerTone.SYNC, tr("screen.radiotowers.airdrop_lobby.status.syncing"), rgb(style.mutedColor()));
    }

    private String partyCount(int members) {
        int max = members;
        try {
            max = AirdropConfig.getLobbyMaxPlayers();
        } catch (RuntimeException ignored) {
            max = Math.max(members, 2);
        }
        return tr("screen.radiotowers.airdrop_lobby.count", members, max);
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "READY" -> tr("screen.radiotowers.airdrop_lobby.status.ready_member");
            case "INVITED" -> tr("screen.radiotowers.airdrop_lobby.status.invited");
            case "JOINED" -> tr("screen.radiotowers.airdrop_lobby.status.joined");
            default -> status;
        };
    }

    private static String actionLabel(String action) {
        return "CANCEL".equals(action)
            ? tr("screen.radiotowers.airdrop_lobby.cancel")
            : tr("screen.radiotowers.airdrop_lobby.kick");
    }

    private int titleRgb() {
        return opaque(style.textColorTitle());
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static int rgb(int argb) {
        return argb & 0xFFFFFF;
    }

    private static int opaque(int color) {
        return 0xFF000000 | (color & 0xFFFFFF);
    }

    private static int argb(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0xFFFFFF);
    }

    private enum BannerTone { READY, WAIT, SYNC, COUNT }

    private record Banner(BannerTone tone, String text, int rgb) {}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (onlineList != null && onlineList.mouseScrolled(mouseX, mouseY, delta)) return true;
        if (memberList != null && memberList.mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int rowH = style.listRowHeight();
            if (rowH > 0 && onlineList != null && onlineViewport != null && onlineViewport.contains(mouseX, mouseY)) {
                int index = (int) ((mouseY - onlineViewport.y() + onlineList.scrollOffset()) / rowH);
                List<OnlineRow> items = onlineList.items();
                if (index >= 0 && index < items.size()) {
                    inviteByName(items.get(index).name);
                    return true;
                }
            }
            if (rowH > 0 && memberList != null && memberViewport != null && memberViewport.contains(mouseX, mouseY)) {
                int index = (int) ((mouseY - memberViewport.y() + memberList.scrollOffset()) / rowH);
                List<MemberRow> items = memberList.items();
                if (index >= 0 && index < items.size()) {
                    MemberRow row = items.get(index);
                    if (!row.action.isEmpty() && AirdropLobbyNetwork.isAvailable()) {
                        int right = memberViewport.x() + memberViewport.width() - 4;
                        Chip badge = placeChip(row.host ? tr("screen.radiotowers.airdrop_lobby.host") : statusLabel(row.status), right, 0, rowH);
                        Chip action = placeChip(actionLabel(row.action), badge.x - 4, 0, rowH);
                        int rowTop = memberViewport.y() + index * rowH - (int) memberList.scrollOffset();
                        Chip hit = new Chip(action.x, rowTop + (rowH - action.h) / 2, action.w, action.h);
                        if (hit.contains(mouseX, mouseY)) {
                            if ("CANCEL".equals(row.action)) {
                                AirdropLobbyNetwork.channel().sendToServer(new LobbyPackets.CancelInvite(lobbyId, row.uuid));
                            } else {
                                AirdropLobbyNetwork.channel().sendToServer(new LobbyPackets.Kick(lobbyId, row.uuid));
                            }
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
