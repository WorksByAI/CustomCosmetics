# SPDX-License-Identifier: AGPL-3.0-or-later
import importlib.util
from pathlib import Path
import unittest

tool = Path(__file__).with_name('generate-accessory-materials.py')
spec = importlib.util.spec_from_file_location('accessory_materials', tool)
materials = importlib.util.module_from_spec(spec)
spec.loader.exec_module(materials)

class AccessoryMaterialsTest(unittest.TestCase):
    def test_both_original_atlases_reproduce_the_bundled_assets_exactly(self):
        folder = tool.parent.parent / 'src/main/resources/assets/customcosmetics/textures/accessories'
        self.assertEqual(materials.png(), (folder / 'materials.png').read_bytes())
        self.assertEqual(materials.png(materials.detailed_atlas_pixels(), 256), (folder / 'detailed-materials.png').read_bytes())

    def test_detailed_materials_are_opaque_and_have_finite_bounded_color_channels(self):
        data = materials.detailed_atlas_pixels()
        self.assertEqual(256 * 256 * 4, len(data))
        self.assertEqual({255}, set(data[3::4]))
        self.assertGreaterEqual(min(data[::4]), 190)
        self.assertLessEqual(max(data[::4]), 255)

if __name__ == '__main__':
    unittest.main()
