package fr.infuseting.tacz.durability;

import com.tacz.guns.api.item.IGun;
import fr.infuseting.tacz.config.MechanicsConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class GunDurabilityManager {

    private GunDurabilityManager() {
    }

    public static boolean isEnabled() {
        return MechanicsConfig.ENABLE_DURABILITY.get();
    }

    public static float getDurabilityLossPerShot() {
        return MechanicsConfig.DURABILITY_LOSS_PER_SHOT.get().floatValue();
    }

    public static float getJamProbability(ItemStack gun) {
        if (!isEnabled()) {
            return 0.0f;
        }
        if (!(gun.getItem() instanceof IGun iGun)) {
            return 0.0f;
        }
        float dur = iGun.getDurability(gun);
        float threshold = MechanicsConfig.JAM_SAFETY_THRESHOLD.get().floatValue();
        if (dur >= threshold) {
            return 0.0f;
        }
        float factor = (threshold - dur) / threshold;
        float maxChance = MechanicsConfig.MAX_JAM_CHANCE.get().floatValue();
        return maxChance * factor * factor;
    }

    public static boolean rollJam(LivingEntity shooter, ItemStack gun) {
        if (!isEnabled()) {
            return false;
        }
        if (!(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        if (iGun.isJammed(gun)) {
            return true;
        }
        float prob = getJamProbability(gun);
        if (prob <= 0.0f) {
            return false;
        }
        return shooter.getRandom().nextFloat() < prob;
    }

    public static float getVelocityMultiplier(ItemStack gun) {
        if (!isEnabled()) {
            return 1.0f;
        }
        if (!(gun.getItem() instanceof IGun iGun)) {
            return 1.0f;
        }
        float dur = iGun.getDurability(gun);
        float threshold = MechanicsConfig.JAM_SAFETY_THRESHOLD.get().floatValue();
        if (dur >= threshold) {
            return 1.0f;
        }
        float factor = (threshold - dur) / threshold;
        float maxLoss = MechanicsConfig.STAT_PENALTY_VELOCITY_MAX.get().floatValue();
        return Math.max(0.1f, 1.0f - factor * maxLoss);
    }

    public static float getDamageMultiplier(ItemStack gun) {
        if (!isEnabled()) {
            return 1.0f;
        }
        if (!(gun.getItem() instanceof IGun iGun)) {
            return 1.0f;
        }
        float dur = iGun.getDurability(gun);
        float threshold = MechanicsConfig.JAM_SAFETY_THRESHOLD.get().floatValue();
        if (dur >= threshold) {
            return 1.0f;
        }
        float factor = (threshold - dur) / threshold;
        float maxLoss = MechanicsConfig.STAT_PENALTY_DAMAGE_MAX.get().floatValue();
        return Math.max(0.1f, 1.0f - factor * maxLoss);
    }

    public static float getSpreadMultiplier(ItemStack gun) {
        if (!isEnabled()) {
            return 1.0f;
        }
        if (!(gun.getItem() instanceof IGun iGun)) {
            return 1.0f;
        }
        float dur = iGun.getDurability(gun);
        float threshold = MechanicsConfig.JAM_SAFETY_THRESHOLD.get().floatValue();
        if (dur >= threshold) {
            return 1.0f;
        }
        float factor = (threshold - dur) / threshold;
        float maxSpread = MechanicsConfig.STAT_PENALTY_SPREAD_MAX.get().floatValue();
        return 1.0f + factor * (maxSpread - 1.0f);
    }

    public static int getPiercePenalty(ItemStack gun) {
        if (!isEnabled()) {
            return 0;
        }
        if (!(gun.getItem() instanceof IGun iGun)) {
            return 0;
        }
        float dur = iGun.getDurability(gun);
        if (dur < 30.0f) {
            return 1;
        }
        return 0;
    }

    public static boolean cleanGun(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        float current = iGun.getDurability(gun);
        float maxCap = iGun.getMaxDurability(gun);
        if (current >= maxCap && !iGun.isJammed(gun)) {
            return false;
        }

        float regularLoss = MechanicsConfig.CLEANING_MAX_CAP_LOSS_REGULAR.get().floatValue();
        float heavyLoss = MechanicsConfig.CLEANING_MAX_CAP_LOSS_HEAVY.get().floatValue();
        float restoreFactor = MechanicsConfig.CLEANING_RESTORE_WEAR_FACTOR.get().floatValue();

        float baseCapLoss;
        if (current >= 80.0f) {
            baseCapLoss = regularLoss;
        } else if (current <= 40.0f) {
            baseCapLoss = heavyLoss;
        } else {
            float t = (80.0f - current) / 40.0f;
            baseCapLoss = Mth.lerp(t, regularLoss, heavyLoss);
        }

        float durabilityRestored = Math.max(0.0f, maxCap - current);
        float totalCapLoss = baseCapLoss + (durabilityRestored * restoreFactor);

        float newMaxCap = Math.max(10.0f, maxCap - totalCapLoss);
        iGun.setMaxDurability(gun, newMaxCap);
        iGun.setDurability(gun, newMaxCap);
        iGun.setJammed(gun, false);
        return true;
    }
}
