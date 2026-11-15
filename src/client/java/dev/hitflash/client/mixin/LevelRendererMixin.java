package dev.hitflash.client.mixin;

import dev.hitflash.client.FlashRenderState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow protected abstract boolean shouldShowEntityOutlines();

    @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
    private void hitflash$scheduleVanillaPostChain(Camera camera, Frustum frustum, DeltaTracker ticks,
                                                  LevelRenderState world, CallbackInfo callback) {
        if (!world.haveGlowingEntities && shouldShowEntityOutlines()) {
            for (var entityState : world.entityRenderStates) {
                if (entityState instanceof FlashRenderState flash && flash.hitflash$outline() != 0) {
                    world.haveGlowingEntities = true;
                    return;
                }
            }
        }
    }
}
