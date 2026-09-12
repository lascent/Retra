#!/usr/bin/env python3
from html.parser import HTMLParser
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

class IdCollector(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = []
    def handle_starttag(self, tag, attrs):
        for key, value in attrs:
            if key == 'id' and value:
                self.ids.append(value)

errors = []
for rel in [
    'app/src/main/AndroidManifest.xml',
    'app/src/main/res/xml/backup_rules.xml',
    'app/src/main/res/xml/data_extraction_rules.xml',
]:
    try:
        ET.parse(ROOT / rel)
    except Exception as exc:
        errors.append(f'{rel}: invalid XML: {exc}')

html_path = ROOT / 'app/src/main/assets/retra/index.html'
html = html_path.read_text(encoding='utf-8')
collector = IdCollector()
collector.feed(html)
duplicates = sorted({value for value in collector.ids if collector.ids.count(value) > 1})
if duplicates:
    errors.append('Duplicate HTML IDs: ' + ', '.join(duplicates))

required_ids = {
    'libraryGrid', 'librarySelectionBar', 'librarySelectionCount',
    'librarySelectionCategories', 'librarySelectionFavorite', 'librarySelectionMore',
    'multiCategoryModal', 'multiCategoryList', 'multiCategorySave'
}
missing = sorted(required_ids - set(collector.ids))
if missing:
    errors.append('Missing required UI IDs: ' + ', '.join(missing))

if 'Install shaders coming soon' in html or 'INSTALL SHADERS' in html:
    errors.append('Unfinished shader installer is exposed in release HTML')

asset_root = ROOT / 'app/src/main/assets/retra'
script_sources = re.findall(r'<script\s+src=["\']([^"\']+\.js)["\']', html, re.I)
if not script_sources:
    errors.append('No Web UI JavaScript modules are referenced by index.html')
script_chunks = []
for source in script_sources:
    script_path = asset_root / source
    if not script_path.exists():
        errors.append(f'Missing referenced JavaScript module: {source}')
        continue
    script_chunks.append(script_path.read_text(encoding='utf-8'))
script = '\n'.join(script_chunks)
if re.search(r'Coming soon', script, re.I):
    errors.append('Placeholder "Coming soon" text remains in Web UI JavaScript')

style_sources = re.findall(r'<link[^>]+href=["\']([^"\']+\.css)["\']', html, re.I)
for source in style_sources:
    style_path = asset_root / source
    if not style_path.exists():
        errors.append(f'Missing referenced stylesheet module: {source}')
        continue
    style = style_path.read_text(encoding='utf-8')
    if style.count('/*') != style.count('*/'):
        errors.append(f'Unbalanced CSS comment boundary in {source}')

manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
if 'android:usesCleartextTraffic="false"' not in manifest:
    errors.append('Cleartext traffic is not explicitly disabled')

if errors:
    print('Retra release validation FAILED:')
    for error in errors:
        print(' -', error)
    sys.exit(1)

print(f'Retra release validation passed: {len(collector.ids)} unique HTML IDs, XML valid, release placeholders cleared.')
