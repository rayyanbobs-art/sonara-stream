import os
import shutil
import re
import xml.etree.ElementTree as ET

def configure_android():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    gen_android_dir = os.path.join(base_dir, "src-tauri", "gen", "android")
    patches_dir = os.path.join(base_dir, "src-tauri", "android-patches")
    
    if not os.path.exists(gen_android_dir):
        print(f"Error: {gen_android_dir} does not exist. Run 'tauri android init' first.")
        return False

    # 1. Copy Kotlin patches
    target_kotlin_dir = os.path.join(
        gen_android_dir, "app", "src", "main", "java", "com", "sonara", "stream"
    )
    os.makedirs(target_kotlin_dir, exist_ok=True)

    main_activity_target = os.path.join(target_kotlin_dir, "MainActivity.kt")
    if os.path.exists(main_activity_target):
        try:
            with open(main_activity_target, "r", encoding="utf-8") as f:
                print("Original MainActivity.kt contents:\n" + f.read())
        except Exception as e:
            print("Could not read original MainActivity.kt:", e)

    for patch_file in ["MainActivity.kt", "MediaPlaybackService.kt"]:
        src_path = os.path.join(patches_dir, patch_file)
        dest_path = os.path.join(target_kotlin_dir, patch_file)
        if os.path.exists(src_path):
            shutil.copy2(src_path, dest_path)
            print(f"Copied {patch_file} -> {dest_path}")
        else:
            print(f"Warning: {src_path} not found")

    # 2. Patch AndroidManifest.xml
    manifest_path = os.path.join(
        gen_android_dir, "app", "src", "main", "AndroidManifest.xml"
    )
    if not os.path.exists(manifest_path):
        print(f"Error: {manifest_path} does not exist.")
        return False

    with open(manifest_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Ensure usesCleartextTraffic is true
    if 'android:usesCleartextTraffic="true"' not in content:
        content = content.replace(
            "<application ",
            '<application android:usesCleartextTraffic="true" '
        )
        print("Added usesCleartextTraffic=true to <application>")

    # Permissions to ensure
    required_permissions = [
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.WAKE_LOCK",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
        "android.permission.POST_NOTIFICATIONS",
    ]

    permissions_to_insert = []
    for perm in required_permissions:
        if f'android:name="{perm}"' not in content:
            permissions_to_insert.append(f'    <uses-permission android:name="{perm}" />')

    if permissions_to_insert:
        perm_block = "\n".join(permissions_to_insert) + "\n"
        content = content.replace("<application", perm_block + "    <application", 1)
        print(f"Inserted permissions: {required_permissions}")

    # Ensure MediaPlaybackService is declared
    service_decl = '<service android:name=".MediaPlaybackService" android:foregroundServiceType="mediaPlayback" android:exported="false" />'
    if "MediaPlaybackService" not in content:
        # Insert before </application>
        content = content.replace("</application>", f"        {service_decl}\n    </application>")
        print("Registered MediaPlaybackService in AndroidManifest.xml")

    with open(manifest_path, "w", encoding="utf-8") as f:
        f.write(content)

    # 3. Patch app/build.gradle.kts for androidx.media dependency
    gradle_path = os.path.join(gen_android_dir, "app", "build.gradle.kts")
    if os.path.exists(gradle_path):
        with open(gradle_path, "r", encoding="utf-8") as f:
            gradle_content = f.read()
        if "androidx.media:media" not in gradle_content:
            gradle_content = gradle_content.replace(
                "dependencies {",
                'dependencies {\n    implementation("androidx.media:media:1.7.0")'
            )
            with open(gradle_path, "w", encoding="utf-8") as f:
                f.write(gradle_content)
            print("Added androidx.media:media:1.7.0 dependency to build.gradle.kts")
    else:
        gradle_groovy_path = os.path.join(gen_android_dir, "app", "build.gradle")
        if os.path.exists(gradle_groovy_path):
            with open(gradle_groovy_path, "r", encoding="utf-8") as f:
                gradle_content = f.read()
            if "androidx.media:media" not in gradle_content:
                gradle_content = gradle_content.replace(
                    "dependencies {",
                    "dependencies {\n    implementation 'androidx.media:media:1.7.0'"
                )
                with open(gradle_groovy_path, "w", encoding="utf-8") as f:
                    f.write(gradle_content)
                print("Added androidx.media:media:1.7.0 dependency to build.gradle")

    # 4. Patch styles/themes to ensure dark window background
    res_dir = os.path.join(gen_android_dir, "app", "src", "main", "res")
    for values_folder in ["values", "values-night"]:
        for xml_name in ["styles.xml", "themes.xml"]:
            xml_path = os.path.join(res_dir, values_folder, xml_name)
            if os.path.exists(xml_path):
                try:
                    with open(xml_path, "r", encoding="utf-8") as f:
                        style_content = f.read()
                    if "android:windowBackground" not in style_content:
                        style_content = style_content.replace(
                            "</style>",
                            '    <item name="android:windowBackground">#09090b</item>\n    </style>'
                        )
                        with open(xml_path, "w", encoding="utf-8") as f:
                            f.write(style_content)
                        print(f"Patched windowBackground in {xml_path}")
                except Exception as e:
                    print(f"Error patching {xml_path}: {e}")

    print("Android configuration complete!")
    return True

if __name__ == "__main__":
    success = configure_android()
    if not success:
        exit(1)
