package net.mcreator.radiotowers.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import net.mcreator.radiotowers.events.WaveMobAggroHandler;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.WaveSpawnTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When something (e.g. Berezka API) sets a Monster's position to the sky during a wave,
 * we override only the FIRST time per entity with a stable ground ring position, then allow
 * normal movement. This avoids both sky spawns and the zoomies caused by re-teleporting every setPos.
 */
@Mixin(Entity.class)
public class EntitySetPosMixin {

    @Unique
    private static final ThreadLocal<Boolean> radiotowers$inSetPosOverride = ThreadLocal.withInitial(() -> false);

    @Unique
    private static final double RADIOTOWERS$SKY_Y = 55;

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true, remap = true)
    private void radiotowers$overrideSkyPositionOnce(double x, double y, double z, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) return;
        if (!(self.level() instanceof ServerLevel level)) return;
        if (!(self instanceof Monster)) return;
        // Only touch wave-spawned mobs; otherwise we override every Monster in the dimension and rubberband normal mobs
        if (!self.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
            && !self.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
            && !WaveMobAggroHandler.isWaveSpawnedMob(self)) return;
        if (radiotowers$inSetPosOverride.get()) return;
        if (y <= RADIOTOWERS$SKY_Y) return;
        if (PendingAirdropStorage.getAnyPendingInLevel(level) == null) return;

        int id = self.getId();
        if (WaveMobAggroHandler.SKY_POSITION_ALREADY_FIXED.contains(id)) return;

        Vec3 ground = WaveMobAggroHandler.getStableRingPositionForEntity(level, id);
        if (ground == null) return;

        WaveMobAggroHandler.SKY_POSITION_ALREADY_FIXED.add(id);
        radiotowers$inSetPosOverride.set(true);
        try {
            self.setPos(ground.x, ground.y, ground.z);
        } finally {
            radiotowers$inSetPosOverride.set(false);
        }
        ci.cancel();
    }
}
