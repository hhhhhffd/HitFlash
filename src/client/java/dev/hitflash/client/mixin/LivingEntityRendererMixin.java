package dev.hitflash.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hitflash.client.FlashRenderState;
import dev.hitflash.client.HitFlashClient;
import dev.hitflash.client.outline.WhiteOutline;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
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
        ((FlashRenderState) state).hitflash$setEffect(HitFlashClient.modelEffectFor(entity));
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
        )
    )
    private void hitflash$submitOutline(LivingEntityRenderState state, PoseStack pose,
                                       SubmitNodeCollector collector, CameraRenderState camera,
                                       CallbackInfo callback) {
        if (state instanceof AvatarRenderState avatar) {
            int color = ((FlashRenderState) state).hitflash$outline();
            if (color != 0) WhiteOutline.submit(avatar, pose, collector, color);
        }
    }

    @ModifyArgs(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
        )
    )
    private void hitflash$bodyOnly(Args args) {
        int overlay = ((FlashRenderState) args.get(1)).hitflash$overlay();
        if (overlay >= 0) args.set(5, overlay);
    }
}
