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

import static androidx.media3.extractor.Extractor.RESULT_CONTINUE;
import static com.homesoft.exo.extractor.avi.BoxReader.CHUNK_HEADER_SIZE;
import static com.homesoft.exo.extractor.avi.DataHelper.FIRST_CHUNK;
import static com.homesoft.exo.extractor.avi.DataHelper.VIDEO_CHUNK_ID;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.test.utils.FakeExtractorInput;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.function.Predicate;

@RunWith(AndroidJUnit4.class)
public class AviExtractorTest {

  private boolean sniff(ByteBuffer byteBuffer) {
    AviExtractor aviExtractor = new AviExtractor();
    FakeExtractorInput input = new FakeExtractorInput.Builder()
        .setData(byteBuffer.array()).build();
    try {
      return aviExtractor.sniff(input);
    } catch (IOException e) {
      Assert.fail(e.getMessage());
      return false;
    }
  }

  @Test
  public void peek_givenTooFewByte() {
    Assert.assertFalse(sniff(AviExtractor.allocate(AviExtractor.PEEK_BYTES - 1)));
  }

  @Test
  public void peek_givenAllZero() {
    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
    Assert.assertFalse(sniff(byteBuffer));
  }

  @Test
  public void peek_givenOnlyRiff() {
    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
    byteBuffer.putInt(AviExtractor.RIFF);
    Assert.assertFalse(sniff(byteBuffer));
  }

  @Test
  public void peek_givenOnlyRiffAvi_() {
    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
    byteBuffer.putInt(AviExtractor.RIFF);
    byteBuffer.putInt(128);
    byteBuffer.putInt(AviExtractor.AVI_);
    Assert.assertTrue(sniff(byteBuffer));
  }

  @Test
  public void toString_givenKnownString() {
    final int riff = 'R' | ('I' << 8) | ('F' << 16) | ('F' << 24);
    Assert.assertEquals("RIFF", AviExtractor.toString(riff));
  }

  @Test
  public void setSeekMap_givenStubbedSeekMap() throws IOException {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    aviExtractor.setSeekMap(aviSeekMap);
    Assert.assertEquals(aviSeekMap, fakeExtractorOutput.seekMap);
    Assert.assertEquals(aviSeekMap, aviExtractor.seekMap);
  }

  @Test
  public void getStreamId_givenInvalidStreamId() {
    Assert.assertEquals(-1, AviExtractor.getStreamId(AviExtractor.JUNK));
  }

  @Test
  public void getStreamId_givenValidStreamId() {
    Assert.assertEquals(1, AviExtractor.getStreamId('0' | ('1' << 8) | ('d' << 16) | ('c' << 24)));
  }

  @Test
  public void readMovi_givenMoviWRecList() throws IOException {
    AviExtractor aviExtractor = setupVideoAviExtractor();
    final ByteBuffer byteBuffer = AviExtractor.allocate(2048);
    final AviExtractor.MoviBox box = aviExtractor.moviList.get(0);
    final int start = (int)box.getStart();
    byteBuffer.position(start);
    DataHelper.appendChunk(byteBuffer, VIDEO_CHUNK_ID, 32);
    //Add rec LIST
    byteBuffer.putInt(ListBox.LIST);
    byteBuffer.putInt(4 + CHUNK_HEADER_SIZE + 64 + CHUNK_HEADER_SIZE + 96);
    byteBuffer.putInt(AviExtractor.REC_);
    //Add chunks to rec LIST
    DataHelper.appendChunk(byteBuffer, VIDEO_CHUNK_ID, 64);
    DataHelper.appendChunk(byteBuffer, VIDEO_CHUNK_ID, 96);

    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).build();
    readUntil(aviExtractor, input, extract-> box.getPosition() == byteBuffer.position() && extract.readerStack.size() == 1);
    final StreamHandler streamHandler = aviExtractor.getStreamHandler(VIDEO_CHUNK_ID);
    FakeTrackOutput output = (FakeTrackOutput) streamHandler.trackOutput;
    Assert.assertEquals(3, output.getSampleCount());
    Assert.assertEquals(32, output.getSampleData(0).length);
    Assert.assertEquals(64, output.getSampleData(1).length);
    Assert.assertEquals(96, output.getSampleData(2).length);
  }
  static AviExtractor setupVideoAviExtractor() {
    final AviExtractor aviExtractor = new AviExtractor();
    //PARENT_HEADER_SIZE is not a valid offset, but makes the tests easier
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    setMoviBox(aviExtractor, FIRST_CHUNK, 128*1024);

    final StreamHandler streamHandler = DataHelper.getVideoChunkHandler(9);
    aviExtractor.setChunkHandlers(new StreamHandler[]{streamHandler});
    final Format format = new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_MP4V).build();
    streamHandler.trackOutput.format(format);
    return aviExtractor;
  }

  private static void setMoviBox(AviExtractor aviExtractor, long position, int size) {
    final AviExtractor.MoviBox moviBox = aviExtractor.new MoviBox(position, size);
    aviExtractor.readerStack.clear();
    aviExtractor.readerStack.add(moviBox);
    aviExtractor.moviList.clear();
    aviExtractor.addMovi(moviBox);
  }

  @Test
  public void readSamples_givenAtEndOfInput() throws IOException {
    AviExtractor aviExtractor = setupVideoAviExtractor();
    aviExtractor.readerStack.clear();

    final ByteBuffer byteBuffer = AviExtractor.allocate(FIRST_CHUNK);
    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).build();
    Assert.assertEquals(Extractor.RESULT_END_OF_INPUT, aviExtractor.read(input, new PositionHolder()));
  }

  @Test
  public void readSamples_completeChunk() throws IOException {
    AviExtractor aviExtractor = setupVideoAviExtractor();
    final StreamHandler streamHandler = aviExtractor.getSeekStreamHandler();
    final ByteBuffer byteBuffer = AviExtractor.allocate(FIRST_CHUNK + CHUNK_HEADER_SIZE + 24);
    byteBuffer.position(FIRST_CHUNK);
    byteBuffer.putInt(streamHandler.chunkId);
    byteBuffer.putInt(24);
    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array())
        .build();
    input.skipFully(FIRST_CHUNK);
    final PositionHolder positionHolder = new PositionHolder();
    Assert.assertEquals(RESULT_CONTINUE, aviExtractor.read(input, positionHolder));
    Assert.assertEquals(streamHandler, aviExtractor.readerStack.peek());
    aviExtractor.read(input, positionHolder);
    Assert.assertEquals(streamHandler.getPosition(), input.getPosition());
    Assert.assertEquals(RESULT_CONTINUE, aviExtractor.read(input, positionHolder));
    Assert.assertEquals(aviExtractor.moviList.get(0), aviExtractor.readerStack.peek());

    final FakeTrackOutput fakeTrackOutput = (FakeTrackOutput) streamHandler.trackOutput;
    Assert.assertEquals(24, fakeTrackOutput.getSampleData(0).length);
  }

  @Test
  public void seek_givenKeyFrame() {
    final AviExtractor aviExtractor = setupVideoAviExtractor();
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap(aviExtractor.getSeekStreamHandler());
    aviExtractor.setSeekMap(aviSeekMap);
    final StreamHandler streamHandler = aviExtractor.getSeekStreamHandler();
    aviExtractor.seek(streamHandler.positions[1], streamHandler.getTimeUs(1));
    Assert.assertEquals(streamHandler.getTimeUs(), streamHandler.getTimeUs(1));
  }

  @Test
  public void release() {
    //Shameless way to get 100% method coverage
    final AviExtractor aviExtractor = new AviExtractor();
    aviExtractor.release();
    //Nothing to assert on a method that does nothing
  }

  @Test
  public void parseStream_givenXvidStreamList() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    final ListBox streamList = DataHelper.getVideoStreamList();
    aviExtractor.buildStreamHandler(streamList, 0);
    FakeTrackOutput trackOutput = fakeExtractorOutput.track(0, C.TRACK_TYPE_VIDEO);
    Assert.assertEquals(MimeTypes.VIDEO_MP4V, trackOutput.lastFormat.sampleMimeType);
  }

  @Test
  public void parseStream_givenAacStreamList() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    final ListBox streamList = DataHelper.getAacStreamList();
    aviExtractor.buildStreamHandler(streamList, 0);
    FakeTrackOutput trackOutput = fakeExtractorOutput.track(0, C.TRACK_TYPE_VIDEO);
    Assert.assertEquals(MimeTypes.AUDIO_AAC, trackOutput.lastFormat.sampleMimeType);
  }

  @Test
  public void parseStream_givenNoStreamHeader() {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    final ListBox streamList = new ListBox(1024L, 128, ListBox.TYPE_STRL, new ArrayDeque<>());
    Assert.assertNull(aviExtractor.buildStreamHandler(streamList, 0));
  }

  @Test
  public void parseStream_givenNoStreamFormat() {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    final ListBox streamList = new ListBox(1024L, 128, ListBox.TYPE_STRL,new ArrayDeque<>());
    streamList.add(DataHelper.getVidsStreamHeader());
    Assert.assertNull(aviExtractor.buildStreamHandler(streamList, 0));
  }

  static void readUntil(AviExtractor aviExtractor, FakeExtractorInput input, Predicate<AviExtractor> predicate) throws IOException {
    final PositionHolder positionHolder = new PositionHolder();
    do {
      int rc = aviExtractor.read(input, positionHolder);
      if (rc == Extractor.RESULT_END_OF_INPUT) {
        return;
      } else if (rc == Extractor.RESULT_SEEK) {
        input.setPosition((int)positionHolder.position);
      }
    } while (!predicate.test(aviExtractor));
  }


  @Test
  public void readTracks_givenVideoTrack() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);

    final ByteBuffer byteBuffer = DataHelper.getRiffHeader(0xdc, 0xc8);
    final ByteBuffer aviHeader = DataHelper.createAviHeader();
    byteBuffer.putInt(aviHeader.capacity());
    byteBuffer.put(aviHeader);
    byteBuffer.putInt(ListBox.LIST);
    byteBuffer.putInt(byteBuffer.remaining() - 4);
    byteBuffer.putInt(ListBox.TYPE_STRL);

    final StreamHeaderBox streamHeaderBox = DataHelper.getVidsStreamHeader();
    byteBuffer.putInt(StreamHeaderBox.STRH);
    byteBuffer.putInt((int)streamHeaderBox.getSize());
    byteBuffer.put(streamHeaderBox.getByteBuffer());

    final StreamFormatBox streamFormatBox = DataHelper.getVideoStreamFormat();
    byteBuffer.putInt(StreamFormatBox.STRF);
    byteBuffer.putInt((int)streamFormatBox.getSize());
    byteBuffer.put(streamFormatBox.getByteBuffer());

    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).
        build();
    readUntil(aviExtractor, input, (extractor)->extractor.readerStack.size() == 1);


    final StreamHandler streamHandler = aviExtractor.getSeekStreamHandler();
    Assert.assertEquals(streamHandler.getDurationUs(), streamHeaderBox.getDurationUs());
  }

  @Test
  public void unboundIntArray_add_givenExceedsCapacity() {
    final ChunkIndex chunkIndex = new ChunkIndex();
    final int testLen = chunkIndex.positions.length + 1;
    for (int i=0; i < testLen; i++) {
      chunkIndex.add(i, 1024, false);
    }

    Assert.assertEquals(testLen, chunkIndex.getCount());
    for (int i=0; i < testLen; i++) {
      Assert.assertEquals(i, chunkIndex.positions[i]);
    }
  }
}