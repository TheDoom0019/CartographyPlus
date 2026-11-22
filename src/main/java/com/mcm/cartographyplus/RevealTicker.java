package com.mcm.cartographyplus;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = CartographyPlus.MODID)
public final class RevealTicker {

    private RevealTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RevealManager.tick(event);
        EyeScanner.tick(event.getServer());
    }
}
