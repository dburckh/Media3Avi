# Media3Avi
Library with improved AVI support for Jetpack Media3 (ExoPlayer)

Port of my original AVI library to support Media3.  Although Media3 now has AVI support, it is quite limited.  This should support most AVI files.

## Usage
Add the media3-avi library to your project.

Construct ExoPlayer with these options:

```java
ExoPlayer exoPlayer = new ExoPlayer.Builder(context)
    // Core AVI Support
    .setMediaSourceFactory(new DefaultMediaSourceFactory(context, new AviExtractorsFactory()))
    // Optional MJPEG Support
    .setRenderersFactory(new MjpegRenderersFactory(context))
    .build();
```
