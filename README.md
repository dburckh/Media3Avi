# Media3Avi
Library with improved AVI support for Jetpack Media3 (formerly ExoPlayer)

Port of my original AVI library to support Media3.  Although ExoPlayer/Media3 now has AVI support, it is quite limited.  This should support most AVI files.

## Usage
- Add Media3 to your app as described.  https://developer.android.com/jetpack/androidx/releases/media3
- Add the media3-avi library to your project (currently source only, no .aar)

Construct ExoPlayer with these options: 

### {.tabset}
####Kotlin
```kotlin
val exoPlayer = ExoPlayer.Builder(context)
    // Core AVI Support
    .setMediaSourceFactory(DefaultMediaSourceFactory(context,AviExtractorsFactory()))
    // Optional MJPEG Support
    .setRenderersFactory(MjpegRenderersFactory(context))
    .build()
```

####Java
```java
ExoPlayer exoPlayer = new ExoPlayer.Builder(context)
    // Core AVI Support
    .setMediaSourceFactory(new DefaultMediaSourceFactory(context, new AviExtractorsFactory()))
    // Optional MJPEG Support
    .setRenderersFactory(new MjpegRenderersFactory(context))
    .build();
```
###

###Change Log
2.0
- OpenDML Support
  - RIFF-AVIX extension
  - Index of Indexes (indx)
  - Index of Chunks (ix??)
- AviExtractor Rewrite
- ExtractorAsserts tests

1.1
- Crash Fixes

1.0 
- Separate Library from ExoPlayer