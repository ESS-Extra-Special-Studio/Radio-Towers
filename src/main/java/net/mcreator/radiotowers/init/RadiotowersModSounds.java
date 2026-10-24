/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.util.SoundEvent;
import net.minecraft.util.ResourceLocation;

import net.mcreator.radiotowers.RadiotowersMod;

public class RadiotowersModSounds {
	public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RadiotowersMod.MODID);
	public static final RegistryObject<SoundEvent> RADIO_TUNING = REGISTRY.register("radio_tuning", () -> new SoundEvent(new ResourceLocation("radiotowers", "radio_tuning")));
	public static final RegistryObject<SoundEvent> TRASH_WALKING = REGISTRY.register("trash_walking", () -> new SoundEvent(new ResourceLocation("radiotowers", "trash_walking")));
	public static final RegistryObject<SoundEvent> FALL_IN_TRASH = REGISTRY.register("fall_in_trash", () -> new SoundEvent(new ResourceLocation("radiotowers", "fall_in_trash")));
	public static final RegistryObject<SoundEvent> PLANEFLYBY = REGISTRY.register("planeflyby", () -> new SoundEvent(new ResourceLocation("radiotowers", "planeflyby")));
	public static final RegistryObject<SoundEvent> PARACHUTE_OPENING = REGISTRY.register("parachute_opening", () -> new SoundEvent(new ResourceLocation("radiotowers", "parachute_opening")));
	public static final RegistryObject<SoundEvent> SIREN = REGISTRY.register("siren", () -> new SoundEvent(new ResourceLocation("radiotowers", "siren")));
}