package com.homesoft.exo.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.extractor.TrackOutput;

import java.util.Arrays;

public class VideoStreamHandler extends StreamHandler {
    private long frameUs;
    protected int index;
    private boolean allKeyFrames;
    /**
     * Secondary chunk id.  Bad muxers sometimes use uncompressed for key frames
     */
    final int chunkIdAlt;

    VideoStreamHandler(int id, long durationUs, @NonNull TrackOutput trackOutput) {
        super(id, TYPE_VIDEO, durationUs, trackOutput);
        chunkIdAlt = getChunkIdLower(id) | ('d' << 16) | ('b' << 24);
    }

    public boolean handlesChunkId(int chunkId) {
        return super.handlesChunkId(chunkId) || chunkIdAlt == chunkId;
    }

    protected boolean isKeyFrame() {
        // -8 because the position array includes the header, but the read skips it.
        return allKeyFrames || Arrays.binarySearch(positions, readEnd - readSize - 8) >= 0;
    }

    protected void advanceTime() {
        index++;
    }

    @Override
    protected void sendMetadata(int size) {
        if (size > 0) {
            System.out.println("VideoStream: " + getId() + " Us: " + getTimeUs() + " size: " + size + " key: " + isKeyFrame());
            trackOutput.sampleMetadata(
                    getTimeUs(), (isKeyFrame() ? C.BUFFER_FLAG_KEY_FRAME : 0), size, 0, null);
        }
        advanceTime();
    }

    @Override
    public long[] setSeekStream() {
        final int[] seekFrameIndices;
        if (chunkIndex.isAllKeyFrames()) {
            allKeyFrames = true;
            seekFrameIndices = chunkIndex.getChunkSubset(durationUs, 3);
        } else {
            seekFrameIndices = chunkIndex.getChunkSubset();
        }
        final int frames = chunkIndex.getCount();
        frameUs = durationUs / frames;
        setSeekPointSize(seekFrameIndices.length);
        for (int i=0;i<seekFrameIndices.length;i++) {
            final int index = seekFrameIndices[i];
            positions[i] = chunkIndex.getChunkPosition(index);
            times[i] = durationUs * index / frames;
        }
        chunkIndex.release();
        return positions;
    }

    @Override
    public long getTimeUs() {
        return durationUs * index / chunkIndex.getCount();
    }

    @Override
    public void seekPosition(long position) {
        final int seekIndex = getSeekIndex(position);
        final long timeUs = times[seekIndex];
        index = (int)((timeUs + frameUs / 2) / frameUs);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public void setFps(int fps) {
        final int chunks = (int)(durationUs * fps / C.MICROS_PER_SECOND);
        chunkIndex.setCount(chunks);
        frameUs = C.MICROS_PER_SECOND / fps;
    }
}
