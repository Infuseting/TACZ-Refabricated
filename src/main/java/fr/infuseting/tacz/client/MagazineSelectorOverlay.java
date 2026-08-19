package fr.infuseting.tacz.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.infuseting.tacz.TaCZMagazines;
import fr.infuseting.tacz.item.AmmoBoxMagazineStorage;
import fr.infuseting.tacz.item.MagazineItem;
import fr.infuseting.tacz.item.MagazineReloadSource;
import com.tacz.guns.api.item.IAmmoBox;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class MagazineSelectorOverlay {

    private static final ResourceLocation MAG_HUD_ATLAS =
            new ResourceLocation(TaCZMagazines.MODID, "textures/gui/magazine_hud.png");

    private static final int ATLAS_W = 550;
    private static final int ATLAS_H = 730;

    private static final int SELECTOR_BG_U = 0;
    private static final int SELECTOR_BG_V = 360;
    private static final int SELECTOR_BG_W = 473;
    private static final int SELECTOR_BG_H = 180;
    private static final int SELECTOR_EXTRA_W = 8;
    private static final int SELECTOR_EXTRA_H = 6;

    private static final int SLOT_NORMAL_U = 0;
    private static final int SLOT_NORMAL_V = 575;
    private static final int SLOT_NORMAL_W = 91;
    private static final int SLOT_NORMAL_H = 91;

    private static final int SLOT_SELECTED_U = 286;
    private static final int SLOT_SELECTED_V = 575;
    private static final int SLOT_SELECTED_W = 91;
    private static final int SLOT_SELECTED_H = 101;

    private static final List<Integer> magazineSlots = new ArrayList<>();
    private static final List<ItemStack> magazineStacks = new ArrayList<>();

    private static int selectedIndex = 0;
    private static boolean open = false;

    // â”€â”€ lifecycle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static void open(LocalPlayer player) {
        magazineSlots.clear();
        magazineStacks.clear();
        selectedIndex = 0;

        ItemStack gun = player.getMainHandItem();

        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof MagazineItem magItem) {
                if (magItem.isAmmoBoxOfGun(gun, stack) && magItem.getAmmoCount(stack) > 0) {
                    magazineSlots.add(i);
                    magazineStacks.add(stack.copy());
                }
            } else if (AmmoBoxMagazineStorage.isExternalAmmoBox(stack)) {
                ItemStack boxedMagazine;
                IAmmoBox box = (IAmmoBox) stack.getItem();
                if (box.isAllTypeCreative(stack)) {
                    boxedMagazine = MagazineReloadSource.createFullMagazineForGun(gun);
                } else {
                    boxedMagazine = AmmoBoxMagazineStorage.peekBestCompatible(stack, gun);
                }
                if (!boxedMagazine.isEmpty()) {
                    magazineSlots.add(i);
                    magazineStacks.add(boxedMagazine);
                }
            }
        }

        open = !magazineSlots.isEmpty();
    }

    public static void close() {
        open = false;
        magazineSlots.clear();
        magazineStacks.clear();
    }

    public static boolean isOpen() {
        return open;
    }

    public static int getSelectedSlot() {
        if (!open || magazineSlots.isEmpty()) return -1;
        return magazineSlots.get(selectedIndex);
    }

    // â”€â”€ input â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static boolean onMouseScroll(double scrollDelta) {
        if (!open || magazineSlots.isEmpty()) return false;
        int delta = scrollDelta > 0 ? -1 : 1;
        selectedIndex = Math.floorMod(selectedIndex + delta, magazineSlots.size());
        return true;
    }

    // â”€â”€ rendering â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static void render(GuiGraphics gfx, float tickDelta) {
        if (!open || magazineStacks.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        float scaleFactor = HudScaleUtil.lockFactor();
        float anchorX = screenW / 2f;
        float anchorY = screenH - 70f;
        gfx.pose().pushPose();
        gfx.pose().translate(anchorX, anchorY, 0f);
        gfx.pose().scale(scaleFactor, scaleFactor, 1f);
        gfx.pose().translate(-anchorX, -anchorY, 0f);

        int count = magazineStacks.size();
        int slotSize = 20;
        int padding = 4;
        int totalWidth = count * slotSize + (count - 1) * padding;
        int startX = (screenW - totalWidth) / 2;
        int y = screenH - 80;

        int bgPad = 6;
        int panelW = Math.min(SELECTOR_BG_W, totalWidth + bgPad * 2 + SELECTOR_EXTRA_W);
        int panelH = Math.min(SELECTOR_BG_H, slotSize + bgPad * 2 + 10 + SELECTOR_EXTRA_H);
        int panelX = (screenW - panelW) / 2;
        int slotVisualCenterY = y - 2 + slotSize / 2;
        int panelY = slotVisualCenterY - panelH / 2;

        renderAtlasPanel(gfx, panelX, panelY, panelW, panelH);

        // Title
        Component title = Component.literal("Select Magazine");
        gfx.drawCenteredString(font, title, screenW / 2, y - bgPad - 2, 0xFFFFFF);

        // Slots
        for (int i = 0; i < count; i++) {
            int x = startX + i * (slotSize + padding);
            boolean selected = (i == selectedIndex);

            renderSlotBackground(gfx, x - 2, y - 2, slotSize, selected);

            ItemStack stack = magazineStacks.get(i);
            gfx.renderItem(stack, x, y);

            if (stack.getItem() instanceof MagazineItem magItem && MagazineItem.isChecked(stack)) {
                String ammoStr = String.valueOf(magItem.getAmmoCount(stack));
                gfx.drawString(font, ammoStr, x + slotSize - font.width(ammoStr) - 1, y + slotSize - 8, selected ? 0xFFFF44 : 0xAAAAAA);
            }
        }

        // Hint
        Component hint = Component.literal("Scroll to select  |  Release R to load");
        gfx.drawCenteredString(font, hint, screenW / 2, y + slotSize + 2, 0xAAAAAA);

        gfx.pose().popPose();
    }

    private static void renderAtlasPanel(GuiGraphics gfx, int x, int y, int w, int h) {
        float targetAspect = (float) w / h;
        float sourceAspect = (float) SELECTOR_BG_W / SELECTOR_BG_H;

        int sourceW = SELECTOR_BG_W;
        int sourceH = SELECTOR_BG_H;
        if (targetAspect > sourceAspect) {
            sourceH = Math.round(SELECTOR_BG_W / targetAspect);
        } else {
            sourceW = Math.round(SELECTOR_BG_H * targetAspect);
        }

        int sourceU = SELECTOR_BG_U + (SELECTOR_BG_W - sourceW) / 2;
        int sourceV = SELECTOR_BG_V + (SELECTOR_BG_H - sourceH) / 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        gfx.blit(MAG_HUD_ATLAS, x, y, w, h,
                (float) sourceU, (float) sourceV, sourceW, sourceH,
                ATLAS_W, ATLAS_H);
        RenderSystem.disableBlend();
    }

    private static void renderSlotBackground(GuiGraphics gfx, int x, int y, int size, boolean selected) {
        int sourceU = selected ? SLOT_SELECTED_U : SLOT_NORMAL_U;
        int sourceV = selected ? SLOT_SELECTED_V : SLOT_NORMAL_V;
        int sourceW = selected ? SLOT_SELECTED_W : SLOT_NORMAL_W;
        int sourceH = selected ? SLOT_SELECTED_H : SLOT_NORMAL_H;
        int drawH = selected ? size + 2 : size;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        gfx.blit(MAG_HUD_ATLAS, x, y, size, drawH,
                (float) sourceU, (float) sourceV, sourceW, sourceH,
                ATLAS_W, ATLAS_H);
        RenderSystem.disableBlend();
    }
}

