# Pangolin per-app (fork)

Fork of [fosrl/android](https://github.com/fosrl/android) with a per-app VPN mode ("all apps except…" / "only these apps") and a keep-alive for MIUI. Branch: `per-app-vpn`. Package `net.pangolin.Pangolin.perapp`, installs next to the Play Store app.

## Releases and automatic updates

Every push to `per-app-vpn` runs `.github/workflows/perapp-release.yml`: unit tests, a signed arm64 APK, and a GitHub Release tagged `perapp-<build>`. The `versionCode` is upstream's `versionCode × 1000 + build`, so every release is an update.

### Phone setup (once)

1. Uninstall any older "Pangolin per-app" built by hand (debug key): Android refuses to update an app signed with a different key.
2. Install [Obtainium](https://github.com/ImranR98/Obtainium) (from its GitHub releases or F-Droid).
3. Obtainium → **Add app** → URL `https://github.com/Azokul/pangolin-android`.
   - *Filter release titles by regular expression*: `Pangolin per-app`
   - *Filter APKs by regular expression*: `arm64`
   - Add, then **Install**.
4. Obtainium settings: background update checks on (e.g. every 6 h) and **Enable background updates**. On Android 12+ it then installs updates without asking for apps it installed; on MIUI/HyperOS a confirmation can still appear unless Obtainium uses **Shizuku**.

### Signing key

- Master copy at home, root-only: `/root/android-release/perapp-release.jks` + `perapp-release.pass` on the r330 host (and `/root/release/` in CT 199 `apk-build`). Keep a copy in Vaultwarden: **losing it means every phone must uninstall and reinstall**.
- Alias `perapp`, RSA 4096, valid until 2126. SHA-256 fingerprint
  `90:E6:4F:24:4C:AC:19:0F:E0:78:2F:E0:2B:21:F9:C9:BC:DD:5E:2A:1F:86:69:09:BC:DD:2A:65:0E:DD:56:02`
- GitHub secrets `PERAPP_KEYSTORE_B64` (base64 of the .jks) and `PERAPP_KEYSTORE_PASSWORD`.

### Build locally

```bash
PERAPP_KEYSTORE=/root/release/perapp-release.jks PERAPP_KEYSTORE_PASSWORD="$(cat /root/release/perapp-release.pass)" PERAPP_BUILD=1 \
  ./gradlew -Pandroid.injected.build.abi=arm64-v8a -Pandroid.injected.testOnly=false :app:assemblePerapp
```

Without the variables the APK is unsigned and keeps upstream's versionCode.

## Syncing with upstream

```bash
git fetch upstream && git merge upstream/main   # on per-app-vpn
```

The versionCode override lives at the end of `app/build.gradle.kts` (not in `defaultConfig`), so upstream version bumps merge cleanly.
