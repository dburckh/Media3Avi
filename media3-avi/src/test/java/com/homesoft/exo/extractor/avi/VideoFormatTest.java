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

import androidx.media3.common.MimeTypes;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class VideoFormatTest {
  @Test
  public void getters_givenVideoStreamFormat() throws IOException {
    final StreamFormatBox streamFormatBox = DataHelper.getVideoStreamFormat();
    final VideoFormat videoFormat = streamFormatBox.getVideoFormat();
    Assert.assertEquals(720, videoFormat.getWidth());
    Assert.assertEquals(480, videoFormat.getHeight());
    Assert.assertEquals(MimeTypes.VIDEO_MP4V, videoFormat.getMimeType());
  }
}
