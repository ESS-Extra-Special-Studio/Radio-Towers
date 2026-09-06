package net.mcreator.radiotowers.airdrop;

import net.mcreator.radiotowers.config.AirdropConfig;

/**
 * Runtime overrides for airdrop behavior toggles.
 * Null means "use config value"; non-null is a live override (set by command).
 */
public final class AirdropRuntimeToggles {
    private static volatile Boolean endRecipeItemsOverride = null;

    private AirdropRuntimeToggles() {}

    public static boolean isEndRecipeItemsEnabled() {
        Boolean override = endRecipeItemsOverride;
        return override != null ? override.booleanValue() : AirdropConfig.ENABLE_END_RECIPE_ITEMS.get();
    }

    public static void setEndRecipeItemsOverride(Boolean enabledOrNull) {
        endRecipeItemsOverride = enabledOrNull;
        AirdropCatalog.invalidate();
    }

    public static Boolean getEndRecipeItemsOverride() {
        return endRecipeItemsOverride;
    }
}
