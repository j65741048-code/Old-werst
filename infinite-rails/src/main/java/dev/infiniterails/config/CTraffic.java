package dev.infiniterails.config;

import net.createmod.catnip.config.ConfigBase;

/** Server config, written the way Create writes its own (Catnip ConfigBase), so it shows in Create's config screen style. */
public class CTraffic extends ConfigBase {

    public final ConfigGroup traffic = group(0, "traffic", Comments.traffic);

    public final ConfigInt maxAiTrains = i(-1, -1, Integer.MAX_VALUE, "maxAiTrains",
            Comments.maxAiTrains, Comments.maxAiTrains2, Comments.maxAiTrains3);
    public final ConfigInt deviceClass = i(0, 0, 2, "deviceClass", Comments.deviceClass);
    public final ConfigInt mobileTrainCap = i(12, 0, Integer.MAX_VALUE, "mobileTrainCap", Comments.mobileTrainCap);
    public final ConfigBool adaptive = b(true, "adaptiveLimit", Comments.adaptive);
    public final ConfigInt targetMspt = i(40, 10, 50, "targetMspt", Comments.targetMspt);

    public final ConfigGroup spawning = group(0, "spawning", Comments.spawning);

    public final ConfigInt minSpacing = i(96, 16, 2048, "minTrainSpacing", Comments.minSpacing);
    public final ConfigInt spawnMinDistance = i(64, 16, 512, "spawnMinDistance", Comments.spawnMin);
    public final ConfigInt spawnMaxDistance = i(160, 32, 1024, "spawnMaxDistance", Comments.spawnMax);
    public final ConfigInt despawnDistance = i(288, 64, 4096, "despawnDistance", Comments.despawn);
    public final ConfigInt spawnIntervalTicks = i(40, 5, 1200, "spawnIntervalTicks", Comments.interval);
    public final ConfigInt passengerCars = i(2, 0, 8, "passengerCars", Comments.cars);
    public final ConfigInt stationWaitSeconds = i(10, 1, 300, "stationWaitSeconds", Comments.wait);
    public final ConfigBool villagerConductors = b(true, "villagerConductors", Comments.villagers);

    @Override
    public String getName() {
        return "traffic";
    }

    private static class Comments {
        static String traffic = "AI train traffic on the infinite railway";
        static String maxAiTrains = "Maximum number of AI trains alive at once.";
        static String maxAiTrains2 = "-1 = automatic (device based: mobile uses mobileTrainCap, PC is unlimited but throttled by adaptiveLimit)";
        static String maxAiTrains3 = "0 = no AI trains. Any other number is a hard cap. 2147483647 = infinite.";
        static String deviceClass = "0 = detect automatically, 1 = treat as mobile, 2 = treat as PC";
        static String mobileTrainCap = "Automatic cap on mobile devices (Pojav / FCL / Zalith style launchers)";
        static String adaptive = "Raise or lower the live cap so the server stays near targetMspt.";
        static String targetMspt = "[in milliseconds] Tick time the adaptive limit tries to stay under.";
        static String spawning = "Where and how AI trains appear";
        static String minSpacing = "[in Blocks] Minimum distance between a new train and any other AI train, so traffic is spread out.";
        static String spawn = "";
        static String spawnMin = "[in Blocks] AI trains never spawn closer than this to a player.";
        static String spawnMax = "[in Blocks] AI trains spawn within this distance of a player (keep inside simulation distance).";
        static String despawn = "[in Blocks] AI trains farther than this from every player are removed.";
        static String interval = "[in Ticks] How often the spawner tries to add a train.";
        static String cars = "Passenger cars pulled behind each locomotive.";
        static String wait = "[in Seconds] How long a train waits at each station.";
        static String villagers = "Seat a villager in the cab as the conductor.";
    }
}
