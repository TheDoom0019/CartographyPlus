package com.mcm.cartographyplus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Reveals the map with several vertical walls of virtual cameras. Each wall keeps one full column
 * resident through player tickets, and only moves right once that column has been sampled. Anything
 * the walls miss is force-loaded in a final mop-up pass, so the result is always complete.
 */
public final class RevealManager {

    /**
     * Ticket radius in chunks for every camera. A bigger radius means every camera asks for a much
     * larger area at once, which keeps the chunk system's queue full (that is what makes the server
     * feel saturated, like Chunky does).
     */
    private static final int RADIUS = 10;
    /** How many walls sweep the map at the same time. */
    private static final int WALLS = 1;
    /** Ticks a column may stay incomplete before the wall moves on. */
    private static final int COLUMN_TIMEOUT = 2000;
    /** Milliseconds per tick for the final mop-up. */
    private static final long MOP_UP_MS = 50L;
    /**
     * After the last pixel is written the job keeps ticking for a while so the chunk system can
     * unload and save what it generated. Without this the world save on exit has to write thousands
     * of chunks in one go and the exit appears to hang.
     */
    private static final int DRAIN_TICKS = 400;
    /**
     * Chunks generated only for the map are not worth writing to disk: the terrain is generated from
     * the seed and will come back identically the next time someone visits. Clearing the unsaved flag
     * makes the chunk system drop them on unload, which keeps the world folder and the exit save
     * small. Player edits near a player are never touched.
     */
    private static final boolean DISCARD_GENERATED = true;

    private static final List<Job> JOBS = new ArrayList<>();

    private RevealManager() {
    }

    public static boolean start(ServerLevel level, MapItemSavedData data, ServerPlayer player, MapId mapId) {
        if (!JOBS.isEmpty()) {
            player.displayClientMessage(Component.translatable("cartographyplus.reveal.busy"), true);
            return false;
        }
        int span = 128 << data.scale;
        int minX = data.centerX - span / 2;
        int minZ = data.centerZ - span / 2;
        Job job = new Job(level, data, player, mapId,
                minX >> 4, minZ >> 4, (minX + span - 1) >> 4, (minZ + span - 1) >> 4);
        JOBS.add(job);
        CartographyPlus.LOGGER.info("[cp] reveal start: map={} scale={} chunks={} walls={} camerasPerWall={} radius={}",
                mapId == null ? -1 : mapId.id(), data.scale, job.pending.size(),
                job.walls.size(), job.walls.isEmpty() ? 0 : job.walls.get(0).zs.length, RADIUS);
        player.sendSystemMessage(Component.translatable("cartographyplus.reveal.started", job.pending.size()));
        return true;
    }

    public static void tick(ServerTickEvent.Post event) {
        if (JOBS.isEmpty()) {
            return;
        }
        Iterator<Job> iterator = JOBS.iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next();
            if (job.player.hasDisconnected() || !job.player.isAlive()) {
                job.releaseAll();
                iterator.remove();
                continue;
            }
            job.step();
            if (job.finished()) {
                job.releaseAll();
                job.data.setDirty();
                job.sync();
                CartographyPlus.LOGGER.info("[cp] reveal done: filled={} leftover={} discarded={} loadedChunks={}",
                        job.filled, job.pending.size(), job.discarded,
                        job.level.getChunkSource().getLoadedChunksCount());
                if (!job.player.hasDisconnected()) {
                    job.player.sendSystemMessage(Component.translatable(
                            "cartographyplus.reveal.done", job.filled));
                }
                iterator.remove();
            } else if (++job.ticks % 100 == 0) {
                long now = net.minecraft.Util.getMillis();
                double rate = (job.filled - job.lastLogFilled) * 1000.0 / Math.max(1L, now - job.lastLogTime);
                job.lastLogFilled = job.filled;
                job.lastLogTime = now;
                CartographyPlus.LOGGER.info("[cp] reveal progress: pending={} filled={} rate={}/s walls={}",
                        job.pending.size(), job.filled, String.format("%.1f", rate), job.walls.size());
            }
        }
    }

    private static final class Wall {
        final int[] zs;
        int columnX;
        int ticketedX = Integer.MIN_VALUE;
        int waiting;

        Wall(int[] zs, int columnX) {
            this.zs = zs;
            this.columnX = columnX;
        }
    }

    private static final class Job {
        private final ServerLevel level;
        private final MapItemSavedData data;
        private final ServerPlayer player;
        private final MapId mapId;
        private final Set<Long> pending = new HashSet<>();
        private final List<Wall> walls = new ArrayList<>();
        private final int minCX;
        private final int maxCX;
        private final int minCZ;
        private final int maxCZ;
        private int filled;
        private int ticks;
        private int drained;
        private int discarded;
        private int lastLogFilled;
        private long lastLogTime = net.minecraft.Util.getMillis();

        Job(ServerLevel level, MapItemSavedData data, ServerPlayer player, MapId mapId,
            int minCX, int minCZ, int maxCX, int maxCZ) {
            this.level = level;
            this.data = data;
            this.player = player;
            this.mapId = mapId;
            this.minCX = minCX;
            this.maxCX = maxCX;
            this.minCZ = minCZ;
            this.maxCZ = maxCZ;

            // Cameras overlap by half a radius, so a column is covered twice and leftovers shrink.
            int cameras = (maxCZ - minCZ) / RADIUS + 1;
            int width = maxCX - minCX + 1;
            for (int w = 0; w < WALLS; w++) {
                int[] zs = new int[cameras];
                for (int i = 0; i < cameras; i++) {
                    zs[i] = Math.min(maxCZ, minCZ + RADIUS + i * RADIUS);
                }
                walls.add(new Wall(zs, minCX + w * (width / WALLS)));
            }

            for (int cz = minCZ; cz <= maxCZ; cz++) {
                for (int cx = minCX; cx <= maxCX; cx++) {
                    ChunkPos pos = new ChunkPos(cx, cz);
                    if (needsChunk(pos)) {
                        pending.add(pos.toLong());
                    }
                }
            }
        }

        boolean finished() {
            return pending.isEmpty() && drained > DRAIN_TICKS;
        }

        void step() {
            boolean allDone = true;
            for (Wall wall : walls) {
                if (wall.columnX <= maxCX + RADIUS) {
                    allDone = false;
                }
            }

            if (allDone) {
                if (!pending.isEmpty()) {
                    mopUp();
                } else if (drained <= DRAIN_TICKS) {
                    if (drained++ == 0) {
                        releaseAll();
                        CartographyPlus.LOGGER.info("[cp] reveal drain: waiting for unload, loadedChunks={}",
                                level.getChunkSource().getLoadedChunksCount());
                    } else if (drained == DRAIN_TICKS) {
                        CartographyPlus.LOGGER.info("[cp] reveal drain done: loadedChunks={}",
                                level.getChunkSource().getLoadedChunksCount());
                    }
                }
                if (++ticks % 4 == 0) {
                    sync();
                }
                return;
            }

            for (Wall wall : walls) {
                if (wall.columnX > maxCX + RADIUS) {
                    release(wall);
                    continue;
                }
                updateTickets(wall);
                sampleWall(wall);
                if (columnRemaining(wall) == 0) {
                    wall.columnX += 2 * RADIUS;
                    wall.waiting = 0;
                } else if (++wall.waiting > COLUMN_TIMEOUT) {
                    CartographyPlus.LOGGER.warn("[cp] reveal wall at {} timed out", wall.columnX);
                    wall.columnX += 2 * RADIUS;
                    wall.waiting = 0;
                }
            }

            if (++ticks % 4 == 0) {
                sync();
            }
        }

        /** Force-loads every chunk the walls missed; blocking, but bounded per tick. */
        private void mopUp() {
            long deadline = net.minecraft.Util.getMillis() + MOP_UP_MS;
            for (Long key : new ArrayList<>(pending)) {
                if (net.minecraft.Util.getMillis() >= deadline) {
                    break;
                }
                ChunkPos pos = new ChunkPos(key);
                try {
                    LevelChunk chunk = level.getChunk(pos.x, pos.z);
                    if (!chunk.isEmpty() && needsChunk(pos)) {
                        sample(chunk, pos.x, pos.z);
                    }
                    pending.remove(key);
                } catch (Throwable throwable) {
                    pending.remove(key);
                    CartographyPlus.LOGGER.warn("[cp] reveal mop-up skipped {}", pos, throwable);
                }
            }
        }

        private void sampleWall(Wall wall) {
            for (int cz : wall.zs) {
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                        int cx = wall.columnX + dx;
                        int z = cz + dz;
                        long key = ChunkPos.asLong(cx, z);
                        if (!pending.contains(key)) {
                            continue;
                        }
                        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, z);
                        if (chunk == null) {
                            continue;
                        }
                        ChunkPos pos = new ChunkPos(cx, z);
                        if (needsChunk(pos)) {
                            sample(chunk, cx, z);
                        }
                        pending.remove(key);
                    }
                }
            }
        }

        private int columnRemaining(Wall wall) {
            int left = 0;
            for (int cx = wall.columnX - RADIUS; cx <= wall.columnX + RADIUS; cx++) {
                if (cx < minCX - RADIUS || cx > maxCX + RADIUS) {
                    continue;
                }
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    if (pending.contains(ChunkPos.asLong(cx, cz))) {
                        left++;
                    }
                }
            }
            return left;
        }

        private void updateTickets(Wall wall) {
            if (wall.ticketedX == wall.columnX) {
                return;
            }
            release(wall);
            for (int cz : wall.zs) {
                ChunkPos pos = new ChunkPos(wall.columnX, cz);
                level.getChunkSource().addRegionTicket(TicketType.PLAYER, pos, RADIUS, pos);
            }
            wall.ticketedX = wall.columnX;
        }

        private void release(Wall wall) {
            if (wall.ticketedX == Integer.MIN_VALUE) {
                return;
            }
            for (int cz : wall.zs) {
                ChunkPos pos = new ChunkPos(wall.ticketedX, cz);
                level.getChunkSource().removeRegionTicket(TicketType.PLAYER, pos, RADIUS, pos);
            }
            wall.ticketedX = Integer.MIN_VALUE;
        }

        void releaseAll() {
            for (Wall wall : walls) {
                release(wall);
            }
        }

        /** True when this chunk still covers at least one unexplored pixel. */
        private boolean needsChunk(ChunkPos pos) {
            int step = 1 << data.scale;
            int blockMinX = pos.x << 4;
            int blockMinZ = pos.z << 4;
            int px0 = Math.max(0, Math.floorDiv(blockMinX - data.centerX, step) + 64);
            int px1 = Math.min(127, Math.floorDiv(blockMinX + 15 - data.centerX, step) + 64);
            int pz0 = Math.max(0, Math.floorDiv(blockMinZ - data.centerZ, step) + 64);
            int pz1 = Math.min(127, Math.floorDiv(blockMinZ + 15 - data.centerZ, step) + 64);
            for (int px = px0; px <= px1; px++) {
                for (int pz = pz0; pz <= pz1; pz++) {
                    if (data.colors[px + pz * 128] == 0) {
                        return true;
                    }
                }
            }
            return false;
        }

        void sync() {
            if (mapId == null || player.hasDisconnected() || player.connection == null) {
                return;
            }
            net.minecraft.network.protocol.Packet<?> packet = data.getUpdatePacket(mapId, player);
            if (packet != null) {
                player.connection.send(packet);
            }
        }

        /** Drops a chunk that only existed to be sampled, so it is not written to disk. */
        private void discard(LevelChunk chunk) {
            if (chunk.getPersistedStatus() == net.minecraft.world.level.chunk.status.ChunkStatus.FULL) {
                return;
            }
            for (net.minecraft.server.level.ServerPlayer other : level.players()) {
                ChunkPos playerChunk = other.chunkPosition();
                if (Math.abs(playerChunk.x - chunk.getPos().x) <= 4
                        && Math.abs(playerChunk.z - chunk.getPos().z) <= 4) {
                    return;
                }
            }
            chunk.setUnsaved(false);
            discarded++;
        }

        private void sample(LevelChunk chunk, int chunkX, int chunkZ) {
            int step = 1 << data.scale;
            int span = 128 << data.scale;
            int minX = data.centerX - span / 2;
            int minZ = data.centerZ - span / 2;
            int maxX = minX + span - 1;
            int maxZ = minZ + span - 1;

            int blockMinX = chunkX << 4;
            int blockMinZ = chunkZ << 4;
            if (blockMinX + 15 < minX || blockMinX > maxX || blockMinZ + 15 < minZ || blockMinZ > maxZ) {
                return;
            }

            int topY = level.getMaxBuildHeight() - 1;
            int px0 = Math.max(0, Math.floorDiv(blockMinX - data.centerX, step) + 64);
            int px1 = Math.min(127, Math.floorDiv(blockMinX + 15 - data.centerX, step) + 64);
            int pz0 = Math.max(0, Math.floorDiv(blockMinZ - data.centerZ, step) + 64);
            int pz1 = Math.min(127, Math.floorDiv(blockMinZ + 15 - data.centerZ, step) + 64);

            if (DISCARD_GENERATED) {
                discard(chunk);
            }

            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int px = px0; px <= px1; px++) {
                int worldX = data.centerX + (px - 64) * step + step / 2;
                for (int pz = pz0; pz <= pz1; pz++) {
                    int index = px + pz * 128;
                    if (data.colors[index] != 0) {
                        continue;
                    }
                    int worldZ = data.centerZ + (pz - 64) * step + step / 2;
                    int y = topY;
                    BlockState state = chunk.getBlockState(pos.set(worldX, y, worldZ));
                    while (state.getMapColor(level, pos) == MapColor.NONE && y > level.getMinBuildHeight()) {
                        y--;
                        state = chunk.getBlockState(pos.set(worldX, y, worldZ));
                    }
                    MapColor mapColor = state.getMapColor(level, pos);
                    if (mapColor == MapColor.NONE) {
                        continue;
                    }
                    MapColor.Brightness brightness = mapColor == MapColor.WATER
                            ? MapColor.Brightness.NORMAL
                            : ((worldX + worldZ) & 1) == 0 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
                    data.setColor(px, pz, (byte) mapColor.getPackedId(brightness));
                    filled++;
                }
            }
        }
    }
}