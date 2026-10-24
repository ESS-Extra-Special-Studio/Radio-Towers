package net.mcreator.radiotowers.integration;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists per-player airdrop cooldown end times (world game time) so they survive disconnect, menu exit, and full restart.
 * Stored on the overworld for a single consistent {@link ServerLevel#getGameTime()} clock.
 */
public class AirdropCooldownSavedData extends SavedData {

    public static final String DATA_NAME = "radiotowers_airdrop_cooldowns";

    private final Map<UUID, Long> cooldownEndGameTimeByPlayer = new HashMap<>();

    public AirdropCooldownSavedData() {
    }

    public static final Codec<AirdropCooldownSavedData> CODEC = CompoundTag.CODEC.xmap(
        AirdropCooldownSavedData::fromTag, data -> data.toTag());
    public static final SavedDataType<AirdropCooldownSavedData> TYPE = new SavedDataType<>(
        DATA_NAME,
        context -> {
            CompoundTag legacy = LegacySavedDataFiles.readLegacy(context, DATA_NAME);
            AirdropCooldownSavedData data = legacy != null ? fromTag(legacy) : new AirdropCooldownSavedData();
            if (legacy != null) data.setDirty();
            return data;
        },
        context -> CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    public static AirdropCooldownSavedData fromTag(CompoundTag tag) {
        AirdropCooldownSavedData data = new AirdropCooldownSavedData();
        ListTag list = tag.getListOrEmpty("players");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompoundOrEmpty(i);
            c.read("player", UUIDUtil.CODEC).ifPresent(id -> {
                if (c.contains("end")) data.cooldownEndGameTimeByPlayer.put(id, c.getLongOr("end", 0L));
            });
        }
        return data;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Long> e : cooldownEndGameTimeByPlayer.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.store("player", UUIDUtil.CODEC, e.getKey());
            c.putLong("end", e.getValue());
            list.add(c);
        }
        tag.put("players", list);
        return tag;
    }

    /**
     * Saved data lives on the overworld so cooldown uses one game-time line across dimensions.
     */
    public static AirdropCooldownSavedData get(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            throw new IllegalStateException("AirdropCooldownSavedData.get requires a server level");
        }
        ServerLevel storage = level.getServer().getLevel(Level.OVERWORLD);
        if (storage == null) storage = level;
        return storage.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Game time when cooldown ends, or 0 if none / expired (expired entries are removed). */
    public long getCooldownEndGameTime(UUID player, long nowGameTime) {
        Long end = cooldownEndGameTimeByPlayer.get(player);
        if (end == null) return 0L;
        if (nowGameTime >= end) {
            cooldownEndGameTimeByPlayer.remove(player);
            setDirty();
            return 0L;
        }
        return end;
    }

    public boolean isOnCooldown(UUID player, long nowGameTime) {
        Long end = cooldownEndGameTimeByPlayer.get(player);
        if (end == null) return false;
        if (nowGameTime >= end) {
            cooldownEndGameTimeByPlayer.remove(player);
            setDirty();
            return false;
        }
        return true;
    }

    public void setCooldownEnd(UUID player, long endGameTime) {
        if (endGameTime <= 0) {
            clearPlayer(player);
            return;
        }
        cooldownEndGameTimeByPlayer.put(player, endGameTime);
        setDirty();
    }

    public void clearPlayer(UUID player) {
        if (player != null && cooldownEndGameTimeByPlayer.remove(player) != null) {
            setDirty();
        }
    }

    /** Wipe all persisted cooldowns (e.g. config set to zero for testing). */
    public void clearAll() {
        if (!cooldownEndGameTimeByPlayer.isEmpty()) {
            cooldownEndGameTimeByPlayer.clear();
            setDirty();
        }
    }
}
