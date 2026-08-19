package fr.infuseting.tacz.mixin;

import fr.infuseting.tacz.client.ClientRecipeSorter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Shadow @Final
    private RecipeManager recipeManager;

    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void onHandleUpdateRecipes(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
        ClientRecipeSorter.onRecipesUpdated(this.recipeManager);
    }
}

