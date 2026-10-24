package net.mcreator.radiotowers.mixin;

import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RandomSpreadStructurePlacement.class)
public interface StructurePlacementSaltAccessor {
    @Accessor("salt")
    int radiotowers$getSalt();
}
