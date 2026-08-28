package net.mcreator.radiotowers.mixin;

import net.mcreator.radiotowers.worldgen.TowerSpawnDensity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies {@code towerSpawnDensityPercent} to RadioTowers structure sets only (matched by placement salt).
 * Vanilla uses the private spacing/separation fields directly in {@code getPotentialStructureChunk},
 * so we re-implement that method with scaled values when config != datapack baseline.
 */
@Mixin(RandomSpreadStructurePlacement.class)
public abstract class RandomSpreadStructurePlacementMixin {

    @Shadow public abstract int spacing();

    @Shadow public abstract int separation();

    @Shadow public abstract RandomSpreadType spreadType();

    @Inject(method = "getPotentialStructureChunk", at = @At("HEAD"), cancellable = true)
    private void radiotowers$scaleDensity(long seed, int chunkX, int chunkZ, CallbackInfoReturnable<ChunkPos> cir) {
        int salt = ((StructurePlacementSaltAccessor) (Object) this).radiotowers$getSalt();
        if (!TowerSpawnDensity.isRadioTowerPlacement(salt)) return;
        if (TowerSpawnDensity.densityPercent() == TowerSpawnDensity.DATAPACK_BASELINE_PERCENT) return;

        int spacing = TowerSpawnDensity.scale(this.spacing());
        int separation = TowerSpawnDensity.scale(this.separation());
        spacing = TowerSpawnDensity.ensureSpacing(spacing, separation);

        int gridX = Math.floorDiv(chunkX, spacing);
        int gridZ = Math.floorDiv(chunkZ, spacing);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, gridX, gridZ, salt);
        int spread = spacing - separation;
        int offsetX = this.spreadType().evaluate(random, spread);
        int offsetZ = this.spreadType().evaluate(random, spread);
        cir.setReturnValue(new ChunkPos(gridX * spacing + offsetX, gridZ * spacing + offsetZ));
    }
}
