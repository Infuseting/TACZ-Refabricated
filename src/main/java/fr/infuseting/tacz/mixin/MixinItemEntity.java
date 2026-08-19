package fr.infuseting.tacz.mixin;

import com.tacz.guns.api.item.IGun;
import fr.infuseting.tacz.capability.GunMagazineCapability;
import fr.infuseting.tacz.item.MagazineItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class MixinItemEntity {

    @Inject(method = "setItem", at = @At("HEAD"))
    private void onSetItem(ItemStack stack, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) return;

        if (stack.getItem() instanceof MagazineItem) {
            MagazineItem.setChecked(stack, false);
        } else if (stack.getItem() instanceof IGun) {
            GunMagazineCapability cap = GunMagazineCapability.of(stack);
            if (cap.hasMagazine()) {
                ItemStack stored = cap.getStoredMagazine();
                MagazineItem.setChecked(stored, false);
                cap.setStoredMagazine(stored);
            }
        }
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void onPlayerTouch(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        ItemStack stack = self.getItem();
        if (stack == null || stack.isEmpty()) return;

        boolean isGunOrMag = stack.getItem() instanceof IGun || stack.getItem() instanceof MagazineItem;
        if (!isGunOrMag) return;

        if (!player.isCrouching()) {
            ci.cancel();
            return;
        }

        Vec3 eyePos = player.getEyePosition();
        Vec3 itemPos = self.position().add(0, self.getBbHeight() / 2.0, 0);
        Vec3 toItem = itemPos.subtract(eyePos);
        double dist = toItem.length();
        if (dist > 4.0) {
            ci.cancel();
            return;
        }

        Vec3 dirToItem = toItem.normalize();
        Vec3 lookVec = player.getLookAngle();
        double dot = lookVec.dot(dirToItem);
        if (dot < 0.85) {
            ci.cancel();
        }
    }
}
