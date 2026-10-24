package net.mcreator.radiotowers.procedures;

import net.minecraftforge.registries.ForgeRegistries;

import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.SoundCategory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.Heightmap;

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

	public static void execute(IWorld world, double x, double z, Entity entity) {
		if (entity == null)
			return;
		entity.setNoGravity(true);
		RadiotowersModVariables.MapVariables vars = RadiotowersModVariables.MapVariables.get(world);
		double range = vars.PlaneSpawnRange;
		double heightMin = vars.PlaneSpawnHeight - vars.PlaneSpawnHeightDownRangeMax;
		double heightMax = vars.PlaneSpawnHeight + vars.PlaneSpawnHeightUpRangeMax;
		ServerWorld _level = (world instanceof ServerWorld) ? (ServerWorld) world : null;
		// Cap horizontal range by server view distance so plane always spawns within visible range
		if (_level != null && _level.getServer() != null) {
			int viewChunks = _level.getServer().getPlayerList().getViewDistance();
			double maxRangeBlocks = (viewChunks * 16) * 0.45; // stay within ~45% of render distance
			if (range > maxRangeBlocks) range = Math.max(16, maxRangeBlocks);
		}
		double spawnX = MathHelper.nextDouble(new java.util.Random(), x - range, x + range);
		double spawnY = MathHelper.nextDouble(new java.util.Random(), heightMin, heightMax);
		double spawnZ = MathHelper.nextDouble(new java.util.Random(), z - range, z + range);
		// Ensure plane never flies through tower: at least MIN_BLOCKS_ABOVE_TOWER above tower/panel, and at least 25 above terrain, and at least 120 absolute
		if (_level != null) {
			int groundY = _level.getHeight(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
			double minYAboveTower = entity.getY() + MIN_BLOCKS_ABOVE_TOWER;
			double minY = Math.max(120, Math.max(groundY + 25, minYAboveTower));
			if (spawnY < minY) spawnY = minY;
		}
		{
			Entity _ent = entity;
			_ent.teleportTo(spawnX, spawnY, spawnZ);
			ServerPlayerEntity _serverPlayer = (_ent instanceof ServerPlayerEntity) ? (ServerPlayerEntity) _ent : null;
			if (_serverPlayer != null)
				_serverPlayer.connection.teleport(spawnX, spawnY, spawnZ, _ent.yRot, _ent.xRot);
		}
		// Fly toward the tower so the plane is visible coming in (slower speed so you can see it)
		double dx = x - spawnX;
		double dz = z - spawnZ;
		double len = Math.sqrt(dx * dx + dz * dz);
		World soundLevel = (world instanceof World) ? (World) world : null;
		if (len > 0.01) {
			entity.setDeltaMovement((dx / len) * PLANE_SPEED, 0, (dz / len) * PLANE_SPEED);
		}
		if (soundLevel != null) {
			if (!soundLevel.isClientSide()) {
				soundLevel.playSound(null, new BlockPos(entity.getX(), entity.getY(), entity.getZ()), ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("radiotowers:planeflyby")), SoundCategory.AMBIENT, 200, 1);
			} else {
				soundLevel.playLocalSound((entity.getX()), (entity.getY()), (entity.getZ()), ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("radiotowers:planeflyby")), SoundCategory.AMBIENT, 200, 1, false);
			}
		}
		RadiotowersMod.queueServerWork(65, () -> {
			try {
			ServerWorld serverLevel = (world instanceof ServerWorld) ? (ServerWorld) world : null;
				if (!(serverLevel != null)) return;
				if (entity.removed || entity.level == null) return;
				double planeX = entity.getX();
				double planeY = entity.getY();
				double planeZ = entity.getZ();
				double mvx = entity.getDeltaMovement().x;
				double mvz = entity.getDeltaMovement().z;
				double mlen = Math.sqrt(mvx * mvx + mvz * mvz);
				if (mlen < 0.001) {
					// Fallback to facing direction if movement vector is too small this tick.
					float yawRad = entity.yRot * ((float) Math.PI / 180F);
					mvx = -MathHelper.sin(yawRad);
					mvz = MathHelper.cos(yawRad);
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
					double angle = new java.util.Random().nextDouble() * 2 * Math.PI;
					dropX = x + Math.cos(angle) * MIN_DROP_OFFSET_FROM_TOWER;
					dropZ = z + Math.sin(angle) * MIN_DROP_OFFSET_FROM_TOWER;
				}
				Entity entityToSpawn = RadiotowersModEntities.AIRDROPENTITY.get().spawn(serverLevel, null, null, null, new BlockPos(dropX, planeY, dropZ), SpawnReason.MOB_SUMMONED, false, false);
				if (entityToSpawn != null) {
					entityToSpawn.yRot = entity.yRot;
					entityToSpawn.setYBodyRot(entity.yRot);
					entityToSpawn.setYHeadRot(entity.yRot);
					// Fall straight down from the plane; give an initial descent so the crate cannot hang at drop height
					entityToSpawn.setNoGravity(false);
					entityToSpawn.setDeltaMovement(0, -0.25, 0);
					entityToSpawn.hasImpulse = true;
					net.mcreator.radiotowers.entity.AirdropentityEntity airdrop = (entityToSpawn instanceof net.mcreator.radiotowers.entity.AirdropentityEntity) ? (net.mcreator.radiotowers.entity.AirdropentityEntity) entityToSpawn : null;
					net.mcreator.radiotowers.entity.PlaneentityEntity plane = (entity instanceof net.mcreator.radiotowers.entity.PlaneentityEntity) ? (net.mcreator.radiotowers.entity.PlaneentityEntity) entity : null;
					if (plane != null
							&& airdrop != null) {
						// Order: plane UUID map -> dimension "next" fallback -> plane's own data (set in deliverAt after spawn)
						net.mcreator.radiotowers.integration.PendingAirdropStorage.DeliveryOrderSnapshot order = PendingAirdropStorage.takeDeliveryOrderForPlane(plane.getUUID());
						if (order == null && serverLevel != null)
							order = PendingAirdropStorage.takeNextDeliveryOrder(serverLevel.dimension());
						if (order != null && !order.itemIds.isEmpty()) {
							airdrop.setAirdropOrder(order.itemIds, order.quantities);
						} else if (plane.hasAirdropOrder()) {
							airdrop.setAirdropOrder(plane.getAirdropItemIds(), plane.getAirdropQuantities());
						} else {
							airdrop.setAirdropOrder(java.util.Collections.emptyList(), java.util.Collections.emptyList());
						}
						airdrop.setAirdropDifficulty(plane.getAirdropDifficulty());
						net.mcreator.radiotowers.integration.PendingAirdropStorage.DeliveryMembersSnapshot members = PendingAirdropStorage.takeNextDeliveryMembers(serverLevel.dimension());
						if (members != null) {
							airdrop.setLobbyMembers(members.members, members.membersOnly);
						}
					}
				}
			} catch (Exception e) {
				RadiotowersMod.LOGGER.warn("Airdrop 65-tick callback failed: {}", e.getMessage());
			}
			RadiotowersMod.queueServerWork(100, () -> {
				try {
					if (entity != null && !entity.removed && entity.level != null && !entity.level.isClientSide())
						entity.remove();
				} catch (Exception ignored) {}
			});
		});
	}
}