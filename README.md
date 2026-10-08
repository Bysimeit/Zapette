# Zapette

A free, ad-free IPTV player for **Google TV / Android TV** and **Android phones and tablets**, designed for the remote
control, touch screens and 4K.

Zapette does not provide any channels: it is a player for the **Xtream Codes** subscription you already have
(server address, username and password).

Available in English, French, Dutch and German.

## Features

- **Live TV, Movies and Series** from your Xtream Codes subscription, browsed by category
- **Search** across channels, movies and series
- **Favorites**: hold OK (or long-press on a touch screen) on any channel, movie or series
- **TV guide** in the info banner (what's on now and next)
- **Resume playback** where you left off, and episodes that play back to back
- **4K and HDR** through the device's hardware decoders
- **Surround audio**: AC-3, E-AC-3, DTS and TrueHD are sent to your soundbar or AV receiver when possible,
  and decoded by the app otherwise
- **Audio tracks and subtitles** selectable while watching
- **Automatic reconnection** when a stream drops
- **Phone and tablet layout**: tabs at the bottom in portrait, full-screen browsing in landscape, and swipe up or down
  to change channel while watching live TV
- **Fast start**: channel and movie lists are kept for 24 hours, so they open instantly
- **Language** picker in Settings
- **No ads, no tracking, no account**: your credentials, favorites and progress stay on your device

## Installation

Zapette runs on Android 7.0 or later. Download the latest `Zapette-x.y.z.apk` from the [**Releases**](../../releases/latest)
page.

### On Google TV / Android TV

1. Get the APK onto your TV, using whichever method suits you:
   - **Downloader** (from the Play Store): enter the APK link and install it directly on the TV.
   - **Send Files to TV** (on your phone and your TV): send the APK from your phone, then open it on the TV.
   - **USB stick**: copy the APK to it, plug it into the TV and open it with a file manager.
2. The first time, Android asks you to allow installing apps from that source: accept, then install.

### On an Android phone or tablet

1. Open the [**Releases**](../../releases/latest) page on your phone and download the APK.
2. Open the downloaded file. The first time, Android asks you to allow your browser (or file manager) to install apps:
   accept, then install.

### Updating

Download the new APK from [Releases](../../releases/latest) and install it over the current version.
Your account, favorites and resume positions are kept.

## Getting started

Open Zapette and enter your **server address**, **username** and **password**. You can also paste a full M3U link
(`…/get.php?username=…&password=…`) into the server address field: the three fields are filled in for you.

> On a TV, typing with a remote is slow: the **Google TV app on your phone** lets you type (or paste) with your phone's
> keyboard.

## Troubleshooting

**A channel won't start or keeps stuttering**
Go to **Settings › Live channel playback** and switch to **HLS**.

**The app says my credentials are wrong, or lists stay empty**
Check the server address, including the port (often `:8080`). Some providers block unknown apps: in that case,
set the User-Agent they recommend in **Settings › User-Agent**.

**"The server is receiving too many requests (HTTP 429)"**
Your provider limits how often the app can ask for lists. Wait a few seconds and try again.

**A movie plays without sound**
Open the player controls › ⚙ › **Audio** and pick another track.

**4K is choppy**
4K depends on what the device's hardware can decode. The info banner (OK or a tap during live TV) shows the resolution
and codec of the current stream, which helps you see what is being played.

**Lists look outdated**
Lists refresh on their own every 24 hours. To refresh them right away, use **Settings › Reload lists**.

## Privacy

Zapette only talks to the IPTV server you sign in to. It has no analytics, no ads and no account of its own.

## Disclaimer

Zapette is only a player and does not provide any content. Use it exclusively with subscriptions and content you are
legally entitled to access.

## License

Zapette is released under the [PolyForm Noncommercial License 1.0.0](LICENSE.md): you are free to use, study, modify
and share it for any noncommercial purpose. Selling it, or using it in a paid product or service, is not allowed.

Zapette uses [AndroidX Media3](https://github.com/androidx/media), [OkHttp](https://github.com/lysine-dev/okhttp),
[Coil](https://coil-kt.github.io/coil/) and [Jellyfin's Media3 FFmpeg decoder](https://github.com/jellyfin/jellyfin-androidx-media),
each under its own license.
