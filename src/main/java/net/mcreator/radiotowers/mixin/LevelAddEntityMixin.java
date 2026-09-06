package net.mcreator.radiotowers.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import net.mcreator.radiotowers.events.WaveMobAggroHandler;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.WaveMobFieldForce;
import net.mcreator.radiotowers.integration.WaveSpawnTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only for mobs tagged by LevelTagWaveSpawnMixin (Berezka API spawns): set position to ground ring
 * before add so the entity joins already on the ground. Hooks addFreshEntity, addEntity, and
 * addWithUUID so we reposition wave mobs regardless of which add path the API uses.
 */
@Mixin(ServerLevel.class)
public class LevelAddEntityMixin {

    private static final int WAVE_MOB_RANGE = 200;
    private static final ThreadLocal<Boolean> RADIOTOWERS_ADDING_REPLACEMENT = ThreadLocal.withInitial(() -> false);
    // Prefer pre-add replacement so zombies spawn already on the ring.
    // This avoids the "spawn then teleport" visual that happens when we only snap in setPos().
    private static final boolean ENABLE_PRE_ADD_REPLACEMENT = true;

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, remap = true)
    private void radiotowers$setWaveMobPositionAddFresh(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (setWaveMobPositionBeforeAdd((ServerLevel) (Object) this, entity)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "addEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, remap = true)
    private void radiotowers$setWaveMobPositionAddEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (setWaveMobPositionBeforeAdd((ServerLevel) (Object) this, entity)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "addWithUUID(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, remap = true)
    private void radiotowers$setWaveMobPositionAddWithUUID(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (setWaveMobPositionBeforeAdd((ServerLevel) (Object) this, entity)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    private static final ResourceLocation PLANE_TYPE_ID = ResourceLocation.parse("radiotowers:planeentity");

    private static boolean setWaveMobPositionBeforeAdd(ServerLevel level, Entity entity) {
        if (!ENABLE_PRE_ADD_REPLACEMENT) return false;
        if (level.isClientSide()) return false;
        if (RADIOTOWERS_ADDING_REPLACEMENT.get()) return false;
        if (!(entity instanceof Mob mob) || !(entity instanceof Monster)) return false;
        if (PLANE_TYPE_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) return false;
        if (entity.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)) return false;
        if (entity.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET)) return false;

        PendingAirdropStorage.Pending pending = PendingAirdropStorage.getPendingNear(level, entity.blockPosition(), WAVE_MOB_RANGE);
        if (pending == null && WaveMobAggroHandler.entityYLooksLikeSkyHighApiSpawn(level, entity))
            pending = PendingAirdropStorage.getOnlyPendingInLevel(level);
        if (pending == null) return false;

        // Only replace "broken" API cases (invuln/noAi, water/lava, sky). Do NOT treat the presence
        // of WAVE_SPAWN_TAG itself as "broken", otherwise once LevelTagWaveSpawnMixin is enabled we
        // would replace every wave mob and re-introduce lifecycle issues.
        boolean likelyBrokenApiSpawn =
            WaveMobFieldForce.isInvulnerableRaw(entity)
                || WaveMobFieldForce.isNoAiRaw(mob)
                || mob.isInWater() || mob.isInLava()
                || WaveMobAggroHandler.entityYLooksLikeSkyHighApiSpawn(level, entity);
        if (!likelyBrokenApiSpawn) return false;

        int nextSpawnCount = pending.spawnCountThisWave + 1;
        if (nextSpawnCount > pending.maxZombiesPerWave) return false;
        int index = pending.spawnCountThisWave;
        Vec3 pos = WaveMobAggroHandler.getRingSpawnPosition(level, pending.waveStartPos, index, pending.maxZombiesPerWave);
        Zombie vanilla = EntityType.ZOMBIE.create(level);
        if (vanilla == null) return false;
        vanilla.setPos(pos.x, pos.y, pos.z);
        vanilla.setDeltaMovement(Vec3.ZERO);
        vanilla.fallDistance = 0;
        vanilla.setInvulnerable(false);
        vanilla.setNoAi(false);
        WaveMobFieldForce.forceOff(vanilla);
        vanilla.getPersistentData().putBoolean(WaveSpawnTag.REPLACEMENT_TAG, true);
        vanilla.getPersistentData().putBoolean(WaveSpawnTag.RING_POSITION_SET, true);
        RADIOTOWERS_ADDING_REPLACEMENT.set(true);
        boolean added;
        try {
            added = level.addFreshEntity(vanilla);
        } finally {
            RADIOTOWERS_ADDING_REPLACEMENT.set(false);
        }
        if (!added) return false;
        pending.spawnCountThisWave = nextSpawnCount;
        return true;
    }
}
