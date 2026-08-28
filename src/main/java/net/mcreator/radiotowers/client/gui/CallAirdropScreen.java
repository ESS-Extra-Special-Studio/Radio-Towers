package net.mcreator.radiotowers.client.gui;

import net.mcreator.radiotowers.airdrop.AirdropCatalog;
import net.mcreator.radiotowers.airdrop.TaczAirdropIntegration;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.integration.AirdropDifficultyTier;
import net.mcreator.radiotowers.network.CallAirdropMessage;
import net.mcreator.radiotowers.network.ActivatePanelWithStationMessage;
import net.mcreator.radiotowers.network.DeactivatePanelMessage;
import net.mcreator.radiotowers.network.RequestAirdropStatePacket;
import net.mcreator.radiotowers.integration.DeadAirDefaultStations;
import net.mcreator.radiotowers.RadiotowersMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscAnchor;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscInsets;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscLayoutSpec;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscPanel;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscScreen;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscSearchBox;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscText;

import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Airdrop call GUI: item order list (left), START lever, difficulty meter.
 * When Dead Air is installed: Turn off, Default (cycle), Dynamic, Internet.
 * This screen assigns the tower's broadcast station — tuning in is done in Dead Air's walkie GUI.
 */
public class CallAirdropScreen extends EscScreen implements InternetStationParent {

    private final BlockPos panelPos;
    private final List<AirdropCatalog.Entry> catalog;
    /** Default assignable stations (cycle only; no Emergency Broadcast — that is tune-only in Dead Air GUI). */
    /** Refreshed from {@link DeadAirDefaultStations} (Jukebox FM only when upgrade is installed on this panel). */
    private List<StationOption> defaultStationsCache = List.of();
    /** Dynamic pool excludes fixed/default/internet stations and includes Dead Air auto-discovered mod stations. */
    private static final List<String> NON_DYNAMIC_STATION_IDS = List.of(
        "dead_air:emergency_broadcast",
        "dead_air:bedrock_radio",
        "dead_air:creatopia_radio",
        "dead_air:remix_radio",
        "dead_air:jukebox_fm",
        "dead_air:internet_cr1",
        "dead_air:internet_ambiance_fm"
    );
    private int defaultCycleIndex = 0;
    private int dynamicCycleIndex = 0;
    private String lastDynamicName = null;
    /** Current station ID (e.g. dead_air:remix_radio) from Dead Air when opening; null if unknown/off. */
    private String currentStationId = null;
    /** Which button is currently active: 0=default, 1=dynamic, 2=internet, -1=none. */
    private int activeButton = -1;
    private Button defaultStationButton;
    private Button dynamicStationButton;
    private Button internetButton;
    /** Catalog index -> quantity ordered (0 = not in order). */
    private final Map<Integer, Integer> orderQuantities = new LinkedHashMap<>();
    private int scrollOffset = 0;
    /** Search filter: only entries whose name or id contain this (case-insensitive) are shown. */
    private String searchQuery = "";
    /** Indices into catalog for entries that match searchQuery. Rebuilt when search changes. */
    private final List<Integer> filteredIndices = new ArrayList<>();
    private EditBox searchEditBox;

    /** Responsive layout regions (recomputed in {@link #buildLayout}). */
    private EscRect listColumn;
    private EscRect controlsColumn;
    private EscRect hintColumn;
    private EscRect searchRect;
    private EscRect titleBand;
    private EscRect listAreaRect;
    private EscRect leverAreaRect;
    private EscRect difficultyBarRect;

    private static final int ROW_HEIGHT = 22;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SEARCH_HEIGHT = 20;
    /** Title band height (below search). */
    private static final int TITLE_BAND_HEIGHT = 44;
    private static final int LIST_HINT_RESERVE = 14;
    /** Push lever, difficulty bar, and station buttons below the column top. */
    private static final int CONTROLS_TOP_OFFSET = 28;
    /** Buttons placed next to the item selection. */
    private static final int BUTTON_WIDTH = 120;
    /** Lever (airdrop switch) size. */
    private static final int LEVER_WIDTH = 44;
    private static final int LEVER_HEIGHT = 36;
    private static final int GAP_LEVER_TO_DIFFICULTY = 8;
    private static final int DIFFICULTY_BAR_HEIGHT = 16;
    private static final int DIFFICULTY_BAR_WIDTH = 100;
    private static final int DIFFICULTY_POINTS_PER_BAR = 10;
    /** Hints drawn immediately right of each Dead Air button (ASCII only). */
    private static final int DEAD_AIR_HINT_COLOR = 0x888888;

    /** Short debounce to prevent accidental double-click spam. */
    private long lastToggleSentAt = 0L;
    /** Last non-null station used by this panel so Turn on restores expected broadcast. */
    private String lastStationIdBeforeTurnOff = null;
    private Button turnOffButton;

    public CallAirdropScreen(BlockPos panelPos) {
        super(Component.translatable("screen.radiotowers.call_airdrop.title"));
        this.panelPos = panelPos;
        // So TaCZ recipe fallback can discover guns/ammo from synced recipes (avoids reflection to obfuscated Minecraft)
        TaczAirdropIntegration.setClientLevelForCatalog(Minecraft.getInstance().level);
        AirdropCatalog.invalidate(); // rebuild so TaCZ and other mod items are present (catalog may have been built before they registered)
        this.catalog = AirdropCatalog.getEntries();
        rebuildFilteredIndices();
    }

    /** Rebuild filteredIndices from catalog and current searchQuery (match on item id and display name, case-insensitive). */
    private void rebuildFilteredIndices() {
        filteredIndices.clear();
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < catalog.size(); i++) {
            AirdropCatalog.Entry e = catalog.get(i);
            if (q.isEmpty()) {
                filteredIndices.add(i);
                continue;
            }
            ItemStack stack = getDisplayStack(e);
            ResourceLocation itemId = e.getItemId();
            String id = itemId.toString().toLowerCase(Locale.ROOT);
            String rawName = stack.isEmpty() ? id : stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if ("minecraft".equals(itemId.getNamespace()) && itemId.getPath().startsWith("music_disc_"))
                rawName = formatMusicDiscName(itemId.getPath()).toLowerCase(Locale.ROOT);
            String override = TaczAirdropIntegration.getDisplayNameForCatalog(e.getItemId(), stack.isEmpty() ? "" : stack.getHoverName().getString());
            String name = override != null ? override.toLowerCase(Locale.ROOT) : rawName;
            if (id.contains(q) || name.contains(q))
                filteredIndices.add(i);
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, filteredIndices.size() - 1)));
    }

    /** Number of item rows that fit in the list area. */
    private int getListVisibleRows() {
        if (listAreaRect == null) return 1;
        return Math.max(1, listAreaRect.height() / ROW_HEIGHT);
    }

    private int listPanelLeft() {
        return listAreaRect != null ? listAreaRect.x() : 0;
    }

    private int listLeft() {
        return listPanelLeft() + SCROLLBAR_WIDTH;
    }

    private int listWidth() {
        return listAreaRect != null ? Math.max(0, listAreaRect.width() - SCROLLBAR_WIDTH) : 0;
    }

    private int listTop() {
        return listAreaRect != null ? listAreaRect.y() : 0;
    }

    /** Bottom Y of the item selection box. */
    private int getListBottom() {
        return listAreaRect != null ? listAreaRect.bottom() : 0;
    }

    /** Stack to show in the airdrop list. Uses TaCZ creative-cache stack when available. For ammo, never show iron ammo_box: use tacz:ammo stack or build one. */
    private static ItemStack getDisplayStack(AirdropCatalog.Entry e) {
        ResourceLocation id = e.getItemId();
        if ("tacz".equals(id.getNamespace())) {
            String path = id.getPath();
            if (path.startsWith("ammo/")) {
                ItemStack cached = TaczAirdropIntegration.getCachedCreativeStack(id);
                if (cached != null && !cached.isEmpty() && !TaczAirdropIntegration.isAmmoBoxStack(cached))
                    return cached;
                ItemStack ammoStack = TaczAirdropIntegration.createAmmoDisplayStack(id);
                if (ammoStack != null && !ammoStack.isEmpty()) return ammoStack;
            } else if (path.startsWith("gun/")) {
                ItemStack cached = TaczAirdropIntegration.getCachedCreativeStack(id);
                if (cached != null && !cached.isEmpty()) return cached;
                ItemStack gunStack = TaczAirdropIntegration.createGunDisplayStackWithAlternates(id);
                if (gunStack != null && !gunStack.isEmpty()) return gunStack;
            }
        }
        return e.createStack();
    }

    private EscRect titleLine1;
    private EscRect titleLine2;
    private EscRect titleLine3;

    private void recomputeLayoutRegions(EscRect content) {
        if (ModList.get().isLoaded("dead_air")) {
            EscRect[] cols = content.splitColumns(new float[]{0.42f, 0.30f, 0.28f}, 10);
            listColumn = cols[0];
            controlsColumn = cols[1];
            hintColumn = cols[2];
        } else {
            EscRect[] cols = content.splitColumns(new float[]{0.52f, 0.48f}, 10);
            listColumn = cols[0];
            controlsColumn = cols[1];
            hintColumn = controlsColumn;
        }

        searchRect = resolve(listColumn, EscLayoutSpec.stretchH(0, SEARCH_HEIGHT, EscInsets.ZERO));
        titleBand = new EscRect(listColumn.x(), searchRect.bottom() + 4, listColumn.width(), TITLE_BAND_HEIGHT);
        int titleLineH = EscText.measureLineHeight(font, EscFonts.DEFAULT) + 1;
        int titlePad = 2;
        titleLine1 = new EscRect(titleBand.x() + titlePad, titleBand.y() + titlePad, titleBand.width() - titlePad * 2, titleLineH);
        titleLine2 = new EscRect(titleBand.x() + titlePad, titleLine1.bottom() + 1, titleBand.width() - titlePad * 2, titleLineH);
        titleLine3 = new EscRect(titleBand.x() + titlePad, titleLine2.bottom() + 1, titleBand.width() - titlePad * 2, titleLineH);
        listAreaRect = new EscRect(
            listColumn.x(),
            titleBand.bottom() + 6,
            listColumn.width(),
            Math.max(ROW_HEIGHT * 3, listColumn.bottom() - titleBand.bottom() - 6 - LIST_HINT_RESERVE)
        );

        leverAreaRect = resolve(controlsColumn, EscLayoutSpec.of(EscAnchor.TOP_LEFT, 0, CONTROLS_TOP_OFFSET, LEVER_WIDTH, LEVER_HEIGHT));
        difficultyBarRect = new EscRect(
            leverAreaRect.right() + GAP_LEVER_TO_DIFFICULTY,
            leverAreaRect.bottom() - DIFFICULTY_BAR_HEIGHT,
            DIFFICULTY_BAR_WIDTH,
            DIFFICULTY_BAR_HEIGHT
        );
    }

    @Override
    protected void buildLayout() {
        EscRect content = contentRect();
        recomputeLayoutRegions(content);

        searchEditBox = addSearchBox(searchRect, EscText.styled(Component.translatable("screen.radiotowers.search.title")));
        searchEditBox.setHint(EscText.styled(Component.translatable("screen.radiotowers.search.hint")));
        searchEditBox.setMaxLength(64);
        searchEditBox.setValue(searchQuery);
        searchEditBox.setResponder(s -> {
            searchQuery = s;
            rebuildFilteredIndices();
        });

        refreshDefaultStationsCache();
        fetchCurrentStationFromDeadAir();
        if (ModList.get().isLoaded("berezkas_zombie_waves_api") || ModList.get().isLoaded("berezka_zombie_waves_api"))
            RadiotowersMod.PACKET_HANDLER.sendToServer(new RequestAirdropStatePacket(panelPos));

        EscRect leverBounds = leverAreaRect;
        AirdropLeverWidget lever = new AirdropLeverWidget(0, 0, LEVER_WIDTH, LEVER_HEIGHT, this::onStart,
            () -> AirdropConfig.ENABLE_CATALOG_AIRDROP.get() && AirdropDifficultyTier.canStart(getTotalDifficulty()));
        layoutWidget(lever, leverBounds);
        addRenderableWidget(lever);

        int y = leverBounds.bottom() + 14 - controlsColumn.y();
        if (ModList.get().isLoaded("dead_air")) {
            turnOffButton = addAnchoredButton(EscText.literal(getPowerButtonLabel()), controlsColumn,
                EscLayoutSpec.of(EscAnchor.TOP_LEFT, 0, y, BUTTON_WIDTH, 20), b -> onTurnOff());
            y += 24;
            defaultStationButton = addAnchoredButton(EscText.literal(defaultLabel()), controlsColumn,
                EscLayoutSpec.of(EscAnchor.TOP_LEFT, 0, y, BUTTON_WIDTH, 20), b -> onDefaultCycle());
            y += 24;
            dynamicStationButton = addAnchoredButton(EscText.literal(dynamicLabel()), controlsColumn,
                EscLayoutSpec.of(EscAnchor.TOP_LEFT, 0, y, BUTTON_WIDTH, 20), b -> onDynamicCycle());
            y += 24;
            internetButton = addAnchoredButton(EscText.styled(Component.translatable("screen.radiotowers.button.internet")), controlsColumn,
                EscLayoutSpec.of(EscAnchor.TOP_LEFT, 0, y, BUTTON_WIDTH, 20), b -> openInternet());
        }
    }

    private void refreshDefaultStationsCache() {
        if (!ModList.get().isLoaded("dead_air")) {
            defaultStationsCache = List.of();
            return;
        }
        ArrayList<StationOption> next = new ArrayList<>();
        for (DeadAirDefaultStations.Entry e : DeadAirDefaultStations.forPanel(panelPos)) {
            next.add(new StationOption(e.displayName(), e.stationId()));
        }
        defaultStationsCache = next;
        if (!defaultStationsCache.isEmpty() && defaultCycleIndex >= defaultStationsCache.size()) {
            defaultCycleIndex = defaultStationsCache.size() - 1;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (ModList.get().isLoaded("dead_air") && minecraft != null && minecraft.player != null
            && minecraft.player.tickCount % 40 == 0) {
            refreshDefaultStationsCache();
            if (defaultStationButton != null) {
                defaultStationButton.setMessage(EscText.literal(defaultLabel()));
            }
        }
    }

    /** Get current station for this panel from Dead Air (client cache) via reflection. */
    private void fetchCurrentStationFromDeadAir() {
        if (!ModList.get().isLoaded("dead_air")) return;
        try {
            Class<?> cache = Class.forName("uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache");
            java.lang.reflect.Method m = cache.getMethod("getStationForPanel", BlockPos.class);
            Object result = m.invoke(null, panelPos);
            if (result != null) {
                currentStationId = result.toString();
                if (findDefaultIndex(currentStationId) >= 0) {
                    activeButton = 0;
                    defaultCycleIndex = findDefaultIndex(currentStationId);
                } else if (findDynamicName(currentStationId) != null) {
                    activeButton = 1;
                    lastDynamicName = findDynamicName(currentStationId);
                    int idx = findDynamicIndex(currentStationId);
                    if (idx >= 0) dynamicCycleIndex = idx;
                } else {
                    activeButton = 2; // internet or other
                }
            }
        } catch (Throwable ignored) {}
    }

    private int findDefaultIndex(String stationId) {
        if (stationId == null) return -1;
        for (int i = 0; i < defaultStationsCache.size(); i++)
            if (stationId.equals(defaultStationsCache.get(i).stationId)) return i;
        return -1;
    }

    private String findDynamicName(String stationId) {
        if (stationId == null) return null;
        for (StationOption opt : getDynamicStations())
            if (stationId.equals(opt.stationId)) return opt.displayName;
        return null;
    }

    private int findDynamicIndex(String stationId) {
        if (stationId == null) return -1;
        List<StationOption> dynamicStations = getDynamicStations();
        for (int i = 0; i < dynamicStations.size(); i++) {
            if (stationId.equals(dynamicStations.get(i).stationId)) return i;
        }
        return -1;
    }

    /** Current default station name (no "Default:" prefix). Shows tower's current station if it's a default, else next in cycle. */
    private String defaultLabel() {
        if (defaultStationsCache.isEmpty()) return Component.translatable("screen.radiotowers.button.default").getString();
        int idx = findDefaultIndex(currentStationId);
        if (idx >= 0) return defaultStationsCache.get(idx).displayName;
        return defaultStationsCache.get(Math.min(defaultCycleIndex, defaultStationsCache.size() - 1)).displayName;
    }

    private String dynamicLabel() {
        return lastDynamicName != null ? lastDynamicName : Component.translatable("screen.radiotowers.button.dynamic").getString();
    }

    private void onDefaultCycle() {
        if (defaultStationsCache.isEmpty()) return;
        int currentIdx = findDefaultIndex(currentStationId);
        if (currentIdx >= 0) {
            defaultCycleIndex = currentIdx;
        } else if (defaultCycleIndex >= defaultStationsCache.size()) {
            defaultCycleIndex = 0;
        }
        int nextIndex = (defaultCycleIndex + 1) % defaultStationsCache.size();
        StationOption opt = defaultStationsCache.get(nextIndex);
        RadiotowersMod.PACKET_HANDLER.sendToServer(new ActivatePanelWithStationMessage(panelPos, opt.stationId));
        currentStationId = opt.stationId;
        lastStationIdBeforeTurnOff = opt.stationId;
        activeButton = 0;
        defaultCycleIndex = nextIndex;
        if (defaultStationButton != null) defaultStationButton.setMessage(EscText.literal(defaultLabel()));
        if (turnOffButton != null) turnOffButton.setMessage(EscText.literal(getPowerButtonLabel()));
    }

    private void onDynamicCycle() {
        List<StationOption> dynamicStations = getDynamicStations();
        if (dynamicStations.isEmpty()) return;
        int currentIdx = findDynamicIndex(currentStationId);
        if (currentIdx >= 0) {
            dynamicCycleIndex = currentIdx;
        } else if (dynamicCycleIndex >= dynamicStations.size()) {
            dynamicCycleIndex = 0;
        }
        int nextIndex = (dynamicCycleIndex + 1) % dynamicStations.size();
        StationOption opt = dynamicStations.get(nextIndex);
        RadiotowersMod.PACKET_HANDLER.sendToServer(new ActivatePanelWithStationMessage(panelPos, opt.stationId));
        currentStationId = opt.stationId;
        lastStationIdBeforeTurnOff = opt.stationId;
        activeButton = 1;
        lastDynamicName = opt.displayName;
        dynamicCycleIndex = nextIndex;
        if (dynamicStationButton != null) dynamicStationButton.setMessage(EscText.literal(dynamicLabel()));
        if (turnOffButton != null) turnOffButton.setMessage(EscText.literal(getPowerButtonLabel()));
    }

    private void openInternet() {
        activeButton = 2;
        if (minecraft != null) minecraft.setScreen(new InternetStationScreen(this, panelPos));
    }

    @Override
    public void onInternetStreamChosen(String deadAirStationId) {
        this.currentStationId = deadAirStationId;
        this.lastStationIdBeforeTurnOff = deadAirStationId;
        this.activeButton = 2;
        if (turnOffButton != null) turnOffButton.setMessage(EscText.literal(getPowerButtonLabel()));
    }

    private void onStart() {
        int total = getTotalDifficulty();
        if (!AirdropConfig.ENABLE_CATALOG_AIRDROP.get()) return;
        if (!AirdropDifficultyTier.canStart(total)) return; // enforce 10-point increments (10,20,30...)
        int maxTotal = AirdropCatalog.getMaxTotalDifficulty();
        total = Math.min(total, maxTotal);
        List<String> itemIds = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        for (var e : orderQuantities.entrySet()) {
            int qty = e.getValue();
            if (qty <= 0) continue;
            int idx = e.getKey();
            if (idx >= 0 && idx < catalog.size()) {
                AirdropCatalog.Entry entry = catalog.get(idx);
                itemIds.add(entry.getItemId().toString());
                quantities.add(qty * entry.getDefaultStackSize());
            }
        }
        RadiotowersMod.PACKET_HANDLER.sendToServer(new CallAirdropMessage(panelPos, total, itemIds, quantities));
        // Clear selection so the next airdrop (if user clicks START again) only has new choices, not previous + new
        orderQuantities.clear();
        boolean hasWaveApi = ModList.get().isLoaded("berezkas_zombie_waves_api") || ModList.get().isLoaded("berezka_zombie_waves_api");
        if (!hasWaveApi && minecraft != null) minecraft.setScreen(null);
    }

    private void onTurnOff() {
        long now = System.currentTimeMillis();
        if (now - lastToggleSentAt < 250) return;
        lastToggleSentAt = now;

        if (currentStationId != null && !currentStationId.isEmpty()) {
            lastStationIdBeforeTurnOff = currentStationId;
            RadiotowersMod.PACKET_HANDLER.sendToServer(new DeactivatePanelMessage(panelPos));
            currentStationId = null;
            activeButton = -1;
            lastDynamicName = null;
        } else {
            String stationToRestore = (lastStationIdBeforeTurnOff != null && !lastStationIdBeforeTurnOff.isEmpty())
                ? lastStationIdBeforeTurnOff
                : (defaultStationsCache.isEmpty() ? "dead_air:bedrock_radio" : defaultStationsCache.get(
                    Math.min(defaultCycleIndex, defaultStationsCache.size() - 1)).stationId);
            RadiotowersMod.PACKET_HANDLER.sendToServer(new ActivatePanelWithStationMessage(panelPos, stationToRestore));
            currentStationId = stationToRestore;
            int idx = findDefaultIndex(stationToRestore);
            if (idx >= 0) {
                defaultCycleIndex = idx;
                activeButton = 0;
            } else if (findDynamicName(stationToRestore) != null) {
                activeButton = 1;
                lastDynamicName = findDynamicName(stationToRestore);
                int didx = findDynamicIndex(stationToRestore);
                if (didx >= 0) dynamicCycleIndex = didx;
            } else {
                activeButton = 2;
            }
        }
        if (defaultStationButton != null) defaultStationButton.setMessage(EscText.literal(defaultLabel()));
        if (dynamicStationButton != null) dynamicStationButton.setMessage(EscText.literal(dynamicLabel()));
        if (turnOffButton != null) turnOffButton.setMessage(EscText.literal(getPowerButtonLabel()));
    }

    private String getPowerButtonLabel() {
        return (currentStationId == null || currentStationId.isEmpty())
            ? Component.translatable("screen.radiotowers.button.turn_on").getString()
            : Component.translatable("screen.radiotowers.button.turn_off").getString();
    }

    private int getTotalDifficulty() {
        int t = 0;
        for (var e : orderQuantities.entrySet()) {
            int qty = e.getValue();
            if (qty <= 0) continue;
            int idx = e.getKey();
            if (idx >= 0 && idx < catalog.size())
                t += catalog.get(idx).getDifficultyPoints() * qty;
        }
        return t;
    }

    /** Word-wrap hint to the right of a button row; y = first line baseline (aligned with button text). */
    private void drawDeadAirHint(GuiGraphics guiGraphics, String text, int x, int y, int maxWidth) {
        EscText.drawWrapped(guiGraphics, font, text, x, y, maxWidth, DEAD_AIR_HINT_COLOR);
    }

    /** Vanilla music discs all use the same translation "Music Disc"; show distinct names e.g. "Music Disc (13)", "Music Disc (Cat)". */
    private static String formatMusicDiscName(String path) {
        if (path == null || !path.startsWith("music_disc_")) return "Music Disc";
        String suffix = path.substring("music_disc_".length()).replace('_', ' ');
        if (suffix.isEmpty()) return "Music Disc";
        boolean allDigits = suffix.chars().allMatch(Character::isDigit);
        String label = allDigits ? suffix : (suffix.substring(0, 1).toUpperCase(Locale.ROOT) + suffix.substring(1).toLowerCase(Locale.ROOT));
        return "Music Disc (" + label + ")";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        EscRect content = contentRect();
        EscPanel.renderPanel(guiGraphics, content, style);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int visibleRows = getListVisibleRows();
        int listSize = filteredIndices.size();
        int maxScroll = Math.max(0, listSize - visibleRows);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        int listBottom = getListBottom();
        int listPanelLeft = listPanelLeft();
        int listLeft = listLeft();
        int listWidth = listWidth();
        int listTop = listTop();
        int listPanelWidth = listAreaRect.width();
        int trackHeight = listAreaRect.height();

        if (titleLine1 != null && titleLine2 != null && titleLine3 != null) {
            EscText.drawScrollingString(guiGraphics, font,
                "Select your loot. Pull down the lever to call in the airdrop.",
                titleLine1.x(), titleLine1.y(), titleLine1.width(), 0xE0E0E0, EscFonts.DEFAULT);
            EscText.drawScrollingString(guiGraphics, font,
                "Minimum " + AirdropDifficultyTier.getMinimumPoints() + " pts; total must use 10-point increments.",
                titleLine2.x(), titleLine2.y(), titleLine2.width(), 0x88CCFF, EscFonts.DEFAULT);
            EscText.drawScrollingString(guiGraphics, font,
                "Warning: Greed will affect difficulty.",
                titleLine3.x(), titleLine3.y(), titleLine3.width(), 0xCC8800, EscFonts.DEFAULT);
        }

        // Item panel: scrollbar (left) + list content (right)
        guiGraphics.fill(listPanelLeft, listTop - 1, listPanelLeft + listPanelWidth + 1, listBottom + 1, 0xFF404040);
        guiGraphics.fill(listLeft, listTop, listLeft + listWidth, listBottom, 0xFF2A2A2A);

        // Scrollbar track (left side of item selection)
        guiGraphics.fill(listPanelLeft, listTop, listPanelLeft + SCROLLBAR_WIDTH, listBottom, 0xFF1A1A1A);
        if (maxScroll > 0 && listSize > 0) {
            int thumbHeight = Math.max(8, (visibleRows * trackHeight) / Math.max(1, listSize));
            int thumbY = listTop + (int) ((long) (trackHeight - thumbHeight) * scrollOffset / maxScroll);
            guiGraphics.fill(listPanelLeft + 1, thumbY, listPanelLeft + SCROLLBAR_WIDTH - 1, thumbY + thumbHeight, 0xFF606060);
        }

        // List rows (clipped to visible area only; indices refer to filtered list, map to catalog via filteredIndices)
        for (int row = 0; row < visibleRows; row++) {
            int idx = scrollOffset + row;
            if (idx >= listSize) break;
            int catalogIdx = filteredIndices.get(idx);
            AirdropCatalog.Entry e = catalog.get(catalogIdx);
            int y = listTop + row * ROW_HEIGHT;
            int qty = orderQuantities.getOrDefault(catalogIdx, 0);
            if (qty > 0)
                guiGraphics.fill(listLeft + 2, y + 1, listLeft + listWidth - 2, y + ROW_HEIGHT - 1, 0x4044AA44);
            ItemStack stack = getDisplayStack(e);
            ResourceLocation itemId = e.getItemId();
            boolean useTaczPlaceholder = AirdropConfig.SIMPLIFY_TACZ_ICONS_IN_AIRDROP_LIST.get()
                && "tacz".equals(itemId.getNamespace())
                && (itemId.getPath().startsWith("ammo/") || itemId.getPath().startsWith("gun/"));
            if (!stack.isEmpty() && !useTaczPlaceholder) {
                guiGraphics.renderItem(stack, listLeft + 4, y + 2);
                guiGraphics.renderItemDecorations(font, stack, listLeft + 4, y + 2);
            } else if (useTaczPlaceholder) {
                guiGraphics.fill(listLeft + 4, y + 2, listLeft + 20, y + 18, 0xFF_3d3d3d);
                guiGraphics.fill(listLeft + 5, y + 3, listLeft + 19, y + 17, 0xFF_2d2d2d);
            }
            String name = stack.isEmpty() ? itemId.toString() : stack.getHoverName().getString();
            if (!stack.isEmpty() && "minecraft".equals(itemId.getNamespace()) && itemId.getPath().startsWith("music_disc_"))
                name = formatMusicDiscName(itemId.getPath());
            String override = TaczAirdropIntegration.getDisplayNameForCatalog(e.getItemId(), name);
            if (override != null) name = override;
            if (font.width(name) > listWidth - 85) name = font.plainSubstrByWidth(name, listWidth - 89) + "...";
            EscText.drawString(guiGraphics, font, name, listLeft + 26, y + 6, 0xE0E0E0, false, EscFonts.DEFAULT);
            String right = (qty > 0 ? "x" + qty + " " : "") + "+" + e.getDifficultyPoints() + (qty > 0 ? " (" + (e.getDifficultyPoints() * qty) + ")" : "");
            EscText.drawString(guiGraphics, font, right, listLeft + listWidth - font.width(right) - 4, y + 6, 0x88FF88, false, EscFonts.DEFAULT);
        }
        EscText.drawString(guiGraphics, font, "L-click +1  R-click -1", listPanelLeft, listBottom + 2, 0x808080, false, EscFonts.DEFAULT);

        // Visual indicator for which station button is currently active (green border)
        if (ModList.get().isLoaded("dead_air") && activeButton >= 0) {
            Button active = activeButton == 0 ? defaultStationButton : (activeButton == 1 ? dynamicStationButton : internetButton);
            if (active != null) {
                int bx = active.getX();
                int bw = active.getWidth();
                int by = active.getY();
                int bh = active.getHeight();
                int c = 0xFF22AA22;
                guiGraphics.fill(bx, by, bx + bw, by + 2, c);
                guiGraphics.fill(bx, by + bh - 2, bx + bw, by + bh, c);
                guiGraphics.fill(bx, by, bx + 2, by + bh, c);
                guiGraphics.fill(bx + bw - 2, by, bx + bw, by + bh, c);
            }
        }

        // Dead Air: short hint in the hint column beside each button row
        if (ModList.get().isLoaded("dead_air") && turnOffButton != null) {
            int textX = hintColumn.x();
            int maxW = hintColumn.width();
            final String hintDynamic = "Extra stations added via addons or other mods.";
            final String hintInternet = "Web stream as a station.";
            final String hintFoot = "Hear it: walkie on, match MHz (Dead Air).";
            drawDeadAirHint(guiGraphics, "Broadcast on/off", textX, turnOffButton.getY() + 6, maxW);
            drawDeadAirHint(guiGraphics, "What this tower plays (click to cycle).", textX, defaultStationButton.getY() + 6, maxW);
            int dynamicY = dynamicStationButton.getY() + 6;
            drawDeadAirHint(guiGraphics, hintDynamic, textX, dynamicY, maxW);
            int dynamicBottom = dynamicY + EscText.wrapHeight(font, hintDynamic, maxW);
            int internetY = Math.max(internetButton.getY() + 6, dynamicBottom + 4);
            drawDeadAirHint(guiGraphics, hintInternet, textX, internetY, maxW);
            int footY = internetY + EscText.wrapHeight(font, hintInternet, maxW) + 4;
            drawDeadAirHint(guiGraphics, hintFoot, textX, footY, maxW);
        }

        // Difficulty meter: bottom-aligned with lever, to the right of the lever
        int meterX = difficultyBarRect.x();
        int meterY = difficultyBarRect.y();
        int meterW = difficultyBarRect.width();
        EscText.drawString(guiGraphics, font, "DIFFICULTY", meterX, meterY - 12, 0xA0A0A0, false, EscFonts.DEFAULT);
        guiGraphics.fill(meterX, meterY, meterX + meterW, meterY + DIFFICULTY_BAR_HEIGHT, 0xFF1A1A1A);
        int maxTotal = AirdropCatalog.getMaxTotalDifficulty();
        int total = Math.min(getTotalDifficulty(), maxTotal);

        // Snap line: next valid 10-point increment (10,20,30...). Shown as a yellow bar underneath.
        int snapped = 0;
        if (total > 0) {
            snapped = ((total + DIFFICULTY_POINTS_PER_BAR - 1) / DIFFICULTY_POINTS_PER_BAR) * DIFFICULTY_POINTS_PER_BAR;
            if (snapped > maxTotal) snapped = maxTotal;
        }
        int snapW = maxTotal <= 0 ? 0 : (meterW * snapped / maxTotal);
        if (snapW > 0) {
            guiGraphics.fill(meterX, meterY, meterX + snapW, meterY + DIFFICULTY_BAR_HEIGHT, 0xFFFFAA33); // yellow/orange guide
        }

        // Current selection: bar showing actual total difficulty.
        int fillW = maxTotal <= 0 ? 0 : (meterW * total / maxTotal);
        if (fillW > 0) {
            boolean validIncrement = total > 0 && total % DIFFICULTY_POINTS_PER_BAR == 0;
            int barColor = validIncrement ? 0xFF55DD55 : 0xFFFF5555;   // green when valid 10‑step, red otherwise
            int tipColor = validIncrement ? 0xFF33FF33 : 0xFFFF3333;
            guiGraphics.fill(meterX, meterY, meterX + fillW, meterY + DIFFICULTY_BAR_HEIGHT, barColor);
            guiGraphics.fill(meterX + fillW - 2, meterY - 1, meterX + fillW + 2, meterY + DIFFICULTY_BAR_HEIGHT + 1, tipColor);
        }
        EscText.drawString(guiGraphics, font, total + " / " + maxTotal + " pts", meterX + meterW + 4, meterY + 2, 0xCCCCCC, false, EscFonts.DEFAULT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listAreaRect == null) return super.mouseClicked(mouseX, mouseY, button);
        int visibleRows = getListVisibleRows();
        int listBottom = getListBottom();
        int trackHeight = listAreaRect.height();
        int listSize = filteredIndices.size();
        int listPanelLeft = listPanelLeft();
        int listLeft = listLeft();
        int listWidth = listWidth();
        int listTop = listTop();
        // Click on scrollbar track: jump to that position
        if (mouseX >= listPanelLeft && mouseX < listPanelLeft + SCROLLBAR_WIDTH && mouseY >= listTop && mouseY < listBottom && button == 0) {
            int maxScroll = Math.max(0, listSize - visibleRows);
            if (maxScroll > 0)
                scrollOffset = (int) ((mouseY - listTop) * maxScroll / (trackHeight - 1));
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            return true;
        }
        if (mouseX >= listLeft && mouseX <= listLeft + listWidth && mouseY >= listTop && mouseY < listBottom) {
            int row = (int) ((mouseY - listTop) / ROW_HEIGHT);
            if (row >= visibleRows) return super.mouseClicked(mouseX, mouseY, button);
            int idx = scrollOffset + row;
            if (idx >= 0 && idx < listSize) {
                int catalogIdx = filteredIndices.get(idx);
                int qty = orderQuantities.getOrDefault(catalogIdx, 0);
                int pts = catalog.get(catalogIdx).getDifficultyPoints();
                int maxTotal = AirdropCatalog.getMaxTotalDifficulty();
                if (button == 0) {
                    if (getTotalDifficulty() + pts <= maxTotal)
                        orderQuantities.put(catalogIdx, Math.min(64, qty + 1));
                } else if (button == 1) orderQuantities.put(catalogIdx, Math.max(0, qty - 1));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollAmount) {
        if (listAreaRect == null) return super.mouseScrolled(mouseX, mouseY, scrollAmount);
        int listBottom = getListBottom();
        int listPanelLeft = listPanelLeft();
        int listTop = listTop();
        boolean overList = mouseX >= listPanelLeft && mouseX <= listPanelLeft + listAreaRect.width()
            && mouseY >= listTop && mouseY < listBottom;
        if (overList) {
            int visibleRows = getListVisibleRows();
            int maxScroll = Math.max(0, filteredIndices.size() - visibleRows);
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollAmount);
    }

    public boolean isPauseScreen() { return false; }

    private static final class StationOption {
        final String displayName;
        final String stationId;
        StationOption(String displayName, String stationId) {
            this.displayName = displayName;
            this.stationId = stationId;
        }
    }

    @SuppressWarnings("unchecked")
    private List<StationOption> getDynamicStations() {
        if (!ModList.get().isLoaded("dead_air")) return List.of();
        try {
            Class<?> registry = Class.forName("uk.co.extraspecialstudio.dead_air.radio.StationRegistry");
            java.lang.reflect.Method m = registry.getMethod("getAllStations");
            Object result = m.invoke(null);
            if (!(result instanceof java.util.Collection<?> stations)) return List.of();
            List<StationOption> out = new java.util.ArrayList<>();
            for (Object st : stations) {
                if (st == null) continue;
                java.lang.reflect.Method getId = st.getClass().getMethod("getId");
                java.lang.reflect.Method getName = st.getClass().getMethod("getName");
                String id = String.valueOf(getId.invoke(st));
                if (NON_DYNAMIC_STATION_IDS.contains(id)) continue;
                String name = String.valueOf(getName.invoke(st));
                out.add(new StationOption(name, id));
            }
            out.sort(java.util.Comparator.comparing((StationOption s) -> s.displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(s -> s.stationId, String.CASE_INSENSITIVE_ORDER));
            return out;
        } catch (Throwable ignored) {
            return List.of();
        }
    }
}
