package net.mcreator.radiotowers.events;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;

import net.mcreator.radiotowers.RadiotowersMod;

import java.util.List;

/**
 * ZombieEntity attraction to active towers and players playing music.
 * Runs only when Dead Air is installed; uses Dead Air API for tower positions and "player playing music".
 * All airdrop/wave aggro (chase player who started) stays in WaveMobAggroHandler.
 */
@Mod.EventBusSubscriber(modid = RadiotowersMod.MODID)
public class ZombieAttractionHandler {

    private static final int ATTRACTION_RANGE = 100;
    private static final int CHECK_INTERVAL = 40;
    private static final double SPEED = 0.08 * 1.2;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.WorldTickEvent event) {
    ServerWorld level = (event.world instanceof ServerWorld) ? (ServerWorld) event.world : null;
        if (event.phase != TickEvent.Phase.END || event.world.isClientSide() || !(level != null)) return;
        if (!ModList.get().isLoaded("dead_air")) return;
        if (level.getGameTime() % CHECK_INTERVAL != 0) return;

        List<BlockPos> towerPositions = getActiveTowerPositionsFromDeadAir(level);
        for (BlockPos towerPos : towerPositions) {
            AxisAlignedBB box = new AxisAlignedBB(towerPos).inflate(ATTRACTION_RANGE);
            List<MobEntity> zombies = level.getEntitiesOfClass(MobEntity.class, box, ZombieAttractionHandler::isZombieType);
            Vector3d towerVec = Vector3d.atCenterOf(towerPos);
            for (MobEntity zombie : zombies) {
                if (zombie.getTarget() != null) continue;
                attractToward(zombie, towerVec);
            }
        }

        int playerRadius = 50;
        for (ServerPlayerEntity player : level.players()) {
            if (player == null || !player.isAlive()) continue;
            if (!isPlayerPlayingMusicFromDeadAir(player)) continue;
            Vector3d playerPos = player.position();
            AxisAlignedBB box = new AxisAlignedBB(playerPos, playerPos).inflate(playerRadius);
            List<MobEntity> zombies = level.getEntitiesOfClass(MobEntity.class, box, ZombieAttractionHandler::isZombieType);
            for (MobEntity zombie : zombies) {
                if (zombie.getTarget() != null) continue;
                if (zombie.distanceTo(player) > playerRadius) continue;
                attractToward(zombie, playerPos);
            }
        }
    }

    private static List<BlockPos> getActiveTowerPositionsFromDeadAir(ServerWorld level) {
        try {
            Class<?> api = Class.forName("uk.co.extraspecialstudio.dead_air.api.DeadAirAPI");
            java.lang.reflect.Method m = api.getMethod("getActiveTowerBlockPositions", ServerWorld.class);
            @SuppressWarnings("unchecked")
            List<BlockPos> list = (List<BlockPos>) m.invoke(null, level);
            return list != null ? list : java.util.Collections.emptyList();
        } catch (Throwable t) {
            return java.util.Collections.emptyList();
        }
    }

    private static boolean isPlayerPlayingMusicFromDeadAir(ServerPlayerEntity player) {
        try {
            Class<?> api = Class.forName("uk.co.extraspecialstudio.dead_air.api.DeadAirAPI");
            java.lang.reflect.Method m = api.getMethod("isPlayerPlayingMusic", net.minecraft.entity.player.PlayerEntity.class);
            return Boolean.TRUE.equals(m.invoke(null, player));
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isZombieType(MobEntity entity) {
        EntityType<?> type = entity.getType();
        return type == EntityType.ZOMBIE || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.HUSK
            || type == EntityType.DROWNED || type == EntityType.ZOMBIFIED_PIGLIN
            || type.getDescriptionId().toLowerCase(java.util.Locale.ROOT).contains("zombie");
    }

    private static void attractToward(MobEntity zombie, Vector3d target) {
        Vector3d pos = zombie.position();
        double dist = pos.distanceTo(target);
        if (dist < 5.0) return;
        Vector3d dir = target.subtract(pos).normalize();
        Vector3d horizontal = new Vector3d(dir.x, 0, dir.z);
        if (horizontal.lengthSqr() > 1.0E-6) horizontal = horizontal.normalize();
        Vector3d current = zombie.getDeltaMovement();
        zombie.setDeltaMovement(current.x + horizontal.x * SPEED, current.y, current.z + horizontal.z * SPEED);
        zombie.getLookControl().setLookAt(target.x, target.y, target.z, 30.0f, 30.0f);
    }
}
