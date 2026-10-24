package net.mcreator.radiotowers.integration;

import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of {@link ZombieWavesAPILoader} defense-session state (server -> client packet).
 * Used by mixins that must cancel on the logical client (e.g. NMS chat from TacZ) as well as the server.
 */
public final class DefenseSessionClientMirror {

    private static final Map<RegistryKey<World>, Boolean> BY_DIM = new ConcurrentHashMap<>();

    public static void set(RegistryKey<World> dimension, boolean active) {
        if (dimension == null) return;
        if (active) {
            BY_DIM.put(dimension, Boolean.TRUE);
        } else {
            BY_DIM.remove(dimension);
        }
    }

    public static boolean isActive(RegistryKey<World> dimension) {
        if (dimension == null) return false;
        return BY_DIM.getOrDefault(dimension, false);
    }
}
