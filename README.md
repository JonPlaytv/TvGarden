# TvGarden

A clean Android TV application built with Jetpack Compose for TV (Material 3) and Media3 (ExoPlayer).

## Features
- Fetches live IPTV channels from [iptv-org](https://github.com/iptv-org/iptv) (News, Movies, Music categories).
- Clean, modern TV interface with D-pad navigation support.
- Live streaming playback using Media3 ExoPlayer.

## Setup
1. Open this project in Android Studio.
2. Sync Gradle with project files.
3. Run on an Android TV Emulator or Device (API 31+ recommended).

## Architecture
- **Data**: `IptvRepository` fetches M3U playlists and parses them into `Channel` objects.
- **UI**: Pure Compose for TV. `HomeScreen` displays channels, `PlayerScreen` handles playback.
- **Navigation**: Using `Navigation Compose`.
