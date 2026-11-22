package com.mcm.cartographyplus;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Any interaction with a highlighted container removes its glowing outline. */
@EventBusSubscriber(modid = CartographyPlus.MODID)
public final class MarkerEvents {

    private MarkerEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        if (EyeScanner.isContainer(level.getBlockState(event.getPos()))) {
            EyeScanner.clearAt(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide) {
            EyeScanner.clearAt(level, event.getPos());
        }
    }

}