package com.mcm.cartographyplus.client;

import com.mcm.cartographyplus.ModItems;
import com.mcm.cartographyplus.SplitCartographyMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class SplitCartographyScreen extends AbstractContainerScreen<SplitCartographyMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/cartography_table.png");
    private static final ResourceLocation MAP_SPRITE =
            ResourceLocation.withDefaultNamespace("container/cartography_table/map");
    private static final ResourceLocation SCALED_MAP_SPRITE =
            ResourceLocation.withDefaultNamespace("container/cartography_table/scaled_map");
    private static final ResourceLocation DUPLICATED_MAP_SPRITE =
            ResourceLocation.withDefaultNamespace("container/cartography_table/duplicated_map");
    private static final ResourceLocation LOCKED_SPRITE =
            ResourceLocation.withDefaultNamespace("container/cartography_table/locked");
    private static final ResourceLocation ERROR_SPRITE =
            ResourceLocation.withDefaultNamespace("container/cartography_table/error");

    /** Bookmark-style page tabs drawn outside the panel, on its right edge. */
    private static final int TAB_X_OFFSET = 176;
    private static final int TAB_Y_OFFSET = 7;
    private static final int TAB_WIDTH = 24;
    private static final int TAB_HEIGHT = 12;
    private static final int TAB_GAP = 0;

    private static final int PREVIEW_LEFT = 71;
    private static final int PREVIEW_TOP = 17;
    private static final int PREVIEW_SIZE = Math.round(128 * 0.45F);

    private static final int CONTROL_X = 16;
    private static final int CONTROL_Y = 58;
    private static final int CONTROL_WIDTH = 46;
    private static final int CONTROL_HEIGHT = 18;

    /** Reveal page layout, taken from the design deck. */
    private static final int REVEAL_MAP_X = 15;
    private static final int REVEAL_MAP_Y = 15;
    private static final int REVEAL_BONE_X = 44;
    private static final int REVEAL_BONE_Y = 14;
    private static final int REVEAL_EYE_X = 44;
    private static final int REVEAL_EYE_Y = 36;
    private static final int REVEAL_BUTTON_X = 16;
    private static final int REVEAL_BUTTON_Y = 58;
    private static final int REVEAL_BUTTON_W = 46;
    private static final int REVEAL_BUTTON_H = 18;
    private static final ResourceLocation BACKGROUND_CLEAN =
            ResourceLocation.fromNamespaceAndPath("cartographyplus", "textures/gui/cartography_clean.png");
    private static final ResourceLocation REVEAL_BUTTON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("cartographyplus", "textures/gui/reveal_button_small.png");

    private static final int COLOR_TAB_ACTIVE = 0xFFC6C6C6;
    private static final int COLOR_TAB_IDLE = 0xFF8B8B8B;
    private static final int COLOR_ON = 0xFF3F6B2E;
    private static final int COLOR_OFF = 0xFF555555;
    private static final int COLOR_BORDER = 0xFF202020;
    private GridTexture grid2;
    private GridTexture grid4;

    public SplitCartographyScreen(SplitCartographyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelY -= 2;
    }

    /** Per-frame cache: the preview asks for these several times per frame. */
    private int frameScale = -1;
    private boolean frameHasEye;

    private int sourceScale() {
        ItemStack stack = this.menu.getSlot(0).getItem();
        if (stack.isEmpty() || Minecraft.getInstance().level == null) {
            return -1;
        }
        MapItemSavedData data = MapItem.getSavedData(stack, Minecraft.getInstance().level);
        return data == null ? -1 : data.scale;
    }

    private boolean hasWideEye() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        for (int i = 0; i < minecraft.player.getInventory().getContainerSize(); i++) {
            if (minecraft.player.getInventory().getItem(i).is(ModItems.WIDE_EYE.get())) {
                return true;
            }
        }
        return false;
    }

    private int revealCost() {
        return SplitCartographyMenu.revealCost(Math.max(0, frameScale));
    }

    private boolean canReveal() {
        if (this.menu.getSlot(0).getItem().isEmpty()) {
            return false;
        }
        ItemStack bones = this.menu.getBoneSlot().getItem();
        ItemStack eye = this.menu.getEyeSlot().getItem();
        return bones.is(Items.BONE) && bones.getCount() >= revealCost() && !eye.isEmpty();
    }

    /** Paints over the vanilla slot frames by sampling a plain patch of the GUI texture. */
    private void coverVanillaSlots(GuiGraphics graphics, int x, int y) {
        cover(graphics, x + 13, y + 13, 22, 22);
        cover(graphics, x + 13, y + 50, 22, 22);
        cover(graphics, x + 142, y + 37, 24, 24);
    }

    private void cover(GuiGraphics graphics, int x, int y, int w, int h) {
        // u/v point at an empty area of the cartography table background, so the patch matches.
        graphics.blit(BACKGROUND, x, y, 106, 150, w, h);
    }

    /** Reveal page: three slots with ghost hints, the required bone count and the reveal button. */
    private void drawRevealPage(GuiGraphics graphics, int x, int y) {
        // Slot.x/y is the item position; the frame sits one pixel up and left of it.
        drawSlotFrame(graphics, x + REVEAL_MAP_X - 1, y + REVEAL_MAP_Y - 1);
        drawSlotFrame(graphics, x + REVEAL_BONE_X - 1, y + REVEAL_BONE_Y - 1);
        drawSlotFrame(graphics, x + REVEAL_EYE_X - 1, y + REVEAL_EYE_Y - 1);

        if (this.menu.getSlot(0).getItem().isEmpty()) {
            drawGhost(graphics, new ItemStack(Items.FILLED_MAP), x + REVEAL_MAP_X, y + REVEAL_MAP_Y);
        }
        ItemStack bones = this.menu.getBoneSlot().getItem();
        if (bones.isEmpty()) {
            drawGhost(graphics, new ItemStack(Items.BONE), x + REVEAL_BONE_X, y + REVEAL_BONE_Y);
            String count = String.valueOf(revealCost());
            graphics.drawString(this.font, count,
                    x + REVEAL_BONE_X + 17 - this.font.width(count), y + REVEAL_BONE_Y + 9,
                    0xFFFFFF, true);
        } else {
            String count = String.valueOf(bones.getCount());
            graphics.drawString(this.font, count,
                    x + REVEAL_BONE_X + 17 - this.font.width(count), y + REVEAL_BONE_Y + 9,
                    0xFFFFFF, true);
        }
        if (this.menu.getEyeSlot().getItem().isEmpty()) {
            drawGhost(graphics, new ItemStack(ModItems.WIDE_EYE.get()), x + REVEAL_EYE_X, y + REVEAL_EYE_Y);
        }
        drawRevealButton(graphics, x, y);
    }

    private void drawSlotFrame(GuiGraphics graphics, int px, int py) {
        graphics.fill(px, py, px + 18, py + 18, 0xFF8B8B8B);
        graphics.fill(px, py, px + 18, py + 1, 0xFF373737);
        graphics.fill(px, py, px + 1, py + 18, 0xFF373737);
        graphics.fill(px, py + 17, px + 18, py + 18, 0xFFFFFFFF);
        graphics.fill(px + 17, py, px + 18, py + 18, 0xFFFFFFFF);
    }

    private void drawGhost(GuiGraphics graphics, ItemStack stack, int px, int py) {
        graphics.pose().pushPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 0.35F);
        graphics.renderItem(stack, px, py);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }

    private void drawRevealButton(GuiGraphics graphics, int x, int y) {
        boolean ready = canReveal();
        int bx = x + REVEAL_BUTTON_X;
        int by = y + REVEAL_BUTTON_Y;
        if (!ready) {
            graphics.setColor(0.55F, 0.55F, 0.55F, 1.0F);
        }
        graphics.blit(REVEAL_BUTTON_TEXTURE, bx, by, 0, 0,
                REVEAL_BUTTON_W, REVEAL_BUTTON_H, REVEAL_BUTTON_W, REVEAL_BUTTON_H);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        Component label = Component.translatable("cartographyplus.button.reveal_short");
        int width = this.font.width(label);
        graphics.drawString(this.font, label, bx + (REVEAL_BUTTON_W - width) / 2, by + 5,
                ready ? 0xF6ECFF : 0x9A9AA6, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Refresh the caches once per frame instead of on every helper call.
        this.frameScale = sourceScale();
        this.frameHasEye = hasWideEye();
        int x = this.leftPos;
        int y = this.topPos;
        int page = this.menu.getPage();
        // The reveal page uses a version of the background with the vanilla slot frames removed.
        graphics.blit(page == SplitCartographyMenu.PAGE_REVEAL ? BACKGROUND_CLEAN : BACKGROUND,
                x, y, 0, 0, this.imageWidth, this.imageHeight);
        if (page == SplitCartographyMenu.PAGE_EXPAND) {
            renderVanillaPreview(graphics);
        } else {
            renderPlainPreview(graphics);
        }

        if (page == SplitCartographyMenu.PAGE_REVEAL) {
            // Drawn before the empty check so the ghost hints show up on an empty page.
            drawRevealPage(graphics, x, y);
            drawTabs(graphics, x, y, page);
            return;
        }
        // The split toggle stays visible even before a map is inserted, otherwise the option
        // looks like it does not exist.
        if (page == SplitCartographyMenu.PAGE_SPLIT) {
            drawSplitChips(graphics, x, y);
        }
        if (page == SplitCartographyMenu.PAGE_EXPAND || page == SplitCartographyMenu.PAGE_SPLIT) {
            if (this.menu.getSlot(0).getItem().isEmpty()) {
                drawGhost(graphics, new ItemStack(Items.FILLED_MAP), x + 15, y + 15);
            }
            if (this.menu.getSlot(1).getItem().isEmpty()) {
                drawGhost(graphics, new ItemStack(Items.PAPER), x + 15, y + 52);
            }
        }
        if (this.menu.getSlot(0).getItem().isEmpty()) {
            drawTabs(graphics, x, y, page);
            return;
        }
        if (page == SplitCartographyMenu.PAGE_EXPAND || page == SplitCartographyMenu.PAGE_SPLIT) {
            if (this.menu.getSlot(0).getItem().isEmpty()) {
                drawGhost(graphics, new ItemStack(Items.FILLED_MAP), x + 15, y + 15);
            }
            if (this.menu.getSlot(1).getItem().isEmpty()) {
                drawGhost(graphics, new ItemStack(Items.PAPER), x + 15, y + 52);
            }
        }
        if (page == SplitCartographyMenu.PAGE_SPLIT && frameScale >= this.menu.getSplitLevels()) {
            drawSplitGrid(graphics, x, y, this.menu.getSplitLevels());
        }
        if (page == SplitCartographyMenu.PAGE_SPLIT) {
            drawSplitChips(graphics, x, y);
        }
        drawTabs(graphics, x, y, page);
    }

    /** Icon shown on each bookmark. */
    private static ItemStack tabIcon(int index) {
        return switch (index) {
            case 1 -> new ItemStack(Items.SHEARS);
            case 2 -> new ItemStack(ModItems.WIDE_EYE.get());
            default -> new ItemStack(Items.FILLED_MAP);
        };
    }

    private void drawTabs(GuiGraphics graphics, int x, int y, int page) {
        for (int i = 0; i < 3; i++) {
            drawTab(graphics, tabX(x, i), tabY(y, i), page == i, tabIcon(i));
        }
    }

    /** Verbatim vanilla rendering: parchment / scaled preview / duplicate preview / lock badge. */
    private void renderVanillaPreview(GuiGraphics graphics) {
        ItemStack extra = this.menu.getSlot(1).getItem();
        boolean paper = extra.is(Items.PAPER);
        boolean emptyMap = extra.is(Items.MAP);
        boolean glass = extra.is(Items.GLASS_PANE);
        ItemStack mapStack = this.menu.getSlot(0).getItem();
        MapId mapId = mapStack.get(DataComponents.MAP_ID);
        MapItemSavedData data = mapId == null ? null : MapItem.getSavedData(mapId, Minecraft.getInstance().level);
        boolean error = false;
        if (data != null) {
            if (data.locked && (paper || glass)) {
                error = true;
                graphics.blitSprite(ERROR_SPRITE, this.leftPos + 35, this.topPos + 31, 28, 21);
            }
            if (paper && data.scale >= 4) {
                error = true;
                graphics.blitSprite(ERROR_SPRITE, this.leftPos + 35, this.topPos + 31, 28, 21);
            }
        }
        int x = this.leftPos;
        int y = this.topPos;
        if (paper && !error) {
            graphics.blitSprite(SCALED_MAP_SPRITE, x + 67, y + 13, 66, 66);
            renderMap(graphics, mapId, data, x + 85, y + 31, 0.226F);
        } else if (emptyMap) {
            graphics.blitSprite(DUPLICATED_MAP_SPRITE, x + 67 + 16, y + 13, 50, 66);
            renderMap(graphics, mapId, data, x + 86, y + 16, 0.34F);
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 1.0F);
            graphics.blitSprite(DUPLICATED_MAP_SPRITE, x + 67, y + 13 + 16, 50, 66);
            renderMap(graphics, mapId, data, x + 70, y + 32, 0.34F);
            graphics.pose().popPose();
        } else if (glass) {
            graphics.blitSprite(MAP_SPRITE, x + 67, y + 13, 66, 66);
            renderMap(graphics, mapId, data, x + 71, y + 17, 0.45F);
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 1.0F);
            graphics.blitSprite(LOCKED_SPRITE, x + 118, y + 60, 10, 14);
            graphics.pose().popPose();
        } else {
            graphics.blitSprite(MAP_SPRITE, x + 67, y + 13, 66, 66);
            renderMap(graphics, mapId, data, x + 71, y + 17, 0.45F);
        }
    }

    /** Plain map preview used by the split / reveal pages. */
    private void renderPlainPreview(GuiGraphics graphics) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blitSprite(MAP_SPRITE, x + 67, y + 13, 66, 66);
        ItemStack stack = this.menu.getSlot(0).getItem();
        if (stack.isEmpty()) {
            return;
        }
        MapId mapId = stack.get(DataComponents.MAP_ID);
        if (mapId == null) {
            return;
        }
        MapItemSavedData data = MapItem.getSavedData(mapId, Minecraft.getInstance().level);
        renderMap(graphics, mapId, data, x + 71, y + 17, 0.45F);
    }

    private void renderMap(GuiGraphics graphics, MapId mapId, MapItemSavedData data,
                           int px, int py, float scale) {
        if (mapId == null || data == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate((float) px, (float) py, 1.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        Minecraft.getInstance().gameRenderer.getMapRenderer()
                .render(graphics.pose(), graphics.bufferSource(), mapId, data, true, 15728880);
        graphics.flush();
        graphics.pose().popPose();
    }

    /** Dashed brown-yellow lines showing where the map will be cut, blitted from a cached texture. */
    private void drawSplitGrid(GuiGraphics graphics, int x, int y, int levels) {
        GridTexture grid;
        if (levels == 1) {
            if (this.grid2 == null) {
                this.grid2 = new GridTexture(2);
            }
            grid = this.grid2;
        } else {
            if (this.grid4 == null) {
                this.grid4 = new GridTexture(4);
            }
            grid = this.grid4;
        }
        // The map preview is rendered at z = +1, so the grid has to go above that or it disappears.
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 2.0F);
        grid.blit(graphics, x + PREVIEW_LEFT, y + PREVIEW_TOP, PREVIEW_SIZE);
        graphics.pose().popPose();
    }

    @Override
    public void removed() {
        super.removed();
        if (this.grid2 != null) {
            this.grid2.close();
            this.grid2 = null;
        }
        if (this.grid4 != null) {
            this.grid4.close();
            this.grid4 = null;
        }
    }

    private static int tabX(int leftPos, int index) {
        return leftPos + TAB_X_OFFSET;
    }

    private static int tabY(int topPos, int index) {
        return topPos + TAB_Y_OFFSET + index * (TAB_HEIGHT + TAB_GAP);
    }

    private static Component tabLabel(int index) {
        return switch (index) {
            case 1 -> Component.translatable("cartographyplus.page.split");
            case 2 -> Component.translatable("cartographyplus.page.reveal");
            default -> Component.translatable("cartographyplus.page.expand");
        };
    }

    private void drawTab(GuiGraphics graphics, int x, int y, boolean active, ItemStack icon) {
        graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT, active ? COLOR_TAB_ACTIVE : COLOR_TAB_IDLE);
        graphics.fill(x, y, x + TAB_WIDTH, y + 1, COLOR_BORDER);
        graphics.fill(x, y + TAB_HEIGHT - 1, x + TAB_WIDTH, y + TAB_HEIGHT, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + TAB_HEIGHT, COLOR_BORDER);
        graphics.fill(x + TAB_WIDTH - 1, y, x + TAB_WIDTH, y + TAB_HEIGHT, COLOR_BORDER);

        graphics.pose().pushPose();
        graphics.pose().translate(x + 4.0F, y + 2.0F, 0.0F);
        graphics.pose().scale(0.625F, 0.625F, 1.0F);
        graphics.renderItem(icon, 0, 0);
        graphics.pose().popPose();
    }

    private static final int CHIP_X = 132;
    private static final int CHIP_W = 14;
    private static final int CHIP_H = 8;
    private static final int CHIP_Y_2 = 15;
    private static final int CHIP_Y_4 = 25;

    /** Two small bookmark chips at the top right corner of the preview: 2x2 and 4x4. */
    private void drawSplitChips(GuiGraphics graphics, int x, int y) {
        boolean hasMap = !this.menu.getSlot(0).getItem().isEmpty();
        drawChip(graphics, x + CHIP_X, y + CHIP_Y_2, "2x2",
                this.menu.getSplitLevels() == 1, hasMap);
        drawChip(graphics, x + CHIP_X, y + CHIP_Y_4, "4x4",
                this.menu.getSplitLevels() == 2, hasMap);
    }

    private void drawChip(GuiGraphics graphics, int px, int py, String label, boolean active, boolean enabled) {
        int fill = !enabled ? 0xFF9A9A9A : (active ? 0xFF5F7A4A : 0xFFB0B0B0);
        graphics.fill(px, py, px + CHIP_W, py + CHIP_H, fill);
        graphics.fill(px + 1, py + 1, px + CHIP_W - 1, py + 2, active ? 0xFF8FA878 : 0xFFC8C8C8);
        graphics.fill(px, py, px + CHIP_W, py + 1, 0xFF6A6A6A);
        graphics.fill(px, py + CHIP_H - 1, px + CHIP_W, py + CHIP_H, 0xFF6A6A6A);
        graphics.fill(px, py, px + 1, py + CHIP_H, 0xFF6A6A6A);
        graphics.fill(px + CHIP_W - 1, py, px + CHIP_W, py + CHIP_H, 0xFF6A6A6A);
        int w = this.font.width(label);
        graphics.drawString(this.font, label, px + (CHIP_W - w) / 2, py + 1,
                active ? 0xF2F2EC : 0x3A3A3A, false);
    }

    private void drawControl(GuiGraphics graphics, int x, int y, Component label, boolean enabled) {
        int bx = x + CONTROL_X;
        int by = y + CONTROL_Y;
        graphics.fill(bx, by, bx + CONTROL_WIDTH, by + CONTROL_HEIGHT, enabled ? COLOR_ON : COLOR_OFF);
        graphics.fill(bx, by, bx + CONTROL_WIDTH, by + 1, COLOR_BORDER);
        graphics.fill(bx, by + CONTROL_HEIGHT - 1, bx + CONTROL_WIDTH, by + CONTROL_HEIGHT, COLOR_BORDER);
        int width = this.font.width(label);
        graphics.drawString(this.font, label, bx + (CONTROL_WIDTH - width) / 2, by + 4, 0xFFFFFF, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The design has no "制图台" / "物品栏" labels.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.leftPos;
        int y = this.topPos;

        for (int i = 0; i < 3; i++) {
            if (inside(mouseX, mouseY, tabX(x, i), tabY(y, i), TAB_WIDTH, TAB_HEIGHT)) {
                click(SplitCartographyMenu.BUTTON_PAGE_EXPAND + i);
                return true;
            }
        }

        int page = this.menu.getPage();
        this.frameScale = sourceScale();
        this.frameHasEye = hasWideEye();
        if (page == SplitCartographyMenu.PAGE_SPLIT) {
            if (inside(mouseX, mouseY, x + CHIP_X, y + CHIP_Y_2, CHIP_W, CHIP_H)) {
                click(SplitCartographyMenu.BUTTON_SPLIT_2);
                return true;
            }
            if (inside(mouseX, mouseY, x + CHIP_X, y + CHIP_Y_4, CHIP_W, CHIP_H)) {
                click(SplitCartographyMenu.BUTTON_SPLIT_4);
                return true;
            }
        }
        if (page == SplitCartographyMenu.PAGE_REVEAL && canReveal()
                && inside(mouseX, mouseY, x + REVEAL_BUTTON_X, y + REVEAL_BUTTON_Y,
                        REVEAL_BUTTON_W, REVEAL_BUTTON_H)) {
            click(SplitCartographyMenu.BUTTON_REVEAL);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void click(int id) {
        if (Minecraft.getInstance().gameMode != null) {
            Minecraft.getInstance().gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }
}