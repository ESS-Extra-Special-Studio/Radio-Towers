package net.mcreator.radiotowers.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.events.WaveMobAggroHandler;
import net.mcreator.radiotowers.init.RadiotowersModSounds;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;
import uk.co.extraspecialstudio.esl.wave.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.UUID;

/**
 * ESL Wave API integration for RadioTowers.
 * Preferred over Berezka when ESL is available.
 * <p>
 * Priority chain:
 * <ol>
 *     <li>ESL available → use ESL Wave API</li>
 *     <li>ESL unavailable + Berezka → legacy Berezka reflection bridge</li>
 *     <li>Neither → plane spawns immediately</li>
 * </ol>
 */
public final class EslWaveIntegration {

    private static final String ESL_MOD_ID = "extraspeciallib";
    private static volatile Boolean eslWaveAvailable;

    /** True if ESL is loaded and the wave API is accessible. */
    public static boolean isEslWaveAvailable() {
        Boolean cached = eslWaveAvailable;
        if (cached != null) return cached;
        boolean modLoaded = ModList.get().isLoaded(ESL_MOD_ID);
        if (!modLoaded) {
            eslWaveAvailable = false;
            return false;
        }
        try {
            ClassLoader loader = ModList.get().getModContainerById(ESL_MOD_ID)
                    .map(c -> c.getMod().getClass().getClassLoader())
                    .orElse(Thread.currentThread().getContextClassLoader());
            Class.forName("uk.co.extraspecialstudio.esl.wave.EslWaveManager", false, loader);
            eslWaveAvailable = true;
            return true;
        } catch (Throwable t) {
            RadiotowersMod.LOGGER.warn("[ESL Waves] Wave API class not found despite ESL loaded: {}", t.getMessage());
            eslWaveAvailable = false;
            return false;
        }
    }

    /** Cancel all ESL wave controllers in this dimension (e.g. quit-to-menu mid-wave). */
    public static void abortAllInDimension(ServerLevel level) {
        if (!isEslWaveAvailable() || level == null) return;
        for (EslWaveController controller : new ArrayList<>(EslWaveManager.activeControllersIn(level.dimension()))) {
            EslWaveManager.cancel(controller);
        }
        ZombieWavesAPILoader.setDefenseSessionActive(level, false);
        WaveMobAggroHandler.clearAllWaveAggroState();
    }

    /** Remove frozen/straggler undead near a wave anchor after reconnect abort. */
    public static void sweepStragglersNear(ServerLevel level, BlockPos center) {
        if (level == null || center == null) return;
        ZombieWavesAPILoader.sweepReconnectStragglerUndead(level, center);
        ZombieWavesAPILoader.sweepBrokenUndeadNearWave(level, center, 320);
        WaveMobAggroHandler.discardAllTrackedWaveMobsStillAlive(level);
    }

    /**
     * Start a zombie defense wave sequence via ESL.
     *
     * @return true if the sequence started successfully
     */
    public static boolean startWaveAt(ServerLevel level, BlockPos waveStartPos, BlockPos panelPos,
                                       int tierDifficulty, int numWaves, int maxZombiesPerWave,
                                       @Nullable ServerPlayer player) {
        if (!isEslWaveAvailable()) return false;
        try {
            int waveDurationTicks = AirdropConfig.getWaveDurationTicks();
            int delayBetweenWaves = 100; // 5 seconds between waves

            EslWaveSequence.Builder seqBuilder = EslWaveSequence.builder()
                .delayBetween(delayBetweenWaves);

            for (int w = 0; w < numWaves; w++) {
                EslWaveDefinition wave = EslWaveDefinition.builder()
                    .spawn(EslSpawnEntry.of(EntityType.ZOMBIE, maxZombiesPerWave)
                        .radius(8, 30)
                        .spawnDelay(3) // stagger spawns slightly
                        .onSpawn(mob -> {
                            if (player != null && player.isAlive()) {
                                mob.setTarget(player);
                            }
                        })
                        .build())
                    .timeout(waveDurationTicks)
                    .displayName("Wave " + (w + 1))
                    .build();
                seqBuilder.wave(wave);
            }

            // Callbacks for RT-specific behavior
            final int[] wavesCleared = {0};
            seqBuilder.onWaveCleared(ctx -> {
                int total = ctx.controller().totalWaves();
                int justCleared = Math.min(total, ++wavesCleared[0]);
                boolean moreWaves = justCleared < total;
                PendingAirdropStorage.Pending pending = PendingAirdropStorage.getPending(level, waveStartPos);
                if (pending != null) {
                    pending.wavesRemaining = Math.max(0, total - justCleared);
                    pending.killedCountThisWave = 0;
                    pending.currentWaveStartedAtGameTime = level.getGameTime();
                    PendingAirdropSavedData.get(level).setDirty();
                }
                ServerPlayer p = ctx.triggeringPlayer();
                if (moreWaves && p != null && p.isAlive()) {
                    p.sendSystemMessage(Component.translatable(
                        "message.radiotowers.wave.cleared_next_starting", justCleared, total));
                }
                if (moreWaves) {
                    level.playSound(null, panelPos, RadiotowersModSounds.SIREN.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
                }
            });

            seqBuilder.onComplete(ctx -> {
                RadiotowersMod.LOGGER.info("[ESL Waves] All waves completed at {}, delivering airdrop", waveStartPos);
                ZombieWavesAPILoader.setDefenseSessionActive(level, false);
                PendingAirdropStorage.deliverAt(level, waveStartPos);
            });

            seqBuilder.onFailed(ctx -> {
                RadiotowersMod.LOGGER.info("[ESL Waves] Wave sequence failed at {}", waveStartPos);
                ZombieWavesAPILoader.setDefenseSessionActive(level, false);
                PendingAirdropStorage.removePending(level, waveStartPos);
                ServerPlayer p = ctx.triggeringPlayer();
                if (p != null && p.isAlive()) {
                    long cooldownEnd = AirdropCooldown.getCooldownEndGameTime(level, p.getUUID());
                    RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> p),
                        new net.mcreator.radiotowers.network.AirdropStatePacket(false, cooldownEnd));
                    p.sendSystemMessage(Component.translatable("message.radiotowers.airdrop.failed"));
                }
            });

            EslWaveController controller = EslWaveManager.start(level, waveStartPos, seqBuilder.build(), player);
            ZombieWavesAPILoader.setDefenseSessionActive(level, true);

            // Store controller ID in pending for HUD sync
            PendingAirdropStorage.Pending pending = PendingAirdropStorage.getPending(level, waveStartPos);
            if (pending != null) {
                pending.eslControllerId = controller.id();
                PendingAirdropSavedData.get(level).setDirty();
            }

            return true;
        } catch (Throwable t) {
            RadiotowersMod.LOGGER.warn("[ESL Waves] Failed to start wave via ESL: {}", t.getMessage());
            return false;
        }
    }

    /**
     * Send wave state to the player's HUD when using ESL waves.
     * Reads directly from the ESL controller instead of polling Berezka's API.
     */
    public static void sendWaveStateToPlayers(ServerLevel level) {
        if (!isEslWaveAvailable()) return;
        try {
            PendingAirdropStorage.forEachPendingIn(level, (waveStartPos, p) -> {
                if (p.eslControllerId == null) return;
                EslWaveController controller = EslWaveManager.findById(p.eslControllerId);
                if (controller == null) return;

                int currentWave = controller.currentWaveIndex() + 1;
                int totalWaves = controller.totalWaves();
                int zombiesLeft = controller.remainingEnemies();
                int secondsRemaining = controller.secondsRemaining();

                ServerPlayer player = level.getServer() != null
                    ? level.getServer().getPlayerList().getPlayer(p.playerWhoStarted) : null;
                if (player != null) {
                    RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player),
                        new net.mcreator.radiotowers.network.WaveStatePacket(
                            currentWave, totalWaves, zombiesLeft, secondsRemaining, true));
                }
            });
        } catch (Throwable ignored) {}
    }

    private EslWaveIntegration() {}
}
