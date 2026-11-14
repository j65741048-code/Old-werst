package dev.infiniterails.world;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RailNetworkPiece extends StructurePiece {

    private static final Map<Key, RailNetworkLayout.Built> CACHE = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, RailNetworkLayout.Built> eldest) {
            return size() > 6;
        }
    };

    private record Key(int cx, int oy, int cz, long seed) {}

    private final int cx;
    private final int oy;
    private final int cz;
    private final long seed;

    public RailNetworkPiece(int cx, int oy, int cz, long seed) {
        super(ModWorldgen.RAIL_NETWORK_PIECE.get(), 0, new BoundingBox(cx - RailNetworkLayout.HALF, -64,
                cz - RailNetworkLayout.HALF, cx + RailNetworkLayout.HALF - 1, oy + 6, cz + RailNetworkLayout.HALF - 1));
        this.cx = cx;
        this.oy = oy;
        this.cz = cz;
        this.seed = seed;
    }

    public RailNetworkPiece(CompoundTag tag) {
        super(ModWorldgen.RAIL_NETWORK_PIECE.get(), tag);
        this.cx = tag.getInt("Cx");
        this.oy = tag.getInt("Oy");
        this.cz = tag.getInt("Cz");
        this.seed = tag.getLong("Seed");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("Cx", cx);
        tag.putInt("Oy", oy);
        tag.putInt("Cz", cz);
        tag.putLong("Seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        RailNetworkLayout.Built built = layoutFor(cx, oy, cz, seed);

        // 1. clear the corridor, 2. lay a deck and pillars, 3. place the rails and furniture
        for (int dcx = -1; dcx <= 1; dcx++) {
            for (int dcz = -1; dcz <= 1; dcz++) {
                long key = new ChunkPos(chunkPos.x + dcx, chunkPos.z + dcz).toLong();
                for (BlockPos cell : built.corridor().getOrDefault(key, List.of())) {
                    clear(level, box, cell);
                }
            }
        }
        for (BlockPos cell : built.corridor().getOrDefault(chunkPos.toLong(), List.of())) {
            support(level, box, cell);
        }
        for (RailNetworkLayout.Placement placement : built.blocks().getOrDefault(chunkPos.toLong(), List.of())) {
            BlockPos at = placement.pos();
            if (!box.isInside(at)) {
                continue;
            }
            BlockState state = placement.state();
            level.setBlock(at, state, 2);
            if (placement.nbt() != null) {
                BlockEntity be = level.getBlockEntity(at);
                if (be != null) {
                    be.loadWithComponents(placement.nbt(), level.registryAccess());
                } else {
                    level.getChunk(at).setBlockEntityNbt(placement.nbt());
                }
            }
            if (state.getBlock() instanceof TrackBlock) {
                if (!state.getValue(TrackBlock.HAS_BE)) {
                    level.getChunk(at).removeBlockEntity(at);
                }
                if (needsTick(at, state)) {
                    level.scheduleTick(at, state.getBlock(), 1);
                }
            }
        }
    }

    private static void clear(WorldGenLevel level, BoundingBox box, BlockPos cell) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 1; dy <= 4; dy++) {
                    BlockPos p = cell.offset(dx, dy, dz);
                    if (box.isInside(p) && !level.getBlockState(p).isAir()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static void support(WorldGenLevel level, BoundingBox box, BlockPos cell) {
        BlockPos deck = cell.below();
        if (box.isInside(deck)) {
            level.setBlock(deck, Blocks.STONE_BRICKS.defaultBlockState(), 2);
        }
        if (((cell.getX() + cell.getZ()) & 7) != 0) {
            return;
        }
        int floor = Math.max(level.getMinBuildHeight(), cell.getY() - 80);
        for (int y = cell.getY() - 2; y >= floor; y--) {
            BlockPos p = new BlockPos(cell.getX(), y, cell.getZ());
            if (!box.isInside(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced()) {
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
            } else {
                break;
            }
        }
    }

    private static boolean needsTick(BlockPos at, BlockState state) {
        int x = at.getX() & 15;
        int z = at.getZ() & 15;
        return state.getValue(TrackBlock.HAS_BE) || state.getValue(TrackBlock.SHAPE) == TrackShape.CR_O
                || x == 0 || x == 15 || z == 0 || z == 15;
    }

    private static RailNetworkLayout.Built layoutFor(int cx, int oy, int cz, long seed) {
        Key key = new Key(cx, oy, cz, seed);
        synchronized (CACHE) {
            RailNetworkLayout.Built cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        RailNetworkLayout.Built built = RailNetworkLayout.build(cx, oy, cz, seed);
        synchronized (CACHE) {
            CACHE.put(key, built);
        }
        return built;
    }
}
