"""Refresh the offline editor catalog from Hypixel's public item metadata, without assets."""
import argparse
import json
import re
from pathlib import Path
import urllib.request

SOURCE = "https://api.hypixel.net/v2/resources/skyblock/items"
CATEGORIES = {"SWORD", "LONGSWORD", "BOW", "AXE", "WAND", "GAUNTLET"}
FORMATTING = re.compile(
    r"§[0-9a-fk-orx]|%%(?:black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white|obfuscated|bold|strikethrough|underlined|italic|reset)%%", re.IGNORECASE)


def catalog(data):
    if data.get("success") is not True:
        raise ValueError("Invalid Hypixel item response")
    items = [{"id": row["id"], "name": FORMATTING.sub("", row["name"]).strip(), "category": row["category"]}
             for row in data["items"] if row.get("category") in CATEGORIES and not row["id"].endswith("_PREVIEW")]
    if len({row["id"] for row in items}) != len(items):
        raise ValueError("Duplicate weapon identity")
    items.sort(key=lambda row: row["id"])
    return {"source": SOURCE, "lastUpdated": data.get("lastUpdated"), "items": items}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, help="Read a previously downloaded official response")
    args = parser.parse_args()
    if args.input:
        data = json.loads(args.input.read_text(encoding="utf-8"))
    else:
        with urllib.request.urlopen(SOURCE, timeout=30) as response:
            data = json.load(response)
    result = catalog(data)
    target = Path(__file__).resolve().parents[1] / "src/main/resources/assets/customcosmetics/skyblock-weapons.json"
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Updated {len(result['items'])} SkyBlock weapon identities.")
