package net.mcreator.radiotowers.network;
import net.mcreator.radiotowers.network.RadiotowersNetwork;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;

import net.mcreator.radiotowers.RadiotowersMod;

public class RadiotowersModVariables {
	@EventBusSubscriber(modid = RadiotowersMod.MODID)
	public static class EventBusVariableHandlers {
		@SubscribeEvent
		public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
			if (event.getEntity() instanceof ServerPlayer player) {
				SavedData mapdata = MapVariables.get(player.level());
				SavedData worlddata = WorldVariables.get(player.level());
				if (mapdata != null)
					RadiotowersNetwork.sendToPlayer(player, new SavedDataSyncMessage(0, mapdata));
				if (worlddata != null)
					RadiotowersNetwork.sendToPlayer(player, new SavedDataSyncMessage(1, worlddata));
			}
		}

		@SubscribeEvent
		public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
			if (event.getEntity() instanceof ServerPlayer player) {
				SavedData worlddata = WorldVariables.get(player.level());
				if (worlddata != null)
					RadiotowersNetwork.sendToPlayer(player, new SavedDataSyncMessage(1, worlddata));
			}
		}

		@SubscribeEvent
		public static void onWorldTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
			if (event.getLevel() instanceof ServerLevel level) {
				WorldVariables worldVariables = WorldVariables.get(level);
				if (worldVariables._syncDirty) {
					RadiotowersNetwork.sendToPlayersInDimension(level, new SavedDataSyncMessage(1, worldVariables));
					worldVariables._syncDirty = false;
				}
				MapVariables mapVariables = MapVariables.get(level);
				if (mapVariables._syncDirty) {
					RadiotowersNetwork.sendToAllPlayers( new SavedDataSyncMessage(0, mapVariables));
					mapVariables._syncDirty = false;
				}
			}
		}
	}

	public static class WorldVariables extends SavedData {
		public static final String DATA_NAME = "radiotowers_worldvars";
		boolean _syncDirty = false;

		public static WorldVariables load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
			WorldVariables data = new WorldVariables();
			data.read(tag);
			return data;
		}

		public void read(CompoundTag nbt) {
		}

		@Override
		public CompoundTag save(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
			return nbt;
		}

		public void markSyncDirty() {
			this.setDirty();
			this._syncDirty = true;
		}

		static WorldVariables clientSide = new WorldVariables();

		public static WorldVariables get(LevelAccessor world) {
			if (world instanceof ServerLevel level) {
				return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WorldVariables::new, WorldVariables::load), DATA_NAME);
			} else {
				return clientSide;
			}
		}
	}

	public static class MapVariables extends SavedData {
		public static final String DATA_NAME = "radiotowers_mapvars";
		boolean _syncDirty = false;
		/** Horizontal range from tower (blocks). Kept within render distance in spawn procedure. */
		public double PlaneSpawnRange = 28.0;
		/** Base height (Y) for plane spawn. Enforced min in code: max(120, ground+25) so plane doesn't fly through terrain. */
		public double PlaneSpawnHeight = 115.0;
		public double PlaneSpawnHeightUpRangeMax = 15.0;
		public double PlaneSpawnHeightDownRangeMax = 15.0;

		public static MapVariables load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
			MapVariables data = new MapVariables();
			data.read(tag);
			return data;
		}

		public void read(CompoundTag nbt) {
			PlaneSpawnRange = nbt.getDouble("PlaneSpawnRange");
			PlaneSpawnHeight = nbt.getDouble("PlaneSpawnHeight");
			PlaneSpawnHeightUpRangeMax = nbt.getDouble("PlaneSpawnHeightUpRangeMax");
			PlaneSpawnHeightDownRangeMax = nbt.getDouble("PlaneSpawnHeightDownRangeMax");
		}

		@Override
		public CompoundTag save(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
			nbt.putDouble("PlaneSpawnRange", PlaneSpawnRange);
			nbt.putDouble("PlaneSpawnHeight", PlaneSpawnHeight);
			nbt.putDouble("PlaneSpawnHeightUpRangeMax", PlaneSpawnHeightUpRangeMax);
			nbt.putDouble("PlaneSpawnHeightDownRangeMax", PlaneSpawnHeightDownRangeMax);
			return nbt;
		}

		public void markSyncDirty() {
			this.setDirty();
			_syncDirty = true;
		}

		static MapVariables clientSide = new MapVariables();

		public static MapVariables get(LevelAccessor world) {
			if (world instanceof ServerLevelAccessor serverLevelAcc) {
				return serverLevelAcc.getLevel().getServer().getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(new SavedData.Factory<>(MapVariables::new, MapVariables::load), DATA_NAME);
			} else {
				return clientSide;
			}
		}
	}

	public static class SavedDataSyncMessage implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
		public static final Type<SavedDataSyncMessage> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RadiotowersMod.MODID, "saved_data_sync"));
		public static final net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, SavedDataSyncMessage> STREAM_CODEC =
			net.minecraft.network.codec.StreamCodec.of((buf, msg) -> SavedDataSyncMessage.buffer(msg, buf), SavedDataSyncMessage::new);

		@Override
		public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
			return TYPE;
		}

		private final int dataType;
		private final SavedData data;

		public SavedDataSyncMessage(int dataType, SavedData data) {
			this.dataType = dataType;
			this.data = data;
		}

		public SavedDataSyncMessage(FriendlyByteBuf buffer) {
			int dataType = buffer.readInt();
			CompoundTag nbt = buffer.readNbt();
			SavedData data = null;
			if (nbt != null) {
				data = dataType == 0 ? new MapVariables() : new WorldVariables();
				if (data instanceof MapVariables mapVariables)
					mapVariables.read(nbt);
				else if (data instanceof WorldVariables worldVariables)
					worldVariables.read(nbt);
			}
			this.dataType = dataType;
			this.data = data;
		}

		public static void buffer(SavedDataSyncMessage message, FriendlyByteBuf buffer) {
			buffer.writeInt(message.dataType);
			if (message.data != null)
				buffer.writeNbt(message.data.save(new CompoundTag(), net.minecraft.core.RegistryAccess.EMPTY));
		}

		public static void handleData(final SavedDataSyncMessage message, final IPayloadContext context) {
			context.enqueueWork(() -> {
				if (message.data != null) {
					if (message.dataType == 0)
						MapVariables.clientSide.read(message.data.save(new CompoundTag(), net.minecraft.core.RegistryAccess.EMPTY));
					else
						WorldVariables.clientSide.read(message.data.save(new CompoundTag(), net.minecraft.core.RegistryAccess.EMPTY));
				}
			});
		}
	}
}