#!/usr/bin/env bash
# Publishes a new xFIN version to GitHub Releases, where xLauncher's updater finds it.
# Usage: scripts/release.sh [--prerelease] 0.4.5 "What changed"
#
# It bumps versionName and versionCode in app/build.gradle.kts, builds a release APK signed with
# the key in keystore.properties, commits, tags v<version>, pushes, and creates the release with
# xFIN-<version>.apk attached. Needs: gh (logged in), a clean git tree, keystore.properties.
set -euo pipefail
cd "$(dirname "$0")/.."

PRERELEASE=()
if [[ "${1:-}" == "--prerelease" ]]; then PRERELEASE=(--prerelease); shift; fi
VERSION="${1:?usage: $0 [--prerelease] <version> [notes]}"
NOTES="${2:-xFIN $VERSION}"
GRADLE_FILE=app/build.gradle.kts
export JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jdk-21.0.12.1+1}"
export PATH="$JAVA_HOME/bin:$PATH"

[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Version must look like 0.4.5"; exit 1; }
[[ -f keystore.properties ]] || { echo "keystore.properties missing: releases must use the release key"; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo "Commit or stash your changes first"; exit 1; }
git rev-parse "v$VERSION" >/dev/null 2>&1 && { echo "Tag v$VERSION already exists"; exit 1; }

CODE=$(grep -oP 'versionCode = \K[0-9]+' "$GRADLE_FILE")
NEXT=$((CODE + 1))
sed -i -E "s/versionCode = [0-9]+/versionCode = $NEXT/; s/versionName = \"[^\"]+\"/versionName = \"$VERSION\"/" "$GRADLE_FILE"
echo "› Version $VERSION (code $NEXT)"

if ! ./gradlew -q :app:assembleRelease; then
  git checkout -- "$GRADLE_FILE"
  exit 1
fi
mkdir -p build
APK="build/xFIN-$VERSION.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"

# Refuse to publish anything not signed with the release key: devices would reject the update.
EXPECTED=$(keytool -list -keystore "$(grep -oP 'storeFile=\K.*' keystore.properties)" \
  -storepass "$(grep -oP 'storePassword=\K.*' keystore.properties)" -rfc 2>/dev/null \
  | openssl x509 -noout -fingerprint -sha256 | cut -d= -f2 | tr -d ':' | tr 'A-F' 'a-f')
ACTUAL=$("$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/* | sort -V | tail -1)/apksigner" \
  verify --print-certs "$APK" 2>/dev/null | grep -oP 'SHA-256 digest: \K[0-9a-f]+' | head -1)
if [[ -z "$EXPECTED" || "$EXPECTED" != "$ACTUAL" ]]; then
  echo "APK isn't signed with the release key"
  git checkout -- "$GRADLE_FILE"
  exit 1
fi

git commit -qam "Version $VERSION ($NEXT)"
git tag "v$VERSION"
git push -q origin HEAD "v$VERSION"
gh release create "v$VERSION" "$APK" --title "xFIN $VERSION" --notes "$NOTES" "${PRERELEASE[@]}"
echo "✓ Released xFIN $VERSION. Sticks see it in Settings › About within a day, or right away on a manual check."
