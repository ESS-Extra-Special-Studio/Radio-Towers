package net.mcreator.radiotowers.integration;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of {@link ZombieWavesAPILoader} defense-session state (server -> client packet).
 * Used by mixins that must cancel on the logical client (e.g. NMS chat from TacZ) as well as the server.
 */
public final class DefenseSessionClientMirror {

    private static final Map<ResourceKey<Level>, Boolean> BY_DIM = new ConcurrentHashMap<>();

    public static void set(ResourceKey<Level> dimension, boolean active) {
        if (dimension == null) return;
        if (active) {
            BY_DIM.put(dimension, Boolean.TRUE);
        } else {
            BY_DIM.remove(dimension);
        }
    }

    public static boolean isActive(ResourceKey<Level> dimension) {
        if (dimension == null) return false;
        return BY_DIM.getOrDefault(dimension, false);
    }
}
