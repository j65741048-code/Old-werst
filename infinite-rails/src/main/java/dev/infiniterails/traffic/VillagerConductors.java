package dev.infiniterails.traffic;

import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.content.schedule.ScheduleProgressStore;
import com.lycoris.loconautics.content.schedule.ScheduleReadiness;
import com.lycoris.loconautics.network.packets.schedule.ConductorHatPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import dev.infiniterails.InfiniteRails;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Puts a villager in the cab and hands the train its schedule the same way Loconautics does when a player
 * gives a schedule to a seated mob: the mob gets the "TrainHat" tag + the train id, and the schedule runtime is assigned.
 */
public final class VillagerConductors {

    public static final String TAG = "infiniterails_conductor";

    private VillagerConductors() {
    }

    /** @return the conductor's uuid, or null when the train could not be driven */
    @Nullable
    public static UUID install(ServerLevel level, UUID trainId, Schedule schedule) {
        SableTrain train = SableTrainRegistry.get(trainId);
        if (train == null || train.car() == null) {
            return null;
        }
        ServerSubLevel sub = SubLevelData.resolve(level, train.car().subLevelId());
        if (sub == null) {
            return null;
        }
        BlockPos seat = findDriverSeat(level, sub);
        if (seat == null) {
            InfiniteRails.LOGGER.warn("[traffic] no driver seat with a controller in front of it on train {}", trainId);
            return null;
        }

        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return null;
        }
        villager.setNoAi(true);
        villager.setInvulnerable(true);
        villager.setPersistenceRequired();
        villager.setSilent(true);
        villager.setCustomName(Component.translatable("infiniterails.conductor"));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        villager.addTag(TAG);
        villager.moveTo(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, 0, 0);
        level.addFreshEntity(villager);
        SeatBlock.sitDown(level, seat, villager);

        villager.getPersistentData().putBoolean("TrainHat", true);
        villager.getPersistentData().putUUID("AdvTrain", trainId);
        ScheduleProgressStore.get(level.getServer()).putSchedule(trainId, schedule.write(level.registryAccess()));
        SableScheduleRuntime.assign(trainId, schedule);
        PacketDistributor.sendToPlayersTrackingEntity(villager, new ConductorHatPacket(villager.getId(), true));
        return villager.getUUID();
    }

    @Nullable
    private static BlockPos findDriverSeat(ServerLevel level, ServerSubLevel sub) {
        var box = sub.getPlot().getBoundingBox();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).getBlock() instanceof SeatBlock
                            && ScheduleReadiness.controllerAt(sub, pos.immutable()) != null) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }

    public static void dismiss(ServerLevel level, UUID conductor) {
        var entity = level.getEntity(conductor);
        if (entity != null) {
            entity.discard();
        }
    }
}
