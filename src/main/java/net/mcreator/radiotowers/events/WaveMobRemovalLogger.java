package net.mcreator.radiotowers.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.WaveSpawnTag;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Diagnostic logger for "instant despawn" reports.
 * In nuclear baseline mode, logs when zombie-family entities are removed shortly after joining near an active wave.
 */
@EventBusSubscriber(modid = RadiotowersMod.MODID)
public final class WaveMobRemovalLogger {

    private WaveMobRemovalLogger() {}

    private static final int NEAR_WAVE_RANGE = 128;
    private static final int LOG_REMOVAL_WITHIN_TICKS = 60;

    private static final Map<Integer, Long> JOINED_AT = new ConcurrentHashMap<>();
    private static final Map<Integer, BlockPos> JOINED_POS = new ConcurrentHashMap<>();

    private static boolean isZombieFamily(Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        var t = le.getType();
        return t == net.minecraft.world.entity.EntityType.ZOMBIE || t == net.minecraft.world.entity.EntityType.DROWNED;
    }

    private static PendingAirdropStorage.Pending getPending(Level lvl, Entity e) {
        if (!(lvl instanceof ServerLevel sl)) return null;
        var p = PendingAirdropStorage.getPendingNear(sl, e.blockPosition(), NEAR_WAVE_RANGE);
        if (p == null) p = PendingAirdropStorage.getAnyPendingInLevel(sl);
        return p;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) return;
        if (event.getLevel().isClientSide()) return;
        Entity e = event.getEntity();
        if (!isZombieFamily(e)) return;
        PendingAirdropStorage.Pending p = getPending(event.getLevel(), e);
        if (p == null) return;
        long now = ((ServerLevel) event.getLevel()).getGameTime();
        JOINED_AT.put(e.getId(), now);
        JOINED_POS.put(e.getId(), e.blockPosition());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) return;
        if (event.getLevel().isClientSide()) return;
        Entity e = event.getEntity();
        if (!isZombieFamily(e)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        Long joinedAt = JOINED_AT.get(e.getId());
        if (joinedAt == null) return;
        long now = level.getGameTime();
        long age = now - joinedAt;
        if (age > LOG_REMOVAL_WITHIN_TICKS) return;

        PendingAirdropStorage.Pending p = getPending(level, e);
        BlockPos wavePos = p != null ? p.waveStartPos : null;

        String reason = "unknown";
        try {
            Entity.RemovalReason rr = e.getRemovalReason();
            reason = rr != null ? rr.name() : "null";
        } catch (Throwable ignored) {
        }

        boolean hasWaveTag =
            e.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
                || e.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
                || e.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET)
                || (e instanceof Mob mob && WaveMobAggroHandler.isWaveSpawnedMob(mob));

        RadiotowersMod.LOGGER.warn(
            "[WaveRemovalDebug] id={} type={} ageTicks={} reason={} pos={} joinedPos={} nearWave={} wavePos={} hasWaveTag={} alive={} removed={}",
            e.getId(),
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()),
            age,
            reason,
            e.blockPosition(),
            JOINED_POS.get(e.getId()),
            p != null,
            wavePos,
            hasWaveTag,
            (e instanceof LivingEntity le && le.isAlive()),
            e.isRemoved()
        );
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) return;
        if (event.getEntity().level().isClientSide()) return;
        LivingEntity e = event.getEntity();
        if (!isZombieFamily(e)) return;
        PendingAirdropStorage.Pending p = getPending(e.level(), e);
        if (p == null) return;
        RadiotowersMod.LOGGER.warn(
            "[WaveRemovalDebug] id={} type={} DEATH pos={} nearWave={} hasWaveTag={}",
            e.getId(),
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()),
            e.blockPosition(),
            true,
            e.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
                || e.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
                || e.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET)
                || WaveMobAggroHandler.isWaveSpawnedMob(e)
        );
    }
}

