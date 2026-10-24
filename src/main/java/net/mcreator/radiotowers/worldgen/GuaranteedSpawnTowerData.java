package net.mcreator.radiotowers.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Persists whether the near-spawn guaranteed radio tower has been placed for this overworld. */
public class GuaranteedSpawnTowerData extends SavedData {
    public static final Codec<GuaranteedSpawnTowerData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.optionalFieldOf("placed", false).forGetter(GuaranteedSpawnTowerData::isPlaced)
    ).apply(i, GuaranteedSpawnTowerData::new));

    public static final SavedDataType<GuaranteedSpawnTowerData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("radiotowers", "spawn_tower"), () -> new GuaranteedSpawnTowerData(), CODEC);

    private boolean placed;

    public GuaranteedSpawnTowerData() {}

    public GuaranteedSpawnTowerData(boolean placed) {
        this.placed = placed;
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
