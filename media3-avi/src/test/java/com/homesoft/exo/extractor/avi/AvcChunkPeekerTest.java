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
import androidx.media3.common.MimeTypes;
import androidx.media3.test.utils.FakeExtractorInput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class AvcChunkPeekerTest {
  private static final Format.Builder FORMAT_BUILDER_AVC = new Format.Builder().
      setSampleMimeType(MimeTypes.VIDEO_H264).
      setWidth(1280).setHeight(720).setFrameRate(24000f/1001f);

  private static final byte[] P_SLICE = {0,0,0,1,0x41,(byte)0x9A,0x13,0x36,0x21,0x3A,0x5F,
      (byte)0xFE,(byte)0x9E,0x10,0,0};

  private FakeTrackOutput fakeTrackOutput;
  private AvcStreamHandler avcChunkHandler;

  @Before
  public void before() {
    fakeTrackOutput = new FakeTrackOutput(false);
    avcChunkHandler = new AvcStreamHandler(0, 10_000_000L, fakeTrackOutput,
        FORMAT_BUILDER_AVC);
    avcChunkHandler.setFps(DataHelper.FPS);
  }

  private void peekStreamHeader() throws IOException {
    final Context context = ApplicationProvider.getApplicationContext();
    final byte[] bytes =
        TestUtil.getByteArray(context,"media/avi/avc_sei_sps_pps_ird.dump");

    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(bytes).build();

    avcChunkHandler.peek(input, bytes.length);
  }

  @Test
  public void peek_givenStreamHeader() throws IOException {
    peekStreamHeader();
    Assert.assertEquals(64, avcChunkHandler.maxPicCount);
    Assert.assertEquals(0, avcChunkHandler.getSpsData().picOrderCountType);
    Assert.assertEquals(1.18f, fakeTrackOutput.lastFormat.pixelWidthHeightRatio, 0.01f);
  }

  @Test
  public void newChunk_givenStreamHeaderAndPSlice() throws IOException {
    peekStreamHeader();
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(P_SLICE).build();

    avcChunkHandler.setRead(0L, P_SLICE.length);
    avcChunkHandler.read(input);

    Assert.assertEquals(12, avcChunkHandler.lastPicCount);
  }
}
