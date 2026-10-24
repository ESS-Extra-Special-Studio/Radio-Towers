package net.mcreator.radiotowers.client.gui;
import net.minecraft.util.text.TranslationTextComponent;

import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import com.mojang.blaze3d.matrix.MatrixStack;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscGraphics;

import net.mcreator.radiotowers.world.inventory.AirdropCrateguiMenu;
import net.mcreator.radiotowers.init.RadiotowersModScreens;

import com.mojang.blaze3d.systems.RenderSystem;

public class AirdropCrateguiScreen extends ContainerScreen<AirdropCrateguiMenu> implements RadiotowersModScreens.ScreenAccessor {
	private final World world;
	private final int x, y, z;
	private final PlayerEntity entity;
	private boolean menuStateUpdateActive = false;

	public AirdropCrateguiScreen(AirdropCrateguiMenu container, PlayerInventory inventory, ITextComponent text) {
		super(container, inventory, text);
		this.world = container.world;
		this.x = container.x;
		this.y = container.y;
		this.z = container.z;
		this.entity = container.entity;
		this.imageWidth = 176;
		this.imageHeight = 166;
	}

	@Override
	public void updateMenuState(int elementType, String name, Object elementState) {
		menuStateUpdateActive = true;
		menuStateUpdateActive = false;
	}

	private static final ResourceLocation texture = new ResourceLocation("radiotowers:textures/screens/airdrop_crategui.png");

	@Override
	public void render(MatrixStack pose, int mouseX, int mouseY, float partialTicks) {
		this.renderBackground(pose);
		super.render(pose, mouseX, mouseY, partialTicks);
		this.renderTooltip(pose, mouseX, mouseY);
	}

	@Override
	protected void renderBg(MatrixStack pose, float partialTicks, int mouseX, int mouseY) {
		RenderSystem.color4f(1, 1, 1, 1);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		new EscGraphics(pose).blit(texture, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
		RenderSystem.disableBlend();
	}

	@Override
	public boolean keyPressed(int key, int b, int c) {
		if (key == 256) {
			this.minecraft.player.closeContainer();
			return true;
		}
		return super.keyPressed(key, b, c);
	}

	@Override
	protected void renderLabels(MatrixStack pose, int mouseX, int mouseY) {
		this.font.draw(pose, new TranslationTextComponent("gui.radiotowers.airdrop_crategui.label_airfrop_crate"), 7, 9, -12829636);
	}

	@Override
	public void init() {
		super.init();
	}
}