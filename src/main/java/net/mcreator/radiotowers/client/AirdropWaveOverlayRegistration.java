package net.mcreator.radiotowers.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.mcreator.radiotowers.RadiotowersMod;

@EventBusSubscriber(modid = RadiotowersMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AirdropWaveOverlayRegistration {

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(RadiotowersMod.MODID, "airdrop_wave"),
            (guiGraphics, deltaTracker) -> {
                var window = net.minecraft.client.Minecraft.getInstance().getWindow();
                AirdropWaveOverlay.render(guiGraphics, window.getGuiScaledWidth(), window.getGuiScaledHeight());
            });
    }
}
