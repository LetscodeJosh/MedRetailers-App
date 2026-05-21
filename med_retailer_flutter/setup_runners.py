import os
import re

def customize_android():
    print("Customizing Android Runner...")
    
    gradle_path = os.path.join("android", "app", "build.gradle")
    if os.path.exists(gradle_path):
        with open(gradle_path, "r", encoding="utf-8") as f:
            content = f.read()
        
        # Replace namespace and applicationId (supports with or without = sign)
        content = re.sub(
            r'namespace\s*=?\s*["\'][^"\']+["\']',
            'namespace = "com.pims.medretailers"',
            content
        )
        content = re.sub(
            r'applicationId\s*=?\s*["\'][^"\']+["\']',
            'applicationId = "com.pims.medretailers"',
            content
        )
        
        # Match minSdk/minSdkVersion and targetSdk/targetSdkVersion
        content = re.sub(
            r'minSdk(Version)?\s*=?\s*[a-zA-Z0-9._]+',
            'minSdk = 26',
            content
        )
        content = re.sub(
            r'targetSdk(Version)?\s*=?\s*[a-zA-Z0-9._]+',
            'targetSdk = 35',
            content
        )
        
        with open(gradle_path, "w", encoding="utf-8") as f:
            f.write(content)
        print("Successfully updated android/app/build.gradle")
    else:
        print("Warning: android/app/build.gradle not found!")

    manifest_path = os.path.join("android", "app", "src", "main", "AndroidManifest.xml")
    if os.path.exists(manifest_path):
        with open(manifest_path, "r", encoding="utf-8") as f:
            content = f.read()
        
        # Update app label
        content = re.sub(
            r'android:label\s*=\s*["\'][^"\']+["\']',
            'android:label="MedRetailer"',
            content
        )
        
        with open(manifest_path, "w", encoding="utf-8") as f:
            f.write(content)
        print("Successfully updated AndroidManifest.xml")
    else:
        print("Warning: AndroidManifest.xml not found!")


def customize_ios():
    print("Customizing iOS Runner...")
    
    pbxproj_path = os.path.join("ios", "Runner.xcodeproj", "project.pbxproj")
    if os.path.exists(pbxproj_path):
        with open(pbxproj_path, "r", encoding="utf-8") as f:
            content = f.read()
        
        # Replace Bundle Identifier
        content = re.sub(
            r'PRODUCT_BUNDLE_IDENTIFIER\s*=\s*[^;]+;',
            'PRODUCT_BUNDLE_IDENTIFIER = com.pims.medretailers;',
            content
        )
        
        # Disable Code Signing requirements for cloud builds
        # Find build settings sections and inject/override code sign variables
        build_settings_pattern = r'(buildSettings\s*=\s*\{([^}]+)\})'
        
        def replace_settings(match):
            original = match.group(1)
            settings_body = match.group(2)
            
            # Ensure keys exist or overwrite them
            overrides = {
                "CODE_SIGN_IDENTITY": '""',
                "CODE_SIGNING_REQUIRED": "NO",
                "CODE_SIGNING_ALLOWED": "NO",
                "DEVELOPMENT_TEAM": "AB12CD34EF",
            }
            
            for key, val in overrides.items():
                pattern = rf'{key}\s*=\s*[^;]+;'
                replacement = f'{key} = {val};'
                if re.search(pattern, settings_body):
                    settings_body = re.sub(pattern, replacement, settings_body)
                else:
                    settings_body += f'\n\t\t\t\t{replacement}'
            
            return f'buildSettings = {{{settings_body}}}'

        content = re.sub(build_settings_pattern, replace_settings, content)
        
        with open(pbxproj_path, "w", encoding="utf-8") as f:
            f.write(content)
        print("Successfully updated ios/Runner.xcodeproj/project.pbxproj")
    else:
        print("Warning: ios/Runner.xcodeproj/project.pbxproj not found!")

    plist_path = os.path.join("ios", "Runner", "Info.plist")
    if os.path.exists(plist_path):
        with open(plist_path, "r", encoding="utf-8") as f:
            content = f.read()
        
        # Replace App Display Name and Bundle Name
        content = re.sub(
            r'<key>CFBundleDisplayName</key>\s*<string>[^<]+</string>',
            '<key>CFBundleDisplayName</key>\n\t<string>MedRetailer</string>',
            content
        )
        content = re.sub(
            r'<key>CFBundleName</key>\s*<string>[^<]+</string>',
            '<key>CFBundleName</key>\n\t<string>MedRetailer</string>',
            content
        )
        
        with open(plist_path, "w", encoding="utf-8") as f:
            f.write(content)
        print("Successfully updated ios/Runner/Info.plist")
    else:
        print("Warning: ios/Runner/Info.plist not found!")


def main():
    customize_android()
    customize_ios()
    print("Runner customization complete.")

if __name__ == "__main__":
    main()
