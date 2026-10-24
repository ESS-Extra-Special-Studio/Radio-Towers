package net.mcreator.radiotowers.procedures;
import net.minecraft.util.text.StringTextComponent;

import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.world.IWorld;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.potion.Effects;
import net.minecraft.potion.EffectInstance;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.command.CommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.BlockState;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import net.minecraftforge.registries.ForgeRegistries;

import net.mcreator.radiotowers.airdrop.AirdropCatalog;
import net.mcreator.radiotowers.airdrop.TaczAirdropIntegration;
import net.mcreator.radiotowers.entity.AirdropentityEntity;
import net.mcreator.radiotowers.block.entity.AirdropCrateBlockEntity;
import net.mcreator.radiotowers.init.RadiotowersModBlocks;

import java.util.List;

public class AirdropentityOnEntityTickUpdateProcedure {

	/** Fill crate at pos from order next tick so the block entity exists. */
	private static void fillCrateFromOrder(ServerWorld level, BlockPos pos, List<String> ids, List<Integer> qty,
			java.util.Collection<java.util.UUID> memberUuids) {
		try {
			TileEntity be = level.getBlockEntity(pos);
			AirdropCrateBlockEntity crate = (be instanceof AirdropCrateBlockEntity) ? (AirdropCrateBlockEntity) be : null;
			if (!(crate != null)) return;
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
						ResourceLocation id = new ResourceLocation(idStr);
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
									for (net.mcreator.radiotowers.airdrop.AirdropCatalog.Entry e : AirdropCatalog.getEntries()) {
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
									for (net.mcreator.radiotowers.airdrop.AirdropCatalog.Entry e : AirdropCatalog.getEntries()) {
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
								net.minecraft.item.Item item = ForgeRegistries.ITEMS.getValue(id);
								if (item != null && item != Items.AIR) {
									stack = new ItemStack(item);
								} else {
									for (net.mcreator.radiotowers.airdrop.AirdropCatalog.Entry e : AirdropCatalog.getEntries()) {
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
									if (toPut.getTag() != null)
										toPut.getTag().remove("AmmoCount");
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
			if (memberUuids != null && !memberUuids.isEmpty()) {
				crate.setAllowedOpeners(memberUuids);
			}
			crate.setChanged();
		} catch (Exception e) {
			net.mcreator.radiotowers.RadiotowersMod.LOGGER.warn("Airdrop crate fill failed: {}", e.getMessage());
		}
	}

	private static boolean touchesWater(IWorld world, double x, double y, double z, Entity entity) {
		if (!entity.isAlive() || entity.removed) return false;
		if (entity.isInWater()) return true;
		BlockPos pos = new BlockPos(x, y, z);
		if (world.getFluidState(pos).is(FluidTags.WATER)) return true;
		if (world.getFluidState(pos.below()).is(FluidTags.WATER)) return true;
		return world.getFluidState(pos.above()).is(FluidTags.WATER);
	}

	/** Block position for the crate (one block above first solid column below the entity). */
	private static BlockPos findSeafloorCratePos(IWorld world, double x, double y, double z) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		int startY = MathHelper.floor(y);
		int minY = 0;
		for (int yy = startY; yy >= minY; yy--) {
			BlockPos p = new BlockPos(bx, yy, bz);
			BlockState st = world.getBlockState(p);
			if (st.getMaterial().blocksMotion())
				return p.above();
		}
		return null;
	}

	private static BlockPos resolveDeliveryCratePos(IWorld world, BlockPos preferred) {
		if (world.isClientSide()) return preferred;
		TileEntity be = world.getBlockEntity(preferred);
		AirdropCrateBlockEntity crate = (be instanceof AirdropCrateBlockEntity) ? (AirdropCrateBlockEntity) be : null;
		if (!(crate != null) || !crate.isStructureLoot())
			return preferred;
		for (Direction dir : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP }) {
			BlockPos alt = preferred.relative(dir);
			TileEntity altBe = world.getBlockEntity(alt);
			AirdropCrateBlockEntity altCrate = (altBe instanceof AirdropCrateBlockEntity) ? (AirdropCrateBlockEntity) altBe : null;
			if (altCrate != null && altCrate.isStructureLoot())
				continue;
			if (world.isEmptyBlock(alt) || world.getBlockState(alt).getMaterial().isReplaceable())
				return alt;
		}
		return preferred;
	}

	private static void placeCrateAndDiscard(IWorld world, Entity entity, BlockPos cratePos) {
		BlockPos deliveryPos = resolveDeliveryCratePos(world, cratePos);
		world.setBlock(deliveryPos, RadiotowersModBlocks.AIRDROP_CRATE.get().defaultBlockState(), 3);
		if (!world.isClientSide()) {
			AirdropentityEntity airdrop = (entity instanceof AirdropentityEntity) ? (AirdropentityEntity) entity : null;
			boolean hasOrder = airdrop != null && airdrop.hasAirdropOrder();
			ServerWorld _level = (world instanceof ServerWorld) ? (ServerWorld) world : null;
			if (hasOrder && airdrop != null && _level != null) {
				List<String> ids = airdrop.getAirdropItemIds();
				List<Integer> qty = airdrop.getAirdropQuantities();
				java.util.Set<java.util.UUID> members = airdrop.isMembersOnlyCrate() ? airdrop.getLobbyMembers() : java.util.Collections.emptySet();
				BlockPos posFinal = deliveryPos.immutable();
				fillCrateFromOrder(_level, posFinal, ids, qty, members);
				// Retry once only if the BE wasn't ready; never clear+refill an already-filled order crate
				// (opening mid-refill caused ghost slots / click reshuffles with TaCZ).
				net.mcreator.radiotowers.RadiotowersMod.queueServerWork(1, () -> {
					TileEntity be = _level.getBlockEntity(posFinal);
					AirdropCrateBlockEntity crate = (be instanceof AirdropCrateBlockEntity) ? (AirdropCrateBlockEntity) be : null;
					if (crate != null && crate.isFilledFromOrder())
						return;
					fillCrateFromOrder(_level, posFinal, ids, qty, members);
				});
			} else if (!hasOrder && _level != null && _level.getServer() != null) {
				double cx = deliveryPos.getX() + 0.5;
				double cy = deliveryPos.getY();
				double cz = deliveryPos.getZ() + 0.5;
				String lootTableId = net.mcreator.radiotowers.config.AirdropConfig.getStandardAirdropLootTable().toString();
				_level.getServer().getCommands().performCommand(new CommandSource(_level.getServer(), new Vector3d(cx, cy, cz), net.minecraft.util.math.vector.Vector2f.ZERO, _level, 4, "", new StringTextComponent(""), _level.getServer(), null).withSuppressedOutput(),
						"data merge block ~ ~ ~ {LootTable:\"" + lootTableId + "\"}");
			}
		}
		if (entity.level != null && !entity.level.isClientSide())
			entity.remove();
	}

	/** Blocks descended per tick — positional fall, ignores gravity mods / NoAI physics quirks. */
	private static final double FALL_STEP = 0.4;

	/** Place crate when this close above the first solid block under the entity. */
	private static final double LAND_CLEARANCE = 1.15;

	/** First solid block under the entity, or null if none in build height. */
	private static BlockPos findGroundBelow(IWorld world, double x, double y, double z) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		int startY = MathHelper.floor(y);
		int minY = 0;
		for (int yy = startY; yy >= minY; yy--) {
			BlockPos p = new BlockPos(bx, yy, bz);
			if (world.getBlockState(p).getMaterial().blocksMotion())
				return p;
		}
		return null;
	}

	public static void execute(IWorld world, double x, double y, double z, Entity entity) {
		if (entity == null || world.isClientSide())
			return;
			AirdropentityEntity airdropEnt = (entity instanceof AirdropentityEntity) ? (AirdropentityEntity) entity : null;
		if (!(airdropEnt != null))
			return;
			LivingEntity living = (entity instanceof LivingEntity) ? (LivingEntity) entity : null;
		if (!(living != null))
			return;

		// Do not trust entity physics in large packs — move the crate down ourselves.
		entity.setNoGravity(true);
		entity.setDeltaMovement(Vector3d.ZERO);

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
		EffectInstance slow = living.getEffect(Effects.SLOW_FALLING);
		if (slow == null || slow.getDuration() < 40 || slow.getAmplifier() > 0) {
			living.addEffect(new EffectInstance(Effects.SLOW_FALLING, 80, 0, false, false));
		}

		double nextY = y - FALL_STEP;
		if (ground != null && nextY < ground.getY() + LAND_CLEARANCE)
			nextY = ground.getY() + LAND_CLEARANCE;
		entity.setPos(x, nextY, z);
		entity.hasImpulse = true;
	}
}