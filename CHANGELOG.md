## 1.2.3 (1.2.3 NeoForge 1.21.1)

Update by: Extra_Special_K

Fixed:
Call Airdrop layout matches Forge again (column weights, gaps, search height, difficulty meter).
World dim behind Call Airdrop / Internet / Panel Settings uses ESC renderWorldDim once (no muddy double-dim).

# Changelog

## 1.2.2 (`1.2.2` NeoForge 1.21.1)

Update by: Extra_Special_K

Loader port of RadioTowers **1.2.2** to NeoForge 1.21.1.

Fixed:

- Airdrop crates hanging in the sky after the plane drop â€” crates step down and place on ground (same fix as Forge).
- Structure density accessors aligned with Forge so tower density config stays consistent.

## 1.2.1 (`1.2.1` NeoForge 1.21.1)

Loader port of RadioTowers 1.2.1 to NeoForge 1.21.1.

- Required: Minecraft 1.21.1, NeoForge, ExtraSpecialCore `[2.0.0,3.0)`, ExtraSpecialLIB `[1.0.0,2.0)`.
- Catalog defense waves use the ESL Wave API. Berezka is optional and no-ops if its classes are missing.
- TaCZ and Dead Air stay optional (reflection / mixin plugin).

## 1.2.1 (`1.2.1` Forge 1.20.1)

Update by: Extra_Special_K

Requires **ExtraSpecialCore (ESC) 2.0.0** or newer. Catalog defense waves use **ExtraSpecialLIB (ESL) 1.0.1** when installed (Berezka remains the fallback).

