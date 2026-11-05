# ZAHRANET-ANDROID (Ariana VPN)
Creator: فاطمه زهرا ساعي · Core: Aether (CluvexStudio, AGPL-3.0) — credit is shown in the app.
Status: written but NOT compiled or run by its author (no Gradle/SDK/network in the authoring environment).

## How it works
`AetherVpnService` runs `libaether.so <--masque|--gool|--psiphon|--tor> --stats`, waits for its SOCKS5 on
127.0.0.1:1819 (opened only after the core verified its tunnel), then builds a TUN with `VpnService.Builder`
and pipes it into that SOCKS5 with hev-socks5-tunnel (DNS via hev mapdns, resolved through SOCKS5).
If a native file is missing, the Gradle build fails on purpose; there is no demo or fake connection.
Irancell lock: SIM MCC/MNC = 432/35 (client-side check, bypassable by modifying the APK).

## Local build (Android Studio, JDK 17)
1. Run `scripts/fetch_native.sh` (Linux/macOS/WSL; needs gh, NDK) — or copy the two real .so files into
   `app/src/main/jniLibs/arm64-v8a/` yourself: `libaether.so`, `libhev-socks5-tunnel.so`.
2. Open the folder in Android Studio, Sync, Build > Build APK(s).
   CLI: `./gradlew assembleRelease` -> `app/build/outputs/apk/release/app-release.apk`.
`gradlew` fetches the missing `gradle-wrapper.jar` on first run; `gradle wrapper --gradle-version 8.9` regenerates the official one.

## GitHub Actions
Upload the project to a repo, then Actions > Build APK > Run workflow. Download the `ZAHRANET-apk` artifact.
