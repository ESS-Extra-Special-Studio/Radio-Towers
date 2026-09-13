package net.mcreator.radiotowers;

import net.minecraft.core.BlockPos;
import net.mcreator.radiotowers.init.RadiotowersModMenus;

/**
 * Invokes client-only code without static references to client classes, so dedicated servers
 * do not link {@code net.minecraft.client} or GUI types when loading common classes.
 */
public final class SafeClientCalls {

	private static final String HOOKS = "net.mcreator.radiotowers.client.RadiotowersClientHooks";

	private SafeClientCalls() {
	}

	public static void openRadioPanel(BlockPos pos) {
		invokeVoid("openRadioPanel", new Class<?>[] { BlockPos.class }, new Object[] { pos });
	}

	public static void applyWaveStatePacket(int currentWave, int totalWaves, int zombiesRemaining, int secondsRemaining, boolean nmsSuppressActive) {
		invokeVoid("applyWaveStatePacket",
			new Class<?>[] { int.class, int.class, int.class, int.class, boolean.class },
			new Object[] { currentWave, totalWaves, zombiesRemaining, secondsRemaining, nmsSuppressActive });
	}

	public static void applyAirdropStatePacket(boolean waveInProgress, long cooldownEndGameTime) {
		invokeVoid("applyAirdropStatePacket", new Class<?>[] { boolean.class, long.class }, new Object[] { waveInProgress, cooldownEndGameTime });
	}

	public static void applyLobbySync(Object syncPacket) {
		invokeVoid("applyLobbySync", new Class<?>[] { Object.class }, new Object[] { syncPacket });
	}

	public static void applyLobbyCountdown(Object countdownPacket) {
		invokeVoid("applyLobbyCountdown", new Class<?>[] { Object.class }, new Object[] { countdownPacket });
	}

	public static void openLobbyScreen(java.util.UUID lobbyId, BlockPos panelPos) {
		invokeVoid("openLobbyScreen", new Class<?>[] { java.util.UUID.class, BlockPos.class }, new Object[] { lobbyId, panelPos });
	}

	public static void onClientSendMenuStateUpdate(RadiotowersModMenus.MenuAccessor menu, int elementType, String name, Object elementState, boolean needClientUpdate) {
		invokeVoid("onClientSendMenuStateUpdate",
			new Class<?>[] { RadiotowersModMenus.MenuAccessor.class, int.class, String.class, Object.class, boolean.class },
			new Object[] { menu, elementType, name, elementState, needClientUpdate });
	}

	public static void applyMenuStateUpdateScreenSync(RadiotowersModMenus.MenuAccessor menu, int elementType, String name, Object elementState) {
		invokeVoid("applyMenuStateUpdateFromServer",
			new Class<?>[] { RadiotowersModMenus.MenuAccessor.class, int.class, String.class, Object.class },
			new Object[] { menu, elementType, name, elementState });
	}

	private static void invokeVoid(String method, Class<?>[] paramTypes, Object[] args) {
		try {
			Class<?> hooks = Class.forName(HOOKS);
			hooks.getMethod(method, paramTypes).invoke(null, args);
		} catch (Throwable t) {
			RadiotowersMod.LOGGER.error("SafeClientCalls.{} failed", method, t);
		}
	}
}
