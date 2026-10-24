package net.mcreator.radiotowers.mixin;

import net.mcreator.radiotowers.worldgen.RadiotowersModStructures;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.DimensionStructuresSettings;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 1.16.5 copies separation from {@code DimensionStructuresSettings} and the defaults map is final.
 * Insert the tower set (34/22) and the plains airdrop set when a dimension's settings are built.
 */
@Mixin(DimensionStructuresSettings.class)
public class DimensionStructuresSettingsMixin {
    @Shadow
    @Final
    @Mutable
    private Map<Structure<?>, StructureSeparationSettings> structureConfig;

    @Inject(method = "<init>(Ljava/util/Optional;Ljava/util/Map;)V", at = @At("RETURN"))
    private void radiotowers$addSpacing(Optional<?> stronghold, Map<Structure<?>, StructureSeparationSettings> config, CallbackInfo ci) {
        addRadioTowers();
    }

    @Inject(method = "<init>(Z)V", at = @At("RETURN"))
    private void radiotowers$addSpacingBool(boolean flag, CallbackInfo ci) {
        addRadioTowers();
    }

    private void addRadioTowers() {
        if (!RadiotowersModStructures.ready || this.structureConfig == null) return;
        if (!RadiotowersModStructures.RADIO_TOWER.isPresent()) return;
        Map<Structure<?>, StructureSeparationSettings> copy = new HashMap<Structure<?>, StructureSeparationSettings>(this.structureConfig);
        RadiotowersModStructures.putIfAbsent(copy, RadiotowersModStructures.RADIO_TOWER.get(), RadiotowersModStructures.towerSettings());
        if (RadiotowersModStructures.AIRDROP.isPresent()) {
            RadiotowersModStructures.putIfAbsent(copy, RadiotowersModStructures.AIRDROP.get(), RadiotowersModStructures.airdropSettings());
        }
        this.structureConfig = copy;
    }
}
