package com.homesoft.exo.extractor.avi;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ChunkIndexTest {
    private static final long[] VIDEO_POSITIONS = {10, 20, 30, 40, 50, 60, 70};

    @Test
    public void getIndices() {
        final int[] audioOffsets = {6, 12, 19, 33, 47, 61, 75};
        ChunkIndex chunkIndex = new ChunkIndex();
        for (int i : audioOffsets) {
            chunkIndex.add(i, true);
        }

        final int[] audioIndices = chunkIndex.getIndices(VIDEO_POSITIONS);
        Assert.assertEquals(audioIndices.length, VIDEO_POSITIONS.length);
        for (int i=1;i<VIDEO_POSITIONS.length;i++) {
            Assert.assertTrue(chunkIndex.getIndexPosition(audioIndices[i]) >= VIDEO_POSITIONS[i]);
        }
        // Special case where the audio stops before the last video frame
        Assert.assertEquals(audioOffsets.length - 1, audioIndices[audioIndices.length - 1]);
    }

    @Test
    public void getKeyFrameSubset() {
        ChunkIndex chunkIndex = new ChunkIndex();
        final int secs = 10;
        for (int i=0;i<secs*24;i++) {
            chunkIndex.add(i * 3L, true);
        }
        final int[] indices = chunkIndex.getKeyFrameSubset(
                TimeUnit.MICROSECONDS.convert(secs, TimeUnit.SECONDS), 2);
        Assert.assertEquals(5, indices.length);
    }
}
