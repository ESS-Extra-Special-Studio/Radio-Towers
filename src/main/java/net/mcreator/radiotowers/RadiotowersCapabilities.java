package net.mcreator.radiotowers;

import net.mcreator.radiotowers.block.entity.AirdropCrateBlockEntity;
import net.mcreator.radiotowers.init.RadiotowersModBlockEntities;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

@EventBusSubscriber(modid = RadiotowersMod.MODID, bus = Bus.MOD)
public final class RadiotowersCapabilities {
    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, RadiotowersModBlockEntities.AIRDROP_CRATE.get(),
            (be, side) -> {
                if (!(be instanceof AirdropCrateBlockEntity crate)) return null;
                if (side == null) return new InvWrapper(crate);
                return new SidedInvWrapper(crate, side);
            });
    }

    private RadiotowersCapabilities() {}
}
