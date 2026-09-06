package net.mcreator.radiotowers.block.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.util.RandomSource;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import net.mcreator.radiotowers.airdrop.TaczAirdropIntegration;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.world.inventory.AirdropCrateguiMenu;
import net.mcreator.radiotowers.init.RadiotowersModBlockEntities;

import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import java.util.stream.IntStream;

import io.netty.buffer.Unpooled;

public class AirdropCrateBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
	private static final ResourceLocation STRUCTURE_OVERRUN_LOOT = ResourceLocation.fromNamespaceAndPath("radiotowers", "radio_tower_overrun_loot");

	private NonNullList<ItemStack> stacks = NonNullList.withSize(27, ItemStack.EMPTY);
	/** When true, crate was filled from player's airdrop selection — do not add Tacz ammo on open. */
	private boolean filledFromOrder = false;
	/** Worldgen / structure chest disguised as airdrop crate — never fill from live deliveries. */
	private boolean structureLoot = false;

	public AirdropCrateBlockEntity(BlockPos position, BlockState state) {
		super(RadiotowersModBlockEntities.AIRDROP_CRATE.get(), position, state);
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		this.filledFromOrder = compound.getBoolean("FilledFromOrder");
		this.structureLoot = compound.getBoolean("StructureLoot");
		if (!this.tryLoadLootTable(compound))
			this.stacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(compound, this.stacks, registries);
		if (!this.filledFromOrder && !this.structureLoot && compound.contains("LootTable", net.minecraft.nbt.Tag.TAG_STRING)) {
			ResourceLocation table = ResourceLocation.tryParse(compound.getString("LootTable"));
			if (table != null && STRUCTURE_OVERRUN_LOOT.equals(table))
				this.structureLoot = true;
		}
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.saveAdditional(compound, registries);
		compound.putBoolean("FilledFromOrder", this.filledFromOrder);
		compound.putBoolean("StructureLoot", this.structureLoot);
		if (!this.trySaveLootTable(compound)) {
			ContainerHelper.saveAllItems(compound, this.stacks, registries);
		}
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveWithoutMetadata(registries);
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
	public Component getDefaultName() {
		return Component.translatable("block.radiotowers.airdrop_crate");
	}

	@Override
	public int getMaxStackSize() {
		return 64;
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return new AirdropCrateguiMenu(id, inventory, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(this.worldPosition));
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.radiotowers.airdrop_crate");
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

	/** Call when filling the crate from the player's airdrop selection so we don't add Tacz ammo on open. */
	public void setFilledFromOrder(boolean filledFromOrder) {
		this.filledFromOrder = filledFromOrder;
	}

	public boolean isFilledFromOrder() {
		return this.filledFromOrder;
	}

	/** True for tower worldgen loot crates — must not receive live airdrop / wave delivery fills. */
	public boolean isStructureLoot() {
		return this.structureLoot;
	}

	@Override
	public void unpackLootTable(Player player) {
		boolean hadLootTable = this.lootTable != null;
		super.unpackLootTable(player);
		// Structure / ordered crates keep fixed contents. Bonus TaCZ only for live loot-table drops.
		if (!hadLootTable || this.filledFromOrder || this.structureLoot) return;
		if (!ModList.get().isLoaded("tacz") || !AirdropConfig.INCLUDE_TACZ_AMMO.get()) return;
		RandomSource random = level != null ? level.getRandom() : RandomSource.create();
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