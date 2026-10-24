package net.mcreator.radiotowers.worldgen;

import com.mojang.datafixers.util.Pair;
import net.mcreator.radiotowers.RadiotowersMod;
import net.minecraft.block.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.WorldGenRegistries;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.StructureFeature;
import net.minecraft.world.gen.feature.jigsaw.JigsawPattern;
import net.minecraft.world.gen.feature.jigsaw.JigsawPiece;
import net.minecraft.world.gen.feature.structure.JigsawStructure;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.structure.VillageConfig;
import net.minecraft.world.gen.feature.template.BlockIgnoreStructureProcessor;
import net.minecraft.world.gen.feature.template.StructureProcessorList;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 1.16.5 has no structure-set datapack. One jigsaw structure holds every tower template
 * (weights 12 / 4 / 1) at spacing 34 / separation 22. The plains airdrop site keeps its own set.
 */
@Mod.EventBusSubscriber(modid = RadiotowersMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RadiotowersModStructures {
    public static final int TOWER_SPACING = 34;
    public static final int TOWER_SEPARATION = 22;
    public static final int AIRDROP_SPACING = 170;
    public static final int AIRDROP_SEPARATION = 110;
    public static final int AIRDROP_SALT = 839898102;

    public static final DeferredRegister<Structure<?>> STRUCTURES =
            DeferredRegister.create(ForgeRegistries.STRUCTURE_FEATURES, RadiotowersMod.MODID);

    public static final RegistryObject<Structure<VillageConfig>> RADIO_TOWER = STRUCTURES.register("radiotower",
            new Supplier<Structure<VillageConfig>>() {
                @Override
                public Structure<VillageConfig> get() {
                    return new JigsawStructure(VillageConfig.CODEC, 0, false, true);
                }
            });

    public static final RegistryObject<Structure<VillageConfig>> AIRDROP = STRUCTURES.register("airdrop",
            new Supplier<Structure<VillageConfig>>() {
                @Override
                public Structure<VillageConfig> get() {
                    return new AirdropSiteStructure(VillageConfig.CODEC);
                }
            });

    public static StructureFeature<?, ?> CONFIGURED_RADIO_TOWER;
    public static StructureFeature<?, ?> CONFIGURED_AIRDROP;
    public static boolean ready;

    private RadiotowersModStructures() {}

    public static void registerPoolsAndConfigured() {
        if (ready) return;
        StructureProcessorList ignore = new StructureProcessorList(Collections.singletonList(
                new BlockIgnoreStructureProcessor(Arrays.asList(Blocks.STRUCTURE_BLOCK, Blocks.WET_SPONGE))));

        registerPool("radio_towers", JigsawPattern.PlacementBehaviour.RIGID, Arrays.asList(
                poolPiece("radiotowers:tower", ignore, 12),
                poolPiece("radiotowers:tower_2", ignore, 4),
                poolPiece("radiotowers:tower_overrun", ignore, 1)));
        registerPool("airdrop", JigsawPattern.PlacementBehaviour.TERRAIN_MATCHING, Collections.singletonList(
                poolPiece("radiotowers:airdrop", ignore, 1)));

        CONFIGURED_RADIO_TOWER = registerConfigured("radiotower", RADIO_TOWER.get(), "radio_towers");
        CONFIGURED_AIRDROP = registerConfigured("airdrop", AIRDROP.get(), "airdrop");

        Structure.STRUCTURES_REGISTRY.put(RADIO_TOWER.get().getRegistryName().toString(), RADIO_TOWER.get());
        Structure.STRUCTURES_REGISTRY.put(AIRDROP.get().getRegistryName().toString(), AIRDROP.get());
        ready = true;
    }

    public static StructureSeparationSettings towerSettings() {
        return new StructureSeparationSettings(TOWER_SPACING, TOWER_SEPARATION, TowerSpawnDensity.RADIO_TOWER_SALT);
    }

    public static StructureSeparationSettings airdropSettings() {
        return new StructureSeparationSettings(AIRDROP_SPACING, AIRDROP_SEPARATION, AIRDROP_SALT);
    }

    @SubscribeEvent
    public static void onBiomeLoad(BiomeLoadingEvent event) {
        if (!ready || CONFIGURED_RADIO_TOWER == null) return;
        Biome.Category category = event.getCategory();
        if (category == Biome.Category.NETHER || category == Biome.Category.THEEND
                || category == Biome.Category.OCEAN || category == Biome.Category.RIVER
                || category == Biome.Category.NONE) {
            return;
        }
        event.getGeneration().getStructures().add(new Supplier<StructureFeature<?, ?>>() {
            @Override
            public StructureFeature<?, ?> get() {
                return CONFIGURED_RADIO_TOWER;
            }
        });
        if (category == Biome.Category.PLAINS && CONFIGURED_AIRDROP != null) {
            event.getGeneration().getStructures().add(new Supplier<StructureFeature<?, ?>>() {
                @Override
                public StructureFeature<?, ?> get() {
                    return CONFIGURED_AIRDROP;
                }
            });
        }
    }

    private static void registerPool(String path, JigsawPattern.PlacementBehaviour behaviour,
            List<Pair<Function<JigsawPattern.PlacementBehaviour, ? extends JigsawPiece>, Integer>> pieces) {
        ResourceLocation id = new ResourceLocation(RadiotowersMod.MODID, path);
        if (WorldGenRegistries.TEMPLATE_POOL.get(id) != null) return;
        JigsawPattern pattern = new JigsawPattern(id, new ResourceLocation("minecraft", "empty"), pieces, behaviour);
        Registry.register(WorldGenRegistries.TEMPLATE_POOL, id, pattern);
    }

    private static Pair<Function<JigsawPattern.PlacementBehaviour, ? extends JigsawPiece>, Integer> poolPiece(
            String template, StructureProcessorList processors, int weight) {
        return Pair.of(JigsawPiece.single(template, processors), Integer.valueOf(weight));
    }

    private static StructureFeature<?, ?> registerConfigured(String path, Structure<VillageConfig> structure, final String poolPath) {
        ResourceLocation id = new ResourceLocation(RadiotowersMod.MODID, path);
        StructureFeature<?, ?> existing = WorldGenRegistries.CONFIGURED_STRUCTURE_FEATURE.get(id);
        if (existing != null) return existing;
        StructureFeature<?, ?> configured = structure.configured(new VillageConfig(new Supplier<JigsawPattern>() {
            @Override
            public JigsawPattern get() {
                return WorldGenRegistries.TEMPLATE_POOL.get(new ResourceLocation(RadiotowersMod.MODID, poolPath));
            }
        }, 1));
        return Registry.register(WorldGenRegistries.CONFIGURED_STRUCTURE_FEATURE, id, configured);
    }

    public static void putIfAbsent(java.util.Map<Structure<?>, StructureSeparationSettings> config, Structure<?> structure, StructureSeparationSettings settings) {
        if (structure != null && config != null && !config.containsKey(structure)) {
            config.put(structure, settings);
        }
    }

    @SuppressWarnings("unused")
    private static List<Pair<Function<JigsawPattern.PlacementBehaviour, ? extends JigsawPiece>, Integer>> emptyPieces() {
        return new ArrayList<Pair<Function<JigsawPattern.PlacementBehaviour, ? extends JigsawPiece>, Integer>>();
    }
}
