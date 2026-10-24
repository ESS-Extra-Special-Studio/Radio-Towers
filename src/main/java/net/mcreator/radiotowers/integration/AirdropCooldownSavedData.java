package net.mcreator.radiotowers.integration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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

    public static final Codec<AirdropCooldownSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("players", Map.of()).forGetter(AirdropCooldownSavedData::encodedPlayers)
    ).apply(i, AirdropCooldownSavedData::fromEncoded));

    public static final SavedDataType<AirdropCooldownSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("radiotowers", "airdrop_cooldowns"), () -> new AirdropCooldownSavedData(), CODEC);

    private final Map<UUID, Long> cooldownEndGameTimeByPlayer = new HashMap<>();

    public AirdropCooldownSavedData() {
    }

    private AirdropCooldownSavedData(Map<String, Long> encoded) {
        encoded.forEach((id, end) -> {
            try {
                cooldownEndGameTimeByPlayer.put(UUID.fromString(id), end);
            } catch (IllegalArgumentException ignored) {}
        });
    }

    private static AirdropCooldownSavedData fromEncoded(Map<String, Long> encoded) {
        return new AirdropCooldownSavedData(encoded);
    }

    private Map<String, Long> encodedPlayers() {
        Map<String, Long> out = new HashMap<>();
        cooldownEndGameTimeByPlayer.forEach((id, end) -> out.put(id.toString(), end));
        return out;
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
