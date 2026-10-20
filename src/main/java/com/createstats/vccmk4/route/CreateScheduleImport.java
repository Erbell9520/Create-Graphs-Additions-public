package com.createstats.vccmk4.route;

import com.createstats.surveyor.core.RouteSchedule;
import com.createstats.surveyor.core.StopCondition;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.filter.FilterItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// read the component directly so addon rows survive
public final class CreateScheduleImport {
    private CreateScheduleImport() {
    }

    public record Read(RouteSchedule schedule, int progress) {
    }

    @Nullable
    public static Read read(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty() || !stack.has(AllDataComponents.TRAIN_SCHEDULE)) return null;
        CompoundTag tag = stack.get(AllDataComponents.TRAIN_SCHEDULE);
        return tag == null ? null : read(tag, registries);
    }

    static Read read(CompoundTag tag, HolderLookup.Provider registries) {
        List<RouteSchedule.Entry> entries = new ArrayList<>();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && entries.size() < RouteSchedule.MAX_ENTRIES; i++) {
            CompoundTag e = list.getCompound(i);
            RouteSchedule.Instruction ins = instruction(e.getCompound("Instruction"));
            List<String> declined = new ArrayList<>();
            List<List<StopCondition>> columns = new ArrayList<>();
            for (Tag col : e.getList("Conditions", Tag.TAG_LIST)) {
                if (!(col instanceof ListTag conds)) continue;
                List<StopCondition> column = new ArrayList<>();
                for (int k = 0; k < conds.size(); k++) {
                    StopCondition c = condition(conds.getCompound(k), registries, declined);
                    if (c != null) column.add(c);
                }

                if (!column.isEmpty()) columns.add(column);
                else if (!conds.isEmpty()) declined.add("a column with nothing left to wait for");
            }
            entries.add(new RouteSchedule.Entry(ins, columns, declined));
        }
        return new Read(new RouteSchedule(entries, tag.getBoolean("Cyclic")), tag.getInt("Progress"));
    }

    private static RouteSchedule.Instruction instruction(CompoundTag ins) {
        String id = ins.getString("Id");
        CompoundTag data = ins.getCompound("Data");
        return switch (id) {
            case "create:destination" -> new RouteSchedule.TravelTo(data.getString("Text"));
            case "create:rename" -> new RouteSchedule.Rename(data.getString("Text"));
            case "create:throttle" -> new RouteSchedule.Throttle(data.contains("Value") ? data.getInt("Value") : 100);
            case "create:package_delivery", "create:package_retrieval" ->
                    new RouteSchedule.Unsupported(id, "a road stop has no postbox for packages");
            default -> new RouteSchedule.Unsupported(id, "not an instruction a road vehicle knows");
        };
    }

    @Nullable
    private static StopCondition condition(CompoundTag c, HolderLookup.Provider registries, List<String> declined) {
        String id = c.getString("Id");
        CompoundTag d = c.getCompound("Data");
        switch (id) {
            case "create:delay":
                return new StopCondition.Wait(seconds(d));
            case "create:idle":
                return new StopCondition.Idle(Math.max(1, seconds(d)));
            case "create:time_of_day":
                return new StopCondition.TimeOfDay(d.getInt("Hour"), d.getInt("Minute"), d.getInt("Rotation"));
            case "create:item_threshold": {
                String item = filterItem(d.getCompound("Item"), registries);
                if (item == null) {
                    declined.add("items matching a Create filter: put the item itself in the slot");
                    return null;
                }
                return new StopCondition.Items(StopCondition.Op.byOrdinal(d.getInt("Operator")), threshold(d), d.getInt("Measure") == 1, item);
            }
            case "create:fluid_threshold": {
                String fluid = bucketFluid(d.getCompound("Bucket"), registries);
                if (fluid == null) {
                    declined.add("fluid matching a Create filter: put a filled bucket in the slot");
                    return null;
                }
                return new StopCondition.Fluid(StopCondition.Op.byOrdinal(d.getInt("Operator")), threshold(d), fluid);
            }
            case "create:redstone_link": {
                ListTag f = d.getList("Frequency", Tag.TAG_COMPOUND);
                String a = f.size() > 0 ? itemId(f.getCompound(0), registries) : "";
                String b = f.size() > 1 ? itemId(f.getCompound(1), registries) : "";

                return new StopCondition.Link(a, b, d.getInt("Inverted") != 1);
            }
            case "create:player_count":

                return new StopCondition.Players(d.getInt("Count"), d.getInt("Exact") != 0);
            case "create:powered":
                return new StopCondition.Powered();
            case "create:unloaded":
                declined.add("chunk unloaded: a vehicle on a route keeps its own chunks loaded");
                return null;
            default:
                declined.add(id + ": not a condition a road vehicle knows");
                return null;
        }
    }

    static int seconds(CompoundTag d) {
        int value = d.getInt("Value");
        int per = switch (d.getInt("TimeUnit")) {
            case 0 -> 1;
            case 2 -> 1200;
            default -> 20;
        };
        return Math.max(0, Math.min(StopCondition.MAX_SECONDS, (value * per + 19) / 20));
    }

    private static int threshold(CompoundTag d) {
        try {
            return Math.max(0, Math.min(StopCondition.MAX_AMOUNT, Integer.parseInt(d.getString("Threshold").trim())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Nullable
    private static String filterItem(CompoundTag stack, HolderLookup.Provider registries) {
        ItemStack st = ItemStack.parseOptional(registries, stack);
        if (st.isEmpty()) return "";
        if (st.getItem() instanceof FilterItem) return null;
        return BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }

    @Nullable
    private static String bucketFluid(CompoundTag stack, HolderLookup.Provider registries) {
        ItemStack st = ItemStack.parseOptional(registries, stack);
        if (st.isEmpty()) return "";
        if (st.getItem() instanceof FilterItem) return null;
        if (st.getItem() instanceof BucketItem b && b.content != Fluids.EMPTY) return BuiltInRegistries.FLUID.getKey(b.content).toString();
        return "";
    }

    private static String itemId(CompoundTag stack, HolderLookup.Provider registries) {
        ItemStack st = ItemStack.parseOptional(registries, stack);
        return st.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }
}
