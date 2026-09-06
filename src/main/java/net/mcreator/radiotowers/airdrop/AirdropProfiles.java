package net.mcreator.radiotowers.airdrop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pack-maker airdrop presets under {@code config/radiotowers/profiles/&lt;name&gt;.json}.
 */
public final class AirdropProfiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final class Data {
        public String standardAirdropLootTable;
        public Boolean enableStandardAirdrop;
        public Boolean enableCatalogAirdrop;
        public Boolean includeTaczGuns;
        public Boolean includeTaczAmmo;
        public Boolean enableEndRecipeItems;
        public Integer minimumCatalogPoints;
        public Integer maxTotalDifficulty;
        public List<String> blacklistItems;
        public List<String> whitelistTags;
        public List<String> customCatalogEntries;
    }

    private AirdropProfiles() {}

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve("radiotowers").resolve("profiles");
    }

    public static String sanitize(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    public static Path fileFor(String name) {
        return directory().resolve(sanitize(name) + ".json");
    }

    public static List<String> listNames() {
        List<String> out = new ArrayList<>();
        Path dir = directory();
        if (!Files.isDirectory(dir)) return out;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                .map(p -> p.getFileName().toString().replaceFirst("\\.json$", ""))
                .sorted()
                .forEach(out::add);
        } catch (IOException ignored) {}
        return out;
    }

    public static Data snapshotLive() {
        Data d = new Data();
        d.standardAirdropLootTable = AirdropConfig.STANDARD_AIRDROP_LOOT_TABLE.get();
        d.enableStandardAirdrop = AirdropConfig.ENABLE_STANDARD_AIRDROP.get();
        d.enableCatalogAirdrop = AirdropConfig.ENABLE_CATALOG_AIRDROP.get();
        d.includeTaczGuns = AirdropConfig.INCLUDE_TACZ_GUNS.get();
        d.includeTaczAmmo = AirdropConfig.INCLUDE_TACZ_AMMO.get();
        d.enableEndRecipeItems = AirdropConfig.ENABLE_END_RECIPE_ITEMS.get();
        d.minimumCatalogPoints = AirdropConfig.getMinimumCatalogPoints();
        d.maxTotalDifficulty = AirdropConfig.MAX_TOTAL_DIFFICULTY.get();
        d.blacklistItems = copyStrings(AirdropConfig.BLACKLIST_ITEMS.get());
        d.whitelistTags = copyStrings(AirdropConfig.WHITELIST_TAGS.get());
        d.customCatalogEntries = copyStrings(AirdropConfig.CUSTOM_CATALOG_ENTRIES.get());
        return d;
    }

    public static Path saveLive(String name) throws IOException {
        String id = sanitize(name);
        if (id.isEmpty()) throw new IOException("Invalid profile name");
        Files.createDirectories(directory());
        Path file = fileFor(id);
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(snapshotLive(), w);
        }
        AirdropConfig.ACTIVE_AIRDROP_PROFILE.set(id);
        AirdropConfig.ACTIVE_AIRDROP_PROFILE.save();
        return file;
    }

    public static Path loadIntoLive(String name) throws IOException {
        String id = sanitize(name);
        Path file = fileFor(id);
        if (!Files.isRegularFile(file)) throw new IOException("Missing profile: " + id);
        Data d;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            d = GSON.fromJson(r, Data.class);
        }
        if (d == null) throw new IOException("Empty profile: " + id);
        apply(d);
        AirdropConfig.ACTIVE_AIRDROP_PROFILE.set(id);
        AirdropConfig.ACTIVE_AIRDROP_PROFILE.save();
        AirdropCatalog.invalidate();
        return file;
    }

    public static void apply(Data d) {
        if (d.standardAirdropLootTable != null && !d.standardAirdropLootTable.isBlank()) {
            AirdropConfig.STANDARD_AIRDROP_LOOT_TABLE.set(d.standardAirdropLootTable.trim());
        }
        if (d.enableStandardAirdrop != null) AirdropConfig.ENABLE_STANDARD_AIRDROP.set(d.enableStandardAirdrop);
        if (d.enableCatalogAirdrop != null) AirdropConfig.ENABLE_CATALOG_AIRDROP.set(d.enableCatalogAirdrop);
        if (d.includeTaczGuns != null) AirdropConfig.INCLUDE_TACZ_GUNS.set(d.includeTaczGuns);
        if (d.includeTaczAmmo != null) AirdropConfig.INCLUDE_TACZ_AMMO.set(d.includeTaczAmmo);
        if (d.enableEndRecipeItems != null) AirdropConfig.ENABLE_END_RECIPE_ITEMS.set(d.enableEndRecipeItems);
        if (d.minimumCatalogPoints != null) AirdropConfig.MINIMUM_CATALOG_POINTS.set(d.minimumCatalogPoints);
        if (d.maxTotalDifficulty != null) AirdropConfig.MAX_TOTAL_DIFFICULTY.set(d.maxTotalDifficulty);
        if (d.blacklistItems != null) AirdropConfig.BLACKLIST_ITEMS.set(d.blacklistItems);
        if (d.whitelistTags != null) AirdropConfig.WHITELIST_TAGS.set(d.whitelistTags);
        if (d.customCatalogEntries != null) AirdropConfig.CUSTOM_CATALOG_ENTRIES.set(d.customCatalogEntries);
        AirdropConfig.saveAll();
    }

    private static List<String> copyStrings(List<? extends String> in) {
        List<String> out = new ArrayList<>();
        if (in != null) for (Object o : in) if (o instanceof String s) out.add(s);
        return out;
    }
}
