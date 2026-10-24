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
import net.minecraft.network.play.IServerPlayNetHandler;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.vector.Vector3d;

import net.mcreator.radiotowers.procedures.PlaneentityOnInitialEntitySpawnProcedure;

import java.util.ArrayList;
import java.util.List;
import net.mcreator.radiotowers.init.RadiotowersModEntities;

import javax.annotation.Nullable;

public class PlaneentityEntity extends MonsterEntity {
	/** Synced wreck pose for Dead Air EB crash sites (and future grounded wrecks). */
	public static final DataParameter<Boolean> DATA_CRASHED = EntityDataManager.defineId(PlaneentityEntity.class,
		DataSerializers.BOOLEAN);

	private List<String> airdropItemIds = new ArrayList<>();
	private List<Integer> airdropQuantities = new ArrayList<>();
	private int airdropDifficulty;

	public void setAirdropOrder(List<String> itemIds, List<Integer> quantities) {
		this.airdropItemIds = itemIds != null ? new ArrayList<>(itemIds) : new ArrayList<>();
		this.airdropQuantities = quantities != null ? new ArrayList<>(quantities) : new ArrayList<>();
		if (this.airdropQuantities.size() != this.airdropItemIds.size()) {
			while (this.airdropQuantities.size() < this.airdropItemIds.size())
				this.airdropQuantities.add(1);
			this.airdropQuantities = this.airdropQuantities.subList(0, this.airdropItemIds.size());
		}
	}

	public List<String> getAirdropItemIds() { return new ArrayList<>(airdropItemIds); }
	public List<Integer> getAirdropQuantities() { return new ArrayList<>(airdropQuantities); }
	public boolean hasAirdropOrder() { return !airdropItemIds.isEmpty(); }
	public void setAirdropDifficulty(int difficulty) { this.airdropDifficulty = Math.max(0, difficulty); }
	public int getAirdropDifficulty() { return airdropDifficulty; }

	public PlaneentityEntity(FMLPlayMessages.SpawnEntity packet, World world) {
		this(RadiotowersModEntities.PLANEENTITY.get(), world);
	}

	public PlaneentityEntity(EntityType<PlaneentityEntity> type, World world) {
		super(type, world);
		maxUpStep = 0.6f;
		xpReward = 0;
		setNoAi(true);
		setPersistenceRequired();
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		this.entityData.define(DATA_CRASHED, false);
	}

	/** Grounded EB / story wreck — no fly-in, crash renderer pose. */
	public void markAsCrashedWreck() {
		this.entityData.set(DATA_CRASHED, true);
		setNoAi(true);
		setDeltaMovement(Vector3d.ZERO);
		xRot = 0f;
	}

	public boolean isCrashedWreck() {
		return this.entityData.get(DATA_CRASHED);
	}

	@Override
	public IPacket<?> getAddEntityPacket() {
		return NetworkHooks.getEntitySpawningPacket(this);
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
	public void addAdditionalSaveData(CompoundNBT compound) {
		super.addAdditionalSaveData(compound);
		compound.putBoolean("CrashedWreck", isCrashedWreck());
		ListNBT ids = new ListNBT();
		for (String s : airdropItemIds) ids.add(net.minecraft.nbt.StringNBT.valueOf(s));
		compound.put("AirdropItemIds", ids);
		ListNBT qty = new ListNBT();
		for (Integer i : airdropQuantities) qty.add(net.minecraft.nbt.IntNBT.valueOf(i));
		compound.put("AirdropQuantities", qty);
		compound.putInt("AirdropDifficulty", airdropDifficulty);
	}

	@Override
	public void readAdditionalSaveData(CompoundNBT compound) {
		super.readAdditionalSaveData(compound);
		if (compound.getBoolean("CrashedWreck")) {
			markAsCrashedWreck();
		}
		airdropItemIds.clear();
		ListNBT ids = compound.getList("AirdropItemIds", 8);
		for (int i = 0; i < ids.size(); i++) airdropItemIds.add(ids.getString(i));
		airdropQuantities.clear();
		ListNBT qty = compound.getList("AirdropQuantities", 3);
		for (int i = 0; i < qty.size(); i++) airdropQuantities.add(qty.getInt(i));
		if (compound.contains("AirdropDifficulty", 3)) airdropDifficulty = compound.getInt("AirdropDifficulty");
	}

	@Override
	public ILivingEntityData finalizeSpawn(IServerWorld world, DifficultyInstance difficulty, SpawnReason reason, @Nullable ILivingEntityData livingdata, @Nullable CompoundNBT tag) {
		ILivingEntityData retval = super.finalizeSpawn(world, difficulty, reason, livingdata, tag);
		if (isCrashedWreck() || (tag != null && tag.getBoolean("CrashedWreck"))) {
			markAsCrashedWreck();
			return retval;
		}
		try {
			PlaneentityOnInitialEntitySpawnProcedure.execute(world, this.getX(), this.getZ(), this);
		} catch (Throwable t) {
			// Never hard-crash the world if plane spawn setup fails.
			net.mcreator.radiotowers.RadiotowersMod.LOGGER.error("Plane spawn init failed; skipping airdrop plane setup", t);
		}
		return retval;
	}

	@Override
	public void tick() {
		super.tick();
		if (isCrashedWreck() && !this.level.isClientSide()) {
			setDeltaMovement(Vector3d.ZERO);
		}
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