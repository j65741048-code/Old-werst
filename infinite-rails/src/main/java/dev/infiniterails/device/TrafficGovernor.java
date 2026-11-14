package dev.infiniterails.device;

import dev.infiniterails.config.CTraffic;
import dev.infiniterails.config.InfiniteRailsConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Decides how many AI trains may exist right now.
 * The config sets a ceiling (or "automatic"), and the adaptive part walks a live cap up while ticks are cheap
 * and down when they are not, so "infinite" on a PC really means "as many as this machine can carry".
 */
@EventBusSubscriber(modid = dev.infiniterails.InfiniteRails.MOD_ID)
public final class TrafficGovernor {

    private static long tickStart;
    private static double emaMs = 20.0;
    private static int live = -1;
    private static int active;
    private static int sinceAdjust;

    private TrafficGovernor() {
    }

    public static void reportActive(int count) {
        active = count;
    }

    public static double averageMspt() {
        return emaMs;
    }

    /** Number of AI trains allowed alive at this moment. */
    public static int cap() {
        CTraffic cfg = InfiniteRailsConfig.server();
        int setting = cfg.maxAiTrains.get();
        if (setting == 0) {
            return 0;
        }
        boolean mobile = DeviceProfile.kind() == DeviceProfile.Kind.MOBILE;
        int ceiling = setting > 0 ? setting : (mobile ? cfg.mobileTrainCap.get() : Integer.MAX_VALUE);
        if (!cfg.adaptive.get()) {
            return ceiling;
        }
        if (live < 0) {
            live = Math.min(ceiling, mobile ? 2 : 8);
        }
        return Math.min(ceiling, live);
    }

    @SubscribeEvent
    public static void pre(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void post(ServerTickEvent.Post event) {
        double ms = (System.nanoTime() - tickStart) / 1_000_000.0;
        emaMs += (ms - emaMs) * 0.02;
        if (++sinceAdjust < 100) {
            return;
        }
        sinceAdjust = 0;
        CTraffic cfg = InfiniteRailsConfig.server();
        if (!cfg.adaptive.get() || cfg.maxAiTrains.get() == 0) {
            return;
        }
        int target = cfg.targetMspt.get();
        if (live < 0) {
            live = DeviceProfile.kind() == DeviceProfile.Kind.MOBILE ? 2 : 8;
        }
        if (emaMs > target) {
            live = Math.max(0, live - Math.max(1, live / 4));
        } else if (emaMs < target * 0.7 && active >= live) {
            live = live == Integer.MAX_VALUE ? live : live + 1;
        }
    }
}
