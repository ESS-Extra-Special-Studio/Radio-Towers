package net.mcreator.radiotowers.integration;

import net.minecraftforge.fml.ModList;
import net.mcreator.radiotowers.RadiotowersMod;

import java.lang.reflect.Field;

/**
 * Berezka's {@code WavesManager} broadcasts "Wave started", "Enemies left", "Wave has ended", etc.
 * when {@code org.berezka.berezkas_zombie_waves_api.Config.chatMessages} is true. We turn that off so only RadioTowers
 * messages appear (see {@link ZombieWavesAPILoader} and {@link net.mcreator.radiotowers.integration.PendingAirdropStorage#deliverAt}).
 */
public final class BerezkaWaveChatSilencer {

    private static final String CONFIG_CLASS = "org.berezka.berezkas_zombie_waves_api.Config";
    private static final String[] WAVE_MOD_IDS = { "berezkas_zombie_waves_api", "berezka_zombie_waves_api" };

    private static boolean applied;
    private static Field chatMessagesField;

    private BerezkaWaveChatSilencer() {}

    /** @return true if the Zombie Waves API mod is loaded */
    public static boolean isZombieWavesApiPresent() {
        for (String id : WAVE_MOD_IDS) {
            if (ModList.get().isLoaded(id)) return true;
        }
        return false;
    }

    /**
     * Set Berezka {@code Config.chatMessages = false} so their wave messages are not broadcast.
     * Safe to call multiple times.
     */
    public static void apply() {
        if (!isZombieWavesApiPresent()) return;
        try {
            initField();
            if (chatMessagesField == null) return;
            chatMessagesField.setBoolean(null, false);
            if (!applied) {
                applied = true;
            }
        } catch (Throwable t) {
            RadiotowersMod.LOGGER.warn("[RadioTowers] Could not disable Berezka wave chat: {}", t.getMessage());
        }
    }

    private static void initField() throws ClassNotFoundException, NoSuchFieldException {
        if (chatMessagesField != null) return;
        ClassLoader loader = null;
        for (String id : WAVE_MOD_IDS) {
            var opt = ModList.get().getModContainerById(id);
            if (opt.isPresent()) {
                loader = opt.get().getMod().getClass().getClassLoader();
                break;
            }
        }
        if (loader == null)
            throw new ClassNotFoundException("Berezka zombie waves mod container not found");
        Class<?> cfg = Class.forName(CONFIG_CLASS, true, loader);
        chatMessagesField = cfg.getField("chatMessages");
    }
}
