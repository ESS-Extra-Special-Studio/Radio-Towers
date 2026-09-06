package net.mcreator.radiotowers.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
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
 * During an active wave, force Monsters to never stay NoAI — after any setNoAi call,
 * if this mob is a Monster and now has NoAI, we set it back to false so wave mobs always have AI and can move.
 * Uses RETURN inject + re-entry guard.
 */
@Mixin(Mob.class)
public class MobNoAiWaveMixin {

    @Unique
    private static final ThreadLocal<Boolean> radiotowers$inForceOff = ThreadLocal.withInitial(() -> false);

    @Inject(method = "setNoAi", at = @At("RETURN"), remap = true)
    private void radiotowers$forceNoAiOffAfterSet(boolean noAi, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (radiotowers$inForceOff.get()) return;
        if (!(self instanceof Monster)) return;
        if (self.level().isClientSide() || !(self.level() instanceof ServerLevel level)) return;
        if (PendingAirdropStorage.getAnyPendingInLevel(level) == null) return;
        if (!isWaveMob(self)) return;
        if (!self.isNoAi()) return;
        radiotowers$inForceOff.set(true);
        try {
            self.setNoAi(false);
        } finally {
            radiotowers$inForceOff.set(false);
        }
    }

    private static boolean isWaveMob(Mob e) {
        return e.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
            || e.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
            || WaveMobAggroHandler.isWaveSpawnedMob(e);
    }
}
