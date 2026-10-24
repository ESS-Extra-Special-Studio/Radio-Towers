package net.mcreator.radiotowers.integration;

import net.minecraft.entity.player.ServerPlayerEntity;
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
    public static boolean isBossModeUnlocked(ServerPlayerEntity player) {
        return invokeBool(player, "isBossModeUnlocked");
    }

    /** Defeated Listener → Endless Mode tab unlock. */
    public static boolean isEndlessModeUnlocked(ServerPlayerEntity player) {
        return invokeBool(player, "isListenerDefeated");
    }

    private static boolean invokeBool(ServerPlayerEntity player, String method) {
        if (player == null || !isDeadAirLoaded()) return false;
        try {
            ClassLoader cl = ModList.get().getModContainerById(DEAD_AIR_MOD_ID)
                .map(c -> c.getClass().getClassLoader())
                .orElse(DeadAirUnlockBridge.class.getClassLoader());
            Class<?> api = Class.forName(API_CLASS, true, cl);
            Object result = api.getMethod(method, ServerPlayerEntity.class).invoke(null, player);
            Boolean b = (result instanceof Boolean) ? (Boolean) result : null;
            return b != null && b;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
