package net.mcreator.radiotowers.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.jigsaw.JigsawManager;
import net.minecraft.world.gen.feature.jigsaw.JigsawPatternRegistry;
import net.minecraft.world.gen.feature.structure.AbstractVillagePiece;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.structure.StructureStart;
import net.minecraft.world.gen.feature.structure.VillageConfig;
import net.minecraft.world.gen.feature.template.TemplateManager;

/**
 * Plains airdrop structure. 1.16.5 jigsaws always project onto {@code WORLD_SURFACE_WG},
 * so this start places the pool on {@code OCEAN_FLOOR_WG} instead.
 */
public class AirdropSiteStructure extends Structure<VillageConfig> {
    public AirdropSiteStructure(Codec<VillageConfig> codec) {
        super(codec);
    }

    @Override
    public IStartFactory<VillageConfig> getStartFactory() {
        return Start::new;
    }

    public static class Start extends StructureStart<VillageConfig> {
        public Start(Structure<VillageConfig> structure, int chunkX, int chunkZ, MutableBoundingBox box, int references, long seed) {
            super(structure, chunkX, chunkZ, box, references, seed);
        }

        @Override
        public void generatePieces(DynamicRegistries registries, ChunkGenerator generator, TemplateManager templates, int chunkX, int chunkZ, Biome biome, VillageConfig config) {
            int x = chunkX * 16;
            int z = chunkZ * 16;
            int y = generator.getFirstFreeHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG);
            BlockPos pos = new BlockPos(x, y, z);
            JigsawPatternRegistry.bootstrap();
            JigsawManager.addPieces(
                registries,
                config,
                AbstractVillagePiece::new,
                generator,
                templates,
                pos,
                this.pieces,
                this.random,
                false,
                false);
            this.calculateBoundingBox();
        }
    }
}
