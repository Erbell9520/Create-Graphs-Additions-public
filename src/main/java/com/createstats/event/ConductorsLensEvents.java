package com.createstats.event;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.createstats.CreateStats;
import com.createstats.data.ConductorsLensData;
import com.createstats.data.ConductorsLensData.Reading;
import com.createstats.item.ConductorsLensItem;
import com.createstats.train.smart.TrainIntel;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

// carriages are plain entities, interactLivingEntity never sees them
@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class ConductorsLensEvents {
    private ConductorsLensEvents() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ConductorsLensItem)) {
            return;
        }
        Entity target = event.getTarget();
        if (!(target instanceof CarriageContraptionEntity carriageEntity)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        Player player = event.getEntity();
        Carriage carriage = carriageEntity.getCarriage();
        Train train = carriage == null ? null : carriage.train;
        if (train == null) {
            ConductorsLensData.write(stack, ConductorsLensData.Reading.empty());
            player.displayClientMessage(
                Component.translatable("message.create_stats.conductors_lens.no_train")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        Reading r = readTrain(level, train);
        ConductorsLensData.write(stack, r);
        player.displayClientMessage(buildChatSummary(r), true);
    }

    private static Reading readTrain(Level level, Train train) {
        String name = train.name == null ? "Train" : train.name.getString();
        float speedMps = (float) Math.abs(train.speed) * 20.0f;
        float maxSpeedMps;
        try {
            maxSpeedMps = (float) train.maxSpeed() * 20.0f;
        } catch (RuntimeException ignored) {
            maxSpeedMps = 0.0f;
        }
        int carriages = train.carriages == null ? 0 : train.carriages.size();
        String owner = ownerName(level, train.owner);

        String destinationName = "";
        double distanceToDestination = -1.0;
        boolean waitingForSignal = false;
        double distanceToSignal = -1.0;
        Navigation navigation = train.navigation;
        if (navigation != null) {
            distanceToDestination = navigation.distanceToDestination;
            GlobalStation destination = navigation.destination;
            if (destination != null) {
                destinationName = destination.name == null ? "" : destination.name;
            }
            if (navigation.waitingForSignal != null) {
                waitingForSignal = true;
                distanceToSignal = navigation.distanceToSignal;
            }
        }

        int itemCount = 0;
        int fluidAmount = 0;
        Map<String, Integer> items = new HashMap<>();
        Map<String, Integer> fluids = new HashMap<>();
        if (train.carriages != null) {
            try {
                for (Carriage c : train.carriages) {
                    if (c.storage == null) {
                        continue;
                    }
                    IItemHandlerModifiable mounted = c.storage.getMountedItems();
                    for (int slot = 0; slot < mounted.getSlots(); slot++) {
                        ItemStack cargo = mounted.getStackInSlot(slot);
                        if (cargo.isEmpty()) {
                            continue;
                        }
                        itemCount += cargo.getCount();
                        items.merge(cargo.getHoverName().getString(), cargo.getCount(), Integer::sum);
                    }
                    IFluidHandler tanks = c.storage.getFluids();
                    for (int tank = 0; tank < tanks.getTanks(); tank++) {
                        FluidStack fluid = tanks.getFluidInTank(tank);
                        if (fluid.isEmpty()) {
                            continue;
                        }
                        fluidAmount += fluid.getAmount();
                        fluids.merge(fluidName(fluid), fluid.getAmount(), Integer::sum);
                    }
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
        }

        return new Reading(level.getGameTime(), name, speedMps, maxSpeedMps, carriages, owner,
            destinationName, distanceToDestination, waitingForSignal, distanceToSignal,
            train.fuelTicks, itemCount, largest(items), fluidAmount, largest(fluids));
    }

    private static String fluidName(FluidStack fluid) {
        var key = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        return key == null ? "fluid" : key.getPath().replace('_', ' ');
    }

    private static String largest(Map<String, Integer> counts) {
        String best = "";
        int bestAmount = -1;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestAmount) {
                bestAmount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    private static String ownerName(Level level, UUID owner) {
        if (owner == null) {
            return "";
        }
        MinecraftServer server = level.getServer();
        if (server != null) {
            GameProfileCache cache = server.getProfileCache();
            if (cache != null) {
                Optional<GameProfile> profile = cache.get(owner);
                if (profile.isPresent()) {
                    return profile.get().getName();
                }
            }
        }
        return owner.toString().substring(0, 8);
    }

    private static Component buildChatSummary(Reading r) {
        Component name = Component.literal(r.trainName()).withStyle(ChatFormatting.GOLD);
        if (r.waitingForSignal()) {
            return Component.translatable("message.create_stats.conductors_lens.reading_signal",
                name, TrainIntel.distance(r.distanceToSignal()));
        }
        Component speed = Component.literal(String.format("%.1f m/s", r.speedMps()))
            .withStyle(ChatFormatting.AQUA);
        if (!r.hasDestination()) {
            return Component.translatable("message.create_stats.conductors_lens.reading_no_dest",
                name, speed);
        }
        Component destination = Component.literal(r.destinationName()).withStyle(ChatFormatting.YELLOW);
        return Component.translatable("message.create_stats.conductors_lens.reading",
            name, speed, destination, TrainIntel.distance(r.distanceToDestination()));
    }
}
