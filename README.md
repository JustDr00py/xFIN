# xFIN

A Jellyfin client for Android TV / Google TV, built with Kotlin, Jetpack Compose and androidx.tv Material.

## Build

```bash
# The system JDK on this machine is a runtime only; use the JDK 21 under ~/.jdks.
export JAVA_HOME=$HOME/.jdks/jdk-21.0.12.1+1
./gradlew :app:assembleRelease      # → app/build/outputs/apk/release/app-release.apk
```

Release builds are signed with the key named in `keystore.properties` (git-ignored). It has `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.

## Install on a device

**With xLauncher (recommended):** open xLauncher's settings → **About**, press OK on **xFIN** to check GitHub, then press OK on **Install xFIN \<version\>**. The same row offers updates later. See the [xLauncher README](https://github.com/JustDr00py/xLauncher#new-device-checklist) for setting up a new stick.

**With ADB:** download `xFIN-<version>.apk` from [Releases](https://github.com/JustDr00py/xFIN/releases), then:

```bash
adb connect 192.168.0.100:37155          # IP:port from Developer options → Wireless debugging
adb install -r xFIN-0.4.6.apk
```

## Publishing a release

```bash
scripts/release.sh --prerelease 0.4.7 "What changed"   # pre-release
scripts/release.sh 1.0.0 "What changed"                # full release
```

The script:
1. Checks that the version looks like `0.4.7`, `keystore.properties` exists, the git tree is clean and the tag is new.
2. Bumps `versionName` and `versionCode` in `app/build.gradle.kts`.
3. Builds the release APK and checks that it's signed with the release key. If it isn't, it stops and restores the gradle file.
4. Commits `Version <version> (<code>)`, tags `v<version>`, pushes, and creates the GitHub release with `xFIN-<version>.apk` attached.

xLauncher includes pre-releases when it looks for xFIN updates, so `--prerelease` builds still reach the sticks. They see a new release within a day, or right away when you press **xFIN** in xLauncher's settings.

Needs `gh` logged in (`gh auth status`). If it says *Commit or stash your changes first*, commit your work, or add files that should stay local to `.gitignore`.

**Back up `~/.android/xfin-release.jks` and `keystore.properties`.** Installed copies only accept updates signed with that key. If it's lost, every device has to uninstall xFIN (and lose its settings) before installing a build signed with a new key.
