package net.mcreator.radiotowers.world.inventory;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;

import net.mcreator.radiotowers.init.RadiotowersModMenus;

import java.util.function.Supplier;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

public class AirdropCrateguiMenu extends AbstractContainerMenu implements RadiotowersModMenus.MenuAccessor {
	public final Map<String, Object> menuState = new HashMap<>() {
		@Override
		public Object put(String key, Object value) {
			if (!this.containsKey(key) && this.size() >= 28)
				return null;
			return super.put(key, value);
		}
	};
	public final Level world;
	public final Player entity;
	public int x, y, z;
	private ContainerLevelAccess access = ContainerLevelAccess.NULL;
	private final ItemStacksResourceHandler fallback = new ItemStacksResourceHandler(27);
	private final Map<Integer, Slot> customSlots = new HashMap<>();
	private boolean bound = false;
	private Supplier<Boolean> boundItemMatcher = null;
	private Entity boundEntity = null;
	private BlockEntity boundBlockEntity = null;

	public AirdropCrateguiMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
		super(RadiotowersModMenus.AIRDROP_CRATEGUI.get(), id);
		this.entity = inv.player;
		this.world = inv.player.level();
		BlockPos pos = null;
		if (extraData != null) {
			pos = extraData.readBlockPos();
			this.x = pos.getX();
			this.y = pos.getY();
			this.z = pos.getZ();
			access = ContainerLevelAccess.create(world, pos);
		}
		if (pos != null) {
			if (extraData.readableBytes() == 1) { // bound to item
				byte hand = extraData.readByte();
				ItemStack itemstack = hand == 0 ? this.entity.getMainHandItem() : this.entity.getOffhandItem();
				this.boundItemMatcher = () -> true;
			} else if (extraData.readableBytes() > 1) { // bound to entity
				extraData.readByte();
				boundEntity = world.getEntity(extraData.readVarInt());
			} else { // might be bound to block
				boundBlockEntity = this.world.getBlockEntity(pos);
				if (boundBlockEntity instanceof net.mcreator.radiotowers.block.entity.AirdropCrateBlockEntity crate) {
					this.bound = true;
				}
			}
		}
		this.customSlots.put(0, this.addSlot(this.crateSlot(0, 8, 23)));
		this.customSlots.put(1, this.addSlot(this.crateSlot(1, 26, 23)));
		this.customSlots.put(2, this.addSlot(this.crateSlot(2, 44, 23)));
		this.customSlots.put(3, this.addSlot(this.crateSlot(3, 62, 23)));
		this.customSlots.put(4, this.addSlot(this.crateSlot(4, 80, 23)));
		this.customSlots.put(5, this.addSlot(this.crateSlot(5, 98, 23)));
		this.customSlots.put(6, this.addSlot(this.crateSlot(6, 116, 23)));
		this.customSlots.put(7, this.addSlot(this.crateSlot(7, 134, 23)));
		this.customSlots.put(8, this.addSlot(this.crateSlot(8, 152, 23)));
		this.customSlots.put(9, this.addSlot(this.crateSlot(9, 8, 41)));
		this.customSlots.put(10, this.addSlot(this.crateSlot(10, 26, 41)));
		this.customSlots.put(11, this.addSlot(this.crateSlot(11, 44, 41)));
		this.customSlots.put(12, this.addSlot(this.crateSlot(12, 62, 41)));
		this.customSlots.put(13, this.addSlot(this.crateSlot(13, 80, 41)));
		this.customSlots.put(14, this.addSlot(this.crateSlot(14, 98, 41)));
		this.customSlots.put(15, this.addSlot(this.crateSlot(15, 116, 41)));
		this.customSlots.put(16, this.addSlot(this.crateSlot(16, 134, 41)));
		this.customSlots.put(17, this.addSlot(this.crateSlot(17, 152, 41)));
		this.customSlots.put(18, this.addSlot(this.crateSlot(18, 8, 59)));
		this.customSlots.put(19, this.addSlot(this.crateSlot(19, 26, 59)));
		this.customSlots.put(20, this.addSlot(this.crateSlot(20, 44, 59)));
		this.customSlots.put(21, this.addSlot(this.crateSlot(21, 62, 59)));
		this.customSlots.put(22, this.addSlot(this.crateSlot(22, 80, 59)));
		this.customSlots.put(23, this.addSlot(this.crateSlot(23, 98, 59)));
		this.customSlots.put(24, this.addSlot(this.crateSlot(24, 116, 59)));
		this.customSlots.put(25, this.addSlot(this.crateSlot(25, 134, 59)));
		this.customSlots.put(26, this.addSlot(this.crateSlot(26, 152, 59)));
		for (int si = 0; si < 3; ++si)
			for (int sj = 0; sj < 9; ++sj)
				this.addSlot(new Slot(inv, sj + (si + 1) * 9, 0 + 8 + sj * 18, 0 + 84 + si * 18));
		for (int si = 0; si < 9; ++si)
			this.addSlot(new Slot(inv, si, 0 + 8 + si * 18, 0 + 142));
	}

	@Override
	public boolean stillValid(Player player) {
		if (this.bound) {
			if (this.boundItemMatcher != null)
				return this.boundItemMatcher.get();
			else if (this.boundBlockEntity != null)
				return AbstractContainerMenu.stillValid(this.access, player, this.boundBlockEntity.getBlockState().getBlock());
			else if (this.boundEntity != null)
				return this.boundEntity.isAlive();
		}
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player playerIn, int index) {
		ItemStack itemstack = ItemStack.EMPTY;
		Slot slot = (Slot) this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack itemstack1 = slot.getItem();
			itemstack = itemstack1.copy();
			if (index < 27) {
				if (!this.moveItemStackTo(itemstack1, 27, this.slots.size(), true))
					return ItemStack.EMPTY;
				slot.onQuickCraft(itemstack1, itemstack);
			} else if (!this.moveItemStackTo(itemstack1, 0, 27, false)) {
				if (index < 27 + 27) {
					if (!this.moveItemStackTo(itemstack1, 27 + 27, this.slots.size(), true))
						return ItemStack.EMPTY;
				} else {
					if (!this.moveItemStackTo(itemstack1, 27, 27 + 27, false))
						return ItemStack.EMPTY;
				}
				return ItemStack.EMPTY;
			}
			if (itemstack1.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
			if (itemstack1.getCount() == itemstack.getCount()) {
				return ItemStack.EMPTY;
			}
			slot.onTake(playerIn, itemstack1);
		}
		return itemstack;
	}

	@Override
	protected boolean moveItemStackTo(ItemStack p_38904_, int p_38905_, int p_38906_, boolean p_38907_) {
		boolean flag = false;
		int i = p_38905_;
		if (p_38907_) {
			i = p_38906_ - 1;
		}
		if (p_38904_.isStackable()) {
			while (!p_38904_.isEmpty()) {
				if (p_38907_) {
					if (i < p_38905_) {
						break;
					}
				} else if (i >= p_38906_) {
					break;
				}
				Slot slot = this.slots.get(i);
				ItemStack itemstack = slot.getItem();
				if (slot.mayPlace(itemstack) && !itemstack.isEmpty() && ItemStack.isSameItemSameComponents(p_38904_, itemstack)) {
					int j = itemstack.getCount() + p_38904_.getCount();
					int maxSize = Math.min(slot.getMaxStackSize(), p_38904_.getMaxStackSize());
					if (j <= maxSize) {
						p_38904_.setCount(0);
						itemstack.setCount(j);
						slot.set(itemstack);
						flag = true;
					} else if (itemstack.getCount() < maxSize) {
						p_38904_.shrink(maxSize - itemstack.getCount());
						itemstack.setCount(maxSize);
						slot.set(itemstack);
						flag = true;
					}
				}
				if (p_38907_) {
					--i;
				} else {
					++i;
				}
			}
		}
		if (!p_38904_.isEmpty()) {
			if (p_38907_) {
				i = p_38906_ - 1;
			} else {
				i = p_38905_;
			}
			while (true) {
				if (p_38907_) {
					if (i < p_38905_) {
						break;
					}
				} else if (i >= p_38906_) {
					break;
				}
				Slot slot1 = this.slots.get(i);
				ItemStack itemstack1 = slot1.getItem();
				if (itemstack1.isEmpty() && slot1.mayPlace(p_38904_)) {
					if (p_38904_.getCount() > slot1.getMaxStackSize()) {
						slot1.setByPlayer(p_38904_.split(slot1.getMaxStackSize()));
					} else {
						slot1.setByPlayer(p_38904_.split(p_38904_.getCount()));
					}
					slot1.setChanged();
					flag = true;
					break;
				}
				if (p_38907_) {
					--i;
				} else {
					++i;
				}
			}
		}
		return flag;
	}

	private Slot crateSlot(int index, int sx, int sy) {
		if (this.bound && this.boundBlockEntity instanceof net.minecraft.world.Container container) {
			return new Slot(container, index, sx, sy);
		}
		return new ResourceHandlerSlot(this.fallback, this.fallback::set, index, sx, sy);
	}

	@Override
	public void removed(Player playerIn) {
		super.removed(playerIn);
		if (!bound && playerIn instanceof ServerPlayer serverPlayer) {
			if (!serverPlayer.isAlive() || serverPlayer.hasDisconnected()) {
				for (int j = 0; j < fallback.size(); ++j) {
					playerIn.drop(fallback.getResource(j).toStack(fallback.getAmountAsInt(j)), false, net.minecraft.util.Prediction.SERVER_ONLY);
					fallback.set(j, ItemResource.EMPTY, 0);
				}
			} else {
				for (int i = 0; i < fallback.size(); ++i) {
					playerIn.getInventory().placeItemBackInInventory(fallback.getResource(i).toStack(fallback.getAmountAsInt(i)), net.minecraft.util.Prediction.SERVER_ONLY);
					fallback.set(i, ItemResource.EMPTY, 0);
				}
			}
		}
	}

	@Override
	public Map<Integer, Slot> getSlots() {
		return Collections.unmodifiableMap(customSlots);
	}

	@Override
	public Map<String, Object> getMenuState() {
		return menuState;
	}
}