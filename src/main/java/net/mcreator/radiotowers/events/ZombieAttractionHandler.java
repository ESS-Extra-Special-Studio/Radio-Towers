package net.mcreator.radiotowers.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;

import net.mcreator.radiotowers.RadiotowersMod;

import java.util.List;

/**
 * Zombie attraction to active towers and players playing music.
 * Runs only when Dead Air is installed; uses Dead Air API for tower positions and "player playing music".
 * All airdrop/wave aggro (chase player who started) stays in WaveMobAggroHandler.
 */
@Mod.EventBusSubscriber(modid = RadiotowersMod.MODID)
public class ZombieAttractionHandler {

    private static final int ATTRACTION_RANGE = 100;
    private static final int CHECK_INTERVAL = 40;
    private static final double SPEED = 0.08 * 1.2;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide() || !(event.level instanceof ServerLevel level)) return;
        if (!ModList.get().isLoaded("dead_air")) return;
        if (level.getGameTime() % CHECK_INTERVAL != 0) return;

        List<BlockPos> towerPositions = getActiveTowerPositionsFromDeadAir(level);
        for (BlockPos towerPos : towerPositions) {
            AABB box = new AABB(towerPos).inflate(ATTRACTION_RANGE);
            List<Mob> zombies = level.getEntitiesOfClass(Mob.class, box, ZombieAttractionHandler::isZombieType);
            Vec3 towerVec = Vec3.atCenterOf(towerPos);
            for (Mob zombie : zombies) {
                if (zombie.getTarget() != null) continue;
                attractToward(zombie, towerVec);
            }
        }

        int playerRadius = 50;
        for (ServerPlayer player : level.players()) {
            if (player == null || !player.isAlive()) continue;
            if (!isPlayerPlayingMusicFromDeadAir(player)) continue;
            Vec3 playerPos = player.position();
            AABB box = new AABB(playerPos, playerPos).inflate(playerRadius);
            List<Mob> zombies = level.getEntitiesOfClass(Mob.class, box, ZombieAttractionHandler::isZombieType);
            for (Mob zombie : zombies) {
                if (zombie.getTarget() != null) continue;
                if (zombie.distanceTo(player) > playerRadius) continue;
                attractToward(zombie, playerPos);
            }
        }
    }

    private static List<BlockPos> getActiveTowerPositionsFromDeadAir(ServerLevel level) {
        try {
            Class<?> api = Class.forName("uk.co.extraspecialstudio.dead_air.api.DeadAirAPI");
            java.lang.reflect.Method m = api.getMethod("getActiveTowerBlockPositions", ServerLevel.class);
            @SuppressWarnings("unchecked")
            List<BlockPos> list = (List<BlockPos>) m.invoke(null, level);
            return list != null ? list : List.of();
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static boolean isPlayerPlayingMusicFromDeadAir(ServerPlayer player) {
        try {
            Class<?> api = Class.forName("uk.co.extraspecialstudio.dead_air.api.DeadAirAPI");
            java.lang.reflect.Method m = api.getMethod("isPlayerPlayingMusic", net.minecraft.world.entity.player.Player.class);
            return Boolean.TRUE.equals(m.invoke(null, player));
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isZombieType(Mob entity) {
        EntityType<?> type = entity.getType();
        return type == EntityType.ZOMBIE || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.HUSK
            || type == EntityType.DROWNED || type == EntityType.ZOMBIFIED_PIGLIN
            || type.getDescriptionId().toLowerCase(java.util.Locale.ROOT).contains("zombie");
    }

    private static void attractToward(Mob zombie, Vec3 target) {
        Vec3 pos = zombie.position();
        double dist = pos.distanceTo(target);
        if (dist < 5.0) return;
        Vec3 dir = target.subtract(pos).normalize();
        Vec3 horizontal = new Vec3(dir.x, 0, dir.z);
        if (horizontal.lengthSqr() > 1.0E-6) horizontal = horizontal.normalize();
        Vec3 current = zombie.getDeltaMovement();
        zombie.setDeltaMovement(current.x + horizontal.x * SPEED, current.y, current.z + horizontal.z * SPEED);
        zombie.getLookControl().setLookAt(target.x, target.y, target.z, 30.0f, 30.0f);
    }
}
