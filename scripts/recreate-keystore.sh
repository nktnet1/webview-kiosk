#!/bin/bash
# Script to recreate the Android keystore with compatible settings

set -e

echo "=========================================="
echo "Android Keystore Recreation Script"
echo "=========================================="
echo ""
echo "This script will create a new keystore with settings compatible"
echo "with the Android Gradle Plugin signing tools."
echo ""

# Check if keystore already exists
KEYSTORE_FILE="app/keystore.jks"
if [ -f "$KEYSTORE_FILE" ]; then
    echo "WARNING: Keystore file already exists at $KEYSTORE_FILE"
    read -p "Do you want to overwrite it? (yes/no): " OVERWRITE
    if [ "$OVERWRITE" != "yes" ]; then
        echo "Aborting."
        exit 1
    fi
    rm "$KEYSTORE_FILE"
fi

# Prompt for keystore details
echo ""
echo "Please enter the keystore details:"
echo ""
read -p "Key Alias (default: release): " KEY_ALIAS
KEY_ALIAS=${KEY_ALIAS:-release}

read -sp "Keystore Password: " KEYSTORE_PASSWORD
echo ""
read -sp "Confirm Keystore Password: " KEYSTORE_PASSWORD_CONFIRM
echo ""

if [ "$KEYSTORE_PASSWORD" != "$KEYSTORE_PASSWORD_CONFIRM" ]; then
    echo "ERROR: Passwords do not match"
    exit 1
fi

read -sp "Key Password (press Enter to use same as keystore password): " KEY_PASSWORD
echo ""
if [ -z "$KEY_PASSWORD" ]; then
    KEY_PASSWORD="$KEYSTORE_PASSWORD"
fi

# Prompt for certificate details
echo ""
echo "Certificate details (CN=Common Name, OU=Organization Unit, etc.):"
read -p "Common Name (e.g., Your Name): " CN
read -p "Organization Unit (e.g., Development): " OU
read -p "Organization (e.g., Your Company): " O
read -p "City/Locality: " L
read -p "State/Province: " ST
read -p "Country Code (2 letters, e.g., US): " C

DNAME="CN=$CN, OU=$OU, O=$O, L=$L, ST=$ST, C=$C"

echo ""
echo "=========================================="
echo "Creating keystore with the following settings:"
echo "=========================================="
echo "File: $KEYSTORE_FILE"
echo "Alias: $KEY_ALIAS"
echo "Validity: 10000 days (~27 years)"
echo "Key Algorithm: RSA with 2048 bits"
echo "Signature Algorithm: SHA256withRSA"
echo "Keystore Type: JKS (for maximum compatibility)"
echo "Distinguished Name: $DNAME"
echo ""

# Create the keystore with explicit compatible settings
# Using JKS format (not PKCS12) for better compatibility
# Using standard algorithms that are well-supported
keytool -genkeypair \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -sigalg SHA256withRSA \
    -validity 10000 \
    -storetype JKS \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "$DNAME"

echo ""
echo "=========================================="
echo "Keystore created successfully!"
echo "=========================================="
echo ""
echo "Next steps:"
echo ""
echo "1. Verify the keystore:"
echo "   keytool -list -v -keystore $KEYSTORE_FILE -storepass <password>"
echo ""
echo "2. Convert to base64 for GitHub Secrets:"
echo "   base64 -w 0 $KEYSTORE_FILE > keystore.base64"
echo ""
echo "3. Update GitHub Secrets:"
echo "   - RELEASE_KEYSTORE_BASE64: (content of keystore.base64)"
echo "   - KEYSTORE_PASSWORD: $KEYSTORE_PASSWORD"
echo "   - KEY_ALIAS: $KEY_ALIAS"
echo "   - KEY_PASSWORD: (your key password)"
echo ""
echo "4. IMPORTANT: Store these credentials securely!"
echo "   Consider using a password manager."
echo ""
echo "5. Clean up sensitive files:"
echo "   rm keystore.base64"
echo "   rm $KEYSTORE_FILE  # After uploading to GitHub Secrets"
echo ""

# Offer to create base64 version
read -p "Do you want to create the base64 version now? (yes/no): " CREATE_BASE64
if [ "$CREATE_BASE64" = "yes" ]; then
    base64 -w 0 "$KEYSTORE_FILE" > keystore.base64
    echo ""
    echo "Base64 keystore saved to: keystore.base64"
    echo "Copy this content to GitHub Secrets as RELEASE_KEYSTORE_BASE64"
    echo ""
fi

echo "Done!"
