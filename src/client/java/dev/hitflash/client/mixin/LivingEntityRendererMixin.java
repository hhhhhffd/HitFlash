package dev.hitflash.client.mixin;

import dev.hitflash.client.FlashRenderState;
import dev.hitflash.client.HitFlashClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void hitflash$extract(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo callback) {
        ((FlashRenderState) state).hitflash$setOutline(HitFlashClient.outlineFor(entity));
    }

    @ModifyArgs(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
        )
    )
    private void hitflash$outlineOnlyTheBaseModel(Args args) {
        // Use the very same mask and post-chain as vanilla Glowing, but don't touch
        // the entity render state's vanilla outlineColor. Feature renderers (armor,
        // shield, held item and cape) continue to get the original vanilla value.
        int customColor = ((FlashRenderState) args.get(1)).hitflash$outline();
        if (customColor != 0 && (int) args.get(8) == 0) args.set(8, customColor);
    }
}
