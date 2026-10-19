from __future__ import annotations

import argparse
import contextlib
import ctypes
import hashlib
import http.client
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import URLError
from urllib.request import ProxyHandler, Request, build_opener
import zipfile


ROOT = Path(__file__).resolve().parent.parent
COUNTER = ROOT / "services/arcadium-presence"
PROBE_CLASS = "ArcadiumOnlineUsageProbe"
OPENER = build_opener(ProxyHandler({}))


def progress(stage: str, **values: object) -> None:
    print(json.dumps({"stage": stage, **values}), flush=True)


def digest(file: Path) -> str:
    with file.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def free_port() -> int:
    with socket.socket() as bound:
        bound.bind(("127.0.0.1", 0))
        return bound.getsockname()[1]


def wait_for(condition, timeout: float, label: str):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        result = condition()
        if result:
            return result
        time.sleep(0.2)
    raise AssertionError("Timed out: " + label)


def get_json(url: str) -> dict | None:
    try:
        with OPENER.open(url, timeout=3) as result:
            return json.loads(result.read(4096))
    except (OSError, ValueError, URLError):
        return None


def write_settings(profile: Path, enabled: bool) -> None:
    target = profile / "settings.json"
    settings = json.loads(target.read_text("utf-8")) if target.exists() else {
        "settings_version": 1,
        "accepted_tos_version": 0,
        "accepted_privacy_version": 0,
        "declined_tos": False,
        "seen_onboarding": True,
        "auto_update": False,
        "discord_enabled": False,
        "run_in_background": False,
        "show_tray_icon": False,
        "dynamic_background_enabled": False,
    }
    settings["online_count_enabled"] = enabled
    temporary = profile / ("settings-" + uuid.uuid4().hex + ".tmp")
    temporary.write_text(json.dumps(settings, indent=2) + "\n", "utf-8")
    temporary.replace(target)


def windows_pipe_present() -> bool:
    if os.name != "nt":
        return False
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.WaitNamedPipeW.argtypes = [ctypes.c_wchar_p, ctypes.c_uint32]
    kernel.WaitNamedPipeW.restype = ctypes.c_int
    available = kernel.WaitNamedPipeW(r"\\.\pipe\io.github.arcadiumclient.launcher.ipc", 1)
    return bool(available) or ctypes.get_last_error() not in (2, 3, 53)


def windows_close(pid: int) -> bool:
    from ctypes import wintypes

    user32 = ctypes.WinDLL("user32", use_last_error=True)
    callback_type = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    user32.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
    user32.GetWindowTextW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
    user32.PostMessageW.argtypes = [wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM]
    closed = False

    @callback_type
    def visit(window, _):
        nonlocal closed
        owner = wintypes.DWORD()
        user32.GetWindowThreadProcessId(window, ctypes.byref(owner))
        title = ctypes.create_unicode_buffer(256)
        user32.GetWindowTextW(window, title, len(title))
        if owner.value == pid and title.value == "Arcadium Client":
            user32.PostMessageW(window, 0x0010, 0, 0)
            closed = True
        return True

    user32.EnumWindows(visit, 0)
    return closed


class OwnedProcess:
    def __init__(self, arguments: list[str], log: Path, *, environment: dict[str, str] | None = None, cwd: Path = ROOT):
        self.log = log.open("wb")
        options: dict = {"stdin": subprocess.PIPE, "stdout": self.log, "stderr": subprocess.STDOUT, "cwd": cwd, "env": environment}
        if os.name == "nt":
            startup = subprocess.STARTUPINFO()
            startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            startup.wShowWindow = subprocess.SW_HIDE
            options.update(startupinfo=startup, creationflags=subprocess.CREATE_NO_WINDOW | subprocess.CREATE_NEW_PROCESS_GROUP)
        else:
            options["start_new_session"] = True
        self.process = subprocess.Popen(arguments, **options)

    def crash(self) -> None:
        if self.process.poll() is None:
            self.process.kill()
            self.process.wait(timeout=10)

    def stop(self) -> None:
        if self.process.poll() is None:
            if os.name == "nt":
                subprocess.run(["taskkill", "/PID", str(self.process.pid), "/T", "/F"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
            else:
                with contextlib.suppress(ProcessLookupError):
                    os.killpg(self.process.pid, signal.SIGTERM)
            with contextlib.suppress(subprocess.TimeoutExpired):
                self.process.wait(timeout=10)
        self.log.close()


class ProtocolRegistration:
    def __init__(self, executable: Path):
        self.executable = str(executable.resolve()).casefold()
        self.values: list[tuple[str, str, object | None]] = []
        self.original_keys: set[str] = set()
        if os.name != "nt":
            return
        import winreg
        for key in [r"Software\Classes\arcadium", r"Software\Classes\arcadium\DefaultIcon", r"Software\Classes\arcadium\shell",
                    r"Software\Classes\arcadium\shell\open", r"Software\Classes\arcadium\shell\open\command"]:
            with contextlib.suppress(FileNotFoundError):
                with winreg.OpenKey(winreg.HKEY_CURRENT_USER, key):
                    self.original_keys.add(key)
        for key, name in [(r"Software\Classes\arcadium", ""), (r"Software\Classes\arcadium", "URL Protocol"),
                          (r"Software\Classes\arcadium\DefaultIcon", ""), (r"Software\Classes\arcadium\shell\open\command", "")]:
            try:
                with winreg.OpenKey(winreg.HKEY_CURRENT_USER, key) as handle:
                    saved = winreg.QueryValueEx(handle, name)
            except FileNotFoundError:
                saved = None
            self.values.append((key, name, saved))

    def restore(self) -> None:
        if os.name != "nt":
            return
        import winreg
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Classes\arcadium\shell\open\command") as handle:
                current, _ = winreg.QueryValueEx(handle, "")
            if self.executable not in current.casefold():
                return
        except FileNotFoundError:
            return
        for key, name, saved in reversed(self.values):
            with winreg.CreateKey(winreg.HKEY_CURRENT_USER, key) as handle:
                if saved is None:
                    with contextlib.suppress(FileNotFoundError):
                        winreg.DeleteValue(handle, name)
                else:
                    winreg.SetValueEx(handle, name, 0, saved[1], saved[0])
        for key in [r"Software\Classes\arcadium\shell\open\command", r"Software\Classes\arcadium\shell\open", r"Software\Classes\arcadium\shell",
                    r"Software\Classes\arcadium\DefaultIcon", r"Software\Classes\arcadium"]:
            if key not in self.original_keys:
                with contextlib.suppress(OSError):
                    winreg.DeleteKey(winreg.HKEY_CURRENT_USER, key)


class RecordingProxy:
    def __init__(self, upstream_port: int, profile: Path):
        self.events: list[dict] = []
        self.failures: list[str] = []
        self.profile = profile
        self.lock = threading.Lock()
        owner = self

        class Handler(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"

            def log_message(self, *_):
                pass

            def do_POST(self):
                self.forward()

            def do_DELETE(self):
                self.forward()

            def do_GET(self):
                self.forward()

            def forward(self):
                if self.path not in ("/health", "/v1/usage/count", "/v1/usage/heartbeat"):
                    self.send_error(404)
                    return
                length = int(self.headers.get("Content-Length", "0"))
                if length > 512:
                    self.send_error(400)
                    return
                body = self.rfile.read(length)
                headers = {name: self.headers[name] for name in ("Authorization", "Content-Type") if name in self.headers}
                packet = None
                if self.path == "/v1/usage/heartbeat":
                    try:
                        identity = json.loads((owner.profile / "usage-identity.json").read_text("utf-8"))
                        packet = json.loads(body)
                        assert headers.get("Authorization") == "Bearer " + identity["secret"]
                        assert set(packet) == {"installationId", "sessionId", "client"}
                        assert packet["installationId"] == identity["installationId"]
                        assert packet["client"] in ("launcher", "game")
                        assert uuid.UUID(packet["sessionId"]).version == 4
                        assert "Origin" not in self.headers and "Cookie" not in self.headers
                    except Exception:
                        with owner.lock:
                            owner.failures.append("A client sent invalid credentials, identity or heartbeat fields")
                        self.send_error(400)
                        return
                try:
                    connection = http.client.HTTPConnection("127.0.0.1", upstream_port, timeout=10)
                    connection.request(self.command, self.path, body=body if length else None, headers=headers)
                    result = connection.getresponse()
                    output = result.read(4097)
                    assert len(output) <= 4096
                    status = result.status
                    connection.close()
                except Exception:
                    self.send_error(502)
                    return
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(output)))
                self.send_header("Connection", "close")
                self.end_headers()
                with contextlib.suppress(BrokenPipeError, ConnectionResetError):
                    self.wfile.write(output)
                if packet:
                    with owner.lock:
                        owner.events.append({"client": packet["client"], "method": self.command, "status": status,
                                             "sessionHash": hashlib.sha256(packet["sessionId"].encode()).hexdigest(),
                                             "elapsed": round(time.monotonic(), 3)})

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.origin = "http://127.0.0.1:" + str(self.server.server_port)

    def matching(self, client: str, method: str, *, after: float = 0) -> list[dict]:
        with self.lock:
            return [value.copy() for value in self.events if value["client"] == client and value["method"] == method
                    and value["status"] == 200 and value["elapsed"] >= after]

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)


def game_classpath(features: Path, kotlin: Path, fixture: Path) -> list[Path]:
    libraries = fixture / "libraries"
    libraries.mkdir()
    extracted: dict[str, Path] = {}

    def nested(archive: zipfile.ZipFile):
        for item in archive.infolist():
            if not item.filename.startswith("META-INF/jars/") or not item.filename.endswith(".jar"):
                continue
            assert item.file_size < 128 * 1024 * 1024
            data = archive.read(item)
            checksum = hashlib.sha256(data).hexdigest()
            if checksum in extracted:
                continue
            target = libraries / (checksum[:12] + "-" + Path(item.filename).name)
            target.write_bytes(data)
            extracted[checksum] = target
            with zipfile.ZipFile(target) as child:
                nested(child)

    with zipfile.ZipFile(features) as source:
        assert "io/github/arcadiumclient/runtime/client/presence/OnlineUsage.class" in source.namelist(), "Features JAR does not contain the shipping online counter"
        nested(source)
    with zipfile.ZipFile(kotlin) as source:
        nested(source)
    return [features, kotlin, *extracted.values()]


def loader_libraries(reference: Path) -> list[Path]:
    assert reference.stat().st_size < 2 * 1024 * 1024
    arguments = json.loads(reference.read_text("utf-8-sig"))
    classpath = arguments[arguments.index("-cp") + 1]
    selected = [Path(value) for value in classpath.split(";" if ";" in classpath else os.pathsep)
                if Path(value).name.startswith(("fabric-loader-", "asm-", "gson-", "sponge-mixin-"))]
    assert selected and all(file.is_file() for file in selected)
    assert sum(file.name.startswith("fabric-loader-") for file in selected) == 1
    return selected


def feature_snapshot(features: Path, fixture: Path) -> Path:
    target = fixture / "ArcadiumFeatures-tested.jar"
    deadline = time.monotonic() + 15
    while True:
        try:
            before = features.stat()
            shutil.copyfile(features, target)
            after = features.stat()
            if before.st_size == after.st_size and before.st_mtime_ns == after.st_mtime_ns and zipfile.is_zipfile(target):
                assert digest(target) == digest(features)
                return target
        except OSError:
            pass
        if time.monotonic() >= deadline:
            raise RuntimeError("The Features artifact is still being rebuilt; wait for a completed remap")
        time.sleep(0.5)


def prepare_driver(java: Path, features: Path, kotlin: Path, fixture: Path, reference: Path | None = None) -> list[Path]:
    source = fixture / (PROBE_CLASS + ".java")
    source.write_text("""
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
public final class ArcadiumOnlineUsageProbe {
    public static void main(String[] arguments) throws Exception {
        Class<?> launcher = Class.forName("net.fabricmc.loader.impl.launch.FabricLauncher");
        Object environment = Proxy.newProxyInstance(launcher.getClassLoader(), new Class<?>[] {launcher}, (proxy, method, values) -> {
            if (method.getName().equals("isDevelopment")) return false;
            if (method.getName().equals("getTargetClassLoader")) return ArcadiumOnlineUsageProbe.class.getClassLoader();
            throw new UnsupportedOperationException("Unexpected loader operation: " + method.getName());
        });
        Class.forName("net.fabricmc.loader.impl.launch.FabricLauncherBase").getMethod("setLauncher", launcher).invoke(null, environment);
        Class<?> usage = Class.forName("io.github.arcadiumclient.runtime.client.presence.OnlineUsage");
        usage.getMethod("initialize").invoke(usage.getField("INSTANCE").get(null));
        System.out.println("Shipping game counter initialized");
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String command = input.readLine();
            if (command == null || command.equals("quit")) break;
        }
        System.exit(0);
    }
}
""".strip() + "\n", "utf-8")
    classes = fixture / "classes"
    classes.mkdir()
    subprocess.run([str(java.with_name("javac.exe" if os.name == "nt" else "javac")), "-d", str(classes), str(source)], check=True, capture_output=True)
    tested_features = feature_snapshot(features, fixture)
    return [classes, *game_classpath(tested_features, kotlin, fixture), *loader_libraries(reference)]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--launcher", type=Path, default=Path(os.environ.get("CARGO_TARGET_DIR", ROOT / "target")) / "debug/Arcadium.exe")
    parser.add_argument("--features", type=Path, default=ROOT / "packages/arcadium_common/assets/distribution/ArcadiumFeatures-1.8.9.jar")
    parser.add_argument("--java", type=Path, default=Path(os.environ.get("JAVA_HOME", "")) / "bin" / ("java.exe" if os.name == "nt" else "java"))
    parser.add_argument("--kotlin", type=Path, required=True, help="Original Fabric language Kotlin JAR from the local game instance")
    parser.add_argument("--reference", type=Path, required=True, help="command.json captured from an isolated local game fixture")
    arguments = parser.parse_args()
    if os.name != "nt":
        parser.error("This native launcher integration requires Windows")
    assert arguments.launcher.is_file() and arguments.features.is_file() and arguments.java.is_file()
    if windows_pipe_present():
        raise RuntimeError("Another Arcadium launcher owns the Windows IPC pipe; close it before running this isolated test")
    kotlin = arguments.kotlin
    fixture = ROOT / "build/online-usage-integration" / (time.strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:8])
    fixture.mkdir(parents=True)
    profile = fixture / "launcher-profile"
    profile.mkdir()
    write_settings(profile, True)
    classpath = prepare_driver(arguments.java, arguments.features.resolve(), kotlin.resolve(), fixture, arguments.reference)
    port = free_port()
    node = shutil.which("node")
    assert node
    worker = OwnedProcess([node, str(COUNTER / "node_modules/wrangler/bin/wrangler.js"), "dev", "--local", "--ip", "127.0.0.1",
                           "--port", str(port), "--inspector-port", str(free_port()), "--persist-to", str(fixture / "worker-storage"), "--log-level", "error"],
                          fixture / "worker.log", cwd=COUNTER)
    proxy = None
    processes = [worker]
    protocol = ProtocolRegistration(arguments.launcher)
    checks: dict[str, object] = {}
    started = time.monotonic()
    try:
        wait_for(lambda: get_json(f"http://127.0.0.1:{port}/health"), 30, "Local workerd startup")
        proxy = RecordingProxy(port, profile)
        environment = os.environ.copy()
        environment.update(ARCADIUM_DATA_DIR=str(profile), ARCADIUM_USAGE_ENDPOINT=proxy.origin, ARCADIUM_DISABLE_AUTOUPDATE="1",
                           HTTP_PROXY="http://127.0.0.1:9", HTTPS_PROXY="http://127.0.0.1:9", ALL_PROXY="http://127.0.0.1:9", NO_PROXY="127.0.0.1,localhost,::1")

        def start_launcher(label: str) -> OwnedProcess:
            instance = OwnedProcess([str(arguments.launcher.resolve())], fixture / (label + ".log"), environment=environment)
            processes.append(instance)
            return instance

        def require_online(expected: int, seconds: float = 22, *, fresher_than: int | None = None):
            result = wait_for(lambda: (value if (value := get_json(proxy.origin + "/v1/usage/count")) and value.get("online") == expected
                                      and (fresher_than is None or value.get("updatedAt", 0) > fresher_than) else None),
                              seconds, f"Online count {expected}")
            assert set(result) == {"online", "updatedAt"}
            return result

        def close_launcher(instance: OwnedProcess):
            if os.name == "nt":
                assert windows_close(instance.process.pid), "No owned launcher window was found"
            else:
                instance.process.send_signal(signal.SIGTERM)
            instance.process.wait(timeout=12)
            assert instance.process.returncode == 0

        progress("launcher-start", evidence=str(fixture))
        launcher = start_launcher("launcher-enabled")
        wait_for(lambda: proxy.matching("launcher", "POST"), 40, "Native launcher heartbeat")
        launcher_snapshot = require_online(1)
        identity = json.loads((profile / "usage-identity.json").read_text("utf-8"))
        assert identity["version"] == 1 and uuid.UUID(identity["installationId"]).version == 4 and len(identity["secret"]) == 64
        checks["nativeLauncherCounted"] = True

        game = OwnedProcess([str(arguments.java.resolve()), "-Darcadium.launcherDataDir=" + str(profile), "-Darcadium.usageEndpoint=" + proxy.origin,
                             "-cp", os.pathsep.join(str(file) for file in classpath), PROBE_CLASS], fixture / "game-counter.log")
        processes.append(game)
        wait_for(lambda: proxy.matching("game", "POST"), 20, "Shipping JVM counter heartbeat")
        require_online(1, fresher_than=launcher_snapshot["updatedAt"])
        assert proxy.matching("launcher", "POST")[0]["sessionHash"] != proxy.matching("game", "POST")[0]["sessionHash"]
        checks["nativeAndGameDeduplicated"] = True
        progress("both-clients-count-once")

        wait_for(lambda: proxy.matching("game", "POST", after=time.monotonic() - 1), 35, "A fresh game lease before launcher shutdown")
        before_close = require_online(1)
        closing = time.monotonic()
        close_launcher(launcher)
        wait_for(lambda: proxy.matching("launcher", "DELETE", after=closing), 5, "Launcher releases its own session")
        require_online(1, fresher_than=before_close["updatedAt"])
        wait_for(lambda: proxy.matching("game", "POST", after=closing), 35, "Game heartbeat after launcher exit")
        require_online(1)
        checks["gameContinuesAfterLauncherCloses"] = True
        progress("launcher-closed-game-still-online")

        disabled_at = time.monotonic()
        write_settings(profile, False)
        launcher_disabled = start_launcher("launcher-disabled")
        wait_for(lambda: proxy.matching("game", "DELETE", after=disabled_at), 35, "Running game observes the shared opt-out file")
        require_online(0)
        assert not proxy.matching("launcher", "POST", after=disabled_at)
        assert launcher_disabled.process.poll() is None and game.process.poll() is None
        assert json.loads((profile / "usage-identity.json").read_text("utf-8")) == identity
        checks["sharedOptOutExcludesBothClients"] = True
        close_launcher(launcher_disabled)
        progress("opted-out-both-clients-zero")

        enabled_at = time.monotonic()
        write_settings(profile, True)
        wait_for(lambda: proxy.matching("game", "POST", after=enabled_at), 35, "Running game observes counting enabled")
        require_online(1)
        checks["gameResumesWithoutLauncher"] = True
        crashed_at = time.monotonic()
        game.crash()
        assert not proxy.matching("game", "DELETE", after=crashed_at)
        progress("game-crashed-awaiting-lease-expiry", maximumSeconds=110)
        require_online(0, 112)
        assert time.monotonic() - crashed_at >= 80, "A crashed game disappeared before its 90-second lease should expire"
        assert not proxy.matching("game", "DELETE", after=crashed_at)
        checks["crashedClientExpiresWithoutShutdownRequest"] = True
        assert not proxy.failures
        checks["onlyExactAnonymousInstallationFieldsSent"] = True
        result = {"passed": True, "checks": checks, "seconds": round(time.monotonic() - started, 2),
                  "launcherSha256": digest(arguments.launcher), "featuresSha256": digest(fixture / "ArcadiumFeatures-tested.jar"),
                  "gameMode": "Shipping OnlineUsage class and bundled runtime dependencies, with a headless Fabric launcher environment and no Minecraft render process",
                  "localOnly": True, "events": proxy.events}
        (fixture / "verification.json").write_text(json.dumps(result, indent=2) + "\n", "utf-8")
        progress("passed", checks=len(checks), seconds=result["seconds"], evidence=str(fixture / "verification.json"))
    except BaseException as error:
        (fixture / "failure.json").write_text(json.dumps({"passed": False, "checks": checks, "error": type(error).__name__ + ": " + str(error)}, indent=2) + "\n", "utf-8")
        progress("failed", evidence=str(fixture / "failure.json"), error=type(error).__name__ + ": " + str(error))
        raise
    finally:
        for instance in reversed(processes):
            instance.stop()
        if proxy:
            proxy.close()
        protocol.restore()


if __name__ == "__main__":
    main()
