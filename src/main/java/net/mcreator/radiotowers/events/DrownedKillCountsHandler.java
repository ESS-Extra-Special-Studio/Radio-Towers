package net.mcreator.radiotowers.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.PendingAirdropSavedData;
import net.mcreator.radiotowers.integration.WaveSpawnTag;
import net.mcreator.radiotowers.events.WaveMobAggroHandler;

/**
 * Wave mob deaths: decrement the API's remainEnemysCount only for mobs we fully own on the
 * RadioTowers side. That avoids double-counting while still letting replacement/adopted mobs
 * finish the wave correctly.
 */
@Mod.EventBusSubscriber(modid = RadiotowersMod.MODID)
public class DrownedKillCountsHandler {

    private static final int WAVE_MOB_RANGE = 200;
    /** Prevent double-increment when the same entity death event is observed more than once. */
    private static final java.util.Set<Integer> DEATH_COUNTED_ENTITY_IDS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        LivingEntity dead = event.getEntity();

        // In nuclear baseline mode we disabled all wave tags/mixins.
        if (RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) {
            boolean isZombieFamily = dead.getType() == net.minecraft.world.entity.EntityType.ZOMBIE
                || dead.getType() == net.minecraft.world.entity.EntityType.DROWNED;
            if (!isZombieFamily) return;

            // Only count actual wave mobs; otherwise leftover ambient/vanilla zombies can decrement
            // our killedCountThisWave and trigger early wave completion / wrong airdrop credits.
            boolean looksLikeWaveMob =
                WaveMobAggroHandler.isWaveSpawnedMob(dead)
                    || dead.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET)
                    || dead.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
                    || dead.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG);
            if (!looksLikeWaveMob) return;

            if (!DEATH_COUNTED_ENTITY_IDS.add(dead.getId())) return;
            var pending = PendingAirdropStorage.getPendingNear(level, dead.blockPosition(), WAVE_MOB_RANGE);
            if (pending == null) pending = PendingAirdropStorage.getAnyPendingInLevel(level);
            if (pending == null || pending.isEslManaged()) return;

            pending.killedCountThisWave = Math.max(0, Math.min(pending.maxZombiesPerWave, pending.killedCountThisWave + 1));
            PendingAirdropSavedData.get(level).setDirty();
            return;
        }

        boolean waveMob =
            WaveMobAggroHandler.isWaveSpawnedMob(dead)
                || dead.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET)
                || dead.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
                || dead.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG);
        if (!waveMob) return;

        // Count death once per entity id.
        if (!DEATH_COUNTED_ENTITY_IDS.add(dead.getId())) return;
        var pending = PendingAirdropStorage.getPendingNear(level, dead.blockPosition(), WAVE_MOB_RANGE);
        if (pending == null) pending = PendingAirdropStorage.getAnyPendingInLevel(level);
        if (pending == null || pending.isEslManaged()) return;
        pending.killedCountThisWave = Math.max(0, Math.min(pending.maxZombiesPerWave, pending.killedCountThisWave + 1));
        PendingAirdropSavedData.get(level).setDirty();
    }

    /** Call when a new wave starts or the current wave ends (kept for compatibility with ZombieWavesAPILoader). */
    public static void clearDeathCountedIds() {
        DEATH_COUNTED_ENTITY_IDS.clear();
    }
}
