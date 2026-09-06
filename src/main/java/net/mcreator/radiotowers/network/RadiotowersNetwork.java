package net.mcreator.radiotowers.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RadiotowersNetwork {
    public static final String PROTOCOL = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar(PROTOCOL);
        reg.playToServer(CallAirdropMessage.TYPE, CallAirdropMessage.STREAM_CODEC, CallAirdropMessage::handle);
        reg.playToServer(RequestAirdropStatePacket.TYPE, RequestAirdropStatePacket.STREAM_CODEC, RequestAirdropStatePacket::handle);
        reg.playToServer(ActivatePanelForDeadAirMessage.TYPE, ActivatePanelForDeadAirMessage.STREAM_CODEC, ActivatePanelForDeadAirMessage::handle);
        reg.playToServer(ActivatePanelWithStationMessage.TYPE, ActivatePanelWithStationMessage.STREAM_CODEC, ActivatePanelWithStationMessage::handle);
        reg.playToServer(DeactivatePanelMessage.TYPE, DeactivatePanelMessage.STREAM_CODEC, DeactivatePanelMessage::handle);
        reg.playToServer(MenuStateUpdateMessage.TYPE, MenuStateUpdateMessage.STREAM_CODEC, MenuStateUpdateMessage::handler);
        reg.playToClient(AirdropStatePacket.TYPE, AirdropStatePacket.STREAM_CODEC, AirdropStatePacket::handle);
        reg.playToClient(WaveStatePacket.TYPE, WaveStatePacket.STREAM_CODEC, WaveStatePacket::handle);
        reg.playToClient(DefenseSessionSyncPacket.TYPE, DefenseSessionSyncPacket.STREAM_CODEC, DefenseSessionSyncPacket::handle);
        reg.playToClient(RadiotowersModVariables.SavedDataSyncMessage.TYPE, RadiotowersModVariables.SavedDataSyncMessage.STREAM_CODEC, RadiotowersModVariables.SavedDataSyncMessage::handleData);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersInDimension(level, payload);
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }

    private RadiotowersNetwork() {}
}
