package net.mcreator.radiotowers.integration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists pending airdrops per level so the wave survives player leave/rejoin and server restart.
 */
public class PendingAirdropSavedData extends SavedData {

    private record Entry(List<Integer> wavePos, List<Integer> panelPos, List<String> itemIds, List<Integer> quantities,
                         int difficulty, String player, long storedAt, long waveStartedAt, int wavesRemaining,
                         int totalWaves, int tier, int maxZombies, int spawnCount, int killedCount) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.listOf().fieldOf("wavePos").forGetter(Entry::wavePos),
            Codec.INT.listOf().fieldOf("panelPos").forGetter(Entry::panelPos),
            Codec.STRING.listOf().fieldOf("itemIds").forGetter(Entry::itemIds),
            Codec.INT.listOf().fieldOf("quantities").forGetter(Entry::quantities),
            Codec.INT.fieldOf("difficulty").forGetter(Entry::difficulty),
            Codec.STRING.fieldOf("player").forGetter(Entry::player),
            Codec.LONG.fieldOf("storedAt").forGetter(Entry::storedAt),
            Codec.LONG.fieldOf("waveStartedAt").forGetter(Entry::waveStartedAt),
            Codec.INT.fieldOf("wavesRemaining").forGetter(Entry::wavesRemaining),
            Codec.INT.fieldOf("totalWaves").forGetter(Entry::totalWaves),
            Codec.INT.fieldOf("tier").forGetter(Entry::tier),
            Codec.INT.fieldOf("maxZombies").forGetter(Entry::maxZombies),
            Codec.INT.fieldOf("spawnCount").forGetter(Entry::spawnCount),
            Codec.INT.fieldOf("killedCount").forGetter(Entry::killedCount)
        ).apply(i, Entry::new));
    }

    public static final Codec<PendingAirdropSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Entry.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(PendingAirdropSavedData::toEntries)
    ).apply(i, PendingAirdropSavedData::fromEntries));

    public static final SavedDataType<PendingAirdropSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("radiotowers", "pending_airdrops"), () -> new PendingAirdropSavedData(), CODEC);

    private final Map<BlockPos, PendingAirdropStorage.Pending> pending = new HashMap<>();

    public PendingAirdropSavedData() {
        super();
    }

    private static PendingAirdropSavedData fromEntries(List<Entry> entries) {
        PendingAirdropSavedData data = new PendingAirdropSavedData();
        for (Entry entry : entries) {
            if (entry.wavePos().size() < 3 || entry.player() == null || entry.player().isEmpty()) continue;
            UUID player;
            try {
                player = UUID.fromString(entry.player());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            BlockPos waveStart = new BlockPos(entry.wavePos().get(0), entry.wavePos().get(1), entry.wavePos().get(2));
            BlockPos panelPos = entry.panelPos().size() >= 3
                ? new BlockPos(entry.panelPos().get(0), entry.panelPos().get(1), entry.panelPos().get(2))
                : waveStart;
            PendingAirdropStorage.Pending p = new PendingAirdropStorage.Pending(
                waveStart, panelPos, new ArrayList<>(entry.itemIds()), new ArrayList<>(entry.quantities()),
                entry.difficulty(), player, entry.storedAt(), entry.wavesRemaining(), entry.totalWaves(), entry.tier(), entry.maxZombies());
            p.currentWaveStartedAtGameTime = entry.waveStartedAt();
            p.wavesRemaining = entry.wavesRemaining();
            p.spawnCountThisWave = entry.spawnCount();
            p.killedCountThisWave = entry.killedCount();
            data.pending.put(waveStart.immutable(), p);
        }
        return data;
    }

    private List<Entry> toEntries() {
        List<Entry> list = new ArrayList<>();
        for (PendingAirdropStorage.Pending p : pending.values()) {
            list.add(new Entry(
                List.of(p.waveStartPos.getX(), p.waveStartPos.getY(), p.waveStartPos.getZ()),
                List.of(p.panelPos.getX(), p.panelPos.getY(), p.panelPos.getZ()),
                List.copyOf(p.itemIds), List.copyOf(p.quantities),
                p.difficulty, p.playerWhoStarted.toString(), p.storedAtGameTime, p.currentWaveStartedAtGameTime,
                p.wavesRemaining, p.totalWaves, p.tierDifficulty, p.maxZombiesPerWave,
                p.spawnCountThisWave, p.killedCountThisWave));
        }
        return list;
    }

    public Map<BlockPos, PendingAirdropStorage.Pending> getPending() {
        return pending;
    }

    public static PendingAirdropSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
