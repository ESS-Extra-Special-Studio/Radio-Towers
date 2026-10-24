package net.mcreator.radiotowers.block.entity;
import net.minecraft.util.text.TranslationTextComponent;

import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.common.capabilities.Capability;

import net.minecraft.block.BlockState;
import net.minecraft.tileentity.LockableLootTileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.container.Container;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.util.NonNullList;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.network.PacketBuffer;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;

import net.mcreator.radiotowers.airdrop.TaczAirdropIntegration;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.world.inventory.AirdropCrateguiMenu;
import net.mcreator.radiotowers.init.RadiotowersModBlockEntities;

import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import java.util.stream.IntStream;

import io.netty.buffer.Unpooled;

public class AirdropCrateBlockEntity extends LockableLootTileEntity implements ISidedInventory {
	private static final ResourceLocation STRUCTURE_OVERRUN_LOOT = new ResourceLocation("radiotowers", "radio_tower_overrun_loot");

	private NonNullList<ItemStack> stacks = NonNullList.withSize(27, ItemStack.EMPTY);
	private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());
	/** Menu opens with {@code facing == null}; must expose the real container (not a private empty handler). */
	private final LazyOptional<IItemHandler> unrestrictedHandler = LazyOptional.of(() -> new InvWrapper(this));
	/** When true, crate was filled from player's airdrop selection — do not add Tacz ammo on open. */
	private boolean filledFromOrder = false;
	/** Worldgen / structure chest disguised as airdrop crate — never fill from live deliveries. */
	private boolean structureLoot = false;
	/** When non-empty, only these UUIDs may open the crate (lobbied delivery). */
	private final java.util.Set<java.util.UUID> allowedOpeners = new java.util.LinkedHashSet<>();

	public AirdropCrateBlockEntity() {
		super(RadiotowersModBlockEntities.AIRDROP_CRATE.get());
	}

	@Override
	public void load(BlockState state, CompoundNBT compound) {
		super.load(state, compound);
		this.filledFromOrder = compound.getBoolean("FilledFromOrder");
		this.structureLoot = compound.getBoolean("StructureLoot");
		allowedOpeners.clear();
		if (compound.contains("AllowedOpenerStrings", 9)) {
			net.minecraft.nbt.ListNBT list = compound.getList("AllowedOpenerStrings", 8);
			for (int i = 0; i < list.size(); i++) {
				try {
					allowedOpeners.add(java.util.UUID.fromString(list.getString(i)));
				} catch (Exception ignored) {}
			}
		}
		if (!this.tryLoadLootTable(compound))
			this.stacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		net.minecraft.inventory.ItemStackHelper.loadAllItems(compound, this.stacks);
		if (!this.filledFromOrder && !this.structureLoot && compound.contains("LootTable", 8)) {
			ResourceLocation table = compound.getString("LootTable").isEmpty() || !ResourceLocation.isValidResourceLocation(compound.getString("LootTable")) ? null : new ResourceLocation(compound.getString("LootTable"));
			if (table != null && STRUCTURE_OVERRUN_LOOT.equals(table))
				this.structureLoot = true;
		}
	}

	@Override
	public CompoundNBT save(CompoundNBT compound) {
		super.save(compound);
		compound.putBoolean("FilledFromOrder", this.filledFromOrder);
		compound.putBoolean("StructureLoot", this.structureLoot);
		if (!allowedOpeners.isEmpty()) {
			net.minecraft.nbt.ListNBT list = new net.minecraft.nbt.ListNBT();
			for (java.util.UUID u : allowedOpeners) {
				list.add(net.minecraft.nbt.StringNBT.valueOf(u.toString()));
			}
			compound.put("AllowedOpenerStrings", list);
		}
		if (!this.trySaveLootTable(compound)) {
			net.minecraft.inventory.ItemStackHelper.saveAllItems(compound, this.stacks);
		}
		return compound;
	}

	@Override
	public SUpdateTileEntityPacket getUpdatePacket() {
		return new SUpdateTileEntityPacket(this.getBlockPos(), 0, this.getUpdateTag());
	}

	@Override
	public CompoundNBT getUpdateTag() {
		return this.save(new CompoundNBT());
	}

	@Override
	public int getContainerSize() {
		return stacks.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack itemstack : this.stacks)
			if (!itemstack.isEmpty())
				return false;
		return true;
	}

	@Override
	public ITextComponent getDefaultName() {
		return new TranslationTextComponent("block.radiotowers.airdrop_crate");
	}

	@Override
	public int getMaxStackSize() {
		return 64;
	}

	@Override
	public Container createMenu(int id, PlayerInventory inventory) {
		return new AirdropCrateguiMenu(id, inventory, new PacketBuffer(Unpooled.buffer()).writeBlockPos(this.worldPosition));
	}

	@Override
	public ITextComponent getDisplayName() {
		return new TranslationTextComponent("block.radiotowers.airdrop_crate");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.stacks;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> stacks) {
		this.stacks = stacks;
	}

	@Override
	public boolean canPlaceItem(int index, ItemStack stack) {
		return true;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return IntStream.range(0, this.getContainerSize()).toArray();
	}

	@Override
	public boolean canPlaceItemThroughFace(int index, ItemStack itemstack, @Nullable Direction direction) {
		return this.canPlaceItem(index, itemstack);
	}

	@Override
	public boolean canTakeItemThroughFace(int index, ItemStack itemstack, Direction direction) {
		return true;
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction facing) {
		if (!this.remove && capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			// AirdropCrateguiMenu binds with facing == null. Previously only sided handlers
			// were exposed, so the menu fell back to a private empty ItemStackHandler —
			// classic ghost-slot / click-reshuffle desync (especially visible with TaCZ NBT items).
			if (facing == null)
				return unrestrictedHandler.cast();
			return handlers[facing.ordinal()].cast();
		}
		return super.getCapability(capability, facing);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		for (LazyOptional<? extends IItemHandler> handler : handlers)
			handler.invalidate();
		unrestrictedHandler.invalidate();
	}

	/** Call when filling the crate from the player's airdrop selection so we don't add Tacz ammo on open. */
	public void setFilledFromOrder(boolean filledFromOrder) {
		this.filledFromOrder = filledFromOrder;
	}

	public boolean isFilledFromOrder() {
		return this.filledFromOrder;
	}

	public void setAllowedOpeners(java.util.Collection<java.util.UUID> uuids) {
		allowedOpeners.clear();
		if (uuids != null) allowedOpeners.addAll(uuids);
		setChanged();
	}

	public boolean hasOpenRestriction() {
		return !allowedOpeners.isEmpty();
	}

	public boolean canPlayerOpen(java.util.UUID playerUuid) {
		if (allowedOpeners.isEmpty()) return true;
		return playerUuid != null && allowedOpeners.contains(playerUuid);
	}

	/** True for tower worldgen loot crates — must not receive live airdrop / wave delivery fills. */
	public boolean isStructureLoot() {
		return this.structureLoot;
	}

	@Override
	public void unpackLootTable(PlayerEntity player) {
		boolean hadLootTable = this.lootTable != null;
		super.unpackLootTable(player);
		// Structure / ordered crates keep fixed contents. Bonus TaCZ only for live loot-table drops.
		if (!hadLootTable || this.filledFromOrder || this.structureLoot) return;
		if (!ModList.get().isLoaded("tacz") || !AirdropConfig.INCLUDE_TACZ_AMMO.get()) return;
		java.util.Random random = level != null ? level.random : new java.util.Random();
		List<Integer> emptySlots = new ArrayList<>();
		for (int i = 0; i < getContainerSize(); i++)
			if (this.stacks.get(i).isEmpty()) emptySlots.add(i);
		int toAdd = Math.min(2 + random.nextInt(4), emptySlots.size());
		for (int n = 0; n < toAdd && !emptySlots.isEmpty(); n++) {
			int idx = random.nextInt(emptySlots.size());
			int slot = emptySlots.remove(idx);
			ItemStack taczStack = TaczAirdropIntegration.randomTaczAmmoStack(random);
			if (!taczStack.isEmpty() && TaczAirdropIntegration.hasAmmoIdTag(taczStack))
				this.stacks.set(slot, taczStack);
		}
		this.setChanged();
	}

}