package net.mcreator.radiotowers.mixin;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraftforge.registries.ForgeRegistries;
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

    private static final ResourceLocation PLANE_TYPE_ID = new ResourceLocation("radiotowers:planeentity");

    @ModifyVariable(method = "setInvulnerable(Z)V", at = @At("HEAD"), argsOnly = true, remap = true)
    private boolean radiotowers$forceInvulnOffDuringWave(boolean invulnerable) {
        Entity self = (Entity) (Object) this;
        if (!invulnerable) return false;
        LivingEntity living = (self instanceof LivingEntity) ? (LivingEntity) self : null;
        if (!(living != null) || !(living instanceof MonsterEntity)) return invulnerable;
        if (PLANE_TYPE_ID.equals(ForgeRegistries.ENTITIES.getKey(self.getType()))) return invulnerable;
        ServerWorld level = (living.level instanceof ServerWorld) ? (ServerWorld) living.level : null;
        if (living.level.isClientSide() || !(level != null)) return invulnerable;
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
