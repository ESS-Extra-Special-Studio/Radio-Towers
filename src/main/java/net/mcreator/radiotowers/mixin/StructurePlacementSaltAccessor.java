package net.mcreator.radiotowers.mixin;

import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StructurePlacement.class)
public interface StructurePlacementSaltAccessor {
    @Accessor("salt")
    int radiotowers$getSalt();
}
