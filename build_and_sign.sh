#!/bin/bash
set -e

echo "=== 1. Building Flutter iOS Release Bundle ==="
export PATH="$HOME/development/flutter/bin:$PATH"
flutter build ios --release --no-codesign

echo "=== 2. Creating temporary signing workspace ==="
rm -rf /Users/cig-it/Downloads/ipa_signing
mkdir -p /Users/cig-it/Downloads/ipa_signing

echo "=== 3. Extracting build target ==="
cp -r build/ios/iphoneos/Runner.app /Users/cig-it/Downloads/ipa_signing/Runner.app

cd /Users/cig-it/Downloads/ipa_signing
mkdir -p Payload
mv Runner.app Payload/

SIGN_ID="Apple Development: joshtn234@gmail.com (X3JS4TJ7G4)"

echo "=== 4. Code signing embedded frameworks ==="
find Payload/Runner.app/Frameworks -name "*.framework" -o -name "*.dylib" 2>/dev/null | while read framework; do
  codesign --force --sign "$SIGN_ID" "$framework" 2>&1
  echo "✅ Signed: $(basename "$framework")"
done

echo "=== 5. Code signing main application ==="
codesign --force --sign "$SIGN_ID" Payload/Runner.app 2>&1
echo "✅ Signed: Runner.app"

echo "=== 6. Packaging signed IPA ==="
rm -f /Users/cig-it/Downloads/MedRetailers-Signed.ipa
zip -r /Users/cig-it/Downloads/MedRetailers-Signed.ipa Payload > /dev/null

echo "=== 7. Cleaning up ==="
cd ..
rm -rf /Users/cig-it/Downloads/ipa_signing

echo "==============================================="
echo "🎉 SUCCESS: Signed IPA is ready at:"
echo "👉 /Users/cig-it/Downloads/MedRetailers-Signed.ipa"
echo "==============================================="
