package net.mcreator.radiotowers.entity;

import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import net.mcreator.radiotowers.procedures.AirdropentityOnInitialEntitySpawnProcedure;

import java.util.ArrayList;
import java.util.List;
import net.mcreator.radiotowers.procedures.AirdropentityOnEntityTickUpdateProcedure;
import net.mcreator.radiotowers.init.RadiotowersModEntities;

import javax.annotation.Nullable;

public class AirdropentityEntity extends Monster {
	public static final EntityDataAccessor<Boolean> DATA_IsOpening = SynchedEntityData.defineId(AirdropentityEntity.class, EntityDataSerializers.BOOLEAN);
	public final AnimationState animationState0 = new AnimationState();
	public final AnimationState animationState1 = new AnimationState();
	private List<String> airdropItemIds = new ArrayList<>();
	private List<Integer> airdropQuantities = new ArrayList<>();
	private int airdropDifficulty;
	private final java.util.Set<java.util.UUID> lobbyMembers = new java.util.LinkedHashSet<>();
	private boolean membersOnlyCrate;

	public void setAirdropOrder(List<String> itemIds, List<Integer> quantities) {
		this.airdropItemIds = itemIds != null ? new ArrayList<>(itemIds) : new ArrayList<>();
		this.airdropQuantities = quantities != null ? new ArrayList<>(quantities) : new ArrayList<>();
		if (this.airdropQuantities.size() != this.airdropItemIds.size()) {
			while (this.airdropQuantities.size() < this.airdropItemIds.size())
				this.airdropQuantities.add(1);
			if (this.airdropQuantities.size() > this.airdropItemIds.size())
				this.airdropQuantities = this.airdropQuantities.subList(0, this.airdropItemIds.size());
		}
	}

	public void setLobbyMembers(java.util.Collection<java.util.UUID> members, boolean membersOnly) {
		lobbyMembers.clear();
		if (members != null) lobbyMembers.addAll(members);
		this.membersOnlyCrate = membersOnly && !lobbyMembers.isEmpty();
	}

	public java.util.Set<java.util.UUID> getLobbyMembers() {
		return java.util.Collections.unmodifiableSet(lobbyMembers);
	}

	public boolean isMembersOnlyCrate() {
		return membersOnlyCrate;
	}

	public List<String> getAirdropItemIds() { return new ArrayList<>(airdropItemIds); }
	public List<Integer> getAirdropQuantities() { return new ArrayList<>(airdropQuantities); }
	public boolean hasAirdropOrder() { return !airdropItemIds.isEmpty(); }
	public void setAirdropDifficulty(int difficulty) { this.airdropDifficulty = Math.max(0, difficulty); }
	public int getAirdropDifficulty() { return airdropDifficulty; }

	public AirdropentityEntity(EntityType<AirdropentityEntity> type, Level world) {
		super(type, world);
		xpReward = 0;
		setNoAi(true);
		setPersistenceRequired();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_IsOpening, true);
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean hurt(DamageSource damagesource, float amount) {
		if (damagesource.is(DamageTypes.IN_FIRE))
			return false;
		if (damagesource.getDirectEntity() instanceof AbstractArrow)
			return false;
		if (damagesource.getDirectEntity() instanceof Player)
			return false;
		if (damagesource.getDirectEntity() instanceof ThrownPotion || damagesource.getDirectEntity() instanceof AreaEffectCloud)
			return false;
		if (damagesource.is(DamageTypes.FALL))
			return false;
		if (damagesource.is(DamageTypes.CACTUS))
			return false;
		if (damagesource.is(DamageTypes.DROWN))
			return false;
		if (damagesource.is(DamageTypes.LIGHTNING_BOLT))
			return false;
		if (damagesource.is(DamageTypes.EXPLOSION) || damagesource.is(DamageTypes.PLAYER_EXPLOSION))
			return false;
		if (damagesource.is(DamageTypes.TRIDENT))
			return false;
		if (damagesource.is(DamageTypes.FALLING_ANVIL))
			return false;
		if (damagesource.is(DamageTypes.DRAGON_BREATH))
			return false;
		if (damagesource.is(DamageTypes.WITHER) || damagesource.is(DamageTypes.WITHER_SKULL))
			return false;
		return super.hurt(damagesource, amount);
	}

	@Override
	public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
		return true;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData livingdata) {
		SpawnGroupData retval = super.finalizeSpawn(world, difficulty, reason, livingdata);
		AirdropentityOnInitialEntitySpawnProcedure.execute(this);
		return retval;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag compound) {
		super.addAdditionalSaveData(compound);
		compound.putBoolean("DataIsOpening", this.entityData.get(DATA_IsOpening));
		ListTag ids = new ListTag();
		for (String s : airdropItemIds) ids.add(net.minecraft.nbt.StringTag.valueOf(s));
		compound.put("AirdropItemIds", ids);
		ListTag qty = new ListTag();
		for (Integer i : airdropQuantities) qty.add(net.minecraft.nbt.IntTag.valueOf(i));
		compound.put("AirdropQuantities", qty);
		compound.putInt("AirdropDifficulty", airdropDifficulty);
		compound.putBoolean("MembersOnlyCrate", membersOnlyCrate);
		ListTag members = new ListTag();
		for (java.util.UUID u : lobbyMembers) members.add(net.minecraft.nbt.StringTag.valueOf(u.toString()));
		compound.put("LobbyMembers", members);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag compound) {
		super.readAdditionalSaveData(compound);
		if (compound.contains("DataIsOpening"))
			this.entityData.set(DATA_IsOpening, compound.getBoolean("DataIsOpening"));
		airdropItemIds.clear();
		if (compound.contains("AirdropItemIds", 9)) {
			ListTag ids = compound.getList("AirdropItemIds", 8);
			for (int i = 0; i < ids.size(); i++) airdropItemIds.add(ids.get(i).getAsString());
		}
		airdropQuantities.clear();
		if (compound.contains("AirdropQuantities", 9)) {
			ListTag qty = compound.getList("AirdropQuantities", 3);
			for (int i = 0; i < qty.size(); i++) airdropQuantities.add(((net.minecraft.nbt.NumericTag) qty.get(i)).getAsInt());
		}
		if (compound.contains("AirdropDifficulty", 3)) airdropDifficulty = compound.getInt("AirdropDifficulty");
		membersOnlyCrate = compound.getBoolean("MembersOnlyCrate");
		lobbyMembers.clear();
		if (compound.contains("LobbyMembers", 9)) {
			ListTag members = compound.getList("LobbyMembers", 8);
			for (int i = 0; i < members.size(); i++) {
				try {
					lobbyMembers.add(java.util.UUID.fromString(members.get(i).getAsString()));
				} catch (Exception ignored) {}
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			this.animationState0.animateWhen(AirdropentityOnInitialEntitySpawnProcedure.execute(this), this.tickCount);
			this.animationState1.animateWhen(true, this.tickCount);
		}
	}

	@Override
	public void baseTick() {
		super.baseTick();
		AirdropentityOnEntityTickUpdateProcedure.execute(this.level(), this.getX(), this.getY(), this.getZ(), this);
	}

	public static void init() {
	}

	public static AttributeSupplier.Builder createAttributes() {
		AttributeSupplier.Builder builder = Mob.createMobAttributes();
		builder = builder.add(Attributes.MOVEMENT_SPEED, 0);
		builder = builder.add(Attributes.MAX_HEALTH, 10);
		builder = builder.add(Attributes.ARMOR, 0);
		builder = builder.add(Attributes.ATTACK_DAMAGE, 3);
		builder = builder.add(Attributes.FOLLOW_RANGE, 16);
		return builder;
	}
}