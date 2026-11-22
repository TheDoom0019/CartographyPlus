package com.mcm.cartographyplus;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Logs which of our recipes actually made it into the recipe manager. */
@EventBusSubscriber(modid = CartographyPlus.MODID)
public final class RecipeCheck {

    private RecipeCheck() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        var ids = event.getServer().getRecipeManager().getRecipeIds()
                .filter(id -> id.getNamespace().equals(CartographyPlus.MODID))
                .toList();
        CartographyPlus.LOGGER.debug("[cp] recipes loaded: {}", ids);
    }
}
