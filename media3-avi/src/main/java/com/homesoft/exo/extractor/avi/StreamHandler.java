/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.homesoft.exo.extractor.avi;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import androidx.media3.common.C;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.TrackOutput;

import java.io.IOException;
import java.util.Arrays;

/**
 * Handles chunk data from a given stream.
 * This acts a bridge between AVI and ExoPlayer
 */
public class StreamHandler implements IReader {

  public static final int TYPE_VIDEO = ('d' << 16) | ('c' << 24);
  public static final int TYPE_AUDIO = ('w' << 16) | ('b' << 24);

  @NonNull
  ChunkClock clock;

  @NonNull
  final TrackOutput trackOutput;

  /**
   * The chunk id as it appears in the index and the movi
   */
  final int chunkId;

  /**
   * Secondary chunk id.  Bad muxers sometimes use uncompressed for key frames
   */
  final int chunkIdAlt;

  /**
   * Size total size of the stream in bytes calculated by the index
   */
  int size;

  final ChunkIndex chunkIndex = new ChunkIndex();

  /**
   * Ordered list of key frame chunk indexes
   */
  int[] keyFrames = new int[0];

  /**
   * Open DML IndexBox, currently we just support one, but multiple are possible
   * Will be null if non-exist or if processed (to ChunkIndex)
   */
  @Nullable
  private IndexBox indexBox;

  /**
   * Size of the current chunk in bytes
   */
  transient int readSize;
  /**
   * Bytes remaining in the chunk to be processed
   */
  transient int readRemaining;

  transient long readEnd;

  /**
   * Get stream id in ASCII
   */
  @VisibleForTesting
  static int getChunkIdLower(int id) {
    int tens = id / 10;
    int ones = id % 10;
    return  ('0' + tens) | (('0' + ones) << 8);
  }

  static int getId(int chunkId) {
    return ((chunkId >> 8) & 0xf) + (chunkId & 0xf) * 10;
  }

  StreamHandler(int id, int chunkType, @NonNull TrackOutput trackOutput, @NonNull ChunkClock clock) {
    this.chunkId = getChunkIdLower(id) | chunkType;
    this.clock = clock;
    this.trackOutput = trackOutput;
    if (isVideo()) {
      chunkIdAlt = getChunkIdLower(id) | ('d' << 16) | ('b' << 24);
    } else {
      chunkIdAlt = -1;
    }
  }

  /**
   *
   * @return true if this can handle the chunkId
   */
  public boolean handlesChunkId(int chunkId) {
    return this.chunkId == chunkId || chunkIdAlt == chunkId;
  }

  @NonNull
  public ChunkClock getClock() {
    return clock;
  }

  /**
   * Sets the list of key frames
   * @param keyFrames list of frame indexes or {@link ChunkIndex#ALL_KEY_FRAMES}
   */
  void setKeyFrames(@NonNull final int[] keyFrames) {
    this.keyFrames = keyFrames;
  }

  public boolean isKeyFrame() {
    return keyFrames == ChunkIndex.ALL_KEY_FRAMES || Arrays.binarySearch(keyFrames, clock.getIndex()) >= 0;
  }

  public boolean isVideo() {
    return (chunkId & TYPE_VIDEO) == TYPE_VIDEO;
  }

  public boolean isAudio() {
    return (chunkId & TYPE_AUDIO) == TYPE_AUDIO;
  }

  public long getPosition() {
    return readEnd - readRemaining;
  }

  public void setPosition(final long position, final int size) {
    readEnd = position + size;
    readRemaining = readSize = size;
  }

  protected boolean readComplete() {
    return readRemaining == 0;
  }

  /**
   * Resume a partial read of a chunk
   * May be called multiple times
   */
  public boolean read(@NonNull ExtractorInput input) throws IOException {
    readRemaining -= trackOutput.sampleData(input, readRemaining, false);
    if (readComplete()) {
      done(readSize);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Done reading a chunk.  Send the timing info and advance the clock
   * @param size the amount of data passed to the trackOutput
   */
  void done(final int size) {
    if (size > 0) {
      //System.out.println("Stream: " + getId() + " key: " + isKeyFrame() + " Us: " + clock.getUs() + " size: " + size);
      trackOutput.sampleMetadata(
          clock.getUs(), (isKeyFrame() ? C.BUFFER_FLAG_KEY_FRAME : 0), size, 0, null);
    }
    clock.advance();
  }

  /**
   * Gets the streamId.
   * @return The unique stream id for this file
   */
  public int getId() {
    return getId(chunkId);
  }

  /**
   * A seek occurred
   * @param index of the chunk
   */
  public void setIndex(int index) {
    getClock().setIndex(index);
  }

  @NonNull
  public ChunkIndex getChunkIndex() {
    return chunkIndex;
  }

  public IndexBox getIndexBox() {
    return indexBox;
  }

  public void setIndexBox(IndexBox indexBox) {
    this.indexBox = indexBox;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{position=" + getPosition() + "}";
  }
}
