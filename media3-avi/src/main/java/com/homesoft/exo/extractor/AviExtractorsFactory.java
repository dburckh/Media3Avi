package com.homesoft.exo.extractor;

import android.net.Uri;

import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;

import com.homesoft.exo.extractor.avi.AviExtractor;

import java.util.List;
import java.util.Map;

public class AviExtractorsFactory implements ExtractorsFactory {
    private final DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();

    @Override
    public Extractor[] createExtractors() {
        return patchAvi(defaultExtractorsFactory.createExtractors());
    }

    @Override
    public Extractor[] createExtractors(Uri uri, Map<String, List<String>> responseHeaders) {
        return patchAvi(defaultExtractorsFactory.createExtractors());
    }

    /**
     * Hack to work-around DefaultExtractorsFactory being final
     */
    private Extractor[] patchAvi(Extractor[] extractors) {
        for (int i=0;i<extractors.length;i++) {
            if (extractors[i] instanceof androidx.media3.extractor.avi.AviExtractor) {
                extractors[i] = new AviExtractor();
            }
        }
        return extractors;
    }

    /**
     * Get the underlying DefaultExtractorsFactory
     */
    public DefaultExtractorsFactory getDefaultExtractorsFactory() {
        return defaultExtractorsFactory;
    }
}
