package net.mcreator.radiotowers.mixin;

import net.mcreator.radiotowers.worldgen.TowerSpawnDensity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies {@code towerSpawnDensityPercent} to RadioTowers structure sets only (matched by placement salt).
 * Vanilla uses the private spacing/separation fields directly in {@code getPotentialStructureChunk},
 * so we re-implement that method with scaled values when config != datapack baseline.
 * <p>
 * Spacing/separation/spreadType are read via {@link RandomSpreadStructurePlacementAccessor}
 * so Forge SRG remaps land in the refmap (plain {@code @Shadow} methods/fields were omitted).
 */
@Mixin(RandomSpreadStructurePlacement.class)
public abstract class RandomSpreadStructurePlacementMixin {

    @Inject(method = "getPotentialStructureChunk", at = @At("HEAD"), cancellable = true)
    private void radiotowers$scaleDensity(long seed, int chunkX, int chunkZ, CallbackInfoReturnable<ChunkPos> cir) {
        int salt = ((StructurePlacementSaltAccessor) (Object) this).radiotowers$getSalt();
        if (!TowerSpawnDensity.isRadioTowerPlacement(salt)) return;
        if (TowerSpawnDensity.densityPercent() == TowerSpawnDensity.DATAPACK_BASELINE_PERCENT) return;

        RandomSpreadStructurePlacementAccessor self = (RandomSpreadStructurePlacementAccessor) (Object) this;
        int spacing = TowerSpawnDensity.scale(self.radiotowers$getSpacing());
        int separation = TowerSpawnDensity.scale(self.radiotowers$getSeparation());
        spacing = TowerSpawnDensity.ensureSpacing(spacing, separation);

        int gridX = Math.floorDiv(chunkX, spacing);
        int gridZ = Math.floorDiv(chunkZ, spacing);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, gridX, gridZ, salt);
        int spread = spacing - separation;
        int offsetX = self.radiotowers$getSpreadType().evaluate(random, spread);
        int offsetZ = self.radiotowers$getSpreadType().evaluate(random, spread);
        cir.setReturnValue(new ChunkPos(gridX * spacing + offsetX, gridZ * spacing + offsetZ));
    }
}
