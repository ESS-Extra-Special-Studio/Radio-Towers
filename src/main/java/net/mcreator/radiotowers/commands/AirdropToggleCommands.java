package net.mcreator.radiotowers.commands;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.StringTextComponent;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.airdrop.AirdropCatalog;
import net.mcreator.radiotowers.airdrop.AirdropProfiles;
import net.mcreator.radiotowers.airdrop.AirdropRuntimeToggles;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.integration.AirdropDifficultyTier;
import net.mcreator.radiotowers.integration.EslWaveIntegration;
import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;
import net.mcreator.radiotowers.init.RadiotowersModBlocks;
import net.mcreator.radiotowers.network.OpenCallAirdropPacket;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.ResourceLocationArgument;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.block.Block;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = "radiotowers")
public class AirdropToggleCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSource> dispatcher) {
        // PlayerEntity-facing lobby clicks (no OP required). Separate register so parent OP gate does not apply.
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
                // OP debug: force-open Call Airdrop (Boss/Endless tabs) without right-clicking a thin panel hitbox.
                .then(Commands.literal("opencall").executes(ctx -> openCall(ctx.getSource())))
        );
    }

    private static int openCall(CommandSource src) {
    Object player$src = src.getEntity(); ServerPlayerEntity player = (player$src instanceof ServerPlayerEntity) ? (ServerPlayerEntity) player$src : null;
        if (!(player != null)) {
            src.sendFailure(new StringTextComponent("PlayerEntity only"));
            return 0;
        }
        // Prefer a real radio panel nearby — Base claim rejects non-panel positions.
        BlockPos nearby = findNearbyRadioPanel(player);
        final BlockPos pos = nearby != null ? nearby : player.blockPosition();
        if (nearby == null) {
            src.sendSuccess(new StringTextComponent(
                "No radio panel within 10 blocks — opened at feet " + pos.toShortString()
                    + " (Base claim will fail until you open a real panel)"), false);
        } else {
            src.sendSuccess(new StringTextComponent("Opened Call Airdrop at panel " + pos.toShortString()), false);
        }
        RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new OpenCallAirdropPacket(pos));
        return 1;
    }

    /** Nearest radiotowers:radio_panel within 10 blocks of the player (inclusive). */
    private static BlockPos findNearbyRadioPanel(ServerPlayerEntity player) {
    ServerWorld level = (player.level instanceof ServerWorld) ? (ServerWorld) player.level : null;
        if (!(level != null)) return null;
        Block panel = RadiotowersModBlocks.RADIO_PANEL.get();
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dy = -2; dy <= 4; dy++) {
            for (int dx = -10; dx <= 10; dx++) {
                for (int dz = -10; dz <= 10; dz++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    if (!level.getBlockState(p).is(panel)) continue;
                    int d = dx * dx + dy * dy + dz * dz;
                    if (d < bestDist) {
                        bestDist = d;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }

    private static int lobbyAccept(CommandSource src, String id) {
    Object player$src = src.getEntity(); net.minecraft.entity.player.ServerPlayerEntity player = (player$src instanceof net.minecraft.entity.player.ServerPlayerEntity) ? (net.minecraft.entity.player.ServerPlayerEntity) player$src : null;
        if (!(player != null)) return 0;
        try {
            net.mcreator.radiotowers.lobby.AirdropLobbyService.handleAccept(player, java.util.UUID.fromString(id));
            return 1;
        } catch (Exception e) {
            src.sendFailure(new StringTextComponent("Invalid lobby id"));
            return 0;
        }
    }

    private static int lobbyDecline(CommandSource src, String id) {
    Object player$src = src.getEntity(); net.minecraft.entity.player.ServerPlayerEntity player = (player$src instanceof net.minecraft.entity.player.ServerPlayerEntity) ? (net.minecraft.entity.player.ServerPlayerEntity) player$src : null;
        if (!(player != null)) return 0;
        try {
            net.mcreator.radiotowers.lobby.AirdropLobbyService.handleDecline(player, java.util.UUID.fromString(id));
            return 1;
        } catch (Exception e) {
            src.sendFailure(new StringTextComponent("Invalid lobby id"));
            return 0;
        }
    }

    private static int lobbyReady(CommandSource src, String id) {
    Object player$src = src.getEntity(); net.minecraft.entity.player.ServerPlayerEntity player = (player$src instanceof net.minecraft.entity.player.ServerPlayerEntity) ? (net.minecraft.entity.player.ServerPlayerEntity) player$src : null;
        if (!(player != null)) return 0;
        try {
            java.util.UUID lobbyId = id != null && !id.trim().isEmpty() ? java.util.UUID.fromString(id) : null;
            net.mcreator.radiotowers.lobby.AirdropLobbyService.handleReady(player, lobbyId, true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(new StringTextComponent("Invalid lobby id"));
            return 0;
        }
    }

    private static void line(CommandSource src, String text) {
        src.sendSuccess(new StringTextComponent(text), false);
    }

    private static int dump(CommandSource src) {
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

    private static int catalog(CommandSource src) {
        AirdropCatalog.invalidate();
        java.util.List<net.mcreator.radiotowers.airdrop.AirdropCatalog.Entry> entries = AirdropCatalog.getEntries();
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

    private static int probe(CommandSource src, ResourceLocation id) {
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

    private static int applyTest(CommandSource src) {
        AirdropConfig.applyTestProfile();
        AirdropRuntimeToggles.setEndRecipeItemsOverride(null);
        AirdropCatalog.invalidate();
        line(src, "[RadioTowers] Test profile written to radiotowers-common.toml.");
        catalog(src);
        return checklist(src);
    }

    private static int restore(CommandSource src) {
        AirdropRuntimeToggles.setEndRecipeItemsOverride(null);
        AirdropConfig.restoreDefaults();
        AirdropCatalog.invalidate();
        line(src, "[RadioTowers] Defaults restored to radiotowers-common.toml.");
        return dump(src);
    }

    private static int checklist(CommandSource src) {
        line(src, "=== RadioTowers checks ===");
        line(src, "1. Call Airdrop list: NO tacz:gun/m1911, NO tacz:ammo/9mm. Other guns and ammo still listed.");
        line(src, "2. /radiotowers config catalog — those two present=false.");
        line(src, "3. Standard loot table currently " + AirdropConfig.getStandardAirdropLootTable()
            + " — /radiotowers loot easy|medium|hard|default to switch.");
        line(src, "4. Waves still short (applytest): 20 pts = 2 waves, 5 zombies, 1 min.");
        return 1;
    }

    private static int setLoot(CommandSource src, String tableId) {
        AirdropConfig.STANDARD_AIRDROP_LOOT_TABLE.set(tableId);
        AirdropConfig.STANDARD_AIRDROP_LOOT_TABLE.save();
        line(src, "Standard airdrop loot table = " + tableId);
        return 1;
    }

    private static int profileList(CommandSource src) {
        java.util.List<String> names = AirdropProfiles.listNames();
        line(src, "Profiles in config/radiotowers/profiles/: "
            + (names.isEmpty() ? "(none yet — /radiotowers profile save <name>)" : String.join(", ", names)));
        line(src, "activeAirdropProfile=" + AirdropConfig.ACTIVE_AIRDROP_PROFILE.get());
        return 1;
    }

    private static int profileSave(CommandSource src, String name) {
        try {
            java.nio.file.Path file = AirdropProfiles.saveLive(name);
            line(src, "Saved current airdrop settings to " + file);
        } catch (Exception e) {
            src.sendFailure(new StringTextComponent("Save failed: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    private static int profileLoad(CommandSource src, String name) {
        try {
            java.nio.file.Path file = AirdropProfiles.loadIntoLive(name);
            line(src, "Loaded " + file + " into radiotowers-common.toml.");
            catalog(src);
        } catch (Exception e) {
            src.sendFailure(new StringTextComponent("Load failed: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    private static int status(CommandSource source) {
        boolean enabled = AirdropRuntimeToggles.isEndRecipeItemsEnabled();
        Boolean override = AirdropRuntimeToggles.getEndRecipeItemsOverride();
        String modeKey = override == null ? "command.radiotowers.endrecipeitems.mode.config" : "command.radiotowers.endrecipeitems.mode.runtime_override";
        source.sendSuccess(new TranslationTextComponent(
            "command.radiotowers.endrecipeitems.status",
            new TranslationTextComponent(enabled ? "command.radiotowers.endrecipeitems.state.on" : "command.radiotowers.endrecipeitems.state.off"),
            new TranslationTextComponent(modeKey)
        ), false);
        return 1;
    }

    private static int set(CommandSource source, boolean enabled) {
        AirdropRuntimeToggles.setEndRecipeItemsOverride(enabled);
        AirdropCatalog.invalidate();
        source.sendSuccess(new TranslationTextComponent(
            "command.radiotowers.endrecipeitems.set",
            new TranslationTextComponent(enabled ? "command.radiotowers.endrecipeitems.state.on" : "command.radiotowers.endrecipeitems.state.off")
        ), true);
        return 1;
    }

    private static int toggle(CommandSource source) {
        boolean next = !AirdropRuntimeToggles.isEndRecipeItemsEnabled();
        return set(source, next);
    }

    private static int clearOverride(CommandSource source) {
        AirdropRuntimeToggles.setEndRecipeItemsOverride(null);
        AirdropCatalog.invalidate();
        source.sendSuccess(new TranslationTextComponent("command.radiotowers.endrecipeitems.use_config"), true);
        return 1;
    }
}
