# LensCast

LensCast turns an Android phone into a local network camera with access to
more Camera2 lenses than typical IP camera apps expose.

The project is built for cases where an old phone should act as a flexible
monitoring camera: rear main camera, ultra-wide, telephoto/periscope cameras,
manual focus controls, MJPEG browser preview, RTSP output, AAC audio, snapshots,
and an optional timestamp/battery overlay.

## Why LensCast

Most Android IP camera apps expose only the default front/back camera choices.
LensCast scans Camera2 devices and can use many physical or vendor-exposed
camera IDs, which is useful on multi-camera phones where the ultra-wide or
telephoto lens is otherwise unavailable.

## Current Features

- Camera2 lens scanning and local verified camera list
- Native Android control panel
- Web control panel on the phone's local IP
- MJPEG stream for browser/NVR compatibility
- RTSP stream endpoint
- AAC audio stream
- Snapshot endpoint that saves the captured frame
- Resolution, frame rate, quality, zoom, focus, exposure, and flashlight controls
- Optional overlay with date, time, battery level, and charging state
- Landscape live mode with black-screen power saving

## Network Endpoints

When live streaming is enabled, LensCast shows the current URLs in the app.
The default ports in this build are:

- Web dashboard: `http://<phone-ip>:41737/`
- MJPEG stream: `http://<phone-ip>:41737/video.mjpeg`
- Snapshot: `http://<phone-ip>:41737/snapshot.jpg`
- AAC audio: `http://<phone-ip>:41737/audio.aac`
- RTSP: `rtsp://<phone-ip>:8554/live`

## Build

Requirements:

- Android Studio or Android Gradle Plugin compatible toolchain
- JDK 17
- Android SDK 34
- Gradle 8.7 or compatible

This source snapshot does not include local SDK files or a Gradle wrapper.
Build with your local Gradle installation:

```powershell
gradle assembleDebug
```

The Android package name is currently:

```text
com.opencode.multilensipcam
```

## Release APK

The first public release is `v0.5.14-83`.

The APK attached to the GitHub release is a debug build intended for testing on
local devices. It is not Play Store signed.

## Notes

Lens availability depends on the device vendor's Camera2 implementation. Some
phones expose many useful physical camera IDs; others expose only a limited set.

LensCast is intended for local network use. Do not expose the web dashboard or
stream endpoints directly to the public internet without adding your own access
control and network protection.
