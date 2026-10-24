package net.mcreator.radiotowers.mixin;

import net.mcreator.radiotowers.worldgen.TowerSpawnDensity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.16.5 places structures with {@link Structure#getPotentialFeatureChunk}.
 * Radio tower spacing is scaled by {@code towerSpawnDensityPercent}; 100 keeps 34 / 22.
 * Every radiotowers structure is a surface structure (the private step map only knows vanilla entries).
 */
@Mixin(Structure.class)
public abstract class StructurePlacementMixin {
    @Inject(method = "step", at = @At("HEAD"), cancellable = true)
    private void radiotowers$surfaceStep(CallbackInfoReturnable<GenerationStage.Decoration> cir) {
        ResourceLocation id = ((Structure<?>) (Object) this).getRegistryName();
        if (id != null && "radiotowers".equals(id.getNamespace())) {
            cir.setReturnValue(GenerationStage.Decoration.SURFACE_STRUCTURES);
        }
    }

    @Inject(method = "getPotentialFeatureChunk", at = @At("HEAD"), cancellable = true)
    private void radiotowers$scaleDensity(StructureSeparationSettings settings, long seed, SharedSeedRandom random, int chunkX, int chunkZ, CallbackInfoReturnable<ChunkPos> cir) {
        if (settings == null || !TowerSpawnDensity.isRadioTowerPlacement(settings.salt())) return;
        if (TowerSpawnDensity.densityPercent() == TowerSpawnDensity.DATAPACK_BASELINE_PERCENT) return;

        int spacing = TowerSpawnDensity.scale(settings.spacing());
        int separation = TowerSpawnDensity.scale(settings.separation());
        spacing = TowerSpawnDensity.ensureSpacing(spacing, separation);
        int spread = spacing - separation;

        int gridX = Math.floorDiv(chunkX, spacing);
        int gridZ = Math.floorDiv(chunkZ, spacing);
        random.setLargeFeatureWithSalt(seed, gridX, gridZ, settings.salt());
        int offsetX = random.nextInt(spread);
        int offsetZ = random.nextInt(spread);
        cir.setReturnValue(new ChunkPos(gridX * spacing + offsetX, gridZ * spacing + offsetZ));
    }
}
