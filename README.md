# Zapette

A free, ad-free IPTV player for **Google TV / Android TV** and **Android phones and tablets**, built for the remote
control, touch screens and 4K.

Zapette does not ship any channels: it is a player. It connects to an **Xtream Codes** subscription
(server address + username + password) that you already have.

Available in English, French, Dutch and German.

## Features

- Xtream Codes sign-in with server address, username and password. Paste a full M3U link
  (`get.php?username=…&password=…`) into the server field and the username and password are filled in automatically
- **Live TV**, **Movies**, **Series**, **Search** and **Settings** tabs
- Categories open as you move over them, like on a TV box
- **Favorites**: hold OK (or long-press on a touch screen) on a channel, movie or series
- Phone layout in portrait (bottom tab bar, scrolling category strip) and the TV layout in landscape
- **Media3 / ExoPlayer** player:
  - hardware decoding (4K HEVC, VP9, AV1 and HDR, depending on what the device supports), with automatic fallback to another decoder
  - Live TV: ↑/↓ or CH+/CH- to zap (swipe up/down or the on-screen buttons on a phone), OK or a tap for the info banner with the TV guide (now / next) and technical details (resolution, codec, fps, HDR, audio)
  - Movies and series: ←/→ to seek (faster when held), automatic resume where you left off, episodes play back to back
  - audio track and subtitle selection from the player controls
  - automatic reconnection when the stream drops
- Live stream format of your choice: MPEG-TS (fast zapping) or HLS
- Custom User-Agent (for providers that block unknown apps)
- Nothing is sent anywhere but your IPTV server: credentials, favorites and resume positions stay on the device
- Language picker in Settings (also available as the per-app language in Android 13+ system settings)

## Building

Requirements: a recent Android Studio (AGP 9.4, Gradle 9.6, JDK 17+).

1. Open the `Zapette` folder in Android Studio.
2. Let the Gradle sync finish.
3. **Build › Build App Bundle(s) / APK(s) › Build APK(s)**, or from the command line:

   ```bash
   ./gradlew assembleRelease
   ```

   The APK ends up in `app/build/outputs/apk/release/`. Without a configured keystore it is signed with the debug key,
   which is enough to install it on your own devices.

To sign with your own key (for example in a GitHub Action), set the `ZAPETTE_STORE_FILE`, `ZAPETTE_STORE_PASSWORD`,
`ZAPETTE_KEY_ALIAS` and `ZAPETTE_KEY_PASSWORD` environment variables.

Unit tests: `./gradlew test`.

### Releases

The [Build APK](.github/workflows/build.yml) workflow publishes a pre-release APK for every push to `main`, named after
the upcoming version set in `app/build.gradle.kts` (for example `1.1.0-dev.12`).
To publish a stable release, run it from the **Actions** tab (**Run workflow**) and pick a `major`, `minor` or `patch` bump
from the latest release.

Signing uses the `ZAPETTE_KEYSTORE_BASE64` (keystore encoded in base64), `ZAPETTE_STORE_PASSWORD`, `ZAPETTE_KEY_ALIAS`
and `ZAPETTE_KEY_PASSWORD` repository secrets. Without them, each build gets a different debug key and cannot update a
previous install.

## Installing

Download `Zapette-x.y.z.apk` from the [latest release](https://github.com/Bysimeit/Zapette/releases/latest).
Zapette runs on Android 7.0 or later.

### On an Android phone or tablet

1. Open the release page on your phone and download the APK.
2. Open the downloaded file and allow your browser (or file manager) to install unknown apps when Android asks.
3. Zapette appears in your app drawer. Portrait and landscape are both supported.

### On Google TV

1. On the TV: **Settings › System › About**, click **Android TV OS build** 7 times to enable developer options.
2. **Settings › System › Developer options**: enable **USB debugging** and **Wireless debugging**.
3. From your computer (same network):

   ```bash
   adb pair TV_IP:PAIRING_PORT     # enter the code shown on the TV
   adb connect TV_IP:PORT
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

   Or simply press **Run ▶** in Android Studio once the TV is connected.

No cable or adb? Send the APK to the TV with an app such as *Send Files to TV* or *Downloader*.

## Project layout

```
app/src/main/java/dev/zapette/
├── data/      Xtream API client (player_api.php), models, local settings, HTTP client
├── player/    Full-screen player (Media3) and playback queue
└── ui/        Compose screens: sign-in, browsing, series page, search, settings
app/src/main/res/values*/strings.xml   Translations (en, fr, nl, de)
```

Adding a language: copy `app/src/main/res/values/strings.xml` to `values-xx/strings.xml`, translate it, and add `xx` to
`AppLanguage.tags` in `ui/AppLanguage.kt`.

## Disclaimer

Zapette is only a player. Use it exclusively with subscriptions and content you are legally entitled to access.

## License

Zapette is released under the [PolyForm Noncommercial License 1.0.0](LICENSE.md): you are free to use, study, modify and
share it for any noncommercial purpose. Selling it, or using it in a paid product or service, is not allowed.
