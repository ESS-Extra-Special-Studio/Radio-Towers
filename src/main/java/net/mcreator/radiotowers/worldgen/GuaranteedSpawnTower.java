package net.mcreator.radiotowers.worldgen;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.config.AirdropConfig;
import net.mcreator.radiotowers.init.RadiotowersModBlocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.template.AlwaysTrueRuleTest;
import net.minecraft.world.gen.feature.template.BlockIgnoreStructureProcessor;
import net.minecraft.world.gen.feature.template.BlockMatchRuleTest;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.RuleEntry;
import net.minecraft.world.gen.feature.template.RuleStructureProcessor;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.world.World;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.fluid.Fluids;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;

/**
 * Ensures at least one STANDARD radio tower exists near world spawn (once per overworld).
 * <p>
 * Never force-generates chunks. Prefers flat loaded land, clears trees in the footprint,
 * then fills dirt supports under the pad so it does not float over ravines.
 * Retries as more spawn chunks load until a site is found.
 */
@Mod.EventBusSubscriber(modid = RadiotowersMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuaranteedSpawnTower {
    private static final ResourceLocation TEMPLATE = new ResourceLocation("radiotowers", "tower");
    private static final ResourceLocation TEMPLATE_FALLBACK = new ResourceLocation("radiotowers", "radiotower");
    private static final int STEP_BLOCKS = 8;
    private static final int GROUND_SEARCH_DEPTH = 48;
    /** radiotowers:tower size is 12×48×19 */
    private static final int FOOTPRINT_X = 12;
    private static final int FOOTPRINT_Z = 19;
    /** Prefer this flatness; accept up to LAST_RESORT with supports fill. */
    private static final int PREFERRED_FLATNESS = 3;
    private static final int ACCEPTABLE_FLATNESS = 5;
    private static final int LAST_RESORT_FLATNESS = 8;
    private static final int MAX_LOADED_CANDIDATES = 256;
    /** If a natural tower is already this close, skip guaranteed place (avoids tower-ception). */
    private static final int EXISTING_TOWER_SKIP_RADIUS = 72;
    private static final int[] RETRY_DELAYS_TICKS = { 40, 80, 160, 320, 640, 1200, 2400 };
    private static final int[] PLAYER_RETRY_DELAYS_TICKS = { 20, 60, 120, 240, 480, 900, 1800 };

    /** Debounce chunk-load retries (game time of last schedule). */
    private static long lastChunkRetryGameTime = Long.MIN_VALUE;

    private GuaranteedSpawnTower() {}

    public static void schedule(ServerWorld overworld) {
        if (overworld == null || overworld.dimension() != World.OVERWORLD) return;
        if (!AirdropConfig.GUARANTEE_SPAWN_TOWER.get()) return;
        for (int delay : RETRY_DELAYS_TICKS) {
            int d = delay;
            RadiotowersMod.queueServerWork(d, () -> tryPlace(overworld, null));
        }
    }

    public static void scheduleForPlayer(ServerPlayerEntity player) {
        if (player == null) return;
        ServerWorld level = player.getLevel();
        if (level.dimension() != World.OVERWORLD) return;
        if (!AirdropConfig.GUARANTEE_SPAWN_TOWER.get()) return;
        BlockPos pos = player.blockPosition();
        for (int delay : PLAYER_RETRY_DELAYS_TICKS) {
            int d = delay;
            RadiotowersMod.queueServerWork(d, () -> tryPlace(level, pos));
        }
    }

    /** As spawn chunks finish generating, keep trying until placed. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
    Object level$src = event.getWorld(); ServerWorld level = (level$src instanceof ServerWorld) ? (ServerWorld) level$src : null;
        if (!(level != null)) return;
        if (level.dimension() != World.OVERWORLD) return;
        if (!AirdropConfig.GUARANTEE_SPAWN_TOWER.get()) return;
        if (GuaranteedSpawnTowerData.get(level).isPlaced()) return;

        ChunkPos cp = event.getChunk().getPos();
        BlockPos spawn = level.getSharedSpawnPos();
        int maxChunks = Math.max(4, Math.min(32, AirdropConfig.SPAWN_TOWER_SEARCH_RADIUS_CHUNKS.get()));
        int sx = spawn.getX() >> 4;
        int sz = spawn.getZ() >> 4;
        if (Math.abs(cp.x - sx) > maxChunks || Math.abs(cp.z - sz) > maxChunks) return;

        long t = level.getGameTime();
        if (t == lastChunkRetryGameTime) return;
        lastChunkRetryGameTime = t;
        RadiotowersMod.queueServerWork(10, () -> tryPlace(level, null));
    }

    public static void tryPlace(ServerWorld level, BlockPos preferNear) {
        if (level == null || level.dimension() != World.OVERWORLD) return;
        if (!AirdropConfig.GUARANTEE_SPAWN_TOWER.get()) return;

        GuaranteedSpawnTowerData data = GuaranteedSpawnTowerData.get(level);
        if (data.isPlaced()) return;

        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos center = preferNear != null ? preferNear : spawn;

        // Natural worldgen may already have put a tower in the spawn circle — don't stack another.
        if (findExistingRadioPanelNear(level, center, EXISTING_TOWER_SKIP_RADIUS)
            || findExistingRadioPanelNear(level, spawn, EXISTING_TOWER_SKIP_RADIUS)) {
            data.setPlaced(true);
            RadiotowersMod.LOGGER.info("[RadioTowers] Tower already near spawn; skipping guaranteed spawn tower");
            return;
        }

        int maxChunks = Math.max(4, Math.min(32, AirdropConfig.SPAWN_TOWER_SEARCH_RADIUS_CHUNKS.get()));

        BlockPos placedAt = findSiteInLoadedChunks(level, spawn, center, maxChunks);
        if (placedAt == null) return;

        // Final check at the chosen site (natural tower in this exact footprint)
        if (findExistingRadioPanelNear(level, placedAt.offset(FOOTPRINT_X / 2, 0, FOOTPRINT_Z / 2), 40)) {
            data.setPlaced(true);
            RadiotowersMod.LOGGER.info("[RadioTowers] Tower already at chosen site; skipping guaranteed spawn tower");
            return;
        }

        Template template = getTemplate(level);
        if (template == null) {
            RadiotowersMod.LOGGER.error("[RadioTowers] Structure template radiotowers:tower not found");
            return;
        }
        net.minecraft.util.math.vector.Vector3i size = template.getSize();

        clearVegetationInFootprint(level, placedAt, size);
        if (!placeStandardTower(level, template, placedAt)) {
            RadiotowersMod.LOGGER.warn("[RadioTowers] Failed to place guaranteed spawn tower at {}", placedAt.toShortString());
            return;
        }
        fillSupportsUnderPad(level, placedAt, size);

        data.setPlaced(true);
        RadiotowersMod.LOGGER.info("[RadioTowers] Placed guaranteed STANDARD radio tower at {} (world spawn {})",
            placedAt.toShortString(), spawn.toShortString());
    }

    private static boolean findExistingRadioPanelNear(ServerWorld level, BlockPos center, int radius) {
        Block panel = RadiotowersModBlocks.RADIO_PANEL.get();
        int step = 4;
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                ChunkPos cp = new ChunkPos(x >> 4, z >> 4);
                if (!level.getChunkSource().hasChunk(cp.x, cp.z)) continue;
                if (level.getChunkSource().getChunkNow(cp.x, cp.z) == null) continue;
                int groundY = findGroundY(level, x, z);
                if (groundY == Integer.MIN_VALUE) {
                    groundY = level.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                }
                int minY = Math.max(0, groundY - 4);
                int maxY = Math.min(level.getMaxBuildHeight() - 1, groundY + 48);
                for (int y = minY; y <= maxY; y++) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(panel)) return true;
                }
            }
        }
        return false;
    }

    private static final class SiteSearch {
        int checked;
        BlockPos bestAcceptable;
        int bestAcceptableFlat = Integer.MAX_VALUE;
        BlockPos bestLastResort;
        int bestLastResortFlat = Integer.MAX_VALUE;

        /** @return preferred site to place immediately, or null to keep searching */
        BlockPos offer(SiteScore s) {
            if (s == null || !s.loaded) return null;
            checked++;
            if (s.flatness <= PREFERRED_FLATNESS) return s.origin;
            if (s.flatness <= ACCEPTABLE_FLATNESS) {
                if (s.flatness < bestAcceptableFlat) {
                    bestAcceptable = s.origin;
                    bestAcceptableFlat = s.flatness;
                }
            } else if (s.flatness <= LAST_RESORT_FLATNESS && s.flatness < bestLastResortFlat) {
                bestLastResort = s.origin;
                bestLastResortFlat = s.flatness;
            }
            return null;
        }

        BlockPos best() {
            return bestAcceptable != null ? bestAcceptable : bestLastResort;
        }

        boolean full() {
            return checked >= MAX_LOADED_CANDIDATES;
        }
    }

    private static BlockPos findSiteInLoadedChunks(ServerWorld level, BlockPos spawn, BlockPos center, int maxChunks) {
        int maxR = maxChunks * 16;
        SiteSearch search = new SiteSearch();

        for (int r = 16; r <= maxR; r += STEP_BLOCKS) {
            for (int dx = -r; dx <= r; dx += STEP_BLOCKS) {
                BlockPos early = search.offer(scoreCandidateLoaded(level, center.getX() + dx, center.getZ() + r));
                if (early != null) return early;
                early = search.offer(scoreCandidateLoaded(level, center.getX() + dx, center.getZ() - r));
                if (early != null) return early;
                if (search.full()) return search.best();
            }
            for (int dz = -r + STEP_BLOCKS; dz <= r - STEP_BLOCKS; dz += STEP_BLOCKS) {
                BlockPos early = search.offer(scoreCandidateLoaded(level, center.getX() + r, center.getZ() + dz));
                if (early != null) return early;
                early = search.offer(scoreCandidateLoaded(level, center.getX() - r, center.getZ() + dz));
                if (early != null) return early;
                if (search.full()) return search.best();
            }
        }

        for (int[] off : new int[][]{
            {32, 0}, {0, 32}, {-32, 0}, {0, -32},
            {16, 16}, {-16, 16}, {16, -16}, {-16, -16},
            {24, 8}, {8, 24}, {-24, 8}, {8, -24}
        }) {
            BlockPos early = search.offer(scoreCandidateLoaded(level, spawn.getX() + off[0], spawn.getZ() + off[1]));
            if (early != null) return early;
        }

        return search.best();
    }

    private static final class SiteScore {
        final BlockPos origin;
        final int flatness;
        final boolean loaded;

        SiteScore(BlockPos origin, int flatness, boolean loaded) {
            this.origin = origin;
            this.flatness = flatness;
            this.loaded = loaded;
        }
    }

    /**
     * Trees are allowed — cleared on place. Only requires loaded footprint + land + flatness ≤ LAST_RESORT.
     */
    private static SiteScore scoreCandidateLoaded(ServerWorld level, int x, int z) {
        for (int dx = 0; dx < FOOTPRINT_X; dx += 8) {
            for (int dz = 0; dz < FOOTPRINT_Z; dz += 8) {
                ChunkPos cp = new ChunkPos((x + dx) >> 4, (z + dz) >> 4);
                if (!level.getChunkSource().hasChunk(cp.x, cp.z)) return null;
                if (level.getChunkSource().getChunkNow(cp.x, cp.z) == null) return null;
            }
        }

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int[][] samples = {
            {0, 0}, {FOOTPRINT_X - 1, 0}, {0, FOOTPRINT_Z - 1}, {FOOTPRINT_X - 1, FOOTPRINT_Z - 1},
            {FOOTPRINT_X / 2, 0}, {FOOTPRINT_X / 2, FOOTPRINT_Z - 1},
            {0, FOOTPRINT_Z / 2}, {FOOTPRINT_X - 1, FOOTPRINT_Z / 2},
            {FOOTPRINT_X / 2, FOOTPRINT_Z / 2}
        };
        for (int[] s : samples) {
            int gx = x + s[0];
            int gz = z + s[1];
            int gy = findGroundY(level, gx, gz);
            if (gy == Integer.MIN_VALUE) return null;
            if (!isSuitableLand(level, new BlockPos(gx, gy, gz))) return null;
            minY = Math.min(minY, gy);
            maxY = Math.max(maxY, gy);
        }
        int flatness = maxY - minY;
        if (flatness > LAST_RESORT_FLATNESS) return null;
        return new SiteScore(new BlockPos(x, minY, z), flatness, true);
    }

    private static boolean isVegetation(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(BlockTags.LOGS_THAT_BURN)
            || state.is(BlockTags.SAPLINGS)             || state.is(Blocks.VINE);
    }

    private static boolean isSuitableLand(ServerWorld level, BlockPos ground) {
        Biome biome = level.getBiome(ground);
        Biome.Category category = biome.getBiomeCategory();
        if (category == Biome.Category.OCEAN || category == Biome.Category.RIVER || category == Biome.Category.BEACH) {
            return false;
        }
        BlockState below = level.getBlockState(ground);
        BlockState above = level.getBlockState(ground.above());
        if (below.getFluidState().getType() != Fluids.EMPTY) return false;
        if (above.getFluidState().getType() != Fluids.EMPTY) return false;
        return isSolidGround(below);
    }

    private static int findGroundY(ServerWorld level, int x, int z) {
        BlockPos top = level.getHeightmapPos(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        int startY = top.getY();
        int minY = 0;
        int limit = Math.min(GROUND_SEARCH_DEPTH, startY - minY);
        for (int d = 0; d <= limit; d++) {
            int y = startY - d;
            if (y < minY) break;
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (isVegetation(state)) continue;
            if (state.is(Blocks.SNOW)) continue;
            if (isSolidGround(state)) return y;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean isSolidGround(BlockState state) {
        if (state.isAir() || state.getFluidState().getType() != Fluids.EMPTY) return false;
        if (isVegetation(state)) return false;
        if (state.is(Blocks.SNOW)) return false;
        Block block = state.getBlock();
        if (block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.PODZOL
            || block == Blocks.MYCELIUM || block == Blocks.STONE || block == Blocks.SAND
            || block == Blocks.SANDSTONE || block == Blocks.RED_SAND || block == Blocks.GRAVEL
            || block == Blocks.COARSE_DIRT
            || block == Blocks.CLAY || block == Blocks.SNOW_BLOCK || block == Blocks.TERRACOTTA
            || block == Blocks.RED_SANDSTONE
            || block == Blocks.ANDESITE || block == Blocks.DIORITE || block == Blocks.GRANITE
            || block == Blocks.GRASS_PATH || block == Blocks.FARMLAND
            || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE || block == Blocks.ICE
            || block == Blocks.BASALT
            || block == Blocks.BLACKSTONE || block == Blocks.NETHERRACK) {
            return true;
        }
        return state.getMaterial().blocksMotion();
    }

    private static void clearVegetationInFootprint(ServerWorld level, BlockPos origin, net.minecraft.util.math.vector.Vector3i size) {
        int sx = size.getX();
        int sy = Math.min(size.getY(), 24);
        int sz = size.getZ();
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                for (int dy = 0; dy <= sy; dy++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(p);
                    if (isVegetation(state) || state.getMaterial().isReplaceable() || state.is(Blocks.SNOW)
                        || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
                        || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static void fillSupportsUnderPad(ServerWorld level, BlockPos origin, net.minecraft.util.math.vector.Vector3i size) {
        int sx = size.getX();
        int sz = size.getZ();
        int padY = origin.getY();
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int naturalGround = findGroundY(level, x, z);
                if (naturalGround != Integer.MIN_VALUE && naturalGround >= padY) continue;

                int bottom = naturalGround == Integer.MIN_VALUE
                    ? Math.max(0 + 1, padY - 32)
                    : naturalGround + 1;
                for (int y = padY - 1; y >= bottom; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (state.isAir() || isVegetation(state) || state.getMaterial().isReplaceable()
                        || state.is(Blocks.WATER) || state.is(Blocks.LAVA)
                        || state.getFluidState().getType() != Fluids.EMPTY) {
                        BlockState fill = (padY - y) <= 3
                            ? Blocks.DIRT.defaultBlockState()
                            : Blocks.STONE.defaultBlockState();
                        level.setBlock(p, fill, 2);
                    } else if (isSolidGround(state)) {
                        break;
                    }
                }
            }
        }
    }

    private static Template getTemplate(ServerWorld level) {
        Template opt = level.getStructureManager().get(TEMPLATE);
        if (opt == null) opt = level.getStructureManager().get(TEMPLATE_FALLBACK);
        return opt;
    }

    private static boolean placeStandardTower(ServerWorld level, Template template, BlockPos origin) {
        PlacementSettings settings = new PlacementSettings()
            .setMirror(Mirror.NONE)
            .setRotation(Rotation.NONE)
            .setIgnoreEntities(false)
            .addProcessor(new BlockIgnoreStructureProcessor(java.util.Collections.singletonList(Blocks.STRUCTURE_BLOCK)))
            .addProcessor(new RuleStructureProcessor(java.util.Arrays.asList(new RuleEntry(
                    new BlockMatchRuleTest(Blocks.WET_SPONGE),
                    AlwaysTrueRuleTest.INSTANCE,
                    Blocks.GRASS_BLOCK.defaultBlockState()
                ),
                new RuleEntry(
                    new BlockMatchRuleTest(Blocks.SPONGE),
                    AlwaysTrueRuleTest.INSTANCE,
                    Blocks.GRASS_BLOCK.defaultBlockState()
                ))));
        try {
            template.placeInWorld(level, origin, origin, settings, level.random, 2);
            return true;
        } catch (Exception e) {
            RadiotowersMod.LOGGER.error("[RadioTowers] placeInWorld failed at {}", origin, e);
            return false;
        }
    }
}
