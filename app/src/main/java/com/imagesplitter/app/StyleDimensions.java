package com.imagesplitter.app;

/** FLUX output dimensions are multiples of 16; never crop an input to fit them. */
final class StyleDimensions {
    static int[] output(int width, int height) {
        if (width < 2 || height < 2) throw new IllegalArgumentException("Invalid image");
        double ratio = Math.max(width, height) / (double) Math.min(width, height);
        if (ratio > 7.5) throw new IllegalArgumentException("Image too panoramic");
        double scale = Math.max(1024.0 / Math.max(width, height), 256.0 / Math.min(width, height));
        int w = Math.min(1920, Math.max(256, (int)Math.round(width * scale / 16) * 16));
        int h = Math.min(1920, Math.max(256, (int)Math.round(height * scale / 16) * 16));
        return new int[]{w, h};
    }
}
