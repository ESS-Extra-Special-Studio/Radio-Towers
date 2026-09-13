package net.mcreator.radiotowers.network.lobby;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.lobby.AirdropLobbyService;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import uk.co.extraspecialstudio.esn.EsnChannel;
import uk.co.extraspecialstudio.esn.EsnChannels;

/**
 * Registers the RadioTowers ESN channel (protocol {@code 1}) and lobby packets.
 */
@Mod.EventBusSubscriber(modid = RadiotowersMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AirdropLobbyNetwork {
    public static final String CHANNEL_MOD_ID = "radiotowers";
    public static final String PROTOCOL = "1";

    private static EsnChannel channel;
    private static boolean registered;

    private AirdropLobbyNetwork() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded("esn") && ModList.get().isLoaded("extraspeciallib");
    }

    public static EsnChannel channel() {
        if (channel == null) {
            throw new IllegalStateException("ESN lobby channel not registered");
        }
        return channel;
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(AirdropLobbyNetwork::register);
    }

    private static void register() {
        if (registered) return;
        if (!ModList.get().isLoaded("esn")) {
            RadiotowersMod.LOGGER.info("[Lobby] ESN not loaded — airdrop lobby packets disabled");
            return;
        }
        try {
            channel = EsnChannels.register(CHANNEL_MOD_ID, PROTOCOL);
            channel.register("lobby_open", LobbyPackets.Open.class,
                LobbyPackets.Open::encode, LobbyPackets.Open::decode,
                null, (msg, ctx) -> AirdropLobbyService.handleOpen(ctx.get().sender(), msg.panelPos()));
            channel.register("lobby_invite", LobbyPackets.Invite.class,
                LobbyPackets.Invite::encode, LobbyPackets.Invite::decode,
                null, (msg, ctx) -> AirdropLobbyService.handleInvite(ctx.get().sender(), msg.lobbyId(), msg.targetName()));
            channel.register("lobby_accept", LobbyPackets.Accept.class,
                LobbyPackets.Accept::encode, LobbyPackets.Accept::decode,
                null, (msg, ctx) -> AirdropLobbyService.handleAccept(ctx.get().sender(), msg.lobbyId()));
            channel.register("lobby_decline", LobbyPackets.Decline.class,
                LobbyPackets.Decline::encode, LobbyPackets.Decline::decode,
                null, (msg, ctx) -> AirdropLobbyService.handleDecline(ctx.get().sender(), msg.lobbyId()));
            channel.register("lobby_ready", LobbyPackets.Ready.class,
                LobbyPackets.Ready::encode, LobbyPackets.Ready::decode,
                null, (msg, ctx) -> AirdropLobbyService.handleReady(ctx.get().sender(), msg.lobbyId(), msg.ready()));
            channel.register("lobby_kick", LobbyPackets.Kick.class,
                LobbyPackets.Kick::encode, LobbyPackets.Kick::decode,
                null, (msg, ctx) -> AirdropLobbyService.handleKick(ctx.get().sender(), msg.lobbyId(), msg.targetUuid()));
            channel.register("lobby_cancel_invite", LobbyPackets.CancelInvite.class,
                LobbyPackets.CancelInvite::encode, LobbyPackets.CancelInvite::decode,
                null, (msg, ctx) -> AirdropLobbyService.handleCancelInvite(ctx.get().sender(), msg.lobbyId(), msg.targetUuid()));
            channel.register("lobby_leave", LobbyPackets.Leave.class,
                LobbyPackets.Leave::encode, LobbyPackets.Leave::decode,
                null, (msg, ctx) -> AirdropLobbyService.handleLeave(ctx.get().sender(), msg.lobbyId()));
            channel.register("lobby_go", LobbyPackets.Go.class,
                LobbyPackets.Go::encode, LobbyPackets.Go::decode,
                null, (msg, ctx) -> AirdropLobbyService.handleGo(ctx.get().sender(), msg));
            channel.register("lobby_sync", LobbyPackets.SyncLobby.class,
                LobbyPackets.SyncLobby::encode, LobbyPackets.SyncLobby::decode,
                (msg, ctx) -> net.mcreator.radiotowers.SafeClientCalls.applyLobbySync(msg), null);
            channel.register("lobby_countdown", LobbyPackets.Countdown.class,
                LobbyPackets.Countdown::encode, LobbyPackets.Countdown::decode,
                (msg, ctx) -> net.mcreator.radiotowers.SafeClientCalls.applyLobbyCountdown(msg), null);
            channel.register("lobby_open_screen", LobbyPackets.OpenScreen.class,
                LobbyPackets.OpenScreen::encode, LobbyPackets.OpenScreen::decode,
                (msg, ctx) -> net.mcreator.radiotowers.SafeClientCalls.openLobbyScreen(msg.lobbyId(), msg.panelPos()), null);
            registered = true;
            RadiotowersMod.LOGGER.info("[Lobby] ESN channel registered (protocol {})", PROTOCOL);
        } catch (Throwable t) {
            RadiotowersMod.LOGGER.warn("[Lobby] Failed to register ESN packets: {}", t.toString());
        }
    }
}
