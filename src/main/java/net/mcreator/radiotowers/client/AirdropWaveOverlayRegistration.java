package net.mcreator.radiotowers.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import uk.co.extraspecialstudio.extraspecial.esc.ui.EscGraphics;

/**
 * Draws the airdrop wave HUD overlay (shown only during an active airdrop).
 * 1.18.2 has no RegisterGuiOverlaysEvent.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class AirdropWaveOverlayRegistration {

    @SubscribeEvent
    public static void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getInstance();
        AirdropWaveOverlay.render(new EscGraphics(event.getMatrixStack()),
            mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }
}
