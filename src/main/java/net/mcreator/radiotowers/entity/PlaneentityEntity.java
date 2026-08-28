package net.mcreator.radiotowers.entity;

import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.network.NetworkHooks;

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
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import net.mcreator.radiotowers.procedures.PlaneentityOnInitialEntitySpawnProcedure;

import java.util.ArrayList;
import java.util.List;
import net.mcreator.radiotowers.init.RadiotowersModEntities;

import javax.annotation.Nullable;

public class PlaneentityEntity extends Monster {
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

	public PlaneentityEntity(PlayMessages.SpawnEntity packet, Level world) {
		this(RadiotowersModEntities.PLANEENTITY.get(), world);
	}

	public PlaneentityEntity(EntityType<PlaneentityEntity> type, Level world) {
		super(type, world);
		setMaxUpStep(0.6f);
		xpReward = 0;
		setNoAi(true);
		setPersistenceRequired();
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket() {
		return NetworkHooks.getEntitySpawningPacket(this);
	}

	@Override
	public MobType getMobType() {
		return MobType.UNDEFINED;
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
	public boolean ignoreExplosion() {
		return true;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag compound) {
		super.addAdditionalSaveData(compound);
		ListTag ids = new ListTag();
		for (String s : airdropItemIds) ids.add(net.minecraft.nbt.StringTag.valueOf(s));
		compound.put("AirdropItemIds", ids);
		ListTag qty = new ListTag();
		for (Integer i : airdropQuantities) qty.add(net.minecraft.nbt.IntTag.valueOf(i));
		compound.put("AirdropQuantities", qty);
		compound.putInt("AirdropDifficulty", airdropDifficulty);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag compound) {
		super.readAdditionalSaveData(compound);
		airdropItemIds.clear();
		ListTag ids = compound.getList("AirdropItemIds", 8);
		for (int i = 0; i < ids.size(); i++) airdropItemIds.add(ids.getString(i));
		airdropQuantities.clear();
		ListTag qty = compound.getList("AirdropQuantities", 3);
		for (int i = 0; i < qty.size(); i++) airdropQuantities.add(qty.getInt(i));
		if (compound.contains("AirdropDifficulty", 3)) airdropDifficulty = compound.getInt("AirdropDifficulty");
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData livingdata, @Nullable CompoundTag tag) {
		SpawnGroupData retval = super.finalizeSpawn(world, difficulty, reason, livingdata, tag);
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
			this.animationState0.animateWhen(true, this.tickCount);
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