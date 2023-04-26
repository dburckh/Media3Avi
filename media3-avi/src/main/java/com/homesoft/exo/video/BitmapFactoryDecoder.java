package com.homesoft.exo.video;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderException;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;

import java.nio.ByteBuffer;

public class BitmapFactoryDecoder extends SimpleDecoder<DecoderInputBuffer, BitmapDecoderOutputBuffer, DecoderException> {
    private static final String TAG = "BitmapFactoryDecoder";
    private volatile @C.VideoOutputMode int outputMode;

    protected BitmapFactoryDecoder(int buffers) {
        super(new DecoderInputBuffer[buffers], new BitmapDecoderOutputBuffer[buffers]);
    }

    @NonNull
    @Override
    protected DecoderInputBuffer createInputBuffer() {
        return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    }

    @NonNull
    @Override
    protected BitmapDecoderOutputBuffer createOutputBuffer() {
        return new BitmapDecoderOutputBuffer(outputBuffer -> {
            if (outputBuffer instanceof BitmapDecoderOutputBuffer) {
                BitmapFactoryDecoder.this.releaseOutputBuffer((BitmapDecoderOutputBuffer)outputBuffer);
            }
        });
    }

    @NonNull
    @Override
    protected DecoderException createUnexpectedDecodeException(Throwable error) {
        return new DecoderException(error);
    }

    /**
     * Sets the output mode for frames rendered by the decoder.
     *
     * @param outputMode The output mode.
     */
    public void setOutputMode(@C.VideoOutputMode int outputMode) {
        this.outputMode = outputMode;
    }

    @Nullable
    @Override
    protected DecoderException decode(DecoderInputBuffer inputBuffer, @NonNull BitmapDecoderOutputBuffer outputBuffer, boolean reset) {
        outputBuffer.init(inputBuffer.timeUs, outputMode, null);
        ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
        final BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        opts.inBitmap = outputBuffer.getBitmap();
        opts.inJustDecodeBounds = inputBuffer.isDecodeOnly() || outputMode != C.VIDEO_OUTPUT_MODE_SURFACE_YUV;
        final Bitmap bitmap = BitmapFactory.decodeByteArray(inputData.array(), inputData.arrayOffset(), inputData.limit(), opts);
        if (bitmap == null || opts.inJustDecodeBounds) {
            outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
        } else {
            outputBuffer.setBitmap(bitmap);
            outputBuffer.format = inputBuffer.format;
            outputBuffer.width = bitmap.getWidth();
            outputBuffer.height = bitmap.getHeight();
        }
        return null;
    }

    @NonNull
    @Override
    public String getName() {
        return TAG;
    }
}
