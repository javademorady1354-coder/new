#!/usr/bin/env python3
from html.parser import HTMLParser
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
GAMES = [
    "challenge-mind.html", "number-sequence.html", "eagle-eye.html",
    "color-recognition.html", "multi-point-tracking.html", "odd-shape.html"
]
EXTRA = ("subscription.html", "updates.html", "privacy-policy.html")

class AssetParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
        self.scripts = []
        self.in_script = False
        self.current_script = []
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag in ("a", "script", "link") and attrs.get("href"):
            self.links.append(attrs["href"])
        if tag == "script":
            self.in_script = True
            self.current_script = []
    def handle_data(self, data):
        if self.in_script:
            self.current_script.append(data)
    def handle_endtag(self, tag):
        if tag == "script" and self.in_script:
            self.scripts.append("".join(self.current_script))
            self.in_script = False

home = ASSETS / "index.html"
assert home.is_file(), "index.html is missing"
parser = AssetParser()
parser.feed(home.read_text(encoding="utf-8"))
for page in GAMES + EXTRA:
    assert (ASSETS / page).is_file(), f"Missing asset: {page}"
    assert page in parser.links, f"Page is not linked from home: {page}"

for html_file in sorted(ASSETS.glob("*.html")):
    page = AssetParser()
    page.feed(html_file.read_text(encoding="utf-8"))
    for link in page.links:
        if link.endswith(".html") and "://" not in link and not link.startswith("#"):
            assert (ASSETS / link).is_file(), f"Broken local link in {html_file.name}: {link}"

checked = 0
for html_file in sorted(ASSETS.glob("*.html")):
    page = AssetParser()
    page.feed(html_file.read_text(encoding="utf-8"))
    for script in page.scripts:
        if not script.strip():
            continue
        with tempfile.NamedTemporaryFile("w", suffix=".js", encoding="utf-8") as tmp:
            tmp.write(script)
            tmp.flush()
            subprocess.run(["node", "--check", tmp.name], check=True)
        checked += 1

assert (ROOT / "app/src/main/res/drawable/ic_launcher.xml").is_file(), "Launcher icon is missing"
print(f"OK: {len(GAMES)} games, {len(EXTRA)} support pages, local links, launcher icon and {checked} JavaScript blocks validated.")
