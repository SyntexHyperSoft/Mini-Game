import re
import sys
import subprocess

with open('app/src/main/assets/game/index.html', 'r', encoding='utf-8') as f:
    html = f.read()

m = re.search(r'<script.*?>([\s\S]*?)<\/script>', html, re.I)
if not m:
    print("NO SCRIPT FOUND!")
    sys.exit(1)
script = m.group(1)

# Find all $('...') or document.getElementById('...')
pattern = r'(?:\$|document\.getElementById)\s*\(\s*[\'\"]([a-zA-Z0-9\-_]+)[\'\"]\s*\)'
ids_used = set(re.findall(pattern, script))
html_ids = set(re.findall(r'id=[\'\"]([a-zA-Z0-9\-_]+)[\'\"]', html))

missing = [i for i in sorted(ids_used) if i not in html_ids]
print(f'Total HTML IDs: {len(html_ids)}')
print(f'Total IDs used in JS: {len(ids_used)}')
print(f'Missing IDs ({len(missing)}):')
for m in missing:
    print('  -', m)

# Let's also check safeBind usages:
safe_binds = set(re.findall(r'safeBind\s*\(\s*[\'\"]([a-zA-Z0-9\-_]+)[\'\"]', script))
missing_binds = [i for i in sorted(safe_binds) if i not in html_ids]
print(f'Missing safeBind targets ({len(missing_binds)}):')
for m in missing_binds:
    print('  -', m)
