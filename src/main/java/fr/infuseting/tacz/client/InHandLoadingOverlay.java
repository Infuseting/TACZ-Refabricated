package fr.infuseting.tacz.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class InHandLoadingOverlay {

    private static final int   SEGMENTS  = 64;
    private static final float OUTER_R   = 7.0f;
    private static final float INNER_R   = 4.5f;
    private static final int   COLOR_BG  = 0x55AAAAAA; // semi-transparent gray ring
    private static final int   COLOR_FG  = 0xFFFFFFFF; // white progress arc

    public static void render(GuiGraphics guiGraphics, float tickDelta) {
        if (!MagazineLoadingHandler.isInHandActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int selected = mc.player.getInventory().selected;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        float cx = sw / 2f - 80f + selected * 20;
        float cy = sh - 11f;

        float progress = MagazineLoadingHandler.inHandProgress;

        Matrix4f matrix = guiGraphics.pose().last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        // Background ring
        drawArc(matrix, cx, cy, OUTER_R, INNER_R, 0f, Mth.TWO_PI, COLOR_BG);

        // Progress arc â€” grows clockwise from top
        if (progress > 0f) {
            float end = Mth.TWO_PI * Math.min(progress, 1f);
            drawArc(matrix, cx, cy, OUTER_R, INNER_R, -Mth.HALF_PI, -Mth.HALF_PI + end, COLOR_FG);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawArc(Matrix4f matrix, float cx, float cy,
                                 float outerR, float innerR,
                                 float startAngle, float endAngle, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ( argb        & 0xFF) / 255f;

        float span = endAngle - startAngle;
        if (Math.abs(span) < 0.001f) return;

        Tesselator  tess = Tesselator.getInstance();
        BufferBuilder bb = tess.getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i <= SEGMENTS; i++) {
            float t     = (float) i / SEGMENTS;
            float angle = startAngle + t * span;
            float cos   = Mth.cos(angle);
            float sin   = Mth.sin(angle);

            bb.vertex(matrix, cx + outerR * cos, cy + outerR * sin, 0f)
              .color(r, g, b, a).endVertex();
            bb.vertex(matrix, cx + innerR * cos, cy + innerR * sin, 0f)
              .color(r, g, b, a).endVertex();
        }

        tess.end();
    }
}

