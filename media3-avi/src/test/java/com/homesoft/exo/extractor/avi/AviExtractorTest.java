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

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.test.utils.FakeExtractorInput;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class AviExtractorTest {

//  @Test
//  public void init_givenFakeExtractorOutput() {
//    AviExtractor aviExtractor = new AviExtractor();
//    FakeExtractorOutput output = new FakeExtractorOutput();
//    aviExtractor.init(output);
//
//    Assert.assertEquals(AviExtractor.STATE_READ_TRACKS, aviExtractor.state);
//    Assert.assertEquals(output, aviExtractor.output);
//  }
//
//
//  private boolean sniff(ByteBuffer byteBuffer) {
//    AviExtractor aviExtractor = new AviExtractor();
//    FakeExtractorInput input = new FakeExtractorInput.Builder()
//        .setData(byteBuffer.array()).build();
//    try {
//      return aviExtractor.sniff(input);
//    } catch (IOException e) {
//      Assert.fail(e.getMessage());
//      return false;
//    }
//  }
//
//  @Test
//  public void peek_givenTooFewByte() {
//    Assert.assertFalse(sniff(AviExtractor.allocate(AviExtractor.PEEK_BYTES - 1)));
//  }
//
//  @Test
//  public void peek_givenAllZero() {
//    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
//    Assert.assertFalse(sniff(byteBuffer));
//  }
//
//  @Test
//  public void peek_givenOnlyRiff() {
//    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
//    byteBuffer.putInt(AviExtractor.RIFF);
//    Assert.assertFalse(sniff(byteBuffer));
//  }
//
//  @Test
//  public void peek_givenOnlyRiffAvi_() {
//    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
//    byteBuffer.putInt(AviExtractor.RIFF);
//    byteBuffer.putInt(128);
//    byteBuffer.putInt(AviExtractor.AVI_);
//    Assert.assertFalse(sniff(byteBuffer));
//  }
//
//  @Test
//  public void peek_givenOnlyRiffAvi_List() {
//    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
//    byteBuffer.putInt(AviExtractor.RIFF);
//    byteBuffer.putInt(128);
//    byteBuffer.putInt(AviExtractor.AVI_);
//    byteBuffer.putInt(ListBox.LIST);
//    Assert.assertFalse(sniff(byteBuffer));
//  }
//
//  @Test
//  public void peek_givenOnlyRiffAvi_ListHdrl() {
//    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
//    byteBuffer.putInt(AviExtractor.RIFF);
//    byteBuffer.putInt(128);
//    byteBuffer.putInt(AviExtractor.AVI_);
//    byteBuffer.putInt(ListBox.LIST);
//    byteBuffer.putInt(64);
//    byteBuffer.putInt(ListBox.TYPE_HDRL);
//    Assert.assertFalse(sniff(byteBuffer));
//  }
//
//  @Test
//  public void peek_givenOnlyRiffAvi_ListHdrlAvih() {
//    final ByteBuffer byteBuffer = DataHelper.getRiffHeader(AviExtractor.PEEK_BYTES, 128);
//    Assert.assertTrue(sniff(byteBuffer));
//  }

  @Test
  public void toString_givenKnownString() {
    final int riff = 'R' | ('I' << 8) | ('F' << 16) | ('F' << 24);
    Assert.assertEquals("RIFF", AviExtractor.toString(riff));
  }

  @Test
  public void alignPosition_givenOddPosition() {
    Assert.assertEquals(2, AviExtractor.alignPosition(1));
  }

  @Test
  public void alignPosition_givenEvenPosition() {
    Assert.assertEquals(2, AviExtractor.alignPosition(2));
  }

  @Test
  public void alignInput_givenOddPosition() throws IOException {
    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
        setData(new byte[16]).build();
    fakeExtractorInput.setPosition(1);
    AviExtractor.alignInput(fakeExtractorInput);
    Assert.assertEquals(2, fakeExtractorInput.getPosition());
  }

  @Test

  public void alignInput_givenEvenPosition() throws IOException {
    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
        setData(new byte[16]).build();
    fakeExtractorInput.setPosition(4);
    AviExtractor.alignInput(fakeExtractorInput);
    Assert.assertEquals(4, fakeExtractorInput.getPosition());
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

  private void assertIdx1(AviSeekMap aviSeekMap, StreamHandler videoTrack, int keyFrames,
                          int keyFrameRate) {
    Assert.assertEquals(keyFrames, videoTrack.keyFrames.length);

    final int framesPerKeyFrame = 24 * 3;
    //This indirectly verifies the number of video chunks
    Assert.assertEquals(9 * DataHelper.FPS, videoTrack.getChunkIndex().getChunkCount());

    Assert.assertEquals(2 * framesPerKeyFrame, videoTrack.keyFrames[2]);

    Assert.assertEquals(2 * keyFrameRate * DataHelper.AUDIO_PER_VIDEO,
        aviSeekMap.getSeekIndexes(DataHelper.AUDIO_ID)[2]);
    Assert.assertEquals(DataHelper.MOVI_OFFSET + 4L + 2 * keyFrameRate * DataHelper.VIDEO_SIZE +
            2 * keyFrameRate * DataHelper.AUDIO_SIZE * DataHelper.AUDIO_PER_VIDEO,
        aviSeekMap.getKeyFrameOffsets(2));

  }

//  @Test
//  public void readIdx1_given9secsAv() throws IOException {
//    final AviExtractor aviExtractor = new AviExtractor();
//    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
//    aviExtractor.init(fakeExtractorOutput);
//    final int secs = 9;
//    final int keyFrameRate = 3 * DataHelper.FPS; // Keyframe every 3 seconds
//    final int keyFrames = secs * DataHelper.FPS / keyFrameRate;
//    final ByteBuffer idx1 = DataHelper.getIndex(secs, keyFrameRate);
//    final StreamHandler videoTrack = DataHelper.getVideoChunkHandler(secs);
//    final StreamHandler audioTrack = DataHelper.getAudioChunkHandler(secs);
//    aviExtractor.setChunkHandlers(new StreamHandler[]{videoTrack, audioTrack});
//    aviExtractor.setAviHeader(DataHelper.createAviHeaderBox());
//    aviExtractor.state = AviExtractor.STATE_READ_IDX1;
//    aviExtractor.setMovi(DataHelper.MOVI_OFFSET, 128*1024);
//
//    final ByteBuffer idx1Box = AviExtractor.allocate(idx1.capacity() + 8);
//    idx1Box.putInt(AviExtractor.IDX1);
//    idx1Box.putInt(idx1.capacity());
//    idx1.clear();
//    idx1Box.put(idx1);
//    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder()
//        .setData(idx1Box.array()).build();
//    //aviExtractor.readIdx1(fakeExtractorInput, (int) fakeExtractorInput.getLength());
//    final PositionHolder positionHolder = new PositionHolder();
//    aviExtractor.read(fakeExtractorInput, positionHolder);
//
//    final AviSeekMap aviSeekMap = aviExtractor.aviSeekMap;
//    assertIdx1(aviSeekMap, videoTrack, keyFrames, keyFrameRate);
//
//    Assert.assertEquals(AviExtractor.STATE_READ_CHUNKS, aviExtractor.state);
//    Assert.assertEquals(DataHelper.MOVI_OFFSET + 4, positionHolder.position);
//  }
//
//  @Test
//  public void readIdx1_givenNoVideo() throws IOException {
//    final AviExtractor aviExtractor = new AviExtractor();
//    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
//    aviExtractor.init(fakeExtractorOutput);
//    final int secs = 9;
//    final int keyFrameRate = 3 * DataHelper.FPS; // Keyframe every 3 seconds
//    final ByteBuffer idx1 = DataHelper.getIndex(secs, keyFrameRate);
//    final StreamHandler audioTrack = DataHelper.getAudioChunkHandler(secs);
//    aviExtractor.setChunkHandlers(new StreamHandler[]{audioTrack});
//
//    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder()
//        .setData(idx1.array()).build();
//    aviExtractor.parseIdx1(fakeExtractorInput, (int) fakeExtractorInput.getLength());
//    Assert.assertTrue(fakeExtractorOutput.seekMap instanceof SeekMap.Unseekable);
//  }
//
//  @Test
//  public void readIdx1_givenJunkInIndex() throws IOException {
//    final AviExtractor aviExtractor = new AviExtractor();
//    aviExtractor.setAviHeader(DataHelper.createAviHeaderBox());
//    aviExtractor.setMovi(DataHelper.MOVI_OFFSET, 128*1024);
//    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
//    aviExtractor.init(fakeExtractorOutput);
//
//    final int secs = 9;
//    final int keyFrameRate = 3 * DataHelper.FPS; // Keyframe every 3 seconds
//    final int keyFrames = secs * DataHelper.FPS / keyFrameRate;
//    final ByteBuffer idx1 = DataHelper.getIndex(9, keyFrameRate);
//    // Add JUNK(16) + IDX1 header(8)
//    final ByteBuffer junk = AviExtractor.allocate(idx1.capacity() + 16 + 8);
//    junk.putInt(AviExtractor.JUNK);
//    junk.putInt(8);
//    //8 bytes of junk data
//    junk.putInt(0);
//    junk.putInt(0);
//    idx1.flip();
//    junk.putInt(AviExtractor.IDX1);
//    junk.putInt(idx1.remaining());
//    junk.put(idx1);
//    final StreamHandler videoTrack = DataHelper.getVideoChunkHandler(secs);
//    final StreamHandler audioTrack = DataHelper.getAudioChunkHandler(secs);
//    aviExtractor.setChunkHandlers(new StreamHandler[]{videoTrack, audioTrack});
//
//    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
//        setData(junk.array()).build();
//    aviExtractor.state = AviExtractor.STATE_READ_IDX1;
//    //Read the JUNK
//    aviExtractor.read(fakeExtractorInput, new PositionHolder());
//    //Skip the JUNK
//    aviExtractor.read(fakeExtractorInput, new PositionHolder());
//    //Read IDX1
//    aviExtractor.read(fakeExtractorInput, new PositionHolder());
//    assertIdx1(aviExtractor.aviSeekMap, videoTrack, keyFrames, keyFrameRate);
//  }
//
//  @Test
//  public void readIdx1_givenAllKeyFrames() throws IOException {
//    final AviExtractor aviExtractor = new AviExtractor();
//    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
//    aviExtractor.init(fakeExtractorOutput);
//    final int secs = 4;
//    final ByteBuffer idx1 = DataHelper.getIndex(secs, 1);
//    final StreamHandler videoTrack = DataHelper.getVideoChunkHandler(secs);
//    final StreamHandler audioTrack = DataHelper.getAudioChunkHandler(secs);
//    aviExtractor.setChunkHandlers(new StreamHandler[]{videoTrack, audioTrack});
//
//    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
//        setData(idx1.array()).build();
//    aviExtractor.parseIdx1(fakeExtractorInput, (int) fakeExtractorInput.getLength());
//
//    //We should be throttled to 2 key frame per second
//    Assert.assertSame(ChunkIndex.ALL_KEY_FRAMES, videoTrack.keyFrames);
//  }
//
//  @Test
//  public void readIdx1_givenBufferToShort() throws IOException {
//    final AviExtractor aviExtractor = setupVideoAviExtractor();
//    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
//        setData(new byte[12]).build();
//
//    aviExtractor.parseIdx1(fakeExtractorInput, 12);
//    final FakeExtractorOutput fakeExtractorOutput = (FakeExtractorOutput) aviExtractor.output;
//    Assert.assertTrue(fakeExtractorOutput.seekMap instanceof SeekMap.Unseekable);
//  }
//
//  @Test
//  public void readIdx1_givenBadOffset() throws IOException {
//    final AviExtractor aviExtractor = setupVideoAviExtractor();
//    final int secs = 4;
//    final int indexOffset =  (int)aviExtractor.getMoviOffset() + 4;
//    final ByteBuffer idx1 = DataHelper.getIndex(secs, 1, indexOffset);
//
//    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
//        setData(idx1.array()).build();
//    aviExtractor.parseIdx1(fakeExtractorInput, (int) fakeExtractorInput.getLength());
//    Assert.assertEquals(indexOffset, aviExtractor.aviSeekMap.getSeekPoints(0L).first.position);
//  }
//
//  @Test
//  public void readHeaderList_givenBadHeader() throws IOException {
//    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(new byte[32]).build();
//    Assert.assertNull(AviExtractor.readHeaderList(input));
//  }
//
//  @Test
//  public void readHeaderList_givenNoHeaderList() throws IOException {
//    final ByteBuffer byteBuffer = DataHelper.getRiffHeader(88, 0x44);
//    byteBuffer.putInt(0x14, ListBox.TYPE_STRL); //Overwrite header list with stream list
//    final FakeExtractorInput input = new FakeExtractorInput.Builder().
//        setData(byteBuffer.array()).build();
//    Assert.assertNull(AviExtractor.readHeaderList(input));
//  }
//
//  @Test
//  public void readHeaderList_givenEmptyHeaderList() throws IOException {
//    final ByteBuffer byteBuffer = DataHelper.getRiffHeader(88, 0x44);
//    byteBuffer.putInt(AviHeaderBox.LEN);
//    byteBuffer.put(DataHelper.createAviHeader());
//    final FakeExtractorInput input = new FakeExtractorInput.Builder().
//        setData(byteBuffer.array()).build();
//    final ListBox listBox = AviExtractor.readHeaderList(input);
//    Assert.assertEquals(1, listBox.getChildren().size());
//
//    Assert.assertTrue(listBox.getChildren().get(0) instanceof AviHeaderBox);
//  }
//
//  @Test
//  public void findMovi_givenMoviListAndIndex() throws IOException {
//    final AviExtractor aviExtractor = new AviExtractor();
//    aviExtractor.setAviHeader(DataHelper.createAviHeaderBox());
//    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
//    aviExtractor.init(fakeExtractorOutput);
//
//    ByteBuffer byteBuffer = AviExtractor.allocate(12);
//    byteBuffer.putInt(ListBox.LIST);
//    byteBuffer.putInt(64*1024);
//    byteBuffer.putInt(AviExtractor.MOVI);
//    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).build();
//    aviExtractor.findMovi(input, new PositionHolder());
//    Assert.assertEquals(aviExtractor.state, AviExtractor.STATE_READ_IDX1);
//  }
//
//  @Test
//  public void findMovi_givenMoviListAndNoIndex() throws IOException {
//    final AviExtractor aviExtractor = new AviExtractor();
//    final AviHeaderBox aviHeaderBox = DataHelper.createAviHeaderBox();
//    aviHeaderBox.setFlags(0);
//    aviExtractor.setAviHeader(aviHeaderBox);
//    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
//    aviExtractor.init(fakeExtractorOutput);
//
//    ByteBuffer byteBuffer = AviExtractor.allocate(12);
//    byteBuffer.putInt(ListBox.LIST);
//    byteBuffer.putInt(64*1024);
//    byteBuffer.putInt(AviExtractor.MOVI);
//    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).build();
//    aviExtractor.state = AviExtractor.STATE_FIND_MOVI;
//    aviExtractor.read(input, new PositionHolder());
//    Assert.assertEquals(aviExtractor.state, AviExtractor.STATE_READ_TRACKS);
//    Assert.assertTrue(fakeExtractorOutput.seekMap instanceof SeekMap.Unseekable);
//  }
//
//  @Test
//  public void findMovi_givenJunk() throws IOException {
//    final AviExtractor aviExtractor = new AviExtractor();
//    aviExtractor.setAviHeader(DataHelper.createAviHeaderBox());
//    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
//    aviExtractor.init(fakeExtractorOutput);
//
//    ByteBuffer byteBuffer = AviExtractor.allocate(12);
//    byteBuffer.putInt(AviExtractor.JUNK);
//    byteBuffer.putInt(64*1024);
//    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).build();
//    final PositionHolder positionHolder = new PositionHolder();
//    aviExtractor.findMovi(input, positionHolder);
//    Assert.assertEquals(64 * 1024 + 8, positionHolder.position);
//  }
//
//  static AviExtractor setupVideoAviExtractor() {
//    final AviExtractor aviExtractor = new AviExtractor();
//    aviExtractor.setAviHeader(DataHelper.createAviHeaderBox());
//    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
//    aviExtractor.init(fakeExtractorOutput);
//
//    final StreamHandler streamHandler = DataHelper.getVideoChunkHandler(9);
//    aviExtractor.setChunkHandlers(new StreamHandler[]{streamHandler});
//    final Format format = new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_MP4V).build();
//    streamHandler.trackOutput.format(format);
//
//    aviExtractor.state = AviExtractor.STATE_READ_CHUNKS;
//    aviExtractor.setMovi(DataHelper.MOVI_OFFSET, 128*1024);
//    return aviExtractor;
//  }
//
//  @Test
//  public void readSamples_givenAtEndOfInput() throws IOException {
//    AviExtractor aviExtractor = setupVideoAviExtractor();
//    aviExtractor.setMovi(0, 0);
//    final StreamHandler streamHandler = aviExtractor.getVideoTrack();
//    final ByteBuffer byteBuffer = AviExtractor.allocate(32);
//    byteBuffer.putInt(streamHandler.chunkId);
//    byteBuffer.putInt(24);
//
//    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).build();
//    Assert.assertEquals(Extractor.RESULT_END_OF_INPUT, aviExtractor.read(input, new PositionHolder()));
//  }
//
//  @Test
//  public void readSamples_completeChunk() throws IOException {
//    AviExtractor aviExtractor = setupVideoAviExtractor();
//    final StreamHandler streamHandler = aviExtractor.getVideoTrack();
//    final ByteBuffer byteBuffer = AviExtractor.allocate(32);
//    byteBuffer.putInt(streamHandler.chunkId);
//    byteBuffer.putInt(24);
//
//    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array())
//        .build();
//    Assert.assertEquals(Extractor.RESULT_CONTINUE, aviExtractor.read(input, new PositionHolder()));
//
//    final FakeTrackOutput fakeTrackOutput = (FakeTrackOutput) streamHandler.trackOutput;
//    Assert.assertEquals(24, fakeTrackOutput.getSampleData(0).length);
//  }
//
//  @Test
//  public void readSamples_givenLeadingZeros() throws IOException {
//    AviExtractor aviExtractor = setupVideoAviExtractor();
//    final StreamHandler streamHandler = aviExtractor.getVideoTrack();
//    final ByteBuffer byteBuffer = AviExtractor.allocate(48);
//    byteBuffer.position(16);
//    byteBuffer.putInt(streamHandler.chunkId);
//    byteBuffer.putInt(24);
//
//    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array())
//        .build();
//    Assert.assertEquals(Extractor.RESULT_CONTINUE, aviExtractor.read(input, new PositionHolder()));
//
//    final FakeTrackOutput fakeTrackOutput = (FakeTrackOutput) streamHandler.trackOutput;
//    Assert.assertEquals(24, fakeTrackOutput.getSampleData(0).length);
//  }
//
//  @Test
//  public void seek_givenPosition0() throws IOException {
//    final AviExtractor aviExtractor = setupVideoAviExtractor();
//    final StreamHandler streamHandler = aviExtractor.getVideoTrack();
//    aviExtractor.setChunkHandler(streamHandler);
//    streamHandler.getClock().setIndex(10);
//
//    aviExtractor.seek(0L, 0L);
//
//    Assert.assertNull(aviExtractor.getStreamHandler());
//    Assert.assertEquals(0, streamHandler.getClock().getIndex());
//    Assert.assertEquals(aviExtractor.state, AviExtractor.STATE_SEEK_START);
//
//
//    final ExtractorInput input = new FakeExtractorInput.Builder().setData(new byte[0]).build();
//    final PositionHolder positionHolder = new PositionHolder();
//    Assert.assertEquals(Extractor.RESULT_SEEK, aviExtractor.read(input, positionHolder));
//    Assert.assertEquals(DataHelper.MOVI_OFFSET + 4, positionHolder.position);
//  }
//
//  @Test
//  public void seek_givenKeyFrame() {
//    final AviExtractor aviExtractor = setupVideoAviExtractor();
//    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
//    aviExtractor.aviSeekMap = aviSeekMap;
//    final StreamHandler streamHandler = aviExtractor.getVideoTrack();
//    final long position = aviSeekMap.getKeyFrameOffsets(DataHelper.AUDIO_ID);
//    aviExtractor.seek(position, 0L);
//    Assert.assertEquals(aviSeekMap.getSeekIndexes(streamHandler.getId())[1],
//        streamHandler.getClock().getIndex());
//  }

  @Test
  public void release() {
    //Shameless way to get 100% method coverage
    final AviExtractor aviExtractor = new AviExtractor();
    aviExtractor.release();
    //Nothing to assert on a method that does nothing
  }

//  @Test
//  public void parseStream_givenXvidStreamList() throws IOException {
//    final AviExtractor aviExtractor = new AviExtractor();
//    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
//    aviExtractor.init(fakeExtractorOutput);
//    final ListBox streamList = DataHelper.getVideoStreamList();
//    aviExtractor.buildStreamHandler(streamList, 0);
//    FakeTrackOutput trackOutput = fakeExtractorOutput.track(0, C.TRACK_TYPE_VIDEO);
//    Assert.assertEquals(MimeTypes.VIDEO_MP4V, trackOutput.lastFormat.sampleMimeType);
//  }
//
//  @Test
//  public void parseStream_givenAacStreamList() throws IOException {
//    final AviExtractor aviExtractor = new AviExtractor();
//    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
//    aviExtractor.init(fakeExtractorOutput);
//    final ListBox streamList = DataHelper.getAacStreamList();
//    aviExtractor.buildStreamHandler(streamList, 0);
//    FakeTrackOutput trackOutput = fakeExtractorOutput.track(0, C.TRACK_TYPE_VIDEO);
//    Assert.assertEquals(MimeTypes.AUDIO_AAC, trackOutput.lastFormat.sampleMimeType);
//  }

//  @Test
//  public void parseStream_givenNoStreamHeader() {
//    final AviExtractor aviExtractor = new AviExtractor();
//    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
//    aviExtractor.init(fakeExtractorOutput);
//    final ListBox streamList = new ListBox(128, ListBox.TYPE_STRL, Collections.EMPTY_LIST);
//    Assert.assertNull(aviExtractor.buildStreamHandler(streamList, 0));
//  }
//
//  @Test
//  public void parseStream_givenNoStreamFormat() {
//    final AviExtractor aviExtractor = new AviExtractor();
//    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
//    aviExtractor.init(fakeExtractorOutput);
//    final ListBox streamList = new ListBox(128, ListBox.TYPE_STRL,
//        Collections.singletonList(DataHelper.getVidsStreamHeader()));
//    Assert.assertNull(aviExtractor.buildStreamHandler(streamList, 0));
//  }
//
//  @Test
//  public void readTracks_givenVideoTrack() throws IOException {
//    final AviExtractor aviExtractor = new AviExtractor();
//    aviExtractor.setAviHeader(DataHelper.createAviHeaderBox());
//    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
//    aviExtractor.init(fakeExtractorOutput);
//
//    final ByteBuffer byteBuffer = DataHelper.getRiffHeader(0xdc, 0xc8);
//    final ByteBuffer aviHeader = DataHelper.createAviHeader();
//    byteBuffer.putInt(aviHeader.capacity());
//    byteBuffer.put(aviHeader);
//    byteBuffer.putInt(ListBox.LIST);
//    byteBuffer.putInt(byteBuffer.remaining() - 4);
//    byteBuffer.putInt(ListBox.TYPE_STRL);
//
//    final StreamHeaderBox streamHeaderBox = DataHelper.getVidsStreamHeader();
//    byteBuffer.putInt(StreamHeaderBox.STRH);
//    byteBuffer.putInt(streamHeaderBox.getSize());
//    byteBuffer.put(streamHeaderBox.getByteBuffer());
//
//    final StreamFormatBox streamFormatBox = DataHelper.getVideoStreamFormat();
//    byteBuffer.putInt(StreamFormatBox.STRF);
//    byteBuffer.putInt(streamFormatBox.getSize());
//    byteBuffer.put(streamFormatBox.getByteBuffer());
//
//    aviExtractor.state = AviExtractor.STATE_READ_TRACKS;
//    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).
//        build();
//    final PositionHolder positionHolder = new PositionHolder();
//    aviExtractor.read(input, positionHolder);
//
//    Assert.assertEquals(AviExtractor.STATE_FIND_MOVI, aviExtractor.state);
//
//    final StreamHandler streamHandler = aviExtractor.getVideoTrack();
//    Assert.assertEquals(streamHandler.getClock().durationUs, streamHeaderBox.getDurationUs());
//  }
//
//  @Test
//  public void readSamples_fragmentedChunk() throws IOException {
//    AviExtractor aviExtractor = AviExtractorTest.setupVideoAviExtractor();
//    final StreamHandler streamHandler = aviExtractor.getVideoTrack();
//    final int size = 24 + 16;
//    final ByteBuffer byteBuffer = AviExtractor.allocate(size + 8);
//    byteBuffer.putInt(streamHandler.chunkId);
//    byteBuffer.putInt(size);
//
//    final ExtractorInput chunk = new FakeExtractorInput.Builder().setData(byteBuffer.array()).
//        setSimulatePartialReads(true).build();
//    Assert.assertEquals(Extractor.RESULT_CONTINUE, aviExtractor.read(chunk, new PositionHolder()));
//
//    Assert.assertEquals(Extractor.RESULT_CONTINUE, aviExtractor.read(chunk, new PositionHolder()));
//
//    final FakeTrackOutput fakeTrackOutput = (FakeTrackOutput) streamHandler.trackOutput;
//    Assert.assertEquals(size, fakeTrackOutput.getSampleData(0).length);
//  }

  @Test
  public void unboundIntArray_add_givenExceedsCapacity() {
    final ChunkIndex chunkIndex = new ChunkIndex();
    final int testLen = chunkIndex.positions.length + 1;
    for (int i=0; i < testLen; i++) {
      chunkIndex.add(i, false);
    }

    Assert.assertEquals(testLen, chunkIndex.getChunkCount());
    for (int i=0; i < testLen; i++) {
      Assert.assertEquals(i, chunkIndex.positions[i]);
    }
  }
}