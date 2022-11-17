package com.homesoft.exo.extractor.avi;

import org.junit.Assert;
import org.junit.Test;

public class ChunkIndexTest {
    @Test
    public void getIndices() {
        final long[] videoPositions = {10, 20, 30, 40, 50, 60, 70};
        final int[] audioOffsets = {7/*12*/, 14/*19*/, 28/*33*/, 42/*47*/};
        ChunkIndex chunkIndex = new ChunkIndex();
        chunkIndex.setBaseOffset(5);
        for (int i : audioOffsets) {
            chunkIndex.add(i, true);
        }

        final int[] audioIndices = chunkIndex.getIndices(videoPositions);
        Assert.assertEquals(audioIndices.length, videoPositions.length);
        // Special case where audio arrives after first frame
        Assert.assertEquals(-1, audioIndices[0]);
        for (int i=1;i<videoPositions.length;i++) {
            Assert.assertTrue(chunkIndex.getIndexPosition(audioIndices[i]) < videoPositions[i]);
        }
        // Special case where the audio stops before the last video frame
        Assert.assertEquals(audioOffsets.length - 1, audioIndices[audioIndices.length - 1]);
    }
}
