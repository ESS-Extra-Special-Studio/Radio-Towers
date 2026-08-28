package net.mcreator.radiotowers.airdrop;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * TaCZ (Timeless and Classics Zero Guns) airdrop catalog:
 * - Ammo and guns only; no workbenches or attachments.
 * TaCZ uses: ammo = tacz:ammo/<caliber> (e.g. ammo/9mm, ammo/12g); guns = tacz:gun/<name> (e.g. gun/m1911, gun/glock_17).
 * We capture the exact ItemStacks from TaCZ's creative tabs when they are built; using those ensures correct icons (same as creative/inventory).
 */
public final class TaczAirdropIntegration {

    /** Content ID (tacz:ammo/xxx or tacz:gun/xxx) -> exact ItemStack from creative tab. Populated when BuildCreativeModeTabContentsEvent fires for a tacz tab. */
    private static final Map<ResourceLocation, ItemStack> CREATIVE_STACK_CACHE = new ConcurrentHashMap<>();

    /** Set by client (CallAirdropScreen) before building catalog so recipe fallback can use level.getRecipeManager() without reflection. */
    private static volatile Level clientLevelForCatalog = null;

    /**
     * Call from BuildCreativeModeTabContentsEvent (client). When the tab is from TaCZ, cache every ammo/gun stack by its content ID
     * so the airdrop catalog can use the same stacks and display correct icons. Open the TAC I Ammo / guns creative tab once to populate.
     */
    public static void captureCreativeTabStacks(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (!ModList.get().isLoaded("tacz")) return;
        try {
            ResourceLocation tabId = event.getTabKey().location();
            if (!"tacz".equals(tabId.getNamespace())) return;

            Class<?> iAmmoBoxClass = Class.forName("com.tacz.guns.api.item.IAmmoBox");
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            java.lang.reflect.Method getAmmoIdBox = iAmmoBoxClass.getMethod("getAmmoId", ItemStack.class);
            java.lang.reflect.Method getGunId = iGunClass.getMethod("getGunId", ItemStack.class);
            java.lang.reflect.Method getAmmoIdAmmo = null;
            try {
                Class<?> iAmmoClass = Class.forName("com.tacz.guns.api.item.IAmmo");
                getAmmoIdAmmo = iAmmoClass.getMethod("getAmmoId", ItemStack.class);
            } catch (Throwable ignored) {}

            for (Object entryObj : (Iterable<?>) event.getEntries()) {
                ItemStack stack = null;
                if (entryObj instanceof ItemStack s) stack = s;
                else if (entryObj instanceof Map.Entry<?, ?> ent && ent.getKey() instanceof ItemStack s) stack = s;
                if (stack == null || stack.isEmpty()) continue;
                Item item = stack.getItem();
                ResourceLocation contentId = null;
                boolean isAmmoItem = false;
                if (iAmmoBoxClass.isInstance(item)) {
                    try {
                        Object id = getAmmoIdBox.invoke(item, stack);
                        if (id instanceof ResourceLocation rl) contentId = rl;
                    } catch (Exception ignored) {}
                }
                if (contentId == null && getAmmoIdAmmo != null) {
                    try {
                        Class<?> iAmmoClass = Class.forName("com.tacz.guns.api.item.IAmmo");
                        if (iAmmoClass.isInstance(item)) {
                            Object id = getAmmoIdAmmo.invoke(item, stack);
                            if (id instanceof ResourceLocation rl) contentId = rl;
                        }
                    } catch (Exception ignored) {}
                }
                if (contentId != null) {
                    ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(item);
                    isAmmoItem = itemKey != null && "ammo".equals(itemKey.getPath());
                }
                if (contentId == null && iGunClass.isInstance(item)) {
                    try {
                        Object id = getGunId.invoke(item, stack);
                        if (id instanceof ResourceLocation rl) contentId = rl;
                    } catch (Exception ignored) {}
                }
                if (contentId != null && !shouldExclude(contentId.getPath())) {
                    if (contentId.getNamespace().isEmpty() || "minecraft".equals(contentId.getNamespace()))
                        contentId = ResourceLocation.fromNamespaceAndPath("tacz", contentId.getPath());
                    ItemStack copy = stack.copy();
                    String path = contentId.getPath();
                    ResourceLocation ammoCatalogKey = ResourceLocation.fromNamespaceAndPath("tacz", path.startsWith("ammo/") ? path : "ammo/" + path);
                    boolean isAmmo = iAmmoBoxClass.isInstance(item) || isAmmoItem;
                    if (isAmmo) {
                        ItemStack existing = CREATIVE_STACK_CACHE.get(ammoCatalogKey);
                        if (existing != null && !isAmmoItem) {
                            ResourceLocation existingId = ForgeRegistries.ITEMS.getKey(existing.getItem());
                            if (existingId != null && "ammo".equals(existingId.getPath()))
                                continue;
                        }
                    }
                    CREATIVE_STACK_CACHE.put(contentId, copy);
                    if (!path.startsWith("ammo/") && isAmmo)
                        CREATIVE_STACK_CACHE.put(ammoCatalogKey, copy);
                    if (!path.startsWith("gun/") && (iGunClass.isInstance(item)))
                        CREATIVE_STACK_CACHE.put(ResourceLocation.fromNamespaceAndPath("tacz", "gun/" + path), copy);
                }
            }
            if (!CREATIVE_STACK_CACHE.isEmpty()) {
                AirdropCatalog.invalidate();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Returns a copy of the cached creative stack for this content ID, or null if not cached. Tries catalog key and alternate (caliber/gun name only) so display always finds the stack. */
    public static ItemStack getCachedCreativeStack(ResourceLocation contentId) {
        ItemStack cached = CREATIVE_STACK_CACHE.get(contentId);
        if (cached != null) return cached.copy();
        String path = contentId.getPath();
        String ns = contentId.getNamespace();
        if (path.startsWith("ammo/") && path.length() > 5) {
            String caliber = path.substring(5);
            cached = CREATIVE_STACK_CACHE.get(ResourceLocation.fromNamespaceAndPath(ns, "ammo/" + caliber));
            if (cached == null && caliber.contains("x")) {
                String taczCaliber = caliber.replace("_", "");
                cached = CREATIVE_STACK_CACHE.get(ResourceLocation.fromNamespaceAndPath(ns, "ammo/" + taczCaliber));
                if (cached == null) cached = CREATIVE_STACK_CACHE.get(ResourceLocation.fromNamespaceAndPath(ns, taczCaliber));
            }
            if (cached == null) {
                String alt = ammoCaliberCacheAlternate(caliber);
                if (alt != null) cached = CREATIVE_STACK_CACHE.get(ResourceLocation.fromNamespaceAndPath(ns, "ammo/" + alt));
            }
        }
        if (cached == null && path.startsWith("gun/") && path.length() > 4) {
            String gunName = path.substring(4);
            cached = CREATIVE_STACK_CACHE.get(ResourceLocation.fromNamespaceAndPath(ns, gunName));
            if (cached == null && gunName.length() > 0) {
                String withUnderscore = gunName.replaceFirst("([a-zA-Z]+)([0-9]+)", "$1_$2");
                if (!withUnderscore.equals(gunName))
                    cached = CREATIVE_STACK_CACHE.get(ResourceLocation.fromNamespaceAndPath(ns, withUnderscore));
            }
            if (cached == null)
                cached = tryFindGunCacheByPath(gunName, ns);
        }
        return cached != null ? cached.copy() : null;
    }

    /** Known caliber alternates for cache lookup (TaCZ creative tab may use 338lm vs 338, 357m vs 357mag, etc.). Returns alternate to try, or null. */
    private static String ammoCaliberCacheAlternate(String caliber) {
        if (caliber == null) return null;
        return switch (caliber) {
            case "338" -> "338lm";
            case "338lm" -> "338";
            case "357mag" -> "357m";
            case "357m" -> "357mag";
            case "68x51fury" -> "68fury";
            case "68fury" -> "68x51fury";
            case "rpg_rocket" -> "rpg";
            case "rpg" -> "rpg_rocket";
            default -> null;
        };
    }

    /** Try to find a cached gun stack by matching path (e.g. hk416a5 matches gun/hk_416a5). Ignores underscores and case. */
    private static ItemStack tryFindGunCacheByPath(String gunName, String namespace) {
        String lower = gunName.toLowerCase(Locale.ROOT);
        String lowerNoUnderscore = lower.replace("_", "");
        for (Map.Entry<ResourceLocation, ItemStack> entry : CREATIVE_STACK_CACHE.entrySet()) {
            String keyPath = entry.getKey().getPath();
            String keyGun = (keyPath.startsWith("gun/") && keyPath.length() > 4) ? keyPath.substring(4) : keyPath;
            String keyLower = keyGun.toLowerCase(Locale.ROOT);
            String keyLowerNoUnderscore = keyLower.replace("_", "");
            if (keyLower.equals(lower) || keyLowerNoUnderscore.equals(lowerNoUnderscore))
                return entry.getValue();
        }
        return null;
    }

    /** TaCZ Recipe ID for ammo with "x" uses no underscore (e.g. 57x28 not 5_7x28). */
    private static ResourceLocation ammoIdTaczFormat(ResourceLocation ammoId) {
        String path = ammoId.getPath();
        String caliber = path.startsWith("ammo/") ? path.substring(5) : path;
        if (!caliber.contains("x")) return ammoId;
        String taczCaliber = caliber.replace("_", "");
        return ResourceLocation.fromNamespaceAndPath(ammoId.getNamespace(), "ammo/" + taczCaliber);
    }

    /** Build ammo display stack for crate/loot. Use ammo_box and set NBT (API + direct tag) so server and client get correct caliber. Never returns ammo_box without AmmoId tag. */
    public static ItemStack createAmmoDisplayStack(ResourceLocation ammoId) {
        if (ammoId == null) return ItemStack.EMPTY;
        String path = ammoId.getPath();
        ResourceLocation aid = path.startsWith("ammo/") ? ammoId : ResourceLocation.fromNamespaceAndPath(ammoId.getNamespace(), "ammo/" + path);
        ResourceLocation taczAid = ammoIdTaczFormat(aid);
        ResourceLocation[] idsToTry = path.contains("x") ? new ResourceLocation[]{taczAid, aid} : new ResourceLocation[]{aid};
        Item ammoBox = resolveAmmoBoxItem();
        if (ammoBox != null) {
            for (ResourceLocation tryId : idsToTry) {
                ItemStack stack = buildAmmoBoxStackWithNbt(ammoBox, tryId);
                if (!stack.isEmpty()) return stack;
            }
            ItemStack stack = buildAmmoBoxStackWithNbt(ammoBox, aid);
            if (!stack.isEmpty()) return stack;
        }
        Item ammoItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("tacz", "ammo"));
        if (ammoItem != null && ammoItem != Items.AIR) {
            for (ResourceLocation tryId : idsToTry) {
                ItemStack stack = tryBuildAmmoStackWithNbt(ammoItem, tryId);
                if (!stack.isEmpty()) return stack;
                stack = tryBuildAmmoStackWithRawNbt(ammoItem, tryId);
                if (!stack.isEmpty()) return stack;
            }
        }
        return createAmmoStackForCrateRawNbt(ammoId);
    }

    /**
     * Build for crate: ammo stack with NBT only (no reflection). Prefer tacz:ammo (AmmoItem) so client shows caliber icon; fallback to ammo_box.
     * TaCZ reads AmmoId/AmmoCount from tag for display.
     */
    public static ItemStack createAmmoStackForCrateRawNbt(ResourceLocation ammoId) {
        if (ammoId == null) return ItemStack.EMPTY;
        ResourceLocation aid = ammoId.getPath().startsWith("ammo/") ? ammoId : ResourceLocation.fromNamespaceAndPath(ammoId.getNamespace(), "ammo/" + ammoId.getPath());
        // Prefer tacz:ammo (AmmoItem) so client renders caliber-specific icon; ammo_box often shows as generic "iron ammo box" without correct NBT handling
        Item ammoItem = resolveAmmoBaseItem();
        if (ammoItem == null || ammoItem == Items.AIR) ammoItem = resolveAmmoBoxItem();
        if (ammoItem == null || ammoItem == Items.AIR) ammoItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("tacz", "ammo_box"));
        if (ammoItem == null || ammoItem == Items.AIR) return ItemStack.EMPTY;
        int rounds = getAmmoStackSize(aid);
        ItemStack stack = new ItemStack(ammoItem, 1);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("AmmoId", aid.toString());
        // tacz:ammo uses stack count for rounds; AmmoCount is for ammo_box only (wrong tag hides count in GUI).
        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(ammoItem);
        if (itemKey != null && "ammo".equals(itemKey.getPath()) && "tacz".equals(itemKey.getNamespace())) {
            stack.setCount(Math.min(rounds, Math.max(1, stack.getItem().getMaxStackSize(stack))));
            tag.remove("AmmoCount");
        } else
            tag.putInt("AmmoCount", rounds);
        return stack;
    }

    /** Build ammo_box stack with caliber NBT (API reflection + direct tag so server always has correct data for client). Never returns a stack without AmmoId tag. */
    private static ItemStack buildAmmoBoxStackWithNbt(Item ammoBox, ResourceLocation ammoId) {
        ItemStack stack = new ItemStack(ammoBox, 1);
        try {
            Class<?> iAmmoBoxClass = Class.forName("com.tacz.guns.api.item.IAmmoBox");
            if (iAmmoBoxClass.isInstance(ammoBox)) {
                java.lang.reflect.Method setAmmoId = iAmmoBoxClass.getMethod("setAmmoId", ItemStack.class, ResourceLocation.class);
                java.lang.reflect.Method setAmmoCount = iAmmoBoxClass.getMethod("setAmmoCount", ItemStack.class, int.class);
                setAmmoId.invoke(ammoBox, stack, ammoId);
                setAmmoCount.invoke(ammoBox, stack, getAmmoStackSize(ammoId));
            }
        } catch (Throwable ignored) {}
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("AmmoId", ammoId.toString());
        tag.putInt("AmmoCount", getAmmoStackSize(ammoId));
        if (!hasAmmoIdTag(stack)) return ItemStack.EMPTY;
        return stack;
    }

    public static boolean hasAmmoIdTag(ItemStack stack) {
        return stack.hasTag() && stack.getTag() != null && stack.getTag().contains("AmmoId");
    }

    /**
     * Sets how many rounds TaCZ shows for this stack ({@code AmmoCount} + {@code setAmmoCount} when present).
     * TaCZ often uses NBT for the overlay even when {@link ItemStack#getMaxStackSize()} is {@code > 1}.
     */
    public static void applyAmmoRoundCount(ItemStack stack, int rounds) {
        if (stack == null || stack.isEmpty() || rounds < 0) return;
        Object itemObj = stack.getItem();
        try {
            Class<?> iAmmoBox = Class.forName("com.tacz.guns.api.item.IAmmoBox");
            if (iAmmoBox.isInstance(itemObj)) {
                iAmmoBox.getMethod("setAmmoCount", ItemStack.class, int.class).invoke(itemObj, stack, rounds);
            }
        } catch (Throwable ignored) {}
        stack.getOrCreateTag().putInt("AmmoCount", rounds);
    }

    /** Try to call setAmmoId/setAmmoCount on the item's class (AmmoItem may not implement IAmmoBox but have same NBT methods). */
    private static ItemStack tryBuildAmmoStackWithNbt(Item item, ResourceLocation ammoId) {
        try {
            Class<?> c = item.getClass();
            java.lang.reflect.Method setAmmoId = null, setAmmoCount = null;
            while (c != null) {
                try {
                    if (setAmmoId == null) setAmmoId = c.getDeclaredMethod("setAmmoId", ItemStack.class, ResourceLocation.class);
                    if (setAmmoCount == null) setAmmoCount = c.getDeclaredMethod("setAmmoCount", ItemStack.class, int.class);
                } catch (NoSuchMethodException e) { c = c.getSuperclass(); continue; }
                if (setAmmoId != null && setAmmoCount != null) break;
                c = c.getSuperclass();
            }
            if (setAmmoId != null && setAmmoCount != null) {
                setAmmoId.setAccessible(true);
                setAmmoCount.setAccessible(true);
                ItemStack stack = new ItemStack(item, 1);
                setAmmoId.invoke(item, stack, ammoId);
                int rounds = getAmmoStackSize(ammoId);
                setAmmoCount.invoke(item, stack, rounds);
                stack.getOrCreateTag().putString("AmmoId", ammoId.toString());
                stack.getOrCreateTag().putInt("AmmoCount", rounds);
                return stack;
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    /** Try writing ammo NBT directly (TaCZ may read AmmoId from tag for model). */
    private static ItemStack tryBuildAmmoStackWithRawNbt(Item ammoItem, ResourceLocation ammoId) {
        int rounds = getAmmoStackSize(ammoId);
        ItemStack stack = new ItemStack(ammoItem, 1);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("AmmoId", ammoId.toString());
        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(ammoItem);
        if (itemKey != null && "ammo".equals(itemKey.getPath()) && "tacz".equals(itemKey.getNamespace())) {
            stack.setCount(Math.min(rounds, Math.max(1, stack.getItem().getMaxStackSize(stack))));
            tag.remove("AmmoCount");
        } else
            tag.putInt("AmmoCount", rounds);
        return stack;
    }

    /** True if the stack is the TaCZ ammo_box item (generic iron box icon). */
    public static boolean isAmmoBoxStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && "tacz".equals(id.getNamespace()) && "ammo_box".equals(id.getPath());
    }

    /**
     * Forge stack-aware max size (TaCZ {@code AmmoItem#getMaxStackSize(ItemStack)} returns per-caliber cap from data).
     * Vanilla {@link Item#getMaxStackSize()} without stack is always 1 for TaCZ ammo items.
     */
    public static int getForgeMaxStackSize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 64;
        return Math.min(64, stack.getItem().getMaxStackSize(stack));
    }

    /** Known gun path alternates so display matches TaCZ Recipe ID. (AKM and AK47 are the same gun: ID is ak47, name is AKM.) */
    private static final Map<String, String> GUN_DISPLAY_ALT_PATHS = Map.of();

    /** Build gun display stack; tries alternate path (e.g. hk_416a5) so icon matches TaCZ model. */
    public static ItemStack createGunDisplayStackWithAlternates(ResourceLocation gunId) {
        if (gunId == null || !gunId.getPath().startsWith("gun/")) return ItemStack.EMPTY;
        String path = gunId.getPath();
        String gunName = path.substring(4);
        String ns = gunId.getNamespace();
        String altPath = GUN_DISPLAY_ALT_PATHS.get(gunName.toLowerCase(Locale.ROOT));
        if (altPath != null) {
            ResourceLocation altId = ResourceLocation.fromNamespaceAndPath(ns, "gun/" + altPath);
            ItemStack s = buildGunStack(altId);
            if (!s.isEmpty()) return s;
        }
        return buildGunStack(gunId);
    }

    private static ItemStack buildGunStack(ResourceLocation gunId) {
        try {
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            java.lang.reflect.Method setGunId = iGunClass.getMethod("setGunId", ItemStack.class, ResourceLocation.class);
            Item gunItem = resolveGunBaseItem();
            if (gunItem == null || !iGunClass.isInstance(gunItem)) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(gunItem, 1);
            setGunId.invoke(gunItem, stack, gunId);
            // Mirror ammo handling: keep explicit content id tag for robust crate/network sync.
            stack.getOrCreateTag().putString("GunId", gunId.toString());
            return stack;
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    /** Current size of the creative stack cache (for catalog invalidation when cache grows). */
    public static int getCreativeCacheSize() {
        return CREATIVE_STACK_CACHE.size();
    }

    /** Call from client when opening airdrop GUI so TaCZ recipe fallback can discover guns/ammo from recipes. */
    public static void setClientLevelForCatalog(Level level) {
        clientLevelForCatalog = level;
    }

    /** Path substrings that identify blocks/UI we never add (workbenches, attachments, missing texture). */
    private static final Set<String> EXCLUDE_PATH = Set.of(
        "workbench", "gun_smith", "ammo_workbench", "attachment_workbench", "assembly", "table",
        "attach", "bench", "statue", "target",
        "modern_k", "creative", "hk416a5"
    );

    /** TaCZ ammo path segments (from JAR / TAC I Ammo window); fuzzy "path contains" used for matching. 12g = 12 gauge (incl. slugs). */
    private static final Set<String> TACZ_AMMO_PATHS = Set.of(
        "9mm", "12g", "12ga", "308", "30_06", "338", "338lm", "357mag", "357m", "40mm", "45acp", "45_70", "45_10",
        "46x30", "4_6x30", "50ae", "50bmg", "545x39", "5_45x39", "556x45", "5_56x45", "57x28", "5_7x28",
        "58x42", "5_8x42", "68x51", "68x51fury", "68fury", "6_8", "fury", "762x25", "762x39", "7_62x39", "762x54", "7_62x54",
        "rpg", "rpg_rocket"
    );

    /** Canonical TAC I Ammo calibers – TaCZ Recipe ID path (e.g. tacz:ammo/338, tacz:ammo/357mag). One entry per caliber. */
    private static final List<String> TAC_I_AMMO_CALIBERS = List.of(
        "68x51fury", "9mm", "338", "308", "357mag", "4_6x30", "5_7x28", "45acp", "50bmg",
        "40mm", "12g", "30_06", "50ae", "5_45x39", "45_70", "rpg_rocket", "762x25", "5_56x45", "5_8x42", "7_62x39", "7_62x54"
    );

    /** Base TaCZ registry items – never add these as plain entries (they show as "Iron Ammo Box" / generic gun); only add content IDs like tacz:ammo/9mm, tacz:gun/m1911. */
    private static boolean isBaseTaczItem(String path) {
        return "ammo_box".equals(path) || "gun".equals(path);
    }

    /** TaCZ gun path segments (from JAR); also use fuzzy matching. */
    private static final Set<String> TACZ_GUN_PATHS = Set.of(
        "aa12", "ai_awp", "ak47", "aug", "b93r", "cz75", "db_long", "db_short", "deagle", "deagle_golden",
        "fn_evolys", "fn_fal", "g36k", "glock_17", "hk416d", "hk_g3", "hk_mp5a5", "m1014", "m107",
        "m16a1", "m16a4", "m1911", "m249", "m320", "m4a1", "m700", "m870", "m95", "minigun", "mk14",
        "p320", "p90", "qbz_191", "qbz_95", "rpg7", "rpk", "scar_h", "scar_l", "sks_tactical", "spas_12",
        "spr15hb", "springfield1873", "timeless50", "type_81", "ump45", "uzi", "vector45"
    );

    /** True if the item's namespace is from TaCZ (mod id "tacz" or common variant names like tac_zero). */
    private static boolean isTaczNamespace(String namespace) {
        if (namespace == null) return false;
        String lower = namespace.toLowerCase(Locale.ROOT);
        return lower.contains("tacz") || lower.equals("tac_zero") || lower.equals("taczero");
    }

    /** Airdrop / crate: content IDs may use tacz, tac_zero, etc. — must not require namespace {@code tacz} exactly. */
    public static boolean isTaczContentId(ResourceLocation id) {
        return id != null && isTaczNamespace(id.getNamespace());
    }

    /** Fuzzy: path contains these (ammo-like) and we don't exclude → treat as ammo. Covers every TAC I Ammo caliber. */
    private static final Set<String> BULLET_LIKE_SUBSTRINGS = Set.of(
        "ammo", "bullet", "round", "magazine", "shell", "cartridge",
        "9mm", "12g", "12ga", "12_gauge", "308", "30_06", "30-06", "338", "338lm", "338_lm",
        "357", "357mag", "357m", "40mm", "45acp", "45_acp", "45_70", "45_10", "46x30", "4_6x30", "4_6",
        "50ae", "50_ae", "50bmg", "50_bmg", "545", "545x39", "5_45x39", "556", "556x45", "5_56x45", "5_56",
        "57x28", "5_7x28", "58x42", "5_8x42", "68x51", "68x51fury", "68fury", "6_8", "fury", "762", "762x39", "7_62x39",
        "762x54", "7_62x54", "7_62", "rpg"
    );

    /** Fuzzy: path contains these (gun-like) and we don't exclude → treat as gun. */
    private static final Set<String> GUN_LIKE_SUBSTRINGS = Set.of(
        "gun", "rifle", "pistol", "shotgun", "smg", "sniper"
    );

    private static boolean shouldExclude(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        for (String x : EXCLUDE_PATH)
            if (lower.contains(x)) return true;
        return false;
    }

    /** Last segment of path (after final '/') so "content/9mm" -> "9mm". */
    private static String lastPathSegment(String path) {
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(i + 1) : path;
    }

    /** Human-readable name for ammo ID (e.g. ammo/5_56x45 -> "5.56x45 Ammo") so GUI never shows "Iron Ammo Box". */
    private static String ammoDisplayName(ResourceLocation id) {
        String segment = lastPathSegment(id.getPath());
        String display = segment.replace('_', '.');
        return display + " Ammo";
    }

    /** Human-readable name for gun ID (e.g. gun/glock_17 -> "GLOCK 17", gun/m1911 -> "M1911") so GUI shows gun name. */
    private static String gunDisplayName(ResourceLocation id) {
        String segment = lastPathSegment(id.getPath());
        String withSpaces = segment.replace('_', ' ');
        return withSpaces.toUpperCase(Locale.ROOT);
    }

    /** For airdrop GUI: show this name for TaCZ ammo/gun so list shows "9mm Ammo", "UZI" etc. without changing the stack (keeps TaCZ texture). Returns null to use stack name. */
    public static String getDisplayNameForCatalog(ResourceLocation itemId, String stackHoverName) {
        if (itemId == null || !isTaczNamespace(itemId.getNamespace())) return null;
        String path = itemId.getPath();
        if (path.startsWith("ammo/")) {
            String segment = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            return segment.replace('_', '.') + " Ammo";
        }
        if (path.startsWith("ammo_") || TAC_I_AMMO_CALIBERS.contains(path)) {
            String segment = path.startsWith("ammo_") ? path.substring(5) : path;
            return segment.replace('_', '.') + " Ammo";
        }
        if (path.startsWith("gun/")) {
            String segment = path.substring(path.lastIndexOf('/') + 1);
            return segment.replace('_', ' ').toUpperCase(Locale.ROOT);
        }
        return null;
    }

    /** Whitelist match: exact path, last segment, or path "ammo/<caliber>" (e.g. ammo/9mm, ammo/5_56x45), or path contains segment. */
    private static boolean isAllowedAmmoPath(String path) {
        // TaCZ recipe/item IDs: tacz:ammo/9mm, tacz:ammo/5_56x45, etc. – include when caliber matches whitelist or pattern.
        if (path.startsWith("ammo/")) {
            String caliber = path.substring(5).toLowerCase(Locale.ROOT);
            if (TACZ_AMMO_PATHS.contains(caliber)) return true;
            for (String p : TACZ_AMMO_PATHS) if (caliber.contains(p)) return true;
            if (caliber.matches(".*\\d[_.]?\\d*x\\d+.*")) return true; // e.g. 5_56x45, 7_62x39
            return false;
        }
        if (TACZ_AMMO_PATHS.contains(path) || TACZ_AMMO_PATHS.contains(lastPathSegment(path))) return true;
        for (String p : TACZ_AMMO_PATHS)
            if (path.endsWith("_" + p) || path.endsWith("/" + p) || path.contains(p)) return true;
        return false;
    }

    /** Whitelist match: exact path, last segment, or path "gun/<name>" (e.g. gun/m1911, gun/glock_17), or path contains segment. */
    private static boolean isAllowedGunPath(String path) {
        // TaCZ recipe/item IDs: tacz:gun/m1911, tacz:gun/glock_17, etc. – include when name matches whitelist.
        if (path.startsWith("gun/")) {
            String name = path.substring(4).toLowerCase(Locale.ROOT);
            if (TACZ_GUN_PATHS.contains(name)) return true;
            for (String p : TACZ_GUN_PATHS) if (name.contains(p)) return true;
            return false;
        }
        if (TACZ_GUN_PATHS.contains(path) || TACZ_GUN_PATHS.contains(lastPathSegment(path))) return true;
        for (String p : TACZ_GUN_PATHS)
            if (path.endsWith("_" + p) || path.endsWith("/" + p) || path.contains(p)) return true;
        return false;
    }

    /** Fuzzy: path looks like ammo (contains ammo/bullet/round/etc.) and is not a workbench/gun item. */
    private static boolean isBulletLikePath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("gun") || lower.contains("workstation") || lower.contains("table")) return false;
        if (lower.contains("ammo_box")) return true; // TaCZ registers ammo_box in main registry
        for (String s : BULLET_LIKE_SUBSTRINGS)
            if (lower.contains(s)) return true;
        // Caliber-style pattern: digits, optional underscore, x, digits (e.g. 5_56x45, 7_62x39, 4_6x30)
        if (path.matches(".*\\d[_.]?\\d*x\\d+.*")) return true;
        return false;
    }

    /** Fuzzy: path looks like a gun (contains gun/rifle/pistol/etc.) and is not ammo/table. */
    private static boolean isGunLikePath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("ammo") || lower.contains("bullet") || lower.contains("box") || lower.contains("table")) return false;
        for (String s : GUN_LIKE_SUBSTRINGS)
            if (lower.contains(s)) return true;
        return false;
    }

    /** Points per stack of bullet ammo (one catalog unit = one full ammo box). */
    private static final int BULLET_STACK_POINTS = 5;
    /** Default rounds per ammo box (bullets). RPG/rockets use 3. One catalog unit = one box with this many rounds. */
    private static final int BULLET_DEFAULT_STACK = 64;

    /**
     * Rounds per full in-game stack for tacz:ammo/&lt;caliber&gt; (matches TaCZ stack sizes in creative).
     * Used for catalog boxes and airdrop crate {@code AmmoCount}. Order: specific calibers before broad substrings.
     */
    private static int getAmmoStackSize(ResourceLocation ammoId) {
        if (ammoId == null) return BULLET_DEFAULT_STACK;
        String path = ammoId.getPath();
        String caliber = path.startsWith("ammo/") ? path.substring(5) : path;
        String lower = caliber.toLowerCase(Locale.ROOT);
        // 6 — 40mm, RPG
        if (lower.contains("rpg") || lower.contains("rpg_rocket") || lower.contains("rocket")) return 6;
        if (lower.contains("40mm")) return 6;
        // 30 — .338 LM, .50 AE
        if (lower.contains("338")) return 30;
        if (lower.contains("50ae") || lower.contains("50_ae")) return 30;
        // 36 — 12g, .30-06
        if (lower.contains("12g") || lower.contains("12ga") || lower.contains("12_ga")) return 36;
        if (lower.contains("30_06") || lower.contains("3006") || lower.contains("30-06")) return 36;
        // 48 — .308, .357, .50 BMG, .300 BLK-style, 45-70
        if (lower.contains("50bmg") || lower.contains("50_bmg")) return 48;
        if (lower.contains("45_70") || lower.contains("4570") || lower.contains("45-70") || lower.contains("45_10")) return 48;
        if (lower.contains("357")) return 48;
        if (lower.contains("308")) return 48;
        if (lower.contains("300blk") || lower.contains("300_blk") || lower.contains("blackout")
            || lower.equals("300") || lower.startsWith("300_")) return 48;
        // 60 — most rifle/pistol (incl. .45 ACP, 9mm, 5.56, etc.)
        if (lower.contains("45acp") || lower.contains("45_acp")) return 60;
        if (lower.contains("9mm")) return 60;
        if (lower.contains("5_56") || lower.contains("556")) return 60;
        if (lower.contains("5_45") || lower.contains("545")) return 60;
        if (lower.contains("5_8") || lower.contains("58x") || lower.contains("5842")) return 60;
        if (lower.contains("5_7") || lower.contains("57x") || lower.contains("5728")) return 60;
        if (lower.contains("4_6") || lower.contains("46x") || lower.contains("4630")) return 60;
        if (lower.contains("7_62x54") || lower.contains("762x54")) return 60;
        if (lower.contains("7_62x51") || lower.contains("762x51")) return 60;
        if (lower.contains("7_62x39") || lower.contains("762x39")) return 60;
        if (lower.contains("7_62x35") || lower.contains("762x35")) return 60;
        if (lower.contains("7_62x25") || lower.contains("762x25")) return 60;
        if (lower.contains("6_8") || lower.contains("68x51") || lower.contains("68fury") || lower.contains("fury")) return 60;
        if (lower.matches(".*\\d.*x\\d+.*")) return 60;
        return BULLET_DEFAULT_STACK;
    }

    /** Public hook for airdrop fill: rounds per full stack for this tacz ammo content id (for {@code AmmoCount}). */
    public static int getRoundsPerFullStackForAmmoContentId(ResourceLocation ammoId) {
        return getAmmoStackSize(ammoId);
    }

    /** Points per ammo type. RPG/rocket ammo = 20 per stack; others use BULLET_STACK_POINTS. */
    private static int ammoPoints(ResourceLocation ammoId) {
        if (ammoId == null) return BULLET_STACK_POINTS;
        String path = ammoId.getPath();
        String caliber = path.startsWith("ammo/") ? path.substring(5) : path;
        String lower = caliber.toLowerCase(Locale.ROOT);
        if (lower.contains("rpg") || lower.contains("rpg_rocket") || lower.contains("rocket")) return 20;
        return BULLET_STACK_POINTS;
    }

    /** Gun tier costs (proxy for damage: sniper/rpg highest, then rifle/shotgun, then pistol/smg). */
    private static final int GUN_PISTOL_SMG_POINTS = 12;
    private static final int GUN_RIFLE_SHOTGUN_POINTS = 18;
    private static final int GUN_SNIPER_RPG_POINTS = 25;

    /** Explicit cost overrides for specific guns (display name / path). RPG launcher = 30. */
    private static final java.util.Map<String, Integer> GUN_COST_OVERRIDES = java.util.Map.of(
        "deagle_golden", 30,   // Golden Deagle 357
        "timeless50", 40,     // Timeless .50 Z-...
        "rpg7", 30            // Rocket launcher – more expensive
    );

    private static int gunPoints(String path) {
        if (path == null) return GUN_PISTOL_SMG_POINTS;
        String lower = path.toLowerCase(Locale.ROOT);
        Integer override = GUN_COST_OVERRIDES.get(lower);
        if (override == null && lower.startsWith("gun/")) override = GUN_COST_OVERRIDES.get(lower.substring(4));
        if (override != null) return override;
        if (lower.contains("rpg") || lower.contains("m107") || lower.contains("m95") || lower.contains("ai_awp") || lower.contains("mk14") || lower.contains("m700") || lower.contains("spr15hb") || lower.contains("springfield"))
            return GUN_SNIPER_RPG_POINTS;
        if (lower.contains("scar") || lower.contains("fn_fal") || lower.contains("hk_g3") || lower.contains("m16") || lower.contains("m4a1") || lower.contains("ak47") || lower.contains("aug") || lower.contains("g36k") || lower.contains("type_81") || lower.contains("qbz") || lower.contains("sks_tactical") || lower.contains("rpk")
            || lower.contains("aa12") || lower.contains("m870") || lower.contains("m1014") || lower.contains("spas_12") || lower.contains("db_long") || lower.contains("db_short") || lower.contains("minigun") || lower.contains("m249") || lower.contains("fn_evolys"))
            return GUN_RIFLE_SHOTGUN_POINTS;
        return GUN_PISTOL_SMG_POINTS;
    }

    /** Add TaCZ ammo: whitelist match OR fuzzy path. Skip base ammo_box and ammo/… (content IDs need NBT – added via API/addKnownTaczIdsToCatalog to avoid "Iron Ammo Box"). */
    public static void addAmmoToCatalog(List<AirdropCatalog.Entry> entries) {
        if (!AirdropConfig.INCLUDE_TACZ_AMMO.get()) return;
        Item ammoBoxItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:ammo_box"));
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null || item == Items.AIR) continue;
            if (!isTaczNamespace(id.getNamespace())) continue;
            String path = id.getPath();
            if ("ammo".equals(path) || isBaseTaczItem(path)) continue; // tag or base item
            if (path.startsWith("ammo/")) continue; // content ID – same Item as ammo_box, would show "Iron Ammo Box"; use API/known IDs
            if (item == ammoBoxItem) continue; // any registry alias of ammo_box
            if (shouldExclude(path)) continue;
            if (!isAllowedAmmoPath(path) && !isBulletLikePath(path)) continue;
            if (alreadyInList(entries, id)) continue;
            entries.add(new AirdropCatalog.Entry(id, ammoPoints(id), 1));
        }
    }

    /** Add TaCZ guns: whitelist match OR fuzzy path. Skip base gun and gun/… (content IDs need NBT – added via API/addKnownTaczIdsToCatalog). */
    public static void addGunsToCatalog(List<AirdropCatalog.Entry> entries) {
        if (!AirdropConfig.INCLUDE_TACZ_GUNS.get()) return;
        Item gunItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:gun"));
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null || item == Items.AIR) continue;
            if (!isTaczNamespace(id.getNamespace())) continue;
            String path = id.getPath();
            if ("ammo".equals(path) || isBaseTaczItem(path)) continue;
            if (path.startsWith("gun/")) continue; // content ID – same Item as gun, use API/known IDs
            if (item == gunItem) continue;
            if (shouldExclude(path)) continue;
            if (!isAllowedGunPath(path) && !isGunLikePath(path)) continue;
            if (alreadyInList(entries, id)) continue;
            entries.add(new AirdropCatalog.Entry(id, gunPoints(path)));
        }
    }

    private static boolean alreadyInList(List<AirdropCatalog.Entry> entries, ResourceLocation id) {
        for (AirdropCatalog.Entry e : entries)
            if (e.getItemId().equals(id)) return true;
        return false;
    }

    /** Fallback: add any tacz item that looks like ammo or gun. Skip base ammo_box/gun and ammo/… gun/… (content IDs need NBT). */
    public static void addTaczFallbackToCatalog(List<AirdropCatalog.Entry> entries) {
        if (!AirdropConfig.INCLUDE_TACZ_AMMO.get() && !AirdropConfig.INCLUDE_TACZ_GUNS.get()) return;
        Item ammoBoxItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:ammo_box"));
        Item gunItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:gun"));
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null || item == Items.AIR) continue;
            if (!isTaczNamespace(id.getNamespace())) continue;
            String path = id.getPath();
            if ("ammo".equals(path) || isBaseTaczItem(path)) continue;
            if (path.startsWith("ammo/") || path.startsWith("gun/")) continue;
            if (item == ammoBoxItem || item == gunItem) continue;
            if (shouldExclude(path)) continue;
            boolean ammo = isAllowedAmmoPath(path) || isBulletLikePath(path);
            boolean gun = isAllowedGunPath(path) || isGunLikePath(path);
            if (ammo && !AirdropConfig.INCLUDE_TACZ_AMMO.get()) continue;
            if (gun && !AirdropConfig.INCLUDE_TACZ_GUNS.get()) continue;
            if (!ammo && !gun) continue;
            if (alreadyInList(entries, id)) continue;
            if (!new AirdropCatalog.Entry(id, 10).isValid()) continue;
            int points = ammo ? ammoPoints(id) : gunPoints(path);
            entries.add(new AirdropCatalog.Entry(id, points, ammo ? 1 : 1));
        }
    }

    /**
     * Discovery: any item with namespace "tacz" and path matching ammo/gun. Skip base ammo_box/gun and ammo/… gun/… (content IDs need NBT).
     */
    public static void addAnyMatchingTaczItems(List<AirdropCatalog.Entry> entries) {
        if (!AirdropConfig.INCLUDE_TACZ_AMMO.get() && !AirdropConfig.INCLUDE_TACZ_GUNS.get()) return;
        Item ammoBoxItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:ammo_box"));
        Item gunItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:gun"));
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null || item == Items.AIR) continue;
            if (!isTaczNamespace(id.getNamespace())) continue;
            String path = id.getPath();
            if ("ammo".equals(path) || isBaseTaczItem(path)) continue;
            if (path.startsWith("ammo/") || path.startsWith("gun/")) continue;
            if (item == ammoBoxItem || item == gunItem) continue;
            if (shouldExclude(path)) continue;
            boolean ammo = isAllowedAmmoPath(path) || isBulletLikePath(path);
            boolean gun = isAllowedGunPath(path) || isGunLikePath(path);
            if (ammo && !AirdropConfig.INCLUDE_TACZ_AMMO.get()) continue;
            if (gun && !AirdropConfig.INCLUDE_TACZ_GUNS.get()) continue;
            if (!ammo && !gun) continue;
            if (alreadyInList(entries, id)) continue;
            entries.add(ammo
                ? new AirdropCatalog.Entry(id, ammoPoints(id), 1)
                : new AirdropCatalog.Entry(id, gunPoints(path)));
        }
    }

    /**
     * When tacz mod is loaded but no TaCZ entries were found, add items from namespace "tacz" that aren't base/content IDs.
     * Skips ammo_box, gun, and ammo/… gun/… so we don't get "Iron Ammo Box" duplicates.
     */
    public static void addAllTaczItemsIfEmpty(List<AirdropCatalog.Entry> entries) {
        if (!AirdropConfig.INCLUDE_TACZ_AMMO.get() && !AirdropConfig.INCLUDE_TACZ_GUNS.get()) return;
        if (!ModList.get().isLoaded("tacz")) return;
        if (entries.stream().anyMatch(e -> isTaczNamespace(e.getItemId().getNamespace()))) return;
        Item ammoBoxItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:ammo_box"));
        Item gunItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:gun"));
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null || item == Items.AIR) continue;
            if (!isTaczNamespace(id.getNamespace())) continue;
            String path = id.getPath();
            if ("ammo".equals(path) || isBaseTaczItem(path)) continue;
            if (path.startsWith("ammo/") || path.startsWith("gun/")) continue;
            if (item == ammoBoxItem || item == gunItem) continue;
            if (shouldExclude(path)) continue;
            boolean ammo = isAllowedAmmoPath(path) || isBulletLikePath(path);
            boolean gun = isAllowedGunPath(path) || isGunLikePath(path);
            if (ammo && !AirdropConfig.INCLUDE_TACZ_AMMO.get()) continue;
            if (gun && !AirdropConfig.INCLUDE_TACZ_GUNS.get()) continue;
            if (!ammo && !gun) continue;
            if (alreadyInList(entries, id)) continue;
            int points = ammo ? ammoPoints(id) : (gun ? gunPoints(path) : 10);
            entries.add(new AirdropCatalog.Entry(id, points, ammo ? 1 : 1));
        }
    }

    /**
     * TaCZ uses one "ammo_box" item and one "gun" item; caliber/gun type is in NBT.
     * Use TimelessAPI/CommonAssetsManager via reflection to get all ammo and gun IDs, then add entries
     * that create stacks with setAmmoId/setGunId. Call this first when TaCZ is loaded.
     */
    public static void addTaczViaApiToCatalog(List<AirdropCatalog.Entry> entries) {
        if (!AirdropConfig.INCLUDE_TACZ_AMMO.get() && !AirdropConfig.INCLUDE_TACZ_GUNS.get()) return;
        if (!ModList.get().isLoaded("tacz")) return;
        boolean wantAmmo = AirdropConfig.INCLUDE_TACZ_AMMO.get();
        boolean wantGuns = AirdropConfig.INCLUDE_TACZ_GUNS.get();
        try {
            Set<ResourceLocation> ammoIds;
            Set<ResourceLocation> gunIds;
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ammoIds = getIdsViaTimelessApi("getAllClientAmmoIndex");
                gunIds = getIdsViaTimelessApi("getAllClientGunIndex");
            } else {
                ammoIds = new java.util.HashSet<>();
                gunIds = new java.util.HashSet<>();
            }
            if (ammoIds.isEmpty()) ammoIds = getIdsViaTimelessApi("getAllCommonAmmoIndex");
            if (gunIds.isEmpty()) gunIds = getIdsViaTimelessApi("getAllCommonGunIndex");

            // Fallback: CommonAssetsManager provider (server/singleplayer path). Returns Set<Entry<ResourceLocation, ...>>.
            if (ammoIds.isEmpty() || gunIds.isEmpty()) {
                Object commonManager = Class.forName("com.tacz.guns.resource.CommonAssetsManager")
                    .getMethod("get").invoke(null);
                if (commonManager != null) {
                    if (ammoIds.isEmpty()) ammoIds = getKeysFromEntrySet(commonManager, "getAllAmmos");
                    if (gunIds.isEmpty()) gunIds = getKeysFromEntrySet(commonManager, "getAllGuns");
                }
            }

            // Fallback: discover from GunSmithTable recipes (client has synced recipes; result stacks carry gun/ammo NBT).
            if ((ammoIds.isEmpty() || gunIds.isEmpty()) && FMLEnvironment.dist == Dist.CLIENT) {
                if (!(ammoIds instanceof java.util.HashSet)) ammoIds = new java.util.HashSet<>(ammoIds);
                if (!(gunIds instanceof java.util.HashSet)) gunIds = new java.util.HashSet<>(gunIds);
                getIdsFromGunSmithRecipes(ammoIds, gunIds);
            }

            // If still no ammo IDs (e.g. ammo not in gun_smith recipes), add TAC I Ammo calibers so catalog shows "9mm Bullet" etc., not generic "Iron Ammo Box".
            if (ammoIds.isEmpty()) {
                ammoIds = new java.util.HashSet<>();
                for (String caliber : TAC_I_AMMO_CALIBERS)
                    ammoIds.add(ResourceLocation.parse("tacz:ammo/" + caliber));
            }

            // Catalog stacks use IAmmoBox API (setAmmoId/setAmmoCount); tacz:ammo item is IAmmo only — use ammo_box here.
            Item ammoBoxItem = resolveAmmoBoxItem();
            Item gunItem = resolveGunBaseItem();

            Class<?> iAmmoBoxClass = Class.forName("com.tacz.guns.api.item.IAmmoBox");
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");

            for (ResourceLocation ammoId : ammoIds) {
                if (!wantAmmo) continue;
                if (ammoId == null || "ammo".equals(ammoId.getPath())) continue;
                if (shouldExclude(ammoId.getPath())) continue;
                if (!isAllowedAmmoPath(ammoId.getPath()) && !isBulletLikePath(ammoId.getPath())) continue;
                if (alreadyInList(entries, ammoId)) continue;
                if (ammoBoxItem == null || !iAmmoBoxClass.isInstance(ammoBoxItem)) continue;
                final Item ab = ammoBoxItem;
                final ResourceLocation aid = ammoId;
                final int roundsPerBox = getAmmoStackSize(aid);
                Supplier<ItemStack> creator = () -> {
                    try {
                        ItemStack stack = new ItemStack(ab, 1);
                        iAmmoBoxClass.getMethod("setAmmoId", ItemStack.class, ResourceLocation.class).invoke(ab, stack, aid);
                        iAmmoBoxClass.getMethod("setAmmoCount", ItemStack.class, int.class).invoke(ab, stack, roundsPerBox);
                        return stack;
                    } catch (Exception e) { return ItemStack.EMPTY; }
                };
                AirdropCatalog.Entry e = new AirdropCatalog.Entry(ammoId, ammoPoints(aid), 1, creator);
                if (e.isValid()) entries.add(e);
            }

            for (ResourceLocation gunId : gunIds) {
                if (!wantGuns) continue;
                if (gunId == null) continue;
                if (shouldExclude(gunId.getPath())) continue;
                if (!isAllowedGunPath(gunId.getPath()) && !isGunLikePath(gunId.getPath())) continue;
                if (alreadyInList(entries, gunId)) continue;
                if (gunItem == null || !iGunClass.isInstance(gunItem)) continue;
                final Item gi = gunItem;
                final ResourceLocation gid = gunId;
                Supplier<ItemStack> creator = () -> {
                    try {
                        ItemStack stack = new ItemStack(gi, 1);
                        iGunClass.getMethod("setGunId", ItemStack.class, ResourceLocation.class).invoke(gi, stack, gid);
                        return stack;
                    } catch (Exception e) { return ItemStack.EMPTY; }
                };
                int points = gunPoints(gunId.getPath());
                AirdropCatalog.Entry e = new AirdropCatalog.Entry(gunId, points, 1, creator);
                if (e.isValid()) entries.add(e);
            }
        } catch (Throwable t) {
            net.mcreator.radiotowers.RadiotowersMod.LOGGER.warn("[RadioTowers] TaCZ API integration failed (will use registry fallback): {}", t.toString());
        }
    }

    private static Set<ResourceLocation> getIdsViaTimelessApi(String methodName) {
        try {
            Object set = Class.forName("com.tacz.guns.api.TimelessAPI").getMethod(methodName).invoke(null);
            Set<ResourceLocation> out = new java.util.HashSet<>();
            if (set instanceof Iterable<?> it) {
                for (Object o : it) {
                    if (o == null) continue;
                    if (o instanceof ResourceLocation rl) out.add(rl);
                    else {
                        // In case this is an entry set (Map.Entry), take key.
                        try {
                            Object k = o.getClass().getMethod("getKey").invoke(o);
                            if (k instanceof ResourceLocation rl2) out.add(rl2);
                        } catch (Exception ignored) {}
                    }
                }
            }
            return out;
        } catch (Throwable ignored) {
            return Set.of();
        }
    }

    /**
     * On client, GunSmithTable recipes are synced; each recipe result is a stack with gun/ammo NBT.
     * Extract all gun and ammo IDs from those results. Also scan all recipes by type in case the type key differs.
     */
    private static void getIdsFromGunSmithRecipes(Set<ResourceLocation> ammoIds, Set<ResourceLocation> gunIds) {
        Level level = clientLevelForCatalog;
        if (level == null) {
            return;
        }
        try {
            RecipeManager recipeManager = level.getRecipeManager();
            if (recipeManager == null) return;

            Class<?> iAmmoBoxClass = Class.forName("com.tacz.guns.api.item.IAmmoBox");
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            java.lang.reflect.Method getAmmoId = iAmmoBoxClass.getMethod("getAmmoId", ItemStack.class);
            java.lang.reflect.Method getGunId = iGunClass.getMethod("getGunId", ItemStack.class);

            // Use RecipeManager API directly (no reflection – obfuscation breaks getMethod at runtime). Use raw type to satisfy generics.
            RecipeType<?> taczType = ForgeRegistries.RECIPE_TYPES.getValue(ResourceLocation.parse("tacz:gun_smith_table_crafting"));
            @SuppressWarnings({"unchecked", "rawtypes"})
            java.util.List<Recipe<?>> recipes = taczType != null
                ? (java.util.List) recipeManager.getAllRecipesFor((RecipeType) taczType)
                : java.util.Collections.emptyList();

            // If no TaCZ recipes, scan all recipe types for any result that is tacz gun/ammo_box
            if (recipes.isEmpty()) {
                for (RecipeType<?> type : ForgeRegistries.RECIPE_TYPES) {
                    if (type == null) continue;
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    java.util.List<Recipe<?>> list = (java.util.List) recipeManager.getAllRecipesFor((RecipeType) type);
                    for (Recipe<?> r : list) {
                        if (r == null) continue;
                        ItemStack result = r.getResultItem(level.registryAccess());
                        if (result.isEmpty()) continue;
                        ResourceLocation id = ForgeRegistries.ITEMS.getKey(result.getItem());
                        if (id != null && isTaczNamespace(id.getNamespace())
                            && (iAmmoBoxClass.isInstance(result.getItem()) || iGunClass.isInstance(result.getItem()))) {
                            if (iAmmoBoxClass.isInstance(result.getItem())) {
                                try {
                                    Object rl = getAmmoId.invoke(result.getItem(), result);
                                    if (rl instanceof ResourceLocation loc) ammoIds.add(loc);
                                } catch (Exception ignored) {}
                            } else {
                                try {
                                    Object rl = getGunId.invoke(result.getItem(), result);
                                    if (rl instanceof ResourceLocation loc) gunIds.add(loc);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            } else {
                for (Recipe<?> recipe : recipes) {
                    ItemStack resultStack = recipe.getResultItem(level.registryAccess());
                    if (resultStack.isEmpty()) continue;
                    Item item = resultStack.getItem();
                    ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                    if (itemId == null || !isTaczNamespace(itemId.getNamespace())) continue;
                    if (iAmmoBoxClass.isInstance(item)) {
                        try {
                            Object id = getAmmoId.invoke(item, resultStack);
                            if (id instanceof ResourceLocation rl) ammoIds.add(rl);
                        } catch (Exception ignored) {}
                    } else if (iGunClass.isInstance(item)) {
                        try {
                            Object id = getGunId.invoke(item, resultStack);
                            if (id instanceof ResourceLocation rl) gunIds.add(rl);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Throwable t) {
            net.mcreator.radiotowers.RadiotowersMod.LOGGER.warn("[RadioTowers] TaCZ recipe fallback failed: {}", t.toString());
        }
    }

    private static Set<ResourceLocation> getKeysFromEntrySet(Object provider, String methodName) {
        try {
            Set<ResourceLocation> out = new java.util.HashSet<>();
            Object set = provider.getClass().getMethod(methodName).invoke(provider);
            if (set instanceof Iterable<?> it) {
                for (Object o : it) {
                    if (o == null) continue;
                    Object k = o.getClass().getMethod("getKey").invoke(o);
                    if (k instanceof ResourceLocation rl) out.add(rl);
                }
            }
            return out;
        } catch (Throwable ignored) {
            return Set.of();
        }
    }

    private static Item findFirstTaczItemImplementing(String interfaceName) {
        try {
            Class<?> iface = Class.forName(interfaceName);
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id != null && isTaczNamespace(id.getNamespace()) && iface.isInstance(item))
                    return item;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Returns only tacz:ammo_box (used for crate stacks so client gets a stack that renders with caliber model). */
    private static Item resolveAmmoBoxItem() {
        try {
            Class<?> iAmmoBoxClass = Class.forName("com.tacz.guns.api.item.IAmmoBox");
            Item ammoBoxItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:ammo_box"));
            if (ammoBoxItem != null && ammoBoxItem != Items.AIR && iAmmoBoxClass.isInstance(ammoBoxItem))
                return ammoBoxItem;
            return findFirstTaczItemImplementing("com.tacz.guns.api.item.IAmmoBox");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** TaCZ registers tacz:ammo (AmmoItem) and tacz:ammo_box (AmmoBoxItem). Prefer tacz:ammo for NBT stacks so icons match creative tab. */
    private static Item resolveAmmoBaseItem() {
        try {
            Class<?> iAmmoClass = Class.forName("com.tacz.guns.api.item.IAmmo");
            Item ammoItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:ammo"));
            if (ammoItem != null && ammoItem != Items.AIR && iAmmoClass.isInstance(ammoItem))
                return ammoItem;
            return resolveAmmoBoxItem();
        } catch (Throwable ignored) {
            return resolveAmmoBoxItem();
        }
    }

    /** TaCZ registers tacz:modern_kinetic_gun (no "tacz:gun"). Prefer that as base for setGunId stacks. */
    private static Item resolveGunBaseItem() {
        Item gunItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:modern_kinetic_gun"));
        if (gunItem != null && gunItem != Items.AIR) return gunItem;
        gunItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:gun"));
        if (gunItem != null && gunItem != Items.AIR) return gunItem;
        return findFirstTaczItemImplementing("com.tacz.guns.api.item.IGun");
    }

    /** True if the stack has TaCZ gun content id NBT. */
    public static boolean hasGunIdTag(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasTag() && stack.getTag() != null && stack.getTag().contains("GunId");
    }

    /** Raw-NBT fallback for crate guns so we never degrade to generic base gun item. */
    public static ItemStack createGunStackForCrateRawNbt(ResourceLocation gunId) {
        if (gunId == null || !gunId.getPath().startsWith("gun/")) return ItemStack.EMPTY;
        Item gunItem = resolveGunBaseItem();
        if (gunItem == null || gunItem == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(gunItem, 1);
        stack.getOrCreateTag().putString("GunId", gunId.toString());
        return stack;
    }

    /**
     * Add every TaCZ-registered Item that is ammo or gun (by interface), excluding the base ammo_box/gun.
     * Uses only plain ItemStack(item) – no NBT, no creative cache – so icons are whatever the item's model is.
     * Call this FIRST so we prefer real registered items over NBT-based entries.
     */
    public static void addAllTaczRegistryAmmoAndGuns(List<AirdropCatalog.Entry> entries) {
        if (!AirdropConfig.INCLUDE_TACZ_AMMO.get() && !AirdropConfig.INCLUDE_TACZ_GUNS.get()) return;
        if (!ModList.get().isLoaded("tacz")) return;
        boolean wantAmmo = AirdropConfig.INCLUDE_TACZ_AMMO.get();
        boolean wantGuns = AirdropConfig.INCLUDE_TACZ_GUNS.get();
        try {
            Class<?> iAmmoBoxClass = Class.forName("com.tacz.guns.api.item.IAmmoBox");
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id == null || item == Items.AIR || !isTaczNamespace(id.getNamespace())) continue;
                String path = id.getPath();
                if (shouldExclude(path)) continue;
                if (alreadyInList(entries, id)) continue;
                if (wantAmmo && iAmmoBoxClass.isInstance(item) && !"ammo_box".equals(path) && !"ammo".equals(path)) {
                    AirdropCatalog.Entry e = new AirdropCatalog.Entry(id, ammoPoints(id), 1);
                    if (e.isValid()) entries.add(e);
                } else if (wantGuns && iGunClass.isInstance(item) && !"gun".equals(path)) {
                    AirdropCatalog.Entry e = new AirdropCatalog.Entry(id, gunPoints(path), 1);
                    if (e.isValid()) entries.add(e);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * When TaCZ is loaded: add known tacz:ammo/<caliber> and tacz:gun/<name>.
     * Only NBT-based entries (no creative cache). Registry items should already be added by addAllTaczRegistryAmmoAndGuns.
     */
    public static void addKnownTaczIdsToCatalog(List<AirdropCatalog.Entry> entries) {
        if (!AirdropConfig.INCLUDE_TACZ_AMMO.get() && !AirdropConfig.INCLUDE_TACZ_GUNS.get()) return;
        if (!ModList.get().isLoaded("tacz")) return;
        boolean wantAmmo = AirdropConfig.INCLUDE_TACZ_AMMO.get();
        boolean wantGuns = AirdropConfig.INCLUDE_TACZ_GUNS.get();
        Item ammoBoxItem = resolveAmmoBoxItem();
        Item gunItem = resolveGunBaseItem();

        // Plain registry items by path (real item, real icon)
        for (ResourceLocation key : ForgeRegistries.ITEMS.getKeys()) {
            if (!wantAmmo) break;
            if (key == null || !isTaczNamespace(key.getNamespace())) continue;
            String path = key.getPath();
            if (!path.startsWith("ammo/") || path.length() <= 5) continue;
            if (shouldExclude(path)) continue;
            if (alreadyInList(entries, key)) continue;
            Item item = ForgeRegistries.ITEMS.getValue(key);
            if (item == null || item == Items.AIR) continue;
            AirdropCatalog.Entry e = new AirdropCatalog.Entry(key, ammoPoints(key), 1);
            if (e.isValid()) entries.add(e);
        }
        if (wantAmmo) {
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null || item == Items.AIR) continue;
            if (!isTaczNamespace(id.getNamespace())) continue;
            String path = id.getPath();
            boolean ammoPath = (path.startsWith("ammo_") && path.length() > 5) || TAC_I_AMMO_CALIBERS.contains(path);
            if (!ammoPath) continue;
            if (shouldExclude(path)) continue;
            if (alreadyInList(entries, id)) continue;
            ResourceLocation ammoId = path.startsWith("ammo/") ? id : ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "ammo/" + path);
            AirdropCatalog.Entry e = new AirdropCatalog.Entry(id, ammoPoints(ammoId), 1);
            if (e.isValid()) entries.add(e);
        }
        for (Item item : getTaczAmmoItems()) {
            if (item == ammoBoxItem) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null || alreadyInList(entries, id)) continue;
            if ("ammo_box".equals(id.getPath()) || "ammo".equals(id.getPath())) continue;
            AirdropCatalog.Entry e = new AirdropCatalog.Entry(id, ammoPoints(id), 1);
            if (e.isValid()) entries.add(e);
        }
        }

        try {
            Class<?> iAmmoBoxClass = Class.forName("com.tacz.guns.api.item.IAmmoBox");
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            for (String caliber : TAC_I_AMMO_CALIBERS) {
                if (!wantAmmo) break;
                if (alreadyInList(entries, ResourceLocation.parse("tacz:ammo/" + caliber))) continue;
                if (alreadyInList(entries, ResourceLocation.parse("tacz:" + caliber))) continue;
                ResourceLocation idAmmo = ResourceLocation.parse("tacz:ammo/" + caliber);
                ResourceLocation idBare = ResourceLocation.parse("tacz:" + caliber);
                Item registeredAmmo = ForgeRegistries.ITEMS.getValue(idAmmo);
                if (registeredAmmo == null || registeredAmmo == Items.AIR) registeredAmmo = ForgeRegistries.ITEMS.getValue(idBare);
                ResourceLocation idToUse = (registeredAmmo != null && registeredAmmo != Items.AIR) ? ForgeRegistries.ITEMS.getKey(registeredAmmo) : null;
                if (idToUse != null) {
                    AirdropCatalog.Entry e = new AirdropCatalog.Entry(idToUse, ammoPoints(idAmmo), 1);
                    if (e.isValid()) entries.add(e);
                    continue;
                }
                ResourceLocation id = idAmmo;
                if (ammoBoxItem == null || !iAmmoBoxClass.isInstance(ammoBoxItem)) continue;
                final Item ab = ammoBoxItem;
                final ResourceLocation aid = id;
                final int roundsPerBox = getAmmoStackSize(aid);
                Supplier<ItemStack> creator = () -> {
                    try {
                        ItemStack stack = new ItemStack(ab, 1);
                        iAmmoBoxClass.getMethod("setAmmoId", ItemStack.class, ResourceLocation.class).invoke(ab, stack, aid);
                        iAmmoBoxClass.getMethod("setAmmoCount", ItemStack.class, int.class).invoke(ab, stack, roundsPerBox);
                        return stack;
                    } catch (Exception e) { return ItemStack.EMPTY; }
                };
                AirdropCatalog.Entry e = new AirdropCatalog.Entry(id, ammoPoints(aid), 1, creator);
                if (e.isValid()) entries.add(e);
            }
            for (String gunName : TACZ_GUN_PATHS) {
                if (!wantGuns) break;
                ResourceLocation id = ResourceLocation.parse("tacz:gun/" + gunName);
                if (alreadyInList(entries, id)) continue;
                Item registeredGun = ForgeRegistries.ITEMS.getValue(id);
                if (registeredGun != null && registeredGun != Items.AIR) {
                    AirdropCatalog.Entry e = new AirdropCatalog.Entry(id, gunPoints(gunName), 1);
                    if (e.isValid()) entries.add(e);
                    continue;
                }
                if (gunItem == null || !iGunClass.isInstance(gunItem)) continue;
                final Item gi = gunItem;
                final ResourceLocation gid = id;
                Supplier<ItemStack> creator = () -> {
                    try {
                        ItemStack stack = new ItemStack(gi, 1);
                        iGunClass.getMethod("setGunId", ItemStack.class, ResourceLocation.class).invoke(gi, stack, gid);
                        return stack;
                    } catch (Exception ex) { return ItemStack.EMPTY; }
                };
                AirdropCatalog.Entry e = new AirdropCatalog.Entry(id, gunPoints(gunName), 1, creator);
                if (e.isValid()) entries.add(e);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Ammo items for crate loot: whitelist or fuzzy ammo-like paths. */
    public static List<Item> getTaczAmmoItems() {
        return ForgeRegistries.ITEMS.getValues().stream()
            .filter(item -> {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id == null || !isTaczNamespace(id.getNamespace()) || item == Items.AIR) return false;
                if ("ammo".equals(id.getPath())) return false; // tag placeholder
                String path = id.getPath();
                if (shouldExclude(path)) return false;
                return isAllowedAmmoPath(path) || isBulletLikePath(path);
            })
            .toList();
    }

    /**
     * Random bonus ammo for standard (non-order) loot-table crates.
     * Always builds a caliber stack with AmmoId NBT — never bare {@code tacz:ammo}/{@code ammo_box},
     * which show as generic "iron ammo box" ghosts and desync on click with TaCZ addons.
     */
    public static ItemStack randomTaczAmmoStack(RandomSource random) {
        if (TAC_I_AMMO_CALIBERS.isEmpty()) return ItemStack.EMPTY;
        String caliber = TAC_I_AMMO_CALIBERS.get(random.nextInt(TAC_I_AMMO_CALIBERS.size()));
        ResourceLocation ammoId = ResourceLocation.parse("tacz:ammo/" + caliber);
        ItemStack stack = createAmmoDisplayStack(ammoId);
        if (stack.isEmpty() || !hasAmmoIdTag(stack))
            stack = createAmmoStackForCrateRawNbt(ammoId);
        if (stack.isEmpty() || !hasAmmoIdTag(stack))
            return ItemStack.EMPTY;
        return stack;
    }
}
