Structure template NBT files must go here for /dead_air spawntower to work.

Required files (names must match exactly):
  tower.nbt
  tower_2.nbt
  tower_overrun.nbt

These are the same IDs referenced in data/radiotowers/worldgen/template_pool/*.json ("location": "radiotowers:tower", etc.).

How to get them:
1. From MCreator: In-game, build each structure, use a Structure Block to Save it, then in MCreator use File > Export workspace or export structures so the .nbt files end up in this folder.
2. From an older RadioTowers JAR: If you have a version that had working spawns, unzip it and copy data/radiotowers/structures/*.nbt into this folder, then rebuild the mod.

Without these files, the built mod JAR will not contain structure templates and Dead Air's /dead_air spawntower command will report "Structure not found".
