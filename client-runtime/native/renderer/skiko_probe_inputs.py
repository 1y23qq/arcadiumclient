#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import time
import tomllib
import zipfile

FILES={"skiko-awt-0.154.0.jar","skiko-awt-runtime-macos-arm64-0.154.0.jar","skiko-awt-runtime-macos-x64-0.154.0.jar",
    "jbr-api-1.9.0.jar",
    "atomicfu-jvm-0.33.0.jar","kotlin-stdlib-2.4.21.jar","kotlin-stdlib-jdk7-2.4.21.jar","kotlin-stdlib-jdk8-2.4.21.jar",
    "kotlinx-coroutines-core-jvm-1.11.0.jar","kotlinx-coroutines-jdk8-1.11.0.jar","Poppins-Regular.ttf","Poppins-OFL.txt"}

def validate(path,expected,root):
    if not re.fullmatch('[a-f0-9]{64}',expected) or not path.is_file() or path.stat().st_size>32*1024*1024:
        raise RuntimeError('Skiko probe input checksum or size is invalid')
    with path.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
    if digest!=expected:raise RuntimeError('Skiko input archive checksum mismatch')
    with zipfile.ZipFile(path) as z:
        infos=z.infolist()
        if len(infos)!=len(FILES)+1 or {i.filename for i in infos}!=FILES|{'manifest.json'} or sum(i.file_size for i in infos)>32*1024*1024:
            raise RuntimeError('Skiko input archive has an unexpected file inventory')
        if any(i.is_dir() or stat.S_ISLNK(i.external_attr>>16) for i in infos):raise RuntimeError('Unexpected Skiko archive object')
        manifest=json.loads(z.read('manifest.json'))
        version=tomllib.loads((root/'client-ui/gradle/libs.versions.toml').read_text())['versions']['skiko']
        lock=json.loads((root/'distribution/oneclient-1.8.9.lock.json').read_text(encoding='utf-8-sig'))
        renderer=next(c for c in lock['arcadiumComponents'] if c['file']=='ArcadiumRenderer-1.8.9.jar')
        if (manifest.get('schema')!='arcadium.p5-skiko-public-inputs.v1' or manifest.get('skikoVersion')!=version or version!='0.154.0'
            or manifest.get('rendererSha256')!=renderer['sha256'] or manifest.get('gameSession') is not False or manifest.get('profilesConfigsLogsAuthIncluded') is not False):
            raise RuntimeError('Skiko input source identity or privacy inventory differs')
        pins=manifest.get('files',[])
        if len(pins)!=len(FILES) or {p['path'] for p in pins}!=FILES:raise RuntimeError('Invalid Skiko manifest paths')
        for item in pins:
            data=z.read(item['path'])
            if len(data)!=item['bytes'] or hashlib.sha256(data).hexdigest()!=item['sha256']:raise RuntimeError('Skiko payload checksum mismatch')
        for name,source in [('Poppins-Regular.ttf','Poppins-Regular.ttf'),('Poppins-OFL.txt','OFL.txt')]:
            if z.read(name)!=(root/'client-ui/modules/ui/src/main/resources/assets/oneconfig/fonts/Poppins'/source).read_bytes():
                raise RuntimeError('The probe font differs from the current UI asset')
    return {'sha256':digest,'bytes':path.stat().st_size,'manifest':manifest,'filesVerified':len(pins)}

def unpack(path,expected,root,destination):
    proof=validate(path,expected,root)
    if destination.is_symlink():raise RuntimeError('Skiko staging cannot follow a directory link')
    destination.mkdir(parents=True,exist_ok=True)
    if {p.name for p in destination.iterdir()}-FILES-{'manifest.json'} or any(p.is_symlink() for p in destination.iterdir()):raise RuntimeError('Unowned Skiko staging input exists')
    with zipfile.ZipFile(path) as z:
        payload={name:z.read(name) for name in FILES|{'manifest.json'}}
        for name,data in payload.items():
            target=destination/name
            if target.exists() and target.read_bytes()!=data:raise RuntimeError('Existing Skiko staging file differs')
        for name,data in payload.items():
            target=destination/name
            if not target.exists():target.write_bytes(data)
    return proof

def run_owned(command,fixture,log_path,receipt):
    if fixture.exists():raise RuntimeError('The native Skiko fixture must be fresh')
    fixture.mkdir(parents=True)
    for name in ['home','tmp']: (fixture/name).mkdir()
    allowed={'PATH','JAVA_HOME','LANG','LC_ALL','LC_CTYPE','__CF_USER_TEXT_ENCODING','SYSTEMROOT','WINDIR'}
    environment={k:v for k,v in os.environ.items() if k.upper() in allowed}
    started=time.monotonic();natural=False;forced=False;child=None;code=None;failure=None
    intent={'ownedNativeSkikoJvm':True,'pid':None,'gameSession':False,'networkProxyBlocked':True,'maximumSeconds':60}
    try:
        with log_path.open('wb') as log:
            child=subprocess.Popen(command,cwd=fixture,env=environment,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
            intent['pid']=child.pid
            (fixture/'owned-process.json').write_text(json.dumps(intent,indent=2)+'\n')
            try:code=child.wait(timeout=60);natural=True
            except subprocess.TimeoutExpired:
                if child.poll() is None:child.kill();forced=True
                code=child.wait(timeout=10)
    except BaseException as error:
        failure=error
        if child is not None and child.poll() is None:
            try:child.kill();forced=True;code=child.wait(timeout=10)
            except Exception as cleanup:intent['cleanupFailure']=str(cleanup)
    finally:
        terminated=child is not None and child.poll() is not None
        actual_code=code if code is not None else child.returncode if child is not None else None
        result={**intent,'exitCode':actual_code,'processCreated':child is not None,'naturalExit':natural,'forcedTermination':forced,
            'terminationVerified':terminated,'passed':natural and code==0 and not forced and terminated and failure is None,
            'elapsedSeconds':time.monotonic()-started,'performanceClaim':False}
        if failure is not None:result.update(failureClass=type(failure).__name__,failure=str(failure))
        receipt.write_text(json.dumps(result,indent=2)+'\n')
    if child is not None and not terminated:raise RuntimeError('The owned Skiko JVM termination is unverified') from failure
    return result
