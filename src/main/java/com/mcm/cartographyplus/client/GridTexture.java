package com.mcm.cartographyplus.client;

import com.mcm.cartographyplus.CartographyPlus;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The split grid baked into a tiny texture: drawing ~90 quads per frame for a static overlay is
 * pointless, so it is rasterised once per division count and blitted instead.
 */
final class GridTexture {

    private static final int RES = 128;
    /** AABBGGRR: alpha 0xD8, red 0xC8, green 0xA2, blue 0x00. */
    private static final int COLOR = 0xD800A2C8;

    private final ResourceLocation id;

    GridTexture(int divisions) {
        NativeImage image = new NativeImage(RES, RES, true);
        for (int i = 1; i < divisions; i++) {
            int line = RES * i / divisions;
            for (int pos = 0; pos < RES; pos += 8) {
                for (int offset = 0; offset < 5 && pos + offset < RES; offset++) {
                    image.setPixelRGBA(line, pos + offset, COLOR);
                    image.setPixelRGBA(pos + offset, line, COLOR);
                }
            }
        }
        this.id = ResourceLocation.fromNamespaceAndPath(CartographyPlus.MODID, "grid_" + divisions);
        Minecraft.getInstance().getTextureManager().register(this.id, new DynamicTexture(image));
    }

    void blit(GuiGraphics graphics, int x, int y, int size) {
        // Sample the whole 128x128 texture and scale it down; passing "size" as the quad size would
        // only sample the top-left corner of the texture (that was the broken-grid bug).
        float scale = (float) size / (float) RES;
        graphics.pose().pushPose();
        graphics.pose().translate((float) x, (float) y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(this.id, 0, 0, 0, 0, RES, RES, RES, RES);
        graphics.pose().popPose();
    }

    void close() {
        Minecraft.getInstance().getTextureManager().release(this.id);
    }
}
