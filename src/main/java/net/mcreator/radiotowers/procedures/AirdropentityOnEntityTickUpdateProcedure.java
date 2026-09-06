package net.mcreator.radiotowers.procedures;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.core.registries.BuiltInRegistries;

import net.mcreator.radiotowers.airdrop.AirdropCatalog;
import net.mcreator.radiotowers.airdrop.TaczAirdropIntegration;
import net.mcreator.radiotowers.entity.AirdropentityEntity;
import net.mcreator.radiotowers.block.entity.AirdropCrateBlockEntity;
import net.mcreator.radiotowers.init.RadiotowersModBlocks;

import java.util.List;

public class AirdropentityOnEntityTickUpdateProcedure {

	/** Fill crate at pos from order next tick so the block entity exists. */
	private static void fillCrateFromOrder(ServerLevel level, BlockPos pos, List<String> ids, List<Integer> qty) {
		try {
			BlockEntity be = level.getBlockEntity(pos);
			if (!(be instanceof AirdropCrateBlockEntity crate)) return;
			if (crate.isStructureLoot()) return;
			for (int i = 0; i < crate.getContainerSize(); i++)
				crate.setItem(i, ItemStack.EMPTY);
			if (ids != null && !ids.isEmpty()) {
				int slot = 0;
				int crateSlots = crate.getContainerSize();
				for (int i = 0; i < ids.size() && slot < crateSlots; i++) {
					try {
						String idStr = ids.get(i);
						if (idStr == null || idStr.isEmpty()) continue;
						ResourceLocation id = ResourceLocation.parse(idStr);
						ItemStack stack = ItemStack.EMPTY;
						int unitsPerStack = 1;
						String path = id.getPath();
						if (TaczAirdropIntegration.isTaczContentId(id) && path.startsWith("ammo/"))
							unitsPerStack = TaczAirdropIntegration.getRoundsPerFullStackForAmmoContentId(id);
						// TaCZ ammo: try creative cache (exact tab stack) then API (reflection) then raw NBT so client shows caliber
						if (TaczAirdropIntegration.isTaczContentId(id)) {
							if (path.startsWith("ammo/")) {
								ItemStack cached = TaczAirdropIntegration.getCachedCreativeStack(id);
								if (cached != null && !cached.isEmpty() && TaczAirdropIntegration.hasAmmoIdTag(cached))
									stack = cached;
								if (stack.isEmpty())
									stack = TaczAirdropIntegration.createAmmoDisplayStack(id);
								if (stack.isEmpty() || (TaczAirdropIntegration.isAmmoBoxStack(stack) && !TaczAirdropIntegration.hasAmmoIdTag(stack))) {
									for (var e : AirdropCatalog.getEntries()) {
										if (e.getItemId().equals(id)) {
											stack = e.createStack();
											if (!stack.isEmpty() && TaczAirdropIntegration.isAmmoBoxStack(stack) && !TaczAirdropIntegration.hasAmmoIdTag(stack))
												stack = ItemStack.EMPTY;
											else if (!stack.isEmpty()) break;
										}
									}
								}
								if (stack.isEmpty()) stack = TaczAirdropIntegration.createAmmoStackForCrateRawNbt(id);
								if (!stack.isEmpty() && TaczAirdropIntegration.isAmmoBoxStack(stack) && !TaczAirdropIntegration.hasAmmoIdTag(stack))
									stack = ItemStack.EMPTY;
							} else if (path.startsWith("gun/")) {
								ItemStack cached = TaczAirdropIntegration.getCachedCreativeStack(id);
								if (cached != null && !cached.isEmpty() && TaczAirdropIntegration.hasGunIdTag(cached))
									stack = cached;
								if (stack.isEmpty()) {
									for (var e : AirdropCatalog.getEntries()) {
										if (e.getItemId().equals(id)) {
											stack = e.createStack();
											if (!stack.isEmpty()) break;
										}
									}
								}
								if (!stack.isEmpty() && !TaczAirdropIntegration.hasGunIdTag(stack))
									stack = ItemStack.EMPTY;
								if (stack.isEmpty()) stack = TaczAirdropIntegration.createGunDisplayStackWithAlternates(id);
								if (!stack.isEmpty() && !TaczAirdropIntegration.hasGunIdTag(stack))
									stack = ItemStack.EMPTY;
								if (stack.isEmpty()) stack = TaczAirdropIntegration.createGunStackForCrateRawNbt(id);
								unitsPerStack = 1;
							}
						}
						if (stack.isEmpty()) {
							if (TaczAirdropIntegration.isTaczContentId(id) && (path.startsWith("ammo/") || path.startsWith("gun/"))) {
								// Never use registry lookup for tacz content IDs (no such registry key); skip to avoid iron ammo box
							} else {
								var item = BuiltInRegistries.ITEM.get(id);
								if (item != null && item != Items.AIR) {
									stack = new ItemStack(item);
								} else {
									for (var e : AirdropCatalog.getEntries()) {
										if (e.getItemId().equals(id)) {
											stack = e.createStack();
											unitsPerStack = e.getDefaultStackSize();
											break;
										}
									}
								}
							}
						}
						if (stack.isEmpty()) continue;
						int orderQty = Math.max(1, (i < qty.size() && qty.get(i) != null) ? qty.get(i) : 1);
						if (TaczAirdropIntegration.isTaczContentId(id) && path.startsWith("ammo/")
							&& TaczAirdropIntegration.hasAmmoIdTag(stack)) {
							int rpb = TaczAirdropIntegration.getRoundsPerFullStackForAmmoContentId(id);
							int remainingRounds = orderQty * rpb;
							while (remainingRounds > 0 && slot < crateSlots) {
								int n = Math.min(remainingRounds, rpb);
								ItemStack toPut = stack.copy();
								if (TaczAirdropIntegration.isAmmoBoxStack(toPut)) {
									toPut.setCount(1);
									TaczAirdropIntegration.applyAmmoRoundCount(toPut, n);
								} else {
									int cap = TaczAirdropIntegration.getForgeMaxStackSize(toPut);
									int put = Math.min(n, Math.max(1, cap));
									toPut.setCount(put);
									net.mcreator.radiotowers.ItemCustomNbt.update(toPut, t -> t.remove("AmmoCount"));
								}
								crate.setItem(slot, toPut);
								slot++;
								remainingRounds -= n;
							}
							continue;
						}
						int remaining = orderQty * unitsPerStack;
						int maxStack = TaczAirdropIntegration.getForgeMaxStackSize(stack);
						while (remaining > 0 && slot < crateSlots) {
							int put = Math.min(remaining, maxStack);
							ItemStack toPut = stack.copy();
							toPut.setCount(put);
							crate.setItem(slot, toPut);
							slot++;
							remaining -= put;
						}
					} catch (Exception ignored) {}
				}
			}
			crate.setFilledFromOrder(true);
			crate.setChanged();
		} catch (Exception e) {
			net.mcreator.radiotowers.RadiotowersMod.LOGGER.warn("Airdrop crate fill failed: {}", e.getMessage());
		}
	}

	private static boolean touchesWater(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (!entity.isAlive() || entity.isRemoved()) return false;
		if (entity.isInWater()) return true;
		BlockPos pos = BlockPos.containing(x, y, z);
		if (world.getFluidState(pos).is(FluidTags.WATER)) return true;
		if (world.getFluidState(pos.below()).is(FluidTags.WATER)) return true;
		return world.getFluidState(pos.above()).is(FluidTags.WATER);
	}

	/** Block position for the crate (one block above first solid column below the entity). */
	private static BlockPos findSeafloorCratePos(LevelAccessor world, double x, double y, double z) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		int startY = Mth.floor(y);
		int minY = world.getMinBuildHeight();
		for (int yy = startY; yy >= minY; yy--) {
			BlockPos p = new BlockPos(bx, yy, bz);
			BlockState st = world.getBlockState(p);
			if (st.blocksMotion())
				return p.above();
		}
		return null;
	}

	private static BlockPos resolveDeliveryCratePos(LevelAccessor world, BlockPos preferred) {
		if (world.isClientSide()) return preferred;
		BlockEntity be = world.getBlockEntity(preferred);
		if (!(be instanceof AirdropCrateBlockEntity crate) || !crate.isStructureLoot())
			return preferred;
		for (Direction dir : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP }) {
			BlockPos alt = preferred.relative(dir);
			BlockEntity altBe = world.getBlockEntity(alt);
			if (altBe instanceof AirdropCrateBlockEntity altCrate && altCrate.isStructureLoot())
				continue;
			if (world.isEmptyBlock(alt) || world.getBlockState(alt).canBeReplaced())
				return alt;
		}
		return preferred;
	}

	private static void placeCrateAndDiscard(LevelAccessor world, Entity entity, BlockPos cratePos) {
		BlockPos deliveryPos = resolveDeliveryCratePos(world, cratePos);
		world.setBlock(deliveryPos, RadiotowersModBlocks.AIRDROP_CRATE.get().defaultBlockState(), 3);
		if (!world.isClientSide()) {
			boolean hasOrder = entity instanceof AirdropentityEntity airdrop && airdrop.hasAirdropOrder();
			if (hasOrder && entity instanceof AirdropentityEntity airdrop && world instanceof ServerLevel _level) {
				List<String> ids = airdrop.getAirdropItemIds();
				List<Integer> qty = airdrop.getAirdropQuantities();
				BlockPos posFinal = deliveryPos.immutable();
				fillCrateFromOrder(_level, posFinal, ids, qty);
				// Retry once only if the BE wasn't ready; never clear+refill an already-filled order crate
				// (opening mid-refill caused ghost slots / click reshuffles with TaCZ).
				net.mcreator.radiotowers.RadiotowersMod.queueServerWork(1, () -> {
					BlockEntity be = _level.getBlockEntity(posFinal);
					if (be instanceof AirdropCrateBlockEntity crate && crate.isFilledFromOrder())
						return;
					fillCrateFromOrder(_level, posFinal, ids, qty);
				});
			} else if (!hasOrder && world instanceof ServerLevel _level && _level.getServer() != null) {
				double cx = deliveryPos.getX() + 0.5;
				double cy = deliveryPos.getY();
				double cz = deliveryPos.getZ() + 0.5;
				String lootTableId = net.mcreator.radiotowers.config.AirdropConfig.getStandardAirdropLootTable().toString();
				_level.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, new Vec3(cx, cy, cz), Vec2.ZERO, _level, 4, "", Component.literal(""), _level.getServer(), null).withSuppressedOutput(),
						"data merge block ~ ~ ~ {LootTable:\"" + lootTableId + "\"}");
			}
		}
		if (entity.level() != null && !entity.level().isClientSide())
			entity.discard();
	}

	/** Blocks descended per tick — positional fall, ignores gravity mods / NoAI physics quirks. */
	private static final double FALL_STEP = 0.4;

	/** Place crate when this close above the first solid block under the entity. */
	private static final double LAND_CLEARANCE = 1.15;

	/** First solid block under the entity, or null if none in build height. */
	private static BlockPos findGroundBelow(LevelAccessor world, double x, double y, double z) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		int startY = Mth.floor(y);
		int minY = world.getMinBuildHeight();
		for (int yy = startY; yy >= minY; yy--) {
			BlockPos p = new BlockPos(bx, yy, bz);
			if (world.getBlockState(p).blocksMotion())
				return p;
		}
		return null;
	}

	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null || world.isClientSide())
			return;
		if (!(entity instanceof AirdropentityEntity airdropEnt))
			return;
		if (!(entity instanceof LivingEntity living))
			return;

		// Do not trust entity physics in large packs — move the crate down ourselves.
		entity.setNoGravity(true);
		entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);

		if (touchesWater(world, x, y, z, entity)) {
			living.removeAllEffects();
			airdropEnt.getEntityData().set(AirdropentityEntity.DATA_IsOpening, false);
			BlockPos cratePos = findSeafloorCratePos(world, x, y, z);
			if (cratePos != null) {
				placeCrateAndDiscard(world, entity, cratePos);
				return;
			}
		}

		BlockPos ground = findGroundBelow(world, x, y, z);
		if (ground != null && y <= ground.getY() + LAND_CLEARANCE) {
			living.removeAllEffects();
			placeCrateAndDiscard(world, entity, ground.above());
			return;
		}

		// Parachute VFX only (amp 0). Descent is positional below.
		MobEffectInstance slow = living.getEffect(MobEffects.SLOW_FALLING);
		if (slow == null || slow.getDuration() < 40 || slow.getAmplifier() > 0) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
		}

		double nextY = y - FALL_STEP;
		if (ground != null && nextY < ground.getY() + LAND_CLEARANCE)
			nextY = ground.getY() + LAND_CLEARANCE;
		entity.setPos(x, nextY, z);
		entity.hasImpulse = true;
	}
}