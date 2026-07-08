#!/bin/bash
echo "Building iOS app..."
flutter build ios --release --no-codesign

if [ $? -eq 0 ]; then
  echo "Packaging into Payload..."
  mkdir -p Payload
  cp -R build/ios/iphoneos/Runner.app Payload/
  
  echo "Zipping to MedRetailers-Signed.ipa..."
  zip -r -q MedRetailers-Signed.ipa Payload
  rm -rf Payload
  
  echo "Success! The .ipa is ready at $(pwd)/MedRetailers-Signed.ipa"
else
  echo "Build failed!"
  exit 1
fi
