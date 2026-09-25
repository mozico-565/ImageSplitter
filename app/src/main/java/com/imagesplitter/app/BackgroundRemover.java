package com.imagesplitter.app;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/** Offline DeepLabV3 foreground segmentation. Produces an alpha mask for transparent PNG export. */
final class BackgroundRemover implements AutoCloseable {
    private static final int SIZE = 257;
    private static final int CLASSES = 21;
    private final Interpreter interpreter;
    private final float[][][][] input = new float[1][SIZE][SIZE][3];
    private final float[][][][] output = new float[1][SIZE][SIZE][CLASSES];
    private final int[] pixels = new int[SIZE * SIZE];
    private final int[] maskPixels = new int[SIZE * SIZE];

    BackgroundRemover(AssetManager assets) throws Exception {
        try (AssetFileDescriptor fd = assets.openFd("deeplabv3_257_mv_gpu.tflite");
             FileInputStream stream = new FileInputStream(fd.getFileDescriptor());
             FileChannel channel = stream.getChannel()) {
            MappedByteBuffer model = channel.map(FileChannel.MapMode.READ_ONLY,
                    fd.getStartOffset(), fd.getDeclaredLength());
            interpreter = new Interpreter(model, new Interpreter.Options().setNumThreads(2));
        }
        int[] in = interpreter.getInputTensor(0).shape();
        int[] out = interpreter.getOutputTensor(0).shape();
        if (in.length != 4 || in[1] != SIZE || in[2] != SIZE || in[3] != 3
                || out.length != 4 || out[1] != SIZE || out[2] != SIZE
                || out[3] != CLASSES) {
            interpreter.close();
            throw new IllegalStateException("Unexpected DeepLabV3 tensor dimensions");
        }
    }

    Bitmap createMask(Bitmap source) {
        Bitmap scaled = Bitmap.createScaledBitmap(source, SIZE, SIZE, true);
        try {
            scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
            int index = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int color = pixels[index++];
                    input[0][y][x][0] = (Color.red(color) - 127.5f) / 127.5f;
                    input[0][y][x][1] = (Color.green(color) - 127.5f) / 127.5f;
                    input[0][y][x][2] = (Color.blue(color) - 127.5f) / 127.5f;
                }
            }
            interpreter.run(input, output);
            int foreground = 0;
            index = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    float[] scores = output[0][y][x];
                    int bestClass = 0;
                    float bestScore = scores[0];
                    for (int c = 1; c < CLASSES; c++) {
                        if (scores[c] > bestScore) {
                            bestScore = scores[c];
                            bestClass = c;
                        }
                    }
                    boolean keep = bestClass != 0;
                    if (keep) foreground++;
                    maskPixels[index++] = keep ? Color.WHITE : Color.TRANSPARENT;
                }
            }
            if (foreground < SIZE * SIZE / 200)
                throw new IllegalStateException("No foreground subject was detected");
            Bitmap mask = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            mask.setPixels(maskPixels, 0, SIZE, 0, 0, SIZE, SIZE);
            return mask;
        } finally {
            if (scaled != source) scaled.recycle();
        }
    }

    @Override public void close() { interpreter.close(); }
}
