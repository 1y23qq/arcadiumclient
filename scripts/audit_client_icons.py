from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def audit(root: Path = ROOT) -> dict:
    manifest = json.loads((root / 'licenses/assets/client-lucide.json').read_text(encoding='utf-8'))
    expected = {f'assets/arcadium/lucide/{name}.svg': entry['sha256'] for name, entry in manifest['icons'].items()}
    prefixes = ('client-ui/modules/ui/src/main/resources/', 'client-ui/modules/internal/src/main/resources/',
                'client-runtime/src/main/resources/')
    for path, entry in manifest['aliases'].items():
        for prefix in prefixes:
            if path.startswith(prefix):
                expected[path[len(prefix):]] = entry['sha256']
                break
    distribution = root / 'packages/arcadium_common/assets/distribution'
    catalog = json.loads((distribution / 'client-components.json').read_text(encoding='utf-8'))
    result = {}
    for version in ('1.7.10', '1.8.9', '1.21.11', '26.2'):
        seen = set()
        licensed = False
        module_catalog = False
        archives = []

        def inspect(raw: bytes, label: str) -> None:
            nonlocal licensed, module_catalog
            with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                archives.append(label)
                for name in archive.namelist():
                    if name in expected:
                        digest = hashlib.sha256(archive.read(name)).hexdigest()
                        if digest != expected[name]:
                            raise ValueError(f'{version}: outdated icon in {label}: {name}')
                        seen.add(name)
                    elif name == 'org/polyfrost/oneconfig/internal/ui/api/ModuleIconCatalog.class':
                        module_catalog = True
                    elif 'lucide' in name.lower() and name.lower().endswith('license.txt'):
                        licensed |= hashlib.sha256(archive.read(name)).hexdigest() == manifest['licenseSha256']
                    elif name.startswith('META-INF/jars/') and name.endswith('.jar'):
                        inspect(archive.read(name), label + '!' + name)

        for entry in catalog[version]:
            path = distribution / entry['file']
            data = path.read_bytes()
            if hashlib.sha256(data).hexdigest() != entry['sha256']:
                raise ValueError(f'{version}: component checksum differs: {path.name}')
            inspect(data, path.name)
        missing = sorted(expected.keys() - seen)
        if missing or not licensed or not module_catalog:
            raise ValueError(json.dumps({'version': version, 'missing': missing, 'licensed': licensed, 'moduleCatalog': module_catalog}))
        result[version] = {'verifiedIcons': len(seen), 'licensePresent': licensed, 'moduleCatalogPresent': module_catalog,
                           'componentFiles': [entry['file'] for entry in catalog[version]], 'inspectedArchives': len(archives)}
    return {'passed': True, 'lucideCommit': manifest['commit'], 'minecraftVersions': result}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Verify shipped Lucide resources and notices in every Minecraft version.')
    parser.add_argument('--output', type=Path, default=ROOT / 'build/client-icon-audit.json')
    args = parser.parse_args()
    report = audit()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(report))
