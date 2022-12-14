package com.homesoft.exo.extractor.avi;

import android.content.Context;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.extractor.MpegAudioUtil;
import androidx.media3.test.utils.FakeExtractorInput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
public class MpegAudioStreamHandlerTest {
  private static final int FPS = 24;
  private Format MP3_FORMAT = new Format.Builder().setChannelCount(2).
      setSampleMimeType(MimeTypes.AUDIO_MPEG).setSampleRate(44100).build();
  private static final long CHUNK_MS = C.MICROS_PER_SECOND / FPS;
  private final MpegAudioUtil.Header header = new MpegAudioUtil.Header();
  private FakeTrackOutput fakeTrackOutput;
  private MpegAudioStreamHandler mpegAudioChunkHandler;
  private byte[] mp3Frame;
  private long frameUs;

  @Before
  public void before() throws IOException {
    fakeTrackOutput = new FakeTrackOutput(false);
    fakeTrackOutput.format(MP3_FORMAT);
    mpegAudioChunkHandler = new MpegAudioStreamHandler(0, C.MICROS_PER_SECOND, fakeTrackOutput,
         MP3_FORMAT.sampleRate);

    if (mp3Frame == null) {
      final Context context = ApplicationProvider.getApplicationContext();
      mp3Frame = TestUtil.getByteArray(context,"media/avi/frame.mp3.dump");
      header.setForHeaderData(ByteBuffer.wrap(mp3Frame).getInt());
      //About 26ms
      frameUs = header.samplesPerFrame * C.MICROS_PER_SECOND / header.sampleRate;
    }
  }

  @Test
  public void newChunk_givenSingleFrame() throws IOException {
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(mp3Frame).build();

    mpegAudioChunkHandler.setRead(0L, mp3Frame.length);
    mpegAudioChunkHandler.read(input);
    Assert.assertArrayEquals(mp3Frame, fakeTrackOutput.getSampleData(0));
    Assert.assertEquals(frameUs, mpegAudioChunkHandler.getTimeUs());
  }

  @Test
  public void newChunk_givenSeekAndFragmentedFrames() throws IOException {
    ByteBuffer byteBuffer = ByteBuffer.allocate(mp3Frame.length * 2);
    byteBuffer.put(mp3Frame, mp3Frame.length / 2, mp3Frame.length / 2);
    byteBuffer.put(mp3Frame);
    final int remainder = byteBuffer.remaining();
    byteBuffer.put(mp3Frame, 0, remainder);

    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).
        build();

    mpegAudioChunkHandler.timeUs = CHUNK_MS; //Seek
    mpegAudioChunkHandler.setRead(0L, byteBuffer.capacity());
    Assert.assertFalse(mpegAudioChunkHandler.read(input));
    Assert.assertArrayEquals(mp3Frame, fakeTrackOutput.getSampleData(0));
    Assert.assertEquals(frameUs + CHUNK_MS, mpegAudioChunkHandler.getTimeUs());

    Assert.assertTrue(mpegAudioChunkHandler.read(input));
    Assert.assertEquals(header.frameSize - remainder, mpegAudioChunkHandler.getFrameRemaining());
  }

  @Test
  public void newChunk_givenTwoFrames() throws IOException {
    ByteBuffer byteBuffer = ByteBuffer.allocate(mp3Frame.length * 2);
    byteBuffer.put(mp3Frame);
    byteBuffer.put(mp3Frame);

    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).
        build();
    mpegAudioChunkHandler.setRead(0L, byteBuffer.capacity());
    Assert.assertFalse(mpegAudioChunkHandler.read(input));
    Assert.assertEquals(1, fakeTrackOutput.getSampleCount());
    Assert.assertEquals(0L, fakeTrackOutput.getSampleTimeUs(0));

    Assert.assertTrue(mpegAudioChunkHandler.read(input));
    Assert.assertEquals(2, fakeTrackOutput.getSampleCount());
    Assert.assertEquals(frameUs, fakeTrackOutput.getSampleTimeUs(1));
  }
}
