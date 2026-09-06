package net.mcreator.radiotowers.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.core.registries.BuiltInRegistries;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.WaveSpawnTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tag entities as wave spawns when added via addFreshEntity or addEntity so WaveMobAggroHandler
 * can treat them. Only tag monsters that spawn near an active {@link PendingAirdropStorage} wave anchor
 * (see {@link PendingAirdropStorage#isNearAnyWaveStartXZ}) so global mob spawns are not wave-tagged.
 * Exclude airdrop plane (radiotowers:planeentity) so it is never tagged or repositioned.
 * MUST be listed before LevelAddEntityMixin in radiotowers.mixins.json so tag is set at HEAD before position/clear run (see WAVE_MOB_MIXIN_RULES.md).
 */
@Mixin(ServerLevel.class)
public class LevelTagWaveSpawnMixin {

    private static final ResourceLocation PLANE_TYPE_ID = ResourceLocation.parse("radiotowers:planeentity");

    /** Match {@link net.mcreator.radiotowers.events.WaveMobAggroHandler#WAVE_MOB_RANGE} so API spawns far from tower still get tagged. */
    private static final int TAG_HORIZONTAL_RANGE = 384;

    private static void tagIfWaveSpawn(ServerLevel level, Entity entity) {
        if (!(entity instanceof Monster)) return;
        // Only tag zombie-family mobs for wave accounting; never tag ambient creepers/skeletons/etc.
        if (!(entity instanceof Zombie)) return;
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (PLANE_TYPE_ID.equals(typeId)) return; // never tag the airdrop plane
        if (PendingAirdropStorage.getAnyPendingInLevel(level) == null) return;
        // Only tag spawns near an active wave anchor — never tag random overworld mobs during a pending airdrop.
        if (!PendingAirdropStorage.isNearAnyWaveStartXZ(level, entity.getX(), entity.getZ(), TAG_HORIZONTAL_RANGE)) return;
        entity.getPersistentData().putBoolean(WaveSpawnTag.WAVE_SPAWN_TAG, true);
    }

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), remap = true)
    private void radiotowers$tagWaveSpawnAddFresh(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        tagIfWaveSpawn((ServerLevel) (Object) this, entity);
    }

    @Inject(method = "addEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), remap = true)
    private void radiotowers$tagWaveSpawnAddEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        tagIfWaveSpawn((ServerLevel) (Object) this, entity);
    }

    @Inject(method = "addWithUUID(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), remap = true)
    private void radiotowers$tagWaveSpawnAddWithUUID(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        tagIfWaveSpawn((ServerLevel) (Object) this, entity);
    }
}
