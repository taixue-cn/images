"""Rebuild reviewed Java overlays against a checksum-locked Paper SDK."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent


def build():
    config = json.loads((ROOT / 'build-config.json').read_text())
    lock = json.loads((ROOT / 'sdk-lock.json').read_text())
    sdk = Path(os.environ.get('PLUGIN_BUILD_SDK', ROOT / '.build-sdk'))
    if not sdk.exists():
        archive = ROOT / '.build-sdk.zip'
        subprocess.run(['gh', 'release', 'download', lock['tag'], '--repo', lock['repository'],
                        '--pattern', lock['asset'], '--output', str(archive), '--clobber'], check=True)
        assert hashlib.sha256(archive.read_bytes()).hexdigest() == lock['sha256'], 'SDK checksum mismatch'
        sdk.mkdir()
        with zipfile.ZipFile(archive) as source:
            source.extractall(sdk)
    manifest = json.loads((sdk / 'manifest.json').read_text())
    for item in manifest:
        assert hashlib.sha256((sdk / item['file']).read_bytes()).hexdigest() == item['sha256'], item['file']
    original = ROOT / 'vendor/original.jar'
    assert not original.exists() or hashlib.sha256(original.read_bytes()).hexdigest() == config['original_sha256']
    classes = ROOT / 'build/classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    sources = [ROOT / p for p in config['compile']]
    assert sources, 'No adapted source selected'
    assert all(p.is_file() for p in sources)
    java = Path(os.environ['JAVA_HOME']) / 'bin' if 'JAVA_HOME' in os.environ else Path('/usr/bin')
    classpath = [sdk / item['file'] for item in manifest] + ([original] if original.exists() else [])
    extra = ROOT / 'vendor/dependencies'
    if extra.exists():
        classpath += list(extra.glob('*.jar'))
    processing = ['-proc:none']
    if config.get('lombok'):
        processors = [sdk / item['file'] for item in manifest if '-lombok-' in item['file']]
        assert len(processors) == 1, 'Exactly one locked Lombok processor required'
        processing = ['-processorpath', str(processors[0]), '-processor', 'lombok.launch.AnnotationProcessorHider$AnnotationProcessor']
    args = ['--release', '25', '-encoding', 'UTF-8', *processing, '-cp',
            os.pathsep.join(str(p) for p in classpath), '-d', str(classes), *map(str, sources)]
    argfile = ROOT / 'build/javac.args'
    argfile.write_text('\n'.join('"' + arg.replace('\\', '\\\\').replace('"', '\\"') + '"' for arg in args))
    subprocess.run([str(java / 'javac'), '@' + str(argfile)], check=True)
    updates = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    for resource in config.get('resources', []):
        folder = ROOT / resource
        for p in folder.rglob('*'):
            if p.is_file():
                updates[p.relative_to(folder).as_posix()] = p.read_bytes()
    assert updates, 'Empty overlay'
    dist = ROOT / 'dist'
    dist.mkdir(exist_ok=True)
    output = dist / config['artifact']
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as target:
        if original.exists():
            with zipfile.ZipFile(original) as source:
                for item in source.infolist():
                    if item.filename not in updates and not item.filename.upper().endswith(('.SF', '.RSA', '.DSA')):
                        target.writestr(item, source.read(item.filename))
        for name, data in sorted(updates.items()):
            entry = zipfile.ZipInfo(name, (2026, 10, 26, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            target.writestr(entry, data)
    with zipfile.ZipFile(output) as jar:
        assert jar.testzip() is None
        assert 'plugin.yml' in jar.namelist() or 'paper-plugin.yml' in jar.namelist()
        assert all(jar.read(name) == data for name, data in updates.items())
    (dist / 'SHA256SUMS').write_text(hashlib.sha256(output.read_bytes()).hexdigest() + '  ' + output.name + '\n')
    print(f'Built {output.name}: {len(sources)} source files, {len(updates)} class/resource entries')


if __name__ == '__main__':
    build()
