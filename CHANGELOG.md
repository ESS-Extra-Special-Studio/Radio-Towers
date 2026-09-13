## 1.2.4 (1.2.4 NeoForge 1.21.1)

Update by: Extra_Special_K

Added:
Optional airdrop lobby on a shared server. Invite online players, ready-check, shared countdown, then Go. The solo lever path is unchanged.
Pending invites block Go until cancelled, and the lobby says why Go is blocked.
Accept teleports to the tower panel and chat says so. Decline is equally clear.
Lobbied crates are members-only. Solo crates stay world loot.
Config lobbyMaxPlayers, lobbyCountdownSeconds, lobbyInviteTimeoutSeconds, lobbyMembersOnlyCrates.
Commands /radiotowers lobby accept, decline, and ready (chat buttons on the invite).
Airdrop Lobby screen (online list, party, status banner) using ESC.
Changed:
ESL is required (ESC already required it). ESN is optional. Without ESN the solo path still works and the lobby does not.

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

