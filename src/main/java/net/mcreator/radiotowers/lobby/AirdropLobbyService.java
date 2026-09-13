package net.mcreator.radiotowers.lobby;

import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.integration.AirdropCooldown;
import net.mcreator.radiotowers.integration.AirdropDifficultyTier;
import net.mcreator.radiotowers.integration.EslWaveIntegration;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;
import net.mcreator.radiotowers.network.AirdropStatePacket;
import net.mcreator.radiotowers.network.RadiotowersNetwork;
import net.mcreator.radiotowers.network.lobby.AirdropLobbyNetwork;
import net.mcreator.radiotowers.network.lobby.LobbyPackets;
import net.mcreator.radiotowers.procedures.RadioPanelOnBlockRightClickedProcedure;
import net.mcreator.radiotowers.init.RadiotowersModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import uk.co.extraspecialstudio.esl.lobby.EslLobby;
import uk.co.extraspecialstudio.esl.lobby.EslLobbyEvent;
import uk.co.extraspecialstudio.esl.lobby.EslLobbyManager;
import uk.co.extraspecialstudio.esl.lobby.EslLobbyMember;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RadioTowers airdrop lobby feature (ESL sessions + ESN packets via {@link AirdropLobbyNetwork}).
 * Solo / empty lobby keeps the Call Airdrop lever path unchanged.
 */
public final class AirdropLobbyService {

    private static boolean busRegistered;

    private static final Map<UUID, PendingGo> PENDING_GO = new HashMap<>();
    private static final Map<UUID, Long> COUNTDOWN_END = new HashMap<>();

    private record PendingGo(UUID hostUuid, BlockPos panelPos, int totalDifficulty,
                             List<String> itemIds, List<Integer> quantities) {}

    private AirdropLobbyService() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded("extraspeciallib") && ModList.get().isLoaded("esn");
    }

    private static void ensureBus() {
        if (busRegistered || !isAvailable()) return;
        busRegistered = true;
        EslLobbyManager.init();
        NeoForge.EVENT_BUS.register(AirdropLobbyService.class);
    }

    /** True when host has invited/joined guests — solo lever must not start. */
    public static boolean hasGuestsBlockingSoloStart(UUID hostUuid) {
        if (!isAvailable() || hostUuid == null) return false;
        EslLobby lobby = EslLobbyManager.findByMember(hostUuid);
        if (lobby == null || lobby.state() == EslLobby.State.CLOSED) return false;
        if (!lobby.hostUuid().equals(hostUuid)) return false;
        if (hasPendingInvites(lobby)) return true;
        return lobby.memberUuids().size() > 1;
    }

    public static void applySharedCooldownWave(ServerLevel level, Collection<UUID> members) {
        if (level == null || members == null) return;
        for (UUID uuid : members) {
            if (uuid == null) continue;
            AirdropCooldown.setCooldownAfterWaveDelivery(level, uuid);
            ServerPlayer player = level.getServer() != null
                ? level.getServer().getPlayerList().getPlayer(uuid) : null;
            if (player != null) {
                long end = AirdropCooldown.getCooldownEndGameTime(level, uuid);
                RadiotowersNetwork.sendToPlayer(player, new AirdropStatePacket(false, end));
            }
        }
    }

    public static void handleOpen(ServerPlayer host, BlockPos panelPos) {
        if (!isAvailable() || host == null || panelPos == null) return;
        ensureBus();
        EslLobby lobby = ensureLobby(host, panelPos);
        if (lobby == null) return;
        syncTo(host, lobby);
        if (AirdropLobbyNetwork.isAvailable()) {
            AirdropLobbyNetwork.channel().sendTo(host,
                new LobbyPackets.OpenScreen(lobby.id(), lobby.anchor()));
        }
    }

    public static void handleInvite(ServerPlayer host, UUID lobbyId, String targetName) {
        if (!isAvailable() || host == null) return;
        ensureBus();
        EslLobby lobby = requireHostLobby(host, lobbyId);
        if (lobby == null) return;
        if (targetName == null || targetName.isBlank()) {
            host.sendSystemMessage(Component.translatable("message.radiotowers.lobby.player_offline"));
            return;
        }
        ServerPlayer target = findOnlineByName(host.getServer(), targetName.trim());
        if (target == null) {
            host.sendSystemMessage(Component.translatable("message.radiotowers.lobby.player_offline"));
            return;
        }
        if (target.getUUID().equals(host.getUUID())) return;
        EslLobby already = EslLobbyManager.findByMember(target.getUUID());
        if (already != null && already.state() != EslLobby.State.CLOSED) {
            host.sendSystemMessage(Component.translatable("message.radiotowers.lobby.target_busy"));
            return;
        }
        if (!lobby.invite(target.getUUID(), target.getGameProfile().getName())) {
            host.sendSystemMessage(Component.translatable("message.radiotowers.lobby.invite_failed"));
            return;
        }
        sendInviteChat(host, target, lobby);
        syncToLobby(lobby);
    }

    public static void handleAccept(ServerPlayer player, UUID lobbyId) {
        if (!isAvailable() || player == null) return;
        ensureBus();
        EslLobby lobby = resolveLobby(player, lobbyId);
        if (lobby == null) {
            player.sendSystemMessage(Component.translatable("message.radiotowers.lobby.no_invite"));
            return;
        }
        if (!lobby.join(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.radiotowers.lobby.accept_failed"));
            return;
        }
        teleportNearPanel(player, lobby.anchor());
        player.sendSystemMessage(Component.translatable("message.radiotowers.lobby.joined"));
        sendReadyHint(player);
        syncToLobby(lobby);
    }

    public static void handleDecline(ServerPlayer player, UUID lobbyId) {
        if (!isAvailable() || player == null) return;
        ensureBus();
        EslLobby lobby = resolveLobby(player, lobbyId);
        if (lobby == null) return;
        EslLobbyMember member = lobby.getMember(player.getUUID());
        if (member == null) return;
        if (member.status() == EslLobbyMember.Status.INVITED) {
            lobby.cancelInvite(player.getUUID());
        } else {
            lobby.leave(player.getUUID());
        }
        player.sendSystemMessage(Component.translatable("message.radiotowers.lobby.declined"));
        syncToLobby(lobby);
    }

    public static void handleReady(ServerPlayer player, UUID lobbyId, boolean ready) {
        if (!isAvailable() || player == null) return;
        ensureBus();
        EslLobby lobby = resolveLobby(player, lobbyId);
        if (lobby == null) {
            player.sendSystemMessage(Component.translatable("message.radiotowers.lobby.not_in_lobby"));
            return;
        }
        if (!ready) {
            syncToLobby(lobby);
            return;
        }
        if (!lobby.setReady(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.radiotowers.lobby.ready_failed"));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.radiotowers.lobby.ready_ok"));
        syncToLobby(lobby);
    }

    public static void handleKick(ServerPlayer host, UUID lobbyId, UUID target) {
        if (!isAvailable() || host == null || target == null) return;
        ensureBus();
        EslLobby lobby = requireHostLobby(host, lobbyId);
        if (lobby == null) return;
        if (!lobby.kick(target)) return;
        ServerPlayer victim = host.getServer().getPlayerList().getPlayer(target);
        if (victim != null) {
            victim.sendSystemMessage(Component.translatable("message.radiotowers.lobby.kicked"));
        }
        syncToLobby(lobby);
    }

    public static void handleCancelInvite(ServerPlayer host, UUID lobbyId, UUID target) {
        if (!isAvailable() || host == null || target == null) return;
        ensureBus();
        EslLobby lobby = requireHostLobby(host, lobbyId);
        if (lobby == null) return;
        if (!lobby.cancelInvite(target)) return;
        ServerPlayer victim = host.getServer().getPlayerList().getPlayer(target);
        if (victim != null) {
            victim.sendSystemMessage(Component.translatable("message.radiotowers.lobby.invite_cancelled"));
        }
        syncToLobby(lobby);
    }

    public static void handleLeave(ServerPlayer player, UUID lobbyId) {
        if (!isAvailable() || player == null) return;
        ensureBus();
        EslLobby lobby = resolveLobby(player, lobbyId);
        if (lobby == null) return;
        UUID id = lobby.id();
        boolean wasHost = lobby.hostUuid().equals(player.getUUID());
        lobby.leave(player.getUUID());
        if (wasHost || lobby.state() == EslLobby.State.CLOSED) {
            PENDING_GO.remove(id);
            COUNTDOWN_END.remove(id);
            EslLobbyManager.cancel(lobby);
            notifyClosed(player.getServer(), id, lobby.anchor());
        } else {
            syncToLobby(lobby);
        }
        player.sendSystemMessage(Component.translatable("message.radiotowers.lobby.left"));
    }

    public static void handleGo(ServerPlayer host, LobbyPackets.Go msg) {
        if (!isAvailable() || host == null || msg == null) return;
        ensureBus();
        EslLobby lobby = requireHostLobby(host, msg.lobbyId());
        if (lobby == null) return;
        BlockPos panelPos = msg.panelPos() != null ? msg.panelPos() : lobby.anchor();
        if (hasPendingInvites(lobby) || !lobby.canStart()) {
            host.sendSystemMessage(Component.literal(goBlockReason(lobby)));
            syncToLobby(lobby);
            return;
        }
        boolean party = lobby.memberUuids().size() > 1;
        if (!party) {
            startAirdrop(host, panelPos, msg.totalDifficulty(), msg.itemIds(), msg.quantities(),
                lobby.memberUuids(), null);
            EslLobbyManager.cancel(lobby);
            PENDING_GO.remove(lobby.id());
            COUNTDOWN_END.remove(lobby.id());
            return;
        }
        if (!lobby.start()) {
            host.sendSystemMessage(Component.literal(goBlockReason(lobby)));
            syncToLobby(lobby);
            return;
        }
        PENDING_GO.put(lobby.id(), new PendingGo(host.getUUID(), panelPos.immutable(), msg.totalDifficulty(),
            new ArrayList<>(msg.itemIds() != null ? msg.itemIds() : List.of()),
            new ArrayList<>(msg.quantities() != null ? msg.quantities() : List.of())));
        int seconds = AirdropConfig.getLobbyCountdownSeconds();
        COUNTDOWN_END.put(lobby.id(), host.serverLevel().getGameTime() + seconds * 20L);
        broadcastCountdown(lobby, seconds);
        syncToLobby(lobby);
    }

    private static void startAirdrop(ServerPlayer sender, BlockPos panelPos, int totalDifficulty,
                                     List<String> itemIds, List<Integer> quantities,
                                     Set<UUID> members, @Nullable UUID lobbyId) {
        List<String> ids = itemIds != null ? new ArrayList<>(itemIds) : new ArrayList<>();
        List<Integer> qty = quantities != null ? new ArrayList<>(quantities) : new ArrayList<>();
        boolean standardAirdrop = totalDifficulty == 0 && ids.isEmpty();
        if (standardAirdrop && !AirdropConfig.ENABLE_STANDARD_AIRDROP.get()) {
            sender.sendSystemMessage(Component.literal("[RadioTowers] Standard airdrops are disabled in config."));
            return;
        }
        if (!standardAirdrop && !AirdropConfig.ENABLE_CATALOG_AIRDROP.get()) {
            sender.sendSystemMessage(Component.literal("[RadioTowers] Catalog airdrops are disabled in config."));
            return;
        }
        if (!standardAirdrop && !AirdropDifficultyTier.canStart(totalDifficulty)) return;

        ServerLevel level = sender.serverLevel();
        Set<UUID> party = new LinkedHashSet<>();
        party.add(sender.getUUID());
        if (members != null) party.addAll(members);
        boolean lobbied = party.size() > 1;
        UUID storeLobbyId = lobbied ? lobbyId : null;
        boolean membersOnly = lobbied && AirdropConfig.isLobbyMembersOnlyCrates();

        boolean hasWaveBackend = !standardAirdrop
            && AirdropConfig.shouldUseZombieWavesForAirdrop()
            && (EslWaveIntegration.isEslWaveAvailable() || ZombieWavesAPILoader.isZombieWavesAPILoaded());

        if (hasWaveBackend) {
            if (PendingAirdropStorage.hasPendingForPlayer(level, sender.getUUID())) return;
            if (AirdropCooldown.isOnCooldown(level, sender.getUUID())) return;
            AirdropDifficultyTier.Tier tier = AirdropDifficultyTier.getTier(totalDifficulty);
            if (tier == null) return;
            BlockPos panel = panelPos.immutable();
            BlockPos waveStart = groundForWave(level, panel);
            ZombieWavesAPILoader.purgeWaveEntityResidue(level, waveStart);
            PendingAirdropStorage.store(level, waveStart, panel, ids, qty, totalDifficulty, sender.getUUID(),
                tier.numWaves, tier.numWaves, tier.apiDifficulty, party, storeLobbyId);
            boolean waveStarted = false;
            if (EslWaveIntegration.isEslWaveAvailable()) {
                waveStarted = EslWaveIntegration.startWaveAt(level, waveStart, panel, tier.apiDifficulty, tier.numWaves,
                    AirdropDifficultyTier.getZombiesPerWaveForTier(tier.apiDifficulty), sender);
            }
            if (!waveStarted && ZombieWavesAPILoader.isZombieWavesAPILoaded()) {
                waveStarted = ZombieWavesAPILoader.startWaveAt(level, waveStart, tier.apiDifficulty, sender);
            }
            if (!waveStarted) {
                PendingAirdropStorage.removePending(level, waveStart);
                RadioPanelOnBlockRightClickedProcedure.execute(level, panel.getX(), panel.getY(), panel.getZ(), ids, qty, totalDifficulty);
                if (membersOnly) {
                    PendingAirdropStorage.putNextDeliveryMembers(level.dimension(), party, true);
                }
            } else {
                level.playSound(null, panel, RadiotowersModSounds.SIREN.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
            }
            for (UUID uuid : party) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(uuid);
                if (p != null) {
                    RadiotowersNetwork.sendToPlayer(p,
                        new AirdropStatePacket(true, AirdropCooldown.getCooldownEndGameTime(level, uuid)));
                }
            }
            return;
        }

        if (AirdropCooldown.isOnCooldown(level, sender.getUUID())) return;
        RadioPanelOnBlockRightClickedProcedure.execute(level, panelPos.getX(), panelPos.getY(), panelPos.getZ(), ids, qty, totalDifficulty);
        if (membersOnly) {
            PendingAirdropStorage.putNextDeliveryMembers(level.dimension(), party, true);
        }
        for (UUID uuid : party) {
            AirdropCooldown.setCooldownAfterStandardAirdrop(level, uuid);
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(uuid);
            if (p != null) {
                RadiotowersNetwork.sendToPlayer(p,
                    new AirdropStatePacket(false, AirdropCooldown.getCooldownEndGameTime(level, uuid)));
            }
        }
    }

    private static BlockPos groundForWave(ServerLevel level, BlockPos pos) {
        int cx = pos.getX();
        int cz = pos.getZ();
        int o = 8;
        int[] samples = new int[] {
            surfaceY(level, cx, cz),
            surfaceY(level, cx + o, cz),
            surfaceY(level, cx - o, cz),
            surfaceY(level, cx, cz + o),
            surfaceY(level, cx, cz - o)
        };
        java.util.Arrays.sort(samples);
        return new BlockPos(cx, samples[samples.length / 2], cz);
    }

    private static int surfaceY(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return Math.max(level.getMinBuildHeight(), Math.min(h, level.getMaxBuildHeight() - 1));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!isAvailable()) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        List<UUID> due = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : COUNTDOWN_END.entrySet()) {
            EslLobby lobby = EslLobbyManager.find(e.getKey());
            if (lobby == null || lobby.state() == EslLobby.State.CLOSED) {
                due.add(e.getKey());
                continue;
            }
            ServerLevel level = server.getLevel(lobby.dimension());
            if (level == null) continue;
            long remaining = e.getValue() - level.getGameTime();
            if (remaining <= 0) {
                due.add(e.getKey());
            } else if (remaining % 20 == 0) {
                broadcastCountdown(lobby, (int) Math.max(0, (remaining + 19) / 20));
            }
        }
        for (UUID id : due) finishCountdown(server, id);
    }

    private static void finishCountdown(MinecraftServer server, UUID lobbyId) {
        COUNTDOWN_END.remove(lobbyId);
        PendingGo go = PENDING_GO.remove(lobbyId);
        EslLobby lobby = EslLobbyManager.find(lobbyId);
        if (go == null || lobby == null) {
            if (lobby != null) EslLobbyManager.cancel(lobby);
            return;
        }
        lobby.markInProgress();
        ServerPlayer host = server.getPlayerList().getPlayer(go.hostUuid());
        Set<UUID> members = new LinkedHashSet<>(lobby.memberUuids());
        broadcastCountdown(lobby, 0);
        if (host != null) {
            startAirdrop(host, go.panelPos(), go.totalDifficulty(), go.itemIds(), go.quantities(),
                members, lobby.id());
        }
        EslLobbyManager.cancel(lobby);
    }

    @SubscribeEvent
    public static void onLobbyEvent(EslLobbyEvent event) {
        if (event instanceof EslLobbyEvent.Closed closed) {
            PENDING_GO.remove(closed.lobby().id());
            COUNTDOWN_END.remove(closed.lobby().id());
            return;
        }
        if (event instanceof EslLobbyEvent.InviteSent
            || event instanceof EslLobbyEvent.Joined
            || event instanceof EslLobbyEvent.Left
            || event instanceof EslLobbyEvent.Kicked
            || event instanceof EslLobbyEvent.ReadyChanged
            || event instanceof EslLobbyEvent.AllReady
            || event instanceof EslLobbyEvent.Starting
            || event instanceof EslLobbyEvent.Created) {
            syncToLobby(event.lobby());
        }
    }

    private static EslLobby ensureLobby(ServerPlayer host, BlockPos panelPos) {
        EslLobby existing = findLobbyAt(host.serverLevel(), panelPos);
        if (existing != null) {
            if (!existing.hostUuid().equals(host.getUUID())) {
                host.sendSystemMessage(Component.translatable("message.radiotowers.lobby.not_host"));
                return null;
            }
            return existing;
        }
        EslLobby other = EslLobbyManager.findByMember(host.getUUID());
        if (other != null && other.state() != EslLobby.State.CLOSED) {
            if (other.hostUuid().equals(host.getUUID()) && other.anchor().equals(panelPos.immutable())) {
                return other;
            }
            host.sendSystemMessage(Component.translatable("message.radiotowers.lobby.already_in_lobby"));
            return null;
        }
        EslLobby lobby = EslLobbyManager.create(host.serverLevel(), panelPos.immutable(),
            host.getUUID(), host.getGameProfile().getName(), AirdropConfig.getLobbyMaxPlayers());
        lobby.setInviteTimeoutTicks((int) AirdropConfig.getLobbyInviteTimeoutTicks());
        return lobby;
    }

    private static @Nullable EslLobby findLobbyAt(ServerLevel level, BlockPos panelPos) {
        BlockPos key = panelPos.immutable();
        for (EslLobby lobby : EslLobbyManager.activeIn(level.dimension())) {
            if (lobby.anchor().equals(key)) return lobby;
        }
        return null;
    }

    private static @Nullable EslLobby requireHostLobby(ServerPlayer host, UUID lobbyId) {
        EslLobby lobby = lobbyId != null ? EslLobbyManager.find(lobbyId) : EslLobbyManager.findByMember(host.getUUID());
        if (lobby == null || lobby.state() == EslLobby.State.CLOSED) {
            host.sendSystemMessage(Component.translatable("message.radiotowers.lobby.not_in_lobby"));
            return null;
        }
        if (!lobby.hostUuid().equals(host.getUUID())) {
            host.sendSystemMessage(Component.translatable("message.radiotowers.lobby.not_host"));
            return null;
        }
        return lobby;
    }

    private static @Nullable EslLobby resolveLobby(ServerPlayer player, @Nullable UUID lobbyId) {
        if (lobbyId != null) {
            EslLobby byId = EslLobbyManager.find(lobbyId);
            if (byId != null && byId.getMember(player.getUUID()) != null) return byId;
        }
        return EslLobbyManager.findByMember(player.getUUID());
    }

    private static boolean hasPendingInvites(EslLobby lobby) {
        for (EslLobbyMember m : lobby.members()) {
            if (m.status() == EslLobbyMember.Status.INVITED) return true;
        }
        return false;
    }

    public static String goBlockReason(EslLobby lobby) {
        int pending = 0;
        int notReady = 0;
        for (EslLobbyMember m : lobby.members()) {
            if (m.status() == EslLobbyMember.Status.INVITED) pending++;
            else if (m.status() == EslLobbyMember.Status.JOINED) notReady++;
        }
        if (pending > 0) {
            return Component.translatable("message.radiotowers.lobby.waiting_invites", pending).getString();
        }
        if (notReady > 0) {
            return Component.translatable("message.radiotowers.lobby.waiting_ready", notReady).getString();
        }
        if (lobby.state() == EslLobby.State.COUNTDOWN) {
            return Component.translatable("message.radiotowers.lobby.counting_down").getString();
        }
        return "";
    }

    private static void syncToLobby(EslLobby lobby) {
        if (lobby == null || lobby.state() == EslLobby.State.CLOSED) return;
        MinecraftServer server = lobby.level().getServer();
        if (server == null) return;
        for (UUID uuid : lobby.allMemberUuids()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) syncTo(p, lobby);
        }
    }

    private static void syncTo(ServerPlayer viewer, EslLobby lobby) {
        if (!AirdropLobbyNetwork.isAvailable() || viewer == null || lobby == null) return;
        List<LobbyPackets.MemberSnapshot> members = new ArrayList<>();
        for (EslLobbyMember m : lobby.members()) {
            members.add(new LobbyPackets.MemberSnapshot(m.uuid(), m.displayName(), m.status().name()));
        }
        boolean canStart = lobby.canStart() && !hasPendingInvites(lobby)
            && lobby.state() != EslLobby.State.COUNTDOWN
            && lobby.state() != EslLobby.State.IN_PROGRESS;
        String reason = canStart ? "" : goBlockReason(lobby);
        AirdropLobbyNetwork.channel().sendTo(viewer, new LobbyPackets.SyncLobby(
            lobby.id(), lobby.hostUuid(), lobby.anchor(), lobby.state().name(), reason, canStart, members));
    }

    private static void broadcastCountdown(EslLobby lobby, int seconds) {
        if (!AirdropLobbyNetwork.isAvailable()) return;
        LobbyPackets.Countdown pkt = new LobbyPackets.Countdown(lobby.id(), seconds);
        MinecraftServer server = lobby.level().getServer();
        if (server == null) return;
        for (UUID uuid : lobby.memberUuids()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                AirdropLobbyNetwork.channel().sendTo(p, pkt);
                if (seconds > 0) {
                    p.sendSystemMessage(Component.translatable("message.radiotowers.lobby.countdown", seconds));
                }
            }
        }
    }

    private static void notifyClosed(MinecraftServer server, UUID lobbyId, BlockPos panel) {
        // Clients clear via leave / screen close; no dedicated Closed packet in v1.
    }

    private static void sendInviteChat(ServerPlayer host, ServerPlayer target, EslLobby lobby) {
        String id = lobby.id().toString();
        MutableComponent msg = Component.translatable("message.radiotowers.lobby.invite",
                host.getGameProfile().getName())
            .append(Component.literal(" "))
            .append(Component.translatable("message.radiotowers.lobby.invite_tp_warn")
                .withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" "));
        MutableComponent accept = Component.literal("[Accept]")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/radiotowers lobby accept " + id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.translatable("message.radiotowers.lobby.accept_hover"))));
        MutableComponent decline = Component.literal("[Decline]")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/radiotowers lobby decline " + id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.translatable("message.radiotowers.lobby.decline_hover"))));
        target.sendSystemMessage(msg.append(accept).append(Component.literal(" ")).append(decline));
        host.sendSystemMessage(Component.translatable("message.radiotowers.lobby.invite_sent",
            target.getGameProfile().getName()));
    }

    private static void sendReadyHint(ServerPlayer player) {
        MutableComponent ready = Component.literal("[Ready]")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/radiotowers lobby ready"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.translatable("message.radiotowers.lobby.ready_hover"))));
        player.sendSystemMessage(Component.translatable("message.radiotowers.lobby.ready_hint")
            .append(Component.literal(" ")).append(ready)
            .append(Component.literal(" "))
            .append(Component.translatable("message.radiotowers.lobby.ready_cmd_fallback")));
    }

    public static void teleportNearPanel(ServerPlayer player, BlockPos panel) {
        ServerLevel level = player.serverLevel();
        int x = panel.getX();
        int z = panel.getZ() + 2;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        y = Math.max(level.getMinBuildHeight() + 1, Math.min(y, level.getMaxBuildHeight() - 2));
        BlockPos dest = new BlockPos(x, y, z);
        if (!level.getBlockState(dest).isAir() || !level.getBlockState(dest.above()).isAir()) {
            dest = panel.above();
        }
        player.teleportTo(level, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5,
            player.getYRot(), player.getXRot());
    }

    @Nullable
    private static ServerPlayer findOnlineByName(MinecraftServer server, String name) {
        if (server == null || name == null) return null;
        String needle = name.toLowerCase(Locale.ROOT);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getGameProfile().getName().equalsIgnoreCase(name)) return p;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getGameProfile().getName().toLowerCase(Locale.ROOT).startsWith(needle)) return p;
        }
        return null;
    }
}
