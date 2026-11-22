package com.mcm.cartographyplus;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CartographyPlus.MODID);

    /** Crafted from an ender pearl and a ghast tear; pays the experience for a map reveal. */
    public static final DeferredItem<Item> WIDE_EYE = ITEMS.registerItem(
            "wide_eye", WideEyeItem::new, new Item.Properties().rarity(Rarity.RARE).stacksTo(16));

    private ModItems() {
    }
}
