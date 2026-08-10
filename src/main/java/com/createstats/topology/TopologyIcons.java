package com.createstats.topology;

import com.createstats.renderer.BitmapFont;
import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TopologyIcons
{
    private static final byte[] ICON_SHAFT;
    private static final byte[] ICON_COGWHEEL;
    private static final byte[] ICON_LARGE_COGWHEEL;
    private static final byte[] ICON_GEARBOX;
    private static final byte[] ICON_BELT;
    private static final byte[] ICON_CHAIN;
    private static final byte[] ICON_GEARSHIFT;
    private static final byte[] ICON_GENERIC_SMALL;
    private static final byte[] ICON_WATER_WHEEL;
    private static final byte[] ICON_PRESS;
    private static final byte[] ICON_STEAM_ENGINE;
    private static final byte[] ICON_FAN;
    private static final byte[] ICON_MOTOR;
    private static final byte[] ICON_MIXER;
    private static final byte[] ICON_DRILL;
    private static final byte[] ICON_SAW;
    private static final byte[] ICON_DEPLOYER;
    private static final byte[] ICON_MILLSTONE;
    private static final byte[] ICON_CRUSHING;
    private static final byte[] ICON_WINDMILL;
    private static final byte[] ICON_HAND_CRANK;
    private static final byte[] ICON_FLYWHEEL;
    private static final byte[] ICON_SPEED_CONTROLLER;
    private static final byte[] ICON_GAUGE;
    private static final byte[] ICON_GENERIC;
    
    private TopologyIcons() {
    }
    
    public static byte[] getIcon(NodeType type) {
        return switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case SHAFT -> TopologyIcons.ICON_SHAFT;
            case COGWHEEL -> TopologyIcons.ICON_COGWHEEL;
            case LARGE_COGWHEEL -> TopologyIcons.ICON_LARGE_COGWHEEL;
            case GEARBOX -> TopologyIcons.ICON_GEARBOX;
            case GEARSHIFT -> TopologyIcons.ICON_GEARSHIFT;
            case BELT -> TopologyIcons.ICON_BELT;
            case CHAIN_DRIVE -> TopologyIcons.ICON_CHAIN;
            case SPEED_CONTROLLER -> TopologyIcons.ICON_SPEED_CONTROLLER;
            case MECHANICAL_PRESS -> TopologyIcons.ICON_PRESS;
            case MECHANICAL_MIXER -> TopologyIcons.ICON_MIXER;
            case MECHANICAL_DRILL -> TopologyIcons.ICON_DRILL;
            case MECHANICAL_SAW -> TopologyIcons.ICON_SAW;
            case DEPLOYER -> TopologyIcons.ICON_DEPLOYER;
            case ENCASED_FAN -> TopologyIcons.ICON_FAN;
            case MILLSTONE -> TopologyIcons.ICON_MILLSTONE;
            case CRUSHING_WHEEL -> TopologyIcons.ICON_CRUSHING;
            case WATER_WHEEL -> TopologyIcons.ICON_WATER_WHEEL;
            case WINDMILL -> TopologyIcons.ICON_WINDMILL;
            case HAND_CRANK -> TopologyIcons.ICON_HAND_CRANK;
            case STEAM_ENGINE -> TopologyIcons.ICON_STEAM_ENGINE;
            case CREATIVE_MOTOR -> TopologyIcons.ICON_MOTOR;
            case FLYWHEEL -> TopologyIcons.ICON_FLYWHEEL;
            case GAUGE -> TopologyIcons.ICON_GAUGE;
            case OTHER -> TopologyIcons.ICON_GENERIC;
        };
    }
    
    public static void drawIcon(NativeImage image, byte[] icon, int iconSize, int x, int y, int argbColor, int scale) {
        final int abgr = BitmapFont.argbToAbgr(argbColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        final int mask = (1 << iconSize) - 1;
        for (int row = 0; row < iconSize; ++row) {
            final int rowBits = icon[row] & mask;
            for (int col = 0; col < iconSize; ++col) {
                if ((rowBits & 1 << iconSize - 1 - col) != 0x0) {
                    for (int sy = 0; sy < scale; ++sy) {
                        for (int sx = 0; sx < scale; ++sx) {
                            final int px = x + col * scale + sx;
                            final int py = y + row * scale + sy;
                            if (px >= 0 && px < imgW && py >= 0 && py < imgH) {
                                image.setPixelRGBA(px, py, abgr);
                            }
                        }
                    }
                }
            }
        }
    }
    
    static {
        ICON_SHAFT = new byte[] { 4, 4, 14, 4, 4 };
        ICON_COGWHEEL = new byte[] { 10, 27, 14, 27, 10 };
        ICON_LARGE_COGWHEEL = new byte[] { 14, 21, 27, 21, 14 };
        ICON_GEARBOX = new byte[] { 31, 21, 27, 21, 31 };
        ICON_BELT = new byte[] { 14, 17, 0, 17, 14 };
        ICON_CHAIN = new byte[] { 10, 21, 10, 21, 10 };
        ICON_GEARSHIFT = new byte[] { 4, 12, 31, 17, 31 };
        ICON_GENERIC_SMALL = new byte[] { 31, 17, 21, 17, 31 };
        ICON_WATER_WHEEL = new byte[] { 28, 34, 73, 93, 73, 34, 28 };
        ICON_PRESS = new byte[] { 127, 0, 28, 8, 8, 28, 62 };
        ICON_STEAM_ENGINE = new byte[] { 8, 8, 62, 127, 127, 127, 62 };
        ICON_FAN = new byte[] { 127, 85, 107, 93, 107, 85, 127 };
        ICON_MOTOR = new byte[] { 14, 28, 56, 62, 14, 28, 56 };
        ICON_MIXER = new byte[] { 8, 8, 8, 62, 42, 20, 8 };
        ICON_DRILL = new byte[] { 62, 34, 28, 28, 8, 8, 8 };
        ICON_SAW = new byte[] { 28, 42, 73, 119, 73, 42, 28 };
        ICON_DEPLOYER = new byte[] { 0, 30, 16, 127, 16, 30, 0 };
        ICON_MILLSTONE = new byte[] { 28, 62, 127, 119, 127, 62, 28 };
        ICON_CRUSHING = new byte[] { 28, 54, 99, 65, 99, 54, 28 };
        ICON_WINDMILL = new byte[] { 24, 24, 24, 119, 12, 12, 12 };
        ICON_HAND_CRANK = new byte[] { 0, 30, 18, 30, 16, 16, 16 };
        ICON_FLYWHEEL = new byte[] { 28, 62, 99, 99, 99, 62, 28 };
        ICON_SPEED_CONTROLLER = new byte[] { 28, 34, 73, 77, 65, 34, 28 };
        ICON_GAUGE = new byte[] { 28, 34, 73, 81, 97, 34, 28 };
        ICON_GENERIC = new byte[] { 62, 65, 69, 10, 8, 0, 8 };
    }
}
