package net.mcreator.radiotowers.events;

import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.events.WaveMobAggroHandler;
import net.mcreator.radiotowers.integration.EslWaveIntegration;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;

/**
 * Polls every 20 ticks: (1) ZombieEntity Waves API state – when remainEnemysCount hits 0, deliver;
 * (2) Time-based – when 3 minutes have passed since wave start, end wave and deliver/next.
 * Stops infinite zombies if the API never ends the wave.
 */
@Mod.EventBusSubscriber(modid = RadiotowersMod.MODID)
public class WaveEndDetector {
    private static final boolean WAVE_DEBUG_LOGS = true;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Nuclear baseline: do not end/fail waves or unfreeze mobs, but still push HUD state to the client.
        if (RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) {
            if (event.phase != TickEvent.Phase.START) return;
            long tick = server.getTickCount();
            if (tick % 20 != 0) return;
            for (ServerWorld level : server.getAllLevels()) {
                ZombieWavesAPILoader.enforceApiStoppedWhenNoPending(level);
                // In nuclear mode we rely on basic zombie-family death counting (no tags/mixins),
                // so we still allow wave completion/failure to run, but we skip any unfreeze logic.
                ZombieWavesAPILoader.checkWaveEndAndDeliver(level);
                ZombieWavesAPILoader.checkTimeBasedWaveEnd(level);
                ZombieWavesAPILoader.sendWaveStateToPlayers(level);
                EslWaveIntegration.sendWaveStateToPlayers(level);
            }
            return;
        }
        if (server == null) return;
        if (event.phase == TickEvent.Phase.START) {
            // Unfreeze wave monsters before any entity ticks (reduces ghost zombies)
            WaveMobAggroHandler.forceUnfreezeAllWaveMonsters(server);
            // Run at START so we call endWave before the API's tick (reduces duplicate "Wave has ended" chat)
            long tick = server.getTickCount();
            if (tick % 20 != 0) return;
            for (ServerWorld level : server.getAllLevels()) {
                ZombieWavesAPILoader.enforceApiStoppedWhenNoPending(level);
                ZombieWavesAPILoader.checkWaveEndAndDeliver(level);
                ZombieWavesAPILoader.checkTimeBasedWaveEnd(level);
                ZombieWavesAPILoader.sendWaveStateToPlayers(level);
                EslWaveIntegration.sendWaveStateToPlayers(level);
                if (tick % 100 == 0) ZombieWavesAPILoader.checkGhostSync(level);
                if (WAVE_DEBUG_LOGS && tick % 100 == 0) {
                    PendingAirdropStorage.forEachPendingIn(level, (waveStartPos, p) -> {
                        int trackedAlive = WaveMobAggroHandler.countAliveWaveMobsInLevel(level);
                        int taggedAlive = WaveMobAggroHandler.countAliveWaveMobsInLevelByTag(level);
                        RadiotowersMod.LOGGER.warn(
                            "[WaveDebug] dim={} pos={} spawn={}/{} killed={}/{} trackedAlive={} taggedAlive={} waveAgeTicks={}",
                            level.dimension().location(),
                            waveStartPos,
                            p.spawnCountThisWave,
                            p.maxZombiesPerWave,
                            p.killedCountThisWave,
                            p.maxZombiesPerWave,
                            trackedAlive,
                            taggedAlive,
                            (level.getGameTime() - p.currentWaveStartedAtGameTime)
                        );
                    });
                }
            }
        } else {
            // END: after all entity ticks, force unfreeze every MonsterEntity in levels with pending wave (fixes ghost zombies)
            net.mcreator.radiotowers.events.WaveMobAggroHandler.forceUnfreezeAllWaveMonsters(server);
        }
    }
}
