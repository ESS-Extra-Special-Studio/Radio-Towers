package net.mcreator.radiotowers.client.renderer;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/** Client render snapshot for the airdrop plane. Animation timing stays on the entity. */
public class PlaneRenderState extends LivingEntityRenderState {
    public boolean crashedWreck;
    public final AnimationState flyIn = new AnimationState();
}
