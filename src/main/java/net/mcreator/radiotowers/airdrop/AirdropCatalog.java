package net.mcreator.radiotowers.airdrop;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Catalog of items that can be requested in an airdrop, each with a difficulty point cost.
 * Built from config whitelist (Forge tags), blacklist, and optional Tacz ammo.
 */
public final class AirdropCatalog {

    public static final class Entry {
        private final ResourceLocation itemId;
        private final int difficultyPoints;
        /** When > 1, one "unit" in the GUI represents this many items (e.g. 64 for ammo stacks). */
        private final int defaultStackSize;
        /** When non-null, createStack() and isValid() use this instead of itemId (for TaCZ ammo_box/gun with NBT). */
        private final Supplier<ItemStack> customStackCreator;

        public Entry(ResourceLocation itemId, int difficultyPoints) {
            this(itemId, difficultyPoints, 1, null);
        }

        public Entry(ResourceLocation itemId, int difficultyPoints, int defaultStackSize) {
            this(itemId, difficultyPoints, defaultStackSize, null);
        }

        /** Entry that uses a custom stack (e.g. TaCZ ammo_box with setAmmoId, gun with setGunId). itemId is used for dedup and display key. */
        public Entry(ResourceLocation itemId, int difficultyPoints, int defaultStackSize, Supplier<ItemStack> customStackCreator) {
            this.itemId = itemId;
            this.difficultyPoints = difficultyPoints;
            this.defaultStackSize = defaultStackSize <= 0 ? 1 : Math.min(99, defaultStackSize);
            this.customStackCreator = customStackCreator;
        }

        public ResourceLocation getItemId() { return itemId; }
        public int getDifficultyPoints() { return difficultyPoints; }
        /** Number of items per ordered unit (1 = single item, 64 = full stack for ammo). */
        public int getDefaultStackSize() { return defaultStackSize; }

        /** Return the same catalog entry at a new cost without changing how its stack is created. */
        public Entry withDifficultyPoints(int points) {
            return new Entry(itemId, points, defaultStackSize, customStackCreator);
        }

        public ItemStack createStack() {
            if (customStackCreator != null) {
                try {
                    ItemStack s = customStackCreator.get();
                    if (s == null || s.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                    // Catalog "unit" size (e.g. 16 notes, 64 ammo) must show on the icon.
                    if (defaultStackSize > 1 && s.getCount() < defaultStackSize) {
                        s = s.copy();
                        s.setCount(defaultStackSize);
                    }
                    return s;
                } catch (Exception e) {
                    return ItemStack.EMPTY;
                }
            }
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            return item != null && item != Items.AIR
                ? new ItemStack(item, defaultStackSize)
                : ItemStack.EMPTY;
        }

        /** True if this entry would create a valid, displayable stack (item exists and is not AIR). */
        public boolean isValid() {
            if (customStackCreator != null) {
                try {
                    ItemStack s = customStackCreator.get();
                    return s != null && !s.isEmpty();
                } catch (Exception e) {
                    return false;
                }
            }
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            return item != null && item != Items.AIR;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    /** Addon entries for the airdrop shop: add to this list (e.g. in mod init) to register custom items. Never cleared. Use {@link Entry} (itemId, points) or (itemId, points, defaultStackSize). */
    public static final List<Entry> ADDON_ENTRIES = new ArrayList<>();
    private static boolean built = false;
    /** TaCZ creative cache size when catalog was last built; if cache grows (user opened creative), we rebuild so icons use cached stacks. */
    private static int lastBuiltTaczCacheSize = 0;

    /**
     * Default tags when whitelist is empty (vanilla + common world items). We do not use {@code forge:ingots} or
     * {@code forge:gems} here — those pull <strong>every</strong> modded ingot/gem at one flat cost.
     * <p>
     * <strong>Vanilla is not removed:</strong> iron/gold/copper/netherite ingots, diamond, emerald, lapis, quartz,
     * prismarine crystals, amethyst, etc. are always added in {@link #ensureVanillaCatalogBasics} with tuned point costs.
     * Pack authors who want modded metals can add {@code forge:ingots} to {@code whitelistTags} and set
     * {@code includeNonVanillaForgeTagItems} to true, or use {@code customCatalogEntries}.
     */
    private static final List<String> DEFAULT_WHITELIST_TAGS = List.of(
        "forge:foods",
        "forge:wool",
        "minecraft:logs"
    );

    /** Per-item difficulty overrides (rare = higher). Used when building from tags. */
    private static final Map<ResourceLocation, Integer> ITEM_DIFFICULTY = new HashMap<>();
    /** Per-tag default difficulty when item not in ITEM_DIFFICULTY. */
    private static final Map<String, Integer> TAG_DIFFICULTY = new HashMap<>();
    static {
        // Rare / endgame
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:netherite_ingot"), 50);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:totem_of_undying"), 25);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:enchanted_golden_apple"), 15);
        // Gems (re-evaluated so rare isn't cheap)
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:diamond"), 10);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:emerald"), 10);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:prismarine_crystals"), 10);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:amethyst_shard"), 5);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:lapis_lazuli"), 5);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:nether_quartz"), 6);
        // Ingots
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:gold_ingot"), 5);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:iron_ingot"), 3);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:copper_ingot"), 2);
        // Food
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:golden_apple"), 5);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:bread"), 2);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:cookie"), 2);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:apple"), 3);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:fishing_rod"), 5);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:jukebox"), 25);
        ITEM_DIFFICULTY.put(ResourceLocation.parse("minecraft:flint_and_steel"), 5);
        TAG_DIFFICULTY.put("forge:gems", 6);
        TAG_DIFFICULTY.put("forge:gems/diamond", 10);
        TAG_DIFFICULTY.put("forge:ingots", 3);
        TAG_DIFFICULTY.put("forge:foods", 2);
        TAG_DIFFICULTY.put("forge:wool", 1);
        TAG_DIFFICULTY.put("minecraft:logs", 1);
    }

    /** All catalog entries (read-only). Builds from config tags + blacklist + Tacz on first call. Invalid entries excluded. Sorted by cost (cheapest first). */
    public static synchronized List<Entry> getEntries() {
        if (ModList.get().isLoaded("tacz") && TaczAirdropIntegration.getCreativeCacheSize() > lastBuiltTaczCacheSize)
            built = false;
        if (!built) {
            buildCatalog();
            built = true;
        }
        List<Entry> out = new ArrayList<>();
        for (Entry e : ENTRIES)
            if (e.isValid()) out.add(e);
        out.sort(java.util.Comparator.comparingInt(Entry::getDifficultyPoints));
        return out;
    }

    private static void buildCatalog() {
        ENTRIES.clear();
        List<String> whitelist = new ArrayList<>();
        List<? extends String> configTags = AirdropConfig.WHITELIST_TAGS.get();
        if (configTags != null && !configTags.isEmpty()) {
            for (Object o : configTags) if (o instanceof String s) whitelist.add(s);
        } else
            whitelist.addAll(DEFAULT_WHITELIST_TAGS);
        loadLinesFromFile(AirdropConfig.WHITELIST_FILE.get(), whitelist);

        BlacklistMatcher blacklist = new BlacklistMatcher();
        for (Object o : AirdropConfig.BLACKLIST_ITEMS.get()) {
            if (o instanceof String s) blacklist.add(s.trim());
        }
        List<String> blacklistLines = new ArrayList<>();
        loadLinesFromFile(AirdropConfig.BLACKLIST_FILE.get(), blacklistLines);
        for (String s : blacklistLines) blacklist.add(s.trim());

        int defaultDifficulty = AirdropConfig.DEFAULT_DIFFICULTY_PER_ITEM.get();
        try {
            var tags = ForgeRegistries.ITEMS.tags();
            if (tags != null) {
                for (String tagId : whitelist) {
                    try {
                        ResourceLocation rl = ResourceLocation.parse(tagId);
                        TagKey<Item> key = TagKey.create(Registries.ITEM, rl);
                        var tag = tags.getTag(key);
                        if (tag != null) {
                            int tagPoints = TAG_DIFFICULTY.getOrDefault(tagId, defaultDifficulty);
                            for (Item item : (Iterable<Item>) tag) {
                                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                                if (id == null || item == Items.AIR || blacklist.matches(id)) continue;
                                if (!shouldIncludeFromTag(tagId, id)) continue;
                                int points = ITEM_DIFFICULTY.getOrDefault(id, tagPoints);
                                addEntryUnique(ENTRIES, id, points);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
            ensureVanillaCatalogBasics(ENTRIES, blacklist);
        } catch (Exception e) {
            ensureVanillaCatalogBasics(ENTRIES, blacklist);
        }

        // Always ensure key items are in catalog (food, totem, quartz, prismarine) even if tags miss them
        addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:bread", 2);
        addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:cookie", 2);
        addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:apple", 3);
        addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:golden_apple", 5);
        addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:enchanted_golden_apple", 15);
        addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:totem_of_undying", 25);
        addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:nether_quartz", 6);
        addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:prismarine_crystals", 10);
        addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:arrow", 1);
        addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:torch", 1);
        addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:fishing_rod", 5);
        addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:jukebox", 25);
        addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:flint_and_steel", 5);
        if (AirdropRuntimeToggles.isEndRecipeItemsEnabled()) {
            addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:dragon_breath", 60);
            addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:dragon_head", 80);
            addUniqueIfNotBlacklisted(ENTRIES, blacklist, "minecraft:dragon_egg", 100);
        }

        // TaCZ: (1) Registry first – every Item that is IAmmoBox/IGun (except base) as plain entry (real icons). (2) API/recipe IDs. (3) Known IDs to fill gaps. No creative cache – works without opening creative.
        if (ModList.get().isLoaded("tacz") && (AirdropConfig.INCLUDE_TACZ_AMMO.get() || AirdropConfig.INCLUDE_TACZ_GUNS.get())) {
            TaczAirdropIntegration.addAllTaczRegistryAmmoAndGuns(ENTRIES);
            TaczAirdropIntegration.addTaczViaApiToCatalog(ENTRIES);
            TaczAirdropIntegration.addKnownTaczIdsToCatalog(ENTRIES);
            lastBuiltTaczCacheSize = TaczAirdropIntegration.getCreativeCacheSize();
        }

        int taczInRegistry = 0;
        for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
            if (id != null && id.getNamespace().toLowerCase(java.util.Locale.ROOT).contains("tacz")) {
                taczInRegistry++;
            }
        }
        if (ModList.get().isLoaded("tacz") && (AirdropConfig.INCLUDE_TACZ_AMMO.get() || AirdropConfig.INCLUDE_TACZ_GUNS.get()) && taczInRegistry == 0)
            RadiotowersMod.LOGGER.warn("[RadioTowers] TaCZ mod is loaded but no items in registry with namespace 'tacz'. TaCZ content may use a different namespace or register later. You can add items manually in config: [airdrop] customCatalogEntries = [\"namespace:item_id=points\"] (get ids from JEI/REI).");

        // Add any entries registered by addons (e.g. other mods)
        for (Entry addon : ADDON_ENTRIES) {
            if (addon == null || !addon.isValid()) continue;
            if (blacklist.matchesEntry(addon)) continue;
            boolean found = false;
            for (Entry e : ENTRIES) if (e.getItemId().equals(addon.getItemId())) { found = true; break; }
            if (!found) ENTRIES.add(addon);
        }

        // TaCZ / registry fallbacks are added after tag filtering — strip blacklist here so
        // tacz:gun/* and similar globs actually hide those entries.
        ENTRIES.removeIf(blacklist::matchesEntry);

        // Custom catalog: config list + optional file. Format: item_id=points. Overrides existing cost.
        // TaCZ ids use the same aliases as blacklist. Blacklist still wins.
        List<String> customLines = new ArrayList<>();
        List<? extends String> configCustom = AirdropConfig.CUSTOM_CATALOG_ENTRIES.get();
        if (configCustom != null) for (Object o : configCustom) if (o instanceof String s) customLines.add(s);
        loadLinesFromFile(AirdropConfig.CUSTOM_CATALOG_FILE.get(), customLines);
        int maxPoints = getMaxTotalDifficulty();
        for (String line : customLines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            int eq = t.indexOf('=');
            if (eq <= 0 || eq == t.length() - 1) continue;
            try {
                String rawId = t.substring(0, eq).trim().replace("\"", "");
                if (rawId.isEmpty()) continue;
                if ("tacz:ammo".equalsIgnoreCase(rawId)) continue;
                int points = Integer.parseInt(t.substring(eq + 1).trim());
                points = Math.max(1, Math.min(maxPoints, points));
                BlacklistMatcher lineIds = new BlacklistMatcher();
                lineIds.add(rawId);
                List<Entry> hits = new ArrayList<>();
                for (Entry candidate : ENTRIES) {
                    if (!lineIds.matchesEntry(candidate)) continue;
                    if (blacklist.matchesEntry(candidate)) continue;
                    hits.add(candidate);
                }
                ENTRIES.removeIf(lineIds::matchesEntry);
                if (!hits.isEmpty()) {
                    for (Entry existing : hits) {
                        Entry e = existing.withDifficultyPoints(points);
                        if (e.isValid()) ENTRIES.add(e);
                    }
                    continue;
                }
                if (!rawId.contains(":")) continue;
                ResourceLocation id = ResourceLocation.parse(rawId);
                if (blacklist.matches(id)) continue;
                // Unknown ids follow the registry-item path; this does not register TaCZ content.
                Entry e = new Entry(id, points);
                if (e.isValid()) ENTRIES.add(e);
            } catch (Exception ignored) {}
        }

        // Never expose tag placeholders (e.g. tacz:ammo) or base TaCZ items (tacz:ammo_box, tacz:gun) – only content IDs like tacz:ammo/9mm, tacz:gun/m1911.
        // Remove any TaCZ entry that slipped in without a content path (e.g. from addons) or has excluded path (modern_k, workbenches, etc.).
        ENTRIES.removeIf(e -> {
            String path = e.getItemId().getPath();
            String pathLower = path.toLowerCase(java.util.Locale.ROOT);
            if (pathLower.contains("modern_k")) return true;
            if (e.getItemId().getNamespace().toLowerCase(java.util.Locale.ROOT).contains("tacz")) {
                if ("ammo".equals(path) || "ammo_box".equals(path) || "gun".equals(path)) return true;
                if (!path.contains("/")) return true;
                if (pathLower.contains("workbench") || pathLower.contains("gun_smith")
                    || pathLower.contains("attachment") || pathLower.contains("assembly") || pathLower.contains("creative")
                    || pathLower.contains("statue") || pathLower.contains("target")) return true;
            }
            return false;
        });
        ENTRIES.removeIf(blacklist::matchesEntry);
    }

    /** Broad Forge tags that pull in hundreds of mod items; default to vanilla-only unless config allows modded. */
    private static boolean isBroadForgeTag(String tagId) {
        return "forge:ingots".equals(tagId)
            || "forge:gems".equals(tagId)
            || "forge:gems/diamond".equals(tagId)
            || "forge:foods".equals(tagId);
    }

    /** Only include normal log items from minecraft:logs (no stripped, no wood, no planks). */
    private static boolean shouldIncludeFromTag(String tagId, ResourceLocation itemId) {
        if ("minecraft:logs".equals(tagId)) {
            String path = itemId.getPath();
            return path.endsWith("_log") && !path.contains("stripped");
        }
        if (!AirdropConfig.INCLUDE_NON_VANILLA_FORGE_TAG_ITEMS.get() && isBroadForgeTag(tagId)) {
            return "minecraft".equals(itemId.getNamespace());
        }
        return true;
    }

    private static void addEntryUnique(List<Entry> list, ResourceLocation id, int points) {
        if ("tacz".equals(id.getNamespace()) && "ammo".equals(id.getPath())) return; // tacz:ammo is a tag, not a real item
        for (Entry e : list)
            if (e.getItemId().equals(id)) return;
        Entry e = new Entry(id, points);
        if (!e.isValid()) return; // skip items that don't exist or are AIR (no icon, raw ID in GUI)
        list.add(e);
    }

    /**
     * Vanilla metals & gems (always present; avoids scanning {@code forge:ingots}, which includes all modded ingots).
     * Ingots: iron, gold, copper, netherite. Gems/materials: diamond, emerald, lapis, nether quartz, prismarine crystals, amethyst shard.
     */
    private static void ensureVanillaCatalogBasics(List<Entry> list, BlacklistMatcher blacklist) {
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:iron_ingot", 3);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:gold_ingot", 5);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:copper_ingot", 2);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:diamond", 10);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:emerald", 10);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:netherite_ingot", 50);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:nether_quartz", 6);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:prismarine_crystals", 10);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:amethyst_shard", 5);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:lapis_lazuli", 5);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:bread", 2);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:cookie", 2);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:apple", 3);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:cooked_beef", 3);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:golden_apple", 5);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:enchanted_golden_apple", 15);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:totem_of_undying", 25);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:arrow", 1);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:torch", 1);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:white_wool", 1);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:bow", 4);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:iron_sword", 5);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:shield", 4);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:fishing_rod", 5);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:jukebox", 25);
        addUniqueIfNotBlacklisted(list, blacklist, "minecraft:flint_and_steel", 5);
    }

    private static void addUniqueIfNotBlacklisted(List<Entry> list, BlacklistMatcher blacklist, String id, int points) {
        ResourceLocation rl = ResourceLocation.parse(id);
        if (blacklist.matches(rl)) return;
        addEntryUnique(list, rl, points);
    }

    /** Blacklist that supports exact ids, TaCZ aliases (gun/m1911 vs m1911), last-segment names, and globs. */
    static final class BlacklistMatcher {
        private final Set<ResourceLocation> exact = new HashSet<>();
        private final Set<String> contentKeys = new HashSet<>();
        private final Set<String> lastSegments = new HashSet<>();
        private final List<Pattern> patterns = new ArrayList<>();

        void add(String entry) {
            if (entry == null || entry.isEmpty()) return;
            String raw = entry.trim().replace("\"", "");
            if (raw.isEmpty() || raw.startsWith("#")) return;
            if (raw.contains("*")) {
                String regex = raw.replace(".", "\\.").replace("*", ".*");
                try { patterns.add(Pattern.compile("^" + regex + "$")); } catch (Exception ignored) {}
                return;
            }
            // Bare name: "m1911" or "9mm" — Forge would otherwise store this as minecraft:m1911
            if (!raw.contains(":")) {
                lastSegments.add(raw.toLowerCase(java.util.Locale.ROOT));
                return;
            }
            try {
                ResourceLocation rl = ResourceLocation.parse(raw);
                exact.add(rl);
                contentKeys.add(contentKey(rl));
                String path = rl.getPath();
                String last = lastSegment(path);
                lastSegments.add(last);
                String ns = rl.getNamespace();
                if (path.startsWith("gun/") || path.startsWith("ammo/")) {
                    exact.add(ResourceLocation.fromNamespaceAndPath(ns, last));
                    contentKeys.add(contentKey(ResourceLocation.fromNamespaceAndPath(ns, last)));
                } else if (!path.contains("/")) {
                    exact.add(ResourceLocation.fromNamespaceAndPath(ns, "gun/" + path));
                    exact.add(ResourceLocation.fromNamespaceAndPath(ns, "ammo/" + path));
                    contentKeys.add(contentKey(ResourceLocation.fromNamespaceAndPath(ns, "gun/" + path)));
                    contentKeys.add(contentKey(ResourceLocation.fromNamespaceAndPath(ns, "ammo/" + path)));
                }
            } catch (Exception ignored) {
                lastSegments.add(raw.toLowerCase(java.util.Locale.ROOT));
            }
        }

        boolean matches(ResourceLocation id) {
            if (id == null) return false;
            if (exact.contains(id)) return true;
            if (contentKeys.contains(contentKey(id))) return true;
            String last = lastSegment(id.getPath());
            if (lastSegments.contains(last)) return true;
            String full = id.toString();
            String ns = id.getNamespace();
            for (Pattern p : patterns) {
                if (p.matcher(full).matches()) return true;
                if (p.matcher(ns + ":" + last).matches()) return true;
                if (p.matcher(ns + ":gun/" + last).matches()) return true;
                if (p.matcher(ns + ":ammo/" + last).matches()) return true;
            }
            return false;
        }

        boolean matchesEntry(Entry e) {
            if (e == null) return false;
            if (matches(e.getItemId())) return true;
            try {
                ItemStack stack = e.createStack();
                if (stack != null && !stack.isEmpty() && stack.hasTag()) {
                    var tag = stack.getTag();
                    if (tag != null) {
                        if (tag.contains("GunId")) {
                            ResourceLocation gunId = ResourceLocation.tryParse(tag.getString("GunId"));
                            if (matches(gunId)) return true;
                        }
                        if (tag.contains("AmmoId")) {
                            ResourceLocation ammoId = ResourceLocation.tryParse(tag.getString("AmmoId"));
                            if (matches(ammoId)) return true;
                        }
                    }
                }
                String hover = stack != null && !stack.isEmpty() ? stack.getHoverName().getString() : "";
                String display = TaczAirdropIntegration.getDisplayNameForCatalog(e.getItemId(), hover);
                if (display == null) display = hover;
                if (display != null && !display.isEmpty()) {
                    String d = display.toLowerCase(java.util.Locale.ROOT);
                    for (String seg : lastSegments) {
                        if (seg.length() >= 3 && d.contains(seg)) return true;
                    }
                }
            } catch (Exception ignored) {}
            return false;
        }

        private static String lastSegment(String path) {
            int i = path.lastIndexOf('/');
            return (i >= 0 ? path.substring(i + 1) : path).toLowerCase(java.util.Locale.ROOT);
        }

        private static String contentKey(ResourceLocation id) {
            String ns = id.getNamespace().toLowerCase(java.util.Locale.ROOT);
            String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
            if (path.startsWith("gun/")) path = path.substring(4);
            else if (path.startsWith("ammo/")) path = path.substring(5);
            else if (path.startsWith("ammo_")) path = path.substring(5);
            return ns + ":" + path;
        }
    }

    /** True if this catalog row is the same item as {@code query}, including TaCZ id aliases. */
    public static boolean catalogIdMatches(ResourceLocation query, Entry e) {
        if (query == null || e == null) return false;
        if (query.equals(e.getItemId())) return true;
        BlacklistMatcher m = new BlacklistMatcher();
        m.add(query.toString());
        return m.matchesEntry(e);
    }

    /** Max total difficulty points allowed per request (from config). Treat old default 80 as 100. */
    public static int getMaxTotalDifficulty() {
        int v = AirdropConfig.MAX_TOTAL_DIFFICULTY.get();
        return (v == 80) ? 100 : v; // migration: old default was 80, new default is 100
    }

    /** Force rebuild on next getEntries() (e.g. after config reload). */
    public static void invalidate() {
        built = false;
    }

    /** Replace the entire catalog (e.g. from addon API). */
    public static void setCatalog(List<Entry> entries) {
        ENTRIES.clear();
        ENTRIES.addAll(entries);
        built = true;
    }

    /** Add a single entry (for addons). */
    public static void addEntry(ResourceLocation itemId, int difficultyPoints) {
        ENTRIES.add(new Entry(itemId, difficultyPoints));
    }

    private static void loadLinesFromFile(String filename, List<String> out) {
        if (filename == null || filename.isBlank()) return;
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("radiotowers");
            Path file = dir.resolve(filename.trim());
            if (Files.isRegularFile(file))
                for (String line : Files.readAllLines(file)) {
                    String t = line.trim();
                    if (!t.isEmpty() && !t.startsWith("#")) out.add(t);
                }
        } catch (Exception ignored) {}
    }
}
