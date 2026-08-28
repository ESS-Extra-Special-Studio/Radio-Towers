package net.mcreator.radiotowers.integration;

import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * Default station cycle for panel GUIs when Dead Air is present.
 * Jukebox FM is only included when the panel has the Jukebox Upgrade (synced via KnownTowersClientCache).
 */
public final class DeadAirDefaultStations {

    public record Entry(String displayName, String stationId) {}

    private static final List<Entry> WITHOUT_JUKEBOX = List.of(
        new Entry("Bedrock Radio", "dead_air:bedrock_radio"),
        new Entry("Creatopia Radio", "dead_air:creatopia_radio"),
        new Entry("Remix Radio", "dead_air:remix_radio")
    );
    private static final List<Entry> WITH_JUKEBOX = List.of(
        new Entry("Bedrock Radio", "dead_air:bedrock_radio"),
        new Entry("Creatopia Radio", "dead_air:creatopia_radio"),
        new Entry("Remix Radio", "dead_air:remix_radio"),
        new Entry("Jukebox FM", "dead_air:jukebox_fm")
    );

    private DeadAirDefaultStations() {}

    public static List<Entry> forPanel(BlockPos panelPos) {
        if (!ModList.get().isLoaded("dead_air") || panelPos == null) {
            return WITHOUT_JUKEBOX;
        }
        try {
            Class<?> c = Class.forName("uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache");
            var m = c.getMethod("isJukeboxModuleInstalledForPanel", BlockPos.class);
            Object r = m.invoke(null, panelPos);
            if (Boolean.TRUE.equals(r)) {
                return WITH_JUKEBOX;
            }
        } catch (Throwable ignored) {
        }
        return WITHOUT_JUKEBOX;
    }
}
