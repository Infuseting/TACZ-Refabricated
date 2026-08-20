package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.gui.GunPackDownloadScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ServerMessageGunPackChunk implements FabricPacket {
    public static final PacketType<ServerMessageGunPackChunk> TYPE = PacketType.create(
            new ResourceLocation(GunMod.MOD_ID, "s2c_gunpack_chunk"),
            ServerMessageGunPackChunk::new
    );

    private final int chunkIndex;
    private final int totalChunks;
    private final byte[] data;

    public ServerMessageGunPackChunk(int chunkIndex, int totalChunks, byte[] data) {
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.data = data;
    }

    public ServerMessageGunPackChunk(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readByteArray());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(totalChunks);
        buf.writeByteArray(data);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            GunPackDownloadScreen.onChunkReceived(chunkIndex, totalChunks, data);
        });
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public byte[] getData() {
        return data;
    }
}
