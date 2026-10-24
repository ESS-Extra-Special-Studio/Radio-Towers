package net.mcreator.radiotowers.client.gui;
import net.minecraft.util.text.TranslationTextComponent;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.network.ActivatePanelWithStationMessage;
import com.mojang.blaze3d.matrix.MatrixStack;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscGraphics;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscAnchor;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscInsets;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscLayoutSpec;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscPanel;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscScreen;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscText;

/**
 * Choose a Creatopia 24/7 live MP3 stream for this panel (Dead Air plays it in-game when the player is in range and tuned).
 */
public class InternetStationScreen extends EscScreen {

    public static final String STATION_CR1 = "dead_air:internet_cr1";
    public static final String STATION_AMBIANCE_FM = "dead_air:internet_ambiance_fm";
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final BlockPos panelPos;
    private final InternetStationParent stationParent;

    public InternetStationScreen(Screen parent, BlockPos panelPos) {
        super(new TranslationTextComponent("screen.radiotowers.internet.title"));
        this.parent = parent;
        this.panelPos = panelPos;
        this.stationParent = parent instanceof InternetStationParent ? (InternetStationParent) parent : null;
    }

    @Override
    protected void buildLayout() {
        EscRect content = contentRect();
        EscRect body = EscPanel.bodyBelowTitle(content, style);
        addAnchoredButton(new TranslationTextComponent("screen.radiotowers.internet.station.cr1"), body,
            EscLayoutSpec.of(EscAnchor.CENTER, 0, -10, BUTTON_WIDTH, BUTTON_HEIGHT), b -> choose(STATION_CR1));
        addAnchoredButton(new TranslationTextComponent("screen.radiotowers.internet.station.ambiance_fm"), body,
            EscLayoutSpec.of(EscAnchor.CENTER, 0, 14, BUTTON_WIDTH, BUTTON_HEIGHT), b -> choose(STATION_AMBIANCE_FM));
        addAnchoredButton(new TranslationTextComponent("screen.radiotowers.internet.back"), body,
            EscLayoutSpec.of(EscAnchor.CENTER, 0, 42, BUTTON_WIDTH, BUTTON_HEIGHT), b -> onClose());
    }

    private void choose(String stationId) {
        RadiotowersMod.PACKET_HANDLER.sendToServer(new ActivatePanelWithStationMessage(panelPos, stationId));
        if (stationParent != null) {
            stationParent.onInternetStreamChosen(stationId);
        }
        onClose();
    }

    @Override
    public void render(MatrixStack pose, int mouseX, int mouseY, float partialTick) {
        EscGraphics guiGraphics = new EscGraphics(pose);
        renderBackground(pose);
        EscRect content = contentRect();
        EscPanel.renderPanel(guiGraphics, content, style);
        super.render(pose, mouseX, mouseY, partialTick);

        EscRect introBand = resolve(content, EscLayoutSpec.stretchH(style.titleGap(), 52, EscInsets.of(12, 0)));
        EscRect[] lines = introBand.splitRows(new float[]{1f, 1f, 1f, 1f}, 2);
        if (lines.length >= 4) {
            EscText.drawInRect(guiGraphics, font, new TranslationTextComponent("screen.radiotowers.internet.line1"),
                lines[0], 0xE0E0E0, EscFonts.DEFAULT, false, 0.5f);
            EscText.drawInRect(guiGraphics, font, new TranslationTextComponent("screen.radiotowers.internet.line2"),
                lines[1], 0xB0B0B0, EscFonts.DEFAULT, false, 0.5f);
            EscText.drawInRect(guiGraphics, font, new TranslationTextComponent("screen.radiotowers.internet.line3"),
                lines[2], 0xB0B0B0, EscFonts.DEFAULT, false, 0.5f);
            EscText.drawInRect(guiGraphics, font, new TranslationTextComponent("screen.radiotowers.internet.line4"),
                lines[3], 0x888888, EscFonts.DEFAULT, false, 0.5f);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
