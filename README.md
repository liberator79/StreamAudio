# StreamAudio

Share whatever your **Windows PC** or **Android phone** is playing with other phones or PCs on the
same Wi-Fi, with very little delay. One device hosts, the others listen.

Built with Kotlin Multiplatform and Compose Multiplatform (Android + Desktop/JVM).

**Full explanation:** [`docs/how-streamaudio-works.html`](docs/how-streamaudio-works.html) is a
self-contained guide (open it in a browser) covering the architecture, protocol, latency design,
platform details, and a walkthrough of every line of the source code.

## Features

- **Host from Windows**: captures system audio with WASAPI loopback (no "Stereo Mix" needed;
  Stereo Mix is used as a fallback).
- **Host from Android 10+**: captures other apps' audio with MediaProjection / AudioPlaybackCapture.
- **Listen on Android or Windows**: hosts on your network show up automatically; you can also type an IP.
- **Low, bounded latency**: 5 ms UDP packets and a jitter buffer that discards backlog, so delay
  can't build up. Choose *Low* (30 ms), *Balanced* (60 ms) or *Stable* (120 ms); the buffer also
  adapts automatically when Wi-Fi gets jittery.
- **Keeps playing with the screen off** (Android foreground services, wake lock and Wi-Fi locks).
- **Auto-resume**: listeners reconnect by themselves when the host restarts.
- **Light & dark themes**: follows the system, or pick Light/Dark in the app (remembered).
- **App icon**: adaptive Android icon (with Android 13 themed-icon support), notification
  icon, and desktop window icon.

## How it works (short version)

```
HOST                                                   LISTENER
AudioSource ─► HostEngine ─► UdpAudioServer ══UDP══►  network thread ─► jitter queue ─► playback thread ─► speaker
(WASAPI /      capture       listener list,  5 ms      receive, seq     30/60/120 ms     trims backlog,
 Stereo Mix /  thread        heartbeats,     packets   check, HELLO     + adaptive       paced by device
 Playback-                   discovery                 every 1 s
 Capture)                    ◄═════════════ HELLO / BYE / DISCOVER ═══════════
```

| Item | Value |
|---|---|
| Audio format | Raw PCM, 48 kHz, 16-bit, stereo (no codec: no encoder delay) |
| Packet | 240 frames = 960 bytes = 5 ms (fits one Wi-Fi frame, no IP fragmentation) |
| Transport | UDP port **50005**, DSCP EF (voice priority) |
| Bitrate | ~1.5 Mbit/s per listener |
| Discovery | UDP broadcast `DISCOVER` → host replies `HOST_INFO` |
| Keep-alive | Listener sends `HELLO` every 1 s; host drops listeners after 4 s of silence |

## Project layout

```
composeApp/src/
  commonMain/   shared code
    StreamProtocol.kt      audio format constants + packet format
    UdpAudioServer.kt      host networking (listeners, heartbeats, discovery replies)
    HostEngine.kt          host capture thread → network
    UdpAudioReceiver.kt    listener networking + jitter buffer + playback thread
    HostDiscovery.kt       find hosts / local IP addresses
    Platform.kt            expect/actual contract each platform implements
    *ViewModel.kt, App.kt, ui/   Compose UI, theme (light/dark)
  jvmMain/      desktop: WASAPI loopback (JNA), JavaSound fallback + speaker, window
  androidMain/  Android: playback capture, AudioTrack, hosting & listening foreground services
  jvmTest/      tests that run the real network pipeline over localhost
docs/
  how-streamaudio-works.html   the full guide
  tools/                       generator for the guide (see below)
```

## Requirements

- **JDK 17 or 21** to build (Gradle 8.13 can't run on JDK 25, which recent Android Studio bundles;
  point `JAVA_HOME` at a 17/21 JDK when using the command line).
- Android hosting needs **Android 10+**; listening works on Android 7+.
- Both devices on the **same Wi-Fi** network. Guest networks with "AP isolation" block
  device-to-device traffic.
- **Windows Firewall**: the first time you host, allow Java on *private* networks, or listeners
  can't connect.

## Build & run

Android (debug APK):
```shell
.\gradlew.bat :composeApp:assembleDebug        # Windows
./gradlew :composeApp:assembleDebug            # macOS/Linux
```

Desktop:
```shell
.\gradlew.bat :composeApp:run
```

Tests (real UDP pipeline over localhost, WASAPI smoke test on Windows):
```shell
.\gradlew.bat :composeApp:jvmTest
```

## Using it

1. On the device whose sound you want to share, tap **Host**. On Android, allow the audio and
   screen-capture prompts (only audio is captured).
2. On the other device, tap **Listen**. The host appears in the list; tap it, or enter the IP
   shown on the host screen.
3. If you hear stutter, switch the latency mode to **Balanced** or **Stable**.

**Tips**
- Some Android brands (Xiaomi, Samsung, Oppo…) restrict background apps aggressively. If audio stops
  with the screen off, set StreamAudio's battery usage to **Unrestricted**.
- Some Android apps opt out of audio capture; their sound can't be shared from a phone.

## Permissions (Android)

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Required by Android for capturing other apps' playback (the microphone isn't used) |
| Screen-capture consent | MediaProjection authorizes playback capture |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` / `_MEDIA_PLAYBACK` | Keep hosting / listening alive in the background |
| `POST_NOTIFICATIONS` | Show the Stop / Disconnect notifications (Android 13+) |
| `WAKE_LOCK`, Wi-Fi state | Keep CPU and Wi-Fi awake (no power-save lag) while streaming |
| `INTERNET`, network state | Sockets and discovery |

## Maintaining the docs and icon

- **Guide**: edit `docs/tools/guide.template.html`, then from the repo root run
  `perl docs/tools/build-guide.pl`. The script embeds the current source with real line numbers
  and fails if an explanation's anchor no longer exists in the code, so the guide can't silently
  drift out of date.
- **Icon**: the source is `composeApp/src/commonMain/composeResources/drawable/app_icon.xml`
  (desktop) and `androidMain/res/drawable/ic_launcher_*.xml` (adaptive icon). Regenerate the
  legacy PNGs with:
  ```shell
  set RENDER_ICONS_TO=%CD%\composeApp\src\androidMain\res
  .\gradlew.bat :composeApp:jvmTest --tests "*IconRender*"
  ```
