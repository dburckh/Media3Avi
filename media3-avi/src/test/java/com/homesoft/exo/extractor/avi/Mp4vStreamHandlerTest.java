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

import android.content.Context;

import androidx.media3.common.Format;
import androidx.media3.test.utils.FakeExtractorInput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
public class Mp4vStreamHandlerTest {
  private static final int VOP_TYPE_P = 1;

  private ByteBuffer makeSequence(int size) {
    return DataHelper.appendNal(AviExtractor.allocate(size), Mp4VStreamHandler.SEQUENCE_START_CODE);
  }

  private static Mp4VStreamHandler getStreamHandler() {
    final FakeTrackOutput fakeTrackOutput = new FakeTrackOutput(false);
    final Format.Builder formatBuilder = new Format.Builder();
    final Mp4VStreamHandler mp4vChunkPeeker = new Mp4VStreamHandler(0, 1_000_000L, fakeTrackOutput, formatBuilder);
    mp4vChunkPeeker.setFps(DataHelper.FPS);
    return mp4vChunkPeeker;
  }

  @Test
  public void peek_givenNoSequence() throws IOException {
    ByteBuffer byteBuffer = makeSequence(32);
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array())
            .build();
    Mp4VStreamHandler mp4VStreamHandler = getStreamHandler();
    mp4VStreamHandler.peek(input, (int) input.getLength());
    Assert.assertEquals(1f, mp4VStreamHandler.pixelWidthHeightRatio, 0.01);
  }

  @Test
  public void peek_givenAspectRatio() throws IOException {
    final Context context = ApplicationProvider.getApplicationContext();
    final byte[] bytes = TestUtil.getByteArray(context, "media/avi/mp4v_sequence.dump");
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(bytes).build();
    final Mp4VStreamHandler mp4vChunkPeeker = getStreamHandler();

    mp4vChunkPeeker.peek(input, (int) input.getLength());
    Assert.assertEquals(1.2121212, mp4vChunkPeeker.pixelWidthHeightRatio, 0.01);
  }

  private ByteBuffer createStreamHeader(int size) {
    ByteBuffer byteBuffer = makeSequence(size);
    byteBuffer.putInt(0x5555);
    DataHelper.appendNal(byteBuffer, (byte) Mp4VStreamHandler.LAYER_START_CODE);

    BitBuffer bitBuffer = new BitBuffer();
    bitBuffer.push(false); //random_accessible_vol
    bitBuffer.push(8, 8); //video_object_type_indication
    bitBuffer.push(true); // is_object_layer_identifier
    bitBuffer.push(7, 7); // video_object_layer_verid, video_object_layer_priority
    bitBuffer.push(4, Mp4VStreamHandler.Extended_PAR);
    bitBuffer.push(8, 16);
    bitBuffer.push(8, 9);
    bitBuffer.push(false); // vol_control_parameters
    bitBuffer.push(2, 0); // video_object_layer_shape
    bitBuffer.push(true); // marker_bit
    bitBuffer.push(16, 24); // vop_time_increment_resolution
    bitBuffer.push(true); // marker_bit

    byteBuffer.put(bitBuffer.toByteArray());
    return byteBuffer;
  }

  @Test
  public void peek_givenCustomAspectRatio() throws IOException {
    final ByteBuffer byteBuffer = createStreamHeader(32);

    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array())
        .build();
    final Mp4VStreamHandler mp4vChunkPeeker = getStreamHandler();
    mp4vChunkPeeker.peek(input, (int) input.getLength());
    Assert.assertEquals(16f/9f, mp4vChunkPeeker.pixelWidthHeightRatio, 0.01);
  }

  private int appendFrame(final ByteBuffer byteBuffer, int vopType, int modulo, int clock) {
    final int inPos = byteBuffer.position();
    DataHelper.appendNal(byteBuffer, Mp4VStreamHandler.VOP_START_CODE);
    final BitBuffer bitBuffer = new BitBuffer();
    bitBuffer.push(2, vopType);
    for (int i=0;i<modulo;i++) {
      bitBuffer.push(true);
    }
    bitBuffer.push(false);
    bitBuffer.push(true); //marker_bit
    bitBuffer.push(5, clock);

    byteBuffer.put(bitBuffer.toByteArray());
    return byteBuffer.position() - inPos;
  }

  @Test
  public void getTimeUs_givenBFrameStream() throws IOException {
    final ByteBuffer byteBuffer = createStreamHeader(1024);

    appendFrame(byteBuffer, VOP_TYPE_P, 0, 22);
    byteBuffer.position(64);
    appendFrame(byteBuffer, VOP_TYPE_P, 1, 1);
    byteBuffer.position(128);
    appendFrame(byteBuffer, Mp4VStreamHandler.VOP_TYPE_B, 0, 23);
    byteBuffer.position(128 + 64);
    appendFrame(byteBuffer, Mp4VStreamHandler.VOP_TYPE_B, 1, 0);

    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array())
            .build();

    final Mp4VStreamHandler mp4vChunkPeeker = getStreamHandler();
    mp4vChunkPeeker.setRead(0, 64);
    mp4vChunkPeeker.read(input);
    Assert.assertEquals(mp4vChunkPeeker.getChunkTimeUs(22), mp4vChunkPeeker.getTimeUs());
    mp4vChunkPeeker.setRead(64, 64);
    mp4vChunkPeeker.read(input);
    Assert.assertEquals(mp4vChunkPeeker.getChunkTimeUs(24 + 1), mp4vChunkPeeker.getTimeUs());
    mp4vChunkPeeker.setRead(128, 64);
    mp4vChunkPeeker.read(input);
    Assert.assertEquals(mp4vChunkPeeker.getChunkTimeUs(23), mp4vChunkPeeker.getTimeUs());

    mp4vChunkPeeker.setRead(128 + 64, 64);
    mp4vChunkPeeker.read(input);
    Assert.assertEquals(mp4vChunkPeeker.getChunkTimeUs(24), mp4vChunkPeeker.getTimeUs());
  }
}