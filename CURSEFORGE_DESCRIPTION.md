# Apocalypse Structures: Radio Towers and Airdrops — CurseForge Page Description

## Short Summary (for CurseForge short description field — under 200 characters)

Worldgen radio towers and airdrops: climb metal bars, use the radio panel for coords and supply drops. Optional Dead Air adds broadcasts and stations. Requires ExtraSpecialCore.

*(~155 characters — adjust if needed)*

---

## Full Description (for CurseForge long description field — paste below the line)

---

**Apocalypse Structures: Radio Towers and Airdrops**

Adds **three climbable radio tower** structures and an **airdrop** system to your world. Climb **Metal Bars** to the top, use the **Radio Panel**, and call in supply drops—or coordinate with **Dead Air** for full broadcast and walkie-tuning gameplay when that mod is installed.

---

**Tower types**

- **Standard** — basic tower; panel works immediately.
- **Fenced** — includes loot; panel may need activation (saved per world).
- **Overrun** — includes loot and pillagers; same activation rules as fenced towers.

---

**Blocks (uncraftable, found in structures)**

- **Metal Bars** — climbable; used on tower exteriors.
- **Radio Panel** — right-click to open panel UI: nearest airdrop coordinates, standard airdrop lever, and (with optional mods) station assignment and catalog ordering.
- **Airdrop Container / crate** — delivered loot from plane flyover.
- **Trash block** — negates fall damage when you land on it.

---

**How it works**

**Structures** generate in the world (configurable via structure sets / pack datapacks).

**Radio Panel** behaviour depends on what else is installed:

- **RadioTowers only** — panel UI for coordinates and a **standard airdrop** (loot table, cooldown).
- **+ Dead Air (optional)** — panel also assigns **broadcast stations** (Turn off, default cycle, Dynamic, Internet). Tune and listen on a **walkie-talkie** through Dead Air’s GUI (MHz, signal bars, music).
- **+ ExtraSpecialLIB / Berezka Zombie Waves API (optional)** — **Call Airdrop** catalog with point budget, search, difficulty, and **defense waves** before the plane delivers your order. ESL is used when present; Berezka is the fallback.

**Airdrop cooldown:** 3 minutes for standard panel calls; longer after wave-based delivery when the waves API is present.

---

**How to use it**

1. Find a radio tower in the world (or spawn one if your pack adds commands/datapacks).
2. Climb **Metal Bars** to the **Radio Panel** at the top.
3. Right-click the panel — coordinates to the nearest airdrop site, lever/buttons for drops and (if installed) station and catalog options.
4. With **Dead Air** + a **walkie-talkie**, turn the walkie on and open Dead Air’s tuning screen to hear assigned stations.

Further options: pack config, **Berezka API for Radio Towers and Airdrops**, **Berezka's library**, and in-mod toml where applicable.

---

**Dependencies**

- **Required:** **ExtraSpecialCore (ESC)** — shared panel and airdrop GUIs.
- **Optional:** **Dead Air** — tower broadcasts, station assignment on panels, walkie integration. Radio Towers does **not** require Dead Air to load; structures and basic airdrops work without it.  
  https://www.curseforge.com/minecraft/mc-mods/dead-air
- **Optional:** **Berezka's library** — Berezka structure/loot library used by some Radio Towers integrations (not required for basic play).  
  https://www.curseforge.com/minecraft/mc-mods/berezka-library
- **Optional:** **Berezka API for Radio Towers and Airdrops** — extra config and customization.  
  https://www.curseforge.com/minecraft/mc-mods/berezka-api-for-radio-towers-and-airdrops
- **Optional:** **Berezka's Zombie Waves API** — defense waves and full Call Airdrop catalog flow (Zombie Waves API requires Berezka's library).  
  https://www.curseforge.com/minecraft/mc-mods/berezkas-zombie-waves-api

Radio Towers is the **structure and airdrop foundation**. Dead Air adds the **radio network** on top; neither replaces the other.

---

**Permissions & credits**

You can use this in your modpacks and you can edit this mod as you wish! Just, don't claim it as your own mod :(

Suggestions are greatly appreciated :D

**Author:** [that1lilguy](https://www.curseforge.com/members/that1lilguy/projects)

**Discord:** https://discord.gg/fkbVsYQXYu

---
