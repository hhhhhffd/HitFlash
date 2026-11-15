package dev.hitflash.client.mixin;

import dev.hitflash.client.FlashRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
abstract class LivingEntityRenderStateMixin implements FlashRenderState {
    @Unique private long hitflash$effect = 0xFFFFFFFFL;
    @Override public int hitflash$overlay() { return (int) hitflash$effect; }
    @Override public int hitflash$outline() { return (int) (hitflash$effect >>> 32); }
    @Override public void hitflash$setEffect(long effect) { this.hitflash$effect = effect; }
}
