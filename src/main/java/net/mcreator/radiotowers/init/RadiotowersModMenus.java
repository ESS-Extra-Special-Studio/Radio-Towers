/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.common.extensions.IForgeMenuType;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

import net.mcreator.radiotowers.SafeClientCalls;
import net.mcreator.radiotowers.world.inventory.AirdropCrateguiMenu;
import net.mcreator.radiotowers.network.MenuStateUpdateMessage;
import net.mcreator.radiotowers.RadiotowersMod;

import java.util.Map;

public class RadiotowersModMenus {
	public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.MENU_TYPES, RadiotowersMod.MODID);
	public static final RegistryObject<MenuType<AirdropCrateguiMenu>> AIRDROP_CRATEGUI = REGISTRY.register("airdrop_crategui", () -> IForgeMenuType.create(AirdropCrateguiMenu::new));

	public interface MenuAccessor {
		Map<String, Object> getMenuState();

		Map<Integer, Slot> getSlots();

		default void sendMenuStateUpdate(Player player, int elementType, String name, Object elementState, boolean needClientUpdate) {
			getMenuState().put(elementType + ":" + name, elementState);
			if (player instanceof ServerPlayer serverPlayer) {
				RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new MenuStateUpdateMessage(elementType, name, elementState));
			} else if (player.level().isClientSide) {
				SafeClientCalls.onClientSendMenuStateUpdate(this, elementType, name, elementState, needClientUpdate);
			}
		}

		default <T> T getMenuState(int elementType, String name, T defaultValue) {
			try {
				return (T) getMenuState().getOrDefault(elementType + ":" + name, defaultValue);
			} catch (ClassCastException e) {
				return defaultValue;
			}
		}
	}
}