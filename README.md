# Card Vault (native)

Personal card vault for Android, rebuilt as a native app in Kotlin + Jetpack Compose (Material 3).

- Cards are encrypted with AES-256-GCM; the key comes from your master password (PBKDF2-SHA256, 310,000 rounds).
- Same encryption and backup format as the original Card Vault: export a backup in the old app and choose
  *Restore from a backup file* (first launch) or *Settings → Import backup* here.
- Fingerprint unlock keeps the vault key wrapped by a hardware key in the Android Keystore.
- Screenshots and the recent-apps preview are blocked. The app has no internet permission.

Every push to `main` builds a signed APK with GitHub Actions and publishes it under **Releases**
(needs the `KEYSTORE_BASE64` secret).
