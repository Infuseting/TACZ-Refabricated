package fr.infuseting.tacz.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class MagazineLoadingOverlay {

    private static final int SEGMENTS   = 64;
    private static final float OUTER_R  = 6.5f;
    private static final float INNER_R  = 4.0f;

    // Colors (ARGB)
    private static final int COLOR_BACKGROUND = 0x55AAAAAA;
    private static final int COLOR_LOAD        = 0xFFFFFFFF;
    private static final int COLOR_UNLOAD      = 0xFFFFFFFF;

    public static void onScreenRender(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
        if (!MagazineLoadingHandler.isActive()) return;

        float progress  = MagazineLoadingHandler.progress;
        boolean unload  = MagazineLoadingHandler.isUnloading();
        int fgColor     = unload ? COLOR_UNLOAD : COLOR_LOAD;

        float cx = (float) mouseX;
        float cy = (float) mouseY;

        Matrix4f matrix = guiGraphics.pose().last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        drawArc(matrix, cx, cy, OUTER_R, INNER_R, 0f, Mth.TWO_PI, COLOR_BACKGROUND);

        if (progress > 0f) {
            float endAngle = Mth.TWO_PI * Math.min(progress, 1f);
            drawArc(matrix, cx, cy, OUTER_R, INNER_R, -Mth.HALF_PI, -Mth.HALF_PI + endAngle, fgColor);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawArc(Matrix4f matrix, float cx, float cy,
                                 float outerR, float innerR,
                                 float startAngle, float endAngle,
                                 int argb) {
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

