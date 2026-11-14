package dev.infiniterails.traffic;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Remembers which trains this mod spawned, so a restart cannot leave untracked trains behind. */
public final class TrafficData extends SavedData {

    public static final String NAME = "infiniterails_traffic";

    /** train id -> conductor villager id */
    public final Map<UUID, UUID> trains = new LinkedHashMap<>();

    public static TrafficData load(CompoundTag tag, HolderLookup.Provider registries) {
        TrafficData data = new TrafficData();
        ListTag list = tag.getList("Trains", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID conductor = entry.hasUUID("Conductor") ? entry.getUUID("Conductor") : new UUID(0, 0);
            data.trains.put(entry.getUUID("Train"), conductor);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        trains.forEach((train, conductor) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Train", train);
            entry.putUUID("Conductor", conductor);
            list.add(entry);
        });
        tag.put("Trains", list);
        return tag;
    }
}
