package net.mcreator.radiotowers.integration;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists pending airdrops per level so the wave survives player leave/rejoin and server restart.
 */
public class PendingAirdropSavedData extends SavedData {

    private static final String DATA_NAME = "radiotowers_pending_airdrops";

    private final Map<BlockPos, PendingAirdropStorage.Pending> pending = new HashMap<>();

    public PendingAirdropSavedData() {
        super();
    }

    public static final Codec<PendingAirdropSavedData> CODEC = CompoundTag.CODEC.xmap(
        PendingAirdropSavedData::fromTag, PendingAirdropSavedData::toTag);
    public static final SavedDataType<PendingAirdropSavedData> TYPE = new SavedDataType<>(
        DATA_NAME,
        context -> {
            CompoundTag legacy = LegacySavedDataFiles.readLegacy(context.level(), DATA_NAME);
            PendingAirdropSavedData data = legacy != null ? fromTag(legacy) : new PendingAirdropSavedData();
            if (legacy != null) data.setDirty();
            return data;
        },
        context -> CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    public static PendingAirdropSavedData fromTag(CompoundTag tag) {
        PendingAirdropSavedData data = new PendingAirdropSavedData();
        ListTag list = tag.getListOrEmpty("pending");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            BlockPos waveStart = BlockPos.of(entry.getLongOr("wavePos", 0L));
            var waveInts = entry.getIntArray("wavePos");
            if (waveInts.isPresent() && waveInts.get().length >= 3) {
                int[] a = waveInts.get();
                waveStart = new BlockPos(a[0], a[1], a[2]);
            } else if (entry.get("wavePos") instanceof CompoundTag wp) {
                waveStart = new BlockPos(wp.getIntOr("X", 0), wp.getIntOr("Y", 0), wp.getIntOr("Z", 0));
            }
            BlockPos panelPos = waveStart;
            var panelInts = entry.getIntArray("panelPos");
            if (panelInts.isPresent() && panelInts.get().length >= 3) {
                int[] a = panelInts.get();
                panelPos = new BlockPos(a[0], a[1], a[2]);
            } else if (entry.get("panelPos") instanceof CompoundTag pp) {
                panelPos = new BlockPos(pp.getIntOr("X", 0), pp.getIntOr("Y", 0), pp.getIntOr("Z", 0));
            }
            List<String> itemIds = new ArrayList<>();
            ListTag ids = entry.getListOrEmpty("itemIds");
            for (int j = 0; j < ids.size(); j++) itemIds.add(ids.getStringOr(j, ""));
            List<Integer> quantities = new ArrayList<>();
            ListTag qty = entry.getListOrEmpty("quantities");
            for (int j = 0; j < qty.size(); j++) quantities.add(qty.getIntOr(j, 0));
            int difficulty = entry.getIntOr("difficulty", 0);
            UUID player = entry.read("player", UUIDUtil.CODEC).orElse(null);
            if (player == null) continue;
            long storedAt = entry.getLongOr("storedAt", 0L);
            long waveStartedAt = entry.getLongOr("waveStartedAt", 0L);
            int wavesRemaining = entry.getIntOr("wavesRemaining", 0);
            int totalWaves = entry.getIntOr("totalWaves", 0);
            int tier = entry.getIntOr("tier", 0);
            int maxZombies = entry.getIntOr("maxZombies", 0);
            int spawnCount = entry.getIntOr("spawnCount", 0);
            int killedCount = entry.getIntOr("killedCount", 0);
            PendingAirdropStorage.Pending p = new PendingAirdropStorage.Pending(waveStart, panelPos, itemIds, quantities, difficulty, player, storedAt, wavesRemaining, totalWaves, tier, maxZombies);
            p.currentWaveStartedAtGameTime = waveStartedAt;
            p.wavesRemaining = wavesRemaining;
            p.spawnCountThisWave = spawnCount;
            p.killedCountThisWave = killedCount;
            data.pending.put(waveStart.immutable(), p);
        }
        return data;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, PendingAirdropStorage.Pending> e : pending.entrySet()) {
            PendingAirdropStorage.Pending p = e.getValue();
            CompoundTag entry = new CompoundTag();
            entry.putIntArray("wavePos", new int[]{p.waveStartPos.getX(), p.waveStartPos.getY(), p.waveStartPos.getZ()});
            entry.putIntArray("panelPos", new int[]{p.panelPos.getX(), p.panelPos.getY(), p.panelPos.getZ()});
            ListTag ids = new ListTag();
            for (String s : p.itemIds) ids.add(net.minecraft.nbt.StringTag.valueOf(s));
            entry.put("itemIds", ids);
            ListTag qty = new ListTag();
            for (Integer n : p.quantities) qty.add(net.minecraft.nbt.IntTag.valueOf(n));
            entry.put("quantities", qty);
            entry.putInt("difficulty", p.difficulty);
            entry.store("player", UUIDUtil.CODEC, p.playerWhoStarted);
            entry.putLong("storedAt", p.storedAtGameTime);
            entry.putLong("waveStartedAt", p.currentWaveStartedAtGameTime);
            entry.putInt("wavesRemaining", p.wavesRemaining);
            entry.putInt("totalWaves", p.totalWaves);
            entry.putInt("tier", p.tierDifficulty);
            entry.putInt("maxZombies", p.maxZombiesPerWave);
            entry.putInt("spawnCount", p.spawnCountThisWave);
            entry.putInt("killedCount", p.killedCountThisWave);
            list.add(entry);
        }
        tag.put("pending", list);
        return tag;
    }

    public Map<BlockPos, PendingAirdropStorage.Pending> getPending() {
        return pending;
    }

    public static PendingAirdropSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
