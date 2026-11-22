package com.mcm.cartographyplus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Cartography table with three pages that swap slot behaviour instead of adding slots:
 * <ul>
 *   <li>EXPAND - untouched vanilla: map + paper / glass pane / empty map, vanilla result;</li>
 *   <li>SPLIT - map slot + paper-only slot + result slot; the split happens when the result is taken;</li>
 *   <li>REVEAL - map slot only, plus a button that spends experience and a wide eye.</li>
 * </ul>
 * Slot count and indices never change, so container sync stays intact.
 */
public class SplitCartographyMenu extends CartographyTableMenu {

    public static final int PAGE_EXPAND = 0;
    public static final int PAGE_SPLIT = 1;
    public static final int PAGE_REVEAL = 2;

    public static final int BUTTON_PAGE_EXPAND = 2;
    public static final int BUTTON_PAGE_SPLIT = 3;
    public static final int BUTTON_PAGE_REVEAL = 4;
    public static final int BUTTON_REVEAL = 5;
    public static final int BUTTON_CYCLE_SPLIT = 6;
    public static final int BUTTON_SPLIT_2 = 8;
    public static final int BUTTON_SPLIT_4 = 9;

    private final BlockPos pos;
    private final Level level;
    private final SimpleContainer extras = new SimpleContainer(2);
    private final ExtraSlot boneSlot;
    private final ExtraSlot eyeSlot;
    private int page = PAGE_EXPAND;
    private int splitLevels = 1;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? page : splitLevels;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                page = value;
                CartographyPlus.LOGGER.info("[cp] page synced to {} (side {})", value,
                        level.isClientSide ? "client" : "server");
            } else {
                splitLevels = value;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public SplitCartographyMenu(int containerId, Inventory playerInventory,
                                ContainerLevelAccess access, BlockPos pos, Level level) {
        super(containerId, playerInventory, access);
        this.pos = pos;
        this.level = level;
        this.addDataSlots(this.data);

        // Two extra slots live off-screen on the vanilla pages and only show up on the reveal page.
        // They are added once in the constructor so the slot count never changes at runtime.
        this.boneSlot = (ExtraSlot) this.addSlot(new ExtraSlot(this.extras, 0, 44, 14,
                stack -> stack.is(Items.BONE)));
        this.eyeSlot = (ExtraSlot) this.addSlot(new ExtraSlot(this.extras, 1, 44, 36,
                stack -> stack.is(ModItems.WIDE_EYE.get())));
        this.applyPageLayout();
    }

    @Override
    public MenuType<?> getType() {
        return CartographyPlus.SPLIT_MENU.get();
    }

    public int getPage() {
        return page;
    }

    public int getSplitLevels() {
        return splitLevels;
    }

    public static int splitPaperCost(int levels) {
        return levels;
    }

    /** Bones needed per map scale (0..4); no experience is spent any more. */
    private static final int[] REVEAL_COST = {4, 8, 16, 32, 64};

    public static int revealCost(int scale) {
        return REVEAL_COST[Math.max(0, Math.min(4, scale))];
    }

    public Slot getBoneSlot() {
        return boneSlot;
    }

    public Slot getEyeSlot() {
        return eyeSlot;
    }

    /**
     * Slot positions are final in 1.21.1, so the two input slots are rebuilt for every layout. The
     * slot count never changes, which is what keeps the container sync intact.
     */
    private void applyPageLayout() {
        // Slots 0 and 1 stay exactly where vanilla puts them on every page. Only the two extra
        // slots appear or disappear; moving slots around caused the item and the frame to drift
        // apart because the client and server rebuilt them at different moments.
    }

    /** Slot that only exists on the reveal page; nothing else ever sees it. */
    private final class ExtraSlot extends Slot {
        private final java.util.function.Predicate<ItemStack> filter;

        ExtraSlot(Container container, int index, int x, int y,
                  java.util.function.Predicate<ItemStack> filter) {
            super(container, index, x, y);
            this.filter = filter;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.filter.test(stack);
        }

        @Override
        public boolean isActive() {
            return page == PAGE_REVEAL;
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        // The reveal page has no result slot in the design, so it must not react to clicks either.
        if (this.page == PAGE_REVEAL && slotId == 2) {
            return;
        }
        if (this.page == PAGE_SPLIT && slotId == 2
                && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)
                && !this.slots.get(2).getItem().isEmpty()) {
            if (!player.level().isClientSide) {
                performSplit(player);
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public void slotsChanged(Container container) {
        switch (this.page) {
            case PAGE_SPLIT -> this.updateSplitResult();
            case PAGE_EXPAND -> super.slotsChanged(container);
            default -> this.clearResult();
        }
    }

    private void clearResult() {
        Slot slot = this.slots.get(2);
        if (!slot.getItem().isEmpty()) {
            slot.set(ItemStack.EMPTY);
            slot.setChanged();
            this.broadcastChanges();
        }
    }

    private void updateSplitResult() {
        ItemStack map = this.slots.get(0).getItem();
        ItemStack paper = this.slots.get(1).getItem();
        ItemStack result = ItemStack.EMPTY;
        if (!map.isEmpty() && paper.is(Items.PAPER) && paper.getCount() >= splitPaperCost(splitLevels)) {
            MapItemSavedData mapData = MapItem.getSavedData(map, this.level);
            if (mapData != null && mapData.scale >= splitLevels) {
                result = map.copyWithCount(1);
                result.set(DataComponents.CUSTOM_NAME, Component.translatable(
                        "cartographyplus.result.split", 1 << (splitLevels * 2)));
            }
        }
        Slot slot = this.slots.get(2);
        if (!ItemStack.matches(slot.getItem(), result)) {
            slot.set(result);
            slot.setChanged();
            this.broadcastChanges();
        }
    }

    /** Vanilla take behaviour, copied because the result slot is replaced. */
    private void vanillaTake(Player player, ItemStack result) {
        this.slots.get(0).remove(1);
        this.slots.get(1).remove(1);
        result.getItem().onCraftedBy(result, player.level(), player);
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private void performSplit(Player player) {
        ItemStack map = this.slots.get(0).getItem();
        ItemStack paper = this.slots.get(1).getItem();
        if (map.isEmpty() || !paper.is(Items.PAPER) || paper.getCount() < splitPaperCost(splitLevels)) {
            return;
        }
        if (MapSplitter.split(player, map, splitLevels)) {
            this.slots.get(0).remove(1);
            this.slots.get(1).remove(splitPaperCost(splitLevels));
            if (this.level instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
        this.updateSplitResult();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case BUTTON_PAGE_EXPAND -> {
                this.page = PAGE_EXPAND;
                this.applyPageLayout();
                this.slotsChanged(null);
                this.broadcastChanges();
                return true;
            }
            case BUTTON_PAGE_SPLIT -> {
                this.page = PAGE_SPLIT;
                this.applyPageLayout();
                this.slotsChanged(null);
                this.broadcastChanges();
                return true;
            }
            case BUTTON_PAGE_REVEAL -> {
                this.page = PAGE_REVEAL;
                this.applyPageLayout();
                this.slotsChanged(null);
                this.broadcastChanges();
                return true;
            }
            case BUTTON_SPLIT_2 -> {
                this.splitLevels = 1;
                this.slotsChanged(null);
                this.broadcastChanges();
                return true;
            }
            case BUTTON_SPLIT_4 -> {
                this.splitLevels = 2;
                this.slotsChanged(null);
                this.broadcastChanges();
                return true;
            }
            case BUTTON_CYCLE_SPLIT -> {
                this.splitLevels = this.splitLevels == 1 ? 2 : 1;
                this.slotsChanged(null);
                this.broadcastChanges();
                return true;
            }
            case BUTTON_REVEAL -> {
                return this.reveal(player);
            }
            default -> {
                return false;
            }
        }
    }

    private boolean reveal(Player player) {
        if (this.page != PAGE_REVEAL || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        ItemStack mapStack = this.slots.get(0).getItem();
        MapItemSavedData mapData = MapItem.getSavedData(mapStack, this.level);
        if (mapStack.isEmpty() || mapData == null || !(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        int cost = revealCost(mapData.scale);
        ItemStack bones = this.extras.getItem(0);
        ItemStack eye = this.extras.getItem(1);
        if (!bones.is(Items.BONE) || bones.getCount() < cost) {
            CartographyPlus.LOGGER.info("[cp] reveal aborted: {} bones < {}", bones.getCount(), cost);
            return false;
        }
        if (!eye.is(ModItems.WIDE_EYE.get())) {
            CartographyPlus.LOGGER.info("[cp] reveal aborted: no wide eye in slot");
            return false;
        }

        // Reveal works on an independent copy with its own map id, otherwise every backup of the
        // same map in the inventory would be revealed as well.
        ItemStack revealed = MapSplitter.copyForReveal(serverLevel, mapStack,
                Component.translatable("cartographyplus.revealed_name"));
        MapItemSavedData revealedData = MapItem.getSavedData(revealed, serverLevel);
        MapId revealedId = revealed.get(DataComponents.MAP_ID);
        if (revealed.isEmpty() || revealedData == null || revealedId == null) {
            return false;
        }
        if (!RevealManager.start(serverLevel, revealedData, serverPlayer, revealedId)) {
            return false;
        }
        bones.shrink(cost);
        eye.shrink(1);
        if (mapStack.getCount() == 1) {
            this.slots.get(0).set(revealed);
        } else {
            mapStack.shrink(1);
            if (!serverPlayer.getInventory().add(revealed)) {
                serverPlayer.drop(revealed, false);
            }
        }
        this.broadcastChanges();
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Hand back whatever is left in the two extra slots, otherwise it would vanish.
        for (int i = 0; i < this.extras.getContainerSize(); i++) {
            ItemStack stack = this.extras.removeItemNoUpdate(i);
            if (!stack.isEmpty() && !player.level().isClientSide) {
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
    }
}