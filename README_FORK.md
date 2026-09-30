# Flym Fork changes

This fork keeps the original Flym source packages but uses a separate Android application ID so it can be installed alongside the original Flym.

## Application identity

- Application ID: `net.frju.flym.fork`
- App label: `Flym Fork`
- FileProvider authority: `${applicationId}.fileprovider`

The fork intentionally does not try to upgrade an existing signed Flym installation because the original signing key is not available.

## GitHub Actions signing

The workflow in `.github/workflows/android.yml` is manually triggered with `workflow_dispatch`. It builds one signed Release APK. Flym currently has no native `.so` libraries of its own, so ABI splits would produce byte-for-byte identical APKs and are intentionally disabled.

Create a new release keystore for this fork and keep it private. Add these GitHub Actions repository secrets:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded `.keystore`/`.jks` file
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The keystore must remain the same for all future releases; otherwise users will not be able to update an installed fork APK.

The repository does not contain a keystore or signing password.

## Creating the signing key

Create a new private release key once, on a trusted machine, for example:

```text
keytool -genkeypair -v -keystore flym-release.jks -alias flym -keyalg RSA -keysize 2048 -validity 10000
```

Then base64-encode that file and store the result in the `ANDROID_KEYSTORE_BASE64` repository secret. On Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("flym-release.jks"))
```

The other three repository secrets are the keystore password, alias, and key password. Do not commit the keystore file to Git; the repository ignores `*.jks` and `*.keystore`.
