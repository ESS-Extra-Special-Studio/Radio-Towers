package net.mcreator.radiotowers.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
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

    public static PendingAirdropSavedData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        PendingAirdropSavedData data = new PendingAirdropSavedData();
        ListTag list = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos waveStart = net.minecraft.core.BlockPos.of(entry.getLong("wavePos"));
            if (entry.contains("wavePos", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                int[] a = entry.getIntArray("wavePos");
                waveStart = new BlockPos(a[0], a[1], a[2]);
            } else if (entry.contains("wavePos", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                CompoundTag wp = entry.getCompound("wavePos");
                waveStart = new BlockPos(wp.getInt("X"), wp.getInt("Y"), wp.getInt("Z"));
            }
            BlockPos panelPos = waveStart;
            if (entry.contains("panelPos", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                int[] a = entry.getIntArray("panelPos");
                panelPos = new BlockPos(a[0], a[1], a[2]);
            } else if (entry.contains("panelPos", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                CompoundTag pp = entry.getCompound("panelPos");
                panelPos = new BlockPos(pp.getInt("X"), pp.getInt("Y"), pp.getInt("Z"));
            }
            List<String> itemIds = new ArrayList<>();
            ListTag ids = entry.getList("itemIds", Tag.TAG_STRING);
            for (int j = 0; j < ids.size(); j++) itemIds.add(ids.get(j).getAsString());
            List<Integer> quantities = new ArrayList<>();
            ListTag qty = entry.getList("quantities", Tag.TAG_INT);
            for (int j = 0; j < qty.size(); j++) quantities.add(((net.minecraft.nbt.NumericTag) qty.get(j)).getAsInt());
            int difficulty = entry.getInt("difficulty");
            UUID player = entry.hasUUID("player") ? entry.getUUID("player") : null;
            if (player == null) continue;
            long storedAt = entry.getLong("storedAt");
            long waveStartedAt = entry.getLong("waveStartedAt");
            int wavesRemaining = entry.getInt("wavesRemaining");
            int totalWaves = entry.getInt("totalWaves");
            int tier = entry.getInt("tier");
            int maxZombies = entry.getInt("maxZombies");
            int spawnCount = entry.getInt("spawnCount");
            int killedCount = entry.getInt("killedCount");
            PendingAirdropStorage.Pending p = new PendingAirdropStorage.Pending(waveStart, panelPos, itemIds, quantities, difficulty, player, storedAt, wavesRemaining, totalWaves, tier, maxZombies);
            p.currentWaveStartedAtGameTime = waveStartedAt;
            p.wavesRemaining = wavesRemaining;
            p.spawnCountThisWave = spawnCount;
            p.killedCountThisWave = killedCount;
            data.pending.put(waveStart.immutable(), p);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
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
            entry.putUUID("player", p.playerWhoStarted);
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
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PendingAirdropSavedData::new, PendingAirdropSavedData::load), DATA_NAME);
    }
}
