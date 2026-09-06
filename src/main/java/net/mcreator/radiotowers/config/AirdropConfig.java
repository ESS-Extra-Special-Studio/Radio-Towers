package net.mcreator.radiotowers.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.List;

/**
 * Airdrop / wave / tower worldgen config for RadioTowers.
 * File: {@code config/radiotowers-common.toml}
 * Optional sidecars under {@code config/radiotowers/} (whitelist, blacklist, custom catalog).
 * <p>
 * Dead Air is <strong>not</strong> required for these keys. Dead Air only adds radio stations,
 * walkies, panel music, and catalog addon items (e.g. T1.Radio).
 */
public class AirdropConfig {

    public static final String DEFAULT_STANDARD_LOOT_TABLE = "radiotowers:chests/airdrop_loot";
    public static final String TEST_BLACKLIST_GUN = "tacz:gun/m1911";
    public static final String TEST_BLACKLIST_AMMO = "tacz:ammo/9mm";
    public static final String LOOT_TABLE_EASY = "radiotowers:airdrop_loot_easy";
    public static final String LOOT_TABLE_MEDIUM = "radiotowers:airdrop_loot_medium";
    public static final String LOOT_TABLE_HARD = "radiotowers:airdrop_loot_hard";

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // --- Catalog (player picks items in Call Airdrop UI) ---
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST_TAGS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST_ITEMS;
    public static final ModConfigSpec.ConfigValue<String> WHITELIST_FILE;
    public static final ModConfigSpec.ConfigValue<String> BLACKLIST_FILE;
    public static final ModConfigSpec.IntValue DEFAULT_DIFFICULTY_PER_ITEM;
    public static final ModConfigSpec.ConfigValue<Integer> MINIMUM_CATALOG_POINTS;
    public static final ModConfigSpec.IntValue MAX_TOTAL_DIFFICULTY;
    public static final ModConfigSpec.BooleanValue INCLUDE_TACZ_AMMO;
    public static final ModConfigSpec.BooleanValue INCLUDE_TACZ_GUNS;
    public static final ModConfigSpec.BooleanValue SIMPLIFY_TACZ_ICONS_IN_AIRDROP_LIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CUSTOM_CATALOG_ENTRIES;
    public static final ModConfigSpec.ConfigValue<String> CUSTOM_CATALOG_FILE;
    public static final ModConfigSpec.BooleanValue INCLUDE_NON_VANILLA_FORGE_TAG_ITEMS;
    public static final ModConfigSpec.BooleanValue ENABLE_END_RECIPE_ITEMS;

    // --- Standard airdrop (loot-table lever / empty order) ---
    public static final ModConfigSpec.ConfigValue<String> STANDARD_AIRDROP_LOOT_TABLE;
    public static final ModConfigSpec.BooleanValue ENABLE_STANDARD_AIRDROP;
    public static final ModConfigSpec.BooleanValue ENABLE_CATALOG_AIRDROP;
    public static final ModConfigSpec.ConfigValue<String> ACTIVE_AIRDROP_PROFILE;

    // --- Cooldowns ---
    public static final ModConfigSpec.IntValue STANDARD_AIRDROP_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue WAVE_DELIVERY_COOLDOWN_MINUTES;

        // --- Waves (ESL Wave API preferred; Berezka optional fallback) ---
    public static final ModConfigSpec.BooleanValue USE_ZOMBIE_WAVES_FOR_AIRDROP;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WAVES_AGGRO_EXCLUDE_ENTITY_IDS;
    public static final ModConfigSpec.IntValue WAVE_DURATION_MINUTES;
    public static final ModConfigSpec.IntValue MAX_WAVES;
    public static final ModConfigSpec.IntValue BASE_ZOMBIES_PER_WAVE;
    public static final ModConfigSpec.IntValue ZOMBIES_PER_TIER_STEP;

    // --- Worldgen ---
    public static final ModConfigSpec.IntValue TOWER_SPAWN_DENSITY_PERCENT;
    public static final ModConfigSpec.BooleanValue GUARANTEE_SPAWN_TOWER;
    public static final ModConfigSpec.IntValue SPAWN_TOWER_SEARCH_RADIUS_CHUNKS;

    static {
        // Keep the existing top-level paths ([airdrop], [waves], [worldgen]) so
        // modpacks update safely. Comments and ordering provide the visual groups.

        // ========== AIRDROPS ==========
        BUILDER.comment(
            "============================================================",
            "AIRDROPS",
            "Catalog airdrop: the player chooses items by point cost.",
            "Standard airdrop: the crate is filled from a loot table.",
            "Dead Air is optional and does not own these settings.",
            "============================================================"
        ).push("airdrop");

        // Start-here settings first.
        MINIMUM_CATALOG_POINTS = BUILDER
            .comment(
                "----- START HERE: AIRDROP SIZE AND LOOT -----",
                "Minimum points required to launch a catalog airdrop.",
                "Must be between 10 and 500 in 10-point steps. Default: 10."
            )
            .define("minimumCatalogPoints", 10,
                value -> value instanceof Number number
                    && number.intValue() >= 10
                    && number.intValue() <= 500
                    && number.intValue() % 10 == 0);
        MAX_TOTAL_DIFFICULTY = BUILDER
            .comment(
                "Maximum points a player can spend on one catalog airdrop.",
                "Default: 100."
            )
            .defineInRange("maxTotalDifficulty", 100, 20, 500);
        STANDARD_AIRDROP_LOOT_TABLE = BUILDER
            .comment(
                "Loot table used by standard (non-catalog) airdrop crates.",
                "Built-in: radiotowers:chests/airdrop_loot (default),",
                "radiotowers:airdrop_loot_easy, radiotowers:airdrop_loot_medium, radiotowers:airdrop_loot_hard.",
                "Or any datapack loot table id. Switch in-game with /radiotowers loot <easy|medium|hard|default>."
            )
            .define("standardAirdropLootTable", DEFAULT_STANDARD_LOOT_TABLE);
        ENABLE_STANDARD_AIRDROP = BUILDER
            .comment("If false, the standard loot-table lever does nothing.")
            .define("enableStandardAirdrop", true);
        ENABLE_CATALOG_AIRDROP = BUILDER
            .comment("If false, players cannot start a catalog (point-buy) airdrop.")
            .define("enableCatalogAirdrop", true);
        ACTIVE_AIRDROP_PROFILE = BUILDER
            .comment(
                "Optional profile name under config/radiotowers/profiles/<name>.json.",
                "Empty = use this toml only. /radiotowers profile save|load <name> writes that folder."
            )
            .define("activeAirdropProfile", "");
        STANDARD_AIRDROP_COOLDOWN_MINUTES = BUILDER
            .comment("Minutes before the player can request another standard airdrop.")
            .defineInRange("standardAirdropCooldownMinutes", 3, 0, 120);
        WAVE_DELIVERY_COOLDOWN_MINUTES = BUILDER
            .comment("Minutes before the player can request another wave-delivered catalog airdrop.")
            .defineInRange("waveDeliveryCooldownMinutes", 10, 0, 120);

        // Common catalog switches.
        INCLUDE_TACZ_AMMO = BUILDER
            .comment(
                "----- CATALOG CONTENT -----",
                "Add TaCZ ammo automatically when TaCZ is installed."
            )
            .define("includeTaczAmmo", true);
        INCLUDE_TACZ_GUNS = BUILDER
            .comment("Add TaCZ guns automatically when TaCZ is installed.")
            .define("includeTaczGuns", true);
        SIMPLIFY_TACZ_ICONS_IN_AIRDROP_LIST = BUILDER
            .comment("Use neutral placeholders if TaCZ icons appear wrong or missing.")
            .define("simplifyTaczIconsInAirdropList", false);
        CUSTOM_CATALOG_ENTRIES = BUILDER
            .comment(
                "Add specific items or override their point costs.",
                "Format: \"namespace:item=points\", for example \"other_mod:medkit=15\".",
                "TaCZ uses the same id aliases as blacklistItems: tacz:gun/m1911=80 also reprices tacz:m1911",
                "and any catalog row whose id or GunId/AmmoId ends in m1911. Bare names (m1911=80) work too.",
                "This changes only the point cost of matching auto-detected rows; it does not register new TaCZ items."
            )
            .defineList("customCatalogEntries", Collections.emptyList(), o -> o instanceof String);
        CUSTOM_CATALOG_FILE = BUILDER
            .comment(
                "Optional file under config/radiotowers/ with one item=points entry per line.",
                "Example: custom_catalog.txt. Empty means disabled."
            )
            .define("customCatalogFile", "");

        // Advanced tag filters after the simple explicit-item controls.
        WHITELIST_TAGS = BUILDER
            .comment(
                "----- ADVANCED CATALOG FILTERS -----",
                "Extra Forge item tags to include. Empty uses the built-in defaults.",
                "Vanilla valuables are added separately; use blacklistItems to remove them."
            )
            .defineList("whitelistTags", Collections.emptyList(), o -> o instanceof String);
        BLACKLIST_ITEMS = BUILDER
            .comment(
                "Item ids that must never appear in the catalog, including TaCZ and addon entries.",
                "Supports glob patterns: tacz:gun/* blocks all TaCZ guns, minecraft:*_sword blocks all swords.",
                "TaCZ: tacz:gun/m1911 also hides tacz:m1911 and any id ending in /m1911. Bare names (m1911, 9mm) work too.",
                "Custom catalog lines cannot put a blacklisted id back."
            )
            .defineList("blacklistItems", List.of(
                "minecraft:nether_star", "minecraft:command_block", "minecraft:barrier",
                "minecraft:structure_block", "minecraft:jigsaw", "minecraft:bedrock",
                "minecraft:spectral_arrow", "minecraft:tipped_arrow"
            ), o -> o instanceof String);
        WHITELIST_FILE = BUILDER
            .comment(
                "Optional file under config/radiotowers/ with one extra tag id per line.",
                "Example: whitelist.txt. Empty means disabled."
            )
            .define("whitelistFile", "");
        BLACKLIST_FILE = BUILDER
            .comment(
                "Optional file under config/radiotowers/ with one excluded item id per line.",
                "Example: blacklist.txt. Empty means disabled."
            )
            .define("blacklistFile", "");
        DEFAULT_DIFFICULTY_PER_ITEM = BUILDER
            .comment("Point cost assigned to items loaded from tags. Rare built-in items may cost more.")
            .defineInRange("defaultDifficultyPerItem", 2, 1, 20);
        INCLUDE_NON_VANILLA_FORGE_TAG_ITEMS = BUILDER
            .comment(
                "Allow modded items from broad forge:ingots, forge:gems, and forge:foods tags.",
                "Leave false to avoid flooding the catalog with every modded material."
            )
            .define("includeNonVanillaForgeTagItems", false);
        ENABLE_END_RECIPE_ITEMS = BUILDER
            .comment("Include dragon egg, dragon head, and dragon breath in the catalog.")
            .define("enableEndRecipeItems", false);
        BUILDER.pop();

        // ========== WAVES ==========
        BUILDER.comment(
            "============================================================",
            "DEFENSE WAVES",
            "Catalog airdrops use Extra Special LIB waves when ESL is installed.",
            "If ESL is absent, Berezka Zombie Waves API is used when present.",
            "Set useZombieWavesForAirdrop=false for immediate plane delivery.",
            "============================================================"
        ).push("waves");
        USE_ZOMBIE_WAVES_FOR_AIRDROP = BUILDER
            .comment(
                "true: catalog airdrops start defense waves when ESL or Berezka is installed.",
                "false: the plane arrives immediately, even when a wave backend is installed."
            )
            .define("useZombieWavesForAirdrop", true);
        MAX_WAVES = BUILDER
            .comment("Maximum number of rounds before the airdrop is delivered.")
            .defineInRange("maxWaves", 5, 1, 20);
        BASE_ZOMBIES_PER_WAVE = BUILDER
            .comment("Zombies in each round at difficulty tier 1 (10 airdrop points).")
            .defineInRange("baseZombiesPerWave", 20, 1, 200);
        ZOMBIES_PER_TIER_STEP = BUILDER
            .comment(
                "Extra zombies added per difficulty tier.",
                "Example with defaults: tier 1 = 20, tier 2 = 30, tier 3 = 40."
            )
            .defineInRange("zombiesPerTierStep", 10, 0, 100);
        WAVE_DURATION_MINUTES = BUILDER
            .comment("Minutes allowed for each defense round before it times out.")
            .defineInRange("waveDurationMinutes", 3, 1, 60);
        WAVES_AGGRO_EXCLUDE_ENTITY_IDS = BUILDER
            .comment(
                "Entities that defense-wave aggro must ignore.",
                "Use registry ids such as minecraft:enderman or minecraft:creeper."
            )
            .defineList("aggroExcludeEntityIds", List.of("minecraft:enderman"), o -> o instanceof String);
        BUILDER.pop();

        // ========== WORLDGEN ==========
        BUILDER.comment(
            "============================================================",
            "RADIO TOWER WORLD GENERATION",
            "These options affect RadioTowers structures, not Dead Air signal range.",
            "Density changes only affect newly generated chunks.",
            "============================================================"
        ).push("worldgen");
        TOWER_SPAWN_DENSITY_PERCENT = BUILDER
            .comment(
                "Natural tower density as a percentage of the built-in datapack setting.",
                "100 = normal, 200 = denser, 50 = rarer. New chunks only."
            )
            .defineInRange("towerSpawnDensityPercent", 100, 50, 200);
        GUARANTEE_SPAWN_TOWER = BUILDER
            .comment("Place one inactive STANDARD tower near world spawn once per overworld.")
            .define("guaranteeSpawnTower", true);
        SPAWN_TOWER_SEARCH_RADIUS_CHUNKS = BUILDER
            .comment(
                "How far to search for a safe location for the guaranteed tower.",
                "Only already-loaded chunks are considered."
            )
            .defineInRange("spawnTowerSearchRadiusChunks", 12, 4, 32);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    /** Ticks for one defense wave (from {@link #WAVE_DURATION_MINUTES}). */
    public static int getWaveDurationTicks() {
        return Math.max(1, WAVE_DURATION_MINUTES.get()) * 60 * 20;
    }

    /** Ticks of cooldown after a standard airdrop. */
    public static int getStandardAirdropCooldownTicks() {
        return Math.max(0, STANDARD_AIRDROP_COOLDOWN_MINUTES.get()) * 60 * 20;
    }

    /** Ticks of cooldown after a wave-delivered airdrop. */
    public static int getWaveDeliveryCooldownTicks() {
        return Math.max(0, WAVE_DELIVERY_COOLDOWN_MINUTES.get()) * 60 * 20;
    }

    /**
     * Loot table id for standard (non-catalog) airdrop crates.
     * Invalid config values fall back to {@link #DEFAULT_STANDARD_LOOT_TABLE}.
     */
    public static ResourceLocation getStandardAirdropLootTable() {
        String raw = STANDARD_AIRDROP_LOOT_TABLE.get();
        if (raw == null || raw.isBlank()) {
            return ResourceLocation.parse(DEFAULT_STANDARD_LOOT_TABLE);
        }
        ResourceLocation parsed = ResourceLocation.tryParse(raw.trim());
        return parsed != null ? parsed : ResourceLocation.parse(DEFAULT_STANDARD_LOOT_TABLE);
    }

    /** Whether catalog airdrops should use ESL (preferred) or Berezka waves when a backend is present. */
    public static boolean shouldUseZombieWavesForAirdrop() {
        return USE_ZOMBIE_WAVES_FOR_AIRDROP.get();
    }

    /** True if this entity type is excluded from defense-wave aggro / capture. */
    public static boolean isWaveAggroExcluded(net.minecraft.world.entity.EntityType<?> type) {
        if (type == null) return false;
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) return false;
        List<? extends String> list = WAVES_AGGRO_EXCLUDE_ENTITY_IDS.get();
        if (list == null || list.isEmpty()) return false;
        String needle = id.toString();
        for (Object o : list) {
            if (o instanceof String raw && needle.equalsIgnoreCase(raw.trim())) return true;
        }
        return false;
    }

    /** Minimum valid catalog order total. Config validation guarantees a 10-point step. */
    public static int getMinimumCatalogPoints() {
        Integer configured = MINIMUM_CATALOG_POINTS.get();
        int requested = configured == null ? 10 : Math.max(10, Math.min(500, configured));
        int max = Math.max(20, MAX_TOTAL_DIFFICULTY.get());
        int highestValidStep = Math.max(10, (max / 10) * 10);
        return Math.min(requested, highestValidStep);
    }

    /** Distinctive values so each RadioTowers key is obvious in-game. Writes toml. */
    public static void applyTestProfile() {
        MINIMUM_CATALOG_POINTS.set(20);
        MAX_TOTAL_DIFFICULTY.set(60);
        STANDARD_AIRDROP_LOOT_TABLE.set(DEFAULT_STANDARD_LOOT_TABLE);
        ENABLE_STANDARD_AIRDROP.set(true);
        ENABLE_CATALOG_AIRDROP.set(true);
        ACTIVE_AIRDROP_PROFILE.set("");
        STANDARD_AIRDROP_COOLDOWN_MINUTES.set(0);
        WAVE_DELIVERY_COOLDOWN_MINUTES.set(1);
        INCLUDE_TACZ_AMMO.set(true);
        INCLUDE_TACZ_GUNS.set(true);
        SIMPLIFY_TACZ_ICONS_IN_AIRDROP_LIST.set(false);
        CUSTOM_CATALOG_ENTRIES.set(List.of());
        CUSTOM_CATALOG_FILE.set("");
        WHITELIST_TAGS.set(List.of());
        BLACKLIST_ITEMS.set(List.of(
            "minecraft:nether_star", "minecraft:command_block", "minecraft:barrier",
            "minecraft:structure_block", "minecraft:jigsaw", "minecraft:bedrock",
            "minecraft:spectral_arrow", "minecraft:tipped_arrow",
            TEST_BLACKLIST_GUN, TEST_BLACKLIST_AMMO
        ));
        WHITELIST_FILE.set("");
        BLACKLIST_FILE.set("");
        DEFAULT_DIFFICULTY_PER_ITEM.set(2);
        INCLUDE_NON_VANILLA_FORGE_TAG_ITEMS.set(false);
        ENABLE_END_RECIPE_ITEMS.set(true);
        USE_ZOMBIE_WAVES_FOR_AIRDROP.set(true);
        MAX_WAVES.set(2);
        BASE_ZOMBIES_PER_WAVE.set(5);
        ZOMBIES_PER_TIER_STEP.set(5);
        WAVE_DURATION_MINUTES.set(1);
        WAVES_AGGRO_EXCLUDE_ENTITY_IDS.set(List.of("minecraft:enderman", "minecraft:creeper"));
        TOWER_SPAWN_DENSITY_PERCENT.set(200);
        GUARANTEE_SPAWN_TOWER.set(true);
        SPAWN_TOWER_SEARCH_RADIUS_CHUNKS.set(12);
        saveAll();
    }

    public static void restoreDefaults() {
        MINIMUM_CATALOG_POINTS.set(10);
        MAX_TOTAL_DIFFICULTY.set(100);
        STANDARD_AIRDROP_LOOT_TABLE.set(DEFAULT_STANDARD_LOOT_TABLE);
        ENABLE_STANDARD_AIRDROP.set(true);
        ENABLE_CATALOG_AIRDROP.set(true);
        ACTIVE_AIRDROP_PROFILE.set("");
        STANDARD_AIRDROP_COOLDOWN_MINUTES.set(3);
        WAVE_DELIVERY_COOLDOWN_MINUTES.set(10);
        INCLUDE_TACZ_AMMO.set(true);
        INCLUDE_TACZ_GUNS.set(true);
        SIMPLIFY_TACZ_ICONS_IN_AIRDROP_LIST.set(false);
        CUSTOM_CATALOG_ENTRIES.set(List.of());
        CUSTOM_CATALOG_FILE.set("");
        WHITELIST_TAGS.set(List.of());
        BLACKLIST_ITEMS.set(List.of(
            "minecraft:nether_star", "minecraft:command_block", "minecraft:barrier",
            "minecraft:structure_block", "minecraft:jigsaw", "minecraft:bedrock",
            "minecraft:spectral_arrow", "minecraft:tipped_arrow"
        ));
        WHITELIST_FILE.set("");
        BLACKLIST_FILE.set("");
        DEFAULT_DIFFICULTY_PER_ITEM.set(2);
        INCLUDE_NON_VANILLA_FORGE_TAG_ITEMS.set(false);
        ENABLE_END_RECIPE_ITEMS.set(false);
        USE_ZOMBIE_WAVES_FOR_AIRDROP.set(true);
        MAX_WAVES.set(5);
        BASE_ZOMBIES_PER_WAVE.set(20);
        ZOMBIES_PER_TIER_STEP.set(10);
        WAVE_DURATION_MINUTES.set(3);
        WAVES_AGGRO_EXCLUDE_ENTITY_IDS.set(List.of("minecraft:enderman"));
        TOWER_SPAWN_DENSITY_PERCENT.set(100);
        GUARANTEE_SPAWN_TOWER.set(true);
        SPAWN_TOWER_SEARCH_RADIUS_CHUNKS.set(12);
        saveAll();
    }

    public static void saveAll() {
        MINIMUM_CATALOG_POINTS.save();
        MAX_TOTAL_DIFFICULTY.save();
        STANDARD_AIRDROP_LOOT_TABLE.save();
        ENABLE_STANDARD_AIRDROP.save();
        ENABLE_CATALOG_AIRDROP.save();
        ACTIVE_AIRDROP_PROFILE.save();
        STANDARD_AIRDROP_COOLDOWN_MINUTES.save();
        WAVE_DELIVERY_COOLDOWN_MINUTES.save();
        INCLUDE_TACZ_AMMO.save();
        INCLUDE_TACZ_GUNS.save();
        SIMPLIFY_TACZ_ICONS_IN_AIRDROP_LIST.save();
        CUSTOM_CATALOG_ENTRIES.save();
        CUSTOM_CATALOG_FILE.save();
        WHITELIST_TAGS.save();
        BLACKLIST_ITEMS.save();
        WHITELIST_FILE.save();
        BLACKLIST_FILE.save();
        DEFAULT_DIFFICULTY_PER_ITEM.save();
        INCLUDE_NON_VANILLA_FORGE_TAG_ITEMS.save();
        ENABLE_END_RECIPE_ITEMS.save();
        USE_ZOMBIE_WAVES_FOR_AIRDROP.save();
        MAX_WAVES.save();
        BASE_ZOMBIES_PER_WAVE.save();
        ZOMBIES_PER_TIER_STEP.save();
        WAVE_DURATION_MINUTES.save();
        WAVES_AGGRO_EXCLUDE_ENTITY_IDS.save();
        TOWER_SPAWN_DENSITY_PERCENT.save();
        GUARANTEE_SPAWN_TOWER.save();
        SPAWN_TOWER_SEARCH_RADIUS_CHUNKS.save();
    }

    private AirdropConfig() {}
}
