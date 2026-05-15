package fr.zeffut.mcwrapped.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import fr.zeffut.mcwrapped.config.ui.McWrappedConfigScreen;

/**
 * Registers the Wrapped config screen with ModMenu's mods list. ModMenu discovers this entrypoint
 * via the {@code modmenu} entry in {@code fabric.mod.json}.
 */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new McWrappedConfigScreen(parent);
    }
}
