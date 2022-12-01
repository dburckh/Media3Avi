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

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import androidx.media3.common.C;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.MpegAudioUtil;
import androidx.media3.extractor.TrackOutput;

import java.io.IOException;

/**
 * This is an MP3 Extractor within the AviExtractor
 *
 * Resolves several issues with Mpeg Audio
 * 1. That muxers don't always mux MPEG audio on the frame boundary
 * 2. That some codecs can't handle multiple or partial frames (Pixels)
 */
public class MpegAudioStreamHandler extends AudioStreamHandler {
  private final MpegAudioUtil.Header header = new MpegAudioUtil.Header();
  private final ParsableByteArray scratch = new ParsableByteArray(8);
  private final int samplesPerSecond;
  /**
   *  Bytes remaining in the Mpeg Audio frame
   *  This includes bytes in both the scratch buffer and the stream
   *  0 means we are seeking a new frame
   */
  private int frameRemaining = 0;

  MpegAudioStreamHandler(int id, long durationUs, @NonNull TrackOutput trackOutput,
                         int samplesPerSecond) {
    super(id, durationUs, trackOutput);
    this.samplesPerSecond = samplesPerSecond;
  }

  @Override
  protected void advanceTime(int size) {
    timeUs += header.samplesPerFrame * C.MICROS_PER_SECOND / samplesPerSecond;
  }

  @Override
  public boolean read(@NonNull ExtractorInput input) throws IOException {
    if (readSize == 0) {
      //TODO: How to handle empty frame?
      return true;
    }
    if (frameRemaining == 0) {
      //Find the next frame
      if (!findFrame(input)) {
        if (scratch.limit() >= readSize) {
          scratch.setPosition(0);
          trackOutput.sampleData(scratch, readSize);
          scratch.reset(0);
          sendMetadata(readSize);
        }
        return readComplete();
      }
    }
    int scratchBytes = scratch.bytesLeft();
    if (scratchBytes > 0) {
//      System.out.println("SampleData-scratch: " + scratchBytes);
      trackOutput.sampleData(scratch, scratchBytes);
      frameRemaining -= scratchBytes;
      scratch.reset(0);
    }

//    System.out.println("SampleData-input : " + Math.min(frameRemaining, readRemaining));
    final int bytes = trackOutput.sampleData(input, Math.min(frameRemaining, readRemaining), false);
    frameRemaining -= bytes;
    if (frameRemaining == 0) {
      sendMetadata(header.frameSize);
    }
    readRemaining -= bytes;
    return readComplete();
  }

  /**
   * Soft read from input to scratch
   * @param bytes to attempt to read
   * @return {@link C#RESULT_END_OF_INPUT} or number of bytes read.
   */
  int readScratch(ExtractorInput input, int bytes) throws IOException {
    final int toRead = Math.min(bytes, readRemaining);
    scratch.ensureCapacity(scratch.limit() + toRead);
    final int read = input.read(scratch.getData(), scratch.limit(), toRead);
    if (read == C.RESULT_END_OF_INPUT) {
      return read;
    }
    readRemaining -= read;
    scratch.setLimit(scratch.limit() + read);
    return read;
  }

  /**
   * Attempt to find a frame header in the input
   * @return true if a frame header was found
   */
  @VisibleForTesting
  boolean findFrame(ExtractorInput input) throws IOException {
    int toRead = 4;
    while (readRemaining > 0 && readScratch(input, toRead) != C.RESULT_END_OF_INPUT) {
      while (scratch.bytesLeft() >= 4) {
        if (header.setForHeaderData(scratch.readInt())) {
          scratch.skipBytes(-4);
          frameRemaining = header.frameSize;
          return true;
        }
        scratch.skipBytes(-3);
      }
      // 16 is small, but if we end up reading multiple frames into scratch, things get complicated.
      // We should only loop on seek, so this is the lesser of the evils.
      toRead = Math.min(readRemaining, 16);
    }
    return false;
  }

  private void reset() {
    scratch.reset(0);
    frameRemaining = 0;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  long getTimeUs() {
    return timeUs;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  int getFrameRemaining() {
    return frameRemaining;
  }
}
