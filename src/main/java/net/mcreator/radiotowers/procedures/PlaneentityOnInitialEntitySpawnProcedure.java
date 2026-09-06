package net.mcreator.radiotowers.procedures;

import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

import net.mcreator.radiotowers.network.RadiotowersModVariables;
import net.mcreator.radiotowers.init.RadiotowersModEntities;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.RadiotowersMod;

import java.util.ArrayList;
import java.util.List;

public class PlaneentityOnInitialEntitySpawnProcedure {
	/** Slots per crate; we drop one crate per chunk so each has at most this many item types. */
	private static final int SLOTS_PER_CRATE = 27;
	/** Blocks between each crate when multiple crates are dropped in a line by the tower. */
	private static final int DROP_LINE_SPACING = 4;
	/** Plane horizontal speed (blocks per tick) so it's visible coming in; ~4.4 blocks/sec. */
	private static final double PLANE_SPEED = 0.22;
	/** Minimum horizontal distance from tower center for airdrop drop — never drop straight on the tower. */
	private static final double MIN_DROP_OFFSET_FROM_TOWER = 14.0;
	/** Drop point offset behind the plane so crate appears to exit the rear, not the nose. */
	private static final double DROP_OFFSET_BEHIND_PLANE = 2.8;
	/** Minimum blocks above the tower/panel the plane must spawn at so it never appears to fly through the tower. */
	private static final int MIN_BLOCKS_ABOVE_TOWER = 10;

	public static void execute(LevelAccessor world, double x, double z, Entity entity) {
		if (entity == null)
			return;
		entity.setNoGravity(true);
		RadiotowersModVariables.MapVariables vars = RadiotowersModVariables.MapVariables.get(world);
		double range = vars.PlaneSpawnRange;
		double heightMin = vars.PlaneSpawnHeight - vars.PlaneSpawnHeightDownRangeMax;
		double heightMax = vars.PlaneSpawnHeight + vars.PlaneSpawnHeightUpRangeMax;
		// Cap horizontal range by server view distance so plane always spawns within visible range
		if (world instanceof ServerLevel _level && _level.getServer() != null) {
			int viewChunks = _level.getServer().getPlayerList().getViewDistance();
			double maxRangeBlocks = (viewChunks * 16) * 0.45; // stay within ~45% of render distance
			if (range > maxRangeBlocks) range = Math.max(16, maxRangeBlocks);
		}
		double spawnX = Mth.nextDouble(RandomSource.create(), x - range, x + range);
		double spawnY = Mth.nextDouble(RandomSource.create(), heightMin, heightMax);
		double spawnZ = Mth.nextDouble(RandomSource.create(), z - range, z + range);
		// Ensure plane never flies through tower: at least MIN_BLOCKS_ABOVE_TOWER above tower/panel, and at least 25 above terrain, and at least 120 absolute
		if (world instanceof ServerLevel _level) {
			int groundY = _level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
			double minYAboveTower = entity.getY() + MIN_BLOCKS_ABOVE_TOWER;
			double minY = Math.max(120, Math.max(groundY + 25, minYAboveTower));
			if (spawnY < minY) spawnY = minY;
		}
		{
			Entity _ent = entity;
			_ent.teleportTo(spawnX, spawnY, spawnZ);
			if (_ent instanceof ServerPlayer _serverPlayer)
				_serverPlayer.connection.teleport(spawnX, spawnY, spawnZ, _ent.getYRot(), _ent.getXRot());
		}
		// Fly toward the tower so the plane is visible coming in (slower speed so you can see it)
		double dx = x - spawnX;
		double dz = z - spawnZ;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len > 0.01) {
			entity.setDeltaMovement((dx / len) * PLANE_SPEED, 0, (dz / len) * PLANE_SPEED);
		}
		if (world instanceof Level _level) {
			if (!_level.isClientSide()) {
				_level.playSound(null, BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("radiotowers:planeflyby")), SoundSource.AMBIENT, 200, 1);
			} else {
				_level.playLocalSound((entity.getX()), (entity.getY()), (entity.getZ()), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("radiotowers:planeflyby")), SoundSource.AMBIENT, 200, 1, false);
			}
		}
		RadiotowersMod.queueServerWork(65, () -> {
			try {
				if (!(world instanceof ServerLevel _level)) return;
				if (entity.isRemoved() || entity.level() == null) return;
				double planeX = entity.getX();
				double planeY = entity.getY();
				double planeZ = entity.getZ();
				double mvx = entity.getDeltaMovement().x;
				double mvz = entity.getDeltaMovement().z;
				double mlen = Math.sqrt(mvx * mvx + mvz * mvz);
				if (mlen < 0.001) {
					// Fallback to facing direction if movement vector is too small this tick.
					float yawRad = entity.getYRot() * ((float) Math.PI / 180F);
					mvx = -Mth.sin(yawRad);
					mvz = Mth.cos(yawRad);
					mlen = Math.sqrt(mvx * mvx + mvz * mvz);
				}
				double forwardX = mlen > 0.001 ? (mvx / mlen) : 0.0;
				double forwardZ = mlen > 0.001 ? (mvz / mlen) : 1.0;
				double dropX = planeX - forwardX * DROP_OFFSET_BEHIND_PLANE;
				double dropZ = planeZ - forwardZ * DROP_OFFSET_BEHIND_PLANE;
				double distToTower = Math.sqrt((dropX - x) * (dropX - x) + (dropZ - z) * (dropZ - z));
				if (distToTower < MIN_DROP_OFFSET_FROM_TOWER && distToTower > 0.01) {
					double scale = MIN_DROP_OFFSET_FROM_TOWER / distToTower;
					dropX = x + (dropX - x) * scale;
					dropZ = z + (dropZ - z) * scale;
				} else if (distToTower <= 0.01) {
					// Plane is directly over tower: drop at random direction, min distance from tower so crate never lands on top
					double angle = RandomSource.create().nextDouble() * 2 * Math.PI;
					dropX = x + Math.cos(angle) * MIN_DROP_OFFSET_FROM_TOWER;
					dropZ = z + Math.sin(angle) * MIN_DROP_OFFSET_FROM_TOWER;
				}
				Entity entityToSpawn = RadiotowersModEntities.AIRDROPENTITY.get().spawn(_level, BlockPos.containing(dropX, planeY, dropZ), MobSpawnType.MOB_SUMMONED);
				if (entityToSpawn != null) {
					entityToSpawn.setYRot(entity.getYRot());
					entityToSpawn.setYBodyRot(entity.getYRot());
					entityToSpawn.setYHeadRot(entity.getYRot());
					// Fall straight down from the plane; give an initial descent so the crate cannot hang at drop height
					entityToSpawn.setNoGravity(false);
					entityToSpawn.setDeltaMovement(0, -0.25, 0);
					entityToSpawn.hasImpulse = true;
					if (entity instanceof net.mcreator.radiotowers.entity.PlaneentityEntity plane
							&& entityToSpawn instanceof net.mcreator.radiotowers.entity.AirdropentityEntity airdrop) {
						// Order: plane UUID map -> dimension "next" fallback -> plane's own data (set in deliverAt after spawn)
						var order = PendingAirdropStorage.takeDeliveryOrderForPlane(plane.getUUID());
						if (order == null && _level != null)
							order = PendingAirdropStorage.takeNextDeliveryOrder(_level.dimension());
						if (order != null && !order.itemIds.isEmpty()) {
							airdrop.setAirdropOrder(order.itemIds, order.quantities);
						} else if (plane.hasAirdropOrder()) {
							airdrop.setAirdropOrder(plane.getAirdropItemIds(), plane.getAirdropQuantities());
						} else {
							airdrop.setAirdropOrder(java.util.Collections.emptyList(), java.util.Collections.emptyList());
						}
						airdrop.setAirdropDifficulty(plane.getAirdropDifficulty());
					}
				}
			} catch (Exception e) {
				RadiotowersMod.LOGGER.warn("Airdrop 65-tick callback failed: {}", e.getMessage());
			}
			RadiotowersMod.queueServerWork(100, () -> {
				try {
					if (entity != null && !entity.isRemoved() && entity.level() != null && !entity.level().isClientSide())
						entity.discard();
				} catch (Exception ignored) {}
			});
		});
	}
}