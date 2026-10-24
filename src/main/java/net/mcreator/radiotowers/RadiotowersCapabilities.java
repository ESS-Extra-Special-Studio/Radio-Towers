package net.mcreator.radiotowers;

import net.mcreator.radiotowers.block.entity.AirdropCrateBlockEntity;
import net.mcreator.radiotowers.init.RadiotowersModBlockEntities;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

@EventBusSubscriber(modid = RadiotowersMod.MODID)
public final class RadiotowersCapabilities {
    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, RadiotowersModBlockEntities.AIRDROP_CRATE.get(),
            (be, side) -> {
                if (!(be instanceof AirdropCrateBlockEntity crate)) return null;
                if (side == null) return VanillaContainerWrapper.of(crate);
                return new WorldlyContainerWrapper(crate, side);
            });
    }

    private RadiotowersCapabilities() {}
}
