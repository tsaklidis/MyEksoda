#!/usr/bin/env bash
# Builds a new release APK for GitHub (`make new_release [BUMP=patch|minor|major]`):
#
# 1. `make check` must pass (nothing is changed before that).
# 2. version.properties: versionCode + 1, and versionName bumped (patch by default).
# 3. The release APK is built, signed with the release key (`make release_key`).
# 4. The signature is checked against that key, and the APK is copied to the project root as
#    eksoda-<version>.apk (ignored by git: it goes to a GitHub release, not the repository).
#
# Any failure after the bump puts version.properties back. Nothing is committed, tagged or
# uploaded: the next steps are printed at the end.
set -euo pipefail
cd "$(dirname "$0")/.."

BUMP="${1:-patch}"
VERSION_FILE="version.properties"
KEY_PROPS="${EKSODA_KEYSTORE:-$HOME/.eksoda/keystore.properties}"
KEYTOOL="${JAVA_HOME:?JAVA_HOME is not set (run through make)}/bin/keytool"
BUILT_APK="app/build/outputs/apk/release/app-release.apk"

fail() {
    echo "new_release: $*" >&2
    exit 1
}

property() { sed -n "s/^$1=//p" "$2" | tail -n 1; }

# --- Preconditions -----------------------------------------------------------------------------

[[ -f "$KEY_PROPS" ]] || fail "no release key at $KEY_PROPS. Run 'make release_key' once."
case "$BUMP" in patch | minor | major) ;; *) fail "BUMP must be patch, minor or major" ;; esac

SDK_DIR="${ANDROID_HOME:-$(property sdk.dir local.properties 2>/dev/null || true)}"
SDK_DIR="${SDK_DIR:-$HOME/Library/Android/sdk}"
APKSIGNER="$(find "$SDK_DIR/build-tools" -maxdepth 2 -name apksigner 2>/dev/null | sort -V | tail -n 1)"
[[ -n "$APKSIGNER" ]] || fail "apksigner not found under $SDK_DIR/build-tools"

if [[ -n "$(git status --porcelain)" ]]; then
    echo "new_release: warning: the working tree has uncommitted changes; they go into the APK."
fi

OLD_CODE="$(property versionCode "$VERSION_FILE")"
OLD_NAME="$(property versionName "$VERSION_FILE")"
[[ "$OLD_CODE" =~ ^[0-9]+$ ]] || fail "versionCode '$OLD_CODE' is not a number"
[[ "$OLD_NAME" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || fail "versionName '$OLD_NAME' is not X.Y.Z"
MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[3]}"
case "$BUMP" in
    major) NEW_NAME="$((MAJOR + 1)).0.0" ;;
    minor) NEW_NAME="$MAJOR.$((MINOR + 1)).0" ;;
    patch) NEW_NAME="$MAJOR.$MINOR.$((PATCH + 1))" ;;
esac
NEW_CODE="$((OLD_CODE + 1))"
OUTPUT="eksoda-$NEW_NAME.apk"
[[ ! -e "$OUTPUT" ]] || fail "$OUTPUT already exists"

echo "new_release: $OLD_NAME ($OLD_CODE) -> $NEW_NAME ($NEW_CODE)"

# --- 1. Checks ---------------------------------------------------------------------------------

make check

# --- 2. Version bump (undone on any failure from here on) --------------------------------------

BACKUP="$(mktemp)"
DONE=0
cp "$VERSION_FILE" "$BACKUP"
# On any exit before the end (a failed command or `fail`), the old version comes back.
finish() {
    if [[ "$DONE" -eq 0 ]]; then
        cp "$BACKUP" "$VERSION_FILE"
        echo "new_release: failed; $VERSION_FILE is back to $OLD_NAME ($OLD_CODE)." >&2
    fi
    rm -f "$BACKUP"
}
trap finish EXIT

sed -e "s/^versionCode=.*/versionCode=$NEW_CODE/" \
    -e "s/^versionName=.*/versionName=$NEW_NAME/" "$BACKUP" >"$VERSION_FILE"

# --- 3. Build ----------------------------------------------------------------------------------

./gradlew :app:assembleRelease

# --- 4. Signature check and copy ---------------------------------------------------------------

EKSODA_KEY_PASSWORD="$(property storePassword "$KEY_PROPS")"
export EKSODA_KEY_PASSWORD
KEY_SHA256="$("$KEYTOOL" -list -v -keystore "$(property storeFile "$KEY_PROPS")" \
    -alias "$(property keyAlias "$KEY_PROPS")" -storepass:env EKSODA_KEY_PASSWORD |
    sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]')"
APK_SHA256="$("$APKSIGNER" verify --print-certs "$BUILT_APK" |
    sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
[[ -n "$KEY_SHA256" && "$APK_SHA256" == "$KEY_SHA256" ]] ||
    fail "the APK is not signed with the release key (APK: ${APK_SHA256:-none})"

cp "$BUILT_APK" "$OUTPUT"
DONE=1

echo
echo "Release $NEW_NAME ($NEW_CODE): $OUTPUT"
echo "  SHA-256 of the file:  $(shasum -a 256 "$OUTPUT" | cut -d ' ' -f 1)"
echo "  Signing certificate:  $APK_SHA256"
echo
echo "Next steps (not done for you):"
echo "  git commit -m \"Release $NEW_NAME\" $VERSION_FILE && git tag v$NEW_NAME && git push --follow-tags"
echo "  Create a GitHub release v$NEW_NAME and attach $OUTPUT"
echo "  (with the gh CLI: gh release create v$NEW_NAME $OUTPUT --prerelease --generate-notes)"
