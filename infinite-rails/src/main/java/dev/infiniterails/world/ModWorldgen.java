package dev.infiniterails.world;

import dev.infiniterails.InfiniteRails;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModWorldgen {

    private static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, InfiniteRails.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, InfiniteRails.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<RailNetworkStructure>> RAIL_NETWORK =
            TYPES.register("rail_network", () -> () -> RailNetworkStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> RAIL_NETWORK_PIECE =
            PIECES.register("rail_network", () -> (StructurePieceType.ContextlessType) RailNetworkPiece::new);

    private ModWorldgen() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        PIECES.register(bus);
    }
}
