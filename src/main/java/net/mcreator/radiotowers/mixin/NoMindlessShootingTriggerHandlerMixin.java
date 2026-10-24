package net.mcreator.radiotowers.mixin;

import net.mcreator.radiotowers.integration.ZombieWavesAPILoader;
import net.minecraft.entity.player.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Defense-in-depth for No Mindless Shooting: {@code TacZListener.onFire} may not always be cancelled
 * (mixin order, or another entry into horde logic). Blocking {@code TriggerHandler.trigger} on the
 * server ensures no NMS horde spawns or The Hordes integration runs during RadioTowers defense waves.
 */
@Pseudo
@Mixin(targets = "stev6.nomindlessshooting.TriggerHandler")
public abstract class NoMindlessShootingTriggerHandlerMixin {

    @Inject(
        method = "trigger(Lnet/minecraft/server/level/ServerPlayerEntity;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void radiotowers$skipNmsDuringDefense(ServerPlayerEntity player, CallbackInfo ci) {
        if (player == null) return;
        try {
            if (ZombieWavesAPILoader.isDefenseWaveActive(player.getLevel())) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
