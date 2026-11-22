package com.mcm.cartographyplus.client;

import com.mcm.cartographyplus.CartographyPlus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CartographyPlus.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.mcm.cartographyplus.ModEntities.WIDE_EYE.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(com.mcm.cartographyplus.ModEntities.CHEST_MARKER.get(),
                context -> new net.minecraft.client.renderer.entity.SlimeRenderer(context));
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        CartographyPlus.LOGGER.info("[cp] registering screen for {}", CartographyPlus.SPLIT_MENU.getId());
        event.register(CartographyPlus.SPLIT_MENU.get(), SplitCartographyScreen::new);
    }
}
