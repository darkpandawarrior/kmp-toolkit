#!/usr/bin/env bash
# test.sh — round-trip check for this SOPS+age vault demo.
#
# If sops + age-keygen are on PATH: generates a throwaway age identity,
# encrypts vault/example.secrets.yaml, decrypts it back, and asserts the
# plaintext round-trips byte-for-byte. Exits non-zero on mismatch.
#
# If either tool is missing: prints the equivalent manual steps and exits 0
# (a missing local tool isn't a failure of this repo).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if ! command -v sops >/dev/null 2>&1 || ! command -v age-keygen >/dev/null 2>&1; then
  cat <<'EOF'
sops and/or age-keygen not found on PATH — skipping the live round-trip.
(install: brew install sops age)

Manual steps to verify this vault once both are installed:
  1. age-keygen -o /tmp/kmp-secrets-demo.key
  2. pub=$(grep '^# public key:' /tmp/kmp-secrets-demo.key | cut -d: -f2 | tr -d ' ')
  3. sops --age "$pub" -e vault/example.secrets.yaml > /tmp/kmp-secrets-demo.enc.yaml
  4. SOPS_AGE_KEY_FILE=/tmp/kmp-secrets-demo.key sops -d /tmp/kmp-secrets-demo.enc.yaml
  5. diff <(SOPS_AGE_KEY_FILE=/tmp/kmp-secrets-demo.key sops -d /tmp/kmp-secrets-demo.enc.yaml) \
          vault/example.secrets.yaml && echo OK
EOF
  exit 0
fi

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
age-keygen -o "$tmp/key.txt" 2>/dev/null
pub="$(grep '^# public key:' "$tmp/key.txt" | cut -d: -f2 | tr -d ' ')"

sops --age "$pub" -e vault/example.secrets.yaml > "$tmp/enc.yaml"
SOPS_AGE_KEY_FILE="$tmp/key.txt" sops -d "$tmp/enc.yaml" > "$tmp/dec.yaml"

if diff -q "$tmp/dec.yaml" vault/example.secrets.yaml >/dev/null; then
  echo "round-trip OK: decrypted output matches vault/example.secrets.yaml"
else
  echo "round-trip FAILED: decrypted output differs from vault/example.secrets.yaml" >&2
  exit 1
fi
