package com.homesoft.exo.video;

import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.sample;

import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Clock;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.test.utils.FakeSampleStream;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.collect.ImmutableList;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class BitmapFactoryDecoderTest {
    private static final Format VIDEO_MJPEG =
            new Format.Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_MJPEG)
                    .setWidth(854)
                    .setHeight(480)
                    .build();
    private Looper testMainLooper;
    private Surface surface;
    private VideoRendererEventListener eventListener;
    private MockBitmapFactoryRenderer renderer;

    @Before
    public void setUp() throws Exception {
        testMainLooper = Looper.getMainLooper();
        eventListener = new VideoRendererEventListener() {
            @Override
            public void onVideoEnabled(DecoderCounters counters) {
                VideoRendererEventListener.super.onVideoEnabled(counters);
            }
        };
        renderer = new MockBitmapFactoryRenderer(/* allowedJoiningTimeMs= */ 0,
                /* eventHandler= */ new Handler(testMainLooper),
                /* eventListener= */ eventListener,
                /* maxDroppedFramesToNotify= */ 1);
        surface = new Surface(new SurfaceTexture(/* texName= */ 0));
        renderer.handleMessage(Renderer.MSG_SET_VIDEO_OUTPUT, surface);
    }
    @Test
    public void render_stockBitmap() throws Exception {
        final Context context = ApplicationProvider.getApplicationContext();
        final AssetManager assetManager = context.getAssets();
        final InputStream in = assetManager.open("media/jpeg/bbb_854x480.jpg");
        final byte[] buffer = new byte[in.available()];
        in.read(buffer);
        FakeSampleStream fakeSampleStream =
                new FakeSampleStream(
                        new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
                        /* mediaSourceEventDispatcher= */ null,
                        DrmSessionManager.DRM_UNSUPPORTED,
                        new DrmSessionEventListener.EventDispatcher(),
                        /* initialFormat= */ VIDEO_MJPEG,
                        ImmutableList.of(
                                sample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME, buffer), // First buffer.
                                END_OF_STREAM_ITEM));
        fakeSampleStream.writeData(/* startPositionUs= */ 0);
        fakeSampleStream.seekToUs(30_000, /* allowTimeBeyondBuffer= */ true);
        renderer.init(/* index= */ 0, PlayerId.UNSET, Clock.DEFAULT);
        renderer.handleMessage(Renderer.MSG_SET_VIDEO_OUTPUT, surface);
        renderer.enable(
                RendererConfiguration.DEFAULT,
                new Format[] {VIDEO_MJPEG},
                fakeSampleStream,
                /* positionUs= */ 0,
                /* joining= */ false,
                /* mayRenderStartOfStream= */ true,
                /* startPositionUs= */ 0,
                /* offsetUs */ 0,
                /* mediaPeriodId= */ new MediaSource.MediaPeriodId(new Object()));

        renderer.setDecoderOutputMode(C.VIDEO_OUTPUT_MODE_SURFACE_YUV);
        renderer.start();
        renderer.setCurrentStreamFinal();
        renderer.render(0, SystemClock.elapsedRealtime() * 1000);
        int posUs = 30_000;
        while (!renderer.isEnded()) {
            renderer.render(posUs, SystemClock.elapsedRealtime() * 1000);
            posUs += 40_000;
        }
        shadowOf(testMainLooper).idle();
        Assert.assertEquals(854, renderer.lastBitmap.getWidth());
    }
}
