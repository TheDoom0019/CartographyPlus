package com.mcm.cartographyplus;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CartographyPlus.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<WideEyeProjectile>> WIDE_EYE =
            ENTITIES.register("wide_eye", () -> EntityType.Builder
                    .<WideEyeProjectile>of(WideEyeProjectile::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("cartographyplus:wide_eye"));

    public static final DeferredHolder<EntityType<?>, EntityType<ChestMarkerEntity>> CHEST_MARKER =
            ENTITIES.register("chest_marker", () -> EntityType.Builder
                    .<ChestMarkerEntity>of(ChestMarkerEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("cartographyplus:chest_marker"));

    private ModEntities() {
    }
}
