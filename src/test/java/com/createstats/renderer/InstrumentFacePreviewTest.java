package com.createstats.renderer;

import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.PduMode;
import com.createstats.util.PreviewImages;
import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

class InstrumentFacePreviewTest {
    private static final Path OUT = Path.of("build/face_preview");

    private static void write(String name, NativeImage image) throws Exception {
        Files.createDirectories(OUT);
        final BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int abgr = image.getPixelRGBA(x, y);
                final int a = (abgr >>> 24) & 0xFF;
                final int b = (abgr >> 16) & 0xFF;
                final int g = (abgr >> 8) & 0xFF;
                final int r = abgr & 0xFF;
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        PreviewImages.write(out, "PNG", new File(OUT.resolve(name + ".png").toString()));
        System.out.println("wrote " + OUT.resolve(name + ".png") + " " + image.getWidth() + "x"
                + image.getHeight());
    }

    private static NativeImage blank(int w, int h) {
        return new NativeImage(w, h, false);
    }

    @Test
    void writeEveryFaceState() throws Exception {
        final int pw = Math.round(0.625f * 256);
        final int ph = Math.round(0.5f * 256);
        for (Object[] c : new Object[][]{
                {"pdu_auto", PduMode.AUTO, 40_000, 0.62f, new int[]{2, 1, 3, 0, 1},
                        new int[]{2, 1, 3, 0, 1}, 6, 0},
                {"pdu_auto_shed", PduMode.AUTO, 40_000, 0.22f, new int[]{2, 1, 3, 2, 1},
                        new int[]{2, 1, 1, 0, 0}, 4, 5},
                {"pdu_manual", PduMode.MANUAL, 40_000, 0.9f, new int[]{2, 1, 3, 0, 1},
                        new int[]{2, 1, 3, 0, 1}, 7, 0},
                {"pdu_no_input", PduMode.OFF, 0, 0.0f, new int[]{2, 1, 3, 0, 1},
                        new int[]{0, 0, 0, 0, 0}, 0, 0}}) {
            final NativeImage img = blank(pw, ph);
            final var face = new PowerDistributionUnitTextureManager.Face((PduMode) c[1],
                    (int) c[2], (float) c[3], 0.25f, 3_400, 40, 4_100, 3_600,
                    (int[]) c[4], (int[]) c[5], (int) c[6], (int) c[7]);
            PowerDistributionUnitTextureManager.draw(
                    new FaceCanvas(img, PowerDistributionUnitRenderer.SCREEN_L,
                            PowerDistributionUnitRenderer.GLASS_B,
                            PowerDistributionUnitRenderer.SCREEN_R,
                            PowerDistributionUnitRenderer.SCREEN_T), face, 0L);
            write((String) c[0], img);
        }

        final int bw = Math.round(0.75f * 256);
        final int bh = Math.round(0.5625f * 256);
        final var r = BatteryControllerPlan.Reason.values();
        for (Object[] c : new Object[][]{
                {"bc_charging", 4_000_000, 62, 2_400, 3_200,
                        new BatteryControllerPlan.Reason[]{r[0], r[0], r[0], r[0], r[0], r[0]}},
                {"bc_mixed", 4_000_000, 18, -1_850, 940,
                        new BatteryControllerPlan.Reason[]{BatteryControllerPlan.Reason.SERVED,
                                BatteryControllerPlan.Reason.PARTIAL,
                                BatteryControllerPlan.Reason.FLOOR,
                                BatteryControllerPlan.Reason.OFF,
                                BatteryControllerPlan.Reason.IDLE,
                                BatteryControllerPlan.Reason.UNWIRED}},
                {"bc_no_storage", 0, 0, 0, -1,
                        new BatteryControllerPlan.Reason[]{r[0], r[0], r[0], r[0], r[0], r[0]}}}) {
            final NativeImage img = blank(bw, bh);
            final var face = new BatteryControllerTextureManager.Face((int) c[1], (int) c[2],
                    (int) c[3], (int) c[4], 12_400, 9_800,
                    (BatteryControllerPlan.Reason[]) c[5]);
            BatteryControllerTextureManager.draw(
                    new FaceCanvas(img, BatteryControllerRenderer.SCREEN_L,
                            BatteryControllerRenderer.SCREEN_B,
                            BatteryControllerRenderer.SCREEN_R,
                            BatteryControllerRenderer.SCREEN_T), face, 0L);
            write((String) c[0], img);
        }

        for (int[] wh : new int[][]{{1, 3}, {2, 3}, {3, 4}, {5, 5}}) {
            final NativeImage img = blank(Math.min(1024, wh[0] * 128), Math.min(1024, wh[1] * 128));
            final var face = new CapacitorBankTextureManager.Face(0.63f, 6_300_000, 10_000_000,
                    1_450, 1_450, 0, 4_820);
            CapacitorBankTextureManager.drawFace(new FaceCanvas(img, 0, 0, wh[0], wh[1]), face,
                    wh[0], wh[1]);
            write("bank_" + wh[0] + "x" + wh[1], img);
        }
    }
}
