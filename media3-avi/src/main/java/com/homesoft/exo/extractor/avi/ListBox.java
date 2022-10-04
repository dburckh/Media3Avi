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
import androidx.annotation.Nullable;

import androidx.media3.extractor.ExtractorInput;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * An AVI LIST box.  Similar to a Java List<Box>
 */
public class ListBox extends Box {
  public static final int LIST = 0x5453494c; // LIST
  public static final int TYPE_HDRL = 0x6c726468; // hdrl - Header List
  public static final int TYPE_STRL = 0x6c727473; // strl - Stream List

  private final int listType;

  final List<Box> children;

  public ListBox(int size, int listType, List<Box> children) {
    super(LIST, size);
    this.listType = listType;
    this.children = children;
  }

  public int getListType() {
    return listType;
  }

  @NonNull
  public List<Box> getChildren() {
    return new ArrayList<>(children);
  }

  @Nullable
  public <T extends Box> T getChild(Class<T> c) {
    for (Box box : children) {
      if (box.getClass() == c) {
        return (T)box;
      }
    }
    return null;
  }

  /**
   * Assume the input is pointing to the list type
   * @throws IOException
   */
  public static ListBox newInstance(final int listSize, BoxFactory boxFactory,
      ExtractorInput input) throws IOException {

    final List<Box> list = new ArrayList<>();
    final ByteBuffer headerBuffer = AviExtractor.allocate(8);
    byte [] bytes = headerBuffer.array();
    input.readFully(bytes, 0, 4);
    final int listType = headerBuffer.getInt();
    //String listTypeName = AviExtractor.toString(listType);
    long endPos = input.getPosition() + listSize - 4;
    while (input.getPosition() + 8 < endPos) {
      headerBuffer.clear();
      input.readFully(bytes, 0, 8);
      final int type = headerBuffer.getInt();
      final int size = headerBuffer.getInt();
      final Box box;
      if (type == LIST) {
        box = newInstance(size, boxFactory, input);
      } else {
        box = boxFactory.createBox(type, size, input);
      }
      AviExtractor.alignInput(input);
      if (box != null) {
        list.add(box);
      }
    }
    return new ListBox(listSize, listType, list);
  }
}
