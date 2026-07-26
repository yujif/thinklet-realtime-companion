import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP_ID = "com.yujif.thinklet.realtimecompanion"


class AndroidIdentityTest(unittest.TestCase):
    def test_gradle_and_thinklet_key_config_use_public_application_id(self):
        build_gradle = (ROOT / "app" / "build.gradle.kts").read_text()
        self.assertEqual(
            APP_ID,
            re.search(r'namespace = "([^"]+)"', build_gradle).group(1),
        )
        self.assertEqual(
            APP_ID,
            re.search(r'applicationId = "([^"]+)"', build_gradle).group(1),
        )

        key_config = json.loads(
            (ROOT / "device-config" / "key_config_realtimecompanion.json").read_text()
        )
        action_param = key_config["key-config"][0]["key-action"]["action-param"]
        self.assertEqual(APP_ID, action_param["package-name"])
        self.assertEqual(f"{APP_ID}.MainActivity", action_param["class-name"])

    def test_kotlin_package_root_matches_public_application_id(self):
        source_root = ROOT / "app" / "src" / "main" / "java"
        package_root = source_root / Path(APP_ID.replace(".", "/"))
        self.assertTrue(package_root.is_dir())

    def test_byok_preferences_are_excluded_from_all_android_backup_paths(self):
        manifest = (ROOT / "app" / "src" / "main" / "AndroidManifest.xml").read_text()
        self.assertIn('android:dataExtractionRules="@xml/data_extraction_rules"', manifest)
        self.assertIn('android:fullBackupContent="@xml/backup_rules"', manifest)

        for resource in ("data_extraction_rules.xml", "backup_rules.xml"):
            rules = (ROOT / "app" / "src" / "main" / "res" / "xml" / resource).read_text()
            self.assertIn('domain="sharedpref"', rules)
            self.assertIn('path="api_key_prefs.xml"', rules)


if __name__ == "__main__":
    unittest.main()
