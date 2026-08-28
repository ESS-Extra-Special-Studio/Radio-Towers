package net.mcreator.radiotowers.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Zombie;
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
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() == null || event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        if (!zombie.getPersistentData().getBoolean(WaveSpawnTag.WAVE_SPAWN_TAG)
            && !zombie.getPersistentData().getBoolean(WaveSpawnTag.REPLACEMENT_TAG))
            return;
        if (!zombie.isInWater()) return;
        zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_DURATION_TICKS, SPEED_AMPLIFIER));
    }
}
