package dev.infiniterails.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class InfiniteRailsConfig {

    private static CTraffic server;

    private InfiniteRailsConfig() {
    }

    public static CTraffic server() {
        return server;
    }

    public static ModConfigSpec serverSpec() {
        return server.specification;
    }

    public static void register(ModContainer container) {
        Pair<CTraffic, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(builder -> {
            CTraffic config = new CTraffic();
            config.registerAll(builder);
            return config;
        });
        server = pair.getLeft();
        server.specification = pair.getRight();
        container.registerConfig(ModConfig.Type.SERVER, server.specification);
    }
}
