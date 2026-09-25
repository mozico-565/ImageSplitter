package com.imagesplitter.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Slices the source in source pixels. Region decoding avoids loading large prints all at once. */
final class SplitEngine {
    static final int ORIGINAL = 0, PX_750 = 750, PX_1080 = 1080, CUSTOM = -1;

    private static int[] rawDimensions(ContentResolver resolver, Uri source) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (ParcelFileDescriptor fd = resolver.openFileDescriptor(source, "r")) {
            if (fd == null) throw new Exception("Image unavailable");
            BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, options);
        }
        if (options.outWidth < 2 || options.outHeight < 2) throw new Exception("Unsupported image");
        return new int[]{options.outWidth, options.outHeight};
    }

    static int orientation(ContentResolver resolver, Uri source) {
        try (InputStream stream = resolver.openInputStream(source)) {
            if (stream == null) return ExifInterface.ORIENTATION_NORMAL;
            int value = new ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            return value < ExifInterface.ORIENTATION_NORMAL || value > ExifInterface.ORIENTATION_ROTATE_270
                    ? ExifInterface.ORIENTATION_NORMAL : value;
        } catch (Exception ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    static int[] dimensions(ContentResolver resolver, Uri source) throws Exception {
        int[] raw = rawDimensions(resolver, source);
        int orientation = orientation(resolver, source);
        if (swapsAxes(orientation)) return new int[]{raw[1], raw[0]};
        return raw;
    }

    static Bitmap loadPreview(ContentResolver resolver, Uri source, int maxSide) throws Exception {
        int[] raw = rawDimensions(resolver, source);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(raw[0], raw[1]) / options.inSampleSize > maxSide * 2) {
            options.inSampleSize *= 2;
        }
        Bitmap decoded;
        try (InputStream stream = resolver.openInputStream(source)) {
            if (stream == null) throw new Exception("Image unavailable");
            decoded = BitmapFactory.decodeStream(stream, null, options);
        }
        if (decoded == null) throw new Exception("Unable to read image");
        Bitmap oriented = orient(decoded, orientation(resolver, source));
        if (oriented != decoded) decoded.recycle();
        if (Math.max(oriented.getWidth(), oriented.getHeight()) <= maxSide) return oriented;
        float scale = maxSide / (float)Math.max(oriented.getWidth(), oriented.getHeight());
        Bitmap sampled = Bitmap.createScaledBitmap(oriented,
                Math.max(1, Math.round(oriented.getWidth() * scale)),
                Math.max(1, Math.round(oriented.getHeight() * scale)), true);
        if (sampled != oriented) oriented.recycle();
        return sampled;
    }

    static Rect partRect(int width, int height, boolean horizontal, int count, int index) {
        if (horizontal) {
            return new Rect(0, boundary(height, count, index),
                    width, boundary(height, count, index + 1));
        }
        return new Rect(boundary(width, count, index), 0,
                boundary(width, count, index + 1), height);
    }

    static int boundary(int length, int count, int boundaryIndex) {
        if (length < 1 || count < 1 || boundaryIndex < 0 || boundaryIndex > count)
            throw new IllegalArgumentException("Invalid split boundary");
        return (int)((long)length * boundaryIndex / count);
    }

    static int[] outputDimensions(Rect part, int target) {
        return outputDimensions(part.width(), part.height(), target);
    }

    static int[] outputDimensions(int width, int height, int target) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Invalid image dimensions");
        if (target == ORIGINAL) return new int[]{width, height};
        float scale = (float)target / Math.max(width, height);
        return new int[]{Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale))};
    }

    /**
     * Calculates one output piece from a single shared scale and scaled-image boundary grid.
     * This makes all clean pieces join back without gaps, overlaps or independently stretched strips.
     */
    static int[] outputPartDimensions(int width, int height, boolean horizontal,
                                      int count, int index, int target) {
        if (target == ORIGINAL) {
            int partWidth = horizontal ? width : boundary(width, count, index + 1) - boundary(width, count, index);
            int partHeight = horizontal ? boundary(height, count, index + 1) - boundary(height, count, index) : height;
            return new int[]{partWidth, partHeight};
        }
        int maxPartLongest = 1;
        for (int i = 0; i < count; i++) {
            int partWidth = horizontal ? width : boundary(width, count, i + 1) - boundary(width, count, i);
            int partHeight = horizontal ? boundary(height, count, i + 1) - boundary(height, count, i) : height;
            maxPartLongest = Math.max(maxPartLongest, Math.max(partWidth, partHeight));
        }
        float scale = target / (float)maxPartLongest;
        int scaledWidth = Math.max(1, Math.round(width * scale));
        int scaledHeight = Math.max(1, Math.round(height * scale));
        int partWidth = horizontal ? scaledWidth : boundary(scaledWidth, count, index + 1) - boundary(scaledWidth, count, index);
        int partHeight = horizontal ? boundary(scaledHeight, count, index + 1) - boundary(scaledHeight, count, index) : scaledHeight;
        return new int[]{partWidth, partHeight};
    }

    static List<Uri> export(ContentResolver resolver, Uri source, int count, boolean horizontal,
                            int target, boolean hint, int accentColor, String folder) throws Exception {
        return export(resolver, source, count, horizontal, target, hint, accentColor, folder,
                null, null);
    }

    interface Progress { void onPart(int current, int total); void onFallback(); }

    static List<Uri> export(ContentResolver resolver, Uri source, int count, boolean horizontal,
                            int target, boolean hint, int accentColor, String folder,
                            AiUpscaler upscaler, Progress progress) throws Exception {
        int[] size = dimensions(resolver, source);
        int[] rawSize = rawDimensions(resolver, source);
        int orientation = orientation(resolver, source);
        if ((horizontal ? size[1] : size[0]) < count) throw new Exception("Too many parts for this image");
        List<Uri> results = new ArrayList<>();
        try (ParcelFileDescriptor fd = resolver.openFileDescriptor(source, "r")) {
            if (fd == null) throw new Exception("Image unavailable");
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(fd.getFileDescriptor(), false);
            if (decoder == null) throw new Exception("Unsupported image");
            try {
                for (int i = 0; i < count; i++) {
                    if (progress != null) progress.onPart(i + 1, count);
                    Rect rect = partRect(size[0], size[1], horizontal, count, i);
                    int[] out = outputPartDimensions(size[0], size[1], horizontal, count, i, target);
                    if ((long)out[0] * out[1] > 48_000_000L)
                        throw new Exception("Output part exceeds 48 megapixels; choose a smaller size");
                    Rect rawRect = toRawRect(rect, rawSize[0], rawSize[1], orientation);
                    Bitmap decoded = decoder.decodeRegion(rawRect, new BitmapFactory.Options());
                    Bitmap sourcePart = decoded == null ? null : orient(decoded, orientation);
                    if (sourcePart != null && sourcePart != decoded) decoded.recycle();
                    if (sourcePart == null) throw new Exception("Unable to decode part " + (i + 1));
                    Bitmap part = sourcePart;
                    if (out[0] != sourcePart.getWidth() || out[1] != sourcePart.getHeight()) {
                        part = Bitmap.createScaledBitmap(sourcePart, out[0], out[1], true);
                        sourcePart.recycle();
                    }
                    if (upscaler != null) {
                        try {
                            Bitmap enhanced = upscaler.enhance(part);
                            part.recycle();
                            part = enhanced;
                        } catch (Exception | OutOfMemoryError failure) {
                            if (progress != null) progress.onFallback();
                            part.recycle();
                            throw new AiUpscaler.Failed(failure);
                        }
                    }
                    if (hint && count > 1) {
                        Bitmap mutable = part.copy(Bitmap.Config.ARGB_8888, true);
                        part.recycle();
                        part = mutable;
                        drawJoinHints(part, horizontal, i, count, accentColor);
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

    private static boolean swapsAxes(int orientation) {
        return orientation == ExifInterface.ORIENTATION_TRANSPOSE
                || orientation == ExifInterface.ORIENTATION_ROTATE_90
                || orientation == ExifInterface.ORIENTATION_TRANSVERSE
                || orientation == ExifInterface.ORIENTATION_ROTATE_270;
    }

    /** Maps an oriented-image rectangle back to the encoded pixel rectangle. */
    private static Rect toRawRect(Rect r, int w, int h, int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                return new Rect(w - r.right, r.top, w - r.left, r.bottom);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return new Rect(w - r.right, h - r.bottom, w - r.left, h - r.top);
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                return new Rect(r.left, h - r.bottom, r.right, h - r.top);
            case ExifInterface.ORIENTATION_TRANSPOSE:
                return new Rect(r.top, r.left, r.bottom, r.right);
            case ExifInterface.ORIENTATION_ROTATE_90:
                return new Rect(r.top, h - r.right, r.bottom, h - r.left);
            case ExifInterface.ORIENTATION_TRANSVERSE:
                return new Rect(w - r.bottom, h - r.right, w - r.top, h - r.left);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return new Rect(w - r.bottom, r.left, w - r.top, r.right);
            default:
                return new Rect(r);
        }
    }

    private static Bitmap orient(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f); break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setScale(1f, -1f); break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f); matrix.postScale(-1f, 1f); break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f); break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f); matrix.postScale(-1f, 1f); break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f); break;
            default:
                return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static void drawJoinHints(Bitmap part, boolean horizontal, int index, int count, int accentColor) {
        Canvas canvas = new Canvas(part);
        Paint paint = new Paint(3);
        paint.setColor(accentColor);
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
