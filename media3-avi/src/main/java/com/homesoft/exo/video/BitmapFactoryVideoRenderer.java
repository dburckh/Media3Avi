package com.homesoft.exo.video;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.video.DecoderVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

public class BitmapFactoryVideoRenderer extends DecoderVideoRenderer {
    static final String TAG = "BitmapFactoryRenderer2";
    private final Rect rect = new Rect();

    private BitmapFactoryDecoder decoder;

    /**
     * @param allowedJoiningTimeMs     The maximum duration in milliseconds for which this video renderer
     *                                 can attempt to seamlessly join an ongoing playback.
     * @param eventHandler             A handler to use when delivering events to {@code eventListener}. May be
     *                                 null if delivery of events is not required.
     * @param eventListener            A listener of events. May be null if delivery of events is not required.
     * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
     *                                 invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
     */
    public BitmapFactoryVideoRenderer(long allowedJoiningTimeMs, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    @NonNull
    @Override
    protected BitmapFactoryDecoder createDecoder(@NonNull Format format, @Nullable CryptoConfig cryptoConfig) {
        decoder = new BitmapFactoryDecoder(2);
        return decoder;
    }

    @Override
    protected void renderOutputBufferToSurface(@NonNull VideoDecoderOutputBuffer outputBuffer, @NonNull Surface surface) {
        if (outputBuffer instanceof BitmapDecoderOutputBuffer) {
            final Bitmap bitmap = ((BitmapDecoderOutputBuffer) outputBuffer).getBitmap();
            if (bitmap != null) {
                final Canvas canvas;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    canvas = surface.lockHardwareCanvas();
                } else {
                    canvas = surface.lockCanvas(null);
                }
                rect.set(0,0,canvas.getWidth(), canvas.getHeight());
                canvas.drawBitmap(bitmap, null, rect, null);
                surface.unlockCanvasAndPost(canvas);
            }
        }
        outputBuffer.release();
    }

    @Override
    protected void setDecoderOutputMode(int outputMode) {
        if (decoder != null) {
            decoder.setOutputMode(outputMode);
        }
    }

    @NonNull
    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public int supportsFormat(Format format) throws ExoPlaybackException {
        //Technically could support any format BitmapFactory supports
        if (MimeTypes.VIDEO_MJPEG.equals(format.sampleMimeType)) {
            return RendererCapabilities.create(C.FORMAT_HANDLED);
        }
        return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
    }
}
