package net.mcreator.radiotowers.network;

import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.minecraft.network.PacketBuffer;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.SafeClientCalls;
import net.mcreator.radiotowers.init.RadiotowersModMenus;

import java.util.function.Supplier;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class MenuStateUpdateMessage {
	private final int elementType;
	private final String name;
	private final Object elementState;

	public MenuStateUpdateMessage(int elementType, String name, Object elementState) {
		this.elementType = elementType;
		this.name = name;
		this.elementState = elementState;
	}

	public MenuStateUpdateMessage(PacketBuffer buffer) {
		this.elementType = buffer.readInt();
		this.name = buffer.readUtf();
		Object elementState = null;
		if (elementType == 0) {
			elementState = buffer.readUtf();
		} else if (elementType == 1) {
			elementState = buffer.readBoolean();
		} else if (elementType == 2) {
			elementState = buffer.readDouble();
		}
		this.elementState = elementState;
	}

	public static void buffer(MenuStateUpdateMessage message, PacketBuffer buffer) {
		buffer.writeInt(message.elementType);
		buffer.writeUtf(message.name);
		Number n = (message.elementState instanceof Number) ? (Number) message.elementState : null;
		if (message.elementType == 0) {
			buffer.writeUtf((String) message.elementState);
		} else if (message.elementType == 1) {
			buffer.writeBoolean((boolean) message.elementState);
		} else if (message.elementType == 2 && n != null) {
			buffer.writeDouble(n.doubleValue());
		}
	}

	public static void handler(MenuStateUpdateMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
	String string = (message.elementState instanceof String) ? (String) message.elementState : null;
		if (message.name.length() > 256 || string != null && string.length() > 8192)
			return;
		NetworkEvent.Context context = contextSupplier.get();
		context.enqueueWork(() -> {
		Object menu$src = context.getSender().containerMenu; RadiotowersModMenus.MenuAccessor menu = (menu$src instanceof RadiotowersModMenus.MenuAccessor) ? (RadiotowersModMenus.MenuAccessor) menu$src : null;
			if (menu != null) {
				menu.getMenuState().put(message.elementType + ":" + message.name, message.elementState);
				if (!context.getDirection().getReceptionSide().isServer()) {
					SafeClientCalls.applyMenuStateUpdateScreenSync(menu, message.elementType, message.name, message.elementState);
				}
			}
		});
		context.setPacketHandled(true);
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		RadiotowersMod.addNetworkMessage(MenuStateUpdateMessage.class, MenuStateUpdateMessage::buffer, MenuStateUpdateMessage::new, MenuStateUpdateMessage::handler);
	}
}