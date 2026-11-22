package com.mcm.cartographyplus;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Cuts one map into {@code n x n} tiles (n = 2^levels) that tile up seamlessly.
 *
 * <p>Each tile is a fresh map with {@code scale - levels} and a centre offset by half a tile.
 * Pixel data is nearest-neighbour sampled out of the source map, so no world access is needed
 * (and no extra detail is invented). Banner/landmark decorations are re-projected onto every tile
 * that covers them.</p>
 */
public final class MapSplitter {

    private static final int SIZE = 128;

    private MapSplitter() {
    }

    /**
     * Makes an independent copy of a map under a brand new map id, so revealing it does not touch
     * the original map (and every backup of it) still sitting in the player's inventory.
     */
    public static ItemStack copyForReveal(Level level, ItemStack source, Component name) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return ItemStack.EMPTY;
        }
        MapItemSavedData data = MapItem.getSavedData(source, serverLevel);
        if (data == null) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = MapItem.create(serverLevel, data.centerX, data.centerZ, data.scale, true, false);
        MapItemSavedData target = MapItem.getSavedData(copy, serverLevel);
        if (target == null) {
            return ItemStack.EMPTY;
        }
        target.colors = data.colors.clone();
        copyMarkers(data, target, serverLevel, collectMarkers(data));
        target.setDirty();
        copy.set(DataComponents.CUSTOM_NAME, name);
        copy.set(DataComponents.LORE, new ItemLore(List.of(describe(source, data))));
        return copy;
    }

    public static boolean split(Player player, ItemStack source, int levels) {
        Level level = player.level();
        if (level.isClientSide || levels < 1 || levels > 2) {
            return false;
        }
        MapItemSavedData data = MapItem.getSavedData(source, level);
        if (data == null || data.scale < levels) {
            return false;
        }

        int n = 1 << levels;
        byte newScale = (byte) (data.scale - levels);
        int sourceSpan = SIZE << data.scale;
        byte[] sourceColors = data.colors;
        if (sourceColors == null) {
            return false;
        }
        int tilePixels = SIZE / n;
        int produced = 0;

        List<MapDecoration> markers = collectMarkers(data);

        for (int col = 0; col < n; col++) {
            for (int row = 0; row < n; row++) {
                int centerX = data.centerX + (int) Math.round(((col + 0.5) / n - 0.5) * sourceSpan);
                int centerZ = data.centerZ + (int) Math.round(((row + 0.5) / n - 0.5) * sourceSpan);

                ItemStack tile = MapItem.create(level, centerX, centerZ, newScale, false, false);
                MapItemSavedData tileData = MapItem.getSavedData(tile, level);
                if (tileData == null) {
                    continue;
                }

                byte[] out = new byte[SIZE * SIZE];
                for (int py = 0; py < SIZE; py++) {
                    int sy = row * tilePixels + py / n;
                    for (int px = 0; px < SIZE; px++) {
                        int sx = col * tilePixels + px / n;
                        out[px + py * SIZE] = sourceColors[sx + sy * SIZE];
                    }
                }
                tileData.colors = out;
                copyMarkers(data, tileData, level, markers);
                tileData.setDirty();
                tile.set(DataComponents.CUSTOM_NAME, Component.literal(
                        "#" + mapIdOf(source) + " " + (col + 1) + "," + (row + 1)));

                if (!player.getInventory().add(tile)) {
                    player.drop(tile, false);
                }
                produced++;
            }
        }
        return produced > 0;
    }

    /**
     * Snapshot of the source map's decorations, minus the live player arrows (tiles are created
     * without position tracking, so a copied arrow would freeze in place).
     */
    static List<MapDecoration> collectMarkers(MapItemSavedData data) {
        List<MapDecoration> markers = new ArrayList<>();
        for (MapDecoration decoration : data.getDecorations()) {
            ResourceKey<MapDecorationType> key = decoration.type().unwrapKey().orElse(null);
            if (key != null && key.location().getPath().startsWith("player")) {
                continue;
            }
            markers.add(decoration);
        }
        return markers;
    }

    /**
     * Decoration coordinates are stored in half-pixel units relative to the map centre, so the world
     * position is {@code center + (coord / 2) * 2^scale}. Feeding that back into
     * {@link MapItemSavedData#addDecoration} lets the target tile clip anything outside its own area.
     */
    static void copyMarkers(MapItemSavedData source, MapItemSavedData tile, Level level,
                                    List<MapDecoration> markers) {
        int index = 0;
        for (MapDecoration decoration : markers) {
            double worldX = source.centerX + (decoration.x() / 2.0D) * (1 << source.scale);
            double worldZ = source.centerZ + (decoration.y() / 2.0D) * (1 << source.scale);
            float rotation = decoration.rot() * 22.5F;
            tile.addDecoration(decoration.type(), level,
                    "cartographyplus_marker_" + index, worldX, worldZ, rotation,
                    decoration.name().orElse(null));
            index++;
        }
    }

    /** "#235" - the id of the map this tile/copy came from, not the freshly allocated one. */
    private static int mapIdOf(ItemStack stack) {
        MapId id = stack.get(DataComponents.MAP_ID);
        return id == null ? -1 : id.id();
    }


    /** Second tooltip line of a revealed copy: original map number plus the area it covers. */
    private static Component describe(ItemStack source, MapItemSavedData data) {
        int edge = SIZE << data.scale;
        return Component.literal("#" + mapIdOf(source) + " \u00b7 " + edge + "x" + edge);
    }
}
