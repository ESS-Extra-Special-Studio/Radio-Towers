package net.mcreator.radiotowers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * In-game HUD overlay for airdrop wave progress. Shown only during an active airdrop wave.
 * Positioned just below where Dead Air draws its walkie info (default top-right: 120x58 panel at pad 2,2).
 */
public final class AirdropWaveOverlay {

    private static final int PANEL_WIDTH = 140;
    private static final int PANEL_HEIGHT = 44;
    private static final int PAD = 2;
    /** Gap below Dead Air's default overlay (bgHeight 58 + pad 2 + gap 6). */
    private static final int OFFSET_BELOW_DEAD_AIR = 58 + PAD + 6;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOR = 0xDDDD00;
    private static final int LABEL_COLOR = 0xAAAAAA;
    private static final int BG_COLOR = 0xCC000000;

    public static void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (!AirdropWaveHudData.isWaveInProgress() || AirdropWaveHudData.getTotalWaves() <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.font == null) return;
        if (mc.screen instanceof net.mcreator.radiotowers.client.gui.CallAirdropScreen) return;

        int x = screenWidth - PANEL_WIDTH - PAD - 0;
        int y = PAD + OFFSET_BELOW_DEAD_AIR;

        guiGraphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BG_COLOR);
        int left = x + 4;
        int lineY = y + 4;

        int cw = AirdropWaveHudData.getCurrentWave();
        int tw = AirdropWaveHudData.getTotalWaves();
        guiGraphics.drawString(mc.font, "Wave " + cw + " / " + tw, left, lineY, TEXT_COLOR, false);
        lineY += LINE_HEIGHT;

        int wavesLeft = Math.max(0, tw - cw);
        guiGraphics.drawString(mc.font, "Waves left: " + wavesLeft, left, lineY, LABEL_COLOR, false);
        lineY += LINE_HEIGHT;

        int z = AirdropWaveHudData.getZombiesRemaining();
        String zombieStr = z >= 0 ? ("Zombies: " + z) : "Zombies: ?";
        guiGraphics.drawString(mc.font, zombieStr, left, lineY, LABEL_COLOR, false);
        lineY += LINE_HEIGHT;

        int sec = AirdropWaveHudData.getSecondsRemaining();
        int min = sec / 60;
        sec = sec % 60;
        guiGraphics.drawString(mc.font, String.format("Time: %d:%02d", min, sec), left, lineY, LABEL_COLOR, false);
    }
}
