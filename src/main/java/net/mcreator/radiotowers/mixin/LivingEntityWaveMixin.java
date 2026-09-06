package net.mcreator.radiotowers.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.core.registries.BuiltInRegistries;
import net.mcreator.radiotowers.events.WaveMobAggroHandler;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.WaveSpawnTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * During an active wave, never allow Monsters to stay invulnerable — the API may set it;
 * we force it off so wave mobs are always killable. Targets Entity (setInvulnerable is defined there).
 */
@Mixin(Entity.class)
public class LivingEntityWaveMixin {

    private static final ResourceLocation PLANE_TYPE_ID = ResourceLocation.parse("radiotowers:planeentity");

    @ModifyVariable(method = "setInvulnerable(Z)V", at = @At("HEAD"), argsOnly = true, remap = true)
    private boolean radiotowers$forceInvulnOffDuringWave(boolean invulnerable) {
        Entity self = (Entity) (Object) this;
        if (!invulnerable) return false;
        if (!(self instanceof LivingEntity living) || !(living instanceof Monster)) return invulnerable;
        if (PLANE_TYPE_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(self.getType()))) return invulnerable;
        if (living.level().isClientSide() || !(living.level() instanceof ServerLevel level)) return invulnerable;
        if (PendingAirdropStorage.getAnyPendingInLevel(level) == null) return invulnerable;
        if (!isWaveMob(self)) return invulnerable;
        return false;
    }

    private static boolean isWaveMob(Entity e) {
        return e.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
            || e.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
            || WaveMobAggroHandler.isWaveSpawnedMob(e);
    }
}
