package dev.infiniterails.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * One 192x192 tile of the endless railway. The structure set spaces tiles exactly 192 blocks apart
 * (random_spread, spacing 12, separation 11 = no random offset), so neighbouring tiles join with no seam.
 */
public final class RailNetworkStructure extends Structure {

    public static final MapCodec<RailNetworkStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Codec.INT.optionalFieldOf("rail_y", 90).forGetter(s -> s.railY)
    ).apply(i, RailNetworkStructure::new));

    private final int railY;

    public RailNetworkStructure(StructureSettings settings, int railY) {
        super(settings);
        this.railY = railY;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int ox = chunk.getMiddleBlockX();
        int oz = chunk.getMiddleBlockZ();
        long seed = context.random().nextLong();
        return Optional.of(new GenerationStub(new BlockPos(ox, railY, oz),
                builder -> builder.addPiece(new RailNetworkPiece(ox, railY, oz, seed))));
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.RAIL_NETWORK.get();
    }
}
