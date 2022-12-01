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

import androidx.media3.extractor.ExtractorInput;
import androidx.media3.test.utils.FakeTrackOutput;

import java.io.IOException;

public class MockNalStreamHandler extends NalStreamHandler {
  private boolean skip;
  public MockNalStreamHandler(int peakSize, boolean skip) {
    super(0, 1_000_000L, new FakeTrackOutput(false), peakSize);
    frameUs = DataHelper.VIDEO_US;
    this.skip = skip;
  }

  @Override
  void processChunk(ExtractorInput input, int nalTypeOffset) throws IOException {

  }

  @Override
  boolean skip(byte nalType) {
    return skip;
  }
}
