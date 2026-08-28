package net.mcreator.radiotowers.client;

import net.mcreator.radiotowers.integration.DefenseSessionClientMirror;
import net.minecraft.client.Minecraft;

/**
 * Client-side data for the airdrop wave HUD. Updated by packet handlers; read by overlay and Call Airdrop screen.
 */
public final class AirdropWaveHudData {

    private static volatile boolean waveInProgress = false;
    private static volatile long cooldownEndGameTime = 0L;
    private static volatile int currentWave = 0;
    private static volatile int totalWaves = 0;
    private static volatile int zombiesRemaining = -1;
    private static volatile int secondsRemaining = 0;

    public static boolean isWaveInProgress() { return waveInProgress; }
    public static long getCooldownEndGameTime() { return cooldownEndGameTime; }
    public static int getCurrentWave() { return currentWave; }
    public static int getTotalWaves() { return totalWaves; }
    public static int getZombiesRemaining() { return zombiesRemaining; }
    public static int getSecondsRemaining() { return secondsRemaining; }

    public static void setAirdropState(boolean inProgress, long cooldownEnd) {
        waveInProgress = inProgress;
        cooldownEndGameTime = cooldownEnd;
        if (!inProgress) {
            totalWaves = 0;
            currentWave = 0;
            zombiesRemaining = -1;
            secondsRemaining = 0;
            if (Minecraft.getInstance().level != null) {
                DefenseSessionClientMirror.set(Minecraft.getInstance().level.dimension(), false);
            }
        }
    }

    public static void setWaveState(int wave, int total, int zombies, int seconds) {
        setWaveState(wave, total, zombies, seconds, false);
    }

    public static void setWaveState(int wave, int total, int zombies, int seconds, boolean nmsSuppressActive) {
        currentWave = wave;
        totalWaves = total;
        zombiesRemaining = zombies;
        secondsRemaining = seconds;
        if (Minecraft.getInstance().level != null) {
            DefenseSessionClientMirror.set(Minecraft.getInstance().level.dimension(), nmsSuppressActive);
        }
    }
}
