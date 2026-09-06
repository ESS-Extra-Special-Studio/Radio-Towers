package net.mcreator.radiotowers.events;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.events.WaveMobAggroHandler;
import net.mcreator.radiotowers.integration.EslWaveIntegration;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;

/**
 * Polls every 20 ticks: (1) Zombie Waves API state – when remainEnemysCount hits 0, deliver;
 * (2) Time-based – when 3 minutes have passed since wave start, end wave and deliver/next.
 * Stops infinite zombies if the API never ends the wave.
 */
@EventBusSubscriber(modid = RadiotowersMod.MODID)
public class WaveEndDetector {
    private static final boolean WAVE_DEBUG_LOGS = true;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null) return;

        // Nuclear baseline: do not end/fail waves or unfreeze mobs, but still push HUD state to the client.
        if (RadiotowersMod.WAVE_PATCH_NUCLEAR_MODE) {
            long tick = event.getServer().getTickCount();
            if (tick % 20 != 0) return;
            for (ServerLevel level : event.getServer().getAllLevels()) {
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
        if (event.getServer() == null) return;
        WaveMobAggroHandler.forceUnfreezeAllWaveMonsters(event.getServer());
        long tick = event.getServer().getTickCount();
        if (tick % 20 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
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
        WaveMobAggroHandler.forceUnfreezeAllWaveMonsters(event.getServer());
    }
}
