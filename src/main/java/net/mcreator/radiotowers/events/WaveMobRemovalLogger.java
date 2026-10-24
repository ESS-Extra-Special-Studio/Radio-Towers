package net.mcreator.radiotowers.events;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.EntityLeaveWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.WaveSpawnTag;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Diagnostic logger for "instant despawn" reports.
 * In nuclear baseline mode, logs when zombie-family entities are removed shortly after joining near an active wave.
 */
@Mod.EventBusSubscriber(modid = RadiotowersMod.MODID)
public final class WaveMobRemovalLogger {

    private WaveMobRemovalLogger() {}

    private static final int NEAR_WAVE_RANGE = 128;
    private static final int LOG_REMOVAL_WITHIN_TICKS = 60;

    private static final Map<Integer, Long> JOINED_AT = new ConcurrentHashMap<>();
    private static final Map<Integer, BlockPos> JOINED_POS = new ConcurrentHashMap<>();

    private static boolean isZombieFamily(Entity e) {
    LivingEntity le = (e instanceof LivingEntity) ? (LivingEntity) e : null;
        if (!(le != null)) return false;
        net.minecraft.entity.EntityType t = le.getType();
        return t == net.minecraft.entity.EntityType.ZOMBIE || t == net.minecraft.entity.EntityType.DROWNED;
    }

    private static PendingAirdropStorage.Pending getPending(World lvl, Entity e) {
    ServerWorld sl = (lvl instanceof ServerWorld) ? (ServerWorld) lvl : null;
        if (!(sl != null)) return null;
        net.mcreator.radiotowers.integration.PendingAirdropStorage.Pending p = PendingAirdropStorage.getPendingNear(sl, e.blockPosition(), NEAR_WAVE_RANGE);
        if (p == null) p = PendingAirdropStorage.getAnyPendingInLevel(sl);
        return p;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinWorldEvent event) {
        if (!RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) return;
        if (event.getWorld().isClientSide()) return;
        Entity e = event.getEntity();
        if (!isZombieFamily(e)) return;
        PendingAirdropStorage.Pending p = getPending(event.getWorld(), e);
        if (p == null) return;
        long now = ((ServerWorld) event.getWorld()).getGameTime();
        JOINED_AT.put(e.getId(), now);
        JOINED_POS.put(e.getId(), e.blockPosition());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveWorldEvent event) {
        if (!RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) return;
        if (event.getWorld().isClientSide()) return;
        Entity e = event.getEntity();
        if (!isZombieFamily(e)) return;
        Object level$src = event.getWorld(); ServerWorld level = (level$src instanceof ServerWorld) ? (ServerWorld) level$src : null;
        if (!(level != null)) return;

        Long joinedAt = JOINED_AT.get(e.getId());
        if (joinedAt == null) return;
        long now = level.getGameTime();
        long age = now - joinedAt;
        if (age > LOG_REMOVAL_WITHIN_TICKS) return;

        PendingAirdropStorage.Pending p = getPending(level, e);
        BlockPos wavePos = p != null ? p.waveStartPos : null;

        String reason = e.removed ? "removed" : "alive";
        MobEntity mob = (e instanceof MobEntity) ? (MobEntity) e : null;

        boolean hasWaveTag =
            e.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
                || e.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
                || e.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET)
                || (mob != null && WaveMobAggroHandler.isWaveSpawnedMob(mob));
                LivingEntity le = (e instanceof LivingEntity) ? (LivingEntity) e : null;

        RadiotowersMod.LOGGER.warn(
            "[WaveRemovalDebug] id={} type={} ageTicks={} reason={} pos={} joinedPos={} nearWave={} wavePos={} hasWaveTag={} alive={} removed={}",
            e.getId(),
            net.minecraftforge.registries.ForgeRegistries.ENTITIES.getKey(e.getType()),
            age,
            reason,
            e.blockPosition(),
            JOINED_POS.get(e.getId()),
            p != null,
            wavePos,
            hasWaveTag,
            (le != null && le.isAlive()),
            e.removed
        );
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) return;
        if (event.getEntity().level.isClientSide()) return;
        LivingEntity e = event.getEntityLiving();
        if (!isZombieFamily(e)) return;
        PendingAirdropStorage.Pending p = getPending(e.level, e);
        if (p == null) return;
        RadiotowersMod.LOGGER.warn(
            "[WaveRemovalDebug] id={} type={} DEATH pos={} nearWave={} hasWaveTag={}",
            e.getId(),
            net.minecraftforge.registries.ForgeRegistries.ENTITIES.getKey(e.getType()),
            e.blockPosition(),
            true,
            e.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
                || e.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
                || e.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET)
                || WaveMobAggroHandler.isWaveSpawnedMob(e)
        );
    }
}

