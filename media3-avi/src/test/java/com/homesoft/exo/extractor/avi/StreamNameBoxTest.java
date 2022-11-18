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

import androidx.media3.test.utils.FakeExtractorInput;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

public class StreamNameBoxTest {

  @Test
  public void createStreamName_givenList() throws IOException {
    final String name = "Test";
    final ListBuilder listBuilder = new ListBuilder(ListBox.TYPE_STRL);
    listBuilder.addBox(DataHelper.getStreamNameBox(name));
    final ByteBuffer listBuffer = listBuilder.build();
    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().setData(listBuffer.array()).build();
    ArrayDeque<IReader> readerStack = new ArrayDeque<>();
    ListBox listBox = new ListBox(BoxReader.PARENT_HEADER_SIZE, listBuffer.capacity() - BoxReader.PARENT_HEADER_SIZE, ListBox.TYPE_STRL, readerStack);
    DataHelper.readRecursive(listBox, fakeExtractorInput, readerStack);
    Assert.assertEquals(1, listBox.getChildren().size());
    final StreamNameBox streamNameBox = (StreamNameBox) listBox.getChildren().get(0);
    Assert.assertEquals(name, streamNameBox.getName());
  }
}
