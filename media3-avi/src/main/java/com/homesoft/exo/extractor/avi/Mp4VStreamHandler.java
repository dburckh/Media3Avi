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

import androidx.media3.common.Format;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ParsableNalUnitBitArray;
import androidx.media3.extractor.TrackOutput;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Peeks an MP4V stream looking for pixelWidthHeightRatio data
 */
public class Mp4VStreamHandler extends NalStreamHandler {
  @VisibleForTesting
  static final byte SEQUENCE_START_CODE = (byte)0xb0;
  @VisibleForTesting
  static final byte VOP_START_CODE = (byte)0xb6;
  @VisibleForTesting
  static final int LAYER_START_CODE = 0x20;
  private static final float[] ASPECT_RATIO = {0f, 1f, 12f/11f, 10f/11f, 16f/11f, 40f/33f};

  private static final int CODING_TYPE_I = 0;
  private static final int CODING_TYPE_P = 1;
  private static final int CODING_TYPE_B = 2;
  private static final byte SIMPLE_PROFILE_MASK = 0b1111;
  @VisibleForTesting
  static final int Extended_PAR = 0xf;

  private final Format.Builder formatBuilder;

  @VisibleForTesting()
  float pixelWidthHeightRatio = 1f;
  boolean bFrames;
  int vopCodingType;
  private final ArrayList<Integer> sizeList = new ArrayList<>(3);
  int queuedBytes = 0;

  public Mp4VStreamHandler(int id, long durationUs, @NonNull TrackOutput trackOutput,
                           @NonNull Format.Builder formatBuilder) {
    super(id, durationUs, trackOutput, 5);
    this.formatBuilder = formatBuilder;
  }

  @Override
  boolean skip(byte nalType) {
    return nalType != SEQUENCE_START_CODE && (!bFrames || nalType != VOP_START_CODE);
  }

  private void queueFrame(int size) {
    sizeList.add(size);
    queuedBytes+= size;
  }

  private void sendQueuedMetadata(int sizeIndex, int chunkOffset) {
    final int size = sizeList.get(sizeIndex);
    queuedBytes -= size;
    trackOutput.sampleMetadata(getChunkTimeUs(index + chunkOffset - 1), 0, size,
            queuedBytes, null);
  }

  /**
   * Send all the metadata we've queued up
   */
  private void sendQueuedMetadata(int currentFrameSize) {
    if (sizeList.isEmpty()) {
      return;
    }
    // Need to add the bytes we just sent to calc the offset correctly
    queuedBytes += currentFrameSize;
    // Send the initial P frame in the future
    sendQueuedMetadata(0, sizeList.size());
    // Send the B-Frames in order
    for (int i=1;i<sizeList.size();i++) {
      sendQueuedMetadata(i, i);
    }
    //Advance the clock
    index += sizeList.size();
    reset();
  }

  private void reset() {
    sizeList.clear();
    queuedBytes = 0;
  }

  @Override
  public void seekPosition(long position) {
    super.seekPosition(position);
    reset();
  }

  @Override
  protected void sendMetadata(int size) {
    switch (vopCodingType) {
      case CODING_TYPE_I:
        sendQueuedMetadata(size);
        break;
      case CODING_TYPE_P:
        sendQueuedMetadata(size);
        queueFrame(size);
        return;
      case CODING_TYPE_B:
        queueFrame(size);
        return;
    }
    super.sendMetadata(size);
  }

  @VisibleForTesting
  void processLayerStart(int nalTypeOffset) {
    @NonNull final ParsableNalUnitBitArray in = new ParsableNalUnitBitArray(buffer, nalTypeOffset + 1, pos);
    in.skipBit(); // random_accessible_vol
    in.skipBits(8); // video_object_type_indication
    boolean is_object_layer_identifier = in.readBit();
    if (is_object_layer_identifier) {
      in.skipBits(7); // video_object_layer_verid, video_object_layer_priority
    }
    int aspect_ratio_info = in.readBits(4);
    final float aspectRatio;
    if (aspect_ratio_info == Extended_PAR) {
      float par_width = (float)in.readBits(8);
      float par_height = (float)in.readBits(8);
      aspectRatio = par_width / par_height;
    } else {
      aspectRatio = ASPECT_RATIO[aspect_ratio_info];
    }
    if (aspectRatio != pixelWidthHeightRatio) {
      trackOutput.format(formatBuilder.setPixelWidthHeightRatio(aspectRatio).build());
      pixelWidthHeightRatio = aspectRatio;
    }
  }

  @Override
  void processChunk(ExtractorInput input, int nalTypeOffset) throws IOException {
    while (true) {
      final byte nalType = buffer[nalTypeOffset];
      if (bFrames && nalType == VOP_START_CODE) {
        vopCodingType = (buffer[nalTypeOffset+1] & 0xc0) >> 6;
        break;
      } else if (nalType == SEQUENCE_START_CODE) {
        final byte profile_and_level_indication = buffer[nalTypeOffset + 1];
        bFrames = (profile_and_level_indication & SIMPLE_PROFILE_MASK) != profile_and_level_indication;
      } else if ((nalType & 0xf0) == LAYER_START_CODE) {
        seekNextNal(input, nalTypeOffset);
        processLayerStart(nalTypeOffset);
        // There may be a VOP start code after this NAL, so if we are tracking B frames, don't exit
        if (!bFrames) {
          break;
        }
      }

      nalTypeOffset = seekNextNal(input, nalTypeOffset);
      if (nalTypeOffset < 0) {
        break;
      }
      compact();
    }
  }
}
