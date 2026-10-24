package net.mcreator.radiotowers.integration;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

/**
 * Holds level/position context for the ZombieEntity Waves API mixin.
 * Set by ZombieWavesAPILoader before calling the API; the mixin injects this level
 * into the API's WavesManager so the wave runs in the correct dimension.
 */
public final class ZombieWavesMixinContext {

    private static ServerWorld pendingLevel;
    private static BlockPos pendingPos;
    private static ServerWorld currentLevel;

    public static void setPending(ServerWorld level, BlockPos pos) {
        pendingLevel = level;
        pendingPos = pos;
    }

    public static ServerWorld getAndClearPendingLevel() {
        ServerWorld level = pendingLevel;
        pendingLevel = null;
        pendingPos = null;
        currentLevel = level;
        return level;
    }

    public static ServerWorld getCurrentLevel() {
        return currentLevel;
    }

    public static void clearCurrentLevel() {
        currentLevel = null;
    }

    public static boolean hasPending() {
        return pendingLevel != null;
    }

    public static ServerWorld getBerezkaCurWorld() {
        try {
            java.util.Optional opt = net.minecraftforge.fml.ModList.get().getModContainerById("berezka_api");
            if (!opt.isPresent()) return null;
            Class<?> main = Class.forName("org.berezka.berezka_api.berezka_api_main", true, opt.get().getClass().getClassLoader());
            java.lang.reflect.Field f = main.getField("curWorld");
            Object w = f.get(null);
            return w instanceof ServerWorld ? (ServerWorld) w : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static String describeContext() {
        return "pending=" + (pendingLevel != null ? pendingLevel.dimension().location().toString() : "null")
            + " current=" + (currentLevel != null ? currentLevel.dimension().location().toString() : "null");
    }
}
