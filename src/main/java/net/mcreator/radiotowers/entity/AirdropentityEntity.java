package net.mcreator.radiotowers.entity;

import net.minecraftforge.fml.network.FMLPlayMessages;
import net.minecraftforge.fml.network.NetworkHooks;

import net.minecraft.world.IServerWorld;
import net.minecraft.world.World;
import net.minecraft.entity.projectile.PotionEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.*;
import net.minecraft.util.DamageSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.play.IServerPlayNetHandler;
import net.minecraft.network.IPacket;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;

import net.mcreator.radiotowers.procedures.AirdropentityOnInitialEntitySpawnProcedure;

import java.util.ArrayList;
import java.util.List;
import net.mcreator.radiotowers.procedures.AirdropentityOnEntityTickUpdateProcedure;
import net.mcreator.radiotowers.init.RadiotowersModEntities;

import javax.annotation.Nullable;

public class AirdropentityEntity extends MonsterEntity {
	public static final DataParameter<Boolean> DATA_IsOpening = EntityDataManager.defineId(AirdropentityEntity.class, DataSerializers.BOOLEAN);
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

	public AirdropentityEntity(FMLPlayMessages.SpawnEntity packet, World world) {
		this(RadiotowersModEntities.AIRDROPENTITY.get(), world);
	}

	public AirdropentityEntity(EntityType<AirdropentityEntity> type, World world) {
		super(type, world);
		maxUpStep = 0.6f;
		xpReward = 0;
		setNoAi(true);
		setPersistenceRequired();
	}

	@Override
	public IPacket<?> getAddEntityPacket() {
		return NetworkHooks.getEntitySpawningPacket(this);
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		this.entityData.define(DATA_IsOpening, true);
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public CreatureAttribute getMobType() {
		return CreatureAttribute.UNDEFINED;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean hurt(DamageSource damagesource, float amount) {
		if (damagesource == DamageSource.IN_FIRE)
			return false;
		if (damagesource.getDirectEntity() instanceof AbstractArrowEntity)
			return false;
		if (damagesource.getDirectEntity() instanceof PlayerEntity)
			return false;
		if (damagesource.getDirectEntity() instanceof PotionEntity || damagesource.getDirectEntity() instanceof AreaEffectCloudEntity)
			return false;
		if (damagesource == net.minecraft.util.DamageSource.FALL)
			return false;
		if (damagesource == DamageSource.CACTUS)
			return false;
		if (damagesource == DamageSource.DROWN)
			return false;
		if (damagesource == DamageSource.LIGHTNING_BOLT)
			return false;
		if (damagesource.isExplosion())
			return false;
		if ("trident".equals(damagesource.getMsgId()))
			return false;
		if (damagesource == DamageSource.ANVIL)
			return false;
		if (damagesource == DamageSource.DRAGON_BREATH)
			return false;
		if (damagesource == DamageSource.WITHER || "witherSkull".equals(damagesource.getMsgId()))
			return false;
		return super.hurt(damagesource, amount);
	}

	@Override
	public boolean ignoreExplosion() {
		return true;
	}

	@Override
	public ILivingEntityData finalizeSpawn(IServerWorld world, DifficultyInstance difficulty, SpawnReason reason, @Nullable ILivingEntityData livingdata, @Nullable CompoundNBT tag) {
		ILivingEntityData retval = super.finalizeSpawn(world, difficulty, reason, livingdata, tag);
		AirdropentityOnInitialEntitySpawnProcedure.execute(this);
		return retval;
	}

	@Override
	public void addAdditionalSaveData(CompoundNBT compound) {
		super.addAdditionalSaveData(compound);
		compound.putBoolean("DataIsOpening", this.entityData.get(DATA_IsOpening));
		ListNBT ids = new ListNBT();
		for (String s : airdropItemIds) ids.add(net.minecraft.nbt.StringNBT.valueOf(s));
		compound.put("AirdropItemIds", ids);
		ListNBT qty = new ListNBT();
		for (Integer i : airdropQuantities) qty.add(net.minecraft.nbt.IntNBT.valueOf(i));
		compound.put("AirdropQuantities", qty);
		compound.putInt("AirdropDifficulty", airdropDifficulty);
		compound.putBoolean("MembersOnlyCrate", membersOnlyCrate);
		ListNBT members = new ListNBT();
		for (java.util.UUID u : lobbyMembers) members.add(net.minecraft.nbt.StringNBT.valueOf(u.toString()));
		compound.put("LobbyMembers", members);
	}

	@Override
	public void readAdditionalSaveData(CompoundNBT compound) {
		super.readAdditionalSaveData(compound);
		if (compound.contains("DataIsOpening"))
			this.entityData.set(DATA_IsOpening, compound.getBoolean("DataIsOpening"));
		airdropItemIds.clear();
		if (compound.contains("AirdropItemIds", 9)) {
			ListNBT ids = compound.getList("AirdropItemIds", 8);
			for (int i = 0; i < ids.size(); i++) airdropItemIds.add(ids.getString(i));
		}
		airdropQuantities.clear();
		if (compound.contains("AirdropQuantities", 9)) {
			ListNBT qty = compound.getList("AirdropQuantities", 3);
			for (int i = 0; i < qty.size(); i++) airdropQuantities.add(qty.getInt(i));
		}
		if (compound.contains("AirdropDifficulty", 3)) airdropDifficulty = compound.getInt("AirdropDifficulty");
		membersOnlyCrate = compound.getBoolean("MembersOnlyCrate");
		lobbyMembers.clear();
		if (compound.contains("LobbyMembers", 9)) {
			ListNBT members = compound.getList("LobbyMembers", 8);
			for (int i = 0; i < members.size(); i++) {
				try {
					lobbyMembers.add(java.util.UUID.fromString(members.getString(i)));
				} catch (Exception ignored) {}
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
	}

	@Override
	public void baseTick() {
		super.baseTick();
		AirdropentityOnEntityTickUpdateProcedure.execute(this.level, this.getX(), this.getY(), this.getZ(), this);
	}

	public static void init() {
	}

	public static AttributeModifierMap.MutableAttribute createAttributes() {
		AttributeModifierMap.MutableAttribute builder = MobEntity.createMobAttributes();
		builder = builder.add(Attributes.MOVEMENT_SPEED, 0);
		builder = builder.add(Attributes.MAX_HEALTH, 10);
		builder = builder.add(Attributes.ARMOR, 0);
		builder = builder.add(Attributes.ATTACK_DAMAGE, 3);
		builder = builder.add(Attributes.FOLLOW_RANGE, 16);
		return builder;
	}
}