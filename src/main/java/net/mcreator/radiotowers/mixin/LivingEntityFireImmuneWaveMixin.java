package net.mcreator.radiotowers.mixin;

import net.minecraft.util.DamageSource;
import net.minecraft.entity.LivingEntity;
import net.mcreator.radiotowers.events.WaveMobAggroHandler;
import net.mcreator.radiotowers.integration.WaveSpawnTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Our replacement wave mobs are plain vanilla zombies, so they still need explicit daytime/lava
 * protection even if the upstream API now protects its own entity class.
 */
@Mixin(LivingEntity.class)
public class LivingEntityFireImmuneWaveMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true, remap = true)
    private void radiotowers$cancelFireDamageForWaveMobs(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        boolean isWaveMob = WaveMobAggroHandler.isWaveSpawnedMob(self)
            || self.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
            || self.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
            || self.getPersistentData().getBoolean(WaveSpawnTag.RING_POSITION_SET);
        if (!isWaveMob) return;
        if (source.isFire()) {
            self.setRemainingFireTicks(0);
            cir.setReturnValue(false);
        }
    }
}
