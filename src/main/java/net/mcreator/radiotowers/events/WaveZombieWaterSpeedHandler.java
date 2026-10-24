package net.mcreator.radiotowers.events;

import net.minecraft.world.server.ServerWorld;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.integration.WaveSpawnTag;

/**
 * Wave zombies (and Drowned from wave zombies) are not slowed by water:
 * while in water they get Speed II so they move at near-normal speed.
 */
@Mod.EventBusSubscriber(modid = RadiotowersMod.MODID)
public class WaveZombieWaterSpeedHandler {

    private static final int SPEED_DURATION_TICKS = 40;
    private static final int SPEED_AMPLIFIER = 1; // Speed II

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingUpdateEvent event) {
        if (event.getEntity() == null || event.getEntity().level.isClientSide()) return;
        if (!(event.getEntity().level instanceof ServerWorld)) return;
        Object zombie$src = event.getEntity(); ZombieEntity zombie = (zombie$src instanceof ZombieEntity) ? (ZombieEntity) zombie$src : null;
        if (!(zombie != null)) return;
        if (!zombie.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
            && !zombie.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG))
            return;
        if (!zombie.isInWater()) return;
        zombie.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED, SPEED_DURATION_TICKS, SPEED_AMPLIFIER));
    }
}
