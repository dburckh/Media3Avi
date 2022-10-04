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

public class ListBuilder {
  private ByteBuffer byteBuffer;

  public ListBuilder(int listType) {
    byteBuffer = AviExtractor.allocate(12);
    byteBuffer.putInt(ListBox.LIST);
    byteBuffer.putInt(12);
    byteBuffer.putInt(listType);
  }

  public void addBox(final ResidentBox box) {
    long boxLen = 4 + 4 + box.getSize();
    if ((boxLen & 1) == 1) {
      boxLen++;
    }
    final ByteBuffer boxBuffer = AviExtractor.allocate(byteBuffer.capacity() + (int)boxLen);
    byteBuffer.clear();
    boxBuffer.put(byteBuffer);
    boxBuffer.putInt(box.getType());
    boxBuffer.putInt((int)box.getSize());
    boxBuffer.put(box.getByteBuffer());
    byteBuffer = boxBuffer;
  }
  public ByteBuffer build() {
    byteBuffer.putInt(4, byteBuffer.capacity() - 8);
    return byteBuffer;
  }
}
