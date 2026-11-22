import importlib.util
import json
import os
import re
import shlex
import shutil
import sys
import traceback
import urllib.request
import zipfile

_PKG_NAME_RE = re.compile(r"[<>=!~;\[\s]")
_PREBUNDLED_MODULES = {"cryptography", "cffi", "pycparser"}


class AndroidStdoutRedirector:
    def __init__(self, kotlin_listener):
        self.listener = kotlin_listener
        self.buffer = ""

    def write(self, text):
        if not text:
            return
        self.buffer += text
        if "\n" in self.buffer:
            lines = self.buffer.split("\n")
            self.buffer = lines[-1]
            for line in lines[:-1]:
                self.listener.onStdout(line + "\n")

    def flush(self):
        if self.buffer:
            self.listener.onStdout(self.buffer)
            self.buffer = ""


class DummyStdin:
    def readline(self):
        return "\n"

    def read(self, *args, **kwargs):
        return ""


def _normalize_name(raw_name):
    return _PKG_NAME_RE.split(raw_name.strip())[0].strip().lower().replace("_", "-")


def _get_installed_db(packages_dir):
    db_path = os.path.join(packages_dir, "installed_packages.json")
    if os.path.exists(db_path):
        try:
            with open(db_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}


def _save_installed_db(packages_dir, db):
    os.makedirs(packages_dir, exist_ok=True)
    db_path = os.path.join(packages_dir, "installed_packages.json")
    tmp_path = db_path + ".tmp"
    try:
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump(db, f, ensure_ascii=False, indent=2)
        os.replace(tmp_path, db_path)
        return True
    except Exception:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)
        return False


def _is_already_available(clean_name, db):
    if clean_name in db or clean_name in _PREBUNDLED_MODULES:
        return True
    mod_name = clean_name.replace("-", "_")
    try:
        return importlib.util.find_spec(mod_name) is not None
    except Exception:
        return False


def safe_network_download_and_install(package_name, packages_dir, visited=None):
    if visited is None:
        visited = set()

    clean_name = _normalize_name(package_name)
    if not clean_name or clean_name in visited:
        return True
    visited.add(clean_name)

    db = _get_installed_db(packages_dir)
    if _is_already_available(clean_name, db):
        print(f"[pip] Requirement already satisfied: {clean_name}")
        return True

    print(f"[pip] Fetching metadata for '{clean_name}' from PyPI...")
    pypi_url = f"https://pypi.org/pypi/{clean_name}/json"

    try:
        req = urllib.request.Request(
            pypi_url,
            headers={"User-Agent": "YinTerm-Pip/1.0"}
        )
        with urllib.request.urlopen(req, timeout=12) as response:
            data = json.loads(response.read().decode("utf-8"))

        wheel_url = None
        filename = None
        for u in data.get("urls", []):
            fn = u.get("filename", "")
            if fn.endswith(".whl") and "none-any" in fn:
                wheel_url = u.get("url")
                filename = fn
                break

        if not wheel_url or not filename:
            print(f"[pip] Error: No Pure Python wheel (none-any.whl) found for '{clean_name}'.")
            return False

        print(f"[pip] Downloading {filename}...")
        os.makedirs(packages_dir, exist_ok=True)
        zip_file_path = os.path.join(packages_dir, filename)

        try:
            urllib.request.urlretrieve(wheel_url, zip_file_path)
            print(f"[pip] Installing '{clean_name}'...")

            top_level_items = set()
            abs_pkg_dir = os.path.abspath(packages_dir)

            with zipfile.ZipFile(zip_file_path, "r") as zip_ref:
                for member in zip_ref.namelist():
                    target_path = os.path.abspath(os.path.join(packages_dir, member))
                    if not target_path.startswith(abs_pkg_dir + os.sep):
                        raise ValueError(f"Unsafe path in wheel: {member}")
                    parts = member.split("/")
                    if parts[0]:
                        top_level_items.add(parts[0])
                zip_ref.extractall(packages_dir)
        finally:
            if os.path.exists(zip_file_path):
                os.remove(zip_file_path)

        db[clean_name] = sorted(top_level_items)
        _save_installed_db(packages_dir, db)
        print(f"[pip] Successfully installed '{clean_name}'!")

        requires_dist = data.get("info", {}).get("requires_dist") or []
        for req_entry in requires_dist:
            if ";" in req_entry:
                # Lewati optional extras & marker platform khusus yang tidak relevan
                marker = req_entry.split(";", 1)[1]
                if "extra ==" in marker or "extra==" in marker or "sys_platform" in marker:
                    continue
            dep_name = _normalize_name(req_entry)
            if dep_name:
                safe_network_download_and_install(dep_name, packages_dir, visited)

        return True
    except Exception as e:
        print(f"[pip] Failed for '{clean_name}': {e}")
        return False


def safe_uninstall(package_name, packages_dir):
    clean_name = _normalize_name(package_name)
    db = _get_installed_db(packages_dir)

    if clean_name not in db:
        print(f"[pip] Skipping '{clean_name}' (not installed).")
        return False

    print(f"[pip] Uninstalling '{clean_name}'...")
    for item in db[clean_name]:
        item_path = os.path.join(packages_dir, item)
        if os.path.exists(item_path):
            if os.path.isdir(item_path):
                shutil.rmtree(item_path, ignore_errors=True)
            else:
                os.remove(item_path)

    del db[clean_name]
    _save_installed_db(packages_dir, db)
    print(f"[pip] Successfully uninstalled '{clean_name}'!")
    return True


def safe_list(packages_dir):
    db = _get_installed_db(packages_dir)
    if not db:
        print("No user packages installed.")
        return True

    print("===== Installed Packages =====")
    for package in sorted(db.keys()):
        print(package)
    return True


def execute_smart_block(text_block, kotlin_listener, cache_dir_path):
    custom_packages_dir = os.path.join(cache_dir_path, "user_python_packages")

    if custom_packages_dir not in sys.path:
        sys.path.insert(0, custom_packages_dir)

    old_stdout = sys.stdout
    old_stderr = sys.stderr
    old_stdin = sys.stdin

    redirector = AndroidStdoutRedirector(kotlin_listener)
    sys.stdout = redirector
    sys.stderr = redirector
    sys.stdin = DummyStdin()

    globals_dict = {"__name__": "__main__"}
    lines = text_block.split("\n")
    current_python_chunk = []

    def run_pending_python():
        nonlocal current_python_chunk
        if current_python_chunk:
            source_code = "\n".join(current_python_chunk)
            current_python_chunk = []
            if source_code.strip():
                compiled = compile(source_code, "<yinterm>", "exec")
                exec(compiled, globals_dict)

    try:
        for line in lines:
            stripped = line.strip()
            is_top_level = bool(stripped) and not line[0].isspace()

            if is_top_level and stripped == "pip list":
                run_pending_python()
                safe_list(custom_packages_dir)

            elif is_top_level and stripped.startswith("pip install "):
                run_pending_python()
                for pkg in stripped[12:].split():
                    safe_network_download_and_install(pkg, custom_packages_dir)

            elif is_top_level and stripped.startswith("pip uninstall "):
                run_pending_python()
                for pkg in stripped[14:].split():
                    safe_uninstall(pkg, custom_packages_dir)

            elif is_top_level and stripped.startswith("python "):
                run_pending_python()
                try:
                    cmd_parts = shlex.split(line)
                except ValueError:
                    cmd_parts = line.split()

                if len(cmd_parts) > 1:
                    file_path = cmd_parts[1]
                    old_argv = sys.argv
                    sys.argv = cmd_parts[1:]
                    try:
                        with open(file_path, "r", encoding="utf-8") as f:
                            script_code = compile(f.read(), file_path, "exec")
                        exec(script_code, globals_dict)
                    finally:
                        sys.argv = old_argv
            else:
                current_python_chunk.append(line)

        run_pending_python()
    except Exception:
        traceback.print_exc(file=sys.stdout)
    finally:
        redirector.flush()
        sys.stdout = old_stdout
        sys.stderr = old_stderr
        sys.stdin = old_stdin