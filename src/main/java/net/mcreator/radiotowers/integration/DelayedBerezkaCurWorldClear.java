package net.mcreator.radiotowers.integration;

import net.neoforged.fml.ModList;

/**
 * Runs later (scheduled from {@link ZombieWavesAPILoader}) to clear berezka_api curWorld.
 * Isolated so no method reference from ZombieWavesAPILoader is used in the work queue,
 * avoiding any CONSTRUCT-phase class-load side effects.
 */
public final class DelayedBerezkaCurWorldClear {

    private static final String BEREZKA_MAIN_CLASS = "org.berezka.berezka_api.berezka_api_main";
    private static final String BEREZKA_API_MOD_ID = "berezka_api";

    /** Called by RadiotowersMod work queue after a short delay so WavesManager.onServerTick can run first. */
    public static void run() {
        var opt = ModList.get().getModContainerById(BEREZKA_API_MOD_ID);
        if (opt.isEmpty()) return;
        try {
            ClassLoader loader = net.mcreator.radiotowers.RadiotowersMod.class.getClassLoader();
            Class<?> main = Class.forName(BEREZKA_MAIN_CLASS, true, loader);
            main.getField("curWorld").set(null, null);
        } catch (Throwable t) {
            // ignore
        }
    }
}
