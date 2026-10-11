"""Regression checks for readable names and actual weapons in the offline catalog."""
import unittest
from update_weapon_catalog import catalog


class CatalogTest(unittest.TestCase):
    def test_filters_menu_previews_without_dropping_usable_weapons(self):
        result = catalog({"success": True, "items": [
            {"id": "AXE_PREVIEW", "name": "%%green%%Axe Preview", "category": "AXE"},
            {"id": "PREVIEW_BLADE", "name": "Preview Blade", "category": "SWORD"},
            {"id": "HYPERION", "name": "Hyperion", "category": "SWORD"},
            {"id": "TERMINATOR", "name": "Terminator", "category": "BOW"},
            {"id": "APPLE", "name": "Apple", "category": "CONSUMABLE"}]})
        self.assertEqual([row["id"] for row in result["items"]], ["HYPERION", "PREVIEW_BLADE", "TERMINATOR"])

    def test_cleans_formats_and_preserves_legitimate_punctuation(self):
        result = catalog({"success": True, "items": [{"id": "TEST", "category": "SWORD",
            "name": "  %%GREEN%%§lSword %%gray%%& Shield §x§f§f§0§0§a§a50%  "}]})
        self.assertEqual(result["items"][0]["name"], "Sword & Shield 50%")

    def test_rejects_invalid_response_and_duplicate_identity(self):
        with self.assertRaises(ValueError):
            catalog({"success": False})
        row = {"id": "TEST", "category": "SWORD", "name": "Test"}
        with self.assertRaises(ValueError):
            catalog({"success": True, "items": [row, row]})


if __name__ == "__main__":
    unittest.main()
