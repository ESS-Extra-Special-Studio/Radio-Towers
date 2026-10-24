/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;

import net.mcreator.radiotowers.RadiotowersMod;

public class RadiotowersModSounds {
	public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, RadiotowersMod.MODID);
	public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_TUNING = REGISTRY.register("radio_tuning", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("radiotowers", "radio_tuning")));
	public static final DeferredHolder<SoundEvent, SoundEvent> TRASH_WALKING = REGISTRY.register("trash_walking", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("radiotowers", "trash_walking")));
	public static final DeferredHolder<SoundEvent, SoundEvent> FALL_IN_TRASH = REGISTRY.register("fall_in_trash", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("radiotowers", "fall_in_trash")));
	public static final DeferredHolder<SoundEvent, SoundEvent> PLANEFLYBY = REGISTRY.register("planeflyby", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("radiotowers", "planeflyby")));
	public static final DeferredHolder<SoundEvent, SoundEvent> PARACHUTE_OPENING = REGISTRY.register("parachute_opening", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("radiotowers", "parachute_opening")));
	public static final DeferredHolder<SoundEvent, SoundEvent> SIREN = REGISTRY.register("siren", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("radiotowers", "siren")));
}