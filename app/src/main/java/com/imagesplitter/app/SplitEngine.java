package com.imagesplitter.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Slices the source in source pixels. Region decoding avoids loading large prints all at once. */
final class SplitEngine {
    static final int ORIGINAL = 0, PX_750 = 750, PX_1080 = 1080, CUSTOM = -1;

    static int[] dimensions(ContentResolver resolver, Uri source) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (ParcelFileDescriptor fd = resolver.openFileDescriptor(source, "r")) {
            if (fd == null) throw new Exception("Image unavailable");
            BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, options);
        }
        if (options.outWidth < 2 || options.outHeight < 2) throw new Exception("Unsupported image");
        return new int[]{options.outWidth, options.outHeight};
    }

    static Rect partRect(int width, int height, boolean horizontal, int count, int index) {
        if (horizontal) {
            return new Rect(0, (int)((long)height * index / count),
                    width, (int)((long)height * (index + 1) / count));
        }
        return new Rect((int)((long)width * index / count), 0,
                (int)((long)width * (index + 1) / count), height);
    }

    static int[] outputDimensions(Rect part, int target) {
        int width = part.width(), height = part.height();
        if (target == ORIGINAL) return new int[]{width, height};
        float scale = (float)target / Math.max(width, height);
        return new int[]{Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale))};
    }

    static List<Uri> export(ContentResolver resolver, Uri source, int count, boolean horizontal,
                            int target, boolean hint, String folder) throws Exception {
        int[] size = dimensions(resolver, source);
        if ((horizontal ? size[1] : size[0]) < count) throw new Exception("Too many parts for this image");
        List<Uri> results = new ArrayList<>();
        try (ParcelFileDescriptor fd = resolver.openFileDescriptor(source, "r")) {
            if (fd == null) throw new Exception("Image unavailable");
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(fd.getFileDescriptor(), false);
            if (decoder == null) throw new Exception("Unsupported image");
            try {
                for (int i = 0; i < count; i++) {
                    Rect rect = partRect(size[0], size[1], horizontal, count, i);
                    int[] out = outputDimensions(rect, target);
                    if ((long)out[0] * out[1] > 48_000_000L)
                        throw new Exception("Output part exceeds 48 megapixels; choose a smaller size");
                    Bitmap sourcePart = decoder.decodeRegion(rect, new BitmapFactory.Options());
                    if (sourcePart == null) throw new Exception("Unable to decode part " + (i + 1));
                    Bitmap part = sourcePart;
                    if (out[0] != sourcePart.getWidth() || out[1] != sourcePart.getHeight()) {
                        part = Bitmap.createScaledBitmap(sourcePart, out[0], out[1], true);
                        sourcePart.recycle();
                    }
                    if (hint && count > 1) {
                        Bitmap mutable = part.copy(Bitmap.Config.ARGB_8888, true);
                        part.recycle();
                        part = mutable;
                        drawJoinHints(part, horizontal, i, count);
                    }
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME,
                            String.format(java.util.Locale.US, "ImageSplitter_%02d.png", i + 1));
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ImageSplitter/" + folder);
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) { part.recycle(); throw new Exception("Unable to save image"); }
                    results.add(uri);
                    try (OutputStream stream = resolver.openOutputStream(uri)) {
                        if (stream == null || !part.compress(Bitmap.CompressFormat.PNG, 100, stream))
                            throw new Exception("Unable to write image");
                    } finally { part.recycle(); }
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    resolver.update(uri, values, null, null);
                }
            } finally { decoder.recycle(); }
        } catch (Exception e) {
            for (Uri uri : results) resolver.delete(uri, null, null);
            throw e;
        }
        return results;
    }

    private static void drawJoinHints(Bitmap part, boolean horizontal, int index, int count) {
        Canvas canvas = new Canvas(part);
        Paint paint = new Paint(3);
        paint.setColor(0xFF2F80ED);
        paint.setStrokeWidth(Math.max(2, Math.min(part.getWidth(), part.getHeight()) / 350f));
        float offset = paint.getStrokeWidth() * 4;
        float tick = offset * 2;
        if (horizontal) {
            if (index > 0) for (float x : new float[]{offset, part.getWidth() - offset})
                canvas.drawLine(x, 0, x, tick, paint);
            if (index < count - 1) for (float x : new float[]{offset, part.getWidth() - offset})
                canvas.drawLine(x, part.getHeight(), x, part.getHeight() - tick, paint);
        } else {
            if (index > 0) for (float y : new float[]{offset, part.getHeight() - offset})
                canvas.drawLine(0, y, tick, y, paint);
            if (index < count - 1) for (float y : new float[]{offset, part.getHeight() - offset})
                canvas.drawLine(part.getWidth(), y, part.getWidth() - tick, y, paint);
        }
    }
}
