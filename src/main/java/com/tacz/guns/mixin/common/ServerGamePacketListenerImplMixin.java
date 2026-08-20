package com.tacz.guns.mixin.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.server.ServerPlayerProtectionHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;
    @Shadow private int aboveGroundTickCount;
    @Shadow private int aboveGroundVehicleTickCount;

    @WrapOperation(method = "handlePlayerCommand", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setSprinting(Z)V"))
    public void cancelSprintCommand(ServerPlayer player, boolean sprint, Operation<Void> original) {
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        original.call(player, gunOperator.getProcessedSprintStatus(sprint));
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void preventFlyKickDuringSync(CallbackInfo ci) {
        if (ServerPlayerProtectionHandler.isProtected(this.player)) {
            this.aboveGroundTickCount = 0;
            this.aboveGroundVehicleTickCount = 0;
            this.player.resetFallDistance();
            this.player.setDeltaMovement(0, 0, 0);
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void freezeMovePlayerDuringSync(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (ServerPlayerProtectionHandler.isProtected(this.player)) {
            this.aboveGroundTickCount = 0;
            this.aboveGroundVehicleTickCount = 0;
            this.player.resetFallDistance();
            this.player.setDeltaMovement(0, 0, 0);
        }
    }
}
