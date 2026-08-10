package com.createstats.blockentity;

import com.createstats.renderer.CalculatorTextureManager;
import com.createstats.util.ExpressionEvaluator;
import com.createstats.util.ExpressionEvaluator.EvaluationException;
import com.createstats.util.ExpressionEvaluator.EvaluationResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class CalculatorBE extends BlockEntity
{
    private static final int MAX_HISTORY = 24;
    private static final int MAX_PINNED_FORMULAS = 12;
    private static final int MAX_EXPRESSION_LENGTH = 256;
    private static final int MAX_RESULT_LENGTH = 96;
    private static final int FACE_PRESS_DURATION = 6;
    private String expression;
    private String lastResultText;
    private String errorMessage;
    private boolean scientificMode;
    private final List<String> history;
    private final List<String> pinnedFormulas;
    private double plotMinX;
    private double plotMaxX;
    private boolean isController;
    private BlockPos controllerPos;
    private int multiblockWidth;
    private int multiblockHeight;
    private String lastPressedToken;
    private long lastPressedTick;
    
    public CalculatorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super((BlockEntityType)type, pos, state);
        this.expression = "31";
        this.lastResultText = "";
        this.errorMessage = "";
        this.scientificMode = false;
        this.history = new ArrayList<String>();
        this.pinnedFormulas = new ArrayList<String>();
        this.plotMinX = -10.0;
        this.plotMaxX = 10.0;
        this.isController = true;
        this.controllerPos = pos;
        this.multiblockWidth = 1;
        this.multiblockHeight = 1;
        this.lastPressedToken = "";
        this.lastPressedTick = Long.MIN_VALUE / 4L;
    }
    
    public boolean isController() {
        return this.isController;
    }
    
    public BlockPos getControllerPos() {
        return this.controllerPos;
    }
    
    public CalculatorBE getControllerBE() {
        if (this.isController) {
            return this;
        }
        if (this.level == null || this.controllerPos == null) {
            return this;
        }
        final BlockEntity be = this.level.getBlockEntity(this.controllerPos);
        if (be instanceof CalculatorBE calculator) {
            return calculator;
        }
        return this;
    }
    
    public int getMultiblockWidth() {
        return this.multiblockWidth;
    }
    
    public int getMultiblockHeight() {
        return this.multiblockHeight;
    }
    
    public String getExpression() {
        return this.expression;
    }
    
    public String getLastResultText() {
        return this.lastResultText;
    }
    
    public String getErrorMessage() {
        return this.errorMessage;
    }
    
    public boolean isScientificMode() {
        return this.scientificMode;
    }
    
    public List<String> getHistory() {
        return Collections.unmodifiableList(this.history);
    }

    public List<String> getPinnedFormulas() {
        return Collections.unmodifiableList(this.pinnedFormulas);
    }
    
    public double getPlotMinX() {
        return this.plotMinX;
    }
    
    public double getPlotMaxX() {
        return this.plotMaxX;
    }
    
    public String getRenderedPressedToken(long currentTick) {
        if (this.lastPressedToken == null || this.lastPressedToken.isEmpty()) {
            return "";
        }
        if (currentTick - this.lastPressedTick > 6L) {
            return "";
        }
        return this.lastPressedToken;
    }
    
    public void setExpression(String expression) {
        this.expression = trimExpression(expression);
        this.markTextureDirtyClient();
    }
    
    public void setLastResultText(String resultText) {
        this.lastResultText = trimText(resultText, 96);
        this.markTextureDirtyClient();
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = trimText(CalculatorBE.formatErrorMessage(errorMessage), 96);
        this.markTextureDirtyClient();
    }
    
    public void setScientificMode(boolean scientificMode) {
        this.scientificMode = scientificMode;
        this.markTextureDirtyClient();
    }
    
    public void setPlotRange(double minX, double maxX) {
        if (maxX <= minX) {
            maxX = minX + 1.0;
        }
        this.plotMinX = minX;
        this.plotMaxX = maxX;
    }
    
    public void clearEntry() {
        this.expression = "";
        this.lastResultText = "";
        this.errorMessage = "";
        this.markTextureDirtyClient();
    }
    
    public void clearHistory() {
        this.history.clear();
        this.markTextureDirtyClient();
    }

    public boolean pinFormula(String formula) {
        final String trimmed = trimExpression(formula);
        if (trimmed.isEmpty()) {
            return false;
        }
        this.pinnedFormulas.remove(trimmed);
        this.pinnedFormulas.add(0, trimmed);
        this.trimPinnedFormulas();
        this.markTextureDirtyClient();
        return true;
    }

    public void clearPinnedFormulas() {
        this.pinnedFormulas.clear();
        this.markTextureDirtyClient();
    }
    
    /** [1] */
    public boolean pressFaceButton(String token) {
        final CalculatorBE controller = this.getControllerBE();
        if (controller != this) {
            return controller.pressFaceButton(token);
        }
        // If this block is a multiblock member whose controller BE is gone,
        // getControllerBE() falls back to returning self. Refuse to process
        // the press rather than silently mutating stale member state.
        if (!this.isController) {
            return false;
        }
        if (token == null || token.isEmpty()) {
            return false;
        }
        this.lastPressedToken = token;
        this.lastPressedTick = (this.level != null) ? this.level.getGameTime() : 0L;
        if ("C".equals(token)) {
            this.clearEntry();
        }
        else if ("__backspace__".equals(token)) {
            if (!this.expression.isEmpty()) {
                this.expression = this.expression.substring(0, this.expression.length() - 1);
            }
            this.errorMessage = "";
        }
        else if ("=".equals(token)) {
            this.evaluateExpression();
        }
        else {
            this.expression = trimExpression(this.expression + token);
            this.errorMessage = "";
        }
        this.syncState();
        return true;
    }
    
    public boolean evaluateExpression() {
        if (this.expression == null || this.expression.trim().isEmpty()) {
            this.errorMessage = CalculatorBE.formatErrorMessage("Expression is empty");
            this.lastResultText = "";
            this.markTextureDirtyClient();
            return false;
        }
        try {
            final EvaluationResult result = ExpressionEvaluator.evaluate(this.expression, this.scientificMode);
            this.lastResultText = trimText(result.getFormatted(), 96);
            this.errorMessage = "";
            this.addHistoryEntry(this.expression + " = " + this.lastResultText);
            this.markTextureDirtyClient();
            return true;
        }
        catch (EvaluationException e) {
            this.lastResultText = "";
            this.errorMessage = trimText(CalculatorBE.formatErrorMessage(e.getMessage()), 96);
            this.markTextureDirtyClient();
            return false;
        }
    }
    
    public void applyClientState(CompoundTag tag) {
        this.loadCalculatorState(tag);
        this.syncState();
    }
    
    public CompoundTag saveCalculatorState() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Expression", this.expression);
        tag.putString("Result", this.lastResultText);
        tag.putString("Error", this.errorMessage);
        tag.putBoolean("Scientific", this.scientificMode);
        tag.putDouble("PlotMinX", this.plotMinX);
        tag.putDouble("PlotMaxX", this.plotMaxX);
        tag.putString("LastPressedToken", this.lastPressedToken);
        tag.putLong("LastPressedTick", this.lastPressedTick);
        final ListTag historyTag = new ListTag();
        for (String entry : this.history) {
            historyTag.add(StringTag.valueOf(entry));
        }
        tag.put("History", historyTag);
        final ListTag pinnedTag = new ListTag();
        for (String formula : this.pinnedFormulas) {
            pinnedTag.add(StringTag.valueOf(formula));
        }
        tag.put("PinnedFormulas", pinnedTag);
        return tag;
    }
    
    public void loadCalculatorState(CompoundTag tag) {
        this.expression = trimExpression(tag.getString("Expression"));
        this.lastResultText = trimText(tag.getString("Result"), 96);
        this.errorMessage = trimText(tag.getString("Error"), 96);
        this.scientificMode = tag.getBoolean("Scientific");
        this.plotMinX = tag.contains("PlotMinX") ? tag.getDouble("PlotMinX") : -10.0;
        this.plotMaxX = tag.contains("PlotMaxX") ? tag.getDouble("PlotMaxX") : 10.0;
        this.lastPressedToken = trimText(tag.getString("LastPressedToken"), 32);
        this.lastPressedTick = tag.contains("LastPressedTick") ? tag.getLong("LastPressedTick") : (Long.MIN_VALUE / 4L);
        if (this.plotMaxX <= this.plotMinX) {
            this.plotMaxX = this.plotMinX + 1.0;
        }
        this.history.clear();
        if (tag.contains("History", 9)) {
            final ListTag historyTag = tag.getList("History", 8);
            for (int i = 0; i < historyTag.size(); ++i) {
                this.history.add(trimText(historyTag.getString(i), 128));
            }
        }
        this.trimHistory();
        this.pinnedFormulas.clear();
        if (tag.contains("PinnedFormulas", 9)) {
            final ListTag pinnedTag = tag.getList("PinnedFormulas", 8);
            for (int i = 0; i < pinnedTag.size(); ++i) {
                final String formula = trimExpression(pinnedTag.getString(i));
                if (!formula.isEmpty() && !this.pinnedFormulas.contains(formula)) {
                    this.pinnedFormulas.add(formula);
                }
            }
        }
        this.trimPinnedFormulas();
    }
    
    private void addHistoryEntry(String entry) {
        final String trimmed = trimText(entry, 128);
        if (trimmed.isEmpty()) {
            return;
        }
        this.history.remove(trimmed);
        this.history.add(0, trimmed);
        this.trimHistory();
    }
    
    private void trimHistory() {
        while (this.history.size() > MAX_HISTORY) {
            this.history.remove(this.history.size() - 1);
        }
    }

    private void trimPinnedFormulas() {
        while (this.pinnedFormulas.size() > MAX_PINNED_FORMULAS) {
            this.pinnedFormulas.remove(this.pinnedFormulas.size() - 1);
        }
    }
    
    private static String trimExpression(String text) {
        return trimText(text, 256);
    }
    
    private static String trimText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    public static String formatErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return switch (message) {
            case "Expression is empty" -> "Enter a formula";
            case "x is only available while plotting" -> "Use Plot to graph x-based formulas";
            case "x needs scientific mode" -> "Enable Scientific to use x";
            case "pi needs scientific mode" -> "Enable Scientific to use pi";
            case "e needs scientific mode" -> "Enable Scientific to use e";
            case "Powers need scientific mode" -> "Enable Scientific to use ^";
            case "Functions need scientific mode" -> "Enable Scientific to use sin, cos, log, etc.";
            case "Plot range must increase" -> "Set x min below x max";
            case "Result is not finite" -> "Result is too large or undefined";
            case "Division by zero" -> "Division by zero";
            case "Unexpected '\0'" -> "Unexpected end of expression";
            default -> message;
        };
    }
    
    private void syncState() {
        this.setChanged();
        if (this.level == null) {
            return;
        }
        if (this.level.isClientSide()) {
            this.markTextureDirtyClient();
        }
        else {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }
    
    private void markTextureDirtyClient() {
        if (this.level != null && this.level.isClientSide()) {
            CalculatorTextureManager.markDirty(this.getControllerBE().getBlockPos());
        }
    }
    
    public void onRemoved() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final List<BlockPos> members = this.getMultiblockMembers();
        this.isController = true;
        this.controllerPos = this.worldPosition;
        this.multiblockWidth = 1;
        this.multiblockHeight = 1;
        for (BlockPos pos : members) {
            if (pos.equals(this.worldPosition)) {
                continue;
            }
            final BlockEntity be = this.level.getBlockEntity(pos);
            if (!(be instanceof CalculatorBE)) {
                continue;
            }
            final CalculatorBE calculator = (CalculatorBE)be;
            calculator.isController = true;
            calculator.controllerPos = pos;
            calculator.multiblockWidth = 1;
            calculator.multiblockHeight = 1;
            calculator.setChanged();
            this.level.sendBlockUpdated(pos, calculator.getBlockState(), calculator.getBlockState(), 3);
        }
        for (BlockPos pos : members) {
            if (pos.equals(this.worldPosition)) {
                continue;
            }
            final BlockEntity be = this.level.getBlockEntity(pos);
            if (be instanceof CalculatorBE calculator2) {
                calculator2.detectAndFormMultiblock();
                break;
            }
        }
    }
    
    public void detectAndFormMultiblock() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final BlockState myState = this.getBlockState();
        if (!myState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return;
        }
        final Direction facing = (Direction)myState.getValue(HorizontalDirectionalBlock.FACING);
        final Direction right = facing.getClockWise();
        final Set<BlockPos> connected = new HashSet<BlockPos>();
        final Queue<BlockPos> toCheck = new LinkedList<BlockPos>();
        connected.add(this.worldPosition);
        toCheck.add(this.worldPosition);
        while (!toCheck.isEmpty()) {
            final BlockPos current = toCheck.poll();
            for (Direction dir : new Direction[] { right, right.getOpposite(), Direction.UP, Direction.DOWN }) {
                final BlockPos neighbor = current.relative(dir);
                if (connected.contains(neighbor)) {
                    continue;
                }
                final BlockEntity be = this.level.getBlockEntity(neighbor);
                if (!(be instanceof CalculatorBE)) {
                    continue;
                }
                final BlockState neighborState = be.getBlockState();
                if (!neighborState.hasProperty(HorizontalDirectionalBlock.FACING) || neighborState.getValue(HorizontalDirectionalBlock.FACING) != facing) {
                    continue;
                }
                connected.add(neighbor);
                toCheck.add(neighbor);
            }
        }
        final BlockPos ref = connected.iterator().next();
        final int refH = dotAlongAxis(ref, right);
        final int refV = ref.getY();
        final Set<Long> occupied = new HashSet<Long>();
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE;
        int maxV = Integer.MIN_VALUE;
        for (BlockPos pos : connected) {
            final int h = dotAlongAxis(pos, right) - refH;
            final int v = pos.getY() - refV;
            occupied.add(packHV(h, v));
            minH = Math.min(minH, h);
            maxH = Math.max(maxH, h);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        int bestArea = 0;
        int bestH = minH;
        int bestV = minV;
        int bestW = 1;
        int bestHeight = 1;
        for (int startV = minV; startV <= maxV; ++startV) {
            for (int startH = minH; startH <= maxH; ++startH) {
                if (!occupied.contains(packHV(startH, startV))) {
                    continue;
                }
                int maxWidth = maxH - startH + 1;
                for (int height = 1; height <= maxV - startV + 1; ++height) {
                    int rowWidth = 0;
                    while (rowWidth < maxWidth && occupied.contains(packHV(startH + rowWidth, startV + height - 1))) {
                        ++rowWidth;
                    }
                    maxWidth = Math.min(maxWidth, rowWidth);
                    if (maxWidth <= 0) {
                        break;
                    }
                    final int area = maxWidth * height;
                    if (area > bestArea || area == bestArea && area == 6 && maxWidth == 2 && height == 3) {
                        bestArea = area;
                        bestH = startH;
                        bestV = startV;
                        bestW = maxWidth;
                        bestHeight = height;
                    }
                }
            }
        }
        final Set<BlockPos> rectangle = new HashSet<BlockPos>();
        for (BlockPos pos2 : connected) {
            final int h2 = dotAlongAxis(pos2, right) - refH;
            final int v2 = pos2.getY() - refV;
            if (h2 >= bestH && h2 < bestH + bestW && v2 >= bestV && v2 < bestV + bestHeight) {
                rectangle.add(pos2);
            }
        }
        final BlockPos ctrlPos = rectangle.stream().min(Comparator.<BlockPos, Integer>comparing(Vec3i::getY).thenComparing(pos3 -> dotAlongAxis(pos3, right))).orElse(this.worldPosition);
        for (BlockPos pos4 : connected) {
            final BlockEntity be2 = this.level.getBlockEntity(pos4);
            if (!(be2 instanceof CalculatorBE calculator)) {
                continue;
            }
            if (rectangle.contains(pos4)) {
                calculator.isController = pos4.equals(ctrlPos);
                calculator.controllerPos = ctrlPos;
                calculator.multiblockWidth = bestW;
                calculator.multiblockHeight = bestHeight;
            }
            else {
                calculator.isController = true;
                calculator.controllerPos = pos4;
                calculator.multiblockWidth = 1;
                calculator.multiblockHeight = 1;
            }
            calculator.setChanged();
            this.level.sendBlockUpdated(pos4, calculator.getBlockState(), calculator.getBlockState(), 3);
        }
    }
    
    public List<BlockPos> getMultiblockMembers() {
        final List<BlockPos> members = new ArrayList<BlockPos>();
        final BlockPos origin = (this.controllerPos != null) ? this.controllerPos : this.worldPosition;
        if (this.level == null || this.multiblockWidth <= 0 || this.multiblockHeight <= 0) {
            members.add(this.worldPosition);
            return members;
        }
        final BlockState state = this.getBlockState();
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            members.add(this.worldPosition);
            return members;
        }
        final Direction right = ((Direction)state.getValue(HorizontalDirectionalBlock.FACING)).getClockWise();
        for (int dy = 0; dy < this.multiblockHeight; ++dy) {
            for (int dx = 0; dx < this.multiblockWidth; ++dx) {
                members.add(origin.relative(right, dx).above(dy));
            }
        }
        return members;
    }
    
    public AABB getRenderBoundingBox() {
        AABB box = new AABB(this.worldPosition);
        for (BlockPos pos : this.getMultiblockMembers()) {
            box = box.minmax(new AABB(pos));
        }
        return box.inflate(1.0);
    }
    
    public void setRemoved() {
        if (this.level != null && this.level.isClientSide()) {
            CalculatorTextureManager.cleanup(this.getControllerBE().getBlockPos());
        }
        super.setRemoved();
    }
    
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("CalculatorState", this.saveCalculatorState());
        tag.putBoolean("IsController", this.isController);
        if (this.controllerPos != null) {
            tag.putLong("ControllerPos", this.controllerPos.asLong());
        }
        tag.putInt("MBWidth", this.multiblockWidth);
        tag.putInt("MBHeight", this.multiblockHeight);
    }
    
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("CalculatorState")) {
            this.loadCalculatorState(tag.getCompound("CalculatorState"));
        }
        else {
            this.expression = trimExpression(tag.getString("Expression"));
            this.lastResultText = trimText(tag.getString("Result"), 96);
            this.errorMessage = trimText(tag.getString("Error"), 96);
        }
        this.isController = (!tag.contains("IsController") || tag.getBoolean("IsController"));
        if (tag.contains("ControllerPos")) {
            this.controllerPos = BlockPos.of(tag.getLong("ControllerPos"));
        }
        else if (this.controllerPos == null) {
            this.controllerPos = this.worldPosition;
        }
        this.multiblockWidth = Math.max(1, tag.getInt("MBWidth"));
        this.multiblockHeight = Math.max(1, tag.getInt("MBHeight"));
        if (this.level != null && this.level.isClientSide()) {
            this.markTextureDirtyClient();
        }
    }
    
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }
    
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return (Packet<ClientGamePacketListener>)ClientboundBlockEntityDataPacket.create((BlockEntity)this);
    }
    
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        final CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.handleUpdateTag(tag, registries);
        }
    }
    
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
    }
    
    private static long packHV(int h, int v) {
        return (long)h << 32 | ((long)v & 0xFFFFFFFFL);
    }
    
    private static int dotAlongAxis(BlockPos pos, Direction axis) {
        return pos.getX() * axis.getStepX() + pos.getZ() * axis.getStepZ();
    }
}
