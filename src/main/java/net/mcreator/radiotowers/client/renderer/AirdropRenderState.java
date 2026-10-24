package net.mcreator.radiotowers.client.renderer;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/** Client render snapshot for the falling airdrop crate. */
public class AirdropRenderState extends LivingEntityRenderState {
    public final AnimationState glider = new AnimationState();
    public final AnimationState falling = new AnimationState();
}
