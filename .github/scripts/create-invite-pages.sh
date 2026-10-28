#!/usr/bin/env bash
# Run from the LSY-Free repository after creating the permanent signing key.
# Only the PUBLIC fingerprint is copied; no private key or password is read.
set -euo pipefail

repo=$(git rev-parse --show-toplevel 2>/dev/null) || {
    echo 'Run this from inside your LSY-Free Git repository.' >&2
    exit 1
}
templates="$repo/docs/lsy-invite-site"
fingerprint_file="$HOME/LSY-Free-Signing/signer-fingerprint.txt"
site="$HOME/daddyyour0786-dev.github.io"

if [ ! -f "$templates/invite.html" ] || [ ! -f "$templates/index.html" ]; then
    echo 'Invite page templates are missing. Apply the invite patch first.' >&2
    exit 1
fi
if [ ! -f "$fingerprint_file" ]; then
    echo "The public fingerprint file was not found: $fingerprint_file" >&2
    echo 'Complete the permanent signing-key setup first. Do not generate a second key if you already made one.' >&2
    exit 1
fi
fingerprint=$(tr -d '\r\n' < "$fingerprint_file" | sed -n 's/^[^=]*=//p' | tr '[:lower:]' '[:upper:]')
if [[ ! "$fingerprint" =~ ^([0-9A-F]{2}:){31}[0-9A-F]{2}$ ]]; then
    echo 'The public fingerprint file does not contain a valid SHA-256 certificate fingerprint.' >&2
    echo 'Do not send anyone the keystore, its base64 text, or its password.' >&2
    exit 1
fi
if [ -e "$site" ] && { [ ! -d "$site" ] || [ -n "$(ls -A "$site")" ]; }; then
    echo "Refusing to overwrite the existing folder: $site" >&2
    echo 'Move or rename that folder before running this script again.' >&2
    exit 1
fi

mkdir -p "$site/.well-known" "$site/invite"
cp "$templates/index.html" "$site/index.html"
cp "$templates/invite.html" "$site/invite/index.html"
: > "$site/.nojekyll"
cat > "$site/.well-known/assetlinks.json" <<JSON
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.lsyfree.music",
      "sha256_cert_fingerprints": ["$fingerprint"]
    }
  }
]
JSON

printf '\nPublic invite site created at: %s\n' "$site"
printf 'It contains only website files and the PUBLIC signing fingerprint.\n'
printf 'Next, create the public GitHub repository daddyyour0786-dev.github.io and push this folder.\n'
