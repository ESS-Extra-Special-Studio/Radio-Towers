package net.mcreator.radiotowers.mixin;

import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RandomSpreadStructurePlacement.class)
public interface RandomSpreadStructurePlacementAccessor {
    @Accessor("spacing")
    int radiotowers$getSpacing();

    @Accessor("separation")
    int radiotowers$getSeparation();

    @Accessor("spreadType")
    RandomSpreadType radiotowers$getSpreadType();
}
