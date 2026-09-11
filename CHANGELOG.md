## 1.2.3 (1.2.3)

Update by: Extra_Special_K

Changed:
Version parity with NeoForge 1.2.3 (Call Airdrop layout alignment lives on NeoForge; Forge jar rebuilt to match numbering).

# Changelog

## 1.2.2 (`1.2.2`)

Update by: Extra_Special_K

Requires **ExtraSpecialCore (ESC) 2.0.0** or newer. Catalog defense waves prefer **ExtraSpecialLIB (ESL)** when installed (Berezka remains an optional fallback).

Fixed:

- Forge crash on launch: `RandomSpreadStructurePlacementMixin` failed applying (`@Shadow spacing` missing from refmap). Spacing/separation/spreadType now use `@Accessor` field remaps so tower density config works under Forge SRG.
- Airdrop crates hanging in the sky after the plane drop â€” descent no longer relies on pack gravity/Slow Falling quirks; crates step down and place on ground (Forge + NeoForge).

## 1.2.1 (`1.2.1`)

Update by: Extra_Special_K

Requires **ExtraSpecialCore (ESC) 2.0.0** or newer. Catalog defense waves use **ExtraSpecialLIB (ESL) 1.0.1** when installed (Berezka remains the fallback).

Fixed:

- Call Airdrop **blacklist** and **customCatalogEntries** match TaCZ content ids by alias (`tacz:gun/m1911` / `tacz:m1911` / last path segment / GunId and AmmoId). Bare names such as `m1911` work. Custom lines reprice the live catalog row instead of missing it.
- After the **last** defense wave, the airdrop is sent with no â€œnext wave startingâ€ message. Mid-sequence chat counts correctly (1 of 3, then 2 of 3, then inbound).
- ESL-run waves are not also completed by the old kill-count / timer path, which had been reprinting â€œwave 1 of Nâ€.

## 1.2.0 (`1.2.0`)

Update by: Extra_Special_K

Requires **ExtraSpecialCore (ESC) 2.0.0** or newer (and its ES Library dependency).

Added:

- Biome tag `#radiotowers:has_radio_tower` â€” towers spawn across common overworld land biomes (not plains-only).
- Guaranteed STANDARD tower near world spawn (once per overworld); config `[worldgen] guaranteeSpawnTower` / `spawnTowerSearchRadiusChunks` (places into loaded chunks only).
- `[worldgen] towerSpawnDensityPercent` in `radiotowers-common.toml` (default 100): scales natural radio tower spacing. Higher = denser. New chunks only.
- `[airdrop] standardAirdropLootTable` â€” ResourceLocation for standard (non-catalog) airdrop crates (default `radiotowers:chests/airdrop_loot`).
- `[airdrop] minimumCatalogPoints` â€” configurable minimum catalog order (default 10; 10-point steps).
- `[waves] maxWaves`, `baseZombiesPerWave`, `zombiesPerTierStep` â€” pack-tunable wave rounds and zombie counts.

Changed:

- All tower types share one structure set (weights STANDARD 12 / FENCED 4 / OVERRUN 1) with spacing **34** and separation **22**, so different types cannot spawn on top of each other.
- Natural towers project to `MOTION_BLOCKING_NO_LEAVES` (less sitting on tree canopies).
- Guaranteed spawn tower prefers flatter ground, clears trees in its footprint, and fills dirt/stone supports under the pad. Skips placing if a tower is already near spawn (no double-stacking). Retries as spawn chunks load; accepts slightly uneven / forested sites so spawn worlds rarely miss a tower.
- `[waves] useZombieWavesForAirdrop` is honored: set false to skip ESL/Berezka waves and spawn the plane immediately even when a wave backend is installed.
- Clearer section comments in `radiotowers-common.toml` (catalog / standard loot / cooldowns / waves / worldgen).
- `customCatalogEntries` can override an auto-detected TaCZ gun or ammo cost by content id without changing its stack data (for example, `"tacz:gun/m1911=80"`).
- Catalog blacklist applies to TaCZ, addon, and custom entries (globs such as `tacz:gun/*` work). `includeTaczAmmo` / `includeTaczGuns` are independent. `[waves] aggroExcludeEntityIds` is honoured. Catalog rebuilds when `radiotowers-common.toml` is reloaded.

## 1.0.11 (`1.0.11`)

Update by: Extra_Special_K

Fixed:

- Without Dead Air / Zombie Waves: the radio panel opens the Call Airdrop catalog instead of firing an empty standard airdrop (which filled crates with random forge-tag loot).
- Airdrop catalog icons show each entryâ€™s stack size (e.g. Ã—64 ammo).
- Airdrop crate GUI binds to the real crate inventory when opened with null facing.
- Ordered crates no longer clear and refill one tick later if already filled.
- Bonus TaCZ ammo on standard loot-table crates always includes proper AmmoId NBT.
- Live delivery loot tables are no longer mis-tagged as structure loot on load.

## 1.0.10 (`1.0.10`)

Update by: Extra_Special_K

Added:

-Config options for airdrop timing in `radiotowers-common.toml`:
  - `[airdrop] standardAirdropCooldownMinutes` (default 3) â€” cooldown after a standard loot-table airdrop
  - `[airdrop] waveDeliveryCooldownMinutes` (default 10) â€” cooldown after a wave-delivered catalog airdrop
  - `[waves] waveDurationMinutes` (default 3) â€” how long each defense wave lasts before time-out
-Panel hint text uses the configured standard cooldown instead of a hard-coded "3 minute" string.

## 1.0.9 (`1.0.9`)

Update by: Extra_Special_K

Changed:

-Call Airdrop, Radio Panel, and Internet Station screens finished on ESC anchor layout.
-Airdrop instruction lines use scrolling text when they exceed the title band; lever, difficulty bar, and station controls repositioned for clearer layout.
-Requires ExtraSpecialCore 1.2.0+, range [1.0.0,2.0).

Fixed:

-Tower structure loot no longer uses live airdrop crate blocks â€” overrun/fenced tower templates now place vanilla chests for structure loot so wave deliveries and catalog fills cannot overwrite or bug out pre-generated tower chests.
-Existing structure airdrop crates (older worlds) are treated as structure loot and skipped when placing new delivery crates nearby.

## 1.0.8 (`1.0.8`)

Update by: Extra_Special_K

Changed:

-Integrated ExtraSpecialCore (ESC) as a required dependency for shared UI primitives.
-Updated Internet Station screen to use ESC panel/button helpers for consistency with the rest of the mod suite.
-Release/version bump for ESC-compatible pack testing.

## 1.0.7 (`1.0.7`)

Update by: Extra_Special_K

Added:

-End-recipe airdrop toggle (default OFF): optional catalog entries for `dragon_breath`, `dragon_head`, and `dragon_egg`, intended for packs where The End is inaccessible.
-Runtime admin command toggle for live servers: `/radiotowers endrecipeitems on|off|toggle|status|use_config` (no restart required to rebuild catalog visibility).

Changed:

-End-recipe item pricing tuned for endgame rarity: Dragon's Breath 60, Dragon Head 80, Dragon Egg 100.
-Panel default station cycle now follows panel upgrade state from Dead Air sync: Jukebox FM is hidden unless that specific panel has the Jukebox Upgrade module.
-Release naming normalized from previous hotfix label to `1.0.7`.
-Localization support expanded: panel/airdrop/internet screen labels, wave status messages, crate naming, and new command responses now use translation keys (translator-ready `en_us` updates included).

---

## 1.0.6 hotfix (`1.0.6-hotfix`)

**Dedicated server crash fix**

The dedicated server was failing because several **common** classes (loaded on the server) had **static references** to client-only code: `net.minecraft.client` types, GUI screens, and `AirdropWaveHudData`. The JVM links those classes as soon as the common class loads, so the server tried to load the client side and crashed.

**What we changed**

- **Client work from common code** â€” Panel open, HUD packet handling, and menu/screen sync now go through `SafeClientCalls` + `RadiotowersClientHooks` (reflection), so server bytecode no longer pulls in client classes.
- **TaCZ / NMS mixin** â€” `NoMindlessShootingTacZListenerMixin` is registered only when TaCZâ€™s `GunShootEvent` is on the classpath, so servers **without** TaCZ do not load that mixin class (which referenced TaCZ types).
- **Metadata** â€” `mods.toml`: `displayTest="MATCH_VERSION"` and a short description that the mod is required on **both** server and client with matching version.

---

1.0.6

Update by: Extra_Special_K

Added:

-No Mindless Shooting (TaCZ): blocks shot counting and horde spawns during RadioTowers defense waves (TacZ listener + server TriggerHandler); defense-session sync on client/server.

-Wave stability: wider spawn join capture; removed periodic excess-zombie discard; reconnect ghost-sweeps do not run during a live catalog wave (avoids wiping an active defense).

-Nuclear baseline: per-tick sky-to-ring teleport on living tick disabled so we donâ€™t fight Berezka spawn position every tick.

-Airdrop cooldown enforced (3 min standard / 10 min after wave delivery); dev testing bypass removed from code.

-Build copies radiotowers-${mod_version}.jar for deploy when multiple JARs exist in build/libs.

1.0.5 hotfix

Update by: Extra_Special_K

Added:

-Airdrop plane and crate entities â€“ Flyover plane, parachute crate, spawn/tick/render pipeline, and server procedures to deliver ordered or loot-table drops.

-Full airdrop networking â€“ Custom packets for calling airdrops, syncing wave and cooldown state, and driving Dead Air panel activation with station IDs.

-radiotowers-common.toml â€“ Configurable airdrop catalog (tags, blacklist, custom entries, TaCZ toggle, wave behaviour, difficulty caps).

-Call Airdrop screen â€“ Point-budget loot catalog with search, difficulty meter, textured start lever, and Dead Air station controls (Turn off, default cycle, Dynamic, Internet) when the Zombie Waves API is installed.

-Radio panel modes â€“ RadioTowers-only: immediate standard airdrop from the panel. With Dead Air but without Zombie Waves: Radio Panel screen with the same lever and station buttons, standard loot-table airdrop, and 3-minute cooldown. With Zombie Waves: full catalog + defense-wave flow.

-Optional mod hooks (non-mandatory dependencies) â€“ Dead Air, TaCZ, Berezka Zombie Waves API, and Berezka API are optional at load time; mixin plugin skips wave mixins when the API is not installed.

-Berezka Zombie Waves integration â€“ Airdrop â€œpointsâ€ map to wave count and tier; plane spawns after waves complete; ground snap, spawn caps, replacement/agro handlers, wave-end detection, and cleanup against stuck or duplicate mobs.

-Persisted wave state â€“ SavedData for pending airdrops so behaviour is tied to the world across sessions where applicable.

-Persisted airdrop cooldowns â€“ Overworld SavedData stores per-player cooldown end times (3 min after a standard call, 10 min after a wave-delivered catalog run); not reset by leaving to the menu or restarting the game.

-Airdrop wave HUD â€“ Client overlay for active wave index, zombie count, and wave timer.

-TaCZ catalog and crates â€“ When TaCZ is present: guns and ammo in the catalog, sane stack sizes and NBT for tacz:ammo vs ammo boxes in crates and orders.

-Extra loot tables â€“ Additional airdrop difficulty loot (airdrop_loot_easy / _medium / _hard) beyond the single chests/airdrop_loot table in the reference build.

-Internet station screen â€“ Sub-screen for the Internet station option from the panel UI.

-Misc integration â€“ Reflection helpers for Berezka curWorld, optional suppression of duplicate Berezka wave chat, drowned kill counting hook, level load cleanup for planes and pending waves.

Dependencies:

-JAR only.

-This build additionally declares optional dependencies on dead_air, tacz, berezkas_zombie_waves_api, and berezka_api (all mandatory=false).

