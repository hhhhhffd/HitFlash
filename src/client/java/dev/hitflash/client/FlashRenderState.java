package dev.hitflash.client;

/** Per-frame visual state attached to the vanilla model render state. */
public interface FlashRenderState {
    int hitflash$overlay();
    int hitflash$outline();
    void hitflash$setEffect(long effect);
}
