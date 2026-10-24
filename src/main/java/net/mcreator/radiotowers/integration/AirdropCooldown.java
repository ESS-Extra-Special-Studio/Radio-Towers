package net.mcreator.radiotowers.integration;

import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.World;
import net.mcreator.radiotowers.config.AirdropConfig;

import java.util.UUID;

/** Per-player airdrop cooldown backed by {@link AirdropCooldownSavedData} (persists across sessions). */
public final class AirdropCooldown {

    private AirdropCooldown() {
    }

    /** Game time used for cooldowns (overworld clock when available). */
    private static long nowGameTime(ServerWorld level) {
        return storageLevel(level).getGameTime();
    }

    private static ServerWorld storageLevel(ServerWorld level) {
        if (level.getServer() == null) return level;
        ServerWorld overworld = level.getServer().getLevel(World.OVERWORLD);
        return overworld != null ? overworld : level;
    }

    /** True when both airdrop cooldown configs are zero (testing / no throttle). */
    public static boolean cooldownsDisabled() {
        return getStandardAirdropCooldownTicks() == 0 && getWaveDeliveryCooldownTicks() == 0;
    }

    public static boolean isOnCooldown(ServerWorld level, UUID playerUuid) {
        if (cooldownsDisabled()) {
            AirdropCooldownSavedData.get(level).clearPlayer(playerUuid);
            return false;
        }
        return AirdropCooldownSavedData.get(level).isOnCooldown(playerUuid, nowGameTime(level));
    }

    public static long getCooldownEndGameTime(ServerWorld level, UUID playerUuid) {
        if (cooldownsDisabled()) {
            AirdropCooldownSavedData.get(level).clearPlayer(playerUuid);
            return 0L;
        }
        return AirdropCooldownSavedData.get(level).getCooldownEndGameTime(playerUuid, nowGameTime(level));
    }

    public static void setCooldownAfterWaveDelivery(ServerWorld level, UUID playerUuid) {
        int ticks = getWaveDeliveryCooldownTicks();
        if (ticks <= 0) {
            AirdropCooldownSavedData.get(level).clearPlayer(playerUuid);
            return;
        }
        long end = nowGameTime(level) + ticks;
        AirdropCooldownSavedData.get(level).setCooldownEnd(playerUuid, end);
    }

    public static void setCooldownAfterStandardAirdrop(ServerWorld level, UUID playerUuid) {
        int ticks = getStandardAirdropCooldownTicks();
        if (ticks <= 0) {
            AirdropCooldownSavedData.get(level).clearPlayer(playerUuid);
            return;
        }
        long end = nowGameTime(level) + ticks;
        AirdropCooldownSavedData.get(level).setCooldownEnd(playerUuid, end);
    }

    /** @deprecated use {@link #setCooldownAfterWaveDelivery} or {@link #setCooldownAfterStandardAirdrop} */
    @Deprecated
    public static void setCooldown(ServerWorld level, UUID playerUuid) {
        setCooldownAfterWaveDelivery(level, playerUuid);
    }

    /** Drop all saved cooldown entries when config disables them. Call on server/world start. */
    public static void purgeAllIfDisabled(ServerWorld level) {
        if (cooldownsDisabled()) {
            AirdropCooldownSavedData.get(level).clearAll();
        }
    }

    public static int getWaveDeliveryCooldownTicks() {
        return AirdropConfig.getWaveDeliveryCooldownTicks();
    }

    public static int getStandardAirdropCooldownTicks() {
        return AirdropConfig.getStandardAirdropCooldownTicks();
    }
}
