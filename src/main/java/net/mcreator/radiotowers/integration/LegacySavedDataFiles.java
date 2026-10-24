package net.mcreator.radiotowers.integration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;

/** Reads a pre-1.21.5 saved-data file whose fields sit on the root compound. */
public final class LegacySavedDataFiles {
    private LegacySavedDataFiles() {}

    public static CompoundTag readLegacy(ServerLevel level, String name) {
        try {
            Path root = level.getServer().getWorldPath(LevelResource.ROOT);
            Identifier dim = level.dimension().identifier();
            Path file = Level.OVERWORLD.equals(level.dimension())
                ? root.resolve("data").resolve(name + ".dat")
                : root.resolve("dimensions").resolve(dim.getNamespace()).resolve(dim.getPath()).resolve("data").resolve(name + ".dat");
            if (!Files.isRegularFile(file)) return null;
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (tag == null || tag.contains("data")) return null;
            return tag;
        } catch (Exception ignored) {
            return null;
        }
    }
}
