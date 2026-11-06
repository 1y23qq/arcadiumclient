import argparse
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('metal_client', Path(__file__).with_name('run_metal_client_probe.py'))
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


class MetalClientInputTests(unittest.TestCase):
    def certificate(self, root, changes=None):
        booleans = {name: name != 'worldRenderScale' for name in probe.BOOLEAN_DEFAULT_KEYS}
        if changes:
            changes(booleans)
        path = root / 'defaults.json'
        path.write_text(json.dumps({'main': {'sha256': 'a' * 64}, 'guiBooleanDefaults': booleans}))
        return path, probe.digest(path)

    def test_accepts_exact_user_defaults_certificate(self):
        with tempfile.TemporaryDirectory() as temporary:
            path, digest = self.certificate(Path(temporary))
            self.assertEqual(probe.read_defaults(path, digest)['main']['sha256'], 'a' * 64)

    def test_rejects_missing_or_wrong_checksum(self):
        with tempfile.TemporaryDirectory() as temporary:
            path, _ = self.certificate(Path(temporary))
            for digest in (None, '', '0' * 63, '0' * 64):
                with self.subTest(digest=digest), self.assertRaises(AssertionError):
                    probe.read_defaults(path, digest)

    def test_rejects_wrong_property_and_nonboolean(self):
        changes = (
            lambda values: values.__setitem__('cachedFontMetrics', 1),
            lambda values: values.__setitem__('cachedFontMetrics', False),
            lambda values: values.__setitem__('worldRenderScale', True),
            lambda values: values.__setitem__('unrelated', values.pop('cachedFontMetrics')),
        )
        for change in changes:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as temporary:
                path, digest = self.certificate(Path(temporary), change)
                with self.assertRaises(AssertionError):
                    probe.read_defaults(path, digest)

    def test_rejects_oversized_certificate(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'defaults.json'
            path.write_bytes(b' ' * (64 * 1024 + 1))
            with self.assertRaises(AssertionError):
                probe.read_defaults(path, probe.digest(path))

    def test_certificate_failure_does_not_create_fixture(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            defaults, _ = self.certificate(root)
            inputs, qa = root / 'inputs.zip', root / 'qa.jar'
            inputs.write_bytes(b'not extracted')
            qa.write_bytes(b'not opened')
            fixture = root / 'ArcadiumMetalClient072-negative'
            arguments = argparse.Namespace(inputs=inputs, inputs_sha256=probe.digest(inputs),
                qa=qa, qa_sha256=probe.digest(qa), defaults_proof=defaults, defaults_sha256='0' * 64,
                fixture=fixture)
            with self.assertRaises(AssertionError):
                probe.prepare(arguments)
            self.assertFalse(fixture.exists())

    def test_child_environment_excludes_ci_credentials_and_java_injection(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {
            'GH_TOKEN': 'fixture-only-token', 'GITHUB_TOKEN': 'fixture-only-token',
            'ACTIONS_RUNTIME_TOKEN': 'fixture-only-token', 'JAVA_TOOL_OPTIONS': '-Dunexpected=true',
            'JDK_JAVA_OPTIONS': '-Dunexpected=true', '_JAVA_OPTIONS': '-Dunexpected=true',
        }):
            environment = probe.child_environment(Path(temporary))
            for name in ('GH_TOKEN', 'GITHUB_TOKEN', 'ACTIONS_RUNTIME_TOKEN', 'JAVA_TOOL_OPTIONS',
                         'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'):
                self.assertNotIn(name, environment)
            for name in ('HOME', 'USERPROFILE', 'APPDATA', 'LOCALAPPDATA', 'TMPDIR', 'TEMP', 'TMP'):
                self.assertTrue(Path(environment[name]).is_relative_to(Path(temporary)))


if __name__ == '__main__':
    unittest.main()
