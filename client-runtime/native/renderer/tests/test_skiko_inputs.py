# SPDX-License-Identifier: GPL-3.0-only
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from skiko_probe_inputs import FILES,validate,unpack,run_owned

class SkikoInputTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        versions=self.root/'client-ui/gradle/libs.versions.toml';versions.parent.mkdir(parents=True);versions.write_text('[versions]\nskiko="0.154.0"\n')
        lock=self.root/'distribution/oneclient-1.8.9.lock.json';lock.parent.mkdir();lock.write_text(json.dumps({'arcadiumComponents':[{'file':'ArcadiumRenderer-1.8.9.jar','sha256':'a'*64}]}))
        font=self.root/'client-ui/modules/ui/src/main/resources/assets/oneconfig/fonts/Poppins';font.mkdir(parents=True)
        (font/'Poppins-Regular.ttf').write_bytes(b'font');(font/'OFL.txt').write_bytes(b'license')
        self.data={name:name.encode() for name in FILES};self.data['Poppins-Regular.ttf']=b'font';self.data['Poppins-OFL.txt']=b'license'
    def tearDown(self):self.temp.cleanup()
    def archive(self,change=None,extra=None):
        manifest={'schema':'arcadium.p5-skiko-public-inputs.v1','skikoVersion':'0.154.0','rendererSha256':'a'*64,'gameSession':False,'profilesConfigsLogsAuthIncluded':False,
            'files':[{'path':n,'bytes':len(d),'sha256':hashlib.sha256(d).hexdigest()} for n,d in self.data.items()]}
        if change:change(manifest)
        path=self.root/'inputs.zip'
        with zipfile.ZipFile(path,'w') as z:
            for n,d in self.data.items():z.writestr(n,d)
            z.writestr('manifest.json',json.dumps(manifest))
            if extra:z.writestr(*extra)
        return path,hashlib.sha256(path.read_bytes()).hexdigest()
    def reject(self,change=None,extra=None):
        path,sha=self.archive(change,extra);out=self.root/'out'
        with self.assertRaises(RuntimeError):unpack(path,sha,self.root,out)
        self.assertFalse(out.exists())
    def test_valid_inventory_and_exact_staging(self):
        path,sha=self.archive();out=self.root/'out';self.assertEqual(12,validate(path,sha,self.root)['filesVerified']);unpack(path,sha,self.root,out)
        self.assertEqual(FILES|{'manifest.json'},{p.name for p in out.iterdir()})
    def test_wrong_archive_checksum_has_no_side_effect(self):
        path,sha=self.archive();out=self.root/'out'
        with self.assertRaises(RuntimeError):unpack(path,'0'*64,self.root,out)
        self.assertFalse(out.exists())
    def test_unlisted_file_rejected_before_staging(self):self.reject(extra=('unowned.txt',b'unowned'))
    def test_wrong_payload_hash_rejected_before_staging(self):self.reject(lambda m:m['files'][0].update(sha256='0'*64))
    def test_different_source_renderer_rejected(self):self.reject(lambda m:m.update(rendererSha256='b'*64))
    def test_privacy_inventory_must_remain_false(self):self.reject(lambda m:m.update(profilesConfigsLogsAuthIncluded=True))
    def test_font_is_bound_to_source_bytes(self):
        self.data['Poppins-Regular.ttf']=b'different-font';self.reject()
    def test_existing_different_stage_is_never_overwritten(self):
        path,sha=self.archive();out=self.root/'out';out.mkdir();(out/'Poppins-Regular.ttf').write_bytes(b'existing')
        with self.assertRaises(RuntimeError):unpack(path,sha,self.root,out)
        self.assertEqual(b'existing',(out/'Poppins-Regular.ttf').read_bytes())
        self.assertEqual({'Poppins-Regular.ttf'},{p.name for p in out.iterdir()})
    def test_launch_precondition_failure_has_an_authoritative_no_process_receipt(self):
        receipt=self.root/'process.json'
        result=run_owned([str(self.root/'absent-owned-java')],self.root/'fixture',self.root/'console.log',receipt)
        self.assertFalse(result['passed']);self.assertFalse(result['processCreated']);self.assertFalse(result['naturalExit']);self.assertFalse(result['terminationVerified'])
        self.assertEqual(result,json.loads(receipt.read_text()))
    def test_existing_native_fixture_is_never_relaunched(self):
        fixture=self.root/'fixture';fixture.mkdir()
        with self.assertRaises(RuntimeError):run_owned([str(self.root/'absent-owned-java')],fixture,self.root/'console.log',self.root/'process.json')
        self.assertEqual([],list(fixture.iterdir()))

if __name__=='__main__':unittest.main()
