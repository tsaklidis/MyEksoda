#!/usr/bin/env bash
# Creates the release signing key, once (`make release_key`).
#
# Every APK published on GitHub must be signed with this key: Android installs an update only
# over an app signed with the same key, and Eksoda keeps all data on the phone, so a lost key
# means testers must uninstall (losing their data unless they made a backup) to update.
#
# The key lives outside the repository, in ~/.eksoda (or $EKSODA_KEY_DIR), so `git clean`
# cannot delete it. Back up the whole directory somewhere safe, e.g. a password manager.
set -euo pipefail

DIR="${EKSODA_KEY_DIR:-$HOME/.eksoda}"
STORE="$DIR/release.jks"
PROPS="$DIR/keystore.properties"
ALIAS="eksoda"
KEYTOOL="${JAVA_HOME:?JAVA_HOME is not set (run through make)}/bin/keytool"

if [[ -e "$STORE" || -e "$PROPS" ]]; then
    echo "A release key already exists in $DIR; not overwriting it." >&2
    exit 1
fi

umask 077
mkdir -p "$DIR"
chmod 700 "$DIR"

# PKCS12 uses one password for the store and the key. Passed through the environment, so it
# never shows in the process list.
EKSODA_KEY_PASSWORD="$(openssl rand -base64 48 | tr -d '/+=\n' | cut -c1-40)"
export EKSODA_KEY_PASSWORD

"$KEYTOOL" -genkeypair -noprompt \
    -keystore "$STORE" -storetype PKCS12 \
    -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "CN=Eksoda" \
    -storepass:env EKSODA_KEY_PASSWORD -keypass:env EKSODA_KEY_PASSWORD

cat >"$PROPS" <<EOF
# Eksoda release signing key. Keep this file and release.jks together, backed up and private.
storeFile=$STORE
storePassword=$EKSODA_KEY_PASSWORD
keyAlias=$ALIAS
keyPassword=$EKSODA_KEY_PASSWORD
EOF

FINGERPRINT="$("$KEYTOOL" -list -v -keystore "$STORE" -alias "$ALIAS" \
    -storepass:env EKSODA_KEY_PASSWORD | sed -n 's/^[[:space:]]*SHA256: //p')"

echo
echo "Release key created in $DIR (release.jks and keystore.properties)."
echo "Certificate SHA-256: $FINGERPRINT"
echo
echo "Back up $DIR now. Without it, no update can ever be installed over a published APK."
