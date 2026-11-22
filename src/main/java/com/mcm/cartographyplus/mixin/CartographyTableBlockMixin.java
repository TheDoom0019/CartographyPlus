package com.mcm.cartographyplus.mixin;

import com.mcm.cartographyplus.CartographyPlus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CartographyTableBlock.class)
public abstract class CartographyTableBlockMixin {

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void cartographyplus$openEnhanced(BlockState state, Level level, BlockPos pos,
                                              Player player, BlockHitResult hit,
                                              CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            CartographyPlus.LOGGER.debug("opening enhanced cartography table at {}", pos);
            CartographyPlus.openMenu(serverPlayer, pos);
            serverPlayer.awardStat(Stats.INTERACT_WITH_CARTOGRAPHY_TABLE);
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }
}
