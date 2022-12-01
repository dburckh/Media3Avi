package com.homesoft.exo.extractor.avi;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.extractor.TrackOutput;

public class AudioStreamHandler extends StreamHandler {

    private long calcTimeUs(long streamPosition) {
        return durationUs * streamPosition / chunkIndex.getSize();
    }

    AudioStreamHandler(int id, long durationUs, @NonNull TrackOutput trackOutput) {
        super(id, TYPE_AUDIO, durationUs, trackOutput);
    }

    protected void advanceTime(int sampleSize) {
        timeUs += calcTimeUs(sampleSize);
    }

    @Override
    public void setTimeUs(long timeUs) {

        super.setTimeUs(timeUs);
    }

    @Override
    protected void sendMetadata(int size) {
        if (size > 0) {
            System.out.println("AudioStream: " + getId() + " Us: " + getTimeUs() + " size: " + size);
            trackOutput.sampleMetadata(
                    getTimeUs(), C.BUFFER_FLAG_KEY_FRAME, size, 0, null);
        }
        advanceTime(size);
    }

    private void setSeekFrames(int[] seekFrameIndices) {
        setSeekPointSize(seekFrameIndices.length);
        final int chunks = chunkIndex.getChunkCount();

        int k = 0;
        long streamBytes = 0;
        for (int c=0;c<chunks;c++) {
            if (seekFrameIndices[k] == c) {
                positions[k] = chunkIndex.getChunkPosition(c);
                times[k] = calcTimeUs(streamBytes);
                k++;
            }
            streamBytes += chunkIndex.getChunkSize(c);
        }
        chunkIndex.release();
    }

    @Override
    public long[] setSeekStream() {
        setSeekFrames(chunkIndex.getChunkSubset(durationUs, 3));
        return positions;
    }

    public void setSeekFrames(long[] positions) {
        setSeekFrames(chunkIndex.getIndices(positions));
    }
}
