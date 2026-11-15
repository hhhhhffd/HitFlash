package dev.hitflash.client.mixin;

import dev.hitflash.client.HitFlashClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void hitflash$confirmedDamage(ClientboundDamageEventPacket packet, CallbackInfo callback) {
        // Vanilla's ensureRunningOnSameThread has already run at this point.
        HitFlashClient.onDamage(packet);
    }
}
