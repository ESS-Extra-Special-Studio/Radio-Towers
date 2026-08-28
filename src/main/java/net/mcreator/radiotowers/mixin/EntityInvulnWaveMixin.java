package net.mcreator.radiotowers.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.mcreator.radiotowers.events.WaveMobAggroHandler;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.WaveSpawnTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During an active wave, force Monsters to never stay invulnerable — after any setInvulnerable call,
 * if this entity is a Monster and now invulnerable, we set it back to false so wave mobs are always killable.
 * Uses RETURN inject + re-entry guard to avoid fighting the API every tick.
 */
@Mixin(Entity.class)
public class EntityInvulnWaveMixin {

    @Unique
    private static final ThreadLocal<Boolean> radiotowers$inForceOff = ThreadLocal.withInitial(() -> false);

    @Inject(method = "setInvulnerable", at = @At("RETURN"), remap = true)
    private void radiotowers$forceInvulnOffAfterSet(boolean invulnerable, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (radiotowers$inForceOff.get()) return;
        if (!(self instanceof Monster)) return;
        if (self.level().isClientSide() || !(self.level() instanceof ServerLevel level)) return;
        if (PendingAirdropStorage.getAnyPendingInLevel(level) == null) return;
        if (!isWaveMob(self)) return;
        if (!self.isInvulnerable()) return;
        radiotowers$inForceOff.set(true);
        try {
            self.setInvulnerable(false);
        } finally {
            radiotowers$inForceOff.set(false);
        }
    }

    private static boolean isWaveMob(Entity e) {
        return e.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
            || e.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
            || WaveMobAggroHandler.isWaveSpawnedMob(e);
    }
}
