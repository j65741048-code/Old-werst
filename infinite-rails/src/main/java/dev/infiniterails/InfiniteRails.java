package dev.infiniterails;

import dev.infiniterails.client.ClientInit;
import dev.infiniterails.config.InfiniteRailsConfig;
import dev.infiniterails.world.ModWorldgen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(InfiniteRails.MOD_ID)
public final class InfiniteRails {

    public static final String MOD_ID = "infiniterails";
    public static final Logger LOGGER = LogUtils.getLogger();

    public InfiniteRails(IEventBus modBus, ModContainer container) {
        InfiniteRailsConfig.register(container);
        ModWorldgen.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.init(modBus, container);
        }
    }
}
