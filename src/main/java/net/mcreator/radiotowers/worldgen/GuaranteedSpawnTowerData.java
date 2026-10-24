package net.mcreator.radiotowers.worldgen;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;

/** Persists whether the near-spawn guaranteed radio tower has been placed for this overworld. */
public class GuaranteedSpawnTowerData extends WorldSavedData {
    private static final String DATA_NAME = "radiotowers_spawn_tower";

    private boolean placed;

    public GuaranteedSpawnTowerData() {
        super(DATA_NAME);
    }

    @Override
    public void load(CompoundNBT tag) {
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
    public CompoundNBT save(CompoundNBT tag) {
        tag.putBoolean("placed", placed);
        return tag;
    }

    public static GuaranteedSpawnTowerData get(ServerWorld level) {
        return level.getDataStorage().computeIfAbsent(GuaranteedSpawnTowerData::new, DATA_NAME);
    }
}
