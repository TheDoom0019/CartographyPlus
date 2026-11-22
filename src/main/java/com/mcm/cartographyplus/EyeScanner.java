package com.mcm.cartographyplus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/** The wide eye's landing scan: hostiles glow, containers get a chest-shaped glowing outline. */
public final class EyeScanner {

    private static final int RADIUS = 32;
    private static final int GLOW_TICKS = 30 * 20;
    /** Hard cap so a chest room cannot spawn a thousand markers. */
    private static final int MAX_MARKERS = 64;

    private record MarkerKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final Map<MarkerKey, ChestMarkerEntity> MARKERS = new HashMap<>();

    private EyeScanner() {
    }

    public static void scan(ServerLevel level, BlockPos center, Player player) {
        int glowing = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(RADIUS),
                entity -> entity instanceof Enemy && entity.isAlive())) {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
            glowing++;
        }

        int highlighted = 0;
        int minCX = (center.getX() - RADIUS) >> 4;
        int maxCX = (center.getX() + RADIUS) >> 4;
        int minCZ = (center.getZ() - RADIUS) >> 4;
        int maxCZ = (center.getZ() + RADIUS) >> 4;
        outer:
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (pos.distSqr(center) > (double) RADIUS * RADIUS) {
                        continue;
                    }
                    if (!isContainer(level.getBlockState(pos))) {
                        continue;
                    }
                    spawnMarker(level, pos);
                    highlighted++;
                    if (highlighted >= MAX_MARKERS) {
                        break outer;
                    }
                }
            }
        }

        CartographyPlus.LOGGER.info("[cp] wide eye scan: {} hostiles glowing, {} containers highlighted",
                glowing, highlighted);
        if (player != null) {
            player.displayClientMessage(Component.translatable("cartographyplus.scan.done",
                    glowing, highlighted), true);
        }
    }

    private static void spawnMarker(ServerLevel level, BlockPos pos) {
        MarkerKey key = new MarkerKey(level.dimension(), pos.immutable());
        clearAt(level, pos);
        ChestMarkerEntity marker = new ChestMarkerEntity(ModEntities.CHEST_MARKER.get(), level);
        marker.setPos(pos.getX() + 0.5D, pos.getY() + 0.07D, pos.getZ() + 0.5D);
        level.addFreshEntity(marker);
        MARKERS.put(key, marker);
    }

    /** Drops the highlight of one container (used when it is opened, broken or replaced). */
    public static void clearAt(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        MarkerKey key = new MarkerKey(level.dimension(), pos.immutable());
        ChestMarkerEntity marker = MARKERS.remove(key);
        if (marker != null) {
            marker.discard();
        }
    }

    public static void clearAll(Level level) {
        MARKERS.entrySet().removeIf(entry -> {
            if (entry.getKey().dimension().equals(level.dimension())) {
                entry.getValue().discard();
                return true;
            }
            return false;
        });
    }

    /** Keeps the bookkeeping clean and removes highlights whose container is gone. */
    public static void tick(MinecraftServer server) {
        if (MARKERS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<MarkerKey, ChestMarkerEntity>> iterator = MARKERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MarkerKey, ChestMarkerEntity> entry = iterator.next();
            MarkerKey key = entry.getKey();
            ChestMarkerEntity marker = entry.getValue();
            ServerLevel level = server.getLevel(key.dimension());
            if (marker == null || !marker.isAlive() || level == null
                    || !isContainer(level.getBlockState(key.pos()))) {
                if (marker != null) {
                    marker.discard();
                }
                iterator.remove();
            }
        }
    }

    public static boolean isContainer(BlockState state) {
        return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.BARREL) || state.is(Blocks.ENDER_CHEST);
    }
}
