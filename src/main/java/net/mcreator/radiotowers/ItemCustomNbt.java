package net.mcreator.radiotowers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

/** 1.21 CustomData helpers (replaces ItemStack getTag / getOrCreateTag). */
public final class ItemCustomNbt {
    public static CompoundTag copy(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    public static boolean has(ItemStack stack, String key) {
        return copy(stack).contains(key);
    }

    public static void set(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void update(ItemStack stack, Consumer<CompoundTag> editor) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, editor);
    }

    private ItemCustomNbt() {}
}
