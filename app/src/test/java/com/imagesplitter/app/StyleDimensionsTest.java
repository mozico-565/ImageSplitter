package com.imagesplitter.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class StyleDimensionsTest {
    @Test public void landscapeAndPortraitPreserveComposition() {
        assertArrayEquals(new int[]{1024,688},StyleDimensions.output(900,600));
        assertArrayEquals(new int[]{688,1024},StyleDimensions.output(600,900));
        assertArrayEquals(new int[]{1024,1024},StyleDimensions.output(500,500));
    }
    @Test public void panoramicOutputRespectsMinimumAndMaximum() {
        assertArrayEquals(new int[]{1920,256},StyleDimensions.output(7500,1000));
        assertArrayEquals(new int[]{256,1920},StyleDimensions.output(1000,7500));
    }
    @Test(expected=IllegalArgumentException.class) public void neverCropUnsupportedPanorama() {
        StyleDimensions.output(8000,1000);
    }
}
