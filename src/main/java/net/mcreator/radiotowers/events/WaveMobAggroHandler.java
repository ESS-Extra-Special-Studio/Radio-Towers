package net.mcreator.radiotowers.events;

import net.neoforged.fml.common.EventBusSubscriber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.init.RadiotowersModEntities;
import net.mcreator.radiotowers.util.LoadedEntityScan;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.WaveMobFieldForce;
import net.mcreator.radiotowers.integration.WaveSpawnTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When Zombie Waves API spawns mobs: (1) cap total spawns per wave to our tier max,
 * (2) place them on a ring around the tower on solid ground so they can close in,
 * (3) make them aggro the player. Re-snaps only mobs that are stuck (in air, lava, inside blocks, inside tower) — not normal swimming.
 * We run at LOWEST priority so we execute after the API (and any other mod) sets spawn position,
 * then place immediately at an evenly spaced ring position so zombies spawn in the right place first time.
 * Periodic resnap still catches any that slip through (e.g. API sets position after us).
 * Registered in commonSetup (not @EventBusSubscriber) so this class is not loaded during CONSTRUCT.
 */
public class WaveMobAggroHandler {

    public WaveMobAggroHandler() {}

    /** Set to false to disable all resnap/placement. */
    private static final boolean RESNAP_ENABLED = true;
    /** Set to false to disable agro (target player/tower) and respawn-if-stuck for testing. Invuln/NoAI clearing still runs. */
    private static final boolean AGRO_AND_STUCK_RESPAWN_ENABLED = true;
    /** Replace API-spawned wave mobs with vanilla Zombies so aggro works and kill @e[type=zombie] finds them all. */
    private static final boolean REPLACE_WAVE_MOBS_WITH_VANILLA = false;
    /** Ticks to wait before resnapping the same mob again — only resnap when truly stuck, not constantly. */
    private static final int RESNAP_COOLDOWN_TICKS = 40;

    /** Zombies spawn on a ring at this radius (blocks) so they close in toward the tower/player. Slightly extended from 20 (was 48 originally). */
    private static final int SPAWN_RING_RADIUS = 30;
    /** Mobs actually inside a solid block or stuck in air get moved to the ring; we do NOT resnap just for being close to tower (that caused constant teleporting). */
    /** Max blocks to scan down from heightmap to find solid ground (avoid treetops/slabs). */
    private static final int GROUND_SEARCH_DEPTH = 40;
    /** Max blocks to scan up from ground to find air (avoid spawning inside leaves). */
    private static final int STANDING_SEARCH_UP = 16;
    /** Re-check wave mobs every tick so sky mobs get resnapped immediately. */
    private static final int TICK_INTERVAL = 1;
    /** Horizontal range for EntityJoinLevel and processWaveMobs: catch API spawns that land far from tower. */
    private static final int WAVE_MOB_RANGE = 320;
    /**
     * Must match how far {@link net.mcreator.radiotowers.mixin.LevelTagWaveSpawnMixin} can tag API zombies (~384)
     * and {@link #WAVE_MOB_RANGE}, or join-time adoption sees {@code pending == null} and skips ring placement / spawn
     * counting while the mob is still tagged — leaving stragglers far from the tower or in caves.
     */
    private static final int UNTRACKED_WAVE_CAPTURE_RANGE = 320;
    /** Only auto-capture untracked joins near wave start; avoids late ambient mob joins consuming wave budget. */
    private static final int UNTRACKED_CAPTURE_WINDOW_TICKS = 20 * 30;
    /** Only resnap "stuck" (inside block/lava) when mob is within this range of wave start — pull stuck mobs from further out. */
    private static final int RESNAP_STUCK_RANGE = 80;
    /** Ticks a mob can be stuck in sky before we discard it so the wave can complete. */
    private static final int MAX_TICKS_IN_SKY_BEFORE_DISCARD = 100;
    private static final Map<Integer, Integer> TICKS_IN_SKY = new ConcurrentHashMap<>();
    /** Last game time we resnapped this mob; used so we don't resnap constantly. */
    private static final Map<Integer, Long> LAST_RESNAP_AT = new ConcurrentHashMap<>();
    /** First game time we saw this wave mob; for first GRACE_PERIOD_TICKS we resnap every tick if in sky (no cooldown). */
    private static final Map<Integer, Long> FIRST_SEEN_AT = new ConcurrentHashMap<>();
    /** For this many ticks after spawn, resnap sky mobs every tick (no cooldown) to fight API re-setting position. */
    private static final int GRACE_PERIOD_TICKS = 80;
    /** Defer placement to next tick so we overwrite API position (which may be set after us or to sky). */
    private static final List<PendingPlacement> DEFERRED_PLACEMENTS = new ArrayList<>();
    /** After resnapping a sky mob, schedule another placement next tick to overwrite late API position. */
    private static final List<DeferredResnap> DEFERRED_RESNAPS = new ArrayList<>();
    /** Entity IDs that were spawned by a wave (EntityJoinLevel). Only these get placement/clear-invuln — not pre-existing mobs in the area. */
    private static final Set<Integer> WAVE_SPAWNED_ENTITY_IDS = ConcurrentHashMap.newKeySet();
    /** Entity IDs for which EntitySetPosMixin already did a one-time sky->ground fix (so we don't keep re-teleporting). */
    public static final Set<Integer> SKY_POSITION_ALREADY_FIXED = ConcurrentHashMap.newKeySet();
    /** Last game time this mob had line of sight to the wave player — so we don't flip to tower on brief obstruction. */
    private static final Map<Integer, Long> LAST_SAW_PLAYER_AT = new ConcurrentHashMap<>();
    /** Last game time we issued a path-to-tower for this mob — avoid re-issuing every tick so they can path around obstacles/jump. */
    private static final Map<Integer, Long> LAST_PATH_TO_TOWER_AT = new ConcurrentHashMap<>();
    /** Last game time we issued a path-to-player for this mob — avoid re-issuing every tick to prevent spinning on the spot. */
    private static final Map<Integer, Long> LAST_PATH_TO_PLAYER_AT = new ConcurrentHashMap<>();
    /** Last distance to tower (squared) for stuck detection. */
    private static final Map<Integer, Double> LAST_DIST_SQ_TO_TOWER = new ConcurrentHashMap<>();
    /** Last game time this mob got closer to tower or had valid target (for stuck detection). */
    private static final Map<Integer, Long> LAST_PROGRESS_TOWARD_GOAL_AT = new ConcurrentHashMap<>();
    /** Ticks without progress toward tower/player before we consider mob stuck and respawn to ring. */
    /** No progress toward tower/player for this many ticks (5 sec) → respawn to ring (does not decrement kill count). */
    private static final int STUCK_NO_PROGRESS_TICKS = 100;
    /** Re-issue path to tower at most every this many ticks so pathfinder can work (go around, jump); longer = less spinning. */
    private static final int PATH_TO_TOWER_INTERVAL_TICKS = 120;
    /** Re-path to player at most this often so mobs don't spin from constant path recalc. */
    private static final int PATH_TO_PLAYER_INTERVAL_TICKS = 120;
    /** Cooldown before respawning a mob that has no line of sight to player or tower. */
    private static final int NO_LINE_OF_SIGHT_RESPAWN_COOLDOWN_TICKS = 60;
    /** Last game time we respawned this mob for having no line of sight to player or tower. */
    private static final Map<Integer, Long> LAST_NO_LINE_OF_SIGHT_RESPAWN_AT = new ConcurrentHashMap<>();

    /** Top solid Y to start {@link #findGroundY} from at this column (heightmap; works on high terrain, unlike old Y<=80 cap). */
    private static int columnTopSolidForGroundScan(ServerLevel level, int x, int z) {
        int minY = level.getMinBuildHeight();
        int airAbove = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return Math.max(minY, Math.min(airAbove - 1, level.getMaxBuildHeight() - 1));
    }

    /** Feet Y clearly above this column's outdoor surface — typical bogus API sky spawn (not a mountain surface). */
    private static boolean isFloatingAboveColumnSurface(ServerLevel level, int x, int z, double feetY) {
        int hm = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return feetY > (double) hm + 12;
    }

    private static boolean mobYSuggestsBrokenApiSkySpawn(ServerLevel level, Mob mob) {
        BlockPos p = mob.blockPosition();
        return mob.getY() > (double) level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) + 24;
    }

    /** Pre-add mixin: Y clearly above local outdoor surface (not valid mountain ground). */
    public static boolean entityYLooksLikeSkyHighApiSpawn(ServerLevel level, net.minecraft.world.entity.Entity entity) {
        BlockPos p = entity.blockPosition();
        return entity.getY() > (double) level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) + 16;
    }

    /** True if this entity was spawned by the current wave (used by mixins e.g. fire immunity). */
    public static boolean isWaveSpawnedMob(Entity entity) {
        return entity != null && WAVE_SPAWNED_ENTITY_IDS.contains(entity.getId());
    }

    /** True if this id is still registered as a wave spawn (NBT tags may have been stripped). */
    public static boolean isTrackedWaveSpawnId(int entityId) {
        return WAVE_SPAWNED_ENTITY_IDS.contains(entityId);
    }

    /**
     * Discard every still-loaded mob we registered for the wave. Catches stragglers that lost Radiotowers tags
     * but never died (e.g. stuck on leaves), which {@link net.mcreator.radiotowers.integration.ZombieWavesAPILoader}
     * would otherwise miss when completing waves.
     */
    public static int discardAllTrackedWaveMobsStillAlive(ServerLevel level) {
        if (level == null || level.isClientSide()) return 0;
        int removed = 0;
        for (int id : new ArrayList<>(WAVE_SPAWNED_ENTITY_IDS)) {
            Entity e = level.getEntity(id);
            if (e == null || e.isRemoved() || !(e instanceof LivingEntity le) || !le.isAlive()) {
                WAVE_SPAWNED_ENTITY_IDS.remove(id);
                continue;
            }
            if (!(e instanceof Mob mob) || !(mob instanceof Monster)) {
                WAVE_SPAWNED_ENTITY_IDS.remove(id);
                continue;
            }
            mob.discard();
            WAVE_SPAWNED_ENTITY_IDS.remove(id);
            removed++;
        }
        return removed;
    }

    /** Count of wave mobs in WAVE_SPAWNED_ENTITY_IDS that are alive in this level. Use to avoid ending the wave while we still have mobs. */
    public static int countAliveWaveMobsInLevel(ServerLevel level) {
        if (level == null || level.isClientSide()) return 0;
        int count = 0;
        for (Integer id : WAVE_SPAWNED_ENTITY_IDS) {
            Entity e = level.getEntity(id);
            if (e != null && !e.isRemoved() && e instanceof LivingEntity le && le.isAlive()) count++;
        }
        return count;
    }

    /** Count of living Monsters in this level that have RING_POSITION_SET (wave mobs), when there is a pending wave. Use so we never deliver while any wave mob exists even if not in WAVE_SPAWNED_ENTITY_IDS (e.g. ghosts). */
    public static int countAliveWaveMobsInLevelByTag(ServerLevel level) {
        if (level == null || level.isClientSide()) return 0;
        if (PendingAirdropStorage.getAnyPendingInLevel(level) == null) return 0;
        int[] count = {0};
        LoadedEntityScan.forEach(level, Monster.class, mob -> {
            if (mob.isAlive() && !mob.isRemoved()
                && (mob.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET)
                    || mob.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)))
                count[0]++;
        });
        return count[0];
    }

    /** Call from ServerTickEvent.END every tick: force unfreeze all Monsters in levels with a pending wave. Runs after all entity ticks so we overwrite any invuln/NoAI set by the API. */
    public static void forceUnfreezeAllWaveMonsters(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            if (PendingAirdropStorage.getAnyPendingInLevel(level) == null) continue;
            LoadedEntityScan.forEach(level, Monster.class, mob -> {
                if (mob.isRemoved()) return;
                if (mob.getType() == RadiotowersModEntities.PLANEENTITY.get()) return;
                if (mob.getType() == RadiotowersModEntities.AIRDROPENTITY.get()) return;
                if (mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) return;
                mob.setInvulnerable(false);
                mob.setNoAi(false);
                WaveMobFieldForce.forceOff(mob);
            });
        }
    }

    /** Called when a level unloads: discard all wave mobs in that level (so they don't persist on save) and clear tracking. */
    public static void onLevelUnload(ServerLevel level) {
        if (level == null || level.isClientSide()) return;
        List<Entity> toDiscard = new ArrayList<>();
        LoadedEntityScan.forEach(level, Entity.class, e -> {
            if (e.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET)) toDiscard.add(e);
        });
        for (Entity e : toDiscard) {
            int id = e.getId();
            e.discard();
            WAVE_SPAWNED_ENTITY_IDS.remove(id);
            FIRST_SEEN_AT.remove(id);
            LAST_RESNAP_AT.remove(id);
            LAST_SAW_PLAYER_AT.remove(id);
            LAST_PATH_TO_TOWER_AT.remove(id);
            LAST_PATH_TO_PLAYER_AT.remove(id);
            LAST_DIST_SQ_TO_TOWER.remove(id);
            LAST_PROGRESS_TOWARD_GOAL_AT.remove(id);
            TICKS_IN_SKY.remove(id);
            SKY_POSITION_ALREADY_FIXED.remove(id);
            LAST_NO_LINE_OF_SIGHT_RESPAWN_AT.remove(id);
        }
    }

    /** Run during each living entity's tick — clear invuln/NoAI first (HIGHEST so we run before entity logic), then resnap sky; set target to player or tower. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.level().isClientSide()) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (entity instanceof Player) return;
        if (!(entity instanceof Monster mob)) return;
        if (AirdropConfig.isWaveAggroExcluded(mob.getType())) return;
        // Only apply wave mob handling to undead (prevents touching creepers, witches, endermen, etc.).
        if (mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) return;
        // Adopt any Monster with wave tags that we didn't register (e.g. added via path we don't hook) so we clear invuln/NoAI and count their death
        boolean inSet = WAVE_SPAWNED_ENTITY_IDS.contains(entity.getId());
        if (!inSet && (mob.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG) || mob.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET))) {
            if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).equals(net.minecraft.resources.ResourceLocation.parse("radiotowers:planeentity"))) return;
            PendingAirdropStorage.Pending pending = PendingAirdropStorage.getPendingNear(level, mob.blockPosition(), WAVE_MOB_RANGE);
            if (pending == null) pending = PendingAirdropStorage.getAnyPendingInLevel(level);
            if (pending != null) {
                WAVE_SPAWNED_ENTITY_IDS.add(mob.getId());
                long now = level.getGameTime();
                LAST_PROGRESS_TOWARD_GOAL_AT.put(mob.getId(), now);
                LAST_DIST_SQ_TO_TOWER.put(mob.getId(), mob.distanceToSqr(pending.waveStartPos.getX() + 0.5, pending.waveStartPos.getY(), pending.waveStartPos.getZ() + 0.5));
            }
        }
        if (!WAVE_SPAWNED_ENTITY_IDS.contains(entity.getId())) {
            // Every tick: unfreeze ANY Monster near a pending wave (catches API spawns that never got our tag)
            PendingAirdropStorage.Pending near = PendingAirdropStorage.getPendingNear(level, mob.blockPosition(), 64);
            if (near != null) {
                mob.setInvulnerable(false);
                mob.setNoAi(false);
                WaveMobFieldForce.forceOff(mob);
            }
            return;
        }
        // Force invulnerable and NoAI off every tick (setters + reflection) so API-spawned mobs never stay frozen/unkillable
        mob.setInvulnerable(false);
        mob.setNoAi(false);
        WaveMobFieldForce.forceOff(mob);
        if (entity.getRemainingFireTicks() > 0) entity.setRemainingFireTicks(0);
        if (!AGRO_AND_STUCK_RESPAWN_ENABLED) return;
        PendingAirdropStorage.Pending pending = PendingAirdropStorage.getPendingNear(level, entity.blockPosition(), WAVE_MOB_RANGE);
        if (pending == null) pending = PendingAirdropStorage.getAnyPendingInLevel(level);
        if (pending == null) return;
        BlockPos waveStartPos = pending.waveStartPos;
        // Match processWaveMobs: nuclear baseline skips per-tick sky→ring teleport that fights Berezka's spawn position.
        if (!RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) {
            boolean inSky = isTrulyInSkyLiving(level, entity, waveStartPos);
            if (RESNAP_ENABLED && inSky) {
                long now = level.getGameTime();
                int mobId = entity.getId();
                FIRST_SEEN_AT.putIfAbsent(mobId, now);
                Long firstSeen = FIRST_SEEN_AT.get(mobId);
                boolean inGracePeriod = firstSeen != null && (now - firstSeen) < GRACE_PERIOD_TICKS;
                Long lastResnap = LAST_RESNAP_AT.get(mobId);
                boolean onCooldown = !inGracePeriod && lastResnap != null && (now - lastResnap) < RESNAP_COOLDOWN_TICKS;
                if (!onCooldown || inGracePeriod) {
                    placeLivingOnRingAtGround(level, entity, waveStartPos, true);
                    LAST_RESNAP_AT.put(mobId, now);
                }
            }
        }
        setTargetToActivatingPlayerOrTower(level, mob, pending);
    }

    /** Run at LOWEST so we run after the API and any other mod that sets invuln/NoAI. Force unfreeze every tick so ghost zombies never persist. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingTickLowest(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        // Nuclear baseline: do not run global unfreeze ticks; we only want initial placement + minimal bookkeeping.
        if (RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.level().isClientSide()) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!(entity instanceof Monster)) return;
        if (entity instanceof Player) return;
        if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(net.minecraft.resources.ResourceLocation.parse("radiotowers:planeentity"))) return;
        if (PendingAirdropStorage.getAnyPendingInLevel(level) == null) return;
        Mob mob = (Mob) entity;
        mob.setInvulnerable(false);
        mob.setNoAi(false);
        WaveMobFieldForce.forceOff(mob);
        if (entity.getRemainingFireTicks() > 0) entity.setRemainingFireTicks(0);
    }

    /** LOWEST = run after the API and other mods. Capture likely wave spawns close to the active wave, not every monster in the dimension. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!(mob instanceof Monster)) return;

        // Restart-safe cleanup: if an old wave-tagged mob is loaded from disk while no wave is pending,
        // discard it immediately so "ghost" leftovers cannot survive into the next session's wave.
        boolean hasWaveTag =
            mob.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
                || mob.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
                || mob.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET);
        if (PendingAirdropStorage.getAnyPendingInLevel(level) == null) {
            if (hasWaveTag || isWaveSpawnedMob(mob)) {
                WaveMobFieldForce.forceOff(mob);
                mob.discard();
            }
            return;
        }

        // Our replacement vanilla Zombie: register and place, do not increment spawn count again
        if (mob.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)) {
            mob.getPersistentData().remove(WaveSpawnTag.REPLACEMENT_TAG);
            PendingAirdropStorage.Pending pending = PendingAirdropStorage.getAnyPendingInLevel(level);
            if (pending == null) pending = PendingAirdropStorage.getOnlyPendingInLevel(level);
            if (pending == null) return;
            registerWaveMob(level, mob, pending);
            return;
        }

        // Detect likely API wave spawns without LevelTagWaveSpawnMixin (that mixin caused ghost zombies).
        // Keep this radius tight so random monsters elsewhere are not treated as part of the wave.
        BlockPos mobPos = mob.blockPosition();
        PendingAirdropStorage.Pending pending = PendingAirdropStorage.getPendingNear(level, mobPos, UNTRACKED_WAVE_CAPTURE_RANGE);
        if (pending == null) return;
        long waveAge = level.getGameTime() - pending.currentWaveStartedAtGameTime;
        if (waveAge < 0 || waveAge > UNTRACKED_CAPTURE_WINDOW_TICKS) return;
        var mobTypeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (mobTypeId != null && mobTypeId.equals(net.minecraft.resources.ResourceLocation.parse("radiotowers:planeentity"))) return;
        if (AirdropConfig.isWaveAggroExcluded(mob.getType())) return;
        // Only capture zombie-family wave mobs; ignore ambient creepers/skeletons around the area.
        if (mob.getType() != EntityType.ZOMBIE && mob.getType() != EntityType.DROWNED) return;
        // Untagged joins: avoid adopting random deep-cave zombies near the tower column. Tagged API spawns skip this.
        if (!mob.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
            && Math.abs(mob.getY() - pending.waveStartPos.getY()) > 40) {
            return;
        }

        // Only count a slot if this join is actually accepted. Over-cap discards must not consume spawn budget.
        int nextSpawnCount = pending.spawnCountThisWave;
        if (!mob.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET)) nextSpawnCount++;
        if (nextSpawnCount > pending.maxZombiesPerWave) {
            net.mcreator.radiotowers.RadiotowersMod.LOGGER.warn(
                "[WaveDebug] over-cap discard id={} type={} pos={} pendingPos={} spawnCount={} max={}",
                mob.getId(),
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()),
                mob.blockPosition(),
                pending.waveStartPos,
                pending.spawnCountThisWave,
                pending.maxZombiesPerWave
            );
            // Never discard here; if we're over cap we just ignore adoption so we don't delete legitimate spawns.
            return;
        }
        pending.spawnCountThisWave = nextSpawnCount;

        // Always replace with vanilla Zombie we create so no API entity (and no API-set invuln/NoAI) ever stays in the world
        // Do not treat water as "broken" here — swimming is valid; LevelAddEntityMixin still replaces API spawns that join already underwater.
        boolean likelyBrokenApiSpawn = WaveMobFieldForce.isInvulnerableRaw(mob)
            || WaveMobFieldForce.isNoAiRaw(mob)
            || mob.isInLava()
            || mobYSuggestsBrokenApiSkySpawn(level, mob);
        if (REPLACE_WAVE_MOBS_WITH_VANILLA && likelyBrokenApiSpawn) {
            int index = pending.spawnCountThisWave - 1;
            Vec3 pos = getRingSpawnPosition(level, pending.waveStartPos, index, pending.maxZombiesPerWave);
            Zombie vanilla = EntityType.ZOMBIE.create(level);
            if (vanilla != null) {
                vanilla.setPos(pos.x, pos.y, pos.z);
                vanilla.setDeltaMovement(Vec3.ZERO);
                vanilla.setInvulnerable(false);
                vanilla.setNoAi(false);
                WaveMobFieldForce.forceOff(vanilla);
                vanilla.getPersistentData().putBoolean(WaveSpawnTag.REPLACEMENT_TAG, true);
                vanilla.getPersistentData().putBoolean(WaveSpawnTag.RING_POSITION_SET, true); // so LevelAddEntityMixin skips and does not re-increment
                if (level.addFreshEntity(vanilla)) {
                    mob.discard();
                    // Replacement will be handled in the next EntityJoinLevel for vanilla (REPLACEMENT_TAG branch)
                    return;
                }
            }
            // Replacement add failed: keep original mob so we do not lose a spawn slot.
            registerWaveMob(level, mob, pending);
            return;
        }

        registerWaveMob(level, mob, pending);
    }

    /** Register a wave mob: place on ring, deferred placements for sky resnap, target player/tower. Clear invuln/NoAI immediately (setters + reflection) so API-spawned mobs never start frozen/unkillable. Mark so only we decrement on death. */
    private static void registerWaveMob(ServerLevel level, Mob mob, PendingAirdropStorage.Pending pending) {
        WAVE_SPAWNED_ENTITY_IDS.add(mob.getId());
        mob.setInvulnerable(false);
        mob.setNoAi(false);
        WaveMobFieldForce.forceOff(mob);
        long now = level.getGameTime();
        LAST_PROGRESS_TOWARD_GOAL_AT.put(mob.getId(), now);
        LAST_DIST_SQ_TO_TOWER.put(mob.getId(), mob.distanceToSqr(pending.waveStartPos.getX() + 0.5, pending.waveStartPos.getY(), pending.waveStartPos.getZ() + 0.5));
        if (RESNAP_ENABLED) {
            FIRST_SEEN_AT.put(mob.getId(), now);
            int index = pending.spawnCountThisWave - 1;
            boolean placed = tryPlaceOnRingAtGround(level, mob, pending.waveStartPos, index, pending.maxZombiesPerWave);
            if (!placed) {
                // Fallback: scan multiple radii/angles to find dry ground (still rejects water/holes).
                placeOnRingAtGround(level, mob, pending.waveStartPos, true);
            }
            // Count this mob for HUD + wave completion logic.
            mob.getPersistentData().putBoolean(WaveSpawnTag.RING_POSITION_SET, true);
            if (AGRO_AND_STUCK_RESPAWN_ENABLED && !RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) {
                synchronized (DEFERRED_PLACEMENTS) {
                    for (long t : new long[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 18, 20, 25, 30, 35, 40 }) {
                        DEFERRED_PLACEMENTS.add(new PendingPlacement(level, mob.getId(), pending.waveStartPos, index, pending.maxZombiesPerWave, pending.playerWhoStarted, now + t));
                    }
                }
            }
        }
        if (AGRO_AND_STUCK_RESPAWN_ENABLED && mob instanceof Monster mon) setTargetToActivatingPlayerOrTower(level, mon, pending);
    }

    /** Process deferred placements (sky resnap only) at START of tick. Run at LOWEST so we run after API. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLevelTickStart(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!AGRO_AND_STUCK_RESPAWN_ENABLED || !RESNAP_ENABLED) return;
        long now = level.getGameTime();
        processDeferredPlacements(level, now);
        List<DeferredResnap> resnapToRemove = new ArrayList<>();
        synchronized (DEFERRED_RESNAPS) {
            for (DeferredResnap r : DEFERRED_RESNAPS) {
                if (r.level != level || r.runAtTick != now) continue;
                resnapToRemove.add(r);
                var entity = level.getEntity(r.entityId);
                if (entity instanceof Monster mob && mob.isAlive() && !mob.isRemoved() && isWayAboveAndFloating(level, mob, r.waveStartPos)) {
                    placeOnRingAtGround(level, mob, r.waveStartPos, false);
                }
            }
            DEFERRED_RESNAPS.removeAll(resnapToRemove);
        }
    }

    /** Run deferred placements when mob is in sky (placement only, no invuln/NoAI). */
    private static void processDeferredPlacements(ServerLevel level, long now) {
        List<PendingPlacement> toRemove = new ArrayList<>();
        synchronized (DEFERRED_PLACEMENTS) {
            for (PendingPlacement p : DEFERRED_PLACEMENTS) {
                if (p.level != level || p.runAtTick != now) continue;
                toRemove.add(p);
                var entity = level.getEntity(p.entityId);
                if (!(entity instanceof Monster mob) || !mob.isAlive() || mob.isRemoved()) continue;
                if (isWayAboveAndFloating(level, mob, p.waveStartPos)) {
                    boolean placed = tryPlaceOnRingAtGround(level, mob, p.waveStartPos, p.index, p.maxZombies);
                    if (!placed) {
                        // If the single-angle placement failed/rejected, do a wider dry-ground scan.
                        placeOnRingAtGround(level, mob, p.waveStartPos, true);
                    }
                }
            }
            DEFERRED_PLACEMENTS.removeAll(toRemove);
        }
    }

    private static final class PendingPlacement {
        final ServerLevel level;
        final int entityId;
        final BlockPos waveStartPos;
        final int index;
        final int maxZombies;
        final UUID playerUuid;
        final long runAtTick;
        PendingPlacement(ServerLevel level, int entityId, BlockPos waveStartPos, int index, int maxZombies, UUID playerUuid, long runAtTick) {
            this.level = level;
            this.entityId = entityId;
            this.waveStartPos = waveStartPos;
            this.index = index;
            this.maxZombies = maxZombies;
            this.playerUuid = playerUuid;
            this.runAtTick = runAtTick;
        }
    }

    private static final class DeferredResnap {
        final ServerLevel level;
        final int entityId;
        final BlockPos waveStartPos;
        final long runAtTick;
        DeferredResnap(ServerLevel level, int entityId, BlockPos waveStartPos, long runAtTick) {
            this.level = level;
            this.entityId = entityId;
            this.waveStartPos = waveStartPos;
            this.runAtTick = runAtTick;
        }
    }

    /** Every tick at END: process deferred placements and resnap. LOWEST so we run after API. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLevelTickEnd(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (AGRO_AND_STUCK_RESPAWN_ENABLED && RESNAP_ENABLED) processDeferredPlacements(level, now);
        if (now % TICK_INTERVAL != 0) return;
        processWaveMobs(level);
    }

    /** Also run at START of tick. LOWEST so we run after API. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLevelTickStartResnap(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!AGRO_AND_STUCK_RESPAWN_ENABLED) return;
        if (level.getGameTime() % TICK_INTERVAL != 0) return;
        processWaveMobs(level);
    }

    /** Shared logic: find wave mobs in WAVE_MOB_RANGE, resnap sky/stuck, set target to player or tower. No invuln/NoAI clearing. */
    private static void processWaveMobs(ServerLevel level) {
        PendingAirdropStorage.forEachPendingIn(level, (waveStartPos, pending) -> {
            AABB box = new AABB(
                waveStartPos.getX() - WAVE_MOB_RANGE, level.getMinBuildHeight(), waveStartPos.getZ() - WAVE_MOB_RANGE,
                waveStartPos.getX() + WAVE_MOB_RANGE, level.getMaxBuildHeight(), waveStartPos.getZ() + WAVE_MOB_RANGE);
            int[] counts = new int[3];
            long now = level.getGameTime();
            for (Monster mob : level.getEntitiesOfClass(Monster.class, box)) {
                if (mob.isRemoved() || !mob.isAlive()) continue;
                int mobId = mob.getId();
                if (!WAVE_SPAWNED_ENTITY_IDS.contains(mob.getId())) {
                    // Adopt wave-tagged mobs that slipped through (e.g. different spawn path) so they get unfrozen and counted
                    boolean tagged = mob.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG) || mob.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET);
                    double captureDistSq = mob.distanceToSqr(waveStartPos.getX() + 0.5, waveStartPos.getY(), waveStartPos.getZ() + 0.5);
                    boolean inCaptureRange = captureDistSq <= UNTRACKED_WAVE_CAPTURE_RANGE * UNTRACKED_WAVE_CAPTURE_RANGE;
                    boolean likelyBrokenUntracked = WaveMobFieldForce.isInvulnerableRaw(mob)
                        || WaveMobFieldForce.isNoAiRaw(mob)
                        || mob.isInLava()
                        || mobYSuggestsBrokenApiSkySpawn(level, mob);
                    if (!tagged) {
                        // If an API mob slipped past EntityJoin replacement, replace it here before it can sit around frozen alongside our zombie.
                        if (REPLACE_WAVE_MOBS_WITH_VANILLA && inCaptureRange && likelyBrokenUntracked) {
                            replaceWithVanillaZombie(level, mob, pending, waveStartPos, mobId, now);
                            continue;
                        }
                        // Never hard-discard untracked zombie-family mobs here.
                        // Some legitimate wave spawns can briefly appear in sky before adoption/placement settles.
                        // Deleting them here causes "spawn then instant despawn" and under-filled waves.
                        continue;
                    }
                    if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).equals(net.minecraft.resources.ResourceLocation.parse("radiotowers:planeentity"))) continue;
                    WAVE_SPAWNED_ENTITY_IDS.add(mob.getId());
                    // Ensure this adopted wave mob is always counted for HUD + wave completion.
                    mob.getPersistentData().putBoolean(WaveSpawnTag.RING_POSITION_SET, true);
                    long t = level.getGameTime();
                    LAST_PROGRESS_TOWARD_GOAL_AT.put(mob.getId(), t);
                    LAST_DIST_SQ_TO_TOWER.put(mob.getId(), mob.distanceToSqr(waveStartPos.getX() + 0.5, waveStartPos.getY(), waveStartPos.getZ() + 0.5));
                    mob.setInvulnerable(false);
                    mob.setNoAi(false);
                    WaveMobFieldForce.forceOff(mob);
                }
                // Force invulnerable and NoAI off (setters + reflection) so API never leaves wave mobs frozen/unkillable
                mob.setInvulnerable(false);
                mob.setNoAi(false);
                WaveMobFieldForce.forceOff(mob);
                if (mob.getRemainingFireTicks() > 0) mob.setRemainingFireTicks(0);
                if (REPLACE_WAVE_MOBS_WITH_VANILLA && mob.getType() != EntityType.ZOMBIE && mob.getType() != EntityType.DROWNED) {
                    replaceWithVanillaZombie(level, mob, pending, waveStartPos, mobId, now);
                    continue;
                }
                counts[0]++;
                if (AGRO_AND_STUCK_RESPAWN_ENABLED && !RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) {
                    setTargetToActivatingPlayerOrTower(level, mob, pending);
                    checkStuckAndRespawnToRing(level, mob, pending);
                    checkNoLineOfSightAndRespawn(level, mob, pending, waveStartPos, now);
                }
                Long firstSeen = FIRST_SEEN_AT.get(mobId);
                boolean inGracePeriod = firstSeen != null && (now - firstSeen) < GRACE_PERIOD_TICKS;
                Long lastResnap = LAST_RESNAP_AT.get(mobId);
                boolean onCooldown = !inGracePeriod && lastResnap != null && (now - lastResnap) < RESNAP_COOLDOWN_TICKS;
                boolean inSky = isWayAboveAndFloating(level, mob, waveStartPos);
                boolean stuck = needsResnap(level, mob, waveStartPos);

                // Nuclear baseline: never do periodic ring resnaps/teleports. We only want the one-time placement
                // at join time (registerWaveMob) that fixes treetop spawns.
                if (RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) {
                    // still clear any counters so stale state doesn't build up
                    TICKS_IN_SKY.remove(mobId);
                } else if (AGRO_AND_STUCK_RESPAWN_ENABLED && RESNAP_ENABLED && !onCooldown && inSky) {
                    int ticks = TICKS_IN_SKY.merge(mobId, 1, Integer::sum);
                    if (ticks >= MAX_TICKS_IN_SKY_BEFORE_DISCARD) {
                        // Don't discard: force-place again so we don't lose wave mobs.
                        placeOnRingAtGround(level, mob, waveStartPos, false);
                        TICKS_IN_SKY.remove(mobId);
                        LAST_RESNAP_AT.put(mobId, now);
                        continue;
                    }
                    placeOnRingAtGround(level, mob, waveStartPos, false);
                    TICKS_IN_SKY.remove(mobId);
                    LAST_RESNAP_AT.put(mobId, now);
                    counts[1]++;
                } else if (AGRO_AND_STUCK_RESPAWN_ENABLED && RESNAP_ENABLED && !onCooldown && stuck) {
                    double distSq = mob.distanceToSqr(waveStartPos.getX() + 0.5, waveStartPos.getY(), waveStartPos.getZ() + 0.5);
                    if (distSq <= RESNAP_STUCK_RANGE * RESNAP_STUCK_RANGE) {
                        placeOnRingAtGround(level, mob, waveStartPos, false);
                        LAST_RESNAP_AT.put(mobId, now);
                        counts[2]++;
                    }
                    TICKS_IN_SKY.remove(mobId);
                }
            }
                // Do not periodically discard "excess" tagged mobs: Berezka's WavesManager refills
                // remainEnemysCount / spawns when we discard, so this caused a spawn→despawn loop.
            int aliveWaveMobs = 0;
            for (Integer id : WAVE_SPAWNED_ENTITY_IDS) {
                var e = level.getEntity(id);
                if (e != null && !e.isRemoved() && e instanceof LivingEntity le && le.isAlive()) aliveWaveMobs++;
            }
            if (now % 20 == 0) {
                long waveAge = now - pending.currentWaveStartedAtGameTime;
                if (waveAge >= 80) {
                    ServerPlayer activatingPlayer = level.getServer() != null ? level.getServer().getPlayerList().getPlayer(pending.playerWhoStarted) : null;
                    boolean playerInRange = activatingPlayer != null && activatingPlayer.level() == level
                        && activatingPlayer.distanceToSqr(waveStartPos.getX() + 0.5, waveStartPos.getY(), waveStartPos.getZ() + 0.5) <= 384 * 384;
                    // Only sync when ALL wave mobs are dead (our set AND tag count), AND wave has run at least 60s.
                    // Requiring tag count 0 prevents count dropping without kills when mobs exist but aren't in our set (e.g. not yet adopted).
                    final long MIN_WAVE_AGE_BEFORE_SYNC_TICKS = 60 * 20; // 60 seconds
                    int aliveByTag = countAliveWaveMobsInLevelByTag(level);
                    // Keep local counting authoritative; we intentionally disabled API remainEnemysCount sync.
                }
            }
        });
        TICKS_IN_SKY.keySet().removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive());
        });
        LAST_RESNAP_AT.keySet().removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive());
        });
        FIRST_SEEN_AT.keySet().removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive());
        });
        LAST_SAW_PLAYER_AT.keySet().removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive());
        });
        LAST_PATH_TO_TOWER_AT.keySet().removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive());
        });
        LAST_PATH_TO_PLAYER_AT.keySet().removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive());
        });
        LAST_DIST_SQ_TO_TOWER.keySet().removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive());
        });
        LAST_PROGRESS_TOWARD_GOAL_AT.keySet().removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive());
        });
        WAVE_SPAWNED_ENTITY_IDS.removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive());
        });
        SKY_POSITION_ALREADY_FIXED.removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive());
        });
        LAST_NO_LINE_OF_SIGHT_RESPAWN_AT.keySet().removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive());
        });
    }

    /** Reset all wave tick state when an airdrop wave finishes (pending removed). */
    public static void clearAllWaveAggroState() {
        WAVE_SPAWNED_ENTITY_IDS.clear();
        TICKS_IN_SKY.clear();
        LAST_RESNAP_AT.clear();
        FIRST_SEEN_AT.clear();
        LAST_SAW_PLAYER_AT.clear();
        LAST_PATH_TO_TOWER_AT.clear();
        LAST_PATH_TO_PLAYER_AT.clear();
        LAST_DIST_SQ_TO_TOWER.clear();
        LAST_PROGRESS_TOWARD_GOAL_AT.clear();
        SKY_POSITION_ALREADY_FIXED.clear();
        LAST_NO_LINE_OF_SIGHT_RESPAWN_AT.clear();
        synchronized (DEFERRED_PLACEMENTS) {
            DEFERRED_PLACEMENTS.clear();
        }
        synchronized (DEFERRED_RESNAPS) {
            DEFERRED_RESNAPS.clear();
        }
    }

    /** Replace a non-vanilla wave mob with a vanilla Zombie at same position so kill @e[type=zombie] finds them all. */
    private static void replaceWithVanillaZombie(ServerLevel level, Monster mob, PendingAirdropStorage.Pending pending, BlockPos waveStartPos, int oldId, long now) {
        double x = mob.getX(), y = mob.getY(), z = mob.getZ();
        WAVE_SPAWNED_ENTITY_IDS.remove(oldId);
        FIRST_SEEN_AT.remove(oldId);
        LAST_RESNAP_AT.remove(oldId);
        LAST_SAW_PLAYER_AT.remove(oldId);
        LAST_PATH_TO_TOWER_AT.remove(oldId);
        LAST_PATH_TO_PLAYER_AT.remove(oldId);
        LAST_DIST_SQ_TO_TOWER.remove(oldId);
        LAST_PROGRESS_TOWARD_GOAL_AT.remove(oldId);
        TICKS_IN_SKY.remove(oldId);
        SKY_POSITION_ALREADY_FIXED.remove(oldId);
        LAST_NO_LINE_OF_SIGHT_RESPAWN_AT.remove(oldId);
        mob.discard();
        Zombie vanilla = EntityType.ZOMBIE.create(level);
        if (vanilla != null) {
            vanilla.setPos(x, y, z);
            vanilla.setDeltaMovement(Vec3.ZERO);
            vanilla.setInvulnerable(false);
            vanilla.setNoAi(false);
            WaveMobFieldForce.forceOff(vanilla);
            // Mark so our counting/completion logic includes this replacement.
            vanilla.getPersistentData().putBoolean(WaveSpawnTag.REPLACEMENT_TAG, true);
            vanilla.getPersistentData().putBoolean(WaveSpawnTag.RING_POSITION_SET, true);
            if (level.addFreshEntity(vanilla)) {
                int newId = vanilla.getId();
                WAVE_SPAWNED_ENTITY_IDS.add(newId);
                FIRST_SEEN_AT.put(newId, now);
                LAST_PROGRESS_TOWARD_GOAL_AT.put(newId, now);
                LAST_DIST_SQ_TO_TOWER.put(newId, vanilla.distanceToSqr(waveStartPos.getX() + 0.5, waveStartPos.getY(), waveStartPos.getZ() + 0.5));
            }
        }
    }

    /** If mob has no line of sight to player or tower AND is physically stuck, respawn to ring. Avoid respawning moving mobs that are just behind a corner. */
    private static void checkNoLineOfSightAndRespawn(ServerLevel level, Monster mob, PendingAirdropStorage.Pending pending, BlockPos waveStartPos, long now) {
        if (!needsResnap(level, mob, waveStartPos)) return;
        ServerPlayer player = level.getServer() != null ? level.getServer().getPlayerList().getPlayer(pending.playerWhoStarted) : null;
        boolean inRange = player != null && player.isAlive() && mob.distanceTo(player) <= TARGET_PLAYER_RANGE;
        boolean canSeePlayer = inRange && mob.hasLineOfSight(player);
        Vec3 towerVec = new Vec3(waveStartPos.getX() + 0.5, waveStartPos.getY() + 1, waveStartPos.getZ() + 0.5);
        Vec3 from = mob.getEyePosition(1.0F);
        BlockHitResult hit = level.clip(new ClipContext(from, towerVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        double distToTower = from.distanceTo(towerVec);
        boolean canSeeTower = hit.getType() == HitResult.Type.MISS || from.distanceTo(hit.getLocation()) >= distToTower - 0.5;
        if (canSeePlayer || canSeeTower) return;
        int mobId = mob.getId();
        Long last = LAST_NO_LINE_OF_SIGHT_RESPAWN_AT.get(mobId);
        if (last != null && (now - last) < NO_LINE_OF_SIGHT_RESPAWN_COOLDOWN_TICKS) return;
        placeOnRingAtGround(level, mob, waveStartPos, true);
        LAST_NO_LINE_OF_SIGHT_RESPAWN_AT.put(mobId, now);
        LAST_RESNAP_AT.put(mobId, now);
    }

    /** True when mob is floating above ground or frozen (invulnerable/NoAI), so we resnap stuck/frozen mobs. */
    private static boolean isWayAboveAndFloating(ServerLevel level, Monster mob, BlockPos waveStartPos) {
        if (mob.isInvulnerable() || mob.isNoAi()) return true;
        return isTrulyInSky(level, mob, waveStartPos);
        //’s start (e.g. airdrop spawn height)
    }

    /** True only when mob has no solid below and is high up — never true for mobs standing on ground. */
    private static boolean isTrulyInSky(ServerLevel level, Monster mob, BlockPos waveStartPos) {
        return isTrulyInSkyLiving(level, mob, waveStartPos);
    }

    /** Same as isTrulyInSky but for any LivingEntity. Conservative: only "in sky" when clearly floating (Y > 100 and no solid within 5 below). */
    private static boolean isTrulyInSkyLiving(ServerLevel level, LivingEntity entity, BlockPos waveStartPos) {
        BlockPos pos = entity.blockPosition();
        if (level.getBlockState(pos.below()).blocksMotion()) return false;
        if (level.getBlockState(pos.below(2)).blocksMotion()) return false;
        if (level.getBlockState(pos.below(3)).blocksMotion()) return false;
        if (level.getBlockState(pos.below(4)).blocksMotion()) return false;
        if (level.getBlockState(pos.below(5)).blocksMotion()) return false;
        double y = entity.getY();
        if (y <= 100) return false; // only treat as sky when clearly high (e.g. mis-spawned in void)
        return true;
    }

    /** True when entity has no solid within 5 below and is above wave ground + 15. Used to discard stray API sky spawns we didn't replace. */
    private static boolean isInSkyForStrayCleanup(ServerLevel level, LivingEntity entity, BlockPos waveStartPos) {
        BlockPos pos = entity.blockPosition();
        if (level.getBlockState(pos.below()).blocksMotion()) return false;
        if (level.getBlockState(pos.below(2)).blocksMotion()) return false;
        if (level.getBlockState(pos.below(3)).blocksMotion()) return false;
        if (level.getBlockState(pos.below(4)).blocksMotion()) return false;
        if (level.getBlockState(pos.below(5)).blocksMotion()) return false;
        return entity.getY() > waveStartPos.getY() + 15;
    }

    private static boolean isWaveMobNear(ServerLevel level, Monster mob, BlockPos waveStartPos) {
        PendingAirdropStorage.Pending p = PendingAirdropStorage.getPendingNear(level, mob.blockPosition(), WAVE_MOB_RANGE);
        return p != null && p.waveStartPos.equals(waveStartPos);
    }

    /** Range within which we set wave mobs to target the activating player (increased for stronger aggro). */
    private static final int TARGET_PLAYER_RANGE = 160;
    /** Speed when pathing to tower when they can't see the player. */
    private static final double MOVE_TO_TOWER_SPEED = 1.2;

    /** Horizontal range for applying agro to pre-existing mobs when a wave starts. */
    private static final int PREEXISTING_AGRO_RANGE = 80;

    /**
     * When a wave starts, apply agro to monsters already in the area (not spawned by the wave).
     * Call once from ZombieWavesAPILoader.startWaveAt after the wave has started.
     */
    public static void applyAgroToPreExistingMobs(ServerLevel level, BlockPos waveStartPos, PendingAirdropStorage.Pending pending) {
        if (level == null || waveStartPos == null || pending == null) return;
        AABB box = new AABB(
            waveStartPos.getX() - PREEXISTING_AGRO_RANGE, level.getMinBuildHeight(), waveStartPos.getZ() - PREEXISTING_AGRO_RANGE,
            waveStartPos.getX() + PREEXISTING_AGRO_RANGE, level.getMaxBuildHeight(), waveStartPos.getZ() + PREEXISTING_AGRO_RANGE);
        for (Monster mob : level.getEntitiesOfClass(Monster.class, box)) {
            if (!mob.isAlive() || mob.isRemoved()) continue;
            if (AirdropConfig.isWaveAggroExcluded(mob.getType())) continue;
            if (WAVE_SPAWNED_ENTITY_IDS.contains(mob.getId())) continue; // API-spawned; they get targeting elsewhere
            setTargetToActivatingPlayerOrTower(level, mob, pending);
        }
    }

    /** Ticks to keep chasing player after losing line of sight (reduces flip to tower on brief obstruction and spinning). */
    private static final int LOST_SIGHT_GRACE_TICKS = 150;

    /**
     * Home on the player that activated the wave; if they can't see the player, attract to the tower.
     * If chasing player and lose track, default back to tower; if heading to tower and player is in range, swap to chase.
     * Player takes priority whenever in range so zombies aggro back when approaching.
     */
    private static void setTargetToActivatingPlayerOrTower(ServerLevel level, Monster mob, PendingAirdropStorage.Pending pending) {
        if (level.getServer() == null) return;
        if (AirdropConfig.isWaveAggroExcluded(mob.getType())) return;
        ServerPlayer player = pickAggroTarget(level, mob, pending);
        BlockPos tower = pending.waveStartPos;
        long now = level.getGameTime();
        int mobId = mob.getId();
        double distSqToTower = mob.distanceToSqr(tower.getX() + 0.5, tower.getY(), tower.getZ() + 0.5);
        boolean inRange = player != null && player.isAlive() && mob.distanceTo(player) <= TARGET_PLAYER_RANGE;
        boolean canSeePlayer = inRange && mob.hasLineOfSight(player);
        if (inRange) {
            if (canSeePlayer) LAST_SAW_PLAYER_AT.put(mobId, now);
            LAST_PATH_TO_TOWER_AT.remove(mobId);
            LAST_PROGRESS_TOWARD_GOAL_AT.put(mobId, now);
            mob.setTarget(player);
            Long lastPathToPlayer = LAST_PATH_TO_PLAYER_AT.get(mobId);
            boolean pathToPlayerStale = lastPathToPlayer == null || (now - lastPathToPlayer) >= PATH_TO_PLAYER_INTERVAL_TICKS;
            if (pathToPlayerStale) {
                var nav = mob.getNavigation();
                nav.moveTo(player, MOVE_TO_TOWER_SPEED);
                LAST_PATH_TO_PLAYER_AT.put(mobId, now);
            }
            LAST_DIST_SQ_TO_TOWER.put(mobId, distSqToTower);
            return;
        }
        LAST_PATH_TO_PLAYER_AT.remove(mobId);
        Long lastSaw = LAST_SAW_PLAYER_AT.get(mobId);
        boolean recentlySawPlayer = lastSaw != null && (now - lastSaw) <= LOST_SIGHT_GRACE_TICKS;
        if (recentlySawPlayer && mob.getTarget() == player) {
            LAST_DIST_SQ_TO_TOWER.put(mobId, distSqToTower);
            return;
        }
        Double prevDistSq = LAST_DIST_SQ_TO_TOWER.get(mobId);
        if (prevDistSq == null || distSqToTower < prevDistSq - 1.0)
            LAST_PROGRESS_TOWARD_GOAL_AT.put(mobId, now);
        mob.setTarget(null);
        var nav = mob.getNavigation();
        Long lastPathAt = LAST_PATH_TO_TOWER_AT.get(mobId);
        boolean pathStale = lastPathAt == null || (now - lastPathAt) >= PATH_TO_TOWER_INTERVAL_TICKS;
        if (pathStale) {
            nav.moveTo(tower.getX() + 0.5, tower.getY(), tower.getZ() + 0.5, MOVE_TO_TOWER_SPEED);
            LAST_PATH_TO_TOWER_AT.put(mobId, now);
        }
        LAST_DIST_SQ_TO_TOWER.put(mobId, distSqToTower);
    }

    /** Nearest alive lobby member within aggro range, else host if online. */
    private static ServerPlayer pickAggroTarget(ServerLevel level, Monster mob, PendingAirdropStorage.Pending pending) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (UUID memberId : pending.effectiveMembers()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(memberId);
            if (p == null || !p.isAlive() || p.level() != level) continue;
            double d = mob.distanceToSqr(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best != null) return best;
        return level.getServer().getPlayerList().getPlayer(pending.playerWhoStarted);
    }

    /** If mob is physically stuck (sky/block/fluid) AND has made no progress for STUCK_NO_PROGRESS_TICKS, respawn to ring. Never respawn moving mobs just for "no progress". */
    private static void checkStuckAndRespawnToRing(ServerLevel level, Monster mob, PendingAirdropStorage.Pending pending) {
        int mobId = mob.getId();
        long now = level.getGameTime();
        if (!needsResnap(level, mob, pending.waveStartPos)) return;
        Long lastProgress = LAST_PROGRESS_TOWARD_GOAL_AT.get(mobId);
        if (lastProgress == null) {
            LAST_PROGRESS_TOWARD_GOAL_AT.put(mobId, now);
            return;
        }
        if (now - lastProgress < STUCK_NO_PROGRESS_TICKS) return;
        BlockPos tower = pending.waveStartPos;
        double distSqToTower = mob.distanceToSqr(tower.getX() + 0.5, tower.getY(), tower.getZ() + 0.5);
        if (distSqToTower < 12 * 12) return;
        ServerPlayer player = level.getServer() != null ? level.getServer().getPlayerList().getPlayer(pending.playerWhoStarted) : null;
        if (player != null && player.isAlive() && mob.distanceToSqr(player) < 10 * 10) return;
        placeOnRingAtGround(level, mob, tower, true);
        LAST_PROGRESS_TOWARD_GOAL_AT.put(mobId, now);
        LAST_PATH_TO_TOWER_AT.remove(mobId);
        LAST_PATH_TO_PLAYER_AT.remove(mobId);
        LAST_DIST_SQ_TO_TOWER.put(mobId, mob.distanceToSqr(tower.getX() + 0.5, tower.getY(), tower.getZ() + 0.5));
    }

    /** True only when mob is actually stuck: in lava, inside a solid (both feet and head), or clearly in sky. Never true for mobs on ground or swimming in water. */
    private static boolean needsResnap(ServerLevel level, Monster mob, BlockPos waveStartPos) {
        if (mob.isInLava()) return true;
        BlockPos pos = mob.blockPosition();
        BlockState support = level.getBlockState(pos.below());
        // Standing on SOLID leaves/logs counts as "bad support" → resnap off treetops/branches.
        if (support.blocksMotion() && !isBadWaveSpawnSupport(support)) return false; // standing on good ground — never resnap
        if (support.blocksMotion() && isBadWaveSpawnSupport(support)) return true;
        boolean feetInSolid = level.getBlockState(pos).blocksMotion();
        boolean headInSolid = level.getBlockState(pos.above()).blocksMotion();
        if (feetInSolid && headInSolid) return true;
        return isTrulyInSky(level, mob, waveStartPos);
    }

    /**
     * Y of the top solid block to stand on, scanning from maxStartY down.
     * Skips leaves and logs so ring spawns land on terrain under trees instead of treetops.
     */
    private static int findGroundY(ServerLevel level, int x, int z, int maxStartY) {
        int minY = level.getMinBuildHeight();
        for (int y = Math.min(maxStartY, level.getMaxBuildHeight() - 1); y >= minY; y--) {
            BlockState st = level.getBlockState(new BlockPos(x, y, z));
            if (!st.blocksMotion()) continue;
            if (isBadWaveSpawnSupport(st)) continue;
            return y;
        }
        return minY;
    }

    /** Surface blocks that are solid but poor wave arenas (treetops, branches). */
    private static boolean isBadWaveSpawnSupport(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    }

    /**
     * First Y above ground where the block is air, so the mob isn't placed inside leaves.
     * Returns groundY+1 if that block is already air; otherwise scans up to STANDING_SEARCH_UP.
     */
    private static double findStandingY(ServerLevel level, int x, int z, int groundY) {
        int maxY = Math.min(groundY + STANDING_SEARCH_UP, level.getMaxBuildHeight() - 1);
        for (int y = groundY + 1; y <= maxY; y++) {
            if (level.getBlockState(new BlockPos(x, y, z)).isAir())
                return y;
        }
        return groundY + 1.0;
    }

    /** Same as findStandingY but requires the block above (y+1) to be air and the block below feet (y-1) to not be fluid (no spawn in/on water or lava). */
    private static double findStandingYWithHeadroom(ServerLevel level, int x, int z, int groundY) {
        int minY = level.getMinBuildHeight();
        int maxY = Math.min(groundY + STANDING_SEARCH_UP, level.getMaxBuildHeight() - 2);
        for (int y = groundY + 1; y <= maxY; y++) {
            if (y - 1 < minY) continue;
            if (level.getBlockState(new BlockPos(x, y, z)).isAir()
                && level.getBlockState(new BlockPos(x, y + 1, z)).isAir()
                && level.getBlockState(new BlockPos(x, y - 1, z)).getFluidState().isEmpty())
                return y;
        }
        return groundY + 1.0;
    }

    /** True if the block at (x, y, z) or the block below is fluid (water/lava) — used to reject spawns in/over water. */
    private static boolean isFeetOrBelowFluid(ServerLevel level, int x, int y, int z) {
        if (y <= level.getMinBuildHeight()) return true;
        return !level.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty()
            || !level.getBlockState(new BlockPos(x, y - 1, z)).getFluidState().isEmpty();
    }

    /** Number of angle steps to try when looking for a dry (non-water) ring position. */
    private static final int RING_ANGLE_ATTEMPTS = 32;

    /**
     * Compute the ground spawn position on the ring for a wave mob (used by mixin so entity is added at correct position).
     * Tries multiple angles to avoid water; uses Math.round so the ring is symmetric around the tower.
     * Caller must not increment pending.spawnCountThisWave before calling — EntityJoinLevel does that.
     */
    public static Vec3 getRingSpawnPosition(ServerLevel level, BlockPos towerCenter, int spawnIndex, int maxZombies) {
        double centerX = towerCenter.getX() + 0.5;
        double centerZ = towerCenter.getZ() + 0.5;
        double baseAngle = maxZombies > 0 ? (2 * Math.PI * spawnIndex) / maxZombies : level.getRandom().nextDouble() * 2 * Math.PI;
        for (int k = 0; k < RING_ANGLE_ATTEMPTS; k++) {
            double angle = baseAngle + (k * 2 * Math.PI / RING_ANGLE_ATTEMPTS);
            int useX = (int) Math.round(centerX + SPAWN_RING_RADIUS * Math.cos(angle));
            int useZ = (int) Math.round(centerZ + SPAWN_RING_RADIUS * Math.sin(angle));
            level.getChunk(SectionPos.blockToSectionCoord(useX), SectionPos.blockToSectionCoord(useZ));
            int maxGroundY = columnTopSolidForGroundScan(level, useX, useZ);
            int groundY = findGroundY(level, useX, useZ, maxGroundY);
            double surfaceY = findStandingYWithHeadroom(level, useX, useZ, groundY);
            int feetY = (int) Math.floor(surfaceY);
            if (feetY > level.getMinBuildHeight() && !level.getBlockState(new BlockPos(useX, feetY - 1, useZ)).blocksMotion()) {
                for (int y = feetY - 1; y >= level.getMinBuildHeight(); y--) {
                    if (level.getBlockState(new BlockPos(useX, y, useZ)).blocksMotion()) {
                        surfaceY = y + 1.0;
                        break;
                    }
                }
            }
            feetY = (int) Math.floor(surfaceY);
            if ((groundY <= level.getMinBuildHeight() && surfaceY <= level.getMinBuildHeight() + 1) || isFloatingAboveColumnSurface(level, useX, useZ, surfaceY)) {
                level.getChunk(SectionPos.blockToSectionCoord(useX), SectionPos.blockToSectionCoord(useZ));
                int reground = findGroundY(level, useX, useZ, columnTopSolidForGroundScan(level, useX, useZ));
                surfaceY = findStandingYWithHeadroom(level, useX, useZ, reground);
                feetY = (int) Math.floor(surfaceY);
            }
            if (feetY > level.getMinBuildHeight() && isFeetOrBelowFluid(level, useX, feetY, useZ))
                continue; // in water, try next angle
            return new Vec3(useX + 0.5, surfaceY, useZ + 0.5);
        }
        // Fallback: tower ground, keep ring X/Z from first angle
        int useX = (int) Math.round(centerX + SPAWN_RING_RADIUS * Math.cos(baseAngle));
        int useZ = (int) Math.round(centerZ + SPAWN_RING_RADIUS * Math.sin(baseAngle));
        level.getChunk(SectionPos.blockToSectionCoord(useX), SectionPos.blockToSectionCoord(useZ));
        int fallbackGround = findGroundY(level, useX, useZ, columnTopSolidForGroundScan(level, useX, useZ));
        double surfaceY = findStandingYWithHeadroom(level, useX, useZ, fallbackGround);
        return new Vec3(useX + 0.5, surfaceY, useZ + 0.5);
    }

    /** Stable ring position for an entity by id (deterministic). Used by EntitySetPosMixin once per entity so we don't keep re-teleporting. */
    public static Vec3 getStableRingPositionForEntity(ServerLevel level, int entityId) {
        PendingAirdropStorage.Pending pending = PendingAirdropStorage.getAnyPendingInLevel(level);
        if (pending == null) return null;
        int maxZ = Math.max(1, pending.maxZombiesPerWave);
        int index = Math.floorMod(entityId, maxZ);
        return getRingSpawnPosition(level, pending.waveStartPos, index, maxZ);
    }

    /** Returns true if placement was done, false if chunk not loaded (caller can defer). */
    private static boolean tryPlaceOnRingAtGround(ServerLevel level, Mob mob, BlockPos towerCenter, int spawnIndex, int maxZombies) {
        double angle = maxZombies > 0 ? (2 * Math.PI * spawnIndex) / maxZombies : level.getRandom().nextDouble() * 2 * Math.PI;
        // Avoid spawning in/on water/lava or holes during initial placement.
        return placeOnRingAtGroundWithAngle(level, mob, towerCenter, angle, true);
    }

    private static void placeOnRingAtGround(ServerLevel level, Mob mob, BlockPos towerCenter, int spawnIndex, int totalSpawns) {
        double angle = totalSpawns > 0 ? (2 * Math.PI * spawnIndex) / totalSpawns : level.getRandom().nextDouble() * 2 * Math.PI;
        placeOnRingAtGroundWithAngle(level, mob, towerCenter, angle, false);
    }

    /** Number of angles to try when placing to avoid water/holes. */
    private static final int PLACEMENT_ANGLE_ATTEMPTS = 24;
    /** Radii to try (blocks) so we can find dry ground if the main ring is in water. */
    private static final int[] PLACEMENT_RADII = { 40, 44, 48, 52, 36, 42, 46 };

    /**
     * Place mob on the spawn ring: random angle (initial) or preserve current angle (resnap).
     * Tries multiple radii and angles to avoid water and holes.
     */
    private static void placeOnRingAtGround(ServerLevel level, Mob mob, BlockPos towerCenter, boolean randomAngle) {
        int cx = towerCenter.getX();
        int cz = towerCenter.getZ();
        double baseAngle;
        if (randomAngle) {
            baseAngle = level.getRandom().nextDouble() * 2 * Math.PI;
        } else {
            double dx = mob.getX() - (cx + 0.5);
            double dz = mob.getZ() - (cz + 0.5);
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.01) {
                baseAngle = level.getRandom().nextDouble() * 2 * Math.PI;
            } else {
                baseAngle = Math.atan2(dz, dx);
            }
        }
        for (int radius : PLACEMENT_RADII) {
            for (int k = 0; k < PLACEMENT_ANGLE_ATTEMPTS; k++) {
                double angle = baseAngle + (k * 2 * Math.PI / PLACEMENT_ANGLE_ATTEMPTS);
                if (placeOnRingAtGroundWithAngle(level, mob, towerCenter, angle, true, radius)) return;
            }
        }
        placeOnRingAtGroundWithAngle(level, mob, towerCenter, baseAngle, false, SPAWN_RING_RADIUS);
    }

    /**
     * Spawn mob at ground level: force-load chunk, find solid ground at or below tower height, place feet on it.
     * If skipBadSpots is true, returns false when position is in fluid or a hole (so caller can try another angle).
     */
    private static boolean placeOnRingAtGroundWithAngle(ServerLevel level, Mob mob, BlockPos towerCenter, double angle, boolean skipBadSpots) {
        return placeOnRingAtGroundWithAngle(level, mob, towerCenter, angle, skipBadSpots, SPAWN_RING_RADIUS);
    }

    private static boolean placeOnRingAtGroundWithAngle(ServerLevel level, Mob mob, BlockPos towerCenter, double angle, boolean skipBadSpots, int radius) {
        double centerX = towerCenter.getX() + 0.5;
        double centerZ = towerCenter.getZ() + 0.5;
        int useX = (int) Math.round(centerX + radius * Math.cos(angle));
        int useZ = (int) Math.round(centerZ + radius * Math.sin(angle));
        level.getChunk(SectionPos.blockToSectionCoord(useX), SectionPos.blockToSectionCoord(useZ));
        int maxGroundY = columnTopSolidForGroundScan(level, useX, useZ);
        int groundY = findGroundY(level, useX, useZ, maxGroundY);
        double surfaceY = findStandingYWithHeadroom(level, useX, useZ, groundY);
        int feetY = (int) Math.floor(surfaceY);
        if (feetY > level.getMinBuildHeight() && !level.getBlockState(new BlockPos(useX, feetY - 1, useZ)).blocksMotion()) {
            for (int y = feetY - 1; y >= level.getMinBuildHeight(); y--) {
                if (level.getBlockState(new BlockPos(useX, y, useZ)).blocksMotion()) {
                    surfaceY = y + 1.0;
                    break;
                }
            }
        }
        if ((groundY <= level.getMinBuildHeight() && surfaceY <= level.getMinBuildHeight() + 1) || isFloatingAboveColumnSurface(level, useX, useZ, surfaceY)) {
            if (skipBadSpots) return false;
            level.getChunk(SectionPos.blockToSectionCoord(useX), SectionPos.blockToSectionCoord(useZ));
            int reground = findGroundY(level, useX, useZ, columnTopSolidForGroundScan(level, useX, useZ));
            surfaceY = findStandingYWithHeadroom(level, useX, useZ, reground);
        }
        feetY = (int) Math.floor(surfaceY);
        if (feetY > level.getMinBuildHeight() && isFeetOrBelowFluid(level, useX, feetY, useZ)) {
            if (skipBadSpots) return false;
            level.getChunk(SectionPos.blockToSectionCoord(useX), SectionPos.blockToSectionCoord(useZ));
            int reground = findGroundY(level, useX, useZ, columnTopSolidForGroundScan(level, useX, useZ));
            surfaceY = findStandingYWithHeadroom(level, useX, useZ, reground);
            mob.teleportTo(useX + 0.5, surfaceY, useZ + 0.5);
        } else {
            if (skipBadSpots && isInHoleOrWater(level, useX, feetY, useZ)) return false;
            mob.teleportTo(useX + 0.5, surfaceY, useZ + 0.5);
        }
        mob.setDeltaMovement(0, 0, 0);
        mob.fallDistance = 0;
        return true;
    }

    /** True if block at feet or feet+1 is fluid, or no solid below (hole). */
    private static boolean isInHoleOrWater(ServerLevel level, int x, int feetY, int z) {
        if (feetY <= level.getMinBuildHeight()) return true;
        if (level.getBlockState(new BlockPos(x, feetY, z)).getFluidState().isEmpty()
            && level.getBlockState(new BlockPos(x, feetY + 1, z)).getFluidState().isEmpty()
            && level.getBlockState(new BlockPos(x, feetY - 1, z)).blocksMotion())
            return false;
        return true;
    }

    /** Place any LivingEntity on the ring. preserveAngle: true = keep same angle (resnap in place), false = random (initial). */
    private static void placeLivingOnRingAtGround(ServerLevel level, LivingEntity entity, BlockPos towerCenter, boolean preserveAngle) {
        double angle;
        if (preserveAngle) {
            double dx = entity.getX() - (towerCenter.getX() + 0.5);
            double dz = entity.getZ() - (towerCenter.getZ() + 0.5);
            double dist = Math.sqrt(dx * dx + dz * dz);
            angle = dist < 0.01 ? level.getRandom().nextDouble() * 2 * Math.PI : Math.atan2(dz, dx);
        } else {
            angle = level.getRandom().nextDouble() * 2 * Math.PI;
        }
        int cx = towerCenter.getX();
        int cz = towerCenter.getZ();
        int useX = (int) (cx + 0.5 + SPAWN_RING_RADIUS * Math.cos(angle));
        int useZ = (int) (cz + 0.5 + SPAWN_RING_RADIUS * Math.sin(angle));
        level.getChunk(SectionPos.blockToSectionCoord(useX), SectionPos.blockToSectionCoord(useZ));
        int maxGroundY = columnTopSolidForGroundScan(level, useX, useZ);
        int groundY = findGroundY(level, useX, useZ, maxGroundY);
        double surfaceY = findStandingYWithHeadroom(level, useX, useZ, groundY);
        int feetY = (int) Math.floor(surfaceY);
        if (feetY > level.getMinBuildHeight() && !level.getBlockState(new BlockPos(useX, feetY - 1, useZ)).blocksMotion()) {
            for (int y = feetY - 1; y >= level.getMinBuildHeight(); y--) {
                if (level.getBlockState(new BlockPos(useX, y, useZ)).blocksMotion()) {
                    surfaceY = y + 1.0;
                    break;
                }
            }
        }
        if ((groundY <= level.getMinBuildHeight() && surfaceY <= level.getMinBuildHeight() + 1) || isFloatingAboveColumnSurface(level, useX, useZ, surfaceY)) {
            level.getChunk(SectionPos.blockToSectionCoord(useX), SectionPos.blockToSectionCoord(useZ));
            int reground = findGroundY(level, useX, useZ, columnTopSolidForGroundScan(level, useX, useZ));
            surfaceY = findStandingYWithHeadroom(level, useX, useZ, reground);
        }
        // Never place in/on water or lava: use tower ground Y but keep ring X/Z
        feetY = (int) Math.floor(surfaceY);
        if (feetY > level.getMinBuildHeight() && isFeetOrBelowFluid(level, useX, feetY, useZ)) {
            level.getChunk(SectionPos.blockToSectionCoord(useX), SectionPos.blockToSectionCoord(useZ));
            int reground = findGroundY(level, useX, useZ, columnTopSolidForGroundScan(level, useX, useZ));
            surfaceY = findStandingYWithHeadroom(level, useX, useZ, reground);
            entity.teleportTo(useX + 0.5, surfaceY, useZ + 0.5);
        } else {
            entity.teleportTo(useX + 0.5, surfaceY, useZ + 0.5);
        }
        entity.setDeltaMovement(0, 0, 0);
        entity.fallDistance = 0;
        // Do NOT set invuln/NoAI here — it was freezing zombies.
    }
}
