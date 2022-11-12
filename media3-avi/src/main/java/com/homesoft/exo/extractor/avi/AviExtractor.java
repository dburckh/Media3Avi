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
import androidx.annotation.VisibleForTesting;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Log;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Objects;

/**
 * Extractor based on the official MicroSoft spec
 * https://docs.microsoft.com/en-us/windows/win32/directshow/avi-riff-file-reference
 */
public class AviExtractor implements Extractor {
  //Minimum time between keyframes in the AviSeekMap
  static final long MIN_KEY_FRAME_RATE_US = 2_000_000L;
  static final long UINT_MASK = 0xffffffffL;
  private static final int RELOAD_MINIMUM_SEEK_DISTANCE = 256 * 1024;

  static long getUInt(@NonNull ByteBuffer byteBuffer) {
    return byteBuffer.getInt() & UINT_MASK;
  }

  @NonNull
  static String toString(int tag) {
    final StringBuilder sb = new StringBuilder(4);
    for (int i=0;i<4;i++) {
      sb.append((char)(tag & 0xff));
      tag >>=8;
    }
    return sb.toString();
  }

  static long alignPosition(long position) {
    if ((position & 1) == 1) {
      position++;
    }
    return position;
  }

  static void alignInput(@NonNull ExtractorInput input) throws IOException {
    // This isn't documented anywhere, but most files are aligned to even bytes
    // and can have gaps of zeros
    if ((input.getPosition() & 1) == 1) {
      input.skipFully(1);
    }
  }

  @NonNull
  static ByteBuffer allocate(int bytes) {
    final byte[] buffer = new byte[bytes];
    final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    return byteBuffer;
  }

  @VisibleForTesting
  static int getStreamId(int chunkId) {
    final int upperChar = chunkId & 0xff;
    if (Character.isDigit(upperChar)) {
      final int lowerChar = (chunkId >> 8) & 0xff;
      if (Character.isDigit(upperChar)) {
        return (lowerChar & 0xf) + ((upperChar & 0xf) * 10);
      }
    }
    return -1;
  }

  static final String TAG = "AviExtractor";
  @VisibleForTesting
  static final int PEEK_BYTES = 28;

  @VisibleForTesting
  static final int STATE_READ_TRACKS = 0;
  @VisibleForTesting
  static final int STATE_FIND_MOVI = 1;
  @VisibleForTesting
  static final int STATE_READ_IDX1 = 2;
  @VisibleForTesting
  static final int STATE_READ_INDX = 3;
  @VisibleForTesting
  static final int STATE_READ_CHUNKS = 5;
  @VisibleForTesting
  static final int STATE_SEEK_START = 6;

  static final int AVIIF_KEYFRAME = 16;


  static final int RIFF = 0x46464952; // RIFF
  static final int AVI_ = 0x20495641; // AVI<space>
  //movie data box
  static final int MOVI = 0x69766f6d; // movi
  //Index
  static final int IDX1 = 0x31786469; // idx1

  static final int JUNK = 0x4b4e554a; // JUNK
  static final int REC_ = 0x20636572; // rec<space>

  @VisibleForTesting
  int state;
  @VisibleForTesting
  ExtractorOutput output;
  private AviHeaderBox aviHeader;
  private long durationUs = C.TIME_UNSET;
  /**
   * ChunkHandlers by StreamId
   */
  private StreamHandler[] streamHandlers = new StreamHandler[0];
  //At the start of the movi tag
  private long moviOffset;
  private long moviEnd;
  @VisibleForTesting
  AviSeekMap aviSeekMap;

  //Set if a chunk is only partially read
  private transient StreamHandler streamHandler;

  private transient int pendingSkip = C.POSITION_UNSET;

  /**
   *
   * @param bytes Must be at least 20
   */
  @Nullable
  static private ByteBuffer getAviBuffer(@NonNull ExtractorInput input, int bytes) throws IOException {
    if (input.getLength() < bytes) {
      return null;
    }
    final ByteBuffer byteBuffer = allocate(bytes);
    input.peekFully(byteBuffer.array(), 0, bytes);
    final int riff = byteBuffer.getInt();
    if (riff != AviExtractor.RIFF) {
      return null;
    }
    long reportedLen = getUInt(byteBuffer) + byteBuffer.position();
    final long inputLen = input.getLength();
    if (inputLen != C.LENGTH_UNSET && inputLen != reportedLen) {
      w("Header length doesn't match stream length");
    }
    int avi = byteBuffer.getInt();
    if (avi != AviExtractor.AVI_) {
      return null;
    }
    final int list = byteBuffer.getInt();
    if (list != ListBox.LIST) {
      return null;
    }
    return byteBuffer;
  }

  @Override
  public boolean sniff(@NonNull ExtractorInput input) throws IOException {
    final ByteBuffer byteBuffer = getAviBuffer(input, PEEK_BYTES);
    if (byteBuffer == null) {
      return false;
    }
    //Len
    byteBuffer.getInt();
    final int hdrl = byteBuffer.getInt();
    if (hdrl != ListBox.TYPE_HDRL) {
      return false;
    }
    final int avih = byteBuffer.getInt();
    return avih == AviHeaderBox.AVIH;
  }

  /**
   * Build and set the SeekMap based on the indices
   */
  private void buildSeekMap() {
    final StreamHandler videoTrack = getVideoTrack();
    if (videoTrack == null) {
      output.seekMap(new SeekMap.Unseekable(getDuration()));
      w("No video track found");
      return;
    }
    final int videoTrackId = videoTrack.getId();
    final ChunkIndex videoChunkIndex = videoTrack.getChunkIndex();
    final int[] videoKeyFrames = videoChunkIndex.getKeyFrameSubset();
    videoTrack.setKeyFrames(videoKeyFrames);
    final int[] seekKeyFrames = videoChunkIndex.isAllKeyFrames() ?
            videoChunkIndex.getKeyFrameSubset(videoTrack.clock.durationUs, 3) :
            videoKeyFrames;
    long[] seekOffsets = videoChunkIndex.getIndexPositions(seekKeyFrames);

    int[][] seekIndexArrays = new int[streamHandlers.length][];
    seekIndexArrays[videoTrackId] = seekKeyFrames;
    for (final StreamHandler streamHandler : streamHandlers) {
      if (streamHandler != null) {
        final int id = streamHandler.getId();
        if (id != videoTrackId) {
          final ChunkIndex chunkIndex = streamHandler.getChunkIndex();
          seekIndexArrays[id] = chunkIndex.getIndices(seekOffsets);
          chunkIndex.release();
        }
      }
    }
    final AviSeekMap seekMap = new AviSeekMap(videoTrackId, videoTrack.clock.durationUs,
            videoTrack.getChunkIndex().getChunkCount(), seekOffsets, seekIndexArrays);

    i("Video chunks=" + videoChunkIndex.getChunkCount() + " us=" + seekMap.getDurationUs());

    fixTimings();

    setSeekMap(seekMap);
  }

  @VisibleForTesting
  void setSeekMap(AviSeekMap aviSeekMap) {
    this.aviSeekMap = aviSeekMap;
    output.seekMap(aviSeekMap);
  }

  @Nullable
  public static ListBox readHeaderList(ExtractorInput input) throws IOException {
    final ByteBuffer byteBuffer = getAviBuffer(input, 20);
    if (byteBuffer == null) {
      return null;
    }
    input.skipFully(20);
    final int listSize = byteBuffer.getInt();
    final ListBox listBox = ListBox.newInstance(listSize, new BoxFactory(), input);
    if (listBox.getListType() != ListBox.TYPE_HDRL) {
      return null;
    }
    return listBox;
  }

  long getDuration() {
    return durationUs;
  }

  @Override
  public void init(@NonNull ExtractorOutput output) {
    this.state = STATE_READ_TRACKS;
    this.output = output;
  }

  @VisibleForTesting
  StreamHandler parseStream(final ListBox streamList, int streamId) {
    final StreamHeaderBox streamHeader = streamList.getChild(StreamHeaderBox.class);
    final StreamFormatBox streamFormat = streamList.getChild(StreamFormatBox.class);
    if (streamHeader == null) {
      w("Missing Stream Header");
      return null;
    }
    //i(streamHeader.toString());
    if (streamFormat == null) {
      w("Missing Stream Format");
      return null;
    }
    final long durationUs = streamHeader.getDurationUs();
    //Initial estimate
    final int length = streamHeader.getLength();
    final Format.Builder builder = new Format.Builder();
    builder.setId(streamId);
    final int suggestedBufferSize = streamHeader.getSuggestedBufferSize();
    if (suggestedBufferSize != 0) {
      builder.setMaxInputSize(suggestedBufferSize);
    }
    final StreamNameBox streamName = streamList.getChild(StreamNameBox.class);
    if (streamName != null) {
      builder.setLabel(streamName.getName());
    }
    final ChunkClock clock = new ChunkClock(durationUs, length);
    final StreamHandler streamHandler;
    if (streamHeader.isVideo()) {
      final VideoFormat videoFormat = streamFormat.getVideoFormat();
      final String mimeType = videoFormat.getMimeType();
      if (mimeType == null) {
        Log.w(TAG, "Unknown FourCC: " + toString(videoFormat.getCompression()));
        return null;
      }
      final TrackOutput trackOutput = output.track(streamId, C.TRACK_TYPE_VIDEO);
      builder.setWidth(videoFormat.getWidth());
      builder.setHeight(videoFormat.getHeight());
      builder.setFrameRate(streamHeader.getFrameRate());
      builder.setSampleMimeType(mimeType);

      if (MimeTypes.VIDEO_H264.equals(mimeType)) {
        streamHandler = new AvcStreamHandler(streamId, trackOutput, clock, builder);
      } else if (MimeTypes.VIDEO_MP4V.equals(mimeType)) {
        streamHandler = new Mp4VStreamHandler(streamId, trackOutput, clock, builder);
      } else {
        streamHandler = new StreamHandler(streamId, StreamHandler.TYPE_VIDEO, trackOutput, clock);
      }
      trackOutput.format(builder.build());
      this.durationUs = durationUs;
    } else if (streamHeader.isAudio()) {
      final AudioFormat audioFormat = streamFormat.getAudioFormat();
      final TrackOutput trackOutput = output.track(streamId, C.TRACK_TYPE_AUDIO);
      final String mimeType = audioFormat.getMimeType();
      builder.setSampleMimeType(mimeType);
      builder.setChannelCount(audioFormat.getChannels());
      builder.setSampleRate(audioFormat.getSamplesPerSecond());
      final int bytesPerSecond = audioFormat.getAvgBytesPerSec();
      if (bytesPerSecond != 0) {
        builder.setAverageBitrate(bytesPerSecond * 8);
      }
      if (MimeTypes.AUDIO_RAW.equals(mimeType)) {
        final short bps = audioFormat.getBitsPerSample();
        if (bps == 8) {
          builder.setPcmEncoding(C.ENCODING_PCM_8BIT);
        } else if (bps == 16){
          builder.setPcmEncoding(C.ENCODING_PCM_16BIT);
        }
      }
      if (MimeTypes.AUDIO_AAC.equals(mimeType) && audioFormat.getCbSize() > 0) {
        builder.setInitializationData(Collections.singletonList(audioFormat.getCodecData()));
      }
      trackOutput.format(builder.build());
      if (MimeTypes.AUDIO_MPEG.equals(mimeType)) {
        streamHandler = new MpegAudioStreamHandler(streamId, trackOutput, clock,
            audioFormat.getSamplesPerSecond());
      } else {
        streamHandler = new StreamHandler(streamId, StreamHandler.TYPE_AUDIO,
            trackOutput, clock);
      }
      streamHandler.setKeyFrames(ChunkIndex.ALL_KEY_FRAMES);
    }else {
      streamHandler = null;
    }
    if (streamHandler != null) {
      final IndexBox indexBox = streamList.getChild(IndexBox.class);
      if (indexBox != null) {
        streamHandler.setIndexBox(indexBox);
      }
    }
    return streamHandler;
  }

  private int readTracks(ExtractorInput input) throws IOException {
    final ListBox headerList = readHeaderList(input);
    if (headerList == null) {
      throw new IOException("AVI Header List not found");
    }
    aviHeader = headerList.getChild(AviHeaderBox.class);
    if (aviHeader == null) {
      throw new IOException("AviHeader not found");
    }
    streamHandlers = new StreamHandler[aviHeader.getStreams()];
    //This is usually wrong, so it will be overwritten by video if present
    durationUs = aviHeader.getTotalFrames() * (long)aviHeader.getMicroSecPerFrame();

    int streamId = 0;
    for (Box box : headerList.getChildren()) {
      if (box instanceof ListBox && ((ListBox) box).getListType() == ListBox.TYPE_STRL) {
        final ListBox streamList = (ListBox) box;
        streamHandlers[streamId] = parseStream(streamList, streamId);
        streamId++;
      }
    }
    output.endTracks();
    state = STATE_FIND_MOVI;
    return RESULT_CONTINUE;
  }

  int findMovi(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    ByteBuffer byteBuffer = allocate(12);
    input.readFully(byteBuffer.array(), 0,12);
    final int tag = byteBuffer.getInt();
    final long size = getUInt(byteBuffer);
    final long position = input.getPosition();
    //-4 because we over read for the LIST type
    long nextBox = alignPosition(position + size - 4);
    if (tag == ListBox.LIST) {
      final int listType = byteBuffer.getInt();
      if (listType == MOVI) {
        moviOffset = position - 4;
        moviEnd = moviOffset + size;
        final StreamHandler streamHandler = getIndexStreamHandler();
        //Prioritize OpenDML if present
        if (streamHandler != null) {
          state = STATE_READ_INDX;
          final IndexBox indexBox = Objects.requireNonNull(streamHandler.getIndexBox());
          nextBox = indexBox.getEntry(0);
        } else if (aviHeader.hasIndex()) {
          state = STATE_READ_IDX1;
        } else {
          //No Index!
          output.seekMap(new SeekMap.Unseekable(getDuration()));
          return seekMovi(seekPosition);
        }
      }
    }
    seekPosition.position = nextBox;
    return RESULT_SEEK;
  }

  @VisibleForTesting
  StreamHandler getVideoTrack() {
    for (@Nullable StreamHandler streamHandler : streamHandlers) {
      if (streamHandler != null && streamHandler.isVideo()) {
        return streamHandler;
      }
    }
    return null;
  }

  /**
   * Walk the StreamHandlers looking for one with an unprocessed IndexBox
   */
  @Nullable
  StreamHandler getIndexStreamHandler() {
    for (@Nullable StreamHandler streamHandler : streamHandlers) {
      if (streamHandler != null && streamHandler.getIndexBox() != null) {
        return streamHandler;
      }
    }
    return null;
  }

  void fixTimings() {
    for (@Nullable final StreamHandler streamHandler : streamHandlers) {
      if (streamHandler != null) {
        if (streamHandler.isAudio()) {
          final long streamDurationUs = streamHandler.getClock().durationUs;
          final ChunkIndex chunkIndex = streamHandler.getChunkIndex();
          final int chunks = chunkIndex.getChunkCount();
          i("Audio #" + streamHandler.getId() + " chunks: " + chunks + " us=" + streamDurationUs +
              " size=" + streamHandler.size);
          final ChunkClock linearClock = streamHandler.getClock();
          //If the audio track duration is off from the video by >5 % recalc using video
          if ((streamDurationUs - durationUs) / (float)durationUs > .05f) {
            w("Audio #" + streamHandler.getId() + " duration is off using videoDuration");
            linearClock.setDuration(durationUs);
          }
          linearClock.setChunks(chunks);
          final int keyFrameCount = chunkIndex.keyFrameCount;
          if (!chunkIndex.isAllKeyFrames()) {
            w("Audio is not all key frames chunks=" + chunks + " keyFrames=" +
                    keyFrameCount);
          }
        }
      }
    }
  }

  /**
   * Reads the index and sets the keyFrames and creates the SeekMap
   */
  void readIdx1(ExtractorInput input, int indexSize) throws IOException {
    if (indexSize < 16) {
      output.seekMap(new SeekMap.Unseekable(getDuration()));
      w("Index too short");
      return;
    }
    final ByteBuffer indexByteBuffer = allocate(indexSize);
    input.readFully(indexByteBuffer.array(), 0, indexSize);
    //Work-around a bug where the offset is from the start of the file, not "movi"
    if (indexByteBuffer.getInt(8) < moviOffset) {
      for (StreamHandler streamHandler : streamHandlers) {
        if (streamHandler != null) {
          streamHandler.getChunkIndex().setBaseOffset(moviOffset);
        }
      }
    }

    while (indexByteBuffer.remaining() >= 16) {
      final int chunkId = indexByteBuffer.getInt(); //0
      final int flags = indexByteBuffer.getInt(); //4
      final int offset = indexByteBuffer.getInt(); //8
      indexByteBuffer.getInt(); // 12 Size

      final StreamHandler streamHandler = getStreamHandler(chunkId);
      if (streamHandler != null) {
        streamHandler.getChunkIndex().add(offset, (flags & AVIIF_KEYFRAME) == AVIIF_KEYFRAME);
      }
    }
    buildSeekMap();
  }

  private void readIndx(@NonNull ExtractorInput input, int indxSize) throws IOException {
    // +8 for chunkId and size
    ByteBuffer byteBuffer = allocate(indxSize + 8);
    input.readFully(byteBuffer.array(), 0, byteBuffer.capacity());
    byteBuffer.position(byteBuffer.position() + 10); //Skip fourCC, size, longs per entry, indexSubType
    final byte indexSubType = byteBuffer.get();
    if (indexSubType != 0) {
      throw new IOException("Expected IndexSubType 0 got " + indexSubType);
    }
    final byte indexType = byteBuffer.get();
    if (indexType != IndexBox.AVI_INDEX_OF_CHUNKS) {
      throw new IOException("Expected IndexType 1 got " + indexType);
    }
    final int entriesInUse = byteBuffer.getInt();
    final int chunkId = byteBuffer.getInt();
    final StreamHandler streamHandler = getStreamHandler(chunkId);
    if (streamHandler == null) {
      throw new IOException("Could not find StreamHandler");
    }
    final ChunkIndex chunkIndex = streamHandler.getChunkIndex();
    //baseOffset does not include the chunk header, so -8 to be compatible with IDX1
    final long baseOffset = byteBuffer.getLong() - 8;
    chunkIndex.setBaseOffset(baseOffset);
    byteBuffer.position(byteBuffer.position() + 4); // Skip reserved

    for (int i=0;i<entriesInUse;i++) {
      final int offset = byteBuffer.getInt();
      final int notKeyFrame = byteBuffer.getInt() & Integer.MIN_VALUE;
      chunkIndex.add(offset, notKeyFrame == 0);
    }
    streamHandler.setIndexBox(null);
  }

  @Nullable
  @VisibleForTesting
  StreamHandler getStreamHandler(int chunkId) {
    for (@Nullable StreamHandler streamHandler : streamHandlers) {
      if (streamHandler != null && streamHandler.handlesChunkId(chunkId)) {
        return streamHandler;
      }
    }
    return null;
  }

  int readChunks(@NonNull ExtractorInput input) throws IOException {
    if (streamHandler != null) {
      if (streamHandler.resume(input)) {
        streamHandler = null;
        alignInput(input);
      }
    } else {
      final int toRead = 8;
      ByteBuffer byteBuffer = allocate(toRead);
      final byte[] bytes = byteBuffer.array();
      alignInput(input);
      input.readFully(bytes, 0, toRead);
      //This is super inefficient, but should be rare
      while (bytes[0] == 0) {
        for (int i=1;i<toRead;i++) {
          bytes[i - 1] = bytes[i];
        }
        int read = input.read(bytes, toRead - 1, 1);
        if (read == C.RESULT_END_OF_INPUT) {
          return RESULT_END_OF_INPUT;
        }
      }
      final int chunkId = byteBuffer.getInt();
      if (chunkId == ListBox.LIST) {
        input.skipFully(8);
        return RESULT_CONTINUE;
      }
      final int size = byteBuffer.getInt();
      if (chunkId == JUNK) {
        input.skipFully(size);
        alignInput(input);
        return RESULT_CONTINUE;
      }
      final StreamHandler streamHandler = getStreamHandler(chunkId);
      if (streamHandler == null) {
        input.skipFully(size);
        alignInput(input);
        w("Unknown tag=" + toString(chunkId) + " pos=" + (input.getPosition() - 8)
            + " size=" + size + " moviEnd=" + moviEnd);
        return RESULT_CONTINUE;
      }
      if (streamHandler.newChunk(size, input)) {
        alignInput(input);
      } else {
        this.streamHandler = streamHandler;
      }
    }
    if (input.getPosition() >= moviEnd) {
      return C.RESULT_END_OF_INPUT;
    }
    return RESULT_CONTINUE;
  }

  private int setPosition(@NonNull ExtractorInput input, @NonNull PositionHolder positionHolder, long position) {
    final long skip = position - input.getPosition();
    if (skip == 0) {
      return RESULT_CONTINUE;
    } else if (skip < 0 || skip > RELOAD_MINIMUM_SEEK_DISTANCE) {
      positionHolder.position = position;
      return RESULT_SEEK;
    } else {
      pendingSkip = (int)skip;
      return RESULT_CONTINUE;
    }
  }

  private int skip(@NonNull ExtractorInput input, @NonNull PositionHolder positionHolder, int size) {
    return setPosition(input,positionHolder, input.getPosition() + size);
  }

  private int seekMovi(@NonNull PositionHolder positionHolder) {
    positionHolder.position = moviOffset + 4;
    state = STATE_READ_CHUNKS;
    return RESULT_SEEK;
  }

  @Override
  public int read(@NonNull ExtractorInput input, @NonNull PositionHolder positionHolder) throws IOException {
    if (pendingSkip != C.POSITION_UNSET) {
      input.skipFully(pendingSkip);
      pendingSkip = C.POSITION_UNSET;
      return RESULT_CONTINUE;
    }
    switch (state) {
      case STATE_READ_CHUNKS:
        return readChunks(input);
      case STATE_SEEK_START:
        state = STATE_READ_CHUNKS;
        positionHolder.position = moviOffset + 4;
        return RESULT_SEEK;
      case STATE_READ_TRACKS:
        return readTracks(input);
      case STATE_FIND_MOVI:
        return findMovi(input, positionHolder);
      case STATE_READ_IDX1: {
        ChunkPeeker chunkPeeker = new ChunkPeeker();
        chunkPeeker.peek(input);
        if (chunkPeeker.getChunkId() == IDX1) {
          readIdx1(input, chunkPeeker.getSize());
          return seekMovi(positionHolder);
        } else {
          return skip(input, positionHolder, chunkPeeker.getSize());
        }
      }
      case STATE_READ_INDX: {
        ChunkPeeker chunkPeeker = new ChunkPeeker();
        chunkPeeker.peek(input);
        if ((chunkPeeker.getChunkId() & IndexBox.IX00_MASK) == IndexBox.IX00) {
          readIndx(input, chunkPeeker.getSize());
          StreamHandler streamHandler = getIndexStreamHandler();
          if (streamHandler == null) {
            buildSeekMap();
            return seekMovi(positionHolder);
          } else {
            return setPosition(input, positionHolder,
                    Objects.requireNonNull(streamHandler.getIndexBox()).getEntry(0));
          }
        } else {
          return skip(input, positionHolder, chunkPeeker.getSize());
        }
      }
    }
    return RESULT_CONTINUE;
  }

  @Override
  public void seek(long position, long timeUs) {
    //i("Seek pos=" + position +", us="+timeUs);
    streamHandler = null;
    if (position <= 0) {
      if (moviOffset != 0) {
        setIndexes(new int[streamHandlers.length]);
        state = STATE_SEEK_START;
      }
    } else {
      if (aviSeekMap != null) {
        setIndexes(aviSeekMap.getIndexes(position));
      }
    }
  }

  private void setIndexes(@NonNull int[] indexes) {
    for (@Nullable StreamHandler streamHandler : streamHandlers) {
      if (streamHandler != null) {
        streamHandler.setIndex(indexes[streamHandler.getId()]);
      }
    }
  }

  @Override
  public void release() {
    //Intentionally blank
  }

  @VisibleForTesting
  void setChunkHandlers(StreamHandler[] streamHandlers) {
    this.streamHandlers = streamHandlers;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  void setAviHeader(final AviHeaderBox aviHeaderBox) {
    aviHeader = aviHeaderBox;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  void setMovi(final int offset, final int end) {
    moviOffset = offset;
    moviEnd = end;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  StreamHandler getStreamHandler() {
    return streamHandler;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  void setChunkHandler(final StreamHandler streamHandler) {
    this.streamHandler = streamHandler;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  long getMoviOffset() {
    return moviOffset;
  }

  private static void w(String message) {
    Log.w(TAG, message);
  }

  private static void i(String message) {
    Log.i(TAG, message);
  }
  static class ChunkPeeker {
    final ByteBuffer byteBuffer = allocate(8);

    public void peek(@NonNull ExtractorInput input) throws IOException {
      input.peekFully(byteBuffer.array(), 0,  8);
    }

    public int getChunkId() {
      return byteBuffer.getInt(0);
    }

    public int getSize() {
      return byteBuffer.getInt(4);
    }

  }
}
