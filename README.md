# Media3Avi
Library with improved AVI support for Jetpack Media3 (formerly ExoPlayer)

Port of my original AVI library to support Media3.  Although ExoPlayer/Media3 now has AVI support, it is quite limited.  This should support most AVI files.

## Usage
- Add Media3 to your app as described.  https://developer.android.com/jetpack/androidx/releases/media3
- Add the dependencies to your project.  See Using the Artifact for details.
  - Or just add the AAR as a module.

Construct ExoPlayer with these options: 

### Kotlin
```kotlin
val exoPlayer = ExoPlayer.Builder(context)
    // Core AVI Support
    .setMediaSourceFactory(DefaultMediaSourceFactory(context, AviExtractorsFactory()))
    // Optional MJPEG Support
    .setRenderersFactory(MjpegRenderersFactory(context))
    .build()
```

### Java
```java
ExoPlayer exoPlayer = new ExoPlayer.Builder(context)
    // Core AVI Support
    .setMediaSourceFactory(new DefaultMediaSourceFactory(context, new AviExtractorsFactory()))
    // Optional MJPEG Support
    .setRenderersFactory(new MjpegRenderersFactory(context))
    .build();
```

### Codec Support Notes
- MP4V ASP (Xvid/Divx) support varies a lot by manufacturer.  Pixel devices generally have limited support, where Samsung/Moto seem to have better support.
- MS MP4V (fourCC: MP42) is common in Avi files, but is not supported by any Android device I've found. 
- AC3/DTS are common in Avi files, but many devices (Pixels) don't support these.  You can build the ffmpeg audio extension for support on these devices.  See Media3/ExoPlayer docs for more. 

## Using the artifact
Important:  Version 2.3.0 on will be using jitpack.io.  As such, there is a new artifact ID.

`implementation 'com.github.dburckh:Media3Avi:2.3.0'`

You will need to update your setting.gradle as below.  More here: https://jitpack.io/#dburckh/Media3Avi
```groovy
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
  }
}
```
Upgraders should remove the references to 'maven.pkg.github.com/dburckh' in your root build.gradle.  You will no longer require GPR_USER/GPR_API_KEY environment variables either.

### Change Log
2.3.0
- Update to AndroidX Media 1.1.1
- Support jitpack.io

2.2.1
- Add more H264 FOURCCs
- Work-around race issue with BitmapFactoryVideoRenderer

2.2
- Refactor BitmapFactoryVideoRenderer
- Fix for non-MP3 data in MP3 stream and empty MP3 chunks

2.1.2
- Fix bugs with Mp4V BVOP

2.1.1
- Fix timings for Mp4V with BVOP

2.1
- Support for audio with variable chunk sizes
- Update to Media3-beta03
- Minor seek fixes

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
