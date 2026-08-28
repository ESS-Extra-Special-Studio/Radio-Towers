package net.mcreator.radiotowers.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import net.mcreator.radiotowers.entity.PlaneentityEntity;
import net.mcreator.radiotowers.init.RadiotowersModEntities;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;

import java.util.List;

public class RadioPanelOnBlockRightClickedProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z) {
		execute(world, x, y, z, null, null, 0);
	}

	public static void execute(LevelAccessor world, double x, double y, double z, List<String> itemIds, List<Integer> quantities) {
		execute(world, x, y, z, itemIds, quantities, 0);
	}

	public static void execute(LevelAccessor world, double x, double y, double z, List<String> itemIds, List<Integer> quantities, int totalDifficulty) {
		if (world instanceof ServerLevel _level) {
			// Clear any previous delivery orders so this airdrop crate gets only THIS request's selection, not a previous airdrop's
			PendingAirdropStorage.clearAllDeliveryOrders();
			Entity entityToSpawn = RadiotowersModEntities.PLANEENTITY.get().spawn(_level, BlockPos.containing(x, y, z), MobSpawnType.MOB_SUMMONED);
			if (entityToSpawn != null) {
				entityToSpawn.setYRot(world.getRandom().nextFloat() * 360F);
				if (entityToSpawn instanceof PlaneentityEntity plane) {
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