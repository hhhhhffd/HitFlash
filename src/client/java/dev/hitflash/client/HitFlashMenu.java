package dev.hitflash.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Optional integration: Fabric only loads this entrypoint when Mod Menu asks for it. */
public final class HitFlashMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return HitFlashSettingsScreen::new;
    }
}
