"""Crea un backup .rar del proyecto donde se copie este script.

Pensado para copiarse en la raiz de CUALQUIER proyecto (Android, Flutter,
Node, Python, lo que sea), no depende de un .gitignore -muchos proyectos
no tienen-, asi que decide que excluir solo por el nombre de las carpetas:
las que empiezan por punto (.gradle, .idea, .dart_tool, .git, etc.) y unas
pocas mas conocidas por ser regenerables (build, node_modules...). Todo lo
demas se incluye, carpetas y archivos sueltos de la raiz igual que el
resto - incluido este mismo script, para no perderlo.

Busca .backup o .backups junto a este script y usa la que ya exista; si
no hay ninguna, crea .backups. Ahi deja el .rar, con el mismo formato de
nombre AAAAMMDD_N control.rar (el numero continua la
secuencia del dia si ya hay backups).

Requiere WinRAR instalado (usa su version de consola, Rar.exe).
"""

import datetime
import os
import re
import subprocess
import tkinter as tk
from tkinter import messagebox

PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))

RAR_EXE_CANDIDATES = [
    r"C:\Program Files\WinRAR\Rar.exe",
    r"C:\Program Files (x86)\WinRAR\Rar.exe",
]

BACKUP_DIR_NAMES = (".backup", ".backups", "_backup", "_backups")
DEFAULT_BACKUP_DIR_NAME = ".backups"

# Carpetas que se consideran siempre regenerables/temporales, ademas de
# cualquier carpeta que empiece por punto. Ajusta esta lista si algun
# proyecto concreto necesita algo distinto.
EXCLUDED_DIR_NAMES = {
    "build",
    "bin",
    "obj",
    "dist",
    "out",
    "target",
    "node_modules",
    "__pycache__",
    "venv",
    ".venv",
    "pods",
}


def _find_rar_exe():
    for path in RAR_EXE_CANDIDATES:
        if os.path.isfile(path):
            return path
    return None


def _find_backup_dir():
    """Usa .backup o .backups si ya existe alguna; si no hay ninguna,
    se creara .backups."""
    for name in BACKUP_DIR_NAMES:
        candidate = os.path.join(PROJECT_DIR, name)
        if os.path.isdir(candidate):
            return candidate
    return os.path.join(PROJECT_DIR, DEFAULT_BACKUP_DIR_NAME)


def _is_excluded_dir(name):
    return name.startswith(".") or name.lower() in EXCLUDED_DIR_NAMES


def _collect_files(backup_dir):
    """Recorre PROJECT_DIR y devuelve, en rutas relativas, todos los
    archivos salvo los que cuelguen de una carpeta excluida o de la
    propia carpeta de backups."""
    files = []

    for root, dirs, filenames in os.walk(PROJECT_DIR):
        rel_root = os.path.relpath(root, PROJECT_DIR)
        top_level = rel_root.split(os.sep)[0] if rel_root != "." else None

        if top_level is not None and top_level.lower() in BACKUP_DIR_NAMES:
            dirs[:] = []
            continue

        dirs[:] = [d for d in dirs if not _is_excluded_dir(d)]

        for filename in filenames:
            rel_path = os.path.normpath(os.path.join(rel_root, filename))
            files.append(rel_path)

    return files


def _next_backup_name(backup_dir):
    today = datetime.date.today().strftime("%Y%m%d")
    os.makedirs(backup_dir, exist_ok=True)
    pattern = re.compile(rf"^{today}_(\d+)(?: .*)?\.rar$", re.IGNORECASE)
    max_n = 0
    for name in os.listdir(backup_dir):
        match = pattern.match(name)
        if match:
            max_n = max(max_n, int(match.group(1)))
    return f"{today}_{max_n + 1} control.rar"


def _run_visible(cmd):
    """Lanza un comando en su propia consola (con "start") y espera a que
    termine. La "" que sigue a start es el hueco del titulo."""
    result = subprocess.run(f'start "" /wait {cmd}', cwd=PROJECT_DIR, shell=True)
    if result.returncode != 0:
        raise RuntimeError(f"El comando termino con codigo {result.returncode}.")


def crear_backup():
    rar_exe = _find_rar_exe()
    if rar_exe is None:
        raise RuntimeError(
            "No se encontro Rar.exe de WinRAR en las rutas habituales "
            f"({', '.join(RAR_EXE_CANDIDATES)}). Instala WinRAR o edita "
            "RAR_EXE_CANDIDATES en este script."
        )

    backup_dir = _find_backup_dir()
    files = _collect_files(backup_dir)
    if not files:
        raise RuntimeError("No se encontro ningun archivo para respaldar.")

    os.makedirs(backup_dir, exist_ok=True)
    backup_name = _next_backup_name(backup_dir)
    backup_path = os.path.join(backup_dir, backup_name)

    listfile_path = os.path.join(backup_dir, "_backup_filelist.tmp.txt")
    with open(listfile_path, "w", encoding="utf-8") as f:
        f.write("\n".join(files))

    try:
        _run_visible(f'"{rar_exe}" a "{backup_path}" @"{listfile_path}"')
    finally:
        if os.path.exists(listfile_path):
            os.remove(listfile_path)

    return backup_path


def main():
    root = tk.Tk()
    root.withdraw()

    try:
        crear_backup()
    except Exception as e:
        messagebox.showerror("Error al crear el backup", str(e))


if __name__ == "__main__":
    main()
