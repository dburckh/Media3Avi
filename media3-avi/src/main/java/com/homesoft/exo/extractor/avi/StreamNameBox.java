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

import java.nio.ByteBuffer;

/**
 * Box containing a human readable stream name
 */
public class StreamNameBox extends ResidentBox {
  public static final int STRN = 0x6e727473; // strn

  StreamNameBox(ByteBuffer byteBuffer) {
    super(STRN,byteBuffer);
  }

  public String getName() {
    int len = byteBuffer.capacity();
    if (byteBuffer.get(len - 1) == 0) {
      len -= 1;
    }
    final byte[] bytes = new byte[len];
    byteBuffer.position(0);
    byteBuffer.get(bytes);
    return new String(bytes);
  }
}
