package com.createstats.blockentity;

import com.createstats.block.ContactorBlock;
import com.createstats.contactor.ContactorLogic;
import com.createstats.contactor.ContactorMotion;
import com.createstats.contactor.ContactorState;
import com.createstats.power.FeFlowGraph;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeRates;
import com.createstats.power.FeStudPanel;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.WireTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// walked through, never into. a switch in each channel
public class ContactorBE extends BlockEntity implements FeStudPanel,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation,
        com.createstats.util.CopyableSettings {
    public record StudLink(BlockPos to, WireTier tier) {
    }

    public static final int MAX_LINKS_PER_STUD = 1;

    private static final int PRUNE_TICKS = 40;

    private final List<List<StudLink>> studLinks = new ArrayList<>(ContactorLogic.STUDS);

    private final ContactorState contactor = new ContactorState();

    private final int[] carried = new int[ContactorLogic.POLES];

    private final int[] load = new int[ContactorLogic.POLES];

    @Nullable
    private ContactorMotion.Unit motion;

    public ContactorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        for (int stud = 0; stud < ContactorLogic.STUDS; stud++) {
            studLinks.add(new ArrayList<>(MAX_LINKS_PER_STUD));
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ContactorBE be) {
        be.tickServer(level, state);
    }

    private void tickServer(Level level, BlockState state) {
        if (!(state.getBlock() instanceof ContactorBlock block)) {
            return;
        }
        BlockState now = state;
        if (contactor.takeFirstTick()) {
            block.followCoils(level, worldPosition, state);
            now = level.getBlockState(worldPosition);
            if (!(now.getBlock() instanceof ContactorBlock)) {
                return;
            }
        }
        final long tick = level.getGameTime();
        if ((tick + Math.floorMod(worldPosition.hashCode(), PRUNE_TICKS)) % PRUNE_TICKS == 0) {
            pruneStudLinks();
        }
        measure(level, now, tick);
        final boolean[] closed = ContactorBlock.poles(now);
        final int[] rating = new int[ContactorLogic.POLES];
        for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
            rating[pole] = poleRating(pole);
        }
        if (contactor.moveComparator(ContactorLogic.comparatorLevel(closed, carried, rating))) {
            level.updateNeighbourForOutputSignal(worldPosition, block);
        }
        syncLoadsIfDue(level, tick);
    }

    private void measure(Level level, BlockState state, long tick) {
        for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
            carried[pole] = ContactorBlock.poleClosed(state, pole) ? poleFlow(level, pole, tick) : 0;
        }
    }

    private int poleFlow(Level level, int pole, long tick) {
        final FeGridMember a = memberOn(level, pole);
        final FeGridMember b = memberOn(level, pole + ContactorLogic.POLES);
        if (a == null || b == null) {
            return 0;
        }
        final FeWireNetwork grid = a.network();
        if (grid == null) {
            return 0;
        }
        final int edge = grid.flowBetween(a.nodeKey(), b.nodeKey(), tick);
        if (edge != FeFlowGraph.UNKNOWN) {
            return Math.max(0, edge);
        }
        final int ta = grid.flowThrough(a.nodeKey(), tick);
        final int tb = grid.flowThrough(b.nodeKey(), tick);
        if (ta == FeFlowGraph.UNKNOWN || tb == FeFlowGraph.UNKNOWN) {
            return 0;
        }
        return Math.max(0, Math.min(ta, tb));
    }

    @Nullable
    private FeGridMember memberOn(Level level, int stud) {
        final List<StudLink> links = studLinks.get(stud);
        if (links.isEmpty() || !level.isLoaded(links.get(0).to())) {
            return null;
        }
        return level.getBlockEntity(links.get(0).to()) instanceof FeGridMember member ? member : null;
    }

    private void syncLoadsIfDue(Level level, long tick) {
        final int every = ContactorLogic.LOAD_SYNC_TICKS;
        if ((tick + Math.floorMod(worldPosition.hashCode() * 7, every)) % every != 0) {
            return;
        }
        boolean due = false;
        for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
            due |= ContactorLogic.worthSending(load[pole], carried[pole]);
        }
        if (!due) {
            return;
        }
        System.arraycopy(carried, 0, load, 0, ContactorLogic.POLES);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    public ContactorState contactor() {
        return contactor;
    }

    public int carried(int pole) {
        return pole >= 0 && pole < ContactorLogic.POLES ? carried[pole] : 0;
    }

    public int load(int pole) {
        return pole >= 0 && pole < ContactorLogic.POLES ? load[pole] : 0;
    }

    public int[] loads() {
        return load;
    }

    public List<StudLink> studLinks(int stud) {
        return ContactorLogic.isStud(stud) ? studLinks.get(stud) : List.of();
    }

    public int wiresOnStuds() {
        int total = 0;
        for (List<StudLink> links : studLinks) {
            total += links.size();
        }
        return total;
    }

    public int studOf(BlockPos far) {
        for (int stud = 0; stud < ContactorLogic.STUDS; stud++) {
            for (StudLink link : studLinks.get(stud)) {
                if (link.to().equals(far)) {
                    return stud;
                }
            }
        }
        return -1;
    }

    public List<StudLink> throughFrom(int stud) {
        if (!ContactorLogic.isStud(stud) || !ContactorBlock.poleClosed(getBlockState(), ContactorLogic.poleOf(stud))) {
            return List.of();
        }
        return studLinks.get(ContactorLogic.pairedStud(stud));
    }

    public int poleRating(int pole) {
        int rate = 0;
        for (int stud : new int[] {pole, pole + ContactorLogic.POLES}) {
            for (StudLink link : studLinks(stud)) {
                final int r = PowerConnectorBE.baseTransferRate() * link.tier().multiplier();
                rate = rate == 0 ? r : Math.min(rate, r);
            }
        }
        return rate;
    }

    public void invalidatePoles(int poleMask) {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
            if ((poleMask & (1 << pole)) == 0) {
                continue;
            }
            for (int stud : new int[] {pole, pole + ContactorLogic.POLES}) {
                for (StudLink link : studLinks.get(stud)) {
                    if (level.isLoaded(link.to()) && level.getBlockEntity(link.to()) instanceof FeGridMember member) {
                        member.invalidateNetwork();
                    }
                }
            }
        }
    }

    private void invalidateLinkedTerminals() {
        invalidatePoles((1 << ContactorLogic.POLES) - 1);
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public int studLinkCount(int stud) {
        return studLinks(stud).size();
    }

    @Override
    public int maxLinksPerStud() {
        return MAX_LINKS_PER_STUD;
    }

    @Override
    public boolean isLinkedToConnector(BlockPos connector) {
        return studOf(connector) >= 0;
    }

    @Override
    public void linkStud(int stud, FeWireTerminal terminal, WireTier tier) {
        if (level == null || level.isClientSide() || !ContactorLogic.isStud(stud)) {
            return;
        }
        final WireTier grade = tier == null ? WireTier.COPPER : tier;
        studLinks.get(stud).add(new StudLink(terminal.terminalPos().immutable(), grade));
        terminal.addLink(worldPosition, grade);
        syncStudLinks();
    }

    @Override
    public int dropStudWires(int stud) {
        if (level == null || level.isClientSide() || !ContactorLogic.isStud(stud)) {
            return 0;
        }
        final List<StudLink> links = studLinks.get(stud);
        if (links.isEmpty()) {
            return 0;
        }
        final int removed = links.size();
        final Map<WireTier, Integer> refund = new EnumMap<>(WireTier.class);
        for (StudLink link : new ArrayList<>(links)) {
            refund.merge(link.tier(), 1, Integer::sum);
            if (level.getBlockEntity(link.to()) instanceof FeWireTerminal terminal) {
                terminal.removeDeadLink(worldPosition);
            }
        }

        invalidatePoles(1 << ContactorLogic.poleOf(stud));
        links.clear();
        for (Map.Entry<WireTier, Integer> entry : refund.entrySet()) {
            Block.popResource(level, worldPosition,
                    new ItemStack(com.createstats.registry.ModItems.wireItem(entry.getKey()), entry.getValue()));
        }
        syncStudLinks();
        return removed;
    }

    public void dropAllStudWires() {
        for (int stud = 0; stud < ContactorLogic.STUDS; stud++) {
            dropStudWires(stud);
        }
    }

    @Override
    public void forgetLinksTo(BlockPos connector) {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean changed = false;
        for (List<StudLink> links : studLinks) {
            changed |= links.removeIf(link -> link.to().equals(connector));
        }
        if (changed) {
            syncStudLinks();
        }
    }

    private void pruneStudLinks() {
        if (level == null) {
            return;
        }
        boolean changed = false;
        for (List<StudLink> links : studLinks) {
            changed |= links.removeIf(link -> level.isLoaded(link.to())
                    && !(level.getBlockEntity(link.to()) instanceof FeWireTerminal terminal
                    && terminal.links().contains(worldPosition)));
        }
        if (changed) {
            syncStudLinks();
        }
    }

    private void syncStudLinks() {
        setChanged();
        invalidateLinkedTerminals();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();

        invalidateLinkedTerminals();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final BlockState state = getBlockState();
        if (!(state.getBlock() instanceof ContactorBlock) || level == null) {
            return false;
        }
        tooltip.add(FeGoggle.title(Component.translatable("block.create_stats.contactor")));
        final boolean leftCoil = ContactorBlock.coil(level, worldPosition, state, ContactorState.LEFT_SIDE);
        final boolean rightCoil = ContactorBlock.coil(level, worldPosition, state, ContactorState.RIGHT_SIDE);
        tooltip.add(FeGoggle.dim(Component.translatable("create_stats.contactor.goggle.coils",
                coilWord(leftCoil), coilWord(rightCoil)).getString()));
        tooltip.add(FeGoggle.dim(Component.translatable("create_stats.contactor.goggle.form",
                ContactorBlock.formName(state.getValue(ContactorBlock.NORMALLY_CLOSED))).getString()));
        for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
            tooltip.add(poleLine(state, pole));
        }
        return true;
    }

    private static Component coilWord(boolean energised) {
        return Component.translatable(energised ? "create_stats.contactor.coil.on" : "create_stats.contactor.coil.off");
    }

    private Component poleLine(BlockState state, int pole) {
        final String name = ContactorLogic.studName(pole) + " - " + ContactorLogic.studName(pole + ContactorLogic.POLES);
        final boolean line = !studLinks(pole).isEmpty();
        final boolean loadSide = !studLinks(pole + ContactorLogic.POLES).isEmpty();
        if (!line && !loadSide) {
            return FeGoggle.dim(Component.translatable("create_stats.contactor.goggle.pole.unwired", name).getString());
        }
        if (!ContactorBlock.poleClosed(state, pole)) {
            return FeGoggle.warn(Component.translatable("create_stats.contactor.goggle.pole.open", name).getString());
        }
        final int rating = poleRating(pole);
        final int carrying = load(pole);
        final String text = Component.translatable("create_stats.contactor.goggle.pole.made", name,
                FeRates.perTick(carrying), FeRates.perTick(rating)).getString();
        return rating > 0 && carrying >= rating ? FeGoggle.bad(text) : FeGoggle.ok(text);
    }

    @Override
    public String settingsKey() {
        return "create_stats:contactor";
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("NormallyClosed", getBlockState().getBlock() instanceof ContactorBlock
                && getBlockState().getValue(ContactorBlock.NORMALLY_CLOSED));
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries, @Nullable Player player,
                                 boolean simulate) {
        if (!tag.contains("NormallyClosed") || !(getBlockState().getBlock() instanceof ContactorBlock block)) {
            return false;
        }
        if (simulate) {
            return true;
        }
        if (level != null && !level.isClientSide()) {
            block.setForm(level, worldPosition, getBlockState(), tag.getBoolean("NormallyClosed"));
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        final ListTag spans = new ListTag();
        for (int stud = 0; stud < ContactorLogic.STUDS; stud++) {
            for (StudLink link : studLinks.get(stud)) {
                final CompoundTag span = new CompoundTag();
                span.putByte("S", (byte) stud);
                span.putLong("P", link.to().asLong());
                span.putByte("T", (byte) link.tier().ordinal());
                spans.add(span);
            }
        }
        tag.put("StudWires", spans);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (List<StudLink> links : studLinks) {
            links.clear();
        }
        final ListTag spans = tag.getList("StudWires", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < spans.size(); i++) {
            final CompoundTag span = spans.getCompound(i);
            final int stud = span.getByte("S");
            if (!ContactorLogic.isStud(stud) || studLinks.get(stud).size() >= MAX_LINKS_PER_STUD) {
                continue;
            }
            studLinks.get(stud).add(new StudLink(BlockPos.of(span.getLong("P")),
                    WireTier.byOrdinal(span.getByte("T"))));
        }

        if (tag.contains("Load")) {
            final int[] sent = tag.getIntArray("Load");
            for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
                load[pole] = pole < sent.length ? Math.max(0, sent[pole]) : 0;
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        tag.putIntArray("Load", load.clone());
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    public ContactorMotion.Unit motion() {
        if (motion == null) {
            motion = new ContactorMotion.Unit();
        }
        return motion;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ContactorBE be) {
        if (ContactorBlock.coil(level, pos, state, ContactorState.LEFT_SIDE)
                || ContactorBlock.coil(level, pos, state, ContactorState.RIGHT_SIDE)) {
            com.createstats.client.ContactorHumSound.keep(level, pos);
        }
    }

    public Component status() {
        final BlockState state = getBlockState();
        if (!(state.getBlock() instanceof ContactorBlock) || level == null) {
            return Component.empty();
        }
        final StringBuilder flags = new StringBuilder();
        for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
            flags.append(ContactorBlock.poleClosed(state, pole) ? 'I' : 'O');
            if (pole < ContactorLogic.POLES - 1) {
                flags.append(' ');
            }
        }
        return Component.translatable("create_stats.contactor.status",
                ContactorBlock.formName(state.getValue(ContactorBlock.NORMALLY_CLOSED)),
                coilWord(ContactorBlock.coil(level, worldPosition, state, ContactorState.LEFT_SIDE)),
                coilWord(ContactorBlock.coil(level, worldPosition, state, ContactorState.RIGHT_SIDE)),
                flags.toString()).withStyle(ChatFormatting.GRAY);
    }
}
