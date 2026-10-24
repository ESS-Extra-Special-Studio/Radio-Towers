/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.common.extensions.IForgeContainerType;

import net.minecraft.inventory.container.Slot;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;

import net.mcreator.radiotowers.SafeClientCalls;
import net.mcreator.radiotowers.world.inventory.AirdropCrateguiMenu;
import net.mcreator.radiotowers.network.MenuStateUpdateMessage;
import net.mcreator.radiotowers.RadiotowersMod;

import java.util.Map;

public class RadiotowersModMenus {
	public static final DeferredRegister<ContainerType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.CONTAINERS, RadiotowersMod.MODID);
	public static final RegistryObject<ContainerType<AirdropCrateguiMenu>> AIRDROP_CRATEGUI = REGISTRY.register("airdrop_crategui", () -> IForgeContainerType.create(AirdropCrateguiMenu::new));

	public interface MenuAccessor {
		Map<String, Object> getMenuState();

		Map<Integer, Slot> getSlots();

		default void sendMenuStateUpdate(PlayerEntity player, int elementType, String name, Object elementState, boolean needClientUpdate) {
			getMenuState().put(elementType + ":" + name, elementState);
			ServerPlayerEntity serverPlayer = (player instanceof ServerPlayerEntity) ? (ServerPlayerEntity) player : null;
			if (serverPlayer != null) {
				RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new MenuStateUpdateMessage(elementType, name, elementState));
			} else if (player.level.isClientSide) {
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