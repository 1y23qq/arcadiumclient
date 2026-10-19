import argparse
import hashlib
import http.server
import json
import os
from pathlib import Path
import platform
import queue
import re
import shutil
import subprocess
import tempfile
import threading
import time
import tomllib
from urllib.parse import parse_qs, urlencode, urlsplit
import uuid


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "packages/arcadium_app/src/cookie_consent.rs"
STATUSES = {"READY", "COMPLETE", "CANCELLED", "UNAVAILABLE", "FAILED"}
LOAD = "webview.load_url(url).map_err(|_| Status::Failed)?;"
POLL = "if self.status.get().is_none() && self.cancelled.load(Ordering::Acquire) {"


def replace_once(source, old, new):
    if source.count(old) != 1:
        raise AssertionError("Production consent fixture anchor changed")
    return source.replace(old, new)


def write_generated(path, content):
    if not path.is_file() or path.read_text(encoding="utf-8") != content:
        path.write_text(content, encoding="utf-8")


def diagnostic_fixture(source):
    for anchor, phase in [
        ("let Ok(event_loop) = new_event_loop()", "event_loop"),
        ("let window = event_loop", "window"),
        ("let webview = builder", "webview"),
    ]:
        source = replace_once(source, anchor, f'eprintln!("ARCADIUM_CONSENT_PHASE {phase}");\n        ' + anchor)
    source = replace_once(source, "for cookie in &request.cookies {",
                          'eprintln!("ARCADIUM_CONSENT_PHASE seed");\n            for (index, cookie) in request.cookies.iter().enumerate() {\n                eprintln!("ARCADIUM_CONSENT_SEED {index}");')
    anchor = "verify_cookies(&webview, &request.cookies).map_err(|_| Status::Failed)?;"
    source = replace_once(source, anchor, 'eprintln!("ARCADIUM_CONSENT_PHASE readback");\n            ' + anchor
                          + '\n            eprintln!("ARCADIUM_CONSENT_PHASE verified");')
    anchor = "if let Some(cookie) = request.cookies.get(self.mac_seeded) {"
    source = replace_once(source, anchor, anchor + '\n                eprintln!("ARCADIUM_CONSENT_PHASE seed");\n                eprintln!("ARCADIUM_CONSENT_SEED {}", self.mac_seeded);')
    anchor = "verify_cookies(webview, request.clone(), self.mac_proxy.clone());"
    source = replace_once(source, anchor, 'eprintln!("ARCADIUM_CONSENT_PHASE readback");\n                ' + anchor)
    start = source.index("    fn matches_mac_cookies(")
    before, mac = source[:start], source[start:]
    mac = replace_once(mac, "if actual.len() != expected.len() {",
                       'eprintln!("ARCADIUM_CONSENT_COUNT {} {}", expected.len(), actual.len());\n        if actual.len() != expected.len() {')
    mac = replace_once(mac, "for source in expected {", "for (index, source) in expected.iter().enumerate() {")
    mac = replace_once(mac, "            }) else {\n                return false;",
                       '            }) else {\n                eprintln!("ARCADIUM_CONSENT_MISMATCH {index} identity");\n                return false;')
    anchor = "            if cookie.isSecure() != source.secure"
    checks = '''            if cookie.isSecure() != source.secure { eprintln!("ARCADIUM_CONSENT_MISMATCH {index} secure"); }
            if cookie.isHTTPOnly() != source.http_only { eprintln!("ARCADIUM_CONSENT_MISMATCH {index} http_only"); }
            if cookie.isSessionOnly() != source.expires_unix.is_none() { eprintln!("ARCADIUM_CONSENT_MISMATCH {index} session"); }
            if source.expires_unix.is_some_and(|expires| cookie.expiresDate().is_none_or(|date| (date.timeIntervalSince1970() - expires as f64).abs() > 1.0)) {
                eprintln!("ARCADIUM_CONSENT_MISMATCH {index} expiry");
            }
'''
    mac = replace_once(mac, anchor, checks + anchor)
    mac = replace_once(mac, "                if !matches {\n                    return false;",
                       '                if !matches {\n                    eprintln!("ARCADIUM_CONSENT_MISMATCH {index} same_site");\n                    return false;')
    source = before + mac
    return source


def generate_probe(directory):
    production = SOURCE.read_text(encoding="utf-8")
    source = diagnostic_fixture(production)
    blank = replace_once(source, LOAD, "// Native fixture does not navigate to Microsoft.")
    blank = replace_once(blank, 'report("READY");', 'self.started = Instant::now();\n            report("READY");')
    blank = replace_once(blank, POLL, """if self.window.as_ref().is_some_and(|window| window.is_visible() == Some(true)) && self.started.elapsed() >= Duration::from_secs(2) {
                self.status.set(Some(Status::Complete));
            }
            """ + POLL)
    callback = replace_once(source, LOAD, """webview.load_url(&format!("{}?code=fixture&state={}", self.policy.redirect, self.policy.state))
                .map_err(|_| Status::Failed)?;""")
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "src").mkdir(exist_ok=True)
    for name, fixture in [("blank", blank), ("callback", callback)]:
        write_generated(directory / "src" / f"{name}.rs", fixture)
        main = f'''mod {name};
fn main() {{
    if std::env::args().nth(1).as_deref() == Some("--fixture-contract") {{
        println!("{{}}", serde_json::json!({{
            "clientId": arcadium_common::constants::MICROSOFT_CLIENT_ID,
            "scope": arcadium_common::constants::MINECRAFT_SCOPES
        }}));
        return;
    }}
    if {name}::requested() && {name}::run().is_err() {{ std::process::exit(1); }}
}}
'''
        write_generated(directory / "src" / f"{name}_main.rs", main)
    auth = json.dumps((ROOT / "packages/arcadium_auth").as_posix())
    common = json.dumps((ROOT / "packages/arcadium_common").as_posix())
    manifest = f'''[package]
name = "arcadium_native_consent_qa"
version = "0.0.0"
edition = "2024"

[workspace]

[dependencies]
arcadium_auth = {{ path = {auth} }}
arcadium_common = {{ path = {common} }}
anyhow = "=1.0.104"
serde_json = "=1.0.151"
url = "=2.5.8"
uuid = {{ version = "=1.26.1", features = ["v4"] }}

[target.'cfg(any(windows, target_os = "macos"))'.dependencies]
wry = {{ version = "=0.57.0", default-features = false, features = ["os-webview"] }}
winit = {{ version = "=0.30.13", default-features = false, features = ["rwh_06"] }}

[target.'cfg(windows)'.dependencies]
webview2-com = "=0.39.1"
windows = {{ version = "=0.62.2", features = ["Win32_Foundation", "Win32_System_Com"] }}

[target.'cfg(target_os = "macos")'.dependencies]
block2 = "=0.6.2"
objc2 = "=0.6.4"
objc2-foundation = {{ version = "=0.3.2", default-features = false, features = [
    "std", "NSHTTPCookie", "NSDictionary", "NSString", "NSDate", "NSRunLoop"
] }}

[[bin]]
name = "arcadium-consent-blank-qa"
path = "src/blank_main.rs"

[[bin]]
name = "arcadium-consent-callback-qa"
path = "src/callback_main.rs"
'''
    profiles = tomllib.loads((ROOT / "Cargo.toml").read_text(encoding="utf-8"))["profile"]
    for name in ["release", "macos-testing"]:
        manifest += f"\n[profile.{name}]\n"
        for key, value in profiles[name].items():
            manifest += f"{key} = {json.dumps(value)}\n"
    write_generated(directory / "Cargo.toml", manifest)
    lock = (ROOT / "Cargo.lock").read_bytes()
    digest = hashlib.sha256(lock).hexdigest()
    marker = directory / ".root-lock-sha256"
    if not marker.is_file() or marker.read_text(encoding="utf-8") != digest:
        (directory / "Cargo.lock").write_bytes(lock)
        marker.write_text(digest, encoding="utf-8")
    return hashlib.sha256(production.encode("utf-8")).hexdigest()


def build_probe(directory, target, target_dir, profile, fetch_dependencies=False):
    command = ["cargo", "build", "--manifest-path", str(directory / "Cargo.toml"),
               "--target", target, "--target-dir", str(target_dir), "--profile", profile,
               "--bin", "arcadium-consent-blank-qa", "--bin", "arcadium-consent-callback-qa"]
    if not fetch_dependencies:
        command.append("--offline")
    subprocess.run(command, cwd=ROOT, env=dict(os.environ, SQLX_OFFLINE="true"), check=True, timeout=1800)
    output = target_dir / target / ("debug" if profile == "dev" else profile)
    suffix = ".exe" if platform.system() == "Windows" else ""
    return output / f"arcadium-consent-blank-qa{suffix}", output / f"arcadium-consent-callback-qa{suffix}"


def cookie_request(contract, redirect):
    state = "Z" * 43
    legacy = contract["clientId"] == "42a60a84-599d-44b2-a7c6-b00cdef1d6a2"
    endpoint = "https://login.live.com/oauth20_authorize.srf" if legacy else "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"
    query = dict(client_id=contract["clientId"], response_type="code", response_mode="query",
                 scope=contract["scope"], state=state, redirect_uri=redirect,
                 code_challenge="Y" * 43, code_challenge_method="S256", prompt="consent")
    cookies = [
        dict(name="__Host-ArcFixture", value="", domain="login.live.com", path="/",
             includeSubdomains=False, secure=True, httpOnly=True, expiresUnix=None, sameSite="Lax"),
        dict(name="ArcFixtureDomain", value="fixture-domain-only", domain="live.com", path="/",
             includeSubdomains=True, secure=True, httpOnly=True, expiresUnix=int(time.time()) + 3600, sameSite="Strict"),
        dict(name="ArcFixtureNone", value="fixture-host-only", domain="login.live.com", path="/",
             includeSubdomains=False, secure=True, httpOnly=False, expiresUnix=None, sameSite="None"),
        dict(name="ArcFixtureNonSecure", value="fixture-non-secure", domain="login.live.com", path="/fixture",
             includeSubdomains=False, secure=False, httpOnly=False, expiresUnix=int(time.time()) + 3600, sameSite="Lax"),
    ]
    if platform.system() == "Windows":
        cookies.append(dict(name="ArcFixtureLongExpiry", value="fixture-long-expiry", domain="login.live.com", path="/long",
                            includeSubdomains=False, secure=True, httpOnly=True,
                            expiresUnix=int(time.time()) + 730 * 86400, sameSite="Lax"))
    return dict(authorizeUrl=endpoint + "?" + urlencode(query), redirectUri=redirect, state=state, cookies=cookies)


def owned_directory(prefix="arcadium-cookie-webview-"):
    directory = Path(tempfile.gettempdir()).resolve() / (prefix + str(uuid.uuid4()))
    directory.mkdir()
    if directory.resolve().parent != Path(tempfile.gettempdir()).resolve():
        raise AssertionError("Native fixture temp directory escaped its parent")
    return directory


def remove_owned(directory):
    parent = Path(tempfile.gettempdir()).resolve()
    prefix = next((value for value in ["arcadium-cookie-webview-", "arcadium-consent-invalid-"] if directory.name.startswith(value)), None)
    if prefix is None or directory.resolve() != directory or directory.parent != parent or directory.is_symlink() or directory.is_junction():
        raise AssertionError("Native fixture cleanup rejected its directory")
    uuid.UUID(directory.name[len(prefix):])
    for _ in range(50):
        try:
            shutil.rmtree(directory)
            return
        except FileNotFoundError:
            return
        except OSError:
            time.sleep(.1)
    raise AssertionError("Native fixture profile remained locked after child exit")


class Helper:
    def __init__(self, executable, request, directory, override):
        environment = dict(os.environ, ARCADIUM_COOKIE_WEBVIEW_DIR=str(directory), WEBVIEW2_USER_DATA_FOLDER=str(override))
        options = {"creationflags": subprocess.CREATE_NO_WINDOW} if platform.system() == "Windows" else {}
        self.process = subprocess.Popen([str(executable), "--cookie-consent"], cwd=ROOT, env=environment,
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **options)
        self.statuses = queue.Queue()
        self.seen = []
        self.diagnostics = bytearray()
        self.directory = directory
        threading.Thread(target=self.read_status, daemon=True).start()
        self.diagnostic_thread = threading.Thread(target=self.read_diagnostics, daemon=True)
        self.diagnostic_thread.start()
        self.process.stdin.write((json.dumps(request, separators=(",", ":")) + "\n").encode("utf-8"))
        self.process.stdin.flush()

    def read_status(self):
        for line in self.process.stdout:
            self.statuses.put(line.decode("ascii", errors="replace").strip())
        self.statuses.put(None)

    def read_diagnostics(self):
        while block := self.process.stderr.read1(4096):
            if len(self.diagnostics) < 8192:
                self.diagnostics.extend(block[:8192 - len(self.diagnostics)])

    def expect(self, status):
        actual = self.statuses.get(timeout=40)
        if actual not in STATUSES:
            raise AssertionError("Native consent lifecycle returned an invalid status: " + self.safe_diagnostics())
        self.seen.append(actual)
        if actual != status:
            raise AssertionError(f"Native consent lifecycle expected {status}, received {actual}: " + self.safe_diagnostics())

    def safe_diagnostics(self):
        try:
            self.process.wait(timeout=1)
        except subprocess.TimeoutExpired:
            pass
        self.diagnostic_thread.join(timeout=1)
        text = self.diagnostics.decode("utf-8", errors="replace")
        phases = re.findall(r"^ARCADIUM_CONSENT_PHASE (event_loop|window|webview|seed|readback|verified)$", text, re.MULTILINE)
        indices = [int(value) for value in re.findall(r"^ARCADIUM_CONSENT_SEED ([0-9]+)$", text, re.MULTILINE)]
        counts = [[int(a), int(b)] for a, b in re.findall(r"^ARCADIUM_CONSENT_COUNT ([0-9]+) ([0-9]+)$", text, re.MULTILINE)]
        mismatches = [[int(index), field] for index, field in re.findall(r"^ARCADIUM_CONSENT_MISMATCH ([0-9]+) (identity|secure|http_only|session|expiry|same_site)$", text, re.MULTILINE)]
        exceptions = [value for value in ["NSInvalidArgumentException", "NSInternalInconsistencyException", "NSRangeException"] if value in text]
        panic = "reentrant_event_handler" if "tried to handle event while another event is currently being handled" in text else "unclassified"
        return json.dumps(dict(exit=self.process.poll(), phases=phases, seedIndices=indices, counts=counts, mismatches=mismatches,
                               nativeExceptions=exceptions, panic=panic), separators=(",", ":"))

    def finish(self, exit_code=0):
        if self.process.wait(timeout=15) != exit_code:
            raise AssertionError("Native consent fixture process failed")
        self.process.stdin.close()
        if platform.system() == "Windows":
            if self.directory.exists():
                remove_owned(self.directory)
        else:
            if not self.directory.exists() or any(self.directory.iterdir()):
                raise AssertionError("WKWebView wrote persistent fixture profile data")
            self.directory.rmdir()

    def close(self):
        if not self.process.stdin.closed:
            self.process.stdin.close()
        if self.process.poll() is None:
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=10)
        if self.directory.exists():
            remove_owned(self.directory)


def verify_blank(executable, request, probe, cancel=False):
    directory = owned_directory()
    helper = Helper(executable, request, directory, probe / "must-not-create-shared-profile")
    try:
        helper.expect("READY")
        if cancel:
            helper.process.stdin.close()
        helper.expect("CANCELLED" if cancel else "COMPLETE")
        helper.finish()
        if (probe / "must-not-create-shared-profile").exists():
            raise AssertionError("Inherited WebView2 profile override was used")
        return dict(case="parent-eof-cancellation" if cancel else "native-cookie-privacy-readback", passed=True,
                    statuses=helper.seen, cookieAttributesVerifiedBeforeReady=True, processAndProfileCleaned=True)
    finally:
        helper.close()


def verify_unowned(executable, request, probe):
    directory = owned_directory("arcadium-consent-invalid-")
    helper = Helper(executable, request, directory, probe / "must-not-create-shared-profile")
    try:
        helper.expect("UNAVAILABLE")
        if helper.process.wait(timeout=15) != 1 or not directory.exists() or any(directory.iterdir()):
            raise AssertionError("Native consent helper modified an unowned profile")
        return dict(case="unowned-directory-rejected", passed=True, statuses=helper.seen, unownedDirectoryPreserved=True)
    finally:
        helper.close()


def verify_forced_kill(executable, request, probe):
    directory = owned_directory()
    helper = Helper(executable, request, directory, probe / "must-not-create-shared-profile")
    try:
        helper.expect("READY")
        helper.process.kill()
        code = helper.process.wait(timeout=10)
        if code == 0:
            raise AssertionError("Forced native fixture termination did not terminate its process")
        helper.finish(code)
        if directory.exists():
            raise AssertionError("Forced native fixture termination left its owned profile")
        return dict(case="forced-child-kill-cleanup", passed=True, statuses=helper.seen,
                    processAndProfileCleaned=True, parentCleanupAfterChildExit=True)
    finally:
        helper.close()


def verify_cleanup_boundary():
    directory = owned_directory("arcadium-consent-outside-")
    marker = directory / "preserved.txt"
    marker.write_text("fixture", encoding="utf-8")
    try:
        try:
            remove_owned(directory)
        except AssertionError:
            pass
        else:
            raise AssertionError("Native fixture cleanup accepted an unowned directory")
        if marker.read_text(encoding="utf-8") != "fixture":
            raise AssertionError("Native fixture cleanup modified an unowned directory")
        return dict(case="parent-cleanup-boundary", passed=True, unownedDirectoryPreserved=True)
    finally:
        marker.unlink()
        directory.rmdir()


def verify_callback(executable, contract, probe):
    received, respond = threading.Event(), threading.Event()
    calls = []

    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_GET(self):
            parameters = parse_qs(urlsplit(self.path).query)
            if parameters.get("state") != ["Z" * 43] or parameters.get("code") != ["fixture"]:
                self.send_error(400)
                return
            calls.append(True)
            received.set()
            if not respond.wait(timeout=15):
                self.send_error(408)
                return
            body = b"<html><body>Arcadium Client</body></html>"
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    legacy = contract["clientId"] == "42a60a84-599d-44b2-a7c6-b00cdef1d6a2"
    server = http.server.HTTPServer(("127.0.0.1", 25575 if legacy else 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    redirect = f"http://localhost:{server.server_address[1]}/" + ("callback" if legacy else "")
    directory = owned_directory()
    helper = Helper(executable, cookie_request(contract, redirect), directory, probe / "must-not-create-shared-profile")
    try:
        helper.expect("READY")
        if not received.wait(timeout=10):
            raise AssertionError("Native WebView did not request the verified loopback callback")
        time.sleep(.4)
        if not helper.statuses.empty() or helper.process.poll() is not None:
            raise AssertionError("Native consent completed before the loopback response")
        respond.set()
        helper.expect("COMPLETE")
        helper.finish()
        if len(calls) != 1:
            raise AssertionError("Native consent callback request count changed")
        return dict(case="native-loopback-response-finish", passed=True, statuses=helper.seen,
                    loopbackRequests=1, waitedForLoopbackResponse=True, processAndProfileCleaned=True)
    finally:
        respond.set()
        helper.close()
        server.shutdown()
        server.server_close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--target")
    parser.add_argument("--target-dir", type=Path)
    parser.add_argument("--profile")
    parser.add_argument("--fetch-dependencies", action="store_true")
    parser.add_argument("--output", type=Path, default=ROOT / "build/native-cookie-consent-tests.json")
    args = parser.parse_args()
    system = platform.system()
    if system not in {"Windows", "Darwin"}:
        raise SystemExit("Native cookie consent QA requires Windows or macOS")
    target = args.target or ("x86_64-pc-windows-msvc" if system == "Windows" else
                             "aarch64-apple-darwin" if platform.machine() == "arm64" else "x86_64-apple-darwin")
    target_dir = (args.target_dir or Path(os.environ.get("CARGO_TARGET_DIR", ROOT / "target"))).resolve()
    profile = args.profile or ("dev" if system == "Windows" else "macos-testing")
    probe = ROOT / "build/native-cookie-consent-qa"
    digest = generate_probe(probe)
    blank, callback = build_probe(probe, target, target_dir, profile, args.fetch_dependencies)
    contract = json.loads(subprocess.check_output([str(blank), "--fixture-contract"], cwd=ROOT, timeout=10))
    legacy = contract["clientId"] == "42a60a84-599d-44b2-a7c6-b00cdef1d6a2"
    request = cookie_request(contract, "http://localhost:25575/callback" if legacy else "http://localhost:41001/")
    cases = [verify_blank(blank, request, probe), verify_blank(blank, request, probe, cancel=True)]
    if system == "Windows":
        cases.append(verify_unowned(blank, request, probe))
    cases.append(verify_forced_kill(blank, request, probe))
    cases.append(verify_cleanup_boundary())
    cases.append(verify_callback(callback, contract, probe))
    result = dict(passed=True, platform=system, target=target, sourceSha256=digest, cases=cases,
                  accounts="synthetic cookies only", externalProviderNavigation=False)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, separators=(",", ":")), flush=True)


if __name__ == "__main__":
    main()
