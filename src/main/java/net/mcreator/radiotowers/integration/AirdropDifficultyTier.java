package net.mcreator.radiotowers.integration;

import net.mcreator.radiotowers.config.AirdropConfig;

/**
 * Maps airdrop points to wave count and API difficulty tier.
 * Orders use 10-point increments and a configurable minimum.
 * Wave count and zombies-per-wave come from {@link AirdropConfig} at runtime.
 * Wave length is {@link AirdropConfig#WAVE_DURATION_MINUTES}.
 */
public final class AirdropDifficultyTier {

    public static final int DEFAULT_MIN_POINTS = 10;
    public static final int MAX_TIER = 10;

    /**
     * Default wave length used only as a compile-time fallback reference.
     * Prefer {@link #getTicksPerWave()} so config changes apply at runtime.
     */
    public static final int TICKS_PER_WAVE = 3 * 60 * 20;

    public static int getTicksPerWave() {
        return AirdropConfig.getWaveDurationTicks();
    }

    public static final class Tier {
        public final int numWaves;
        public final int apiDifficulty;

        public Tier(int numWaves, int apiDifficulty) {
            this.numWaves = numWaves;
            this.apiDifficulty = apiDifficulty;
        }
    }

    /**
     * Get tier for the given total points (catalog selection).
     * Points must meet the configured minimum and use 10-point increments.
     */
    public static Tier getTier(int totalPoints) {
        if (!canStart(totalPoints)) return null;
        int tierIndex = Math.min(MAX_TIER, totalPoints / 10); // 10->1, 20->2, ... 100->10
        int maxWaves = Math.max(1, AirdropConfig.MAX_WAVES.get());
        int numWaves = Math.min(maxWaves, tierIndex);
        int apiDifficulty = tierIndex; // 1–10 for API (zombie toughness, weapons, armour, etc.)
        return new Tier(numWaves, apiDifficulty);
    }

    /** Whether the total meets the configured minimum and is a 10-point increment. */
    public static boolean canStart(int totalPoints) {
        return totalPoints >= getMinimumPoints()
            && totalPoints <= AirdropConfig.MAX_TOTAL_DIFFICULTY.get()
            && totalPoints % 10 == 0;
    }

    public static int getMinimumPoints() {
        return AirdropConfig.getMinimumCatalogPoints();
    }

    /**
     * Max zombies per wave by difficulty tier (1–10).
     * Defaults: 20 at tier 1, +10 per tier — overridden by {@link AirdropConfig#BASE_ZOMBIES_PER_WAVE}
     * and {@link AirdropConfig#ZOMBIES_PER_TIER_STEP}.
     */
    public static int getZombiesPerWaveForTier(int tierIndex) {
        int t = Math.max(1, Math.min(MAX_TIER, tierIndex));
        int base = Math.max(1, AirdropConfig.BASE_ZOMBIES_PER_WAVE.get());
        int step = Math.max(0, AirdropConfig.ZOMBIES_PER_TIER_STEP.get());
        return base + (t - 1) * step;
    }
}
