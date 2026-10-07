# Zapette

A free, ad-free IPTV player for **Google TV / Android TV**, built for the remote control and for 4K.

Zapette does not ship any channels: it is a player. It connects to an **Xtream Codes** subscription
(server address + username + password) that you already have.

Available in English, French, Dutch and German.

## Features

- Xtream Codes sign-in (you can also paste a full M3U link `get.php?username=…&password=…`)
- **Live TV**, **Movies**, **Series**, **Search** and **Account** tabs
- Categories open as you move over them, like on a TV box
- **Favorites**: hold OK on a channel, movie or series
- **Media3 / ExoPlayer** player:
  - hardware decoding (4K HEVC, VP9, AV1 and HDR, depending on what the TV supports), with automatic fallback to another decoder
  - Live TV: ↑/↓ or CH+/CH- to zap, OK for the info banner with the TV guide (now / next) and technical details (resolution, codec, fps, HDR, audio)
  - Movies and series: ←/→ to seek (faster when held), automatic resume where you left off, episodes play back to back
  - audio track and subtitle selection from the player controls
  - automatic reconnection when the stream drops
- Live stream format of your choice: MPEG-TS (fast zapping) or HLS
- Custom User-Agent (for providers that block unknown apps)
- Nothing is sent anywhere but your IPTV server: credentials, favorites and resume positions stay on the TV
- Per-app language selection on Android 13+

## Building

Requirements: a recent Android Studio (AGP 9.3, Gradle 9.6, JDK 17+).

1. Open the `Zapette` folder in Android Studio.
2. Let the Gradle sync finish.
3. **Build › Build App Bundle(s) / APK(s) › Build APK(s)**, or from the command line:

   ```bash
   ./gradlew assembleRelease
   ```

   The APK ends up in `app/build/outputs/apk/release/`. Without a configured keystore it is signed with the debug key,
   which is enough to install it on your own TV.

To sign with your own key (for example in a GitHub Action), set the `ZAPETTE_STORE_FILE`, `ZAPETTE_STORE_PASSWORD`,
`ZAPETTE_KEY_ALIAS` and `ZAPETTE_KEY_PASSWORD` environment variables.

Unit tests: `./gradlew test`.

### Releases

The [Build APK](.github/workflows/build.yml) workflow publishes a pre-release APK for every push to `main`.
To publish a stable release, run it from the **Actions** tab (**Run workflow**) and pick a `major`, `minor` or `patch` bump.

Signing uses the `ZAPETTE_KEYSTORE_BASE64` (keystore encoded in base64), `ZAPETTE_STORE_PASSWORD`, `ZAPETTE_KEY_ALIAS`
and `ZAPETTE_KEY_PASSWORD` repository secrets. Without them, each build gets a different debug key and cannot update a
previous install.

## Installing on Google TV

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
└── ui/        Compose screens: sign-in, browsing, series page, search, account
app/src/main/res/values*/strings.xml   Translations (en, fr, nl, de)
```

Adding a language: copy `app/src/main/res/values/strings.xml` to `values-xx/strings.xml` and translate it.

## Disclaimer

Zapette is only a player. Use it exclusively with subscriptions and content you are legally entitled to access.

## License

Zapette is released under the [PolyForm Noncommercial License 1.0.0](LICENSE.md): you are free to use, study, modify and
share it for any noncommercial purpose. Selling it, or using it in a paid product or service, is not allowed.
