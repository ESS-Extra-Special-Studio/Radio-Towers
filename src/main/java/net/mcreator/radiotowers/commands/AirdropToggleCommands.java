package net.mcreator.radiotowers.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.mcreator.radiotowers.airdrop.AirdropCatalog;
import net.mcreator.radiotowers.airdrop.AirdropProfiles;
import net.mcreator.radiotowers.airdrop.AirdropRuntimeToggles;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.integration.AirdropDifficultyTier;
import net.mcreator.radiotowers.integration.EslWaveIntegration;
import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "radiotowers")
public class AirdropToggleCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Player-facing lobby clicks (no OP required). Separate register so parent OP gate does not apply.
        dispatcher.register(
            Commands.literal("radiotowers")
                .then(Commands.literal("lobby")
                    .then(Commands.literal("accept")
                        .then(Commands.argument("id", StringArgumentType.string())
                            .executes(ctx -> lobbyAccept(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("decline")
                        .then(Commands.argument("id", StringArgumentType.string())
                            .executes(ctx -> lobbyDecline(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("ready")
                        .executes(ctx -> lobbyReady(ctx.getSource(), null))
                        .then(Commands.argument("id", StringArgumentType.string())
                            .executes(ctx -> lobbyReady(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                )
        );
        dispatcher.register(
            Commands.literal("radiotowers")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("endrecipeitems")
                    .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                    .then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), true)))
                    .then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), false)))
                    .then(Commands.literal("toggle").executes(ctx -> toggle(ctx.getSource())))
                    .then(Commands.literal("use_config").executes(ctx -> clearOverride(ctx.getSource())))
                )
                .then(Commands.literal("config")
                    .then(Commands.literal("dump").executes(ctx -> dump(ctx.getSource())))
                    .then(Commands.literal("catalog").executes(ctx -> catalog(ctx.getSource())))
                    .then(Commands.literal("probe")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                            .executes(ctx -> probe(ctx.getSource(), ResourceLocationArgument.getId(ctx, "id")))))
                    .then(Commands.literal("checklist").executes(ctx -> checklist(ctx.getSource())))
                    .then(Commands.literal("applytest").executes(ctx -> applyTest(ctx.getSource())))
                    .then(Commands.literal("restoredefaults").executes(ctx -> restore(ctx.getSource())))
                )
                .then(Commands.literal("loot")
                    .then(Commands.literal("default").executes(ctx -> setLoot(ctx.getSource(), AirdropConfig.DEFAULT_STANDARD_LOOT_TABLE)))
                    .then(Commands.literal("easy").executes(ctx -> setLoot(ctx.getSource(), AirdropConfig.LOOT_TABLE_EASY)))
                    .then(Commands.literal("medium").executes(ctx -> setLoot(ctx.getSource(), AirdropConfig.LOOT_TABLE_MEDIUM)))
                    .then(Commands.literal("hard").executes(ctx -> setLoot(ctx.getSource(), AirdropConfig.LOOT_TABLE_HARD)))
                )
                .then(Commands.literal("profile")
                    .then(Commands.literal("list").executes(ctx -> profileList(ctx.getSource())))
                    .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> profileSave(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                    .then(Commands.literal("load")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> profileLoad(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                )
        );
    }

    private static int lobbyAccept(CommandSourceStack src, String id) {
        var player = src.getPlayer();
        if (player == null) return 0;
        try {
            net.mcreator.radiotowers.lobby.AirdropLobbyService.handleAccept(player, java.util.UUID.fromString(id));
            return 1;
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Invalid lobby id"));
            return 0;
        }
    }

    private static int lobbyDecline(CommandSourceStack src, String id) {
        var player = src.getPlayer();
        if (player == null) return 0;
        try {
            net.mcreator.radiotowers.lobby.AirdropLobbyService.handleDecline(player, java.util.UUID.fromString(id));
            return 1;
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Invalid lobby id"));
            return 0;
        }
    }

    private static int lobbyReady(CommandSourceStack src, String id) {
        var player = src.getPlayer();
        if (player == null) return 0;
        try {
            java.util.UUID lobbyId = id != null && !id.isBlank() ? java.util.UUID.fromString(id) : null;
            net.mcreator.radiotowers.lobby.AirdropLobbyService.handleReady(player, lobbyId, true);
            return 1;
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Invalid lobby id"));
            return 0;
        }
    }

    private static void line(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static int dump(CommandSourceStack src) {
        line(src, "=== RadioTowers live config ===");
        line(src, "enableStandard=" + AirdropConfig.ENABLE_STANDARD_AIRDROP.get()
            + " enableCatalog=" + AirdropConfig.ENABLE_CATALOG_AIRDROP.get()
            + " profile=" + AirdropConfig.ACTIVE_AIRDROP_PROFILE.get());
        line(src, "min=" + AirdropConfig.getMinimumCatalogPoints()
            + " max=" + AirdropConfig.MAX_TOTAL_DIFFICULTY.get()
            + " loot=" + AirdropConfig.getStandardAirdropLootTable());
        line(src, "cooldowns standardMin=" + AirdropConfig.STANDARD_AIRDROP_COOLDOWN_MINUTES.get()
            + " waveMin=" + AirdropConfig.WAVE_DELIVERY_COOLDOWN_MINUTES.get());
        line(src, "tacz ammo=" + AirdropConfig.INCLUDE_TACZ_AMMO.get()
            + " guns=" + AirdropConfig.INCLUDE_TACZ_GUNS.get()
            + " simpleIcons=" + AirdropConfig.SIMPLIFY_TACZ_ICONS_IN_AIRDROP_LIST.get());
        line(src, "endRecipeItems=" + AirdropRuntimeToggles.isEndRecipeItemsEnabled()
            + " nonVanillaForgeTags=" + AirdropConfig.INCLUDE_NON_VANILLA_FORGE_TAG_ITEMS.get());
        line(src, "blacklist=" + AirdropConfig.BLACKLIST_ITEMS.get());
        line(src, "customEntries=" + AirdropConfig.CUSTOM_CATALOG_ENTRIES.get());
        line(src, "[waves] useWaves=" + AirdropConfig.shouldUseZombieWavesForAirdrop()
            + " ESL=" + EslWaveIntegration.isEslWaveAvailable()
            + " Berezka=" + ZombieWavesAPILoader.isZombieWavesAPILoaded());
        line(src, "maxWaves=" + AirdropConfig.MAX_WAVES.get()
            + " durationMin=" + AirdropConfig.WAVE_DURATION_MINUTES.get()
            + " zombies t1=" + AirdropDifficultyTier.getZombiesPerWaveForTier(1)
            + " t2=" + AirdropDifficultyTier.getZombiesPerWaveForTier(2));
        line(src, "aggroExclude=" + AirdropConfig.WAVES_AGGRO_EXCLUDE_ENTITY_IDS.get());
        line(src, "[worldgen] density%=" + AirdropConfig.TOWER_SPAWN_DENSITY_PERCENT.get()
            + " guaranteeSpawn=" + AirdropConfig.GUARANTEE_SPAWN_TOWER.get());
        return 1;
    }

    private static int catalog(CommandSourceStack src) {
        AirdropCatalog.invalidate();
        var entries = AirdropCatalog.getEntries();
        int guns = 0;
        int ammo = 0;
        boolean m1911 = false;
        boolean ammo9 = false;
        boolean netherStar = false;
        for (AirdropCatalog.Entry e : entries) {
            String id = e.getItemId().toString();
            String path = e.getItemId().getPath();
            if (id.startsWith("tacz:") && (path.startsWith("gun/") || path.equals("gun"))) guns++;
            if (id.startsWith("tacz:") && (path.startsWith("ammo/") || path.equals("ammo"))) ammo++;
            String last = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            if (id.startsWith("tacz:") && "m1911".equalsIgnoreCase(last)) m1911 = true;
            if (id.startsWith("tacz:") && "9mm".equalsIgnoreCase(last)) ammo9 = true;
            if ("minecraft:nether_star".equals(id)) netherStar = true;
        }
        line(src, "=== Catalog rebuild ===");
        line(src, "entries=" + entries.size() + " taczGuns=" + guns + " taczAmmo=" + ammo);
        line(src, AirdropConfig.TEST_BLACKLIST_GUN + " present=" + m1911
            + " | " + AirdropConfig.TEST_BLACKLIST_AMMO + " present=" + ammo9
            + " | nether_star=" + netherStar);
        line(src, "After applytest those two TaCZ ids should be present=false. Other guns/ammo should still show.");
        return 1;
    }

    private static int probe(CommandSourceStack src, ResourceLocation id) {
        AirdropCatalog.invalidate();
        for (AirdropCatalog.Entry e : AirdropCatalog.getEntries()) {
            if (AirdropCatalog.catalogIdMatches(id, e)) {
                line(src, e.getItemId() + " IS in catalog at " + e.getDifficultyPoints() + " points (stack " + e.getDefaultStackSize() + ").");
                return 1;
            }
        }
        line(src, id + " is NOT in catalog.");
        return 1;
    }

    private static int applyTest(CommandSourceStack src) {
        AirdropConfig.applyTestProfile();
        AirdropRuntimeToggles.setEndRecipeItemsOverride(null);
        AirdropCatalog.invalidate();
        line(src, "[RadioTowers] Test profile written to radiotowers-common.toml.");
        catalog(src);
        return checklist(src);
    }

    private static int restore(CommandSourceStack src) {
        AirdropRuntimeToggles.setEndRecipeItemsOverride(null);
        AirdropConfig.restoreDefaults();
        AirdropCatalog.invalidate();
        line(src, "[RadioTowers] Defaults restored to radiotowers-common.toml.");
        return dump(src);
    }

    private static int checklist(CommandSourceStack src) {
        line(src, "=== RadioTowers checks ===");
        line(src, "1. Call Airdrop list: NO tacz:gun/m1911, NO tacz:ammo/9mm. Other guns and ammo still listed.");
        line(src, "2. /radiotowers config catalog — those two present=false.");
        line(src, "3. Standard loot table currently " + AirdropConfig.getStandardAirdropLootTable()
            + " — /radiotowers loot easy|medium|hard|default to switch.");
        line(src, "4. Waves still short (applytest): 20 pts = 2 waves, 5 zombies, 1 min.");
        return 1;
    }

    private static int setLoot(CommandSourceStack src, String tableId) {
        AirdropConfig.STANDARD_AIRDROP_LOOT_TABLE.set(tableId);
        AirdropConfig.STANDARD_AIRDROP_LOOT_TABLE.save();
        line(src, "Standard airdrop loot table = " + tableId);
        return 1;
    }

    private static int profileList(CommandSourceStack src) {
        var names = AirdropProfiles.listNames();
        line(src, "Profiles in config/radiotowers/profiles/: "
            + (names.isEmpty() ? "(none yet — /radiotowers profile save <name>)" : String.join(", ", names)));
        line(src, "activeAirdropProfile=" + AirdropConfig.ACTIVE_AIRDROP_PROFILE.get());
        return 1;
    }

    private static int profileSave(CommandSourceStack src, String name) {
        try {
            var file = AirdropProfiles.saveLive(name);
            line(src, "Saved current airdrop settings to " + file);
        } catch (Exception e) {
            src.sendFailure(Component.literal("Save failed: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    private static int profileLoad(CommandSourceStack src, String name) {
        try {
            var file = AirdropProfiles.loadIntoLive(name);
            line(src, "Loaded " + file + " into radiotowers-common.toml.");
            catalog(src);
        } catch (Exception e) {
            src.sendFailure(Component.literal("Load failed: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    private static int status(CommandSourceStack source) {
        boolean enabled = AirdropRuntimeToggles.isEndRecipeItemsEnabled();
        Boolean override = AirdropRuntimeToggles.getEndRecipeItemsOverride();
        String modeKey = override == null ? "command.radiotowers.endrecipeitems.mode.config" : "command.radiotowers.endrecipeitems.mode.runtime_override";
        source.sendSuccess(() -> Component.translatable(
            "command.radiotowers.endrecipeitems.status",
            Component.translatable(enabled ? "command.radiotowers.endrecipeitems.state.on" : "command.radiotowers.endrecipeitems.state.off"),
            Component.translatable(modeKey)
        ), false);
        return 1;
    }

    private static int set(CommandSourceStack source, boolean enabled) {
        AirdropRuntimeToggles.setEndRecipeItemsOverride(enabled);
        AirdropCatalog.invalidate();
        source.sendSuccess(() -> Component.translatable(
            "command.radiotowers.endrecipeitems.set",
            Component.translatable(enabled ? "command.radiotowers.endrecipeitems.state.on" : "command.radiotowers.endrecipeitems.state.off")
        ), true);
        return 1;
    }

    private static int toggle(CommandSourceStack source) {
        boolean next = !AirdropRuntimeToggles.isEndRecipeItemsEnabled();
        return set(source, next);
    }

    private static int clearOverride(CommandSourceStack source) {
        AirdropRuntimeToggles.setEndRecipeItemsOverride(null);
        AirdropCatalog.invalidate();
        source.sendSuccess(() -> Component.translatable("command.radiotowers.endrecipeitems.use_config"), true);
        return 1;
    }
}
