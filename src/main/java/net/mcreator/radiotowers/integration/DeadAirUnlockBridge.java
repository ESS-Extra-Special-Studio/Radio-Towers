package net.mcreator.radiotowers.integration;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Soft dependency on Dead Air Listener unlock flags for Boss / Endless airdrop tabs.
 * Missing Dead Air → both locked (no crash).
 */
public final class DeadAirUnlockBridge {

    private static final String DEAD_AIR_MOD_ID = "dead_air";
    private static final String API_CLASS = "uk.co.extraspecialstudio.dead_air.api.DeadAirAPI";

    private DeadAirUnlockBridge() {}

    public static boolean isDeadAirLoaded() {
        return ModList.get().isLoaded(DEAD_AIR_MOD_ID);
    }

    /** Survived first Listener encounter → Boss Mode tab unlock. */
    public static boolean isBossModeUnlocked(ServerPlayer player) {
        return invokeBool(player, "isBossModeUnlocked");
    }

    /** Defeated Listener → Endless Mode tab unlock. */
    public static boolean isEndlessModeUnlocked(ServerPlayer player) {
        return invokeBool(player, "isListenerDefeated");
    }

    private static boolean invokeBool(ServerPlayer player, String method) {
        if (player == null || !isDeadAirLoaded()) return false;
        try {
            ClassLoader cl = ModList.get().getModContainerById(DEAD_AIR_MOD_ID)
                .map(c -> c.getMod().getClass().getClassLoader())
                .orElse(DeadAirUnlockBridge.class.getClassLoader());
            Class<?> api = Class.forName(API_CLASS, true, cl);
            Object result = api.getMethod(method, ServerPlayer.class).invoke(null, player);
            return result instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
