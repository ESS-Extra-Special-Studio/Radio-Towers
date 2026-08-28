package net.mcreator.radiotowers.mixin;

import com.tacz.guns.api.event.common.GunShootEvent;
import net.mcreator.radiotowers.integration.DefenseSessionClientMirror;
import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No Mindless Shooting compatibility via TacZListener.
 *
 * While a RadioTowers airdrop wave is active, this cancels NMS's TacZ gun listener entirely,
 * so NMS does not count shots, print "too loud" warnings, or start hordes.
 *
 * Outside airdrop waves NMS behaves exactly as designed.
 *
 * <p>TacZ gun events run on the logical client as well as the server; NMS chat/warnings are often client-side.
 * We cancel on {@link ServerPlayer} using server state and on the client player using {@link DefenseSessionClientMirror}.
 */
@Pseudo
@Mixin(targets = "stev6.nomindlessshooting.TacZListener")
public abstract class NoMindlessShootingTacZListenerMixin {

    @Inject(
        method = "onFire(Lcom/tacz/guns/api/event/common/GunShootEvent;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void dead_air$skipNmsDuringAirdrop(GunShootEvent event, CallbackInfo ci) {
        if (event == null) return;
        try {
            LivingEntity shooter = event.getShooter();
            if (!(shooter instanceof Player player)) {
                return;
            }
            if (player instanceof ServerPlayer sp) {
                if (ZombieWavesAPILoader.isDefenseWaveActive(sp.serverLevel())) {
                    ci.cancel();
                }
                return;
            }
            if (player.level().isClientSide()
                && DefenseSessionClientMirror.isActive(player.level().dimension())) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // Fall back to normal behaviour if anything unexpected fails.
        }
    }
}

