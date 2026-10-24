package net.mcreator.radiotowers.entity;

import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.AbstractThrownPotion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.mojang.serialization.Codec;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.Vec3;

import net.mcreator.radiotowers.procedures.PlaneentityOnInitialEntitySpawnProcedure;

import java.util.ArrayList;
import java.util.List;
import net.mcreator.radiotowers.init.RadiotowersModEntities;

import javax.annotation.Nullable;

public class PlaneentityEntity extends Monster {
	/** Synced wreck pose for Dead Air EB crash sites (and future grounded wrecks). */
	public static final EntityDataAccessor<Boolean> DATA_CRASHED = SynchedEntityData.defineId(PlaneentityEntity.class,
		EntityDataSerializers.BOOLEAN);

	public final AnimationState animationState0 = new AnimationState();
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

	public PlaneentityEntity(EntityType<PlaneentityEntity> type, Level world) {
		super(type, world);
		xpReward = 0;
		setNoAi(true);
		setPersistenceRequired();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CRASHED, false);
	}

	/** Grounded EB / story wreck — no fly-in, crash renderer pose. */
	public void markAsCrashedWreck() {
		this.entityData.set(DATA_CRASHED, true);
		setNoAi(true);
		setDeltaMovement(Vec3.ZERO);
		setXRot(0f);
		this.animationState0.stop();
	}

	public boolean isCrashedWreck() {
		return this.entityData.get(DATA_CRASHED);
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource damagesource, float amount) {
		if (damagesource.is(DamageTypes.IN_FIRE))
			return false;
		if (damagesource.getDirectEntity() instanceof AbstractArrow)
			return false;
		if (damagesource.getDirectEntity() instanceof Player)
			return false;
		if (damagesource.getDirectEntity() instanceof AbstractThrownPotion || damagesource.getDirectEntity() instanceof AreaEffectCloud)
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
		return super.hurtServer(level, damagesource, amount);
	}

	@Override
	public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
		return true;
	}

	@Override
	public void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("CrashedWreck", isCrashedWreck());
		var ids = output.list("AirdropItemIds", Codec.STRING);
		for (String s : airdropItemIds) ids.add(s);
		var qty = output.list("AirdropQuantities", Codec.INT);
		for (Integer i : airdropQuantities) qty.add(i);
		output.putInt("AirdropDifficulty", airdropDifficulty);
	}

	@Override
	public void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		if (input.getBooleanOr("CrashedWreck", false)) {
			markAsCrashedWreck();
		}
		airdropItemIds.clear();
		input.listOrEmpty("AirdropItemIds", Codec.STRING).stream().forEach(airdropItemIds::add);
		airdropQuantities.clear();
		input.listOrEmpty("AirdropQuantities", Codec.INT).stream().forEach(airdropQuantities::add);
		airdropDifficulty = input.getIntOr("AirdropDifficulty", 0);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData livingdata) {
		SpawnGroupData retval = super.finalizeSpawn(world, difficulty, reason, livingdata);
		if (isCrashedWreck()) {
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
		if (this.level().isClientSide()) {
			if (isCrashedWreck()) {
				this.animationState0.stop();
			} else {
				this.animationState0.animateWhen(true, this.tickCount);
			}
		}
		if (isCrashedWreck() && !this.level().isClientSide()) {
			setDeltaMovement(Vec3.ZERO);
		}
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