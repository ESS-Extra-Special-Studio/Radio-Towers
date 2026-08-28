package net.mcreator.radiotowers.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.registries.ForgeRegistries;
import net.mcreator.radiotowers.events.WaveMobAggroHandler;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.WaveSpawnTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * During an active wave, never allow Monsters to stay NoAI — the API may set it;
 * we force it off so wave mobs always have AI and can move.
 */
@Mixin(Mob.class)
public class MobWaveMixin {

    private static final ResourceLocation PLANE_TYPE_ID = ResourceLocation.parse("radiotowers:planeentity");

    @ModifyVariable(method = "setNoAi", at = @At("HEAD"), argsOnly = true, remap = true)
    private boolean radiotowers$forceNoAiOffDuringWave(boolean noAi) {
        Mob self = (Mob) (Object) this;
        if (!noAi) return false;
        if (self.level().isClientSide() || !(self.level() instanceof ServerLevel level)) return noAi;
        if (!(self instanceof Monster)) return noAi;
        if (PLANE_TYPE_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(self.getType()))) return noAi;
        if (PendingAirdropStorage.getAnyPendingInLevel(level) == null) return noAi;
        if (!isWaveMob(self)) return noAi;
        return false;
    }

    private static boolean isWaveMob(Mob e) {
        return e.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
            || e.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
            || WaveMobAggroHandler.isWaveSpawnedMob(e);
    }
}
