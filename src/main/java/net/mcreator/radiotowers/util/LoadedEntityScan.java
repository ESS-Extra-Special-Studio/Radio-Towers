package net.mcreator.radiotowers.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.function.Consumer;

/**
 * Iterate entities that are actually loaded. Do not use a world-sized AABB with
 * {@code getEntitiesOfClass}: vanilla walks every section coordinate in that box.
 */
public final class LoadedEntityScan {
	private LoadedEntityScan() {}

	public static <T extends Entity> void forEach(ServerLevel level, Class<T> type, Consumer<T> consumer) {
		for (Entity e : level.getAllEntities()) {
			if (type.isInstance(e) && !e.isRemoved()) {
				consumer.accept(type.cast(e));
			}
		}
	}
}
