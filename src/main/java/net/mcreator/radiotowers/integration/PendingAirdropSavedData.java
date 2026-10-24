package net.mcreator.radiotowers.integration;

import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.nbt.INBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists pending airdrops per level so the wave survives player leave/rejoin and server restart.
 */
public class PendingAirdropSavedData extends WorldSavedData {

    private static final String DATA_NAME = "radiotowers_pending_airdrops";

    private final Map<BlockPos, PendingAirdropStorage.Pending> pending = new HashMap<>();

    public PendingAirdropSavedData() {
        super(DATA_NAME);
    }

    @Override
    public void load(CompoundNBT tag) {
        pending.clear();
        pending.putAll(fromTag(tag).pending);
    }

    private static PendingAirdropSavedData fromTag(CompoundNBT tag) {
        PendingAirdropSavedData data = new PendingAirdropSavedData();
        ListNBT list = tag.getList("pending", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundNBT entry = list.getCompound(i);
            BlockPos waveStart = NBTUtil.readBlockPos(entry.getCompound("wavePos"));
            BlockPos panelPos = entry.contains("panelPos") ? NBTUtil.readBlockPos(entry.getCompound("panelPos")) : waveStart;
            List<String> itemIds = new ArrayList<>();
            ListNBT ids = entry.getList("itemIds", 8);
            for (int j = 0; j < ids.size(); j++) itemIds.add(ids.getString(j));
            List<Integer> quantities = new ArrayList<>();
            ListNBT qty = entry.getList("quantities", 3);
            for (int j = 0; j < qty.size(); j++) quantities.add(qty.getInt(j));
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
    public CompoundNBT save(CompoundNBT tag) {
        ListNBT list = new ListNBT();
        for (Map.Entry<BlockPos, PendingAirdropStorage.Pending> e : pending.entrySet()) {
            PendingAirdropStorage.Pending p = e.getValue();
            CompoundNBT entry = new CompoundNBT();
            entry.put("wavePos", NBTUtil.writeBlockPos(p.waveStartPos));
            entry.put("panelPos", NBTUtil.writeBlockPos(p.panelPos));
            ListNBT ids = new ListNBT();
            for (String s : p.itemIds) ids.add(net.minecraft.nbt.StringNBT.valueOf(s));
            entry.put("itemIds", ids);
            ListNBT qty = new ListNBT();
            for (Integer n : p.quantities) qty.add(net.minecraft.nbt.IntNBT.valueOf(n));
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

    public static PendingAirdropSavedData get(ServerWorld level) {
        return level.getDataStorage().computeIfAbsent(PendingAirdropSavedData::new, DATA_NAME);
    }
}
