package com.createstats.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;

public final class PreviewImages {
    private PreviewImages() {
    }

    public static boolean write(RenderedImage image, String format, File file) throws IOException {
        if (samePicture(image, file)) {
            return true;
        }
        return ImageIO.write(image, format, file);
    }

    static boolean samePicture(RenderedImage image, File file) {
        if (!file.isFile() || !(image instanceof BufferedImage fresh)) {
            return false;
        }
        final BufferedImage onDisk;
        try {
            onDisk = ImageIO.read(file);
        } catch (IOException | RuntimeException e) {
            return false;
        }
        if (onDisk == null || onDisk.getWidth() != fresh.getWidth() || onDisk.getHeight() != fresh.getHeight()) {
            return false;
        }
        final int w = fresh.getWidth();
        final int h = fresh.getHeight();
        final int[] a = fresh.getRGB(0, 0, w, h, null, 0, w);
        final int[] b = onDisk.getRGB(0, 0, w, h, null, 0, w);
        return java.util.Arrays.equals(a, b);
    }
}
