/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;

import net.mcreator.radiotowers.RadiotowersMod;

public class RadiotowersModSounds {
	public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RadiotowersMod.MODID);
	public static final RegistryObject<SoundEvent> RADIO_TUNING = REGISTRY.register("radio_tuning", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("radiotowers", "radio_tuning")));
	public static final RegistryObject<SoundEvent> TRASH_WALKING = REGISTRY.register("trash_walking", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("radiotowers", "trash_walking")));
	public static final RegistryObject<SoundEvent> FALL_IN_TRASH = REGISTRY.register("fall_in_trash", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("radiotowers", "fall_in_trash")));
	public static final RegistryObject<SoundEvent> PLANEFLYBY = REGISTRY.register("planeflyby", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("radiotowers", "planeflyby")));
	public static final RegistryObject<SoundEvent> PARACHUTE_OPENING = REGISTRY.register("parachute_opening", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("radiotowers", "parachute_opening")));
	public static final RegistryObject<SoundEvent> SIREN = REGISTRY.register("siren", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("radiotowers", "siren")));
}