import os
import re

def customize_android():
    print("Customizing Android Runner...")
    
    gradle_path = os.path.join("android", "app", "build.gradle.kts")
    is_kts = True
    if not os.path.exists(gradle_path):
        gradle_path = os.path.join("android", "app", "build.gradle")
        is_kts = False
        
    if os.path.exists(gradle_path):
        with open(gradle_path, "r", encoding="utf-8") as f:
            content = f.read()
        
        # We do NOT replace namespace because it must match the Kotlin package (com.pims.med_retailer_flutter)
        # to prevent java.lang.ClassNotFoundException: Didn't find class "com.pims.medretailers.MainActivity" on launch.
        # We only replace the applicationId (the Play Store package ID).
        content = re.sub(
            r'applicationId\s*=?\s*["\'][^"\']+["\']',
            'applicationId = "com.pims.medretailers"',
            content
        )
        
        # Match minSdk/minSdkVersion and targetSdk/targetSdkVersion
        content = re.sub(
            r'minSdk(Version)?\s*=?\s*[a-zA-Z0-9._]+',
            'minSdk = 24',
            content
        )
        content = re.sub(
            r'targetSdk(Version)?\s*=?\s*[a-zA-Z0-9._]+',
            'targetSdk = 35',
            content
        )
        
        # Match compileSdk/compileSdkVersion
        content = re.sub(
            r'compileSdk(Version)?\s*=?\s*[a-zA-Z0-9._]+',
            'compileSdk = 35',
            content
        )
        
        # Match versionCode
        content = re.sub(
            r'versionCode\s*=?\s*[a-zA-Z0-9._]+',
            'versionCode = 18',
            content
        )
        
        # Inject configuration strategy to exclude duplicate Kotlin stdlib libraries
        if is_kts:
            exclude_strategy = """
configurations {
    all {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    }
}
"""
        else:
            exclude_strategy = """
configurations {
    all {
        exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk8'
        exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk7'
    }
}
"""
        content += exclude_strategy
        
        with open(gradle_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Successfully updated {gradle_path} with SDK versions and exclude strategy")
    else:
        print("Warning: android/app/build.gradle(.kts) not found!")
 
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
        
        # Inject Internet Permission (required for release HTTP/API requests)
        if "android.permission.INTERNET" not in content:
            content = re.sub(
                r'<manifest[^>]*>',
                lambda m: m.group(0) + '\n    <uses-permission android:name="android.permission.INTERNET"/>',
                content
            )
        
        with open(manifest_path, "w", encoding="utf-8") as f:
            f.write(content)
        print("Successfully updated AndroidManifest.xml with Internet permission")
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
        
        # Enable Code Signing requirements for local builds
        # Find build settings sections and inject/override code sign variables
        build_settings_pattern = r'(buildSettings\s*=\s*\{([^}]+)\})'
        
        def replace_settings(match):
            original = match.group(1)
            settings_body = match.group(2)
            
            # Ensure keys exist or overwrite them
            overrides = {
                "CODE_SIGN_IDENTITY": '"Apple Development"',
                "CODE_SIGN_STYLE": "Automatic",
                "CODE_SIGNING_REQUIRED": "YES",
                "CODE_SIGNING_ALLOWED": "YES",
                "DEVELOPMENT_TEAM": '""',
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


def customize_root_gradle():
    print("Customizing Root Android Gradle...")
    root_gradle_path = os.path.join("android", "build.gradle.kts")
    is_kts = True
    if not os.path.exists(root_gradle_path):
        root_gradle_path = os.path.join("android", "build.gradle")
        is_kts = False
        
    if os.path.exists(root_gradle_path):
        with open(root_gradle_path, "r", encoding="utf-8") as f:
            content = f.read()
            
        if is_kts:
            exclude_strategy = """
subprojects {
    configurations.all {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    }
}
"""
        else:
            exclude_strategy = """
subprojects {
    configurations.all {
        exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk8'
        exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk7'
    }
}
"""
        if "kotlin-stdlib-jdk8" not in content:
            content += exclude_strategy
            with open(root_gradle_path, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"Successfully updated root {root_gradle_path} with subprojects exclude strategy")
    else:
        print("Warning: root android/build.gradle(.kts) not found!")


def customize_gradle_properties():
    print("Customizing gradle.properties...")
    props_path = os.path.join("android", "gradle.properties")
    if os.path.exists(props_path):
        with open(props_path, "r", encoding="utf-8") as f:
            content = f.read()
        
        # Force Flutter Gradle plugin to use targetSdk 35
        if "flutter.targetSdkVersion" in content:
            content = re.sub(
                r'flutter\.targetSdkVersion\s*=\s*\d+',
                'flutter.targetSdkVersion=35',
                content
            )
        else:
            content += "\nflutter.targetSdkVersion=35\n"
        
        if "flutter.compileSdkVersion" in content:
            content = re.sub(
                r'flutter\.compileSdkVersion\s*=\s*\d+',
                'flutter.compileSdkVersion=35',
                content
            )
        else:
            content += "flutter.compileSdkVersion=35\n"
        
        with open(props_path, "w", encoding="utf-8") as f:
            f.write(content)
        print("Successfully updated gradle.properties with flutter.targetSdkVersion=35")
    else:
        print("Warning: android/gradle.properties not found!")


def main():
    customize_android()
    customize_root_gradle()
    customize_gradle_properties()
    customize_ios()
    print("Runner customization complete.")

if __name__ == "__main__":
    main()
