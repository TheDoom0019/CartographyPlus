package com.mcm.cartographyplus;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Invisible glowing slime used as a container highlight: a slime's model is a cube, so its glowing
 * outline is a chest-sized box instead of the tall armour stand silhouette.
 *
 * <p>It must be completely transparent to gameplay: no raycast target (so the chest behind can still
 * be opened), no attack interaction (so swinging at it does nothing) and no physics.</p>
 */
public class ChestMarkerEntity extends Slime {

    private static final int LIFETIME = 30 * 20;

    public ChestMarkerEntity(EntityType<? extends ChestMarkerEntity> type, Level level) {
        super(type, level);
        this.setSize(1, true);
        this.setNoAi(true);
        this.setSilent(true);
        this.setInvulnerable(true);
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.setDeltaMovement(Vec3.ZERO);
        this.noPhysics = true;
        if (this.getAttribute(Attributes.SCALE) != null) {
            // size 1 slime is 0.52 wide; scaling to 1.4 gives ~0.73, safely inside a chest (0.875)
            this.getAttribute(Attributes.SCALE).setBaseValue(1.4D);
        }
        this.setGlowingTag(true);
        this.setInvisible(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > LIFETIME) {
            this.discard();
        }
    }

    @Override
    public void playerTouch(Player player) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity entity) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }
}