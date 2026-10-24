package net.mcreator.radiotowers.client.renderer;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

public class PlaneentityRenderState extends LivingEntityRenderState {
	public boolean crashedWreck;
	public final AnimationState flyIn = new AnimationState();
}
