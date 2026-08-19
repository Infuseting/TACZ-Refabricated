package fr.infuseting.tacz.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.infuseting.tacz.TaCZMagazines;
import fr.infuseting.tacz.capability.GunMagazineCapability;
import fr.infuseting.tacz.config.MechanicsConfig;
import fr.infuseting.tacz.item.MagazineItem;
import fr.infuseting.tacz.magazine.MagazineFamilySystem;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.util.AttachmentDataUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class MagazineAmmoHudOverlay {

    private static final ResourceLocation MAG_HUD_ATLAS =
            new ResourceLocation(TaCZMagazines.MODID, "textures/gui/magazine_hud.png");

    private static final int ATLAS_W = 550;
    private static final int ATLAS_H = 730;

    // â”€â”€ limits â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final int MAX_RESERVE_SLOTS = 6;

    private static final int MAG_CELL_W = 43;
    private static final int MAG_CELL_H = 45;
    private static final int MAG_ROWS = 4;
    private static final int MAG_CELL_INSET = 1;

    private static final int LOADED_U = 0;
    private static final int LOADED_V = 0;
    private static final int LOADED_FRAMES = 45;

    private static final int RESERVE_U = 0;
    private static final int RESERVE_V = 180;
    private static final int RESERVE_FRAMES = 45;

    // â”€â”€ reserve silhouette dims â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final int RES_W   = 17;
    private static final int RES_H   = 18;
    private static final int RES_GAP = 3;

    // â”€â”€ loaded silhouette dims â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final int LOAD_W = 23;
    private static final int LOAD_H = 24;
    private static final int LOAD_OFFSET_X = 5;
    private static final int LOAD_OFFSET_Y = -5;

    private static final float HUD_SCALE = 1.1f;

    public static void render(GuiGraphics gfx, float tickDelta) {
        if (!MechanicsConfig.OVERRIDE_AMMO_HUD.get()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        ItemStack gun = player.getMainHandItem();
        if (!(gun.getItem() instanceof IGun iGun)) return;

        ResourceLocation gunId = iGun.getGunId(gun);
        CommonGunIndex gunIndex = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (gunIndex == null) return;
        if (!gunIndex.getGunData().getReloadData().getType().equals(FeedType.MAGAZINE)) return;
        if (MagazineFamilySystem.getFamilyForGun(gunId) == null) return;

        int currentAmmo = iGun.getCurrentAmmoCount(gun);
        int maxAmmo     = resolveGunMaxAmmo(gun, gunIndex);

        List<MagEntry> reserves = collectReserves(player, gun);

        int W = mc.getWindow().getGuiScaledWidth();
        int H = mc.getWindow().getGuiScaledHeight();

        int centerY = H - 38;

        int resW = scaled(RES_W);
        int resH = scaled(RES_H);
        int resGap = scaled(RES_GAP);
        int loadW = scaled(LOAD_W);
        int loadH = scaled(LOAD_H);

        int loadedLeft = W - 63 + LOAD_OFFSET_X;
        int loadedTop  = centerY - loadH / 2 + LOAD_OFFSET_Y;

        boolean overflow  = reserves.size() > MAX_RESERVE_SLOTS;
        int displayCount  = Math.min(reserves.size(), MAX_RESERVE_SLOTS);
        int totalReserveW = displayCount * resW + Math.max(0, displayCount - 1) * resGap;
        int reserveRight  = W - 117 - 3;
        int reserveStart  = reserveRight - totalReserveW;
        int reserveTop    = centerY - resH / 2;

        Font font = mc.font;
        float scaleFactor = HudScaleUtil.clampAboveFactor();

        GunMagazineCapability cap = GunMagazineCapability.of(gun);
        boolean hasMag = cap.hasMagazine();
        boolean isLoadedChecked = hasMag && MagazineItem.isGunMagChecked(cap.getStoredMagazine(), currentAmmo);

        // reserve row
        gfx.pose().pushPose();
        gfx.pose().translate(reserveRight, centerY, 0f);
        gfx.pose().scale(scaleFactor, scaleFactor, 1f);
        gfx.pose().translate(-reserveRight, -centerY, 0f);

        for (int i = 0; i < displayCount; i++) {
            MagEntry e = reserves.get(i);
            int x = reserveStart + i * (resW + resGap);
            renderMagazineFrame(gfx, x, reserveTop, resW, resH, e.ammo, e.max, RESERVE_U, RESERVE_V, RESERVE_FRAMES, e.checked, font);
        }

        if (overflow) {
            int extra  = reserves.size() - MAX_RESERVE_SLOTS;
            String lbl = "+" + extra;
            gfx.drawString(font, lbl,
                    reserveStart - font.width(lbl) - 3,
                    reserveTop + (resH - font.lineHeight) / 2,
                    0xFF888888, false);
        }

        gfx.pose().popPose();

        // loaded silhouette
        gfx.pose().pushPose();
        gfx.pose().translate(loadedLeft, centerY, 0f);
        gfx.pose().scale(scaleFactor, scaleFactor, 1f);
        gfx.pose().translate(-loadedLeft, -centerY, 0f);

        if (!hasMag) {
            boolean chambered = iGun.hasBulletInBarrel(gun);
            if (chambered) {
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f); // White tint if bullet in chamber
            } else {
                RenderSystem.setShaderColor(1f, 0.25f, 0.25f, 1f); // Red tint if no bullet in chamber
            }
            renderMagazineFrame(gfx, loadedLeft, loadedTop, loadW, loadH, 0, maxAmmo, LOADED_U, LOADED_V, LOADED_FRAMES, true, font);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            renderMagazineFrame(gfx, loadedLeft, loadedTop, loadW, loadH, currentAmmo, maxAmmo, LOADED_U, LOADED_V, LOADED_FRAMES, isLoadedChecked, font);
        }

        gfx.pose().popPose();
    }

    private static void renderMagazineFrame(
            GuiGraphics gfx, int x, int y, int drawW, int drawH,
            int ammo, int max, int baseU, int baseV, int frames, boolean checked, Font font) {

        int index = indexForAmmo(ammo, max, frames, checked);
        int col = index / MAG_ROWS;
        int row = index % MAG_ROWS;
        int u = baseU + col * MAG_CELL_W + MAG_CELL_INSET;
        int v = baseV + row * MAG_CELL_H + MAG_CELL_INSET;
        int sourceW = MAG_CELL_W - MAG_CELL_INSET * 2;
        int sourceH = MAG_CELL_H - MAG_CELL_INSET * 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        gfx.blit(MAG_HUD_ATLAS, x, y, drawW, drawH,
                (float) u, (float) v, sourceW, sourceH, ATLAS_W, ATLAS_H);
        RenderSystem.disableBlend();
    }

    private static int indexForAmmo(int ammo, int max, int totalFrames, boolean checked) {
        if (!checked) {
            return 0; // Column 0 (Index 0): Unchecked '?' texture
        }
        // Column 1 starts at index 4 (since MAG_ROWS = 4)
        int startCheckedIndex = MAG_ROWS; // 4
        int totalCheckedFrames = 44; // 11 columns * 4 rows

        if (ammo <= 0 || max <= 0) {
            return startCheckedIndex + totalCheckedFrames - 1; // Index 47: Empty
        }
        if (ammo >= max) {
            return startCheckedIndex; // Index 4: 100% Full
        }

        float ratio = Math.min(1.0f, Math.max(0.0f, (float) ammo / max));
        int offset = (int) Math.floor((1.0f - ratio) * (totalCheckedFrames - 1));
        return startCheckedIndex + Math.max(0, Math.min(totalCheckedFrames - 1, offset));
    }

    private static int scaled(int value) {
        return Math.round(value * HUD_SCALE);
    }

    private static int resolveGunMaxAmmo(ItemStack gun, CommonGunIndex index) {
        return AttachmentDataUtils.getAmmoCountWithAttachment(gun, index.getGunData());
    }

    private static List<MagEntry> collectReserves(LocalPlayer player, ItemStack gun) {
        List<MagEntry> result = new ArrayList<>();
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (!(stack.getItem() instanceof MagazineItem magItem)) continue;
            if (!magItem.isAmmoBoxOfGun(gun, stack)) continue;
            boolean checked = MagazineItem.isChecked(stack);
            int ammo = magItem.getAmmoCount(stack);
            int max  = MagazineItem.getMaxCapacity(stack);
            for (int j = 0; j < stack.getCount(); j++) {
                result.add(new MagEntry(ammo, max, checked, i));
            }
        }
        result.sort((a, b) -> {
            if (a.checked != b.checked) {
                return a.checked ? -1 : 1;
            }
            if (a.checked) {
                return Float.compare(b.fillRatio(), a.fillRatio());
            }
            return Integer.compare(a.slot, b.slot);
        });
        return result;
    }

    private record MagEntry(int ammo, int max, boolean checked, int slot) {
        float fillRatio() { return checked && max > 0 ? (float) ammo / max : 0f; }
    }
}

