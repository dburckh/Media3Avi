package com.homesoft.exo.video;

import android.graphics.Bitmap;
import android.os.Handler;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

public class MockBitmapFactoryRenderer extends BitmapFactoryVideoRenderer {
    Bitmap lastBitmap;
    public MockBitmapFactoryRenderer(long allowedJoiningTimeMs, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    @Override
    protected void renderOutputBufferToSurface(@NonNull VideoDecoderOutputBuffer outputBuffer, @NonNull Surface surface) {
        if (outputBuffer instanceof BitmapDecoderOutputBuffer) {
            lastBitmap = ((BitmapDecoderOutputBuffer) outputBuffer).getBitmap();
        }
        super.renderOutputBufferToSurface(outputBuffer, surface);
        synchronized (this) {
            notify();
        }
    }
}

