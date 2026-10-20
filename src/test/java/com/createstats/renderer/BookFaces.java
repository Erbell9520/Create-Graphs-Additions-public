package com.createstats.renderer;

import com.createstats.book.scene.BlockFigures;
import com.createstats.book.specimen.CarryFixtures;
import com.createstats.book.specimen.StoreFixtures;
import com.createstats.power.BatteryControllerPlan;
import com.createstats.util.PreviewImages;
import com.mojang.blaze3d.platform.NativeImage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BookFaces {
    private BookFaces() {
    }

    private static final int BANK_TEXELS = 128;
    private static final int BANK_MAX = 1024;

    private static final int CONTROLLER_TEXELS = 256;

    private static final int PDU_TEXELS = 256;

    public static void write(Path dir) throws IOException {
        Files.createDirectories(dir);

        final int side = BlockFigures.BANK_SIDE;
        final StoreFixtures.BankReading b = StoreFixtures.bank();
        final int px = Math.min(BANK_MAX, side * BANK_TEXELS);
        final NativeImage bank = new NativeImage(px, px, false);
        new FaceCanvas(bank, 0.0f, 0.0f, side, side).clear(0);
        CapacitorBankTextureManager.drawFace(new FaceCanvas(bank, 0.0f, 0.0f, side, side),
                new CapacitorBankTextureManager.Face(b.fill(), b.stored(), b.capacity(), b.rate(), b.in(), b.out(),
                        b.enduranceTicks(), false, -1, -1), side, side);
        save(bank, dir.resolve(StoreFixtures.BANK_FACE + ".png"));

        final StoreFixtures.ControllerReading c = StoreFixtures.controller();
        final int w = Math.round((BatteryControllerRenderer.SCREEN_R - BatteryControllerRenderer.SCREEN_L)
                * CONTROLLER_TEXELS);
        final int h = Math.round((BatteryControllerRenderer.SCREEN_T - BatteryControllerRenderer.SCREEN_B)
                * CONTROLLER_TEXELS);
        final NativeImage face = new NativeImage(w, h, false);
        BatteryControllerTextureManager.draw(new FaceCanvas(face, BatteryControllerRenderer.SCREEN_L,
                        BatteryControllerRenderer.SCREEN_B, BatteryControllerRenderer.SCREEN_R,
                        BatteryControllerRenderer.SCREEN_T),
                BatteryControllerTextureManager.face(c.capacity(), c.pct(), c.rate(), c.generation(), c.delivered(),
                        c.lamps().toArray(new BatteryControllerPlan.Reason[0]), c.outlook()), 0L);
        save(face, dir.resolve(StoreFixtures.CONTROLLER_FACE + ".png"));

        for (final String fixture : java.util.List.of(CarryFixtures.JUNCTION_CABLE, CarryFixtures.JUNCTION_WIRE)) {
            final CarryFixtures.JunctionReading r = CarryFixtures.junction(fixture);
            final NativeImage screen = new NativeImage(DiagnosticJunctionTextureManager.W,
                    DiagnosticJunctionTextureManager.H, false);
            DiagnosticJunctionTextureManager.draw(screen, new DiagnosticJunctionTextureManager.Face(r.wire(), r.flow(),
                    r.capacity(), r.buffer(), r.maxBuffer(), r.fault(), r.merged(), r.cutOff(),
                    r.channels().toArray(new com.createstats.blockentity.DiagnosticJunctionBE.Fault[0]),
                    r.channelFlow(), r.channelCapacity()));
            save(screen, dir.resolve(fixture + ".png"));
        }

        final com.createstats.blockentity.PowerDistributionUnitBE board = com.createstats.book.PduRig.board(false);
        final NativeImage pdu = new NativeImage(
                Math.round((PowerDistributionUnitRenderer.SCREEN_R - PowerDistributionUnitRenderer.SCREEN_L)
                        * PDU_TEXELS),
                Math.round((PowerDistributionUnitRenderer.SCREEN_T - PowerDistributionUnitRenderer.GLASS_B)
                        * PDU_TEXELS), false);
        PowerDistributionUnitTextureManager.draw(new FaceCanvas(pdu, PowerDistributionUnitRenderer.SCREEN_L,
                        PowerDistributionUnitRenderer.GLASS_B, PowerDistributionUnitRenderer.SCREEN_R,
                        PowerDistributionUnitRenderer.SCREEN_T), PowerDistributionUnitTextureManager.read(board), 0L);
        save(pdu, dir.resolve(com.createstats.book.specimen.PduPlates.FACE + ".png"));
    }

    private static void save(NativeImage image, Path file) throws IOException {
        final BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int abgr = image.getPixelRGBA(x, y);
                final int a = (abgr >>> 24) & 0xFF;
                final int bl = (abgr >> 16) & 0xFF;
                final int g = (abgr >> 8) & 0xFF;
                final int r = abgr & 0xFF;
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | bl);
            }
        }
        image.close();
        PreviewImages.write(out, "PNG", new File(file.toString()));
    }
}
