#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  printf 'Usage: %s installed-or-previous.apk candidate-update.apk\n' "$0" >&2
  exit 2
fi
previous=$1
candidate=$2
for apk in "$previous" "$candidate"; do
  if [ ! -f "$apk" ]; then
    printf 'APK not found: %s\n' "$apk" >&2
    exit 2
  fi
done

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$sdk" ] && [ -f local.properties ]; then
  sdk=$(sed -n 's/^sdk\.dir=//p' local.properties | head -n 1)
fi
if [ -z "$sdk" ]; then
  printf 'Set ANDROID_HOME or run from the project with local.properties.\n' >&2
  exit 2
fi
tools_dir=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)
aapt="$tools_dir/aapt"
apksigner="$tools_dir/apksigner"
if [ ! -x "$aapt" ] || [ ! -x "$apksigner" ]; then
  printf 'Android build tools were not found under %s.\n' "$sdk" >&2
  exit 2
fi

read_field() {
  "$aapt" dump badging "$1" | sed -n "s/^package: name='\([^']*\)' versionCode='\([0-9]*\)'.*/\1 \2/p" | head -n 1
}
read_cert() {
  "$apksigner" verify --print-certs "$1" | sed -n 's/^.*certificate SHA-256 digest: //p' | head -n 1
}
read -r old_package old_code <<< "$(read_field "$previous")"
read -r new_package new_code <<< "$(read_field "$candidate")"
old_cert=$(read_cert "$previous")
new_cert=$(read_cert "$candidate")
if [ -z "$old_package" ] || [ -z "$new_package" ] || [ -z "$old_cert" ] || [ -z "$new_cert" ]; then
  printf 'Could not read package, version code, or signing certificate.\n' >&2
  exit 1
fi
printf 'Previous: %s versionCode %s, signer %s\n' "$old_package" "$old_code" "$old_cert"
printf 'Candidate: %s versionCode %s, signer %s\n' "$new_package" "$new_code" "$new_cert"
if [ "$old_package" != "$new_package" ] || [ "$new_code" -le "$old_code" ] || [ "$old_cert" != "$new_cert" ]; then
  printf 'FAIL: This candidate is not a same-signer in-place update.\n' >&2
  exit 1
fi
printf 'PASS: same package and signing certificate, higher version code.\n'
