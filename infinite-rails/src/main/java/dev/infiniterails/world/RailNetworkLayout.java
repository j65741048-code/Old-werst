package dev.infiniterails.world;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackShape;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Builds one tile of the railway, in tile-local terms around a centre (cx, oy, cz).
 *
 * Every tile has the same skeleton, which is what makes the network endless:
 *   - two lanes per axis (x = +-3 and z = +-3), one lane per direction, running straight through the tile edge
 *   - a diamond crossing in each corner of the centre and four right/left turn switches (curves branching off the lanes)
 *   - signals before the junction
 *   - on the outbound half of every lane a branch line: a passing-loop siding with its own station and four signals
 *   - on the inbound half of every lane an optional platform station
 */
public final class RailNetworkLayout {

    public static final int SPAN = 192;
    public static final int HALF = SPAN / 2;

    static final int LANE = 3;
    private static final int RIGHT_TURN = 20;
    private static final int LEFT_TURN = 30;
    private static final int SIGNAL_CENTER = LEFT_TURN + 3;

    // branch line (siding) geometry, measured along the lane
    private static final int SIDING_START = 44;
    private static final int R = 8;
    private static final int SIDING_LEN = 12;
    private static final int PLATFORM_LEN = 9;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static final String[] NAMES = {"Alder", "Birch", "Cedar", "Dunmore", "Elm", "Fenwick", "Garrow", "Hollis",
            "Ironbridge", "Juniper", "Kestrel", "Larch", "Marlow", "Northgate", "Oakley", "Pinehurst", "Quarry",
            "Ravenscar", "Stonefield", "Thornby", "Underhill", "Vale", "Westmere", "Yarrow"};

    public record Placement(BlockPos pos, BlockState state, CompoundTag nbt) {}

    public record Built(Map<Long, List<Placement>> blocks, Map<Long, List<BlockPos>> corridor) {}

    private final int cx;
    private final int oy;
    private final int cz;
    private final RandomSource random;
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
    private final Map<BlockPos, ListTag> connections = new HashMap<>();
    private final Set<BlockPos> corridor = new HashSet<>();

    private RailNetworkLayout(int cx, int oy, int cz, long seed) {
        this.cx = cx;
        this.oy = oy;
        this.cz = cz;
        this.random = RandomSource.create(seed);
    }

    public static Built build(int cx, int oy, int cz, long seed) {
        RailNetworkLayout layout = new RailNetworkLayout(cx, oy, cz, seed);
        layout.lay();
        Map<Long, List<Placement>> out = new HashMap<>();
        layout.blocks.forEach((pos, state) -> out
                .computeIfAbsent(new ChunkPos(pos).toLong(), k -> new ArrayList<>())
                .add(new Placement(pos, state, layout.blockEntities.get(pos))));
        Map<Long, List<BlockPos>> cor = new HashMap<>();
        for (BlockPos pos : layout.corridor) {
            cor.computeIfAbsent(new ChunkPos(pos).toLong(), k -> new ArrayList<>()).add(pos);
        }
        return new Built(out, cor);
    }

    // ---- coordinates -------------------------------------------------------------------------------------------

    private BlockPos at(int dx, int dz) {
        return new BlockPos(cx + dx, oy, cz + dz);
    }

    /** travel direction (+1 / -1 along the world axis) of the lane on this side */
    private static int travel(boolean alongX, int side) {
        return alongX ? side : -side;
    }

    /** a cell of a lane: u is the signed world coordinate along the lane, k the distance outward from the lane */
    private BlockPos cell(boolean alongX, int side, int u, int k) {
        int across = side * (LANE + k);
        return alongX ? at(u, across) : at(across, u);
    }

    private static Vec3 uvec(boolean alongX, int f) {
        return alongX ? new Vec3(f, 0, 0) : new Vec3(0, 0, f);
    }

    private static Vec3 wvec(boolean alongX, int side) {
        return alongX ? new Vec3(0, 0, side) : new Vec3(side, 0, 0);
    }

    // ---- layout ------------------------------------------------------------------------------------------------

    private void lay() {
        for (int d = -HALF; d < HALF; d++) {
            for (int side = -1; side <= 1; side += 2) {
                track(cell(true, side, d, 0), TrackShape.XO);
                track(cell(false, side, d, 0), TrackShape.ZO);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                track(at(sx * LANE, sz * LANE), TrackShape.CR_O);
                curve(at(sx * RIGHT_TURN, sz * LANE), new Vec3(-sx, 0, 0), at(sx * LANE, sz * RIGHT_TURN), new Vec3(0, 0, -sz));
                curve(at(sx * LEFT_TURN, -sz * LANE), new Vec3(-sx, 0, 0), at(-sx * LANE, sz * LEFT_TURN), new Vec3(0, 0, -sz));
            }
        }
        for (int i = 0; i < 4; i++) {
            boolean alongX = (i & 1) == 0;
            int side = (i & 2) == 0 ? 1 : -1;
            int f = travel(alongX, side);
            // before the junction, on the arriving half of the lane
            signal(alongX, side, -f * SIGNAL_CENTER, 4, f);
            layLane(alongX, side, f);
        }
        connections.forEach((pos, list) -> {
            blocks.put(pos, blocks.get(pos).setValue(TrackBlock.HAS_BE, true));
            CompoundTag tag = entityTag(AllBlockEntityTypes.TRACK.get(), pos);
            tag.put("Connections", list);
            blockEntities.put(pos, tag);
        });
    }

    private void layLane(boolean alongX, int side, int f) {
        TrackShape lane = alongX ? TrackShape.XO : TrackShape.ZO;
        TrackShape perp = alongX ? TrackShape.ZO : TrackShape.XO;
        Vec3 u = uvec(alongX, f);
        Vec3 w = wvec(alongX, side);

        // ---- outbound half: the branch line (passing loop with a station)
        int t0 = SIDING_START;
        int tc = t0 + 2 * R;
        int td = tc + SIDING_LEN;
        int tm2 = td + R;
        int te = td + 2 * R;
        BlockPos a = cell(alongX, side, f * t0, 0);
        BlockPos m1 = cell(alongX, side, f * (t0 + R), R);
        BlockPos c = cell(alongX, side, f * tc, 2 * R);
        BlockPos d = cell(alongX, side, f * td, 2 * R);
        BlockPos m2 = cell(alongX, side, f * tm2, R);
        BlockPos e = cell(alongX, side, f * te, 0);
        track(m1, perp);
        track(m2, perp);
        for (int t = tc; t <= td; t++) {
            track(cell(alongX, side, f * t, 2 * R), lane);
        }
        curve(a, u, m1, w.scale(-1));
        curve(m1, w, c, u.scale(-1));
        curve(d, u, m2, w);
        curve(m2, w.scale(-1), e, u.scale(-1));

        signal(alongX, side, f * 38, -4, f);                       // before the switch
        signal(alongX, side, f * (t0 + 2), -4, f);                 // main line, just past the switch
        signal(alongX, side, f * (te - 2), -4, f);                 // main line, just before the merge
        signalOn(cell(alongX, side, f * (tc + 1), 2 * R), cell(alongX, side, f * (tc + 1), 2 * R - 4), f);
        signalOn(cell(alongX, side, f * (td - 1), 2 * R), cell(alongX, side, f * (td - 1), 2 * R - 4), f);

        for (int t = tc; t < tc + PLATFORM_LEN + 3 && t <= td; t++) {
            for (int k = 2 * R + 1; k <= 2 * R + 5; k++) {
                blocks.put(cell(alongX, side, f * t, k).below(), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        station(cell(alongX, side, f * (tc + 6), 2 * R), cell(alongX, side, f * (tc + 6), 2 * R + 4), f, "Siding ");

        // ---- inbound half: signal and (half the time) a platform on the main lane
        signal(alongX, side, -f * 60, -4, f);
        if (random.nextFloat() < 0.5f) {
            int start = 68 + random.nextInt(10);
            for (int t = start; t < start + PLATFORM_LEN; t++) {
                for (int k = 1; k <= 5; k++) {
                    blocks.put(cell(alongX, side, -f * t, k).below(), Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
            int mid = start + PLATFORM_LEN / 2;
            station(cell(alongX, side, -f * mid, 0), cell(alongX, side, -f * mid, 4), f, "Main ");
        }
    }

    // ---- pieces ------------------------------------------------------------------------------------------------

    private void track(BlockPos pos, TrackShape shape) {
        blocks.put(pos, AllBlocks.TRACK.get().defaultBlockState()
                .setValue(TrackBlock.SHAPE, shape)
                .setValue(TrackBlock.HAS_BE, false));
        corridor.add(pos);
    }

    /**
     * A 90 degree curve between two track blocks. dp / dq point from each end block into the curve
     * (same convention Create's own datagen uses).
     */
    private void curve(BlockPos p, Vec3 dp, BlockPos q, Vec3 dq) {
        Vec3 startP = new Vec3(p.getX() + 0.5 + dp.x * 0.5, oy, p.getZ() + 0.5 + dp.z * 0.5);
        Vec3 startQ = new Vec3(q.getX() + 0.5 + dq.x * 0.5, oy, q.getZ() + 0.5 + dq.z * 0.5);
        BezierConnection bezier = new BezierConnection(Couple.create(p, q), Couple.create(startP, startQ),
                Couple.create(dp, dq), Couple.create(UP, UP), true, false, TrackMaterial.ANDESITE);
        connections.computeIfAbsent(p, k -> new ListTag()).add(bezier.write(p));
        connections.computeIfAbsent(q, k -> new ListTag()).add(bezier.secondary().write(q));

        // remember the cells the curve passes through so terrain can be cleared and supported under it
        Vec3 pq = new Vec3(q.getX() - p.getX(), 0, q.getZ() - p.getZ());
        Vec3 n = pq.subtract(dp.scale(pq.dot(dp)));
        double radius = n.length();
        if (radius < 1e-6) {
            return;
        }
        Vec3 normal = n.scale(1.0 / radius);
        Vec3 centre = new Vec3(p.getX() + 0.5, 0, p.getZ() + 0.5).add(normal.scale(radius));
        Vec3 fromCentre = new Vec3(p.getX() + 0.5, 0, p.getZ() + 0.5).subtract(centre);
        for (int i = 0; i <= 48; i++) {
            double th = Math.PI / 2 * i / 48.0;
            Vec3 pt = centre.add(fromCentre.scale(Math.cos(th))).add(dp.scale(radius * Math.sin(th)));
            corridor.add(new BlockPos(net.minecraft.util.Mth.floor(pt.x), oy, net.minecraft.util.Mth.floor(pt.z)));
        }
    }

    /** a signal on the lane at u, k blocks to the side (positive = outward) */
    private void signal(boolean alongX, int side, int u, int k, int travelSign) {
        signalOn(cell(alongX, side, u, 0), cell(alongX, side, u, k), travelSign);
    }

    private void signalOn(BlockPos trackPos, BlockPos sidePos, int travelSign) {
        int dx = Integer.signum(trackPos.getX() - sidePos.getX());
        int dz = Integer.signum(trackPos.getZ() - sidePos.getZ());
        Direction towardTrack = dx != 0 ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        blocks.put(sidePos, AllBlocks.TRACK_SIGNAL.get().defaultBlockState());
        blocks.put(sidePos.above(), AllBlocks.ORANGE_NIXIE_TUBE.get().defaultBlockState()
                .setValue(NixieTubeBlock.FACING, towardTrack));
        blockEntities.put(sidePos,
                targetingTag(AllBlockEntityTypes.TRACK_SIGNAL.get(), sidePos, trackPos, travelSign < 0));
        corridor.add(sidePos);
    }

    private void station(BlockPos trackPos, BlockPos sidePos, int travelSign, String prefix) {
        blocks.put(sidePos, AllBlocks.TRACK_STATION.get().defaultBlockState());
        CompoundTag tag = targetingTag(AllBlockEntityTypes.TRACK_STATION.get(), sidePos, trackPos, travelSign < 0);
        CompoundTag migrate = new CompoundTag();
        migrate.putString("Name", prefix + NAMES[random.nextInt(NAMES.length)] + " " + (1 + random.nextInt(99)));
        tag.put("Migrate", migrate);
        blockEntities.put(sidePos, tag);
        corridor.add(sidePos);
    }

    private CompoundTag targetingTag(BlockEntityType<?> type, BlockPos pos, BlockPos trackPos, boolean positive) {
        CompoundTag tag = entityTag(type, pos);
        tag.putBoolean("TargetDirection", positive);
        tag.put("TargetTrack", NbtUtils.writeBlockPos(trackPos.subtract(pos)));
        tag.putUUID("Id", new UUID(random.nextLong(), random.nextLong()));
        return tag;
    }

    private static CompoundTag entityTag(BlockEntityType<?> type, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type).toString());
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }
}
