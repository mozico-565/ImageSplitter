package com.imagesplitter.app;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/** Local Real-ESRGAN x4v3 inference, resampled to the requested 2x output. */
final class AiUpscaler implements AutoCloseable {
    static final class Failed extends Exception { Failed(Throwable cause) { super(cause); } }
    private static final int TILE = 128, OVERLAP = 24, STEP = TILE - OVERLAP;
    private final Interpreter interpreter;
    private final float[][][][] input = new float[1][TILE][TILE][3];
    private final float[][][][] output = new float[1][TILE * 4][TILE * 4][3];
    private final int[] pixels = new int[TILE * TILE];
    private final int[] upscaledPixels = new int[TILE * 4 * TILE * 4];
    private final Bitmap tile = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888);
    private final Bitmap tileOutput = Bitmap.createBitmap(TILE * 4, TILE * 4, Bitmap.Config.ARGB_8888);

    AiUpscaler(AssetManager assets) throws Exception {
        try (AssetFileDescriptor fd = assets.openFd("real_esrgan_x4v3.tflite");
             FileInputStream stream = new FileInputStream(fd.getFileDescriptor());
             FileChannel channel = stream.getChannel()) {
            MappedByteBuffer model = channel.map(FileChannel.MapMode.READ_ONLY,
                    fd.getStartOffset(), fd.getDeclaredLength());
            Interpreter.Options options = new Interpreter.Options().setNumThreads(2);
            interpreter = new Interpreter(model, options);
        }
        int[] a = interpreter.getInputTensor(0).shape();
        int[] b = interpreter.getOutputTensor(0).shape();
        if (a.length != 4 || a[1] != TILE || a[2] != TILE || a[3] != 3 ||
                b.length != 4 || b[1] != TILE * 4 || b[2] != TILE * 4 || b[3] != 3) {
            interpreter.close();
            throw new IllegalStateException("Unexpected Real-ESRGAN tensor dimensions");
        }
    }

    Bitmap enhance(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
        // Keep peak memory bounded on medium devices; export uses the original piece on failure.
        if ((long) w * h > 2_500_000L || w > 4000 || h > 4000)
            throw new IllegalArgumentException("Image too large for on-device AI");
        Bitmap result = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888);
        try {
            Canvas resultCanvas = new Canvas(result);
            Paint smooth = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            for (int y = 0; y < h; y += STEP) {
                for (int x = 0; x < w; x += STEP) {
                    int originX = Math.max(0, x - OVERLAP / 2);
                    int originY = Math.max(0, y - OVERLAP / 2);
                    // Edge replication protects model receptive fields at image boundaries.
                    for (int ty = 0; ty < TILE; ty++) {
                        int sy = Math.min(h - 1, originY + ty);
                        for (int tx = 0; tx < TILE; tx++) {
                            pixels[ty * TILE + tx] = source.getPixel(
                                    Math.min(w - 1, originX + tx), sy);
                        }
                    }
                    tile.setPixels(pixels, 0, TILE, 0, 0, TILE, TILE);
                    int n = 0;
                    for (int ty = 0; ty < TILE; ty++) for (int tx = 0; tx < TILE; tx++) {
                        int color = pixels[n++];
                        input[0][ty][tx][0] = Color.red(color) / 255f;
                        input[0][ty][tx][1] = Color.green(color) / 255f;
                        input[0][ty][tx][2] = Color.blue(color) / 255f;
                    }
                    interpreter.run(input, output);
                    n = 0;
                    for (int ty = 0; ty < TILE * 4; ty++) for (int tx = 0; tx < TILE * 4; tx++) {
                        float[] rgb = output[0][ty][tx];
                        upscaledPixels[n++] = Color.rgb(channel(rgb[0]), channel(rgb[1]), channel(rgb[2]));
                    }
                    tileOutput.setPixels(upscaledPixels, 0, TILE * 4, 0, 0, TILE * 4, TILE * 4);
                    int endX = Math.min(w, x + STEP), endY = Math.min(h, y + STEP);
                    Rect src = new Rect((x - originX) * 4, (y - originY) * 4,
                            (endX - originX) * 4, (endY - originY) * 4);
                    RectF dst = new RectF(x * 2, y * 2, endX * 2, endY * 2);
                    resultCanvas.drawBitmap(tileOutput, src, dst, smooth);
                }
            }
            return result;
        } catch (RuntimeException | OutOfMemoryError failure) {
            result.recycle();
            throw failure;
        }
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255f)));
    }

    @Override public void close() {
        interpreter.close();
        tile.recycle();
        tileOutput.recycle();
    }
}
