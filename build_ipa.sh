#!/bin/bash
echo "Auto-incrementing build number in pubspec.yaml..."
python3 -c "
import re
with open('pubspec.yaml', 'r') as f:
    content = f.read()

def repl(m):
    ver, build = m.group(1), int(m.group(2))
    print(f'Incrementing build number from {build} to {build+1}...')
    return f'version: {ver}+{build+1}'

new_content = re.sub(r'^version:\s*([0-9\.]+)\+(\d+)', repl, content, flags=re.MULTILINE)
with open('pubspec.yaml', 'w') as f:
    f.write(new_content)
"

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
