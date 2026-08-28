package net.mcreator.radiotowers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.ModList;
import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.client.gui.CallAirdropScreen;
import net.mcreator.radiotowers.client.gui.PanelSettingsScreen;
import net.mcreator.radiotowers.init.RadiotowersModMenus;
import net.mcreator.radiotowers.init.RadiotowersModScreens;
import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;
import net.mcreator.radiotowers.network.MenuStateUpdateMessage;

/**
 * Client-only entry points called via reflection from common code ({@link net.mcreator.radiotowers.SafeClientCalls}).
 */
public final class RadiotowersClientHooks {

	private static final String DEAD_AIR_MODID = "dead_air";

	private RadiotowersClientHooks() {
	}

	public static void openRadioPanel(BlockPos pos) {
		if (Minecraft.getInstance().screen != null) {
			return;
		}
		boolean berezka = ZombieWavesAPILoader.isZombieWavesAPILoaded();
		boolean esl = net.mcreator.radiotowers.integration.EslWaveIntegration.isEslWaveAvailable();
		boolean wavesApi = berezka || esl;
		boolean deadAir = ModList.get().isLoaded(DEAD_AIR_MODID);
		RadiotowersMod.LOGGER.warn("[RadioPanel] openRadioPanel: berezka={} esl={} wavesApi={} deadAir={}", berezka, esl, wavesApi, deadAir);
		if (wavesApi) {
			Minecraft.getInstance().setScreen(new CallAirdropScreen(pos));
		} else if (deadAir) {
			// Dead Air without waves: station settings + standard (loot-table) lever.
			Minecraft.getInstance().setScreen(new PanelSettingsScreen(pos));
		} else {
			// RadioTowers alone (no Dead Air, no waves API): still open the catalog airdrop UI.
			// Previously this path auto-sent CallAirdropMessage(empty) → loot-table crate with
			// random forge-tag junk and no player selection — broken "standalone airdrop" support.
			Minecraft.getInstance().setScreen(new CallAirdropScreen(pos));
		}
	}

	public static void applyWaveStatePacket(int currentWave, int totalWaves, int zombiesRemaining, int secondsRemaining, boolean nmsSuppressActive) {
		AirdropWaveHudData.setWaveState(currentWave, totalWaves, zombiesRemaining, secondsRemaining, nmsSuppressActive);
	}

	public static void applyAirdropStatePacket(boolean waveInProgress, long cooldownEndGameTime) {
		AirdropWaveHudData.setAirdropState(waveInProgress, cooldownEndGameTime);
	}

	public static void onClientSendMenuStateUpdate(RadiotowersModMenus.MenuAccessor menu, int elementType, String name, Object elementState, boolean needClientUpdate) {
		if (Minecraft.getInstance().screen instanceof RadiotowersModScreens.ScreenAccessor accessor && needClientUpdate) {
			accessor.updateMenuState(elementType, name, elementState);
		}
		RadiotowersMod.PACKET_HANDLER.sendToServer(new MenuStateUpdateMessage(elementType, name, elementState));
	}

	public static void applyMenuStateUpdateFromServer(RadiotowersModMenus.MenuAccessor menu, int elementType, String name, Object elementState) {
		if (Minecraft.getInstance().screen instanceof RadiotowersModScreens.ScreenAccessor accessor) {
			accessor.updateMenuState(elementType, name, elementState);
		}
	}
}
