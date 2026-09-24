# SearchVPN (Android)

An Android app with two features:

1. **Multi-site adult video search** — a WebView aggregator that searches 13+ adult
   video sites at once (Pornhub, XVideos, XHamster, XNXX, YouPorn, Redtube, and more;
   easily extensible in one file).
2. **WireGuard VPN client** — imports **your own** WireGuard (`.conf`) server configs
   from any provider you signed up with, and can auto-connect when the app opens.

Only visit / search sites that you are legally allowed to access in your jurisdiction,
and only use VPNs where doing so is legal. This project provides the software shell;
you provide the VPN config and the accounts.

## What is implemented

- `Search` tab: enter a query → every enabled site opens in its own tab (ViewPager2 of
  WebViews). Back button goes back within a site first, then to the previous site tab.
  "Open" button opens the current page in your external browser.
- `Sites` tab: enable/disable which sites are searched, Desktop-mode toggle (some sites
  behave better with a desktop user-agent), and an explainer for adding new sites.
- `VPN` tab: import a WireGuard config via the system file picker, connect/disconnect,
  auto-connect switch, and a summary of the active endpoint/address/DNS. Connection uses
  the official WireGuard userspace backend (`com.wireguard.android:tunnel`) through
  Android's `VpnService` (so Android shows the standard "VPN is on" prompt the first time).

> Note: this build supports **WireGuard** configs only. OpenVPN (`.ovpn`) is not included;
> it would require the separate ICS-OpenVPN library.

## Building

Requirements:

- [Android Studio](https://developer.android.com/studio) (bundles JDK 17)
- Android SDK Platform 34 (Android Studio will offer to install it on first sync)

Steps:

1. `File → Open` → select the `searchvpn/` folder.
2. Let Gradle sync (it will download AGP 8.5.2, Kotlin 2.0.20, and the WireGuard library
   from Google/Maven Central).
3. `Build → Build App Bundle(s) / APK(s) → Build APK(s)`.
4. The APK appears under `app/build/outputs/apk/debug/`.

If you ever need to build from a terminal and you already have Gradle installed:

```bash
gradle wrapper --gradle-version 8.9   # generates ./gradlew + wrapper jar once
./gradlew assembleDebug
```

## Building for free in the cloud (no PC needed)

The repo ships a ready GitHub Actions workflow (`.github/workflows/build-apk.yml`)
that compiles the APK on GitHub's servers. Do this once:

1. Push this folder to GitHub (from your Android phone's Termux):

   ```bash
   cd ~/searchvpn
   gh auth login          # choose GitHub.com → HTTPS → login via browser code
   git remote add origin git@github.com:YOUR_USERNAME/searchvpn.git
   git push -u origin main
   ```

   (or create the repo on github.com and push with your preferred method).

2. Open the repo on github.com → **Actions** tab → a "Build APK" run should
   already be queued from the push (or click **Run workflow**).

3. Wait for the green checkmark, then open the run → **Artifacts** →
   download `searchvpn-debug-apk`.

4. Unzip it (e.g. with ZArchiver) and tap `app-debug.apk` to install on your phone.

Notes: the workflow uses Gradle 8.9 + JDK 17 + Android SDK 34 and uploads the
debug APK every time you push to `main`. Each rebuild is free for public repos.

## Using the VPN

1. Sign up with a provider that shares **WireGuard configs** on a free tier, e.g.
   ProtonVPN or Windscribe, and download a `.conf` file for a server **near you**.
   (Some "free VPN" apps don't export configs; pick one that does.)
2. In-app: `VPN` tab → *Import WireGuard config* → pick the file.
3. Tap *Connect*. On the first connection Android asks you to allow VPN — grant it.
4. Toggle *Auto-connect when the app opens* if you want the tunnel up the moment you
   start searching.
5. For system-level always-on (reconnects even if the app is killed):
   `Settings → Network → VPN` and select "Always-on" for this app.

Troubleshooting:

- **No internet while connected** → your config's `DNS`/`AllowedIPs` may be restrictive;
  try a different server from your provider.
- **"VPN permission denied"** → grant it when Android asks (or revoke and re-grant via
  the SQLite settings of VPN → this app).
- Result of connecting is shown as *Connected / Not connected*; errors are shown as toasts.

## Adding / changing search sites

All sites live in one file: `app/src/main/java/io/searchvpn/app/data/Sites.kt`.

Each entry is:

```kotlin
SearchSite("key", "Display Name", "https://example.com/search?q={q}")
```

- `{q}` is replaced with the URL-encoded search query — every template must contain it.
- The `key` is stable; it is what Sites-tab checkboxes persist, so don't change existing
  keys or you'll reset user selections.

## Notes on the WireGuard integration

- The app uses `GoBackend` from the official `com.wireguard.android:tunnel` library
  (mirrors the real WireGuard app's userspace implementation). Only one tunnel is ever
  active, matching the library's design.
- VPN consent is requested via `VpnService.prepare()`, and the library's
  `GoBackend$VpnService` is merged in from the library manifest automatically.
- Config text is stored in plaintext in the app's private `SharedPreferences`, same as
  other WireGuard clients; treat your config like a credential.

## License

This project's code is provided under the Apache-2.0 license. The bundled
`com.wireguard.android:tunnel` library is Apache-2.0 (WireGuard project).