package net.mcreator.radiotowers.mixin;

import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.DamageSource;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.mcreator.radiotowers.events.WaveMobAggroHandler;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.WaveSpawnTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right before any damage logic runs in hurt(), force wave Monsters to be non-invulnerable and have AI.
 * This ensures the rest of hurt() sees them as damageable even if the API set flags directly.
 */
@Mixin(LivingEntity.class)
public class LivingEntityHurtWaveMixin {

    @Inject(method = "hurt", at = @At("HEAD"), remap = true)
    private void radiotowers$forceDamageableBeforeHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof MonsterEntity)) return;
        ServerWorld level = (self.level instanceof ServerWorld) ? (ServerWorld) self.level : null;
        if (self.level == null || self.level.isClientSide() || !(level != null)) return;
        if (PendingAirdropStorage.getAnyPendingInLevel(level) == null) return;
        if (!isWaveMob(self)) return;
        self.setInvulnerable(false);
        MobEntity mob = (self instanceof MobEntity) ? (MobEntity) self : null;
        if (mob != null) mob.setNoAi(false);
    }

    private static boolean isWaveMob(LivingEntity e) {
        return e.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
            || e.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
            || WaveMobAggroHandler.isWaveSpawnedMob(e);
    }
}
