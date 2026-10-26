# Sonometer

[Français](README.fr.md)

Android sound level meter built with Jetpack Compose. The measurement engine follows IEC 61672-1, the standard used by physical sound level meters.

## Features

- **Frequency weightings A, C and Z**, Class 1 tolerances at 44.1 and 48 kHz
- **Time weightings Fast, Slow and Impulse**, applied on every sample
- **LEQ, MIN, MAX and PEAK**: energy-equivalent level, time-weighted extremes and highest instantaneous level
- All weightings are measured at the same time: switching keeps the statistics
- **Overload indicator**, lit while the input clips and dimmed once it has clipped
- **Raw microphone capture**: `UNPROCESSED` source when available, otherwise `VOICE_RECOGNITION`, with automatic gain control and noise suppression disabled
- **Calibration** against a reference sound level meter, stored per microphone source
- Start-up transient and microphone taken by another app are excluded from the statistics
- Live chart and PDF report
- English and French

## Measurement accuracy

The conversion to dB SPL uses the microphone sensitivity required by the Android Compatibility Definition Document: 94 dB SPL gives -36 dBFS on `UNPROCESSED`, 90 dB SPL gives -22.35 dBFS on `VOICE_RECOGNITION`. Real phones vary by a few dB and lose sensitivity below about 30 dB and in low frequencies, so calibrating against a reference meter is recommended.

## Build

Requirements: Android Studio with JDK 17 or newer, Android SDK 37.

| Component | Version |
|---|---|
| Android Gradle Plugin | 9.4.1 |
| Gradle | 9.8.2 |
| Kotlin | 2.4.21 |
| minSdk / targetSdk | 24 / 37 |

All dependency versions live in `gradle/libs.versions.toml`.

### Release signing

Create `keystore.properties` at the project root (ignored by Git):

```properties
storeFile=path/to/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without this file the release build is produced unsigned.

## Tests

```
./gradlew :app:testDebugUnitTest
```

Unit tests check the weighting filters against the IEC 61672-1 tables and the meter against reference responses (tone bursts, decay rates, Leq, overload, pause and resume).
