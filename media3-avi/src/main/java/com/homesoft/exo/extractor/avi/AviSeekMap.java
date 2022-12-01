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

import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekPoint;

import java.util.Arrays;

/**
 * Seek map for AVI.
 * Consists of Video chunk offsets and indexes for all streams
 */
public class AviSeekMap implements SeekMap {
  private final long durationUs;
  private final StreamHandler seekStreamHandler;

  public AviSeekMap(long durationUs, StreamHandler seekStreamHandler) {
    this.durationUs = durationUs;
    this.seekStreamHandler = seekStreamHandler;
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @VisibleForTesting
  int getFirstSeekIndex(int index) {
    int firstIndex = -index - 2;
    if (firstIndex < 0) {
      firstIndex = 0;
    }
    return firstIndex;
  }

  private SeekPoint getSeekPoint(int index) {
    final long position = seekStreamHandler.getPosition(index);
    final long timeUs = seekStreamHandler.getTimeUs(index);
    return new SeekPoint(timeUs, position);
  }

  @NonNull
  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    final int index = seekStreamHandler.getTimeUsIndex(timeUs);
    if (index >= 0) {
      return new SeekPoints(getSeekPoint(index));
    }
    final int firstSeekIndex = getFirstSeekIndex(index);
    if (firstSeekIndex + 1 < seekStreamHandler.getSeekPointCount()) {
      return new SeekPoints(getSeekPoint(firstSeekIndex), getSeekPoint(firstSeekIndex + 1));
    } else {
      return new SeekPoints(getSeekPoint(firstSeekIndex));
    }
  }
}
