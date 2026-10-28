#!/usr/bin/env bash
# Run on your own computer. The generated private key never goes in the repo.
set +x
set -euo pipefail
export LC_ALL=C

if ! command -v openssl >/dev/null 2>&1; then
    echo 'OpenSSL was not found. Install OpenSSL for Windows, reopen Git Bash, and try again.' >&2
    exit 1
fi

signing_dir="$HOME/LSY-Free-Signing"
keystore="$signing_dir/lsy-free.p12"
encoded="$signing_dir/lsy-free-keystore.base64.txt"
if [ -e "$keystore" ] || [ -e "$encoded" ]; then
    echo "A key or encoded key already exists in $signing_dir."
    echo 'Refusing to overwrite it: future updates must use the original key.' >&2
    exit 1
fi
umask 077
mkdir -p "$signing_dir"
if git -C "$signing_dir" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "For safety, $signing_dir must not be inside a Git repository." >&2
    echo 'Ask for help choosing a safe folder outside any repository.' >&2
    exit 1
fi

printf 'Choose a signing password (12-128 printable ASCII characters, no spaces).\n'
read -r -s -p 'Password: ' LSY_FREE_SIGNING_PASSWORD
printf '\n'
read -r -s -p 'Repeat password: ' confirmation
printf '\n'
if [ "$LSY_FREE_SIGNING_PASSWORD" != "$confirmation" ]; then
    echo 'Passwords did not match; no key was created.' >&2
    exit 1
fi
if [[ ! "$LSY_FREE_SIGNING_PASSWORD" =~ ^[!-~]{12,128}$ ]]; then
    echo 'Use 12-128 printable ASCII characters with no spaces; no key was created.' >&2
    exit 1
fi
unset confirmation
export LSY_FREE_SIGNING_PASSWORD
tmpdir=$(mktemp -d "${TMPDIR:-/tmp}/lsy-free-signing.XXXXXXXX")
trap 'rm -rf "$tmpdir"; unset LSY_FREE_SIGNING_PASSWORD' EXIT

# Relative filenames also work with MSYS_NO_PATHCONV in Windows Git Bash.
(
    cd "$tmpdir"
    if ! MSYS_NO_PATHCONV=1 openssl req -x509 -newkey rsa:3072 -sha256 -days 10000 \
        -subj '/CN=LSY Free' -keyout private.pem -out certificate.pem \
        -passout env:LSY_FREE_SIGNING_PASSWORD 2>openssl.log; then
        echo 'OpenSSL could not create the signing key:' >&2
        cat openssl.log >&2
        exit 1
    fi
    if ! openssl pkcs12 -export -name lsy-free -inkey private.pem -in certificate.pem \
        -out lsy-free.p12 -passin env:LSY_FREE_SIGNING_PASSWORD \
        -passout env:LSY_FREE_SIGNING_PASSWORD -iter 100000 2>openssl.log; then
        echo 'OpenSSL could not create the keystore:' >&2
        cat openssl.log >&2
        exit 1
    fi
    base64 < lsy-free.p12 | tr -d '\r\n' > lsy-free-keystore.base64.txt
    openssl x509 -in certificate.pem -noout -sha256 -fingerprint > signer-fingerprint.txt
)

mv "$tmpdir/lsy-free.p12" "$keystore"
mv "$tmpdir/lsy-free-keystore.base64.txt" "$encoded"
mv "$tmpdir/signer-fingerprint.txt" "$signing_dir/signer-fingerprint.txt"

printf '\nKey created in: %s\n' "$signing_dir"
printf 'Private keystore: %s\n' "$keystore"
printf 'GitHub secret text: %s\n' "$encoded"
printf 'Public certificate fingerprint: '
cat "$signing_dir/signer-fingerprint.txt"
printf '\nKeep the keystore and password safe. Never commit or share the keystore, encoded key, or password.\n'
