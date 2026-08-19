package fr.infuseting.tacz.mixin;

import fr.infuseting.tacz.client.MagazineSelectorOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {

    @Shadow @Final
    private Minecraft minecraft;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double xoffset, double yoffset, CallbackInfo ci) {
        if (window == this.minecraft.getWindow().getWindow()) {
            if (MagazineSelectorOverlay.isOpen()) {
                double scrollDelta = (this.minecraft.options.discreteMouseScroll().get() ? Math.signum(yoffset) : yoffset) * this.minecraft.options.mouseWheelSensitivity().get();
                if (MagazineSelectorOverlay.onMouseScroll(scrollDelta)) {
                    ci.cancel();
                }
            }
        }
    }
}

