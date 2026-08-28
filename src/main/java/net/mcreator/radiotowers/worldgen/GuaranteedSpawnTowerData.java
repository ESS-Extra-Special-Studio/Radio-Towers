package net.mcreator.radiotowers.worldgen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persists whether the near-spawn guaranteed radio tower has been placed for this overworld. */
public class GuaranteedSpawnTowerData extends SavedData {
    private static final String DATA_NAME = "radiotowers_spawn_tower";

    private boolean placed;

    public GuaranteedSpawnTowerData() {}

    public GuaranteedSpawnTowerData(CompoundTag tag) {
        this.placed = tag.getBoolean("placed");
    }

    public boolean isPlaced() {
        return placed;
    }

    public void setPlaced(boolean placed) {
        this.placed = placed;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("placed", placed);
        return tag;
    }

    public static GuaranteedSpawnTowerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            GuaranteedSpawnTowerData::load,
            GuaranteedSpawnTowerData::new,
            DATA_NAME
        );
    }

    public static GuaranteedSpawnTowerData load(CompoundTag tag) {
        return new GuaranteedSpawnTowerData(tag);
    }
}
