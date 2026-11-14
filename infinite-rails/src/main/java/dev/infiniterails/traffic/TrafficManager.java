package dev.infiniterails.traffic;

import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.rail.RailPick;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.spawn.TrainStructures;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.track.TrackBlock;
import dev.infiniterails.InfiniteRails;
import dev.infiniterails.config.CTraffic;
import dev.infiniterails.config.InfiniteRailsConfig;
import dev.infiniterails.device.TrafficGovernor;
import dev.infiniterails.world.RailNetworkLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps AI traffic alive around players: spawns Loconautics consists on random lane cells of the nearby railway
 * tiles (spread out by a minimum spacing), removes them when nobody is near, and obeys the governor's live cap.
 */
@EventBusSubscriber(modid = InfiniteRails.MOD_ID)
public final class TrafficManager {

    private static final int TILE = RailNetworkLayout.SPAN;
    private static final int TILE_OFFSET = 8; // tile centres are the middle block of chunks 12k
    private static final int LANE = RailNetworkLayout.LANE;

    private static final Map<UUID, Integer> MISSING = new HashMap<>();
    private static int ticker;
    private static int counter;

    private TrafficManager() {
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        CTraffic cfg = InfiniteRailsConfig.server();
        if (cfg == null || ++ticker < cfg.spawnIntervalTicks.get()) {
            return;
        }
        ticker = 0;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            return;
        }
        try {
            run(server, level, cfg);
        } catch (RuntimeException e) {
            InfiniteRails.LOGGER.error("[traffic] cycle failed", e);
        }
    }

    private static void run(MinecraftServer server, ServerLevel level, CTraffic cfg) {
        TrafficData data = level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TrafficData::new, TrafficData::load, null), TrafficData.NAME);
        List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();

        // ---- upkeep: forget vanished trains, remove far ones
        Iterator<Map.Entry<UUID, UUID>> it = data.trains.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            SableTrain train = SableTrainRegistry.get(entry.getKey());
            if (train == null) {
                int missed = MISSING.merge(entry.getKey(), 1, Integer::sum);
                if (missed > 15) {
                    VillagerConductors.dismiss(level, entry.getValue());
                    MISSING.remove(entry.getKey());
                    it.remove();
                    data.setDirty();
                }
                continue;
            }
            MISSING.remove(entry.getKey());
            Vector3dc pos = train.lastKnownPos();
            if (pos != null && nearestPlayerDistance(players, pos.x(), pos.z()) > cfg.despawnDistance.get()) {
                despawn(level, train, entry.getValue());
                it.remove();
                data.setDirty();
            }
        }

        int cap = TrafficGovernor.cap();
        TrafficGovernor.reportActive(data.trains.size());

        if (data.trains.size() > cap) {
            // over the live cap: drop the train farthest from any player
            UUID worst = null;
            double worstDist = -1;
            for (UUID id : data.trains.keySet()) {
                SableTrain train = SableTrainRegistry.get(id);
                Vector3dc pos = train == null ? null : train.lastKnownPos();
                double dist = pos == null ? Double.MAX_VALUE : nearestPlayerDistance(players, pos.x(), pos.z());
                if (dist > worstDist) {
                    worstDist = dist;
                    worst = id;
                }
            }
            if (worst != null) {
                SableTrain train = SableTrainRegistry.get(worst);
                UUID conductor = data.trains.remove(worst);
                if (train != null) {
                    despawn(level, train, conductor);
                }
                data.setDirty();
            }
            return;
        }
        if (data.trains.size() >= cap || players.isEmpty()) {
            return;
        }

        // ---- spawn one train per cycle
        ServerPlayer anchor = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        Spot spot = pickSpot(level, anchor, players, data, cfg);
        if (spot == null) {
            return;
        }
        UUID id = spawn(level, spot, cfg);
        if (id != null) {
            UUID conductor = new UUID(0, 0);
            if (cfg.villagerConductors.get()) {
                UUID seated = VillagerConductors.install(level, id,
                        TrafficSchedules.loop(level.registryAccess(), cfg.stationWaitSeconds.get()));
                if (seated != null) {
                    conductor = seated;
                }
            }
            data.trains.put(id, conductor);
            data.setDirty();
        }
    }

    // ---- spawning --------------------------------------------------------------------------------------------

    private record Spot(BlockPos pos, Vec3 look) {}

    private static Spot pickSpot(ServerLevel level, ServerPlayer anchor, List<ServerPlayer> players, TrafficData data,
                                 CTraffic cfg) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int min = cfg.spawnMinDistance.get();
        int max = Math.max(cfg.spawnMaxDistance.get(), min + 16);
        int spacing = cfg.minSpacing.get();
        List<Vector3dc> others = new ArrayList<>();
        for (UUID id : data.trains.keySet()) {
            SableTrain train = SableTrainRegistry.get(id);
            if (train != null && train.lastKnownPos() != null) {
                others.add(train.lastKnownPos());
            }
        }

        int tileRange = max / TILE + 1;
        int baseX = tileIndex(anchor.getBlockX());
        int baseZ = tileIndex(anchor.getBlockZ());
        for (int attempt = 0; attempt < 24; attempt++) {
            int tx = baseX + rnd.nextInt(-tileRange, tileRange + 1);
            int tz = baseZ + rnd.nextInt(-tileRange, tileRange + 1);
            int cx = tx * TILE + TILE_OFFSET;
            int cz = tz * TILE + TILE_OFFSET;

            boolean alongX = rnd.nextBoolean();
            int side = rnd.nextBoolean() ? 1 : -1;
            int f = alongX ? side : -side;
            int u = (rnd.nextBoolean() ? 1 : -1) * rnd.nextInt(40, 93);
            int x = alongX ? cx + u : cx + side * LANE;
            int z = alongX ? cz + side * LANE : cz + u;

            double toAnchor = Math.hypot(x - anchor.getX(), z - anchor.getZ());
            if (toAnchor < min || toAnchor > max) {
                continue;
            }
            if (nearestPlayerDistance(players, x, z) < min) {
                continue;
            }
            boolean crowded = false;
            for (Vector3dc other : others) {
                if (Math.hypot(other.x() - x, other.z() - z) < spacing) {
                    crowded = true;
                    break;
                }
            }
            if (crowded || level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                continue;
            }
            BlockPos rail = findRail(level, x, z);
            if (rail == null) {
                continue;
            }
            Vec3 look = alongX ? new Vec3(f, 0, 0) : new Vec3(0, 0, f);
            return new Spot(rail, look);
        }
        return null;
    }

    private static UUID spawn(ServerLevel level, Spot spot, CTraffic cfg) {
        List<CompoundTag> consist = new ArrayList<>();
        consist.add(TrainStructures.boilerCart(level));
        for (int i = 0; i < cfg.passengerCars.get(); i++) {
            consist.add(TrainStructures.defaultCart(level));
        }
        TrainStructures.Placement placed = TrainStructures.placeAll(level, consist,
                new RailPick(spot.pos(), false, null), spot.look(), UUID.randomUUID(), "AI Train " + (++counter));
        if (!placed.success()) {
            InfiniteRails.LOGGER.debug("[traffic] spawn at {} failed: {}", spot.pos(), placed.failure());
            return null;
        }
        return placed.trainId();
    }

    private static void despawn(ServerLevel level, SableTrain head, UUID conductor) {
        List<SableTrain> cars = SableTrainSpawner.chainCars(level, head.car().subLevelId());
        if (cars.isEmpty()) {
            cars = List.of(head);
        }
        for (SableTrain car : cars) {
            SableScheduleRuntime.clear(car.id());
            TrainStructures.removeBody(level, car);
        }
        VillagerConductors.dismiss(level, conductor);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private static int tileIndex(int blockCoord) {
        return Math.floorDiv(blockCoord - TILE_OFFSET + TILE / 2, TILE);
    }

    private static BlockPos findRail(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        for (int y = 200; y >= 40; y--) {
            pos.setY(y);
            if (level.getBlockState(pos).getBlock() instanceof TrackBlock) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static double nearestPlayerDistance(List<ServerPlayer> players, double x, double z) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : players) {
            best = Math.min(best, Math.hypot(player.getX() - x, player.getZ() - z));
        }
        return best;
    }
}
