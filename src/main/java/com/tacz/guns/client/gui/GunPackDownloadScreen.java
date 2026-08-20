package com.tacz.guns.client.gui;

import com.tacz.guns.security.GunPackClientCacheManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

@Environment(EnvType.CLIENT)
public class GunPackDownloadScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(GunPackDownloadScreen.class);

    private static String currentSha256 = "";
    private static int totalExpectedBytes = 0;
    private static int totalChunks = 0;
    private static byte[][] chunkStorage = null;
    private static int receivedChunksCount = 0;
    private static int receivedBytesCount = 0;
    private static String currentStage = "Initialisation...";
    private static boolean isActive = false;

    public GunPackDownloadScreen() {
        super(GameNarrator.NO_TITLE);
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        this.addRenderableWidget(
                Button.builder(Component.literal("Annuler & Déconnexion"), b -> {
                    close();
                    if (this.minecraft != null && this.minecraft.level != null) {
                        this.minecraft.level.disconnect();
                    }
                }).bounds(centerX - 80, centerY + 38, 160, 20).build()
        );
    }

    public static synchronized void open(String sha256, int totalBytes, int chunks) {
        currentSha256 = sha256;
        totalExpectedBytes = totalBytes;
        totalChunks = chunks;
        chunkStorage = new byte[chunks][];
        receivedChunksCount = 0;
        receivedBytesCount = 0;
        currentStage = "Connexion et négociation...";
        isActive = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || !(mc.screen instanceof GunPackDownloadScreen)) {
            mc.setScreen(new GunPackDownloadScreen());
        }
    }

    public static synchronized void onChunkReceived(int chunkIndex, int chunks, byte[] data) {
        if (!isActive || chunkStorage == null || chunkIndex < 0 || chunkIndex >= chunkStorage.length) {
            return;
        }

        if (chunkStorage[chunkIndex] == null) {
            chunkStorage[chunkIndex] = data;
            receivedChunksCount++;
            receivedBytesCount += data.length;
        }

        int percent = totalChunks > 0 ? (receivedChunksCount * 100) / totalChunks : 0;
        currentStage = String.format("Téléchargement : %d%% (%d / %d Ko)", percent, receivedBytesCount / 1024, totalExpectedBytes / 1024);

        if (receivedChunksCount >= totalChunks) {
            currentStage = "Finalisation et enregistrement du cache...";
            finalizeDownload();
        }
    }

    private static void finalizeDownload() {
        try {
            GunPackClientCacheManager.saveEncryptedChunks(currentSha256, chunkStorage);
            chunkStorage = null; // Free chunk memory immediately
            currentStage = "Déchiffrement et chargement des armes en mémoire...";
            LOGGER.info("[GunPackDownload] All {} chunks assembled and saved to encrypted cache.", totalChunks);
        } catch (IOException e) {
            LOGGER.error("[GunPackDownload] Error assembling encrypted pack chunks", e);
            currentStage = "Erreur lors de l'assemblage du pack.";
        }
    }

    public static synchronized void close() {
        isActive = false;
        chunkStorage = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GunPackDownloadScreen) {
            mc.setScreen(null);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Prevent accidental cancellation while downloading critical weapon assets
        return false;
    }

    @Override
    public void render(@NotNull GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Title
        Component titleText = Component.literal("Synchronisation des armes (TACZ)");
        gui.drawCenteredString(this.font, titleText, centerX, centerY - 50, 0xFFFFFF);

        // Subtitle / Status
        Component statusText = Component.literal(currentStage);
        gui.drawCenteredString(this.font, statusText, centerX, centerY - 25, 0xAAAAAA);

        // Progress Bar
        int barWidth = 220;
        int barHeight = 12;
        int barX = centerX - barWidth / 2;
        int barY = centerY + 5;

        // Bar background border
        gui.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF000000);
        gui.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

        // Filled progress
        float progress = totalChunks > 0 ? (float) receivedChunksCount / (float) totalChunks : 0f;
        progress = Math.min(Math.max(progress, 0f), 1f);
        int filledWidth = (int) (barWidth * progress);

        if (filledWidth > 0) {
            // Green/Cyan gradient or solid green vanilla bar
            gui.fill(barX, barY, barX + filledWidth, barY + barHeight, 0xFF4CAF50);
        }

        // Percentage text below bar
        int percent = (int) (progress * 100);
        Component percentText = Component.literal(percent + " %");
        gui.drawCenteredString(this.font, percentText, centerX, barY + barHeight + 8, 0xDDDDDD);

        super.render(gui, mouseX, mouseY, partialTick);
    }
}
