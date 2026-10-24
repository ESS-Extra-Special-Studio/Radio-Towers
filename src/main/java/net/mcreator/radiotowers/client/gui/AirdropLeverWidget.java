package net.mcreator.radiotowers.client.gui;
import net.minecraft.network.chat.TranslatableComponent;

import net.mcreator.radiotowers.client.AirdropWaveHudData;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;

/**
 * Textured lever for calling an airdrop. Uses {@link AirdropWaveHudData} for wave/cooldown (server-synced).
 */
public class AirdropLeverWidget extends AbstractWidget {

    private static final ResourceLocation TEX_OFF = new ResourceLocation("radiotowers", "textures/gui/airdrop_switch_off.png");
    private static final ResourceLocation TEX_ON = new ResourceLocation("radiotowers", "textures/gui/airdrop_switch_on.png");

    private final Runnable onActivate;
    /** Extra gate (e.g. catalog needs 10+ points); standard airdrop uses {@code () -> true}. */
    private final BooleanSupplier extraCanActivate;

    public AirdropLeverWidget(int x, int y, int w, int h, Runnable onActivate, BooleanSupplier extraCanActivate) {
        super(x, y, w, h, new TranslatableComponent("screen.radiotowers.call_airdrop.title"));
        this.onActivate = onActivate;
        this.extraCanActivate = extraCanActivate;
    }

    private static boolean cooldownClear() {
        if (AirdropConfig.getStandardAirdropCooldownTicks() == 0
                && AirdropConfig.getWaveDeliveryCooldownTicks() == 0) {
            return true;
        }
        long end = AirdropWaveHudData.getCooldownEndGameTime();
        if (end <= 0) return true;
        var level = Minecraft.getInstance().level;
        return level != null && level.getGameTime() >= end;
    }

    public boolean canActivateLever() {
        if (AirdropWaveHudData.isWaveInProgress()) return false;
        if (!cooldownClear()) return false;
        return extraCanActivate.getAsBoolean();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (canActivateLever()) onActivate.run();
    }

    @Override
    public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        EscGraphics guiGraphics = new EscGraphics(pose);
        int x = this.x;
        int y = this.y;
        int w = this.width;
        int h = this.height;
        long cdEnd = AirdropWaveHudData.getCooldownEndGameTime();
        boolean on = AirdropWaveHudData.isWaveInProgress()
            || (cdEnd > 0 && Minecraft.getInstance().level != null && Minecraft.getInstance().level.getGameTime() < cdEnd);
        ResourceLocation tex = on ? TEX_ON : TEX_OFF;
        guiGraphics.blit(tex, x, y, 0, 0, w, h, w, h);
        if (AirdropWaveHudData.isWaveInProgress() && AirdropWaveHudData.getTotalWaves() > 0) {
            int secRem = AirdropWaveHudData.getSecondsRemaining();
            int min = secRem / 60;
            int sec = secRem % 60;
            int z = AirdropWaveHudData.getZombiesRemaining();
            String zombiesStr = z >= 0 ? (z + " zombies") : "?";
            String status = String.format("Wave %d/%d | %s | %d:%02d",
                AirdropWaveHudData.getCurrentWave(), AirdropWaveHudData.getTotalWaves(), zombiesStr, min, sec);
            guiGraphics.drawString(Minecraft.getInstance().font, status, x, y + h + 2, 0xDDDD00, false);
        } else if (!canActivateLever() && cdEnd > 0
                && !(AirdropConfig.getStandardAirdropCooldownTicks() == 0
                    && AirdropConfig.getWaveDeliveryCooldownTicks() == 0)) {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                long remaining = cdEnd - level.getGameTime();
                if (remaining > 0) {
                    int s = (int) (remaining / 20);
                    int m = s / 60;
                    s = s % 60;
                    String time = String.format("Use again in %d:%02d", m, s);
                    guiGraphics.drawString(Minecraft.getInstance().font, time, x, y + h + 2, 0xCC8800, false);
                }
            }
        }
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
