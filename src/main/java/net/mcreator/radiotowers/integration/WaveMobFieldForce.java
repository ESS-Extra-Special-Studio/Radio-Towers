package net.mcreator.radiotowers.integration;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.mcreator.radiotowers.RadiotowersMod;

import java.lang.reflect.Field;

/**
 * Force the invulnerable and noAi fields directly via reflection so we override
 * any code (e.g. Berezka API) that sets them and never calls the setters, or that
 * re-applies them every tick after our setters run.
 */
public final class WaveMobFieldForce {

    private static Field entityInvulnerable;
    private static Field mobNoAi;
    private static boolean initDone;

    private static void init() {
        if (initDone) return;
        initDone = true;
        for (String name : new String[]{"invulnerable", "f_19840_", "f_19850_"}) {
            try {
                entityInvulnerable = Entity.class.getDeclaredField(name);
                entityInvulnerable.setAccessible(true);
                break;
            } catch (NoSuchFieldException ignored) {}
        }
        if (entityInvulnerable == null)
            RadiotowersMod.LOGGER.warn("[Zombie Waves] Could not find Entity invulnerable field (tried invulnerable, f_19840_, f_19850_)");
        try {
            mobNoAi = Mob.class.getDeclaredField("noAi");
            mobNoAi.setAccessible(true);
        } catch (NoSuchFieldException e) {
            for (Field f : Mob.class.getDeclaredFields()) {
                if (f.getType() == boolean.class && (f.getName().toLowerCase().contains("noai") || f.getName().toLowerCase().contains("no_ai"))) {
                    mobNoAi = f;
                    mobNoAi.setAccessible(true);
                    break;
                }
            }
            if (mobNoAi == null)
                RadiotowersMod.LOGGER.warn("[Zombie Waves] Could not find Mob noAi field: {}", e.getMessage());
        }
    }

    /** Set invulnerable and noAi to false directly on the entity. Call after setters so we override any bypass. */
    public static void forceOff(Mob mob) {
        if (mob == null || mob.isRemoved()) return;
        init();
        try {
            if (entityInvulnerable != null)
                entityInvulnerable.setBoolean(mob, false);
        } catch (Throwable t) {
        }
        try {
            if (mobNoAi != null)
                mobNoAi.setBoolean(mob, false);
        } catch (Throwable t) {
        }
    }

    /** Read the raw invulnerable field without any redirect mixins. */
    public static boolean isInvulnerableRaw(Entity entity) {
        if (entity == null) return false;
        init();
        try {
            return entityInvulnerable != null && entityInvulnerable.getBoolean(entity);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Read the raw noAi field without any redirect mixins. */
    public static boolean isNoAiRaw(Mob mob) {
        if (mob == null) return false;
        init();
        try {
            return mobNoAi != null && mobNoAi.getBoolean(mob);
        } catch (Throwable t) {
            return false;
        }
    }
}
