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

    print("AndroidManifest.xml successfully configured!")
    return True

if __name__ == "__main__":
    success = configure_android()
    if not success:
        exit(1)
