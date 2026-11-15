package dev.hitflash.client;

/** The hit color is kept separate from the vanilla entity-wide outline flag. */
public interface FlashRenderState {
    int hitflash$outline();
    void hitflash$setOutline(int color);
}
