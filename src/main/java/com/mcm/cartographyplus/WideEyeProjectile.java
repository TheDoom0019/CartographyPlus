package com.mcm.cartographyplus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class WideEyeProjectile extends ThrowableItemProjectile {

    public WideEyeProjectile(EntityType<? extends WideEyeProjectile> type, Level level) {
        super(type, level);
    }

    public WideEyeProjectile(Level level, LivingEntity owner) {
        super(ModEntities.WIDE_EYE.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.WIDE_EYE.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        impact(result.getEntity().blockPosition());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        impact(result.getBlockPos());
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    private void impact(BlockPos pos) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        EyeScanner.scan(serverLevel, pos, this.getOwner() instanceof Player p ? p : null);
        serverLevel.playSound(null, pos, SoundEvents.ENDER_EYE_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
