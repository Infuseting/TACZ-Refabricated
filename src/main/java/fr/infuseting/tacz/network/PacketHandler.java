package fr.infuseting.tacz.network;

import fr.infuseting.tacz.TaCZMagazines;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class PacketHandler {

    public static final ResourceLocation SELECT_MAGAZINE_ID = new ResourceLocation(TaCZMagazines.MODID, "select_magazine");
    public static final ResourceLocation OPEN_SELECTOR_ID = new ResourceLocation(TaCZMagazines.MODID, "open_selector");
    public static final ResourceLocation UNLOAD_GUN_MAG_ID = new ResourceLocation(TaCZMagazines.MODID, "unload_gun_mag");
    public static final ResourceLocation BULLET_TRANSFER_ID = new ResourceLocation(TaCZMagazines.MODID, "bullet_transfer");
    public static final ResourceLocation LOAD_ONE_FROM_HAND_ID = new ResourceLocation(TaCZMagazines.MODID, "load_one_from_hand");
    public static final ResourceLocation UNLOAD_ONE_FROM_HAND_ID = new ResourceLocation(TaCZMagazines.MODID, "unload_one_from_hand");
    public static final ResourceLocation CHECK_MAGAZINE_ID = new ResourceLocation(TaCZMagazines.MODID, "check_magazine");
    public static final ResourceLocation UNJAM_GUN_ID = new ResourceLocation(TaCZMagazines.MODID, "unjam_gun");
    public static final ResourceLocation TOGGLE_SAFE_ID = new ResourceLocation(TaCZMagazines.MODID, "toggle_safe");

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SELECT_MAGAZINE_ID, (server, player, handler, buf, responseSender) -> {
            SelectMagazinePacket packet = SelectMagazinePacket.decode(buf);
            server.execute(() -> packet.handle(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(OPEN_SELECTOR_ID, (server, player, handler, buf, responseSender) -> {
            OpenSelectorPacket packet = OpenSelectorPacket.decode(buf);
            server.execute(() -> packet.handle(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(UNLOAD_GUN_MAG_ID, (server, player, handler, buf, responseSender) -> {
            UnloadGunMagPacket packet = UnloadGunMagPacket.decode(buf);
            server.execute(() -> packet.handle(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(BULLET_TRANSFER_ID, (server, player, handler, buf, responseSender) -> {
            BulletTransferPacket packet = BulletTransferPacket.decode(buf);
            server.execute(() -> packet.handle(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(LOAD_ONE_FROM_HAND_ID, (server, player, handler, buf, responseSender) -> {
            LoadOneFromHandPacket packet = LoadOneFromHandPacket.decode(buf);
            server.execute(() -> packet.handle(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(UNLOAD_ONE_FROM_HAND_ID, (server, player, handler, buf, responseSender) -> {
            UnloadOneFromHandPacket packet = UnloadOneFromHandPacket.decode(buf);
            server.execute(() -> packet.handle(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(CHECK_MAGAZINE_ID, (server, player, handler, buf, responseSender) -> {
            CheckMagazinePacket packet = CheckMagazinePacket.decode(buf);
            server.execute(() -> packet.handle(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(UNJAM_GUN_ID, (server, player, handler, buf, responseSender) -> {
            UnjamGunPacket packet = UnjamGunPacket.decode(buf);
            server.execute(() -> packet.handle(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_SAFE_ID, (server, player, handler, buf, responseSender) -> {
            ToggleSafePacket packet = ToggleSafePacket.decode(buf);
            server.execute(() -> packet.handle(player));
        });
    }

    @Environment(EnvType.CLIENT)
    public static void sendSelectMagazine(int slot, boolean fastReload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new SelectMagazinePacket(slot, fastReload).encode(buf);
        ClientPlayNetworking.send(SELECT_MAGAZINE_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendSelectMagazine(int slot) {
        sendSelectMagazine(slot, false);
    }

    @Environment(EnvType.CLIENT)
    public static void sendOpenSelector(boolean open) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new OpenSelectorPacket(open).encode(buf);
        ClientPlayNetworking.send(OPEN_SELECTOR_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendUnloadGunMag() {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new UnloadGunMagPacket().encode(buf);
        ClientPlayNetworking.send(UNLOAD_GUN_MAG_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendBulletTransfer(int slot, boolean unload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new BulletTransferPacket(slot, unload).encode(buf);
        ClientPlayNetworking.send(BULLET_TRANSFER_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendLoadOneFromHand() {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new LoadOneFromHandPacket().encode(buf);
        ClientPlayNetworking.send(LOAD_ONE_FROM_HAND_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendUnloadOneFromHand() {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new UnloadOneFromHandPacket().encode(buf);
        ClientPlayNetworking.send(UNLOAD_ONE_FROM_HAND_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendCheckMagazine() {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new CheckMagazinePacket().encode(buf);
        ClientPlayNetworking.send(CHECK_MAGAZINE_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendUnjamGun() {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new UnjamGunPacket().encode(buf);
        ClientPlayNetworking.send(UNJAM_GUN_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendToggleSafe() {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new ToggleSafePacket().encode(buf);
        ClientPlayNetworking.send(TOGGLE_SAFE_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendToServer(Object msg) {
        if (msg instanceof SelectMagazinePacket p) {
            sendSelectMagazine(p.getSlot());
        } else if (msg instanceof OpenSelectorPacket p) {
            sendOpenSelector(p.isOpen());
        } else if (msg instanceof UnloadGunMagPacket) {
            sendUnloadGunMag();
        } else if (msg instanceof BulletTransferPacket p) {
            sendBulletTransfer(p.getInventorySlot(), p.isUnload());
        } else if (msg instanceof LoadOneFromHandPacket) {
            sendLoadOneFromHand();
        } else if (msg instanceof UnloadOneFromHandPacket) {
            sendUnloadOneFromHand();
        } else if (msg instanceof CheckMagazinePacket) {
            sendCheckMagazine();
        } else if (msg instanceof UnjamGunPacket) {
            sendUnjamGun();
        } else if (msg instanceof ToggleSafePacket) {
            sendToggleSafe();
        }
    }
}

