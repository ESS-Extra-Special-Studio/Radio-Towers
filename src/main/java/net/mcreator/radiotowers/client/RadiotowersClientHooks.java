package net.mcreator.radiotowers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.ModList;
import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.client.gui.CallAirdropScreen;
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
			RadiotowersMod.LOGGER.warn("[RadioPanel] openRadioPanel blocked — screen already open: {}",
				Minecraft.getInstance().screen.getClass().getName());
			return;
		}
		openCallAirdrop(pos);
	}

	/** OP/debug: close any overlay then open Call Airdrop (Boss/Endless tabs visible). */
	public static void forceOpenCallAirdrop(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null) {
			mc.setScreen(null);
		}
		openCallAirdrop(pos != null ? pos : BlockPos.ZERO);
	}

	private static void openCallAirdrop(BlockPos pos) {
		boolean berezka = ZombieWavesAPILoader.isZombieWavesAPILoaded();
		boolean esl = net.mcreator.radiotowers.integration.EslWaveIntegration.isEslWaveAvailable();
		boolean wavesApi = berezka || esl;
		boolean deadAir = ModList.get().isLoaded(DEAD_AIR_MODID);
		RadiotowersMod.LOGGER.warn("[RadioPanel] openRadioPanel: berezka={} esl={} wavesApi={} deadAir={}", berezka, esl, wavesApi, deadAir);
		// Always CallAirdropScreen: catalog airdrop + (with Dead Air) Boss/Endless unlock tabs.
		// Do not route Dead-Air-only packs to PanelSettingsScreen — that UI has no Boss tab,
		// so Listener first-survive unlocks never appeared on the airdrop screen.
		Minecraft.getInstance().setScreen(new CallAirdropScreen(pos));
	}

	public static void applyWaveStatePacket(int currentWave, int totalWaves, int zombiesRemaining, int secondsRemaining, boolean nmsSuppressActive) {
		AirdropWaveHudData.setWaveState(currentWave, totalWaves, zombiesRemaining, secondsRemaining, nmsSuppressActive);
	}

	public static void applyAirdropStatePacket(boolean waveInProgress, long cooldownEndGameTime) {
		AirdropWaveHudData.setAirdropState(waveInProgress, cooldownEndGameTime);
	}

	public static void applyAirdropModeUnlockPacket(boolean bossModeUnlocked, boolean endlessModeUnlocked) {
		AirdropWaveHudData.setModeUnlocks(bossModeUnlocked, endlessModeUnlocked);
	}

	public static void applyLobbySync(Object syncPacket) {
		if (syncPacket instanceof net.mcreator.radiotowers.network.lobby.LobbyPackets.SyncLobby sync) {
			net.mcreator.radiotowers.client.lobby.AirdropLobbyClient.applySync(sync);
		}
	}

	public static void applyLobbyCountdown(Object countdownPacket) {
		if (countdownPacket instanceof net.mcreator.radiotowers.network.lobby.LobbyPackets.Countdown countdown) {
			net.mcreator.radiotowers.client.lobby.AirdropLobbyClient.applyCountdown(countdown);
		}
	}

	public static void openLobbyScreen(java.util.UUID lobbyId, BlockPos panelPos) {
		net.mcreator.radiotowers.client.lobby.AirdropLobbyClient.openScreen(lobbyId, panelPos);
	}

	public static void applyLobbyClosed(Object closedPacket) {
		if (closedPacket instanceof net.mcreator.radiotowers.network.lobby.LobbyPackets.Closed closed) {
			net.mcreator.radiotowers.client.lobby.AirdropLobbyClient.applyClosed(closed);
		}
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
