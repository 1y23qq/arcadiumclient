#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
import time
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from test_forge_compat_runtime import child_environment

BOOLEAN_DEFAULT_KEYS = frozenset((
    'cachedNameMasking', 'cachedFontMetrics', 'batchedVanillaFont', 'gpuNameTags',
    'retainedNameTagScenes', 'compactNameTagPreparation', 'fastInvisibleHolograms',
    'reduceRenderHookAllocations', 'efficientMotionBlur', 'retainedScoreboardPreparation',
    'gpuParticles', 'retainedParticleRecipes', 'retainedHudBatches', 'gpuModelTransforms',
    'gpuPlayerPipeline', 'residentPlayerState', 'gpuVoxelSkinLayers', 'gpuEquipmentPipeline',
    'directFramebufferPresentation', 'gpuTerrainCommands', 'worldRenderScale',
))


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def read_defaults(path, expected_digest):
    assert re.fullmatch('[0-9a-f]{64}', expected_digest or ''), 'Missing defaults certificate checksum'
    assert path.is_file() and path.stat().st_size <= 64 * 1024, 'Missing or oversized defaults certificate'
    assert digest(path) == expected_digest, 'Defaults certificate checksum mismatch'
    defaults = json.loads(path.read_text(encoding='utf-8-sig'))
    boolean_defaults = defaults['guiBooleanDefaults']
    assert set(boolean_defaults) == BOOLEAN_DEFAULT_KEYS, 'Unknown Beta default properties'
    assert all(type(value) is bool for value in boolean_defaults.values()), 'Defaults must be booleans'
    assert sum(boolean_defaults.values()) == 20 and boolean_defaults['worldRenderScale'] is False
    assert re.fullmatch('[0-9a-f]{64}', defaults['main']['sha256']), 'Missing runtime checksum'
    return defaults


def prepare(arguments):
    assert len(arguments.inputs_sha256) == 64 and digest(arguments.inputs) == arguments.inputs_sha256
    assert len(arguments.qa_sha256) == 64 and digest(arguments.qa) == arguments.qa_sha256
    defaults = read_defaults(arguments.defaults_proof, arguments.defaults_sha256)
    boolean_defaults = defaults['guiBooleanDefaults']
    if not arguments.control_gl:
        assert arguments.native_library and arguments.native_sha256 and digest(arguments.native_library) == arguments.native_sha256
        assert arguments.shader and arguments.shader_sha256 and digest(arguments.shader) == arguments.shader_sha256
    root = arguments.fixture.resolve()
    assert root.name.startswith('ArcadiumMetalClient072-') and not root.exists(), 'Use a fresh explicitly owned fixture'
    assert root.parent.is_dir() and not root.is_symlink() and not root.is_junction()
    with zipfile.ZipFile(arguments.inputs) as archive:
        entries = archive.infolist()
        assert len(entries) == 832 and len({x.filename for x in entries}) == 832
        assert sum(x.file_size for x in entries) <= 512 * 1024 * 1024
        assert archive.getinfo('manifest.json').file_size <= 1024 * 1024
        manifest = json.loads(archive.read('manifest.json'))
        assert manifest['schema'] == 'arcadium.private-metal-client-input.v1' and manifest['minecraft'] == '1.8.9'
        assert manifest['java'] == 25 and manifest['entrypoint'] == 'net.fabricmc.loader.impl.launch.knot.KnotClient'
        assert manifest['sourceRuntimeSha256'] == defaults['main']['sha256']
        assert manifest['fakeAccountOnly'] and not manifest['profilesConfigsLogsAuthIncluded'] and manifest['rawCommandNotIncluded'] is True
        listed = {item['path']: item for item in manifest['files']}
        assert len(listed) == 831 and set(listed) | {'manifest.json'} == {x.filename for x in entries}
        for entry in entries:
            path = PurePosixPath(entry.filename)
            assert not path.is_absolute() and '\\' not in entry.filename and '..' not in path.parts and str(path) == entry.filename
            assert not stat.S_ISLNK(entry.external_attr >> 16) and not entry.is_dir()
            if entry.filename == 'manifest.json':
                continue
            item = listed[entry.filename]
            assert item['role'] in {'classpath-public-jar', 'pinned-public-mod', 'source-built-client-component', 'vanilla-asset-index', 'vanilla-asset-object'}
            assert entry.file_size == item['bytes'] and len(item['sha256']) == 64
            if item['role'].endswith('jar') or item['role'].endswith('mod') or item['role'].endswith('component'):
                assert path.parts[0] in {'libraries', 'mods'} and len(path.parts) == 2 and path.suffix == '.jar'
            else:
                assert path.parts[0] == 'assets'
        root.mkdir()
        for entry in entries:
            if entry.filename == 'manifest.json':
                continue
            data = archive.read(entry)
            assert hashlib.sha256(data).hexdigest() == listed[entry.filename]['sha256']
            destination = root / entry.filename
            assert destination.is_relative_to(root)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
    for name in ('game', 'home', 'tmp', 'profile', 'evidence'):
        (root / name).mkdir()
    with zipfile.ZipFile(arguments.qa) as source, zipfile.ZipFile(root / 'mods/ArcadiumReleaseQa.jar', 'w', zipfile.ZIP_DEFLATED) as target:
        mixins = ['MetalClientComposition072SurfaceMixin', 'MetalClientComposition072GameProbe', 'MetalClientComposition072ComposeProbe']
        assert all('io/github/arcadiumclient/runtime/qa/mixin/' + name + '.class' in source.namelist() for name in mixins)
        for item in source.infolist():
            data = source.read(item)
            if item.filename == 'arcadium-release-qa.mixins.json':
                config = json.loads(data)
                config['client'] = mixins
                config['mixins'] = []
                data = json.dumps(config).encode()
            target.writestr(item, data)
    marker = {'schema': 'arcadium.metal-client072.fixture.v1', 'syntheticOffline': True, 'root': str(root),
              'evidence': str(root / 'evidence'), 'controlGl': arguments.control_gl,
              'baseInputSha256': arguments.inputs_sha256, 'qaSourceSha256': arguments.qa_sha256,
              'featureSha256': manifest['sourceRuntimeSha256'], 'capCorrectnessOnly': 60,
              'defaultsProofSha256': arguments.defaults_sha256,
              'runnerSha256': digest(Path(__file__)), 'sourceCommit': arguments.source_commit}
    marker.update(expectedBooleanDefaults=boolean_defaults, storedOffReloadProof=arguments.stored_off_reload)
    if not arguments.control_gl:
        marker.update(nativeLibrary=str(arguments.native_library.resolve()), nativeSha256=arguments.native_sha256,
                      referenceShader=str(arguments.shader.resolve()), referenceShaderSha256=arguments.shader_sha256)
    write(root / 'owned-fixture.json', marker)
    write(root / 'input-manifest.json', manifest)
    (root / 'game/options.txt').write_text('lang:en_US\npauseOnLostFocus:false\nrenderDistance:8\nguiScale:2\nmaxFps:60\nenableVsync:true\nshowInventoryAchievementHint:false\n')
    java = arguments.java.resolve()
    properties = {'fabric.gameVersion': '1.8.9', 'fabric.fixPackageAccess': 'true', 'fabric.modsFolder': str(root / 'mods'),
                  'user.home': str(root / 'home'), 'java.io.tmpdir': str(root / 'tmp'), 'java.awt.headless': 'true',
                  'arcadium.launcherDataDir': str(root / 'profile'), 'arcadium.launcherExecutable': '',
                  'arcadium.usageEndpoint': '', 'arcadium.presenceEndpoint': '',
                  'arcadium.metalClient072.fixture': str(root / 'owned-fixture.json'),
                  'http.proxyHost': '127.0.0.1', 'http.proxyPort': '9', 'https.proxyHost': '127.0.0.1', 'https.proxyPort': '9',
                  'socksProxyHost': '127.0.0.1', 'socksProxyPort': '9'}
    command = [str(java), '-Xms512M', '-Xmx3G', '--enable-native-access=ALL-UNNAMED',
               '--add-opens=java.base/java.lang=ALL-UNNAMED', '--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED']
    if sys.platform == 'darwin':
        command.append('-XstartOnFirstThread')
    command += ['-D' + key + '=' + value for key, value in properties.items()]
    classpath = [root / name for name in manifest['classpath']]
    assert len(classpath) == 44 and all(x.is_file() for x in classpath)
    command += ['-cp', os.pathsep.join(map(str, classpath)), manifest['entrypoint'], '--username', 'ArcadiumTest',
                '--accessToken', '0', '--uuid', uuid.uuid3(uuid.NAMESPACE_OID, 'ArcadiumMetalClient072').hex,
                '--gameDir', str(root / 'game'), '--assetsDir', str(root / 'assets'), '--assetIndex', '1.8',
                '--version', 'Arcadium-MetalClient072', '--versionType', 'custom', '--userType', 'legacy',
                '--userProperties', '{}', '--width', '1280', '--height', '720']
    write(root / 'evidence/command-fake-only.json', command)
    write(root / 'evidence/prepared.json', {**marker, 'launched': False, 'networkBlocked': True, 'noRealAccount': True})
    return root, command


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--inputs', required=True, type=Path)
    parser.add_argument('--inputs-sha256', required=True)
    parser.add_argument('--qa', required=True, type=Path)
    parser.add_argument('--qa-sha256', required=True)
    parser.add_argument('--defaults-proof', required=True, type=Path)
    parser.add_argument('--defaults-sha256', required=True)
    parser.add_argument('--source-commit')
    parser.add_argument('--fixture', required=True, type=Path)
    parser.add_argument('--java', required=True, type=Path)
    parser.add_argument('--native-library', type=Path)
    parser.add_argument('--native-sha256')
    parser.add_argument('--shader', type=Path)
    parser.add_argument('--shader-sha256')
    parser.add_argument('--control-gl', action='store_true')
    parser.add_argument('--stored-off-reload', action='store_true')
    parser.add_argument('--launch', action='store_true')
    arguments = parser.parse_args()
    assert arguments.source_commit is None or re.fullmatch('[0-9a-f]{40}', arguments.source_commit)
    root, command = prepare(arguments)
    print(json.dumps({'fixture': str(root), 'launched': False}), flush=True)
    if not arguments.launch:
        return
    started = time.monotonic()
    natural = False
    forced = False
    with (root / 'evidence/console.log').open('wb') as log:
        kwargs = {'creationflags': subprocess.CREATE_NO_WINDOW} if os.name == 'nt' else {}
        child = subprocess.Popen(command, cwd=root / 'game', env=child_environment(root / 'game'),
                                 stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, **kwargs)
        marker = json.loads((root / 'owned-fixture.json').read_text())
        marker['pid'] = child.pid
        write(root / 'owned-fixture.json', marker)
        print(json.dumps({'pid': child.pid, 'fakeClient': True}), flush=True)
        try:
            code = child.wait(timeout=240)
            natural = True
        except subprocess.TimeoutExpired:
            forced = True
            child.kill()
            code = child.wait(timeout=10)
    result_path = root / 'evidence/result.json'
    result = json.loads(result_path.read_text()) if result_path.is_file() else {}
    report = {'passed': natural and code == 0 and result.get('passed', False), 'naturalExit': natural,
              'exitCode': code, 'seconds': time.monotonic() - started, 'pid': child.pid, 'fixture': str(root),
              'fakeClient': True, 'performanceClaim': False, 'result': result,
              'forcedTermination': forced, 'terminationVerified': child.poll() is not None,
              'runnerSha256': digest(Path(__file__)), 'sourceCommit': arguments.source_commit}
    write(root / 'evidence/run.json', report)
    print(json.dumps({key: report[key] for key in ('passed', 'naturalExit', 'exitCode', 'seconds')}), flush=True)
    raise SystemExit(0 if report['passed'] else 1)


if __name__ == '__main__':
    main()
