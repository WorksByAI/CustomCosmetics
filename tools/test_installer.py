"""Linux installer integration test. Real Node/SQLite/UDP, temporary paths, simulated systemd only.

Run as root on Linux: python3 tools/test_installer.py --node /path/to/node24
Never installs a host service, creates users, or changes firewall rules.
"""
# SPDX-License-Identifier: AGPL-3.0-or-later
import argparse
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import sqlite3
import subprocess
import tempfile


def free_port(kind):
    with socket.socket(socket.AF_INET, kind) as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--node", required=True)
    args = parser.parse_args()
    assert os.name == "posix" and os.geteuid() == 0, "Use an isolated/root Linux test runner"
    root = Path(__file__).resolve().parents[1]
    node = Path(args.node).resolve()
    with tempfile.TemporaryDirectory(prefix="customcosmetics-install-test-") as temporary:
        stage = Path(temporary)
        package = stage / "package with spaces"
        shutil.copytree(root / "server", package, ignore=shutil.ignore_patterns("data", "logs", ".runtime", "node_modules"))
        for name in ["LICENSE", "NOTICE", "LICENSES"]:
            source = root / name
            if source.is_dir():
                shutil.copytree(source, package / name, dirs_exist_ok=True)
            else:
                shutil.copy2(source, package / name)
        runtime = package / ".runtime/node/bin"
        runtime.mkdir(parents=True)
        shutil.copy2(node, runtime / "node")
        os.chmod(runtime / "node", 0o755)
        installed = stage / "installed"
        units = stage / "units"
        units.mkdir()
        deploy = (package / "deploy.sh").read_text()
        # Redirect only the two fixed installation paths in a test copy; production remains unchanged.
        deploy = deploy.replace("/opt/customcosmetics", str(installed)).replace("/etc/systemd/system", str(units))
        deploy = deploy.replace("[[ -d /run/systemd/system ]]", "true")
        (package / "deploy.sh").write_text(deploy)
        tcp, udp = free_port(socket.SOCK_STREAM), free_port(socket.SOCK_DGRAM)
        config = {"port": tcp, "udpPort": udp, "testMarker": "preserve-config"}
        (package / "server.json").write_text(json.dumps(config))
        (package / "source.zip").write_bytes(b"PK-test-source-fixture")
        (package / "data").mkdir()
        with sqlite3.connect(package / "data/cosmetics.sqlite") as db:
            db.execute("CREATE TABLE installer_fixture(value TEXT)")
            db.execute("INSERT INTO installer_fixture VALUES ('preserve-data')")
        mocks = stage / "bin"
        mocks.mkdir()
        supervisor = '''#!/usr/bin/env python3
import json, os, signal, subprocess, sys
from pathlib import Path
stage = Path(os.environ['INSTALL_TEST_STAGE'])
pidfile = stage / 'pid'
with (stage/'operations').open('a') as out: out.write(' '.join(sys.argv[1:])+'\\n')
if sys.argv[1] == 'enable':
    install = stage/'installed'
    env = dict(os.environ, HOST='127.0.0.1', DATABASE=str(install/'data/cosmetics.sqlite'), LOG_DIR=str(install/'logs'), OPENSSL_CONF=str(install/'data/openssl.cnf'))
    with (stage/'service.log').open('ab') as output:
        proc = subprocess.Popen([str(install/'.runtime/node/bin/node'), str(install/'src/main.js')], cwd=install, env=env, stdout=output, stderr=output)
    pidfile.write_text(str(proc.pid))
elif sys.argv[1] == 'is-active':
    if not pidfile.exists(): sys.exit(3)
    try: os.kill(int(pidfile.read_text()), 0)
    except ProcessLookupError: sys.exit(3)
elif sys.argv[1] == 'stop':
    if pidfile.exists():
        try: os.kill(int(pidfile.read_text()), signal.SIGTERM)
        except ProcessLookupError: pass
        pidfile.unlink()
'''
        (mocks / "systemctl").write_text(supervisor)
        # Map only the test service account to the current uid; no host account is created.
        (mocks / "id").write_text("#!/bin/sh\nif [ \"$1\" = '-gn' ]; then exec /usr/bin/id -gn; fi\nexec /usr/bin/id\n")
        (mocks / "chown").write_text("#!/bin/sh\nexit 0\n")
        (mocks / "useradd").write_text("#!/bin/sh\necho 'Unexpected host account mutation' >&2\nexit 99\n")
        for file in mocks.iterdir():
            os.chmod(file, 0o755)
        env = dict(os.environ, PATH=str(mocks) + ":" + os.environ["PATH"], INSTALL_TEST_STAGE=str(stage))
        env.pop("PORT", None)
        env.pop("UDP_PORT", None)
        try:
            for script in package.glob("*.sh"):
                subprocess.run(["bash", "-n", str(script)], check=True)
            result = subprocess.run(["bash", str(package / "install.sh")], env=env, text=True, capture_output=True, timeout=40)
            assert result.returncode == 0, result.stdout + result.stderr
            assert "configured UDP checks passed" in result.stdout
            assert json.loads((installed / "server.json").read_text()) == config
            assert (installed / "source.zip").read_bytes() == b"PK-test-source-fixture"
            assert (installed / "source.zip").stat().st_mode & 0o777 == 0o644
            assert (installed / "admin/app.js").is_file()
            assert (installed / "vendor/ws/LICENSE").is_file()
            assert (installed / "src/websocket.js").is_file()
            credentials=installed / "data/admin-first-login.txt"
            assert credentials.is_file() and credentials.stat().st_mode & 0o777 == 0o600
            assert (installed / "data/openssl.cnf").stat().st_mode & 0o777 == 0o600
            with sqlite3.connect(installed / "data/cosmetics.sqlite") as db:
                assert db.execute("SELECT value FROM installer_fixture").fetchone()[0] == "preserve-data"
                assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
            unit = (units / "customcosmetics.service").read_text()
            assert "ProtectSystem=strict" in unit and "NoNewPrivileges=true" in unit
            assert f"OPENSSL_CONF={installed}/data/openssl.cnf" in unit
            assert "enable --now customcosmetics.service" in (stage / "operations").read_text()
            print("INSTALLER_INTEGRATION_OK: upload path with spaces, SQLite import, preserved config, HTTP, WebSocket and UDP, private admin credentials, boot-service definition")
        finally:
            pidfile = stage / "pid"
            if pidfile.exists():
                try:
                    os.kill(int(pidfile.read_text()), signal.SIGTERM)
                except ProcessLookupError:
                    pass


if __name__ == "__main__":
    main()
