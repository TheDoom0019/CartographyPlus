package com.mcm.cartographyplus;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** Puts the wide eye into the vanilla tools tab so it shows up in creative search, JEI and EMI. */
@EventBusSubscriber(modid = CartographyPlus.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class CreativeTabs {

    private CreativeTabs() {
    }

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.WIDE_EYE.get());
        }
    }
}
