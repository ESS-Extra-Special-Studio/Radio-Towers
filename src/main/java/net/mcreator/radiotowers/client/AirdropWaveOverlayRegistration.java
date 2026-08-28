package net.mcreator.radiotowers.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers the airdrop wave HUD overlay (shown only during an active airdrop).
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AirdropWaveOverlayRegistration {

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("radiotowers_airdrop_wave",
            (gui, guiGraphics, partialTick, screenWidth, screenHeight) ->
                AirdropWaveOverlay.render(guiGraphics, screenWidth, screenHeight)
        );
    }
}
