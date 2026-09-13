package net.mcreator.radiotowers;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import net.neoforged.fml.util.thread.SidedThreadGroups;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.mcreator.radiotowers.network.RadiotowersNetwork;

import net.mcreator.radiotowers.airdrop.AirdropCatalog;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.events.WaveMobAggroHandler;
import net.mcreator.radiotowers.integration.AirdropCooldownSavedData;
import net.mcreator.radiotowers.integration.BerezkaWaveChatSilencer;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;
import net.mcreator.radiotowers.integration.ZombieWavesMixinContext;
import net.mcreator.radiotowers.init.*;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.AbstractMap;

@Mod("radiotowers")
public class RadiotowersMod {
	public static final Logger LOGGER = LogManager.getLogger(RadiotowersMod.class);
	public static final String MODID = "radiotowers";
	/** Nuclear baseline mode for wave debugging: disable custom wave patches/mixins behavior. */
	public static final boolean WAVE_PATCH_NUCLEAR_MODE = true;

	public RadiotowersMod(IEventBus bus, ModContainer container) {
		NeoForge.EVENT_BUS.register(this);
		bus.addListener(this::commonSetup);
		bus.addListener(RadiotowersNetwork::register);
		net.mcreator.radiotowers.network.lobby.AirdropLobbyNetwork.register();
		RadiotowersModSounds.REGISTRY.register(bus);
		RadiotowersModBlocks.REGISTRY.register(bus);
		RadiotowersModBlockEntities.REGISTRY.register(bus);
		RadiotowersModItems.REGISTRY.register(bus);
		RadiotowersModEntities.REGISTRY.register(bus);
		RadiotowersModTabs.REGISTRY.register(bus);
		RadiotowersModMenus.REGISTRY.register(bus);
		container.registerConfig(ModConfig.Type.COMMON, AirdropConfig.SPEC, "radiotowers-common.toml");
		bus.addListener(this::onModConfig);
	}

	private void onModConfig(ModConfigEvent event) {
		if (event.getConfig().getSpec() == AirdropConfig.SPEC) {
			AirdropCatalog.invalidate();
		}
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		// Re-enabled: needed for ground-ring placement (prevents treetop spawns).
		// Note: in nuclear baseline mode, WaveMobAggroHandler is internally gated to avoid aggressive tick-time resnap/unfreeze.
		NeoForge.EVENT_BUS.register(new net.mcreator.radiotowers.events.WaveMobAggroHandler());

		// RadioTowers special blocks in airdrop loot catalog: metal bars 5, trash 2.5 (3), airdrop crate 5
		AirdropCatalog.ADDON_ENTRIES.add(new AirdropCatalog.Entry(ResourceLocation.parse("radiotowers:metal_bars"), 5));
		AirdropCatalog.ADDON_ENTRIES.add(new AirdropCatalog.Entry(ResourceLocation.parse("radiotowers:trashblock"), 1));
		AirdropCatalog.ADDON_ENTRIES.add(new AirdropCatalog.Entry(ResourceLocation.parse("radiotowers:airdrop_crate"), 5));

		// Vanilla music discs in airdrop loot selection, 30 points each
		int discPoints = 30;
		for (String id : new String[]{
			"minecraft:music_disc_13", "minecraft:music_disc_cat", "minecraft:music_disc_blocks",
			"minecraft:music_disc_chirp", "minecraft:music_disc_far", "minecraft:music_disc_mall",
			"minecraft:music_disc_mellohi", "minecraft:music_disc_stal", "minecraft:music_disc_strad",
			"minecraft:music_disc_ward", "minecraft:music_disc_11", "minecraft:music_disc_wait",
			"minecraft:music_disc_otherside", "minecraft:music_disc_pigstep", "minecraft:music_disc_5",
			"minecraft:music_disc_relic"
		}) {
			AirdropCatalog.ADDON_ENTRIES.add(new AirdropCatalog.Entry(ResourceLocation.parse(id), discPoints));
		}
	}

	// Start of user code block mod methods
	// End of user code block mod methods

	private static final Collection<AbstractMap.SimpleEntry<Runnable, Integer>> workQueue = new ConcurrentLinkedQueue<>();

	public static void queueServerWork(int tick, Runnable action) {
		if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER)
			workQueue.add(new AbstractMap.SimpleEntry<>(action, tick));
	}

	/** Re-run ghost cleanup at several delays so entities loaded after the first tick are still removed. */
	private static final int[] RECONNECT_GHOST_SWEEP_DELAYS = { 1, 15, 40, 100, 200, 400 };

	/** Before the first world tick: mute Berezka wave chat so only RadioTowers sends wave lines. */
	@SubscribeEvent
	public void onServerAboutToStart(ServerAboutToStartEvent event) {
		BerezkaWaveChatSilencer.apply();
	}

	/** After worlds are ready: ensure a STANDARD tower near spawn (once per overworld). */
	@SubscribeEvent
	public void onServerStarted(ServerStartedEvent event) {
		ServerLevel overworld = event.getServer().overworld();
		net.mcreator.radiotowers.worldgen.GuaranteedSpawnTower.schedule(overworld);
		for (ServerLevel level : event.getServer().getAllLevels()) {
			net.mcreator.radiotowers.integration.AirdropCooldown.purgeAllIfDisabled(level);
		}
	}

	/** Sync defense-session flag so client-side NMS (TacZ listener) matches server after join/relog. */
	@SubscribeEvent
	public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer sp)) return;
		ZombieWavesAPILoader.pushDefenseSessionStateToPlayer(sp);
		// Testing: when both cooldown configs are 0, clear any persisted cooldown from older sessions.
		if (net.mcreator.radiotowers.config.AirdropConfig.getStandardAirdropCooldownTicks() == 0
				&& net.mcreator.radiotowers.config.AirdropConfig.getWaveDeliveryCooldownTicks() == 0) {
			AirdropCooldownSavedData.get(sp.serverLevel()).clearPlayer(sp.getUUID());
		}
		long cooldownEnd = net.mcreator.radiotowers.integration.AirdropCooldown.getCooldownEndGameTime(sp.serverLevel(), sp.getUUID());
		RadiotowersNetwork.sendToPlayer(sp,
				new net.mcreator.radiotowers.network.AirdropStatePacket(false, cooldownEnd));
		// Place spawn tower into already-loaded chunks only (never force-gen).
		net.mcreator.radiotowers.worldgen.GuaranteedSpawnTower.scheduleForPlayer(sp);
		// Chunks (and straggler mobs) often finish loading after level load; sweep again when the player is in-world.
		if (ZombieWavesAPILoader.hasReconnectStragglerQueue(sp.serverLevel())) {
			ZombieWavesAPILoader.runReconnectStragglerCleanupPass(sp.serverLevel());
		}
	}

	/** On world load: clear pending and wave state immediately so no wave runs on rejoin; sweep ghost mobs with delays. */
	@SubscribeEvent
	public void onLevelLoad(LevelEvent.Load event) {
		if (!(event.getLevel() instanceof ServerLevel serverLevel) || serverLevel.isClientSide()) return;
		boolean berezka = ZombieWavesAPILoader.isZombieWavesAPILoaded();
		boolean esl = net.mcreator.radiotowers.integration.EslWaveIntegration.isEslWaveAvailable();
		if (!berezka && !esl && PendingAirdropStorage.getAnyPendingInLevel(serverLevel) == null) return;

		ZombieWavesAPILoader.clearDefenseWaveSession(serverLevel);
		// Wave + panel positions before clearing pending / ending active waves.
		LinkedHashSet<BlockPos> reconnectCenters = new LinkedHashSet<>(PendingAirdropStorage.copyWaveSweepCentersBeforeClear(serverLevel));
		if (berezka) {
			ZombieWavesAPILoader.addCurrentApiWavePosToList(serverLevel, reconnectCenters);
		}
		net.mcreator.radiotowers.integration.EslWaveIntegration.abortAllInDimension(serverLevel);
		ZombieWavesAPILoader.registerReconnectStragglerCenters(serverLevel, new ArrayList<>(reconnectCenters));
		PendingAirdropStorage.clearAllPendingInLevel(serverLevel);
		if (berezka) {
			ZombieWavesMixinContext.clearCurrentLevel();
			ZombieWavesAPILoader.endWaveAfterDeliver();
		}
		for (BlockPos center : reconnectCenters) {
			net.mcreator.radiotowers.integration.EslWaveIntegration.sweepStragglersNear(serverLevel, center);
		}
		for (int delay : RECONNECT_GHOST_SWEEP_DELAYS) {
			int d = delay;
			queueServerWork(d, () -> {
				ZombieWavesAPILoader.runReconnectStragglerCleanupPass(serverLevel);
				if (d == 1) {
					discardSavedPlanesInLevel(serverLevel);
				}
			});
		}
		queueServerWork(600, () -> ZombieWavesAPILoader.clearReconnectStragglerQueue(serverLevel));
	}

	/** On world unload: discard all wave mobs in this level so they are not saved (no zombies left after quit/rejoin). */
	@SubscribeEvent
	public void onLevelUnload(LevelEvent.Unload event) {
		if (!(event.getLevel() instanceof ServerLevel level) || level.isClientSide()) return;
		ZombieWavesAPILoader.clearDefenseWaveSession(level);
		// Discard all tagged wave mobs (our replacements / API-tagged)
		WaveMobAggroHandler.onLevelUnload(level);
		// Additionally, if a RadioTowers airdrop wave is still pending, sweep any undead near the wave anchor
		// so they cannot be saved and then appear as \"ghost\" zombies on next join.
		PendingAirdropStorage.forEachPendingIn(level, (waveStartPos, pending) -> {
			ZombieWavesAPILoader.sweepBrokenUndeadNearWave(level, waveStartPos, 80);
		});
	}

	/** Discard any airdrop plane entities in the level so a saved-in-flight plane doesn't appear when rejoining. */
	private static void discardSavedPlanesInLevel(ServerLevel level) {
		ResourceLocation planeId = ResourceLocation.parse("radiotowers:planeentity");
		for (Entity e : level.getAllEntities()) {
			if (planeId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())))
				e.discard();
		}
	}

	@SubscribeEvent
	public void tick(ServerTickEvent.Post event) {
		List<AbstractMap.SimpleEntry<Runnable, Integer>> actions = new ArrayList<>();
		workQueue.forEach(work -> {
			work.setValue(work.getValue() - 1);
			if (work.getValue() == 0)
				actions.add(work);
		});
		actions.forEach(e -> e.getKey().run());
		workQueue.removeAll(actions);
	}
}