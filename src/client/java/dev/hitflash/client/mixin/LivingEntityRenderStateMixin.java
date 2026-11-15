package dev.hitflash.client.mixin;

import dev.hitflash.client.FlashRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
abstract class LivingEntityRenderStateMixin implements FlashRenderState {
    @Unique private int hitflash$outline;
    @Override public int hitflash$outline() { return hitflash$outline; }
    @Override public void hitflash$setOutline(int color) { this.hitflash$outline = color; }
}
