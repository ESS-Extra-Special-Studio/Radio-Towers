package net.mcreator.radiotowers.procedures;

import net.minecraft.world.IWorld;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Entity;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.math.BlockPos;

import net.mcreator.radiotowers.entity.PlaneentityEntity;
import net.mcreator.radiotowers.init.RadiotowersModEntities;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;

import java.util.List;

public class RadioPanelOnBlockRightClickedProcedure {
	public static void execute(IWorld world, double x, double y, double z) {
		execute(world, x, y, z, null, null, 0);
	}

	public static void execute(IWorld world, double x, double y, double z, List<String> itemIds, List<Integer> quantities) {
		execute(world, x, y, z, itemIds, quantities, 0);
	}

	public static void execute(IWorld world, double x, double y, double z, List<String> itemIds, List<Integer> quantities, int totalDifficulty) {
	ServerWorld _level = (world instanceof ServerWorld) ? (ServerWorld) world : null;
		if (_level != null) {
			// Clear any previous delivery orders so this airdrop crate gets only THIS request's selection, not a previous airdrop's
			PendingAirdropStorage.clearAllDeliveryOrders();
			Entity entityToSpawn = RadiotowersModEntities.PLANEENTITY.get().spawn(_level, null, null, null, new BlockPos(x, y, z), SpawnReason.MOB_SUMMONED, false, false);
			if (entityToSpawn != null) {
				entityToSpawn.yRot = world.getRandom().nextFloat() * 360F;
				PlaneentityEntity plane = (entityToSpawn instanceof PlaneentityEntity) ? (PlaneentityEntity) entityToSpawn : null;
				if (plane != null) {
					if (itemIds != null && !itemIds.isEmpty()) {
						// Use defensive copies so this plane's order is only this selection (no shared refs with other requests)
						java.util.List<String> ids = new java.util.ArrayList<>(itemIds);
						java.util.List<Integer> qty = quantities != null ? new java.util.ArrayList<>(quantities) : new java.util.ArrayList<>();
						plane.setAirdropOrder(ids, qty);
						PendingAirdropStorage.putDeliveryOrderForPlane(plane.getUUID(), ids, qty);
					}
					plane.setAirdropDifficulty(totalDifficulty);
				}
			}
		}
	}
}