package net.mcreator.radiotowers.mixin;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.MonsterEntity;
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
@Mixin(MobEntity.class)
public class MobWaveMixin {

    private static final ResourceLocation PLANE_TYPE_ID = new ResourceLocation("radiotowers:planeentity");

    @ModifyVariable(method = "setNoAi", at = @At("HEAD"), argsOnly = true, remap = true)
    private boolean radiotowers$forceNoAiOffDuringWave(boolean noAi) {
        MobEntity self = (MobEntity) (Object) this;
        if (!noAi) return false;
        ServerWorld level = (self.level instanceof ServerWorld) ? (ServerWorld) self.level : null;
        if (self.level.isClientSide() || !(level != null)) return noAi;
        if (!(self instanceof MonsterEntity)) return noAi;
        if (PLANE_TYPE_ID.equals(ForgeRegistries.ENTITIES.getKey(self.getType()))) return noAi;
        if (PendingAirdropStorage.getAnyPendingInLevel(level) == null) return noAi;
        if (!isWaveMob(self)) return noAi;
        return false;
    }

    private static boolean isWaveMob(MobEntity e) {
        return e.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
            || e.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG)
            || WaveMobAggroHandler.isWaveSpawnedMob(e);
    }
}
