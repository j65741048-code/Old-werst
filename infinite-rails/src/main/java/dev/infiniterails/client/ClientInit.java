package dev.infiniterails.client;

import dev.infiniterails.InfiniteRails;
import dev.infiniterails.config.InfiniteRailsConfig;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Opens the mod's config in Create's own config screen style (same screen Create and Loconautics use). */
public final class ClientInit {

    private ClientInit() {
    }

    public static void init(IEventBus modBus, ModContainer container) {
        BaseConfigScreen.setDefaultActionFor(InfiniteRails.MOD_ID,
                base -> base.withSpecs(null, null, InfiniteRailsConfig.serverSpec()));
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new BaseConfigScreen(parent, InfiniteRails.MOD_ID));
    }
}
