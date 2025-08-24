package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class ClientMessagePlayerShoot implements FabricPacket {
    public static final PacketType<ClientMessagePlayerShoot> TYPE = PacketType.create(new ResourceLocation(GunMod.MOD_ID, "c2s_player_shoot"), ClientMessagePlayerShoot::new);

    /**
     * 这里的 timestamp 应该是基于 base timestamp 的相对值
     */
    private final long timestamp;

    public ClientMessagePlayerShoot(long timestamp) {
        this.timestamp = timestamp;
    }

    public ClientMessagePlayerShoot(FriendlyByteBuf buf) {
        this(buf.readLong());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeLong(timestamp);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        IGunOperator.fromLivingEntity(player).shoot(player::getXRot, player::getYRot, timestamp);
    }
}
