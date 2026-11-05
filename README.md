# Ariana

Android app by **Fati**. Runs the bundled official engine (Aether Android arm64 build, checksum
verified) and routes traffic through it. Works only with an Irancell SIM (MCC-MNC 43235).

## Build

Open this folder in Android Studio, let Gradle sync (it creates the wrapper), then
Build > Build APK. The engine (`libaether.so`) and Psiphon (`libpsiphon.so`) are already in
`app/src/main/jniLibs/arm64-v8a/`. arm64 phones only.

## Modes

Auto, MASQUE (HTTP/3 or HTTP/2), WireGuard, WireGuard over MASQUE, Chained+Psiphon, Chained+Tor.
Pick whichever is fastest on your own Irancell connection.

## Notes

- The VPN service sets a system HTTP proxy (127.0.0.1:1830) to the engine. Apps that ignore the
  system proxy are not tunnelled; for a full tunnel add a tun2socks library and bridge the TUN
  interface to SOCKS5 127.0.0.1:1819.
- The Irancell check is client-side and can be bypassed by editing the app.
- See NOTICE.md for licenses and trademark.
