package com.createstats.renderer;

import com.createstats.CreateStats;
import com.createstats.blockentity.CalculatorBE;
import com.createstats.util.CalculatorLayout;
import com.createstats.util.CalculatorLayout.FaceRegion;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CalculatorTextureManager
{
    private static final Map<BlockPos, TextureEntry> TEXTURES;
    private static int cleanupCounter;
    
    private CalculatorTextureManager() {
    }
    
    public static ResourceLocation getOrUpdateTexture(CalculatorBE be) {
        final CalculatorBE controller = be.getControllerBE();
        final BlockPos pos = controller.getBlockPos();
        final int textureWidth = Math.max(256, controller.getMultiblockWidth() * 256);
        final int textureHeight = Math.max(256, controller.getMultiblockHeight() * 256);
        TextureEntry entry = CalculatorTextureManager.TEXTURES.get(pos);
        if (entry == null || entry.width != textureWidth || entry.height != textureHeight) {
            if (entry != null) {
                disposeEntry(entry);
            }
            entry = createEntry(pos, textureWidth, textureHeight);
            if (entry == null) {
                return null;
            }
            CalculatorTextureManager.TEXTURES.put(pos, entry);
        }
        final long currentTick = getGameTime();
        entry.lastAccessTick = currentTick;
        final String pressedToken = controller.getRenderedPressedToken(currentTick);
        if (entry.needsRedraw || !pressedToken.equals(entry.renderedPressedToken)) {
            updateTexture(entry, controller, pressedToken);
            entry.lastUpdateTick = currentTick;
            entry.needsRedraw = false;
            entry.renderedPressedToken = pressedToken;
        }
        if (++CalculatorTextureManager.cleanupCounter >= 100) {
            CalculatorTextureManager.cleanupCounter = 0;
            cleanupStale();
        }
        return entry.location;
    }
    
    public static void markDirty(BlockPos pos) {
        final TextureEntry entry = CalculatorTextureManager.TEXTURES.get(pos);
        if (entry != null) {
            entry.needsRedraw = true;
        }
    }
    
    public static void cleanup(BlockPos pos) {
        final TextureEntry entry = CalculatorTextureManager.TEXTURES.remove(pos);
        if (entry != null) {
            disposeEntry(entry);
        }
    }
    
    public static void cleanupAll() {
        for (TextureEntry entry : CalculatorTextureManager.TEXTURES.values()) {
            disposeEntry(entry);
        }
        CalculatorTextureManager.TEXTURES.clear();
    }
    
    private static TextureEntry createEntry(BlockPos pos, int width, int height) {
        try {
            final NativeImage image = new NativeImage(width, height, false);
            final DynamicTexture texture = new DynamicTexture(image);
            final String name = "create_stats/calculator_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + width + "x" + height;
            final ResourceLocation location = Minecraft.getInstance().getTextureManager().register(name, texture);
            final TextureEntry entry = new TextureEntry();
            entry.image = image;
            entry.dynTex = texture;
            entry.location = location;
            entry.lastUpdateTick = 0L;
            entry.lastAccessTick = getGameTime();
            entry.needsRedraw = true;
            entry.width = width;
            entry.height = height;
            entry.renderedPressedToken = "";
            return entry;
        }
        catch (Exception ex) {
            return null;
        }
    }
    
    private static void updateTexture(TextureEntry entry, CalculatorBE be, String pressedToken) {
        final NativeImage image = entry.image;
        final int width = image.getWidth();
        final int height = image.getHeight();
        TextureGraphRenderer.fillRect(image, 0, 0, width, height, -15129708);
        final int outerDepth = Math.max(4, width / 96);
        drawRaisedPlate(image, 0, 0, width, height, outerDepth, -13748409, -6327965, -16446906);
        final int frameInset = Math.max(10, width / 56);
        drawRaisedPlate(image, frameInset, frameInset, width - frameInset * 2, height - frameInset * 2, Math.max(3, width / 168), -13483996, -7649586, -15854558);
        final int titleInset = frameInset + Math.max(6, width / 80);
        final int titleWidth = width - titleInset * 2;
        final int titleHeight = Math.max(18, height / 16);
        drawInsetPanel(image, titleInset, titleInset, titleWidth, titleHeight, Math.max(2, width / 224), -11911033, -15854558, -9474193);
        final int titleScale = fitScale("CALCULATOR", titleWidth - 12, Math.max(2, width / 220));
        BitmapFont.drawCentered(image, "CALCULATOR", titleInset, titleInset + titleHeight / 2 - BitmapFont.textHeight(titleScale) / 2, titleWidth, -2501175, titleScale);
        drawDisplay(image, be, width, height);
        for (FaceRegion button : CalculatorLayout.getFaceButtons()) {
            drawButton(image, button, width, height, button.getToken().equals(pressedToken));
        }
        entry.dynTex.upload();
    }
    
    private static void drawDisplay(NativeImage image, CalculatorBE be, int width, int height) {
        final FaceRegion display = CalculatorLayout.getDisplayArea();
        final int x = scaleX(display.getX(), width);
        final int y = scaleY(display.getY(), height);
        final int w = scaleWidth(display.getWidth(), width);
        final int h = scaleHeight(display.getHeight(), height);
        drawInsetPanel(image, x, y, w, h, Math.max(3, width / 200), -11254323, -16578553, -8232525);
        final int innerInset = Math.max(4, width / 128);
        TextureGraphRenderer.fillRect(image, x + innerInset, y + innerInset, w - innerInset * 2, h - innerInset * 2, -16379633);
        final int smallScale = fitScale("SCIENTIFIC", w - innerInset * 2 - 8, Math.max(1, width / 320));
        final String modeText = be.isScientificMode() ? "SCIENTIFIC" : "SIMPLE";
        final int statusColor = be.getErrorMessage().isEmpty() ? (be.isScientificMode() ? -9973424 : -2501175) : -5813901;
        BitmapFont.drawString(image, modeText, x + innerInset + 4, y + innerInset + 3, statusColor, smallScale);
        BitmapFont.drawRight(image, "CLICK DISPLAY", x + w - innerInset - 4, y + innerInset + 3, -9474193, smallScale);
        TextureGraphRenderer.fillRect(image, x + innerInset + 4, y + innerInset + BitmapFont.textHeight(smallScale) + 6, w - innerInset * 2 - 8, Math.max(1, width / 256), statusColor);
        final String expression = truncate((be.getExpression() == null || be.getExpression().isEmpty()) ? "0" : be.getExpression(), w - innerInset * 2 - 8, Math.max(2, width / 158));
        final String result = be.getErrorMessage().isEmpty() ? (be.getLastResultText().isEmpty() ? "Ready to graph" : "= " + be.getLastResultText()) : be.getErrorMessage();
        final int exprScale = fitScale(expression, w - innerInset * 2 - 8, Math.max(2, width / 158));
        final int exprY = y + h / 2 - BitmapFont.textHeight(exprScale);
        BitmapFont.drawString(image, expression, x + innerInset + 4, exprY, -131587, exprScale);
        final int resultScale = fitScale(result, w - innerInset * 2 - 8, Math.max(1, width / 240));
        final int resultY = y + h - innerInset - BitmapFont.textHeight(resultScale) - 3;
        BitmapFont.drawString(image, truncate(result, w - innerInset * 2 - 8, resultScale), x + innerInset + 4, resultY, be.getErrorMessage().isEmpty() ? -10445081 : -5813901, resultScale);
    }
    
    private static void drawButton(NativeImage image, FaceRegion button, int width, int height, boolean pressed) {
        final int x = scaleX(button.getX(), width);
        final int y = scaleY(button.getY(), height);
        final int w = scaleWidth(button.getWidth(), width);
        final int h = scaleHeight(button.getHeight(), height);
        final int background;
        if ("=".equals(button.getToken())) {
            background = -11501970;
        }
        else if ("C".equals(button.getToken())) {
            background = -7595213;
        }
        else if ("__backspace__".equals(button.getToken())) {
            background = -12360827;
        }
        else if ("/".equals(button.getToken()) || "*".equals(button.getToken()) || "-".equals(button.getToken()) || "+".equals(button.getToken())) {
            background = -7699682;
        }
        else {
            background = -11255482;
        }
        final int depth = Math.max(2, width / 192);
        if (pressed) {
            drawPressedButton(image, x, y, w, h, depth, background);
            TextureGraphRenderer.drawBorder(image, x, y, w, h, Math.max(1, depth), -2501175);
        }
        else {
            drawRaisedButton(image, x, y, w, h, depth, background);
        }
        final int scale = fitScale(button.getLabel(), w - 8, Math.max(2, Math.min(width, height) / 205));
        final int textOffset = pressed ? 1 : 0;
        final int textY = y + h / 2 - BitmapFont.textHeight(scale) / 2 + textOffset;
        BitmapFont.drawCentered(image, button.getLabel(), x + textOffset, textY, w, -131587, scale);
    }
    
    private static int scaleX(double value, int width) {
        return (int)Math.round(value * width);
    }
    
    private static int scaleY(double value, int height) {
        return (int)Math.round(value * height);
    }
    
    private static int scaleWidth(double value, int width) {
        return Math.max(1, (int)Math.round(value * width));
    }
    
    private static int scaleHeight(double value, int height) {
        return Math.max(1, (int)Math.round(value * height));
    }
    
    private static int fitScale(String text, int maxWidth, int preferred) {
        int scale = Math.max(1, Math.min(5, preferred));
        while (scale > 1 && BitmapFont.measureString(text, scale) > maxWidth) {
            --scale;
        }
        return scale;
    }
    
    private static String truncate(String text, int maxWidth, int scale) {
        if (BitmapFont.measureString(text, scale) <= maxWidth) {
            return text;
        }
        String working = text;
        while (working.length() > 3 && BitmapFont.measureString(working + "..", scale) > maxWidth) {
            working = working.substring(0, working.length() - 1);
        }
        return working + "..";
    }
    
    private static void drawRaisedPlate(NativeImage image, int x, int y, int w, int h, int depth, int fillColor, int highlightColor, int shadowColor) {
        TextureGraphRenderer.fillRect(image, x + depth, y + depth, w - depth, h - depth, shadowColor);
        TextureGraphRenderer.fillRect(image, x, y, w - depth, h - depth, fillColor);
        TextureGraphRenderer.fillRect(image, x, y, w - depth, depth, highlightColor);
        TextureGraphRenderer.fillRect(image, x, y, depth, h - depth, highlightColor);
        TextureGraphRenderer.fillRect(image, x + depth, y + h - depth, w - depth, depth, shadowColor);
        TextureGraphRenderer.fillRect(image, x + w - depth, y + depth, depth, h - depth, shadowColor);
    }
    
    private static void drawInsetPanel(NativeImage image, int x, int y, int w, int h, int depth, int rimColor, int innerShadowColor, int highlightColor) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, rimColor);
        TextureGraphRenderer.fillRect(image, x + depth, y + depth, w - depth, h - depth, highlightColor);
        TextureGraphRenderer.fillRect(image, x + depth, y + depth, w - depth * 2, h - depth * 2, innerShadowColor);
        TextureGraphRenderer.fillRect(image, x, y, w, depth, innerShadowColor);
        TextureGraphRenderer.fillRect(image, x, y, depth, h, innerShadowColor);
    }
    
    private static void drawRaisedButton(NativeImage image, int x, int y, int w, int h, int depth, int fillColor) {
        TextureGraphRenderer.fillRect(image, x + depth, y + depth, w - depth, h - depth, -15329770);
        TextureGraphRenderer.fillRect(image, x, y, w - depth, h - depth, fillColor);
        TextureGraphRenderer.fillRect(image, x, y, w - depth, depth, -2235938);
        TextureGraphRenderer.fillRect(image, x, y, depth, h - depth, -2235938);
        TextureGraphRenderer.fillRect(image, x + depth, y + h - depth, w - depth, depth, -16513786);
        TextureGraphRenderer.fillRect(image, x + w - depth, y + depth, depth, h - depth, -16513786);
        TextureGraphRenderer.drawBorder(image, x, y, w - depth, h - depth, 1, -7237231);
    }
    
    private static void drawPressedButton(NativeImage image, int x, int y, int w, int h, int depth, int fillColor) {
        final int inset = Math.max(1, depth - 1);
        TextureGraphRenderer.fillRect(image, x, y, w, h, -15920374);
        TextureGraphRenderer.fillRect(image, x + inset, y + inset, w - inset, h - inset, fillColor);
        TextureGraphRenderer.fillRect(image, x, y, w, depth, -16513786);
        TextureGraphRenderer.fillRect(image, x, y, depth, h, -16513786);
        TextureGraphRenderer.fillRect(image, x + inset, y + h - depth, w - inset, depth, -2235938);
        TextureGraphRenderer.fillRect(image, x + w - depth, y + inset, depth, h - inset, -2235938);
        TextureGraphRenderer.drawBorder(image, x + inset, y + inset, w - inset, h - inset, 1, -8288897);
    }
    
    private static void cleanupStale() {
        final long currentTick = getGameTime();
        final Iterator<Map.Entry<BlockPos, TextureEntry>> iterator = CalculatorTextureManager.TEXTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<BlockPos, TextureEntry> entry = iterator.next();
            if (currentTick - entry.getValue().lastAccessTick > 200L) {
                disposeEntry(entry.getValue());
                iterator.remove();
            }
        }
    }
    
    private static void disposeEntry(TextureEntry entry) {
        try {
            if (entry.dynTex != null) {
                entry.dynTex.close();
            }
        }
        catch (RuntimeException ex) {
            CreateStats.LOGGER.warn("Failed to dispose calculator texture", ex);
        }
    }
    
    private static long getGameTime() {
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.getGameTime();
        }
        return System.currentTimeMillis() / 50L;
    }
    
    static {
        TEXTURES = new HashMap<BlockPos, TextureEntry>();
        CalculatorTextureManager.cleanupCounter = 0;
    }
    
    private static class TextureEntry
    {
        NativeImage image;
        DynamicTexture dynTex;
        ResourceLocation location;
        long lastUpdateTick;
        long lastAccessTick;
        boolean needsRedraw;
        int width;
        int height;
        String renderedPressedToken;
    }
}
