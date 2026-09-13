package net.mcreator.radiotowers.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.entity.PlaneentityEntity;
import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;
import net.mcreator.radiotowers.init.RadiotowersModEntities;
import net.mcreator.radiotowers.network.AirdropStatePacket;

import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pending airdrops: when Zombie Waves API is used, the wave starts at tower ground
 * and the airdrop is only spawned when the wave is completed (API calls onWaveCompleted).
 * Stored in PendingAirdropSavedData per level so the wave survives leave/rejoin and restart.
 */
public final class PendingAirdropStorage {

    public static final class Pending {
        /** Ground position where the wave started (tower center for spawn radius). */
        public final BlockPos waveStartPos;
        public final BlockPos panelPos;
        public final List<String> itemIds;
        public final List<Integer> quantities;
        public final int difficulty;
        public final UUID playerWhoStarted;
        /** Game time when stored; used so we don't deliver in the first few seconds. */
        public final long storedAtGameTime;
        /** Game time when the current wave started (so we can force-end after 3 min). */
        public long currentWaveStartedAtGameTime;
        /** Waves left before delivery (mutated when each wave completes). */
        public int wavesRemaining;
        /** Total number of waves for this airdrop (for UI: "Wave 2/5"). */
        public final int totalWaves;
        /** API difficulty tier (1–10) for zombie behaviour. */
        public final int tierDifficulty;
        /** Max zombies allowed this wave (cap enforced on our side if API ignores it). */
        public final int maxZombiesPerWave;
        /** Number of wave mobs spawned so far this wave; reset when each wave starts. */
        public int spawnCountThisWave;
        /** Number of wave mobs that actually died this wave (used for HUD remaining count). */
        public int killedCountThisWave;
        /** Game time when we last called onWaveCompleted for this pending; used to avoid duplicate end/start spam (cooldown 60 ticks). */
        public long lastWaveCompletedAtGameTime;
        /** ESL wave controller ID when using ESL waves (null when using Berezka or no wave system). */
        public java.util.UUID eslControllerId;
        /** Accepted lobby members (includes host). Empty/solo = host only. */
        public final Set<UUID> memberUuids;
        /** ESL lobby id when this delivery was lobbied; null for solo. */
        public final UUID lobbyId;
        /** When true, crate opens are restricted to {@link #memberUuids}. */
        public final boolean membersOnlyCrate;

        public boolean isEslManaged() {
            return eslControllerId != null;
        }

        /** Accepted members for aggro / fan-out; always includes host. */
        public Set<UUID> effectiveMembers() {
            if (memberUuids != null && !memberUuids.isEmpty()) return memberUuids;
            return Set.of(playerWhoStarted);
        }

        public Pending(BlockPos waveStartPos, BlockPos panelPos, List<String> itemIds, List<Integer> quantities, int difficulty, UUID playerWhoStarted, long storedAtGameTime, int wavesRemaining, int totalWaves, int tierDifficulty, int maxZombiesPerWave) {
            this(waveStartPos, panelPos, itemIds, quantities, difficulty, playerWhoStarted, storedAtGameTime, wavesRemaining, totalWaves, tierDifficulty, maxZombiesPerWave, null, null, false);
        }

        public Pending(BlockPos waveStartPos, BlockPos panelPos, List<String> itemIds, List<Integer> quantities, int difficulty, UUID playerWhoStarted, long storedAtGameTime, int wavesRemaining, int totalWaves, int tierDifficulty, int maxZombiesPerWave, Set<UUID> memberUuids, UUID lobbyId, boolean membersOnlyCrate) {
            this.waveStartPos = waveStartPos != null ? waveStartPos.immutable() : panelPos.immutable();
            this.panelPos = panelPos.immutable();
            this.itemIds = new ArrayList<>(itemIds != null ? itemIds : List.of());
            this.quantities = new ArrayList<>(quantities != null ? quantities : List.of());
            this.difficulty = difficulty;
            this.playerWhoStarted = playerWhoStarted;
            this.storedAtGameTime = storedAtGameTime;
            this.currentWaveStartedAtGameTime = storedAtGameTime;
            this.wavesRemaining = wavesRemaining;
            this.totalWaves = totalWaves;
            this.tierDifficulty = tierDifficulty;
            this.maxZombiesPerWave = maxZombiesPerWave;
            this.spawnCountThisWave = 0;
            this.killedCountThisWave = 0;
            this.lastWaveCompletedAtGameTime = 0;
            LinkedHashSet<UUID> members = new LinkedHashSet<>();
            if (memberUuids != null) members.addAll(memberUuids);
            if (playerWhoStarted != null) members.add(playerWhoStarted);
            this.memberUuids = Collections.unmodifiableSet(members);
            this.lobbyId = lobbyId;
            this.membersOnlyCrate = membersOnlyCrate;
        }

        /** New pending with the same wave state but new itemIds/quantities (for updating selection while wave is running). */
        public Pending copyWithOrder(List<String> itemIds, List<Integer> quantities) {
            Pending p = new Pending(waveStartPos, panelPos, itemIds, quantities, difficulty, playerWhoStarted, storedAtGameTime, wavesRemaining, totalWaves, tierDifficulty, maxZombiesPerWave, memberUuids, lobbyId, membersOnlyCrate);
            p.currentWaveStartedAtGameTime = this.currentWaveStartedAtGameTime;
            p.wavesRemaining = this.wavesRemaining;
            p.spawnCountThisWave = this.spawnCountThisWave;
            p.killedCountThisWave = this.killedCountThisWave;
            p.lastWaveCompletedAtGameTime = this.lastWaveCompletedAtGameTime;
            p.eslControllerId = this.eslControllerId;
            return p;
        }
    }

    /** Ticks to ignore "wave ended" after we just completed a wave (lets next wave start and spawn before we accept end again). */
    public static final int WAVE_COMPLETED_COOLDOWN_TICKS = 60;

    /** waveStartPos (ground at tower) -> pending airdrop. Dimension is the level we're in. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Pending>> BY_DIMENSION = new ConcurrentHashMap<>();

    /** Plane UUID -> (itemIds, quantities) for the airdrop we just delivered. Used when the plane spawns the airdrop 65 ticks later. */
    private static final Map<UUID, DeliveryOrderSnapshot> DELIVERY_ORDER_BY_PLANE = new ConcurrentHashMap<>();
    /** Dimension -> next delivery order. Fallback so the 65-tick callback always gets the order even if plane UUID doesn't match. */
    private static final Map<ResourceKey<Level>, DeliveryOrderSnapshot> NEXT_DELIVERY_ORDER_BY_DIMENSION = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, DeliveryMembersSnapshot> NEXT_DELIVERY_MEMBERS_BY_DIMENSION = new ConcurrentHashMap<>();

    public static void putDeliveryOrderForPlane(UUID planeUuid, List<String> itemIds, List<Integer> quantities) {
        if (planeUuid != null && itemIds != null && !itemIds.isEmpty())
            DELIVERY_ORDER_BY_PLANE.put(planeUuid, new DeliveryOrderSnapshot(itemIds, quantities));
    }

    /** Store the order for the next airdrop in this dimension (used by 65-tick callback as fallback). Call from deliverAt. */
    public static void putNextDeliveryOrder(ResourceKey<Level> dimension, List<String> itemIds, List<Integer> quantities) {
        if (dimension != null && itemIds != null && !itemIds.isEmpty())
            NEXT_DELIVERY_ORDER_BY_DIMENSION.put(dimension, new DeliveryOrderSnapshot(itemIds, quantities));
    }

    public static void putNextDeliveryMembers(ResourceKey<Level> dimension, Set<UUID> members, boolean membersOnly) {
        if (dimension == null || members == null || members.isEmpty()) return;
        NEXT_DELIVERY_MEMBERS_BY_DIMENSION.put(dimension, new DeliveryMembersSnapshot(members, membersOnly));
    }

    @javax.annotation.Nullable
    public static DeliveryMembersSnapshot takeNextDeliveryMembers(ResourceKey<Level> dimension) {
        return dimension != null ? NEXT_DELIVERY_MEMBERS_BY_DIMENSION.remove(dimension) : null;
    }

    /** Take and remove the next delivery order for this dimension. Returns null if none. */
    public static DeliveryOrderSnapshot takeNextDeliveryOrder(ResourceKey<Level> dimension) {
        return dimension != null ? NEXT_DELIVERY_ORDER_BY_DIMENSION.remove(dimension) : null;
    }

    /** Take and remove the delivery order for this plane (use when spawning airdrop entity). Returns null if none. */
    public static DeliveryOrderSnapshot takeDeliveryOrderForPlane(UUID planeUuid) {
        return planeUuid != null ? DELIVERY_ORDER_BY_PLANE.remove(planeUuid) : null;
    }

    /** Clear all delivery orders (e.g. after deliver so no stale entry can be reused). */
    public static void clearAllDeliveryOrders() {
        DELIVERY_ORDER_BY_PLANE.clear();
        NEXT_DELIVERY_ORDER_BY_DIMENSION.clear();
        NEXT_DELIVERY_MEMBERS_BY_DIMENSION.clear();
    }

    /** Snapshot of itemIds + quantities for one delivery. */
    public static final class DeliveryOrderSnapshot {
        public final List<String> itemIds;
        public final List<Integer> quantities;
        DeliveryOrderSnapshot(List<String> itemIds, List<Integer> quantities) {
            this.itemIds = new ArrayList<>(itemIds != null ? itemIds : List.of());
            this.quantities = new ArrayList<>(quantities != null ? quantities : List.of());
        }
    }

    public static final class DeliveryMembersSnapshot {
        public final Set<UUID> members;
        public final boolean membersOnly;
        DeliveryMembersSnapshot(Set<UUID> members, boolean membersOnly) {
            this.members = Collections.unmodifiableSet(new LinkedHashSet<>(members));
            this.membersOnly = membersOnly;
        }
    }

    public static void store(ServerLevel level, BlockPos waveStartPos, BlockPos panelPos,
                             List<String> itemIds, List<Integer> quantities, int difficulty, UUID playerWhoStarted,
                             int wavesRemaining, int totalWaves, int tierDifficulty) {
        store(level, waveStartPos, panelPos, itemIds, quantities, difficulty, playerWhoStarted,
            wavesRemaining, totalWaves, tierDifficulty, null, null);
    }

    public static void store(ServerLevel level, BlockPos waveStartPos, BlockPos panelPos,
                             List<String> itemIds, List<Integer> quantities, int difficulty, UUID playerWhoStarted,
                             int wavesRemaining, int totalWaves, int tierDifficulty,
                             Set<UUID> memberUuids, UUID lobbyId) {
        long gameTime = level.getGameTime();
        int maxZombies = AirdropDifficultyTier.getZombiesPerWaveForTier(tierDifficulty);
        boolean membersOnly = AirdropConfig.isLobbyMembersOnlyCrates()
            && lobbyId != null && memberUuids != null && memberUuids.size() > 1;
        BY_DIMENSION
            .computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>())
            .put(waveStartPos.immutable(), new Pending(waveStartPos, panelPos, itemIds, quantities, difficulty, playerWhoStarted, gameTime, wavesRemaining, totalWaves, tierDifficulty, maxZombies, memberUuids, lobbyId, membersOnly));
    }

    /** Update the loot order for this player's current pending (so reopening GUI and clicking Start uses latest selection). */
    public static boolean updateOrderForPlayer(ServerLevel level, UUID playerUuid, List<String> itemIds, List<Integer> quantities) {
        Map<BlockPos, Pending> map = BY_DIMENSION.get(level.dimension());
        if (map == null) return false;
        for (Map.Entry<BlockPos, Pending> e : map.entrySet()) {
            if (playerUuid.equals(e.getValue().playerWhoStarted)) {
                Pending updated = e.getValue().copyWithOrder(itemIds != null ? itemIds : List.of(), quantities != null ? quantities : List.of());
                map.put(e.getKey(), updated);
                return true;
            }
        }
        return false;
    }

    /** Remove pending at this position (e.g. when wave failed and we deliver immediately). */
    public static void removePending(ServerLevel level, BlockPos waveStartPos) {
        Map<BlockPos, Pending> map = BY_DIMENSION.get(level.dimension());
        if (map != null) map.remove(waveStartPos.immutable());
    }

    /**
     * Wave + panel positions to sweep after quit/rejoin: must be read from SavedData + memory before
     * {@link #clearAllPendingInLevel}. Using both reduces misses when ground anchor and panel differ in height/XZ.
     */
    public static List<BlockPos> copyWaveSweepCentersBeforeClear(ServerLevel level) {
        if (level == null) return List.of();
        LinkedHashSet<BlockPos> set = new LinkedHashSet<>();
        PendingAirdropSavedData saved = PendingAirdropSavedData.get(level);
        for (Pending p : saved.getPending().values()) {
            if (p.waveStartPos != null) set.add(p.waveStartPos.immutable());
            if (p.panelPos != null) set.add(p.panelPos.immutable());
        }
        Map<BlockPos, Pending> mem = BY_DIMENSION.get(level.dimension());
        if (mem != null) {
            for (Pending p : mem.values()) {
                if (p.waveStartPos != null) set.add(p.waveStartPos.immutable());
                if (p.panelPos != null) set.add(p.panelPos.immutable());
            }
        }
        return new ArrayList<>(set);
    }

    /** Clear all pending airdrops in this dimension (memory + saved data). Call on level load so rejoin mid-wave does not resume wave / deliver airdrop. */
    public static void clearAllPendingInLevel(ServerLevel level) {
        if (level == null) return;
        BY_DIMENSION.remove(level.dimension());
        PendingAirdropSavedData saved = PendingAirdropSavedData.get(level);
        if (saved != null && !saved.getPending().isEmpty()) {
            saved.getPending().clear();
            saved.setDirty();
        }
    }

    /** Get pending without removing (for wave-end check). */
    public static Pending getPending(ServerLevel level, BlockPos waveStartPos) {
        Map<BlockPos, Pending> map = BY_DIMENSION.get(level.dimension());
        return map != null ? map.get(waveStartPos.immutable()) : null;
    }

    /** Get pending whose wave start position is within 64 blocks of the given pos (horizontal only). */
    public static Pending getPendingNear(ServerLevel level, BlockPos pos) {
        return getPendingNear(level, pos, 64);
    }

    /** Get pending whose wave start position is within maxRange blocks of the given pos (horizontal only). Use 128 to catch API spawns that land far. */
    public static Pending getPendingNear(ServerLevel level, BlockPos pos, int maxRange) {
        Map<BlockPos, Pending> map = BY_DIMENSION.get(level.dimension());
        if (map == null) return null;
        int rangeSq = maxRange * maxRange;
        for (Map.Entry<BlockPos, Pending> e : map.entrySet()) {
            BlockPos key = e.getKey();
            int dx = pos.getX() - key.getX();
            int dz = pos.getZ() - key.getZ();
            if (dx * dx + dz * dz <= rangeSq) return e.getValue();
        }
        return null;
    }

    /** Get pending for the given panel position (matches by pending.panelPos so only this tower's airdrop counts). */
    public static Pending getPendingForPanel(ServerLevel level, BlockPos panelPos) {
        Map<BlockPos, Pending> map = BY_DIMENSION.get(level.dimension());
        if (map == null || panelPos == null) return null;
        BlockPos p = panelPos.immutable();
        for (Pending pending : map.values()) {
            if (pending.panelPos.equals(p)) return pending;
        }
        return null;
    }

    /** If there is exactly one pending airdrop in this dimension, return it (e.g. for spawns whose position not set yet). */
    public static Pending getOnlyPendingInLevel(ServerLevel level) {
        Map<BlockPos, Pending> map = BY_DIMENSION.get(level.dimension());
        if (map == null || map.size() != 1) return null;
        return map.values().iterator().next();
    }

    /** Any pending airdrop in this dimension (for catch-all wave mob fix when multiple towers possible). */
    public static Pending getAnyPendingInLevel(ServerLevel level) {
        Map<BlockPos, Pending> map = BY_DIMENSION.get(level.dimension());
        if (map == null || map.isEmpty()) return null;
        return map.values().iterator().next();
    }

    /**
     * True if (x,z) is within {@code horizontalBlocks} (inclusive) of any pending wave anchor in this dimension.
     * Used so we do not tag random monsters globally during a wave (that bypasses spawn caps and pollutes counts).
     */
    public static boolean isNearAnyWaveStartXZ(ServerLevel level, double x, double z, int horizontalBlocks) {
        Map<BlockPos, Pending> map = BY_DIMENSION.get(level.dimension());
        if (map == null || map.isEmpty()) return false;
        long maxSq = (long) horizontalBlocks * horizontalBlocks;
        for (BlockPos key : map.keySet()) {
            double dx = x - (key.getX() + 0.5);
            double dz = z - (key.getZ() + 0.5);
            if (dx * dx + dz * dz <= maxSq) return true;
        }
        return false;
    }

    /** Call action for each pending airdrop in this dimension (for time-based wave end). */
    public static void forEachPendingIn(ServerLevel level, java.util.function.BiConsumer<BlockPos, Pending> action) {
        Map<BlockPos, Pending> map = BY_DIMENSION.get(level.dimension());
        if (map != null) {
            for (Map.Entry<BlockPos, Pending> e : map.entrySet()) {
                action.accept(e.getKey(), e.getValue());
            }
        }
    }

    /** Find pending airdrop by wave start position (exact or nearest within range). */
    public static Pending take(ServerLevel level, BlockPos waveStartPos) {
        Map<BlockPos, Pending> map = BY_DIMENSION.get(level.dimension());
        if (map == null) return null;
        BlockPos key = waveStartPos.immutable();
        Pending p = map.remove(key);
        if (p != null) return p;
        BlockPos nearest = null;
        double nearestDist = 25 * 25;
        for (BlockPos pos : map.keySet()) {
            double d = pos.distSqr(waveStartPos);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = pos;
            }
        }
        if (nearest != null) {
            Pending removed = map.remove(nearest);
            if (removed != null) PendingAirdropSavedData.get(level).setDirty();
            return removed;
        }
        return null;
    }

    /**
     * Deliver the airdrop at this wave position (take pending, spawn plane, set cooldown).
     * Called when all waves for this airdrop are complete.
     */
    public static void deliverAt(ServerLevel level, BlockPos waveStartPos) {
        // End the Berezka wave and purge residue *before* removing pending. If we take() first,
        // pending is gone and invuln/NoAI mixins stop applying while the API can still spawn one tick
        // of ghost zombies (frozen, untracked).
        ZombieWavesAPILoader.cleanupAfterRadioTowersWaveFinished(level, waveStartPos);
        Pending p = take(level, waveStartPos);
        if (p == null) return;
        clearAllDeliveryOrders();
        net.minecraft.world.entity.Entity planeEntity = RadiotowersModEntities.PLANEENTITY.get().spawn(level, p.panelPos, MobSpawnType.MOB_SUMMONED);
        if (planeEntity != null) {
            planeEntity.setYRot(level.getRandom().nextFloat() * 360F);
            if (planeEntity instanceof PlaneentityEntity pe) {
                if (!p.itemIds.isEmpty()) {
                    pe.setAirdropOrder(p.itemIds, p.quantities);
                    putDeliveryOrderForPlane(pe.getUUID(), p.itemIds, p.quantities);
                    putNextDeliveryOrder(level.dimension(), p.itemIds, p.quantities);
                }
                pe.setAirdropDifficulty(p.difficulty);
            }
        }
        if (p.membersOnlyCrate) {
            putNextDeliveryMembers(level.dimension(), p.effectiveMembers(), true);
        }
        net.mcreator.radiotowers.lobby.AirdropLobbyService.applySharedCooldownWave(level, p.effectiveMembers());
        for (UUID memberId : p.effectiveMembers()) {
            var player = level.getServer() != null ? level.getServer().getPlayerList().getPlayer(memberId) : null;
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.radiotowers.wave.all_cleared_airdrop_inbound"));
            }
        }
    }

    /** Whether this player currently has a pending wave (one at a time). */
    public static boolean hasPendingForPlayer(ServerLevel level, java.util.UUID playerUuid) {
        Map<BlockPos, Pending> map = BY_DIMENSION.get(level.dimension());
        if (map == null) return false;
        for (Pending p : map.values()) {
            if (playerUuid.equals(p.playerWhoStarted) || p.effectiveMembers().contains(playerUuid)) return true;
        }
        return false;
    }

    /** For aggro: get the player UUID who started the wave at/near this position, if any. */
    public static UUID getPlayerWhoStartedWave(ResourceKey<Level> dimension, BlockPos pos) {
        Map<BlockPos, Pending> map = BY_DIMENSION.get(dimension);
        if (map == null) return null;
        Pending p = map.get(pos.immutable());
        if (p != null) return p.playerWhoStarted;
        for (Map.Entry<BlockPos, Pending> e : map.entrySet()) {
            if (e.getKey().distSqr(pos) <= 64 * 64) return e.getValue().playerWhoStarted;
        }
        return null;
    }
}
