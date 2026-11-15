package dev.hitflash.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.hitflash.client.FlashPalette;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(OverlayTexture.class)
abstract class OverlayTextureMixin implements FlashPalette {
    @Shadow @Final private DynamicTexture texture;
    @Unique private int hitflash$uploaded = -1;

    @Override
    public void hitflash$ensureColor(int rgb) {
        if (this.hitflash$uploaded == rgb) {
            return;
        }
        NativeImage pixels = this.texture.getPixels();
        if (pixels == null) {
            return;
        }
        // Vanilla uses row 3 for hurt red and row 10 for the white overlay.
        // Row 0 is reserved here and is never selected by vanilla coordinates.
        for (int strengthIndex = 0; strengthIndex < 16; strengthIndex++) {
            int baseWeight = 255 - Math.round(255 * 0.80f * strengthIndex / 15);
            pixels.setPixel(strengthIndex, 0, (baseWeight << 24) | rgb);
        }
        this.texture.upload();
        this.hitflash$uploaded = rgb;
    }
}
