package com.mcm.cartographyplus;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(CartographyPlus.MODID)
public class CartographyPlus {
    public static final String MODID = "cartographyplus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<SplitCartographyMenu>> SPLIT_MENU =
            MENUS.register("split_cartography", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> {
                        BlockPos pos = buf.readBlockPos();
                        Level level = inv.player.level();
                        LOGGER.debug("[cp] menu factory side={} pos={}", level.isClientSide ? "client" : "server", pos);
                        ContainerLevelAccess access = level.isClientSide
                                ? ContainerLevelAccess.NULL
                                : ContainerLevelAccess.create(level, pos);
                        return new SplitCartographyMenu(id, inv, access, pos, level);
                    }));

    public CartographyPlus(IEventBus modBus) {
        MENUS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        LOGGER.info("Cartography Plus loaded");
    }

    public static void openMenu(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new SplitCartographyMenu(id, inv,
                        ContainerLevelAccess.create(level, pos), pos, level),
                Component.translatable("container.cartography_table")),
                buf -> buf.writeBlockPos(pos));
    }
}
