#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# generate-keys.sh
#
# Generates RSA-2048 key pair for JWT signing:
#   keys/private_key.pem  — used by auth-service to SIGN tokens
#   keys/public_key.pem   — used by api-gateway to VERIFY tokens
#
# Run once before first deployment. Keep private_key.pem SECRET.
# In production: store in Vault / AWS Secrets Manager / K8s Secrets.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

KEYS_DIR="$(dirname "$0")/../keys"
mkdir -p "$KEYS_DIR"

echo "🔑 Generating RSA-2048 key pair..."

# Generate private key (PKCS#8 format required by JJWT)
openssl genpkey -algorithm RSA \
  -pkeyopt rsa_keygen_bits:2048 \
  -out "$KEYS_DIR/private_key.pem"

# Extract public key
openssl rsa -pubout \
  -in  "$KEYS_DIR/private_key.pem" \
  -out "$KEYS_DIR/public_key.pem"

# Restrict permissions on private key
chmod 600 "$KEYS_DIR/private_key.pem"
chmod 644 "$KEYS_DIR/public_key.pem"

echo "✅ Keys generated:"
echo "   Private: $KEYS_DIR/private_key.pem  (SECRET — never commit)"
echo "   Public:  $KEYS_DIR/public_key.pem   (safe to distribute)"

# Verify the key pair
echo ""
echo "🔍 Verifying key pair..."
MODULUS_PRIVATE=$(openssl rsa -in "$KEYS_DIR/private_key.pem" -modulus -noout)
MODULUS_PUBLIC=$(openssl rsa -pubin -in "$KEYS_DIR/public_key.pem" -modulus -noout)

if [ "$MODULUS_PRIVATE" = "$MODULUS_PUBLIC" ]; then
  echo "✅ Key pair verified — moduli match"
else
  echo "❌ Key pair mismatch — something went wrong!"
  exit 1
fi
