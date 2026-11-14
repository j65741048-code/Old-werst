package dev.infiniterails.traffic;

import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.content.schedule.ScheduleEntry;
import com.lycoris.loconautics.content.schedule.condition.ScheduledDelay;
import com.lycoris.loconautics.content.schedule.condition.ScheduleWaitCondition;
import com.lycoris.loconautics.content.schedule.destination.DestinationInstruction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/** Endless schedule: a main-line platform, then a branch-line siding, forever. Station names use the "*" wildcard. */
public final class TrafficSchedules {

    private TrafficSchedules() {
    }

    public static Schedule loop(HolderLookup.Provider registries, int waitSeconds) {
        List<ScheduleEntry> entries = new ArrayList<>();
        entries.add(stop(registries, "Main *", waitSeconds));
        entries.add(stop(registries, "Siding *", waitSeconds));
        return new Schedule(entries, true, 0);
    }

    private static ScheduleEntry stop(HolderLookup.Provider registries, String pattern, int waitSeconds) {
        DestinationInstruction destination = new DestinationInstruction();
        CompoundTag text = new CompoundTag();
        text.putString("Text", pattern);
        destination.setData(registries, text);

        ScheduledDelay delay = new ScheduledDelay();
        CompoundTag wait = new CompoundTag();
        wait.putInt("Value", waitSeconds);
        wait.putInt("TimeUnit", 1); // SECONDS
        delay.setData(registries, wait);

        List<ScheduleWaitCondition> column = new ArrayList<>();
        column.add(delay);
        List<List<ScheduleWaitCondition>> conditions = new ArrayList<>();
        conditions.add(column);
        return new ScheduleEntry(destination, conditions);
    }
}
