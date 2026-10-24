package net.mcreator.radiotowers.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.mcreator.radiotowers.integration.LegacySavedDataFiles;

/** Persists whether the near-spawn guaranteed radio tower has been placed for this overworld. */
public class GuaranteedSpawnTowerData extends SavedData {
    private static final String DATA_NAME = "radiotowers_spawn_tower";

    private boolean placed;

    public GuaranteedSpawnTowerData() {}

    public static final Codec<GuaranteedSpawnTowerData> CODEC = CompoundTag.CODEC.xmap(
        GuaranteedSpawnTowerData::fromTag, GuaranteedSpawnTowerData::toTag);
    public static final SavedDataType<GuaranteedSpawnTowerData> TYPE = new SavedDataType<>(
        DATA_NAME,
        context -> {
            CompoundTag legacy = LegacySavedDataFiles.readLegacy(context.level(), DATA_NAME);
            GuaranteedSpawnTowerData data = legacy != null ? fromTag(legacy) : new GuaranteedSpawnTowerData();
            if (legacy != null) data.setDirty();
            return data;
        },
        context -> CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private static GuaranteedSpawnTowerData fromTag(CompoundTag tag) {
        GuaranteedSpawnTowerData data = new GuaranteedSpawnTowerData();
        data.placed = tag.getBooleanOr("placed", false);
        return data;
    }

    private CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("placed", placed);
        return tag;
    }

    public boolean isPlaced() {
        return placed;
    }

    public void setPlaced(boolean placed) {
        this.placed = placed;
        setDirty();
    }

    public static GuaranteedSpawnTowerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
