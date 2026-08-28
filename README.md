# Apocalypse Structures: Radio Towers and Airdrops

Forge **1.20.1** mod — worldgen radio towers, airdrops, and radio panels. Optional integration with [Dead Air](https://github.com/ESS-Extra-Special-Studio/Dead-Air) for broadcasts and walkie tuning.

## Ownership

**This mod is not owned by Extra Special Studio.**

| Role | Who |
|------|-----|
| **Original author & project owner** | **[that1lilguy](https://www.curseforge.com/members/that1lilguy/projects)** |
| **Collaborator (this repository)** | [Extra Special Studio](https://extraspecialstudio.co.uk) — Dead Air integration, maintenance builds, Modrinth/CF release help |

Extra Special Studio maintains this GitHub mirror and integration work **with the author's permission**. We do not claim authorship of the original Apocalypse Structures concept or assets.

See **[COLLABORATORS.md](COLLABORATORS.md)** for the full attribution table and download links.

## Downloads

| Platform | Link |
|----------|------|
| CurseForge (author) | [that1lilguy's projects](https://www.curseforge.com/members/that1lilguy/projects) |
| Modrinth | [apocalypse-structures-radio-towers-and-airdrops](https://modrinth.com/project/apocalypse-structures-radio-towers-and-airdrops) |

## Build (Forge)

```powershell
Set-Location "C:\Users\Ksivi\MCreatorWorkspaces\radioos"
.\gradlew.bat build --no-daemon
```

Copy the JAR to your test modpack, or run `.\buildAndCopyToModpack.ps1` (targets **C.Ideas** when configured).

**Requires:** [ExtraSpecialCore (ESC)](https://github.com/ESS-Extra-Special-Studio/ESC) 2.0.0+  
**Optional:** Dead Air, Berezka Zombie Waves API, TaCZ

## Workspace

- **Local path:** `MCreatorWorkspaces\radioos`
- **modId:** `radiotowers`
- **Package:** `net.mcreator.radiotowers` (original MCreator namespace — unchanged)

Integration docs with Dead Air live in the [Dead Air](https://github.com/ESS-Extra-Special-Studio/Dead-Air) repo (`THREE_MOD_INTEGRATION_PLAN.md`, etc.).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
