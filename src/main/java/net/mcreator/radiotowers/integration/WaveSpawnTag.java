package net.mcreator.radiotowers.integration;

/**
 * NBT key used to mark entities spawned by the Berezka Zombie Waves API so only they
 * are treated as wave spawns (ring placement, invuln clear). Must live outside a mixin
 * because mixins cannot have non-private static fields.
 */
public final class WaveSpawnTag {
    private WaveSpawnTag() {}

    /** Set by mixin when entity is added by Berezka API during a wave. */
    public static final String WAVE_SPAWN_TAG = "RadiotowersWaveSpawn";
    /** Set by us when we spawn a vanilla Zombie to replace an API mob; EntityJoinLevel uses this to register without double-counting. */
    public static final String REPLACEMENT_TAG = "RadiotowersWaveReplacement";
    /** Set by LevelAddEntityMixin when we've already set ring position for this entity (avoids double-counting if entity goes through addEntity and addFreshEntity). */
    public static final String RING_POSITION_SET = "RadiotowersRingPosSet";
    /** Set on our replacement zombies only; never removed. Death handler decrements API count only when this is set (avoids double-decrement with API for mobs the API spawned). */
    public static final String WE_DECREMENT_ON_DEATH = "RadiotowersWeDecrementOnDeath";
}
