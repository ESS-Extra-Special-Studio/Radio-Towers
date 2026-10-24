package net.mcreator.radiotowers.lobby;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.StringTextComponent;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.integration.AirdropCooldown;
import net.mcreator.radiotowers.integration.AirdropDifficultyTier;
import net.mcreator.radiotowers.integration.EslWaveIntegration;
import net.mcreator.radiotowers.integration.PendingAirdropStorage;
import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;
import net.mcreator.radiotowers.network.AirdropStatePacket;
import net.mcreator.radiotowers.network.lobby.AirdropLobbyNetwork;
import net.mcreator.radiotowers.network.lobby.LobbyPackets;
import net.mcreator.radiotowers.procedures.RadioPanelOnBlockRightClickedProcedure;
import net.mcreator.radiotowers.init.RadiotowersModSounds;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;
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
@Mod.EventBusSubscriber(modid = RadiotowersMod.MODID)
public final class AirdropLobbyService {

    private static boolean busRegistered;

    private static final Map<UUID, PendingGo> PENDING_GO = new HashMap<>();
    private static final Map<UUID, Long> COUNTDOWN_END = new HashMap<>();

    private static class PendingGo {
        private final UUID hostUuid;
        private final BlockPos panelPos;
        private final int totalDifficulty;
        private final List<String> itemIds;
        private final List<Integer> quantities;
        public PendingGo(UUID hostUuid, BlockPos panelPos, int totalDifficulty, List<String> itemIds, List<Integer> quantities) {
            this.hostUuid = hostUuid;
            this.panelPos = panelPos;
            this.totalDifficulty = totalDifficulty;
            this.itemIds = itemIds;
            this.quantities = quantities;
        }
        public UUID hostUuid() { return this.hostUuid; }
        public BlockPos panelPos() { return this.panelPos; }
        public int totalDifficulty() { return this.totalDifficulty; }
        public List<String> itemIds() { return this.itemIds; }
        public List<Integer> quantities() { return this.quantities; }
    }

    private AirdropLobbyService() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded("extraspeciallib") && ModList.get().isLoaded("esn")
            && AirdropLobbyNetwork.isAvailable();
    }

    private static void ensureBus() {
        if (busRegistered || !ModList.get().isLoaded("extraspeciallib")) return;
        busRegistered = true;
        EslLobbyManager.init();
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

    public static void applySharedCooldownWave(ServerWorld level, Collection<UUID> members) {
        if (level == null || members == null) return;
        for (UUID uuid : members) {
            if (uuid == null) continue;
            AirdropCooldown.setCooldownAfterWaveDelivery(level, uuid);
            ServerPlayerEntity player = level.getServer() != null
                ? level.getServer().getPlayerList().getPlayer(uuid) : null;
            if (player != null) {
                long end = AirdropCooldown.getCooldownEndGameTime(level, uuid);
                RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player),
                    new AirdropStatePacket(false, end));
            }
        }
    }

    public static void handleOpen(ServerPlayerEntity host, BlockPos panelPos) {
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

    public static void handleInvite(ServerPlayerEntity host, UUID lobbyId, String targetName) {
        if (!isAvailable() || host == null) return;
        ensureBus();
        EslLobby lobby = requireHostLobby(host, lobbyId);
        if (lobby == null) return;
        if (targetName == null || targetName.trim().isEmpty()) {
            host.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.player_offline"), net.minecraft.util.Util.NIL_UUID);
            return;
        }
        ServerPlayerEntity target = findOnlineByName(host.getServer(), targetName.trim());
        if (target == null) {
            host.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.player_offline"), net.minecraft.util.Util.NIL_UUID);
            return;
        }
        if (target.getUUID().equals(host.getUUID())) return;
        EslLobby already = EslLobbyManager.findByMember(target.getUUID());
        if (already != null && already.state() != EslLobby.State.CLOSED) {
            host.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.target_busy"), net.minecraft.util.Util.NIL_UUID);
            return;
        }
        if (!lobby.invite(target.getUUID(), target.getGameProfile().getName())) {
            host.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.invite_failed"), net.minecraft.util.Util.NIL_UUID);
            return;
        }
        sendInviteChat(host, target, lobby);
        syncToLobby(lobby);
    }

    public static void handleAccept(ServerPlayerEntity player, UUID lobbyId) {
        if (!isAvailable() || player == null) return;
        ensureBus();
        EslLobby lobby = resolveLobby(player, lobbyId);
        if (lobby == null) {
            player.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.no_invite"), net.minecraft.util.Util.NIL_UUID);
            return;
        }
        if (!lobby.join(player.getUUID())) {
            player.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.accept_failed"), net.minecraft.util.Util.NIL_UUID);
            return;
        }
        teleportNearPanel(player, lobby.anchor());
        player.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.joined"), net.minecraft.util.Util.NIL_UUID);
        sendReadyHint(player);
        syncToLobby(lobby);
    }

    public static void handleDecline(ServerPlayerEntity player, UUID lobbyId) {
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
        player.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.declined"), net.minecraft.util.Util.NIL_UUID);
        syncToLobby(lobby);
    }

    public static void handleReady(ServerPlayerEntity player, UUID lobbyId, boolean ready) {
        if (!isAvailable() || player == null) return;
        ensureBus();
        EslLobby lobby = resolveLobby(player, lobbyId);
        if (lobby == null) {
            player.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.not_in_lobby"), net.minecraft.util.Util.NIL_UUID);
            return;
        }
        if (!ready) {
            // ESL has no un-ready; ignore false for v1
            syncToLobby(lobby);
            return;
        }
        if (!lobby.setReady(player.getUUID())) {
            player.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.ready_failed"), net.minecraft.util.Util.NIL_UUID);
            return;
        }
        player.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.ready_ok"), net.minecraft.util.Util.NIL_UUID);
        syncToLobby(lobby);
    }

    public static void handleKick(ServerPlayerEntity host, UUID lobbyId, UUID target) {
        if (!isAvailable() || host == null || target == null) return;
        ensureBus();
        EslLobby lobby = requireHostLobby(host, lobbyId);
        if (lobby == null) return;
        if (!lobby.kick(target)) return;
        ServerPlayerEntity victim = host.getServer().getPlayerList().getPlayer(target);
        if (victim != null) {
            victim.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.kicked"), net.minecraft.util.Util.NIL_UUID);
        }
        syncToLobby(lobby);
    }

    public static void handleCancelInvite(ServerPlayerEntity host, UUID lobbyId, UUID target) {
        if (!isAvailable() || host == null || target == null) return;
        ensureBus();
        EslLobby lobby = requireHostLobby(host, lobbyId);
        if (lobby == null) return;
        if (!lobby.cancelInvite(target)) return;
        ServerPlayerEntity victim = host.getServer().getPlayerList().getPlayer(target);
        if (victim != null) {
            victim.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.invite_cancelled"), net.minecraft.util.Util.NIL_UUID);
        }
        syncToLobby(lobby);
    }

    public static void handleLeave(ServerPlayerEntity player, UUID lobbyId) {
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
            if (lobby.state() != EslLobby.State.CLOSED) {
                EslLobbyManager.cancel(lobby);
            } else {
                EslLobbyManager.cancel(lobby);
            }
            // Closed event sends lobby_closed to members.
        } else {
            syncToLobby(lobby);
        }
        player.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.left"), net.minecraft.util.Util.NIL_UUID);
    }

    public static void handleGo(ServerPlayerEntity host, LobbyPackets.Go msg) {
        if (!isAvailable() || host == null || msg == null) return;
        ensureBus();
        EslLobby lobby = requireHostLobby(host, msg.lobbyId());
        if (lobby == null) return;
        BlockPos panelPos = msg.panelPos() != null ? msg.panelPos() : lobby.anchor();
        if (hasPendingInvites(lobby) || !lobby.canStart()) {
            host.sendMessage(new StringTextComponent(goBlockReason(lobby)), net.minecraft.util.Util.NIL_UUID);
            syncToLobby(lobby);
            return;
        }
        boolean party = lobby.memberUuids().size() > 1;
        if (!party) {
            // Empty lobby Go = same as solo lever (no countdown ceremony)
            startAirdrop(host, panelPos, msg.totalDifficulty(), msg.itemIds(), msg.quantities(),
                lobby.memberUuids(), null, host.getUUID());
            EslLobbyManager.cancel(lobby);
            PENDING_GO.remove(lobby.id());
            COUNTDOWN_END.remove(lobby.id());
            return;
        }
        if (!lobby.start()) {
            host.sendMessage(new StringTextComponent(goBlockReason(lobby)), net.minecraft.util.Util.NIL_UUID);
            syncToLobby(lobby);
            return;
        }
        PENDING_GO.put(lobby.id(), new PendingGo(host.getUUID(), panelPos.immutable(), msg.totalDifficulty(),
            new ArrayList<>(msg.itemIds() != null ? msg.itemIds() : java.util.Collections.emptyList()),
            new ArrayList<>(msg.quantities() != null ? msg.quantities() : java.util.Collections.emptyList())));
        int seconds = AirdropConfig.getLobbyCountdownSeconds();
        COUNTDOWN_END.put(lobby.id(), host.getLevel().getGameTime() + seconds * 20L);
        broadcastCountdown(lobby, seconds);
        syncToLobby(lobby);
    }

    /**
     * @param orderOwner player whose cooldown / pending slot owns this run (host UUID even if a
     *                   guest is the live {@code sender} after host disconnect mid-countdown)
     */
    private static void startAirdrop(ServerPlayerEntity sender, BlockPos panelPos, int totalDifficulty,
                                     List<String> itemIds, List<Integer> quantities,
                                     Set<UUID> members, @Nullable UUID lobbyId, @Nullable UUID orderOwner) {
        List<String> ids = itemIds != null ? new ArrayList<>(itemIds) : new ArrayList<>();
        List<Integer> qty = quantities != null ? new ArrayList<>(quantities) : new ArrayList<>();
        boolean standardAirdrop = totalDifficulty == 0 && ids.isEmpty();
        if (standardAirdrop && !AirdropConfig.ENABLE_STANDARD_AIRDROP.get()) {
            sender.sendMessage(new StringTextComponent("[RadioTowers] Standard airdrops are disabled in config."), net.minecraft.util.Util.NIL_UUID);
            return;
        }
        if (!standardAirdrop && !AirdropConfig.ENABLE_CATALOG_AIRDROP.get()) {
            sender.sendMessage(new StringTextComponent("[RadioTowers] Catalog airdrops are disabled in config."), net.minecraft.util.Util.NIL_UUID);
            return;
        }
        if (!standardAirdrop && !AirdropDifficultyTier.canStart(totalDifficulty)) return;

        ServerWorld level = sender.getLevel();
        UUID owner = orderOwner != null ? orderOwner : sender.getUUID();
        Set<UUID> party = new LinkedHashSet<>();
        party.add(owner);
        party.add(sender.getUUID());
        if (members != null) party.addAll(members);
        boolean lobbied = party.size() > 1;
        UUID storeLobbyId = lobbied ? lobbyId : null;
        boolean membersOnly = lobbied && AirdropConfig.isLobbyMembersOnlyCrates();

        boolean hasWaveBackend = !standardAirdrop
            && AirdropConfig.shouldUseZombieWavesForAirdrop()
            && (EslWaveIntegration.isEslWaveAvailable() || ZombieWavesAPILoader.isZombieWavesAPILoaded());

        if (hasWaveBackend) {
            // Always gate on the order owner (host), not the live sender — guest fallback after
            // host disconnect must not no-op on the guest's unrelated cooldown.
            if (PendingAirdropStorage.hasPendingForPlayer(level, owner)) return;
            if (AirdropCooldown.isOnCooldown(level, owner)) return;
            AirdropDifficultyTier.Tier tier = AirdropDifficultyTier.getTier(totalDifficulty);
            if (tier == null) return;
            BlockPos panel = panelPos.immutable();
            BlockPos waveStart = groundForWave(level, panel);
            ZombieWavesAPILoader.purgeWaveEntityResidue(level, waveStart);
            PendingAirdropStorage.store(level, waveStart, panel, ids, qty, totalDifficulty, owner,
                tier.numWaves, tier.numWaves, tier.apiDifficulty, party, storeLobbyId);
            // Force members-only flag from config when lobbied
            PendingAirdropStorage.Pending pending = PendingAirdropStorage.getPending(level, waveStart);
            if (pending != null && lobbied && membersOnly != pending.membersOnlyCrate) {
                // Pending is immutable for the flag — store() already sets membersOnly when lobbyId != null && size > 1
            }
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
                level.playSound(null, panel, RadiotowersModSounds.SIREN.get(), SoundCategory.NEUTRAL, 1.0f, 1.0f);
            }
            for (UUID uuid : party) {
                ServerPlayerEntity p = level.getServer().getPlayerList().getPlayer(uuid);
                if (p != null) {
                    RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> p),
                        new AirdropStatePacket(true, AirdropCooldown.getCooldownEndGameTime(level, uuid)));
                }
            }
            return;
        }

        if (AirdropCooldown.isOnCooldown(level, owner)) return;
        RadioPanelOnBlockRightClickedProcedure.execute(level, panelPos.getX(), panelPos.getY(), panelPos.getZ(), ids, qty, totalDifficulty);
        if (membersOnly) {
            PendingAirdropStorage.putNextDeliveryMembers(level.dimension(), party, true);
        }
        for (UUID uuid : party) {
            AirdropCooldown.setCooldownAfterStandardAirdrop(level, uuid);
            ServerPlayerEntity p = level.getServer().getPlayerList().getPlayer(uuid);
            if (p != null) {
                RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> p),
                    new AirdropStatePacket(false, AirdropCooldown.getCooldownEndGameTime(level, uuid)));
            }
        }
    }

    private static BlockPos groundForWave(ServerWorld level, BlockPos pos) {
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

    private static int surfaceY(ServerWorld level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int h = level.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        return Math.max(0, Math.min(h, level.getMaxBuildHeight() - 1));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isAvailable()) return;
        MinecraftServer server = net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        List<UUID> due = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : COUNTDOWN_END.entrySet()) {
            EslLobby lobby = EslLobbyManager.find(e.getKey());
            if (lobby == null || lobby.state() == EslLobby.State.CLOSED) {
                due.add(e.getKey());
                continue;
            }
            ServerWorld level = server.getLevel(lobby.dimension());
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
        Set<UUID> members = new LinkedHashSet<>(lobby.memberUuids());
        broadcastCountdown(lobby, 0);
        ServerPlayerEntity actor = server.getPlayerList().getPlayer(go.hostUuid());
        if (actor == null) {
            // Host disconnected mid-countdown — start from any online member so the party is not wasted.
            for (UUID uuid : members) {
                actor = server.getPlayerList().getPlayer(uuid);
                if (actor != null) break;
            }
        }
        if (actor != null) {
            startAirdrop(actor, go.panelPos(), go.totalDifficulty(), go.itemIds(), go.quantities(),
                members, lobby.id(), go.hostUuid());
        } else {
            RadiotowersMod.LOGGER.warn("[Lobby] Countdown finished for {} but no members online — aborting", lobbyId);
        }
        EslLobbyManager.cancel(lobby);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!isAvailable()) return;
        Object player$src = event.getEntity(); ServerPlayerEntity player = (player$src instanceof ServerPlayerEntity) ? (ServerPlayerEntity) player$src : null;
        if (!(player != null)) return;
        ensureBus();
        EslLobby lobby = EslLobbyManager.findByMember(player.getUUID());
        if (lobby == null || lobby.state() == EslLobby.State.CLOSED) return;
        if (lobby.hostUuid().equals(player.getUUID())) {
            UUID id = lobby.id();
            // Mid-countdown: start for remaining online members instead of wasting the party.
            if (PENDING_GO.containsKey(id) && COUNTDOWN_END.containsKey(id)) {
                finishCountdown(player.getServer(), id);
            } else {
                EslLobbyManager.cancel(lobby);
            }
        } else {
            lobby.leave(player.getUUID());
            syncToLobby(lobby);
        }
    }

    @SubscribeEvent
    public static void onLobbyEvent(EslLobbyEvent event) {
    EslLobbyEvent.Closed closed = (event instanceof EslLobbyEvent.Closed) ? (EslLobbyEvent.Closed) event : null;
        if (closed != null) {
            PENDING_GO.remove(closed.lobby().id());
            COUNTDOWN_END.remove(closed.lobby().id());
            MinecraftServer server = closed.lobby().level() != null ? closed.lobby().level().getServer() : null;
            if (server != null) {
                notifyClosed(server, closed.lobby().id(), closed.lobby().allMemberUuids());
            }
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

    private static EslLobby ensureLobby(ServerPlayerEntity host, BlockPos panelPos) {
        EslLobby existing = findLobbyAt(host.getLevel(), panelPos);
        if (existing != null) {
            if (!existing.hostUuid().equals(host.getUUID())) {
                host.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.not_host"), net.minecraft.util.Util.NIL_UUID);
                return null;
            }
            return existing;
        }
        EslLobby other = EslLobbyManager.findByMember(host.getUUID());
        if (other != null && other.state() != EslLobby.State.CLOSED) {
            if (other.hostUuid().equals(host.getUUID()) && other.anchor().equals(panelPos.immutable())) {
                return other;
            }
            host.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.already_in_lobby"), net.minecraft.util.Util.NIL_UUID);
            return null;
        }
        EslLobby lobby = EslLobbyManager.create(host.getLevel(), panelPos.immutable(),
            host.getUUID(), host.getGameProfile().getName(), AirdropConfig.getLobbyMaxPlayers());
        lobby.setInviteTimeoutTicks((int) AirdropConfig.getLobbyInviteTimeoutTicks());
        return lobby;
    }

    private static @Nullable EslLobby findLobbyAt(ServerWorld level, BlockPos panelPos) {
        BlockPos key = panelPos.immutable();
        for (EslLobby lobby : EslLobbyManager.activeIn(level.dimension())) {
            if (lobby.anchor().equals(key)) return lobby;
        }
        return null;
    }

    private static @Nullable EslLobby requireHostLobby(ServerPlayerEntity host, UUID lobbyId) {
        EslLobby lobby = lobbyId != null ? EslLobbyManager.find(lobbyId) : EslLobbyManager.findByMember(host.getUUID());
        if (lobby == null || lobby.state() == EslLobby.State.CLOSED) {
            host.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.not_in_lobby"), net.minecraft.util.Util.NIL_UUID);
            return null;
        }
        if (!lobby.hostUuid().equals(host.getUUID())) {
            host.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.not_host"), net.minecraft.util.Util.NIL_UUID);
            return null;
        }
        return lobby;
    }

    private static @Nullable EslLobby resolveLobby(ServerPlayerEntity player, @Nullable UUID lobbyId) {
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
            return new TranslationTextComponent("message.radiotowers.lobby.waiting_invites", pending).getString();
        }
        if (notReady > 0) {
            return new TranslationTextComponent("message.radiotowers.lobby.waiting_ready", notReady).getString();
        }
        if (lobby.state() == EslLobby.State.COUNTDOWN) {
            return new TranslationTextComponent("message.radiotowers.lobby.counting_down").getString();
        }
        return "";
    }

    private static void syncToLobby(EslLobby lobby) {
        if (lobby == null || lobby.state() == EslLobby.State.CLOSED) return;
        MinecraftServer server = lobby.level().getServer();
        if (server == null) return;
        for (UUID uuid : lobby.allMemberUuids()) {
            ServerPlayerEntity p = server.getPlayerList().getPlayer(uuid);
            if (p != null) syncTo(p, lobby);
        }
    }

    private static void syncTo(ServerPlayerEntity viewer, EslLobby lobby) {
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
            ServerPlayerEntity p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                AirdropLobbyNetwork.channel().sendTo(p, pkt);
                if (seconds > 0) {
                    p.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.countdown", seconds), net.minecraft.util.Util.NIL_UUID);
                }
            }
        }
    }

    private static void notifyClosed(MinecraftServer server, UUID lobbyId, Collection<UUID> members) {
        if (!AirdropLobbyNetwork.isAvailable() || server == null || lobbyId == null) return;
        LobbyPackets.Closed pkt = new LobbyPackets.Closed(lobbyId);
        if (members != null) {
            for (UUID uuid : members) {
                ServerPlayerEntity p = server.getPlayerList().getPlayer(uuid);
                if (p != null) AirdropLobbyNetwork.channel().sendTo(p, pkt);
            }
        }
    }

    private static void sendInviteChat(ServerPlayerEntity host, ServerPlayerEntity target, EslLobby lobby) {
        String id = lobby.id().toString();
        IFormattableTextComponent msg = new TranslationTextComponent("message.radiotowers.lobby.invite",
                host.getGameProfile().getName())
            .append(new StringTextComponent(" "))
            .append(new TranslationTextComponent("message.radiotowers.lobby.invite_tp_warn")
                .withStyle(TextFormatting.YELLOW))
            .append(new StringTextComponent(" "));
        IFormattableTextComponent accept = new StringTextComponent("[Accept]")
            .withStyle(Style.EMPTY.withColor(TextFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/radiotowers lobby accept " + id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TranslationTextComponent("message.radiotowers.lobby.accept_hover"))));
        IFormattableTextComponent decline = new StringTextComponent("[Decline]")
            .withStyle(Style.EMPTY.withColor(TextFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/radiotowers lobby decline " + id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TranslationTextComponent("message.radiotowers.lobby.decline_hover"))));
        target.sendMessage(msg.append(accept).append(new StringTextComponent(" ")).append(decline), net.minecraft.util.Util.NIL_UUID);
        host.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.invite_sent",
            target.getGameProfile().getName()), net.minecraft.util.Util.NIL_UUID);
    }

    private static void sendReadyHint(ServerPlayerEntity player) {
        IFormattableTextComponent ready = new StringTextComponent("[Ready]")
            .withStyle(Style.EMPTY.withColor(TextFormatting.AQUA).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/radiotowers lobby ready"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TranslationTextComponent("message.radiotowers.lobby.ready_hover"))));
        player.sendMessage(new TranslationTextComponent("message.radiotowers.lobby.ready_hint")
            .append(new StringTextComponent(" ")).append(ready)
            .append(new StringTextComponent(" "))
            .append(new TranslationTextComponent("message.radiotowers.lobby.ready_cmd_fallback")), net.minecraft.util.Util.NIL_UUID);
    }

    public static void teleportNearPanel(ServerPlayerEntity player, BlockPos panel) {
        ServerWorld level = player.getLevel();
        int x = panel.getX();
        int z = panel.getZ() + 2;
        int y = level.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        y = Math.max(0 + 1, Math.min(y, level.getMaxBuildHeight() - 2));
        BlockPos dest = new BlockPos(x, y, z);
        if (!level.getBlockState(dest).isAir() || !level.getBlockState(dest.above()).isAir()) {
            dest = panel.above();
        }
        player.teleportTo(level, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5,
            player.yRot, player.xRot);
    }

    @Nullable
    private static ServerPlayerEntity findOnlineByName(MinecraftServer server, String name) {
        if (server == null || name == null) return null;
        String needle = name.toLowerCase(Locale.ROOT);
        for (ServerPlayerEntity p : server.getPlayerList().getPlayers()) {
            if (p.getGameProfile().getName().equalsIgnoreCase(name)) return p;
        }
        for (ServerPlayerEntity p : server.getPlayerList().getPlayers()) {
            if (p.getGameProfile().getName().toLowerCase(Locale.ROOT).startsWith(needle)) return p;
        }
        return null;
    }
}
