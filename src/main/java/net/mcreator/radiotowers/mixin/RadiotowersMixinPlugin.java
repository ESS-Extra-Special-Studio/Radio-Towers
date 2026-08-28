package net.mcreator.radiotowers.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Skips {@link WavesManagerMixin} when Berezka Zombie Waves API is not installed — the target class is absent
 * and Mixin would fail. Registers {@code NoMindlessShootingTacZListenerMixin} only when TaCZ is on the classpath
 * so dedicated servers without TaCZ do not link {@code GunShootEvent}.
 */
public final class RadiotowersMixinPlugin implements IMixinConfigPlugin {

    private static final String WAVES_MANAGER_CLASS = "org.berezka.berezkas_zombie_waves_api.WavesManager";
    private static final String WAVES_MANAGER_MIXIN = "net.mcreator.radiotowers.mixin.WavesManagerMixin";
    private static final String TACZ_GUN_SHOOT_EVENT = "com.tacz.guns.api.event.common.GunShootEvent";
    private static final String NMS_TACZ_LISTENER_MIXIN = "NoMindlessShootingTacZListenerMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (WAVES_MANAGER_MIXIN.equals(mixinClassName)) {
            return isClassLoadable(WAVES_MANAGER_CLASS);
        }
        return true;
    }

    /**
     * Forge can use layered/sibling classloaders; checking only this mod's loader may return false negatives.
     */
    private static boolean isClassLoadable(String binaryName) {
        ClassLoader[] loaders = {
            Thread.currentThread().getContextClassLoader(),
            RadiotowersMixinPlugin.class.getClassLoader(),
            ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader cl : loaders) {
            if (cl == null) continue;
            try {
                Class.forName(binaryName, false, cl);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        if (isClassLoadable(TACZ_GUN_SHOOT_EVENT)) {
            return Collections.singletonList(NMS_TACZ_LISTENER_MIXIN);
        }
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
