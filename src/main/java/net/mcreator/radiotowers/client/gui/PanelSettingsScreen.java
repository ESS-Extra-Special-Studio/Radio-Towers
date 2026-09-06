package net.mcreator.radiotowers.client.gui;
import net.mcreator.radiotowers.network.RadiotowersNetwork;

import net.mcreator.radiotowers.integration.DeadAirDefaultStations;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.network.ActivatePanelWithStationMessage;
import net.mcreator.radiotowers.network.CallAirdropMessage;
import net.mcreator.radiotowers.network.DeactivatePanelMessage;
import net.mcreator.radiotowers.network.RequestAirdropStatePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscAnchor;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscInsets;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscLayoutSpec;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscPanel;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscScreen;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscText;

import net.neoforged.fml.ModList;

import java.util.Collections;
import java.util.List;

/**
 * Radio panel when Dead Air is present but Zombie Waves API is not: standard airdrop lever (loot table),
 * 3-minute cooldown, and the same Dead Air station controls as the full Call Airdrop screen (no catalog).
 */
public class PanelSettingsScreen extends EscScreen implements InternetStationParent {

    private static final int LEVER_WIDTH = 44;
    private static final int LEVER_HEIGHT = 36;
    private static final int BUTTON_WIDTH = 120;

    private final BlockPos panelPos;

    /** Refreshed from {@link DeadAirDefaultStations} (Jukebox FM only when upgrade is installed on this panel). */
    private List<StationOption> defaultStationsCache = List.of();
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
    private String currentStationId = null;
    private int activeButton = -1;
    private Button defaultStationButton;
    private Button dynamicStationButton;
    private Button internetButton;

    private long lastToggleSentAt = 0L;
    private String lastStationIdBeforeTurnOff = null;
    private Button turnOffButton;

    private EscRect bodyRect;
    private EscRect hintLine1;
    private EscRect hintLine2;

    public PanelSettingsScreen(BlockPos panelPos) {
        super(Component.translatable("screen.radiotowers.panel_settings.title"));
        this.panelPos = panelPos;
    }

    @Override
    protected void buildLayout() {
        EscRect content = contentRect();
        bodyRect = EscPanel.bodyBelowTitle(content, style);
        int hintsTop = bodyRect.y() + Math.max(LEVER_HEIGHT + 28, bodyRect.height() / 2);
        hintLine1 = new EscRect(bodyRect.x() + 12, hintsTop, bodyRect.width() - 24, 22);
        hintLine2 = new EscRect(bodyRect.x() + 12, hintLine1.bottom() + 2, bodyRect.width() - 24, 22);

        RadiotowersNetwork.sendToServer(new RequestAirdropStatePacket(panelPos));
        refreshDefaultStationsCache();
        fetchCurrentStationFromDeadAir();

        EscRect leverBounds = resolve(bodyRect, EscLayoutSpec.of(EscAnchor.TOP_CENTER, 0, 0, LEVER_WIDTH, LEVER_HEIGHT));
        AirdropLeverWidget lever = new AirdropLeverWidget(0, 0, LEVER_WIDTH, LEVER_HEIGHT, this::onCallStandardAirdrop,
            () -> AirdropConfig.ENABLE_STANDARD_AIRDROP.get());
        layoutWidget(lever, leverBounds);
        addRenderableWidget(lever);

        int y = leverBounds.bottom() + 14 - bodyRect.y();
        if (ModList.get().isLoaded("dead_air")) {
            turnOffButton = addAnchoredButton(EscText.literal(getPowerButtonLabel()), bodyRect,
                EscLayoutSpec.of(EscAnchor.TOP_CENTER, 0, y, BUTTON_WIDTH, 20), b -> onTurnOff());
            y += 24;
            defaultStationButton = addAnchoredButton(EscText.literal(defaultLabel()), bodyRect,
                EscLayoutSpec.of(EscAnchor.TOP_CENTER, 0, y, BUTTON_WIDTH, 20), b -> onDefaultCycle());
            y += 24;
            dynamicStationButton = addAnchoredButton(EscText.literal(dynamicLabel()), bodyRect,
                EscLayoutSpec.of(EscAnchor.TOP_CENTER, 0, y, BUTTON_WIDTH, 20), b -> onDynamicCycle());
            y += 24;
            internetButton = addAnchoredButton(EscText.styled(Component.translatable("screen.radiotowers.button.internet")), bodyRect,
                EscLayoutSpec.of(EscAnchor.TOP_CENTER, 0, y, BUTTON_WIDTH, 20), b -> openInternet());
        }
    }

    private void refreshDefaultStationsCache() {
        if (!ModList.get().isLoaded("dead_air")) {
            defaultStationsCache = List.of();
            return;
        }
        java.util.ArrayList<StationOption> next = new java.util.ArrayList<>();
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
                defaultStationButton.setMessage(Component.literal(defaultLabel()));
            }
        }
    }

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
                    activeButton = 2;
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
        RadiotowersNetwork.sendToServer(new ActivatePanelWithStationMessage(panelPos, opt.stationId));
        currentStationId = opt.stationId;
        lastStationIdBeforeTurnOff = opt.stationId;
        activeButton = 0;
        defaultCycleIndex = nextIndex;
        if (defaultStationButton != null) defaultStationButton.setMessage(Component.literal(defaultLabel()));
        if (turnOffButton != null) turnOffButton.setMessage(Component.literal(getPowerButtonLabel()));
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
        RadiotowersNetwork.sendToServer(new ActivatePanelWithStationMessage(panelPos, opt.stationId));
        currentStationId = opt.stationId;
        lastStationIdBeforeTurnOff = opt.stationId;
        activeButton = 1;
        lastDynamicName = opt.displayName;
        dynamicCycleIndex = nextIndex;
        if (dynamicStationButton != null) dynamicStationButton.setMessage(Component.literal(dynamicLabel()));
        if (turnOffButton != null) turnOffButton.setMessage(Component.literal(getPowerButtonLabel()));
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
        if (turnOffButton != null) turnOffButton.setMessage(Component.literal(getPowerButtonLabel()));
    }

    private void onCallStandardAirdrop() {
        RadiotowersNetwork.sendToServer(
            new CallAirdropMessage(panelPos, 0, Collections.emptyList(), Collections.emptyList()));
        // Stay open so the lever shows cooldown if the server rejects or after success (Esc to close).
    }

    private void onTurnOff() {
        long now = System.currentTimeMillis();
        if (now - lastToggleSentAt < 250) return;
        lastToggleSentAt = now;
        if (currentStationId != null && !currentStationId.isEmpty()) {
            lastStationIdBeforeTurnOff = currentStationId;
            RadiotowersNetwork.sendToServer(new DeactivatePanelMessage(panelPos));
            currentStationId = null;
            activeButton = -1;
            lastDynamicName = null;
        } else {
            String stationToRestore = (lastStationIdBeforeTurnOff != null && !lastStationIdBeforeTurnOff.isEmpty())
                ? lastStationIdBeforeTurnOff
                : (defaultStationsCache.isEmpty() ? "dead_air:bedrock_radio" : defaultStationsCache.get(
                    Math.min(defaultCycleIndex, defaultStationsCache.size() - 1)).stationId);
            RadiotowersNetwork.sendToServer(new ActivatePanelWithStationMessage(panelPos, stationToRestore));
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
        if (defaultStationButton != null) defaultStationButton.setMessage(Component.literal(defaultLabel()));
        if (dynamicStationButton != null) dynamicStationButton.setMessage(Component.literal(dynamicLabel()));
        if (turnOffButton != null) turnOffButton.setMessage(Component.literal(getPowerButtonLabel()));
    }

    private String getPowerButtonLabel() {
        return (currentStationId == null || currentStationId.isEmpty())
            ? Component.translatable("screen.radiotowers.button.turn_on").getString()
            : Component.translatable("screen.radiotowers.button.turn_off").getString();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        EscRect content = contentRect();
        EscPanel.renderPanel(guiGraphics, content, style);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        EscText.drawInRect(guiGraphics, font, title, EscPanel.titleBar(content, style),
            0xE0E0E0, EscFonts.DEFAULT, false, 0.5f);

        if (hintLine1 != null && hintLine2 != null) {
            EscText.drawInRect(guiGraphics, font, Component.literal("Pull the lever for a standard airdrop (loot crate)."),
                hintLine1, 0x88CCFF, EscFonts.DEFAULT, false, 0.5f);
            int cooldownMin = AirdropConfig.STANDARD_AIRDROP_COOLDOWN_MINUTES.get();
            String cooldownHint = cooldownMin <= 0
                ? "No cooldown between calls."
                : cooldownMin == 1
                    ? "1 minute cooldown between calls."
                    : cooldownMin + " minute cooldown between calls.";
            EscText.drawInRect(guiGraphics, font, Component.literal(cooldownHint),
                hintLine2, 0xA0A0A0, EscFonts.DEFAULT, false, 0.5f);
        }

        if (ModList.get().isLoaded("dead_air") && activeButton >= 0) {
            Button active = activeButton == 0 ? defaultStationButton : (activeButton == 1 ? dynamicStationButton : internetButton);
            if (active != null) {
                int bx = active.getX();
                int by = active.getY();
                int bw = active.getWidth();
                int bh = active.getHeight();
                int c = 0xFF22AA22;
                guiGraphics.fill(bx, by, bx + bw, by + 2, c);
                guiGraphics.fill(bx, by + bh - 2, bx + bw, by + bh, c);
                guiGraphics.fill(bx, by, bx + 2, by + bh, c);
                guiGraphics.fill(bx + bw - 2, by, bx + bw, by + bh, c);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

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
