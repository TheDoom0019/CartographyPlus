package com.mcm.cartographyplus;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Living entities crash in their constructor unless their attribute supplier is registered, and the
 * supplier has to contain everything the constructor touches (max health, follow range for the
 * path navigation, ...). {@code Mob.createMobAttributes()} covers the vanilla baseline.
 */
@EventBusSubscriber(modid = CartographyPlus.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class EntityAttributes {

    private EntityAttributes() {
    }

    @SubscribeEvent
    public static void onCreateAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.CHEST_MARKER.get(),
                Mob.createMobAttributes().add(Attributes.ATTACK_DAMAGE, 0.0D).build());
    }
}
