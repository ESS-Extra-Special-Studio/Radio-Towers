package net.mcreator.radiotowers.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.SafeClientCalls;
import net.mcreator.radiotowers.init.RadiotowersModMenus;


public class MenuStateUpdateMessage implements CustomPacketPayload {
    public static final Type<MenuStateUpdateMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RadiotowersMod.MODID, "menu_state_update"));
    public static final StreamCodec<FriendlyByteBuf, MenuStateUpdateMessage> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> MenuStateUpdateMessage.buffer(msg, buf), MenuStateUpdateMessage::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


	private final int elementType;
	private final String name;
	private final Object elementState;

	public MenuStateUpdateMessage(int elementType, String name, Object elementState) {
		this.elementType = elementType;
		this.name = name;
		this.elementState = elementState;
	}

	public MenuStateUpdateMessage(FriendlyByteBuf buffer) {
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

	public static void buffer(MenuStateUpdateMessage message, FriendlyByteBuf buffer) {
		buffer.writeInt(message.elementType);
		buffer.writeUtf(message.name);
		if (message.elementType == 0) {
			buffer.writeUtf((String) message.elementState);
		} else if (message.elementType == 1) {
			buffer.writeBoolean((boolean) message.elementState);
		} else if (message.elementType == 2 && message.elementState instanceof Number n) {
			buffer.writeDouble(n.doubleValue());
		}
	}

	public static void handler(MenuStateUpdateMessage message, IPayloadContext context) {
		if (message.name.length() > 256 || message.elementState instanceof String string && string.length() > 8192)
			return;
		context.enqueueWork(() -> {
			if (context.player() != null && context.player().containerMenu instanceof RadiotowersModMenus.MenuAccessor menu) {
				menu.getMenuState().put(message.elementType + ":" + message.name, message.elementState);
			}
		});
	}
}