package net.mcreator.radiotowers.worldgen;

import net.mcreator.radiotowers.config.AirdropConfig;

import java.util.Set;

/**
 * Scales RadioTowers {@code random_spread} spacing/separation from config.
 * <p>
 * All tower types share one structure set so separation applies between any towers.
 * {@code towerSpawnDensityPercent}: 100 = datapack defaults; higher = denser.
 */
public final class TowerSpawnDensity {
    public static final int DATAPACK_BASELINE_PERCENT = 100;

    /** Salt for the combined radiotowers structure set. */
    private static final Set<Integer> RADIO_TOWER_SALTS = Set.of(689686682);

    private TowerSpawnDensity() {}

    public static boolean isRadioTowerPlacement(int salt) {
        return RADIO_TOWER_SALTS.contains(salt);
    }

    public static int densityPercent() {
        try {
            return Math.max(50, Math.min(200, AirdropConfig.TOWER_SPAWN_DENSITY_PERCENT.get()));
        } catch (Throwable t) {
            return DATAPACK_BASELINE_PERCENT;
        }
    }

    public static int scale(int datapackValue) {
        int percent = densityPercent();
        if (percent == DATAPACK_BASELINE_PERCENT) return datapackValue;
        long scaled = Math.round(datapackValue * (double) DATAPACK_BASELINE_PERCENT / (double) percent);
        return (int) Math.max(1, Math.min(4096, scaled));
    }

    public static int ensureSpacing(int spacing, int separation) {
        return Math.max(spacing, separation + 1);
    }
}
