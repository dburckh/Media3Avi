package com.homesoft.exo.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Used to parse Indexes and build the SeekMap
 * In this class offset is relative to baseOffset and position is absolute file position
 */
public class ChunkIndex {
    public static final int[] ALL_KEY_FRAMES = new int[0];
    private static final long[] RELEASED = new long[0];

    @NonNull
    @VisibleForTesting
    long[] positions = new long[8];
    boolean[] keys = new boolean[8];
    int keyFrameCount = 0;
    private int chunks = 0;

    /**
     * Add a chunk
     * @param key key frame
     */
    public void add(long position, boolean key) {
        if (positions.length <= chunks) {
            checkReleased();
            grow();
        }
        this.positions[chunks] = position;
        if (key) {
            this.keys[chunks] = true;
            keyFrameCount++;
        }
        chunks++;
    }

    boolean isAllKeyFrames() {
        return keyFrameCount == chunks;
    }

    public int getChunkCount() {
        return chunks;
    }

    /**
     * Get the key frame indices
     * @return the key frame indices or {@link #ALL_KEY_FRAMES}
     */
    public int[] getKeyFrameSubset() {
        checkReleased();
        if (isAllKeyFrames()) {
            return ALL_KEY_FRAMES;
        } else {
            final int[] keyFrames = new int[keyFrameCount];
            int i=0;
            for (int f = 0; f < chunks; f++) {
                if (keys[f]) {
                    keyFrames[i++] = f;
                }
            }
            return keyFrames;
        }
    }

    /**
     * Used for creating the SeekMap
     * @param seekPositions array of positions, usually key frame positions of another stream
     * @return the chunk indices at or before the position.
     *         Special Cases:
     *         0 if before this stream has not started
     *         chunkCount - 1 if there are no more chunks in the stream
     */
    public int[] getIndices(final long[] seekPositions) {
        checkReleased();

        final int[] work = new int[seekPositions.length];
        int i = -1;
        final int maxI = getChunkCount() - 1;
        for (int p=0;p<seekPositions.length;p++) {
            while (i < maxI && positions[i + 1] < seekPositions[p]) {
                i++;
            }
            work[p] = i;
        }
        return work;
    }

    /**
     * Build a subset of key frame indices given the stream duration and a key frames per second
     * Useful for generating a sparse SeekMap if all frames are key frames
     * @param durationUs stream length in Us
     * @param keyFrameRate secs between key frames
     */
    public int[] getKeyFrameSubset(final long durationUs, final int keyFrameRate) {
        checkReleased();
        final long frameDurUs = durationUs / chunks;
        final long keyFrameRateUs = keyFrameRate * 1_000_000L;
        final int[] work = new int[chunks]; //This is overkill, but keeps the logic simple.
        long clockUs = 0;
        long nextKeyFrameUs = 0;
        int k = 0;
        for (int f = 0; f < chunks; f++) {
            if (clockUs >= nextKeyFrameUs) {
                work[k++] = f;
                nextKeyFrameUs += keyFrameRateUs;
            }
            clockUs+= frameDurUs;
        }
        return Arrays.copyOf(work, k);
    }

    /**
     * Get the positions for an array of indices
     */
    public long[] getIndexPositions(int[] indices) {
        checkReleased();
        long[] positions = new long[indices.length];
        for (int k=0;k<indices.length;k++) {
            positions[k] = this.positions[indices[k]];
        }
        return positions;
    }

    private void checkReleased() {
        if (positions == RELEASED) {
            throw new IllegalStateException("ChunkIndex released.");
        }
    }

    /**
     * Release the arrays at this point only getChunks() and isAllKeyFrames() are allowed
     */
    public void release() {
        positions = RELEASED;
        keys = new boolean[0];
    }

    private void grow() {
        int newLength = positions.length +  Math.max(positions.length /4, 1);
        positions = Arrays.copyOf(positions, newLength);
        keys = Arrays.copyOf(keys, newLength);
    }
}
