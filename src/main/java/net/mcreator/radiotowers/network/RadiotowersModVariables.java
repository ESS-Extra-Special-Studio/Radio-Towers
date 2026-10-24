package net.mcreator.radiotowers.network;

import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;

import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.network.PacketBuffer;
import net.minecraft.nbt.CompoundNBT;

import net.mcreator.radiotowers.RadiotowersMod;

import java.util.function.Supplier;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class RadiotowersModVariables {
	@SubscribeEvent
	public static void init(FMLCommonSetupEvent event) {
		RadiotowersMod.addNetworkMessage(SavedDataSyncMessage.class, SavedDataSyncMessage::buffer, SavedDataSyncMessage::new, SavedDataSyncMessage::handleData);
	}

	@Mod.EventBusSubscriber
	public static class EventBusVariableHandlers {
		@SubscribeEvent
		public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		Object player$src = event.getEntity(); ServerPlayerEntity player = (player$src instanceof ServerPlayerEntity) ? (ServerPlayerEntity) player$src : null;
			if (player != null) {
				WorldSavedData mapdata = MapVariables.get(player.level);
				WorldSavedData worlddata = WorldVariables.get(player.level);
				if (mapdata != null)
					RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new SavedDataSyncMessage(0, mapdata));
				if (worlddata != null)
					RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new SavedDataSyncMessage(1, worlddata));
			}
		}

		@SubscribeEvent
		public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		Object player$src = event.getEntity(); ServerPlayerEntity player = (player$src instanceof ServerPlayerEntity) ? (ServerPlayerEntity) player$src : null;
			if (player != null) {
				WorldSavedData worlddata = WorldVariables.get(player.level);
				if (worlddata != null)
					RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new SavedDataSyncMessage(1, worlddata));
			}
		}

		@SubscribeEvent
		public static void onWorldTick(TickEvent.WorldTickEvent event) {
		ServerWorld level = (event.world instanceof ServerWorld) ? (ServerWorld) event.world : null;
			if (event.phase == TickEvent.Phase.END && level != null) {
				WorldVariables worldVariables = WorldVariables.get(level);
				if (worldVariables._syncDirty) {
					RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.DIMENSION.with(level::dimension), new SavedDataSyncMessage(1, worldVariables));
					worldVariables._syncDirty = false;
				}
				MapVariables mapVariables = MapVariables.get(level);
				if (mapVariables._syncDirty) {
					RadiotowersMod.PACKET_HANDLER.send(PacketDistributor.ALL.noArg(), new SavedDataSyncMessage(0, mapVariables));
					mapVariables._syncDirty = false;
				}
			}
		}
	}

	public static class WorldVariables extends WorldSavedData {
		public static final String DATA_NAME = "radiotowers_worldvars";
		boolean _syncDirty = false;

		public WorldVariables() {
			super(DATA_NAME);
		}

		@Override
		public void load(CompoundNBT tag) {
			read(tag);
		}

		public void read(CompoundNBT nbt) {
		}

		@Override
		public CompoundNBT save(CompoundNBT nbt) {
			return nbt;
		}

		public void markSyncDirty() {
			this.setDirty();
			this._syncDirty = true;
		}

		static WorldVariables clientSide = new WorldVariables();

		public static WorldVariables get(IWorld world) {
		ServerWorld level = (world instanceof ServerWorld) ? (ServerWorld) world : null;
			if (level != null) {
				return level.getDataStorage().computeIfAbsent(WorldVariables::new, DATA_NAME);
			} else {
				return clientSide;
			}
		}
	}

	public static class MapVariables extends WorldSavedData {
		public static final String DATA_NAME = "radiotowers_mapvars";
		boolean _syncDirty = false;
		/** Horizontal range from tower (blocks). Kept within render distance in spawn procedure. */
		public double PlaneSpawnRange = 28.0;
		/** Base height (Y) for plane spawn. Enforced min in code: max(120, ground+25) so plane doesn't fly through terrain. */
		public double PlaneSpawnHeight = 115.0;
		public double PlaneSpawnHeightUpRangeMax = 15.0;
		public double PlaneSpawnHeightDownRangeMax = 15.0;

		public MapVariables() {
			super(DATA_NAME);
		}

		@Override
		public void load(CompoundNBT tag) {
			read(tag);
		}

		public void read(CompoundNBT nbt) {
			PlaneSpawnRange = nbt.getDouble("PlaneSpawnRange");
			PlaneSpawnHeight = nbt.getDouble("PlaneSpawnHeight");
			PlaneSpawnHeightUpRangeMax = nbt.getDouble("PlaneSpawnHeightUpRangeMax");
			PlaneSpawnHeightDownRangeMax = nbt.getDouble("PlaneSpawnHeightDownRangeMax");
		}

		@Override
		public CompoundNBT save(CompoundNBT nbt) {
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

		public static MapVariables get(IWorld world) {
		ServerWorld level = (world instanceof ServerWorld) ? (ServerWorld) world : null;
			if (level != null) {
				return level.getServer().overworld().getDataStorage().computeIfAbsent(MapVariables::new, DATA_NAME);
			} else {
				return clientSide;
			}
		}
	}

	public static class SavedDataSyncMessage {
		private final int dataType;
		private final WorldSavedData data;

		public SavedDataSyncMessage(int dataType, WorldSavedData data) {
			this.dataType = dataType;
			this.data = data;
		}

		public SavedDataSyncMessage(PacketBuffer buffer) {
			int dataType = buffer.readInt();
			CompoundNBT nbt = buffer.readNbt();
			WorldSavedData data = null;
			if (nbt != null) {
				data = dataType == 0 ? new MapVariables() : new WorldVariables();
				MapVariables mapVariables = (data instanceof MapVariables) ? (MapVariables) data : null;
				WorldVariables worldVariables = (data instanceof WorldVariables) ? (WorldVariables) data : null;
				if (mapVariables != null)
					mapVariables.read(nbt);
				else if (worldVariables != null)
					worldVariables.read(nbt);
			}
			this.dataType = dataType;
			this.data = data;
		}

		public static void buffer(SavedDataSyncMessage message, PacketBuffer buffer) {
			buffer.writeInt(message.dataType);
			if (message.data != null)
				buffer.writeNbt(message.data.save(new CompoundNBT()));
		}

		public static void handleData(final SavedDataSyncMessage message, final Supplier<NetworkEvent.Context> contextSupplier) {
			NetworkEvent.Context context = contextSupplier.get();
			context.enqueueWork(() -> {
				if (!context.getDirection().getReceptionSide().isServer() && message.data != null) {
					if (message.dataType == 0)
						MapVariables.clientSide.read(message.data.save(new CompoundNBT()));
					else
						WorldVariables.clientSide.read(message.data.save(new CompoundNBT()));
				}
			});
			context.setPacketHandled(true);
		}
	}
}