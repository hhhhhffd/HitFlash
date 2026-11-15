package dev.hitflash.client.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface OutlineRenderTypeInvoker {
    @Invoker("create")
    static RenderType hitflash$create(String name, RenderSetup setup) {
        throw new AssertionError("RenderType mixin was not applied");
    }
}
