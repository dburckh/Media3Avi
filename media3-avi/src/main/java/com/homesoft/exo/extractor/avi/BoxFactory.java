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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Factory for Boxes.  These usually exist inside a ListBox
 */
public class BoxFactory {
  /**
   * Arbitrary number to keep VM from crashing
   */
  static final int MAX_BOX_SIZE = 64*1024;
  static int[] types = {AviHeaderBox.AVIH, StreamHeaderBox.STRH, StreamFormatBox.STRF, StreamNameBox.STRN, IndexBox.INDX};
  static {
    Arrays.sort(types);
  }

  public boolean isUnknown(final int type) {
    return Arrays.binarySearch(types, type) < 0;
  }

  private ResidentBox createBoxImpl(final int type, final int size, final ByteBuffer boxBuffer) {
    switch (type) {
      case AviHeaderBox.AVIH:
        return new AviHeaderBox(type, size, boxBuffer);
      case StreamHeaderBox.STRH:
        return new StreamHeaderBox(type, size, boxBuffer);
      case StreamFormatBox.STRF:
        return new StreamFormatBox(type, size, boxBuffer);
      case StreamNameBox.STRN:
        return new StreamNameBox(type, size, boxBuffer);
      case IndexBox.INDX:
        return new IndexBox(type, size, boxBuffer);
      default:
        return null;
    }
  }

  public ResidentBox createBox(final int type, final int size, ExtractorInput input) throws IOException {
    if (isUnknown(type)) {
      input.skipFully(size);
      return null;
    }
    if (size > MAX_BOX_SIZE) {
      throw new IOException("Box too big: " + AviExtractor.toString(type) + " " + size);
    }
    final ByteBuffer boxBuffer = AviExtractor.allocate(size);
    input.readFully(boxBuffer.array(),0,size);
    return createBoxImpl(type, size, boxBuffer);
  }
}
