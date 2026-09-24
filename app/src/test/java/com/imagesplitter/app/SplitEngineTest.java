package com.imagesplitter.app;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SplitEngineTest {
    @Test public void unevenBoundariesCoverEveryPixelExactlyOnce() {
        int length = 1003;
        int previous = 0;
        int total = 0;
        for (int i = 1; i <= 20; i++) {
            int next = SplitEngine.boundary(length, 20, i);
            assertEquals(previous, SplitEngine.boundary(length, 20, i - 1));
            total += next - previous;
            previous = next;
        }
        assertEquals(length, total);
        assertEquals(length, previous);
    }

    @Test public void outputLongestSideIsExactAndAspectRatioIsPreserved() {
        assertArrayEquals(new int[]{1080, 675},
                SplitEngine.outputDimensions(1600, 1000, 1080));
        assertArrayEquals(new int[]{469, 750},
                SplitEngine.outputDimensions(1000, 1600, 750));
        assertArrayEquals(new int[]{321, 123},
                SplitEngine.outputDimensions(321, 123, SplitEngine.ORIGINAL));
    }

    @Test public void resizedPiecesStillRecombineToOneExactGrid() {
        int totalHeight = 0;
        int commonWidth = -1;
        for (int i = 0; i < 3; i++) {
            int[] part = SplitEngine.outputPartDimensions(1600, 3001, true, 3, i, 1080);
            if (commonWidth < 0) commonWidth = part[0];
            assertEquals(commonWidth, part[0]);
            totalHeight += part[1];
        }
        assertEquals(1080, commonWidth);
        assertEquals(Math.round(3001 * (1080f / 1600f)), totalHeight);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidBoundary() {
        SplitEngine.boundary(100, 0, 0);
    }
}
