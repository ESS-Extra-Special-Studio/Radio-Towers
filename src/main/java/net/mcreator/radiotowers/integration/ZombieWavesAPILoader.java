package net.mcreator.radiotowers.integration;
import net.mcreator.radiotowers.network.RadiotowersNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.util.LoadedEntityScan;
import net.mcreator.radiotowers.events.WaveMobAggroHandler;
import net.mcreator.radiotowers.init.RadiotowersModSounds;
import net.mcreator.radiotowers.network.WaveStatePacket;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModContainer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.mcreator.radiotowers.network.DefenseSessionSyncPacket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integration with Berezka's Zombie Waves API (no compile dependency).
 * - Start wave at tower ground when player clicks START; airdrop is delivered only when wave completes.
 * - We detect wave end by reading the API's manager.remainEnemysCount and manager.pos (no API changes needed).
 * - Optional: API can still call onWaveCompleted(level, waveStartPos) for immediate delivery.
 */
public final class ZombieWavesAPILoader {

    private static final String MOD_ID = "berezkas_zombie_waves_api";
    private static final String[] MOD_IDS = { "berezkas_zombie_waves_api", "berezka_zombie_waves_api" };
    private static final String API_CLASS = "org.berezka.berezkas_zombie_waves_api.API";
    private static final String WAVES_MANAGER_CLASS = "org.berezka.berezkas_zombie_waves_api.WavesManager";
    /** berezka_api (dependency of zombie waves) has static curWorld used by WavesManager.onServerTick for spawn level. We must set it so spawns use our dimension. */
    private static final String BEREZKA_MAIN_CLASS = "org.berezka.berezka_api.berezka_api_main";
    private static final String BEREZKA_API_MOD_ID = "berezka_api";
    /** Don't deliver until wave has had time to run (avoid delivering when API has remainEnemysCount=0 at start). */
    private static final long MIN_TICKS_BEFORE_DELIVER = 200;
    /** Horizontal range (blocks) for "wave area" when counting zombies for ghost-sync. Match DrownedKillCountsHandler. */
    private static final int WAVE_AREA_RANGE = 320;
    /** Wave must run at least this long before we sync "ghost" count (0 in area but API > 0). 60 seconds. */
    private static final long MIN_TICKS_FOR_GHOST_SYNC = 60 * 20;
    /** True while a RadioTowers defense-wave session is active in a dimension (from first start to final deliver/fail). */
    private static final Map<ResourceKey<net.minecraft.world.level.Level>, Boolean> ACTIVE_DEFENSE_SESSION_BY_DIM = new ConcurrentHashMap<>();
    /** After quit mid-wave, sweep undead near these positions for a short time (chunks/player load asynchronously). */
    private static final Map<ResourceKey<Level>, List<BlockPos>> RECONNECT_STRAGGLER_CENTERS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Long> RECONNECT_STRAGGLER_UNTIL_GAME_TIME = new ConcurrentHashMap<>();
    private static final long RECONNECT_STRAGGLER_QUEUE_TICKS = 6000L;

    /** Retained to clear any stale state from older builds that used zero-alive completion streaks. */
    private static final java.util.Map<BlockPos, Integer> ZERO_ALIVE_CHECK_STREAK = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Start a single zombie wave at the given ground position (bottom of tower).
     * difficulty is the tier (1–10) from AirdropDifficultyTier; each wave lasts 3 minutes.
     * Does not spawn the plane; airdrop is delivered when all waves complete (onWaveCompleted).
     * Sets mixin context so the API's WavesManager gets our level and ticks/spawns correctly.
     * @return true if the wave actually started
     */
    /** Ticks to ignore duplicate start (e.g. double-click) so we don't end+restart the same wave. */
    private static final int WAVE_START_DEBOUNCE_TICKS = 100;
    /** When we last called start for (dimension, pos); used to debounce double-start. */
    private static final java.util.Map<String, Long> LAST_START_AT = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean startWaveAt(ServerLevel level, BlockPos groundPos, int tierDifficulty, ServerPlayer playerWhoStarted) {
        if (level == null || level.isClientSide() || groundPos == null) {
            RadiotowersMod.LOGGER.warn("[Zombie Waves] startWaveAt skipped: level null or client or pos null (level={}, pos={})", level != null && !level.isClientSide(), groundPos);
            return false;
        }
        if (!isZombieWavesAPILoaded()) {
            RadiotowersMod.LOGGER.warn("[Zombie Waves] startWaveAt skipped: API mod not loaded");
            return false;
        }
        BerezkaWaveChatSilencer.apply();
        // Debounce: if we already started at this position very recently, don't end+restart (avoids double-click / multiple starts)
        String startKey = level.dimension().location() + "_" + groundPos.getX() + "_" + groundPos.getY() + "_" + groundPos.getZ();
        long now = level.getGameTime();
        Long lastStart = LAST_START_AT.get(startKey);
        if (lastStart != null && (now - lastStart) < WAVE_START_DEBOUNCE_TICKS) {
            return true;
        }
        LAST_START_AT.put(startKey, now);
        int waveDurationTicks = AirdropDifficultyTier.getTicksPerWave(); // 3 minutes
        int spawnRange = 24;
        int timeBetweenWaves = 60; // ticks between each spawn (slower so we don't get bursts; we also ramp cap in WaveMobAggroHandler)
        int waveDifficulty = Math.max(1, tierDifficulty);

        boolean started;
        if (RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) {
            // Nuclear baseline: start API wave with minimal RadioTowers intervention.
            // Important: do not return early; we still need to reset our Pending wave state
            // so HUD (zombies left + timer) works.
            // Critical: ensure berezka_api_main.curWorld is set and do not schedule delayed clear,
            // otherwise WavesManager.onServerTick may see level=null and crash on later waves.
            setBerezkaCurWorld(level);
            endWaveViaReflection(false); // stop any previous wave state without nulling curWorld later
            setBerezkaCurWorld(level);
            started = tryStartZombieWaveWithPlayer(level, groundPos, waveDifficulty, waveDurationTicks, spawnRange, timeBetweenWaves, playerWhoStarted);
            if (!started) started = tryStartZombieWaveDirect(level, groundPos, waveDifficulty, waveDurationTicks, spawnRange, timeBetweenWaves);
            if (!started) started = tryCommandWaveStart(level, groundPos, waveDifficulty, waveDurationTicks, spawnRange, timeBetweenWaves, playerWhoStarted);
        } else {
            // WavesManager has NO level field — onServerTick() uses berezka_api_main.getCurWorld(). Set curWorld BEFORE endWave so API never sees null; do NOT schedule delayed clear (we are starting a new wave).
            setBerezkaCurWorld(level);
            endWaveViaReflection(false); // clear previous wave state; skip delayed curWorld clear so we don't null it 15 ticks into the new wave
            setBerezkaCurWorld(level); // again in case API's endWave() cleared it
            net.mcreator.radiotowers.events.DrownedKillCountsHandler.clearDeathCountedIds();
            ZombieWavesMixinContext.setPending(level, groundPos);

            // Prefer API overload with ServerPlayer so WavesManager gets Optional.of(player) for built-in agro (zombies target that player)
            started = tryStartZombieWaveWithPlayer(level, groundPos, waveDifficulty, waveDurationTicks, spawnRange, timeBetweenWaves, playerWhoStarted);
            if (!started)
                started = tryStartZombieWaveDirect(level, groundPos, waveDifficulty, waveDurationTicks, spawnRange, timeBetweenWaves);
            if (!started)
                started = tryCommandWaveStart(level, groundPos, waveDifficulty, waveDurationTicks, spawnRange, timeBetweenWaves, playerWhoStarted);
        }

        if (!started) {
            ZombieWavesMixinContext.getAndClearPendingLevel(); // clear if unused
            RadiotowersMod.LOGGER.warn("[Zombie Waves] Wave did not start at {} — API and command failed. Airdrop will be delivered immediately.", groundPos);
        } else {
            setDefenseSessionActive(level, true);
            PendingAirdropStorage.Pending p = PendingAirdropStorage.getPending(level, groundPos);
            if (p != null) {
                p.spawnCountThisWave = 0; // reset so we can cap spawns this wave
                p.killedCountThisWave = 0;
                p.currentWaveStartedAtGameTime = level.getGameTime(); // for debounce and ramp
                PendingAirdropSavedData.get(level).setDirty();
                if (playerWhoStarted != null) {
                    int waveNum = p.totalWaves - p.wavesRemaining + 1;
                    playerWhoStarted.sendSystemMessage(Component.translatable(
                        "message.radiotowers.wave.started", waveNum, p.totalWaves));
                }
                if (RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) {
                    logNuclearWaveAPISnapshot(level, p.waveStartPos);
                    RadiotowersMod.queueServerWork(5, () -> logNuclearWaveAPISnapshot(level, p.waveStartPos));
                    RadiotowersMod.queueServerWork(20, () -> logNuclearWaveAPISnapshot(level, p.waveStartPos));
                }
            }
        }
        return started;
    }

    private static void logNuclearWaveAPISnapshot(ServerLevel level, BlockPos waveStartPos) {
        if (level == null || level.isClientSide() || waveStartPos == null) return;
        if (!RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) return;
        Optional<ModContainer> opt = getZombieWavesModContainer();
        if (opt.isEmpty()) return;
        try {
            ClassLoader zwLoader = net.mcreator.radiotowers.RadiotowersMod.class.getClassLoader();
            Class<?> apiClass = Class.forName(API_CLASS, true, zwLoader);
            Object manager = apiClass.getField("manager").get(null);
            if (manager == null) return;
            BlockPos apiPos = (BlockPos) manager.getClass().getField("pos").get(manager);
            int remain = manager.getClass().getField("remainEnemysCount").getInt(manager);
            int aliveZombieFamily = 0;
            int onApproxRing = 0;
            // Debug-only: match RadioTowers ring radius used when placing on ring (WaveMobAggroHandler.SPAWN_RING_RADIUS).
            // If API spawns off-ring first then teleports to ring, these counts will change between snapshots.
            final double RING_RADIUS = 30.0;
            final double RING_TOLERANCE = 6.0;
            if (apiPos != null) {
                int r = 120;
                AABB box = new AABB(
                    apiPos.getX() - r, level.getMinBuildHeight(), apiPos.getZ() - r,
                    apiPos.getX() + r, level.getMaxBuildHeight(), apiPos.getZ() + r
                );
                for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box)) {
                    if (!m.isAlive() || m.isRemoved()) continue;
                    var t = m.getType();
                    if (t == net.minecraft.world.entity.EntityType.ZOMBIE || t == net.minecraft.world.entity.EntityType.DROWNED) {
                        aliveZombieFamily++;
                        double dx = (m.getX() + 0.5) - (waveStartPos.getX() + 0.5);
                        double dz = (m.getZ() + 0.5) - (waveStartPos.getZ() + 0.5);
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        if (Math.abs(dist - RING_RADIUS) <= RING_TOLERANCE) onApproxRing++;
                    }
                }
            }
            RadiotowersMod.LOGGER.warn(
                "[NuclearWaveAPI] t={} waveStartPos={} apiPos={} remainEnemysCount={} aliveZombieFamily@apiPosRadius120={} onApproxRing={} (radius={} tol={})",
                level.getGameTime(), waveStartPos, apiPos, remain, aliveZombieFamily, onApproxRing, RING_RADIUS, RING_TOLERANCE
            );
        } catch (Throwable ignored) {
        }
    }

    public static boolean isZombieWavesAPILoaded() {
        for (String id : MOD_IDS) {
            if (ModList.get().isLoaded(id)) return true;
        }
        return false;
    }

    /** Get the Zombie Waves API mod's classloader (whichever mod id is loaded). */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private static Optional<ModContainer> getZombieWavesModContainer() {
        for (String id : MOD_IDS) {
            var opt = ModList.get().getModContainerById(id);
            if (opt.isPresent()) return Optional.of(opt.get());
        }
        return Optional.empty();
    }

    /** Convenience: start wave without player context (command runs as server). */
    public static boolean startWaveAt(ServerLevel level, BlockPos groundPos, int tierDifficulty) {
        return startWaveAt(level, groundPos, tierDifficulty, null);
    }

    /**
     * Call API.startZombieWave(BlockPos, int, int, int, int, ServerPlayer) when we have a player.
     * The API passes Optional.of(player) to WavesManager so the wave has built-in agro (zombies target that player).
     */
    private static boolean tryStartZombieWaveWithPlayer(ServerLevel level, BlockPos groundPos, int waveDifficulty, int waveDurationTicks, int spawnRange, int timeBetweenWaves, ServerPlayer player) {
        if (player == null) return false;
        int zombiesPerWave = AirdropDifficultyTier.getZombiesPerWaveForTier(waveDifficulty);
        Optional<ModContainer> opt = getZombieWavesModContainer();
        if (opt.isEmpty()) return false;
        ClassLoader zwLoader = net.mcreator.radiotowers.RadiotowersMod.class.getClassLoader();
        try {
            Class<?> apiClass = Class.forName(API_CLASS, true, zwLoader);
            java.lang.reflect.Method m = apiClass.getMethod("startZombieWave", BlockPos.class, int.class, int.class, int.class, int.class, ServerPlayer.class);
            m.invoke(null, groundPos, waveDifficulty, zombiesPerWave, spawnRange, timeBetweenWaves, player);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Call API.startZombieWave(BlockPos, int, int, int, int) via reflection.
     * API order is (pos, difficulty, enemysToSpawn, spawn_range, time_between_waves). Passing duration (3600)
     * as 2nd int made it try to spawn 3600; pass our zombie cap and ticks-between-spawns instead.
     */
    private static boolean tryStartZombieWaveDirect(ServerLevel level, BlockPos groundPos, int waveDifficulty, int waveDurationTicks, int spawnRange, int timeBetweenWaves) {
        int zombiesPerWave = AirdropDifficultyTier.getZombiesPerWaveForTier(waveDifficulty);
        Optional<ModContainer> opt = getZombieWavesModContainer();
        if (opt.isEmpty()) return false;
        ClassLoader zwLoader = net.mcreator.radiotowers.RadiotowersMod.class.getClassLoader();
        try {
            Class<?> apiClass = Class.forName(API_CLASS, true, zwLoader);
            java.lang.reflect.Method m = apiClass.getMethod("startZombieWave", BlockPos.class, int.class, int.class, int.class, int.class);
            m.invoke(null, groundPos, waveDifficulty, zombiesPerWave, spawnRange, timeBetweenWaves);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Run the /wave start command. The API's command handler uses source.getPlayer().blockPosition() for spawn pos — so it ignores source position. When player != null the wave starts (spawn Y will be player Y; our resnap fixes). When player == null the command returns 1 but never calls startZombieWave, so direct call is required. */
    private static boolean tryCommandWaveStart(ServerLevel level, BlockPos groundPos, int waveDifficulty, int waveDuration, int spawnRange, int timeBetweenWaves, net.minecraft.server.level.ServerPlayer player) {
        try {
            var server = level.getServer();
            if (server == null) {
                RadiotowersMod.LOGGER.warn("[Zombie Waves] Command skipped: level.getServer() is null");
                return false;
            }
            if (player == null) {
                return false;
            }
            // Command args match API.startZombieWave: difficulty, enemysToSpawn, spawn_range, time_between_waves (NOT wave duration).
            int zombiesPerWave = AirdropDifficultyTier.getZombiesPerWaveForTier(waveDifficulty);
            String cmd = String.format("wave start %d %d %d %d", waveDifficulty, zombiesPerWave, spawnRange, timeBetweenWaves);
            var source = player.createCommandSourceStack().withLevel(level).withPosition(Vec3.atCenterOf(groundPos));
            server.getCommands().performPrefixedCommand(source, cmd);
            return true;
        } catch (Throwable t) {
            RadiotowersMod.LOGGER.warn("[Zombie Waves] Command wave start threw: {} (cause: {})", t.getMessage(), t.getCause() != null ? t.getCause().getMessage() : "none", t);
            return false;
        }
    }

    /**
     * Call this when a zombie wave completes (all zombies dead).
     * If more waves remain, starts the next wave; otherwise delivers the airdrop.
     * Sets lastWaveCompletedAtGameTime so we don't double-fire from API and time-based checks.
     */
    public static void onWaveCompleted(ServerLevel level, BlockPos waveStartPos) {
        if (level == null || level.isClientSide() || waveStartPos == null) return;
        PendingAirdropStorage.Pending p = PendingAirdropStorage.getPending(level, waveStartPos);
        if (p == null) return;
        if (p.isEslManaged()) return;
        logWaveDeliverySnapshot(level, waveStartPos, "onWaveCompleted_enter", p);
        long now = level.getGameTime();
        if (p.lastWaveCompletedAtGameTime != 0 && (now - p.lastWaveCompletedAtGameTime) < PendingAirdropStorage.WAVE_COMPLETED_COOLDOWN_TICKS)
            return; // already processed this completion recently (avoid duplicate next-wave start)
        boolean forcedCleanup = false;
        int aliveInArea = countWaveZombiesInArea(level, waveStartPos);
        if (aliveInArea > 0) {
            // If our death counter reached the expected max, but a stuck/frozen mob never dies,
            // we force-discard the remaining tagged mobs so the wave can actually complete.
            if (p.killedCountThisWave >= p.maxZombiesPerWave) {
                int removed = discardRemainingWaveMobsInArea(level, waveStartPos);
                RadiotowersMod.LOGGER.warn("[Zombie Waves] onWaveCompleted forced cleanup at {}: killedCountReachedMax={} removedStillAlive={} removed={}",
                    waveStartPos, p.killedCountThisWave, aliveInArea, removed);
                forcedCleanup = removed > 0;
                // Stop API spawning in case it is still running (prevents "kept spawning after HUD hit 0").
                endWaveViaReflection();
            } else {
                RadiotowersMod.LOGGER.warn("[Zombie Waves] onWaveCompleted at {} blocked: {} alive wave mobs still in area (killedCount={}/{})",
                    waveStartPos, aliveInArea, p.killedCountThisWave, p.maxZombiesPerWave);
                return;
            }
        }
        // Stragglers: completion used tag-based counts; mobs can lose NBT but stay in WAVE_SPAWNED_ENTITY_IDS, or stay
        // unfrozen but stuck (treetops). Remove tagged + tracked + frozen/tracked sweep before advancing state.
        discardRemainingWaveMobsInArea(level, waveStartPos);
        WaveMobAggroHandler.discardAllTrackedWaveMobsStillAlive(level);
        sweepBrokenUndeadNearWave(level, waveStartPos, WAVE_AREA_RANGE);

        p.lastWaveCompletedAtGameTime = now;
        p.wavesRemaining--;
        PendingAirdropSavedData.get(level).setDirty();
        logWaveDeliverySnapshot(level, waveStartPos, "onWaveCompleted_afterDecrement", p);
        if (p.wavesRemaining <= 0) {
            logWaveDeliverySnapshot(level, waveStartPos, "onWaveCompleted_beforeDeliver", p);
            try {
                PendingAirdropStorage.deliverAt(level, waveStartPos);
            } finally {
                setDefenseSessionActive(level, false);
            }
            logWaveDeliverySnapshot(level, waveStartPos, "onWaveCompleted_afterDeliver", p);
        } else {
            ServerPlayer player = level.getServer() != null ? level.getServer().getPlayerList().getPlayer(p.playerWhoStarted) : null;
            if (player != null) {
                int cleared = p.totalWaves - p.wavesRemaining;
                player.sendSystemMessage(Component.translatable(
                    "message.radiotowers.wave.cleared_next_starting", cleared, p.totalWaves));
            }
            if (!forcedCleanup) endWaveViaReflection(); // stop current wave before starting next
            WaveMobAggroHandler.clearAllWaveAggroState();
            p.currentWaveStartedAtGameTime = level.getGameTime(); // 3-min timer for the next wave
            PendingAirdropSavedData.get(level).setDirty();
            startWaveAt(level, waveStartPos, p.tierDifficulty, player);
            level.playSound(null, waveStartPos, RadiotowersModSounds.SIREN.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
        }
    }

    /**
     * Call when time ran out and zombies are still alive (wave failed). Removes pending, ends API wave, no delivery.
     */
    public static void onWaveFailedTimeout(ServerLevel level, BlockPos waveStartPos) {
        if (level == null || level.isClientSide() || waveStartPos == null) return;
        PendingAirdropStorage.Pending p = PendingAirdropStorage.getPending(level, waveStartPos);
        if (p == null) return;
        endWaveViaReflection();
        PendingAirdropStorage.removePending(level, waveStartPos);
        setDefenseSessionActive(level, false);
        purgeWaveEntityResidue(level, waveStartPos);
        PendingAirdropSavedData.get(level).setDirty();
        ServerPlayer player = level.getServer() != null ? level.getServer().getPlayerList().getPlayer(p.playerWhoStarted) : null;
        if (player != null) {
            long cooldownEnd = net.mcreator.radiotowers.integration.AirdropCooldown.getCooldownEndGameTime(level, p.playerWhoStarted);
            RadiotowersNetwork.sendToPlayer(player,
                new net.mcreator.radiotowers.network.AirdropStatePacket(false, cooldownEnd));
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.radiotowers.airdrop.failed"));
        }
    }

    /** Call after delivering airdrop so the API doesn't start another wave from its own timer. */
    public static void endWaveAfterDeliver() {
        endWaveViaReflection();
    }

    /** Set berezka_api_main.curWorld so WavesManager.onServerTick() spawns in our level. Safe no-op if berezka_api not present. */
    private static void setBerezkaCurWorld(ServerLevel level) {
        var opt = ModList.get().getModContainerById(BEREZKA_API_MOD_ID);
        if (opt.isEmpty()) return;
        try {
            ClassLoader loader = net.mcreator.radiotowers.RadiotowersMod.class.getClassLoader();
            Class<?> main = Class.forName(BEREZKA_MAIN_CLASS, true, loader);
            java.lang.reflect.Field curWorld = main.getField("curWorld");
            curWorld.set(null, level);
        } catch (Throwable ignored) {
        }
    }

    /** Ticks to wait before clearing berezka_api curWorld so WavesManager.onServerTick doesn't NPE (it calls getCurWorld() in startWave). */
    private static final int CUR_WORLD_CLEAR_DELAY_TICKS = 15;

    /** Call the API's manager.endWave() so it stops spawning. Safe to call if API not loaded.
     * If scheduleDelayedClear is true, clears berezka_api curWorld after a delay (use when wave actually ended, not when about to start a new wave). */
    private static void endWaveViaReflection(boolean scheduleDelayedClear) {
        if (!isZombieWavesAPILoaded()) return;
        net.mcreator.radiotowers.events.DrownedKillCountsHandler.clearDeathCountedIds();
        Optional<ModContainer> opt = getZombieWavesModContainer();
        if (opt.isEmpty()) return;
        try {
            ClassLoader zwLoader = net.mcreator.radiotowers.RadiotowersMod.class.getClassLoader();
            Class<?> apiClass = Class.forName(API_CLASS, true, zwLoader);
            Object manager = apiClass.getField("manager").get(null);
            if (manager != null)
                manager.getClass().getMethod("endWave").invoke(manager);
        } catch (Throwable t) {
            // ignore
        }
        if (scheduleDelayedClear)
            RadiotowersMod.queueServerWork(CUR_WORLD_CLEAR_DELAY_TICKS, DelayedBerezkaCurWorldClear::run);
    }

    private static void endWaveViaReflection() {
        // In nuclear baseline we intentionally avoid any delayed clearing of berezka_api_main.curWorld.
        // WavesManager reads curWorld on its tick loop; delayed nulling can crash on subsequent waves.
        endWaveViaReflection(!RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE);
    }

    /**
     * Safety guard: if the API wave manager still looks active but we no longer have any pending airdrop
     * for this level, force-stop the API wave so it cannot keep trickle-spawning zombies after delivery.
     */
    public static void enforceApiStoppedWhenNoPending(ServerLevel level) {
        if (level == null || level.isClientSide() || !isZombieWavesAPILoaded()) return;
        // Active pending exists -> do nothing.
        if (PendingAirdropStorage.getAnyPendingInLevel(level) != null) return;
        Optional<ModContainer> opt = getZombieWavesModContainer();
        if (opt.isEmpty()) return;
        try {
            ClassLoader zwLoader = net.mcreator.radiotowers.RadiotowersMod.class.getClassLoader();
            Class<?> apiClass = Class.forName(API_CLASS, true, zwLoader);
            Object manager = apiClass.getField("manager").get(null);
            if (manager == null) return;

            BlockPos apiPos = null;
            int remain = 0;
            int currentWave = 0;
            try {
                Object posObj = manager.getClass().getField("pos").get(manager);
                if (posObj instanceof BlockPos p) apiPos = p;
            } catch (Throwable ignored) {}
            try { remain = manager.getClass().getField("remainEnemysCount").getInt(manager); } catch (Throwable ignored) {}
            try { currentWave = manager.getClass().getField("currentWave").getInt(manager); } catch (Throwable ignored) {}

            // If manager appears active but no pending exists, hard-stop.
            if (remain > 0 || currentWave > 0 || apiPos != null) {
                endWaveViaReflection();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * True only while a RadioTowers defense wave is actually active in this level.
     * Uses both our pending storage and the Zombie Waves API manager runtime state.
     */
    /**
     * True while our defense session flag is set for this dimension.
     * Does not require {@link #isZombieWavesAPILoaded()} — the session is only set after a successful
     * wave start; gating on the mod list could incorrectly return false and let NMS spawn hordes anyway.
     */
    public static boolean isDefenseWaveActive(ServerLevel level) {
        if (level == null || level.isClientSide()) return false;
        return ACTIVE_DEFENSE_SESSION_BY_DIM.getOrDefault(level.dimension(), false);
    }

    /** Explicitly clear active defense-wave session marker for this dimension (used on load/unload hard reset). */
    public static void clearDefenseWaveSession(ServerLevel level) {
        if (level == null) return;
        setDefenseSessionActive(level, false);
    }

    /**
     * Server-only: set whether a defense wave session is active and sync to all players in the dimension.
     * Client mirror is updated via {@link DefenseSessionSyncPacket} so optional mixins can cancel on the logical client too.
     */
    public static void setDefenseSessionActive(ServerLevel level, boolean active) {
        if (level == null || level.isClientSide()) return;
        ResourceKey<Level> dim = level.dimension();
        if (active) {
            ACTIVE_DEFENSE_SESSION_BY_DIM.put(dim, Boolean.TRUE);
        } else {
            ACTIVE_DEFENSE_SESSION_BY_DIM.remove(dim);
        }
        RadiotowersNetwork.sendToPlayersInDimension(level,
            new DefenseSessionSyncPacket(dim.location(), active));
    }

    /** Server -> one player: current defense session flag (call on login so client mirror matches). */
    public static void pushDefenseSessionStateToPlayer(ServerPlayer player) {
        if (player == null) return;
        ServerLevel level = player.serverLevel();
        boolean active = ACTIVE_DEFENSE_SESSION_BY_DIM.getOrDefault(level.dimension(), false);
        RadiotowersNetwork.sendToPlayer(player,
            new DefenseSessionSyncPacket(level.dimension().location(), active));
    }

    /**
     * Poll the API's state: when a wave actually ran (currentWave > 0), remainEnemysCount == 0,
     * and we have a pending airdrop for manager.pos, deliver it. Do not deliver if the wave never started.
     * Uses the Zombie Waves mod's classloader so we don't load their classes in our mod's context.
     */
    public static void checkWaveEndAndDeliver(ServerLevel level) {
        if (level == null || level.isClientSide() || !isZombieWavesAPILoaded()) return;
        // Don't deliver in first 5 seconds after level load (mobs may not be in our count yet)
        if (level.getGameTime() < 100) return;
        PendingAirdropStorage.forEachPendingIn(level, (waveStartPos, p) -> {
            if (p == null) return;
            if (p.isEslManaged()) return;
            long now = level.getGameTime();
            long waveAgeTicks = now - p.currentWaveStartedAtGameTime;
            // Avoid completing immediately when wave just started.
            if (waveAgeTicks < MIN_TICKS_BEFORE_DELIVER) return;
            if (now - p.storedAtGameTime < MIN_TICKS_BEFORE_DELIVER) return;
            if (p.lastWaveCompletedAtGameTime != 0
                && (now - p.lastWaveCompletedAtGameTime) < PendingAirdropStorage.WAVE_COMPLETED_COOLDOWN_TICKS)
                return;
            // If deaths reached max, finish even if a stuck mob is still alive.
            if (p.killedCountThisWave >= p.maxZombiesPerWave) {
                onWaveCompleted(level, waveStartPos);
                return;
            }
            // Completion is kill-count authoritative: requires all expected kills.
            // This prevents false completion when some spawns are discarded/replaced/desynced.
            if (p.killedCountThisWave >= p.maxZombiesPerWave) onWaveCompleted(level, waveStartPos);
            else ZERO_ALIVE_CHECK_STREAK.remove(waveStartPos.immutable());
        });
    }

    /**
     * Send wave state (wave number, zombies left, time left) to each player who has a pending airdrop.
     * Uses OUR pending data for wave index and timer so the UI always shows correct countdown.
     * Zombie count from API when manager.pos matches this pending's position, else -1 (client shows "—").
     */
    public static void sendWaveStateToPlayers(ServerLevel level) {
        if (level == null || level.isClientSide()) return;
        long now = level.getGameTime();
        PendingAirdropStorage.forEachPendingIn(level, (waveStartPos, p) -> {
            if (p.isEslManaged()) return;
            int currentWave = p.totalWaves - p.wavesRemaining + 1;
            long ticksLeft = p.currentWaveStartedAtGameTime + AirdropDifficultyTier.getTicksPerWave() - now;
            int secondsRemaining = (int) Math.max(0, ticksLeft / 20);
            // HUD should show "zombies left" (max - kills), not "alive in the arena".
            // Using killedCount makes it start at max immediately and only decrease on actual deaths.
            int killed = Math.max(0, Math.min(p.killedCountThisWave, p.maxZombiesPerWave));
            int zombies = Math.max(0, p.maxZombiesPerWave - killed);
            ServerPlayer player = level.getServer() != null ? level.getServer().getPlayerList().getPlayer(p.playerWhoStarted) : null;
            var packet = new WaveStatePacket(currentWave, p.totalWaves, zombies, secondsRemaining, true);
            for (java.util.UUID memberId : p.effectiveMembers()) {
                ServerPlayer m = level.getServer() != null ? level.getServer().getPlayerList().getPlayer(memberId) : null;
                if (m != null) {
                    RadiotowersNetwork.sendToPlayer(m, packet);
                }
            }
        });
    }

    private static final int MANAGER_POS_TOLERANCE_SQ = 16 * 16; // 16 blocks radius
    /** Temporary diagnostics around completion/delivery for trickle-spawn reports. */
    private static final boolean WAVE_DELIVERY_DEBUG = true;

    private static void logWaveDeliverySnapshot(ServerLevel level, BlockPos waveStartPos, String stage, PendingAirdropStorage.Pending pending) {
        if (!WAVE_DELIVERY_DEBUG || level == null || waveStartPos == null) return;
        Integer apiRemain = getApiRemainingCountForPosition(level, waveStartPos);
        int apiCurrentWave = -1;
        BlockPos apiPos = null;
        try {
            Optional<ModContainer> opt = getZombieWavesModContainer();
            if (opt.isPresent()) {
                ClassLoader zwLoader = net.mcreator.radiotowers.RadiotowersMod.class.getClassLoader();
                Class<?> apiClass = Class.forName(API_CLASS, true, zwLoader);
                Object manager = apiClass.getField("manager").get(null);
                if (manager != null) {
                    try { apiCurrentWave = manager.getClass().getField("currentWave").getInt(manager); } catch (Throwable ignored) {}
                    try {
                        Object posObj = manager.getClass().getField("pos").get(manager);
                        if (posObj instanceof BlockPos p) apiPos = p;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {
        }
        int aliveTagged = countWaveZombiesInArea(level, waveStartPos);
        int trackedAlive = WaveMobAggroHandler.countAliveWaveMobsInLevel(level);
        String pendingInfo = pending == null
            ? "pending=null"
            : ("pending{wavesRemaining=" + pending.wavesRemaining
                + ",killed=" + pending.killedCountThisWave + "/" + pending.maxZombiesPerWave
                + ",total=" + pending.totalWaves + "}");
        RadiotowersMod.LOGGER.warn(
            "[WaveDeliveryDebug] stage={} t={} waveStartPos={} apiPos={} apiRemain={} apiCurrentWave={} aliveTagged={} trackedAlive={} {}",
            stage, level.getGameTime(), waveStartPos, apiPos, apiRemain, apiCurrentWave, aliveTagged, trackedAlive, pendingInfo
        );
    }

    /**
     * Decrements the API's remainEnemysCount when a wave mob dies or we discard one.
     * Uses exact position match, or if {@code waveStartPos} is within 16 blocks (horizontal), treats as match.
     * @return true if the count was decremented (manager pos matched and count was &gt; 0)
     */
    public static boolean decrementRemainingCountForWaveAt(ServerLevel level, BlockPos waveStartPos) {
        if (level == null || level.isClientSide() || !isZombieWavesAPILoaded()) return false;
        Optional<ModContainer> opt = getZombieWavesModContainer();
        if (opt.isEmpty()) return false;
        try {
            ClassLoader zwLoader = net.mcreator.radiotowers.RadiotowersMod.class.getClassLoader();
            Class<?> apiClass = Class.forName(API_CLASS, true, zwLoader);
            Object manager = apiClass.getField("manager").get(null);
            if (manager == null) return false;
            BlockPos managerPos = (BlockPos) manager.getClass().getField("pos").get(manager);
            if (managerPos == null) return false;
            boolean posMatch = waveStartPos != null && (managerPos.equals(waveStartPos) || managerPos.distSqr(waveStartPos) <= MANAGER_POS_TOLERANCE_SQ);
            if (!posMatch) return false;
            java.lang.reflect.Field countField = manager.getClass().getField("remainEnemysCount");
            int current = countField.getInt(manager);
            if (current <= 0) return false;
            int next = Math.max(0, current - 1);
            countField.setInt(manager, next);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Decrement the API's remainEnemysCount for the wave at the manager's current position (no position match needed). Use when a wave mob dies and we know a wave is active. */
    public static boolean decrementRemainingCountForCurrentWave(ServerLevel level) {
        if (level == null || level.isClientSide() || !isZombieWavesAPILoaded()) return false;
        Optional<ModContainer> opt = getZombieWavesModContainer();
        if (opt.isEmpty()) return false;
        try {
            ClassLoader zwLoader = net.mcreator.radiotowers.RadiotowersMod.class.getClassLoader();
            Class<?> apiClass = Class.forName(API_CLASS, true, zwLoader);
            Object manager = apiClass.getField("manager").get(null);
            if (manager == null) return false;
            BlockPos managerPos = (BlockPos) manager.getClass().getField("pos").get(manager);
            if (managerPos == null) return false;
            java.lang.reflect.Field countField = manager.getClass().getField("remainEnemysCount");
            int current = countField.getInt(manager);
            if (current <= 0) return false;
            int next = Math.max(0, current - 1);
            countField.setInt(manager, next);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * When the API thinks there are more zombies left than actually exist (e.g. API never spawned them),
     * decrement the API count down to the real alive count so the wave can complete.
     * Call periodically with the number of wave mobs that are actually alive in the world.
     * Safeguard: never mass-decrement when aliveCount > 0 (only fix off-by-one when e.g. 1 stuck); full sync only when aliveCount == 0.
     */
    public static void syncRemainingCountToAliveWaveMobs(ServerLevel level, BlockPos waveStartPos, int aliveCount) {
        // No-op: Berezka's internal remainEnemysCount has shown heavy desync with our replacements.
        // We now complete/deliver based on LOCAL alive counting instead of mutating API state.
    }

    /** If API manager exists and its pos equals waveStartPos, return remainEnemysCount; else null. */
    private static Integer getApiRemainingCountForPosition(ServerLevel level, BlockPos waveStartPos) {
        if (!isZombieWavesAPILoaded()) return null;
        Optional<ModContainer> opt = getZombieWavesModContainer();
        if (opt.isEmpty()) return null;
        try {
            ClassLoader zwLoader = net.mcreator.radiotowers.RadiotowersMod.class.getClassLoader();
            Class<?> apiClass = Class.forName(API_CLASS, true, zwLoader);
            Object manager = apiClass.getField("manager").get(null);
            if (manager == null) return null;
            BlockPos managerPos = (BlockPos) manager.getClass().getField("pos").get(manager);
            if (managerPos == null || !managerPos.equals(waveStartPos)) return null;
            java.lang.reflect.Field countField = manager.getClass().getField("remainEnemysCount");
            try {
                return countField.getInt(manager);
            } catch (Exception e) {
                return countField.getInt(null);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * When the API thinks zombies remain but none exist in the wave area (unloaded, despawned, or never spawned),
     * sync the API count to 0 so the wave can complete. Only runs when wave has been active at least 60s.
     * Never sync when we still have mobs in WAVE_SPAWNED_ENTITY_IDS (avoids "wave ended before I killed all 20").
     */
    public static void checkGhostSync(ServerLevel level) {
        // Disabled: the API's internal remainEnemysCount is already desync'ing heavily
        // with our compatibility layer (replacements + tag adoption). Any API sync/dec logic
        // can cause early end/complete spam and/or prevent delivery.
    }

    /**
     * Remove any wave zombies/drowned that were saved with the world (persist across quit/reload).
     * Call after level load so reconnecting players don't see a full wave of leftover mobs at the tower.
     */
    public static void removeSavedWaveMobsFromLevel(ServerLevel level) {
        if (level == null || level.isClientSide()) return;
        discardAllWaveTaggedMobsInLevel(level);
    }

    /**
     * If Berezka's wave manager still has a spawn position (e.g. pending save missed), add it as an extra sweep center.
     * Call before {@link #endWaveAfterDeliver()} clears API state.
     */
    public static void addCurrentApiWavePosToList(ServerLevel level, Collection<BlockPos> out) {
        if (level == null || out == null || !isZombieWavesAPILoaded()) return;
        Optional<ModContainer> opt = getZombieWavesModContainer();
        if (opt.isEmpty()) return;
        try {
            ClassLoader zwLoader = net.mcreator.radiotowers.RadiotowersMod.class.getClassLoader();
            Class<?> apiClass = Class.forName(API_CLASS, true, zwLoader);
            Object manager = apiClass.getField("manager").get(null);
            if (manager == null) return;
            Object posObj = manager.getClass().getField("pos").get(manager);
            if (posObj instanceof BlockPos p) {
                out.add(p.immutable());
            }
        } catch (Throwable ignored) {
        }
    }

    public static void registerReconnectStragglerCenters(ServerLevel level, List<BlockPos> centers) {
        if (level == null) return;
        ResourceKey<Level> dim = level.dimension();
        if (centers == null || centers.isEmpty()) {
            RECONNECT_STRAGGLER_CENTERS.remove(dim);
            RECONNECT_STRAGGLER_UNTIL_GAME_TIME.remove(dim);
            return;
        }
        RECONNECT_STRAGGLER_CENTERS.put(dim, new ArrayList<>(centers));
        RECONNECT_STRAGGLER_UNTIL_GAME_TIME.put(dim, level.getGameTime() + RECONNECT_STRAGGLER_QUEUE_TICKS);
    }

    public static void clearReconnectStragglerQueue(ServerLevel level) {
        if (level == null) return;
        ResourceKey<Level> dim = level.dimension();
        RECONNECT_STRAGGLER_CENTERS.remove(dim);
        RECONNECT_STRAGGLER_UNTIL_GAME_TIME.remove(dim);
    }

    public static boolean hasReconnectStragglerQueue(ServerLevel level) {
        if (level == null) return false;
        List<BlockPos> c = RECONNECT_STRAGGLER_CENTERS.get(level.dimension());
        return c != null && !c.isEmpty();
    }

    /**
     * Tagged wave mobs + undead stragglers near queued reconnect centers (used on timer and on player join).
     */
    public static void runReconnectStragglerCleanupPass(ServerLevel level) {
        if (level == null || level.isClientSide()) return;
        // Quit/rejoin ghost cleanup must not run during a live catalog wave: it wipes all wave-tagged mobs
        // in the dimension and all zombies in 320 blocks of each reconnect center — same towers as new waves.
        if (PendingAirdropStorage.getAnyPendingInLevel(level) != null) return;
        removeSavedWaveMobsFromLevel(level);
        ResourceKey<Level> dim = level.dimension();
        List<BlockPos> centers = RECONNECT_STRAGGLER_CENTERS.get(dim);
        if (centers == null || centers.isEmpty()) return;
        Long until = RECONNECT_STRAGGLER_UNTIL_GAME_TIME.get(dim);
        if (until != null && level.getGameTime() > until) {
            clearReconnectStragglerQueue(level);
            return;
        }
        for (BlockPos c : centers) {
            sweepReconnectStragglerUndead(level, c);
        }
    }

    /**
     * Removes undead monsters near a sweep center after quit/rejoin. Includes modded undead (not only vanilla
     * {@code Zombie}), matching the wave arena radius used elsewhere.
     */
    public static int sweepReconnectStragglerUndead(ServerLevel level, BlockPos center) {
        if (level == null || level.isClientSide() || center == null) return 0;
        final int horizontalRadius = WAVE_AREA_RANGE;
        AABB box = new AABB(
            center.getX() - horizontalRadius, level.getMinBuildHeight(), center.getZ() - horizontalRadius,
            center.getX() + horizontalRadius, level.getMaxBuildHeight(), center.getZ() + horizontalRadius);
        int removed = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            if (mob.isRemoved() || !mob.isAlive()) continue;
            if (!(mob instanceof Monster)) continue;
            if (mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) continue;
            if (mob instanceof ZombifiedPiglin) continue;
            if (mob instanceof Warden) continue;
            if (mob instanceof Phantom) continue;
            if (mob instanceof Skeleton) continue;
            if (mob instanceof Stray) continue;
            if (mob instanceof WitherSkeleton) continue;
            if (mob instanceof Zoglin) continue;
            // Vanilla zombie family (incl. Drowned/Husk subclasses of Zombie) + modded undead wave mobs.
            if (mob instanceof Zombie || mob instanceof ZombieVillager) {
                mob.discard();
                removed++;
                continue;
            }
            if (mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
                mob.discard();
                removed++;
            }
        }
        return removed;
    }

    /** Discard every living entity in this dimension that still carries our wave NBT (full-world sweep). */
    public static int discardAllWaveTaggedMobsInLevel(ServerLevel level) {
        if (level == null || level.isClientSide()) return 0;
        int[] removed = {0};
        LoadedEntityScan.forEach(level, LivingEntity.class, entity -> {
            if (entity.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
                || entity.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
                || entity.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET)) {
                entity.discard();
                removed[0]++;
            }
        });
        return removed[0];
    }

    /**
     * Remove wave stragglers in the arena: undead with invuln/NoAI (incl. untagged after unfreeze tick stops), or any
     * undead monster still in {@link WaveMobAggroHandler}'s tracked spawn set (tags stripped but mob never died).
     */
    public static int sweepBrokenUndeadNearWave(ServerLevel level, BlockPos center, int horizontalRadius) {
        if (level == null || level.isClientSide() || center == null) return 0;
        AABB box = new AABB(
            center.getX() - horizontalRadius, level.getMinBuildHeight(), center.getZ() - horizontalRadius,
            center.getX() + horizontalRadius, level.getMaxBuildHeight(), center.getZ() + horizontalRadius);
        int removed = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            if (mob.isRemoved() || !mob.isAlive()) continue;
            if (!(mob instanceof Monster)) continue;
            if (mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) continue;
            boolean frozen = WaveMobFieldForce.isInvulnerableRaw(mob) || WaveMobFieldForce.isNoAiRaw(mob)
                || mob.isInvulnerable() || mob.isNoAi();
            boolean stillTracked = WaveMobAggroHandler.isTrackedWaveSpawnId(mob.getId());
            if (!frozen && !stillTracked) continue;
            mob.discard();
            removed++;
        }
        return removed;
    }

    /**
     * After pending is cleared: stop API, remove tagged mobs, clear client-tracked wave IDs, and sweep stragglers.
     * Does not spawn the plane — use when wave is cancelled or after {@link PendingAirdropStorage#deliverAt} took pending.
     */
    public static void purgeWaveEntityResidue(ServerLevel level, BlockPos waveAnchor) {
        if (level == null || level.isClientSide() || waveAnchor == null) return;
        discardAllWaveTaggedMobsInLevel(level);
        WaveMobAggroHandler.discardAllTrackedWaveMobsStillAlive(level);
        sweepBrokenUndeadNearWave(level, waveAnchor, WAVE_AREA_RANGE);
        WaveMobAggroHandler.clearAllWaveAggroState();
        // Important: do NOT schedule delayed sweeps in nuclear baseline mode.
        // Those delayed tasks can overlap with the next wave start and end up discarding freshly-spawned wave mobs,
        // which looks like "zombies spawn then instantly despawn".
        if (!RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) {
            RadiotowersMod.queueServerWork(5, () -> runPostWaveSweep(level, waveAnchor));
            for (int delay : new int[] { 20, 60, 120 }) {
                int d = delay;
                RadiotowersMod.queueServerWork(d, () -> runPostWaveSweep(level, waveAnchor));
            }
        }
    }

    private static void runPostWaveSweep(ServerLevel level, BlockPos waveAnchor) {
        if (level == null || level.isClientSide()) return;
        discardAllWaveTaggedMobsInLevel(level);
        WaveMobAggroHandler.discardAllTrackedWaveMobsStillAlive(level);
        sweepBrokenUndeadNearWave(level, waveAnchor, WAVE_AREA_RANGE);
    }

    /** Call right after a successful final-wave delivery: ensure API is stopped and no stray wave entities remain. */
    public static void cleanupAfterRadioTowersWaveFinished(ServerLevel level, BlockPos waveAnchor) {
        logWaveDeliverySnapshot(level, waveAnchor, "cleanup_beforeEndWave", PendingAirdropStorage.getPending(level, waveAnchor));
        endWaveAfterDeliver();
        purgeWaveEntityResidue(level, waveAnchor);
        logWaveDeliverySnapshot(level, waveAnchor, "cleanup_afterPurge", PendingAirdropStorage.getPending(level, waveAnchor));
    }

    /** Count only wave-spawned Zombie/Drowned in the area. Uses RING_POSITION_SET so our replacements (which have that tag only) are counted; WAVE_SPAWN_TAG/REPLACEMENT_TAG are removed after processing. */
    private static int countWaveZombiesInArea(ServerLevel level, BlockPos waveStartPos) {
        AABB box = new AABB(
            waveStartPos.getX() - WAVE_AREA_RANGE, level.getMinBuildHeight(), waveStartPos.getZ() - WAVE_AREA_RANGE,
            waveStartPos.getX() + WAVE_AREA_RANGE, level.getMaxBuildHeight(), waveStartPos.getZ() + WAVE_AREA_RANGE);
        return (int) level.getEntitiesOfClass(LivingEntity.class, box).stream()
            .filter(e -> e != null && e.isAlive() && !e.isRemoved())
            .filter(e -> e.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET)
                || e.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
                || e.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG))
            .count();
    }

    /** Discard any still-alive wave mobs in the arena that are tagged for our wave logic. */
    private static int discardRemainingWaveMobsInArea(ServerLevel level, BlockPos waveStartPos) {
        AABB box = new AABB(
            waveStartPos.getX() - WAVE_AREA_RANGE, level.getMinBuildHeight(), waveStartPos.getZ() - WAVE_AREA_RANGE,
            waveStartPos.getX() + WAVE_AREA_RANGE, level.getMaxBuildHeight(), waveStartPos.getZ() + WAVE_AREA_RANGE);
        int removed = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == null || e.isRemoved() || !e.isAlive()) continue;
            if (!e.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET)
                && !e.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
                && !e.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)) continue;
            e.discard();
            removed++;
        }
        return removed;
    }

    /**
     * Time-based wave end: after 3 minutes per wave, either complete (if all zombies dead) or fail (time ran out).
     * Time ran out with zombies left = fail: cancel airdrop, no next wave, no delivery.
     */
    public static void checkTimeBasedWaveEnd(ServerLevel level) {
        if (level == null || level.isClientSide()) return;
        long now = level.getGameTime();
        long waveDurationTicks = AirdropDifficultyTier.getTicksPerWave();
        PendingAirdropStorage.forEachPendingIn(level, (waveStartPos, p) -> {
            if (p.isEslManaged()) return;
            long waveAge = now - p.currentWaveStartedAtGameTime;
            if (waveAge < MIN_TICKS_BEFORE_DELIVER) return;
            if (waveAge < waveDurationTicks) return;
            if (p.lastWaveCompletedAtGameTime != 0 && (now - p.lastWaveCompletedAtGameTime) < PendingAirdropStorage.WAVE_COMPLETED_COOLDOWN_TICKS)
                return; // already processed (avoid duplicate)
            if (p.killedCountThisWave >= p.maxZombiesPerWave) onWaveCompleted(level, waveStartPos);
            else onWaveFailedTimeout(level, waveStartPos);
        });
    }
}
