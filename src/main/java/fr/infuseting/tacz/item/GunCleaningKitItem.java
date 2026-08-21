package fr.infuseting.tacz.item;

import com.tacz.guns.api.item.IGun;
import fr.infuseting.tacz.durability.GunDurabilityManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class GunCleaningKitItem extends Item {

    public GunCleaningKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack kitStack = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack gunStack = player.getItemInHand(otherHand);

        if (!(gunStack.getItem() instanceof IGun iGun)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.taczmagazines.cleaning_kit_hint"), true);
            }
            return InteractionResultHolder.pass(kitStack);
        }

        if (GunDurabilityManager.cleanGun(gunStack)) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0f, 1.2f);
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.taczmagazines.gun_cleaned"), true);
                if (!player.getAbilities().instabuild) {
                    kitStack.shrink(1);
                }
            }
            return InteractionResultHolder.sidedSuccess(kitStack, level.isClientSide);
        } else {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.taczmagazines.gun_already_clean"), true);
            }
            return InteractionResultHolder.fail(kitStack);
        }
    }
}
