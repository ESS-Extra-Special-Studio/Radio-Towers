package net.mcreator.radiotowers.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.mcreator.radiotowers.integration.ZombieWavesMixinContext;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into Berezka's Zombie Waves API WavesManager.
 * WavesManager has NO "level" field — onServerTick() uses berezka_api_main.getCurWorld() (static curWorld).
 * We: (1) consume pending level in start(BlockPos) for context; (2) at HEAD of onServerTick, if getCurWorld() is null, set curWorld from our context so the API never NPEs.
 */
@Mixin(targets = "org.berezka.berezkas_zombie_waves_api.WavesManager")
public class WavesManagerMixin {

    @Inject(method = "start(Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"), remap = false)
    private void radiotowers$injectLevel(BlockPos pos, CallbackInfo ci) {
        ZombieWavesMixinContext.getAndClearPendingLevel();
    }

    /** API uses getCurWorld() in onServerTick; if it's null we set berezka_api_main.curWorld from our context. */
    @Inject(method = "onServerTick(Lnet/minecraftforge/event/TickEvent$ServerTickEvent;)V", at = @At("HEAD"), remap = false)
    private void radiotowers$ensureCurWorldSet(ServerTickEvent.Post event, CallbackInfo ci) {
        try {
            var opt = net.neoforged.fml.ModList.get().getModContainerById("berezka_api");
            if (opt.isEmpty()) return;
            Class<?> main = Class.forName("org.berezka.berezka_api.berezka_api_main", true, net.mcreator.radiotowers.RadiotowersMod.class.getClassLoader());
            java.lang.reflect.Field curWorldField = main.getField("curWorld");
            Object current = curWorldField.get(null);
            if (current != null) return;
            ServerLevel level = ZombieWavesMixinContext.getCurrentLevel();
            if (level == null) level = ZombieWavesMixinContext.getBerezkaCurWorld();
            if (level != null) {
                curWorldField.set(null, level);
            }
        } catch (Throwable t) {
            // ignore
        }
    }
}
