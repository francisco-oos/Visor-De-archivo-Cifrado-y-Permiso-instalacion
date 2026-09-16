import base64
import hashlib
import json
import os
import secrets
import shutil
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, simpledialog, ttk

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except Exception:
    AESGCM = None

from app_constants import CATALOG_AREAS, DISPLAY_AREAS, SOURCE_MANUALS_DIR, MANUALS_ASSET_DIR

MAGIC = b"VSDOC2"
LEGACY_KEY_FILE = "CLAVE_DOCUMENTOS_NO_ENVIAR.txt"
SECRETS_FILE = "visor-secrets.properties"


def project_root() -> Path:
    return Path(__file__).resolve().parent.parent


def source_root() -> Path:
    return Path(__file__).resolve().parent / SOURCE_MANUALS_DIR


def assets_manuals_dir() -> Path:
    return project_root() / "app" / "src" / "main" / "assets" / MANUALS_ASSET_DIR


def secrets_properties_path() -> Path:
    return project_root() / SECRETS_FILE


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def write_properties(path: Path, values: dict[str, str]):
    preferred_order = [
        "DOCUMENT_KEY_B64",
        "DOCUMENT_KEY_SHA256",
        "LICENSE_VERIFY_PUBLIC_KEY_B64",
        "TELEGRAM_DIRECT_ENABLED",
        "TELEGRAM_BOT_TOKEN",
        "TELEGRAM_ADMIN_CHAT_ID",
        "LEGACY_LICENSE_HMAC_SECRET",
    ]
    keys = [k for k in preferred_order if k in values]
    keys.extend(sorted(k for k in values if k not in keys))
    body = [
        "# Archivo local. NO subir a Git.",
        "# Generado/actualizado por tools/document_encryptor.py.",
        "",
    ]
    body.extend(f"{key}={values[key]}" for key in keys)
    body.append("")
    path.write_text("\n".join(body), encoding="utf-8")


def save_document_key(key: bytes):
    if len(key) != 32:
        raise ValueError("La clave de documentos debe tener 32 bytes (AES-256).")
    props_path = secrets_properties_path()
    props = read_properties(props_path)
    props["DOCUMENT_KEY_B64"] = base64.b64encode(key).decode("ascii")
    props["DOCUMENT_KEY_SHA256"] = hashlib.sha256(key).hexdigest()
    props.setdefault("LICENSE_VERIFY_PUBLIC_KEY_B64", "")
    props.setdefault("TELEGRAM_DIRECT_ENABLED", "false")
    props.setdefault("TELEGRAM_BOT_TOKEN", "")
    props.setdefault("TELEGRAM_ADMIN_CHAT_ID", "")
    props.setdefault("LEGACY_LICENSE_HMAC_SECRET", "")
    write_properties(props_path, props)


def load_or_create_key() -> bytes:
    # Fuente actual: visor-secrets.properties (fuera de Git).
    props = read_properties(secrets_properties_path())
    encoded = props.get("DOCUMENT_KEY_B64", "").strip()
    if encoded:
        key = base64.b64decode(encoded)
        if len(key) != 32:
            raise ValueError("DOCUMENT_KEY_B64 inválida: debe decodificar a 32 bytes.")
        save_document_key(key)
        return key

    # Migración transparente desde el archivo secreto usado por versiones anteriores.
    legacy_path = Path(__file__).resolve().parent / LEGACY_KEY_FILE
    if legacy_path.exists():
        key = base64.b64decode(legacy_path.read_text(encoding="utf-8").strip())
        if len(key) != 32:
            raise ValueError(f"{LEGACY_KEY_FILE} contiene una clave inválida.")
        save_document_key(key)
        return key

    key = secrets.token_bytes(32)
    save_document_key(key)
    return key


def safe_filename(value: str) -> str:
    clean = "".join(c if c.isalnum() else "_" for c in value).strip("_")
    return clean[:50] or "documento"


def ensure_folders():
    src = source_root()
    src.mkdir(parents=True, exist_ok=True)
    for area in CATALOG_AREAS:
        folder = src / area
        folder.mkdir(parents=True, exist_ok=True)
        marker = folder / "PEGA_AQUI_LOS_PDF_DE_ESTA_AREA.txt"
        if not marker.exists():
            marker.write_text(
                f"Coloca aquí los PDFs del departamento {DISPLAY_AREAS.get(area, area)}.\n"
                "Después abre el encriptador y presiona Cifrar y colocar en APK.\n",
                encoding="utf-8",
            )


def encrypt_pdf(pdf: Path, area_id: str, key: bytes, index: int):
    data = pdf.read_bytes()
    if not data.startswith(b"%PDF"):
        raise ValueError(f"No parece PDF válido: {pdf.name}")

    title = pdf.stem.replace("_", " ").strip()
    digest = hashlib.sha256(data).hexdigest()
    out_name = f"doc_{index:04d}_{safe_filename(pdf.stem)}_{digest[:8]}.bin"

    metadata = {
        "schema": "VSDOC2",
        "title": title,
        "file": out_name,
        "area_id": area_id,
        "category": DISPLAY_AREAS.get(area_id, area_id),
        "sha256": digest,
    }
    meta_bytes = json.dumps(metadata, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    nonce = secrets.token_bytes(12)
    encrypted = AESGCM(key).encrypt(nonce, data, meta_bytes)

    raw = MAGIC + len(meta_bytes).to_bytes(4, "big") + meta_bytes + nonce + encrypted

    item = {
        "title": title,
        "file": out_name,
        "category": DISPLAY_AREAS.get(area_id, area_id),
        "area_id": area_id,
        "keywords": f"{title} {DISPLAY_AREAS.get(area_id, area_id)} {area_id}",
        "sha256": digest,
        "format": "VSDOC2",
    }
    return out_name, raw, item


class DocumentEncryptor(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Visor Seguro - Encriptador de Manuales")
        self.geometry("1180x720")
        self.configure(bg="#071A2F")
        ensure_folders()
        self.selected_area = tk.StringVar(value=CATALOG_AREAS[0])
        self._build_ui()
        self.refresh()

    def _build_ui(self):
        style = ttk.Style()
        style.theme_use("clam")
        style.configure("Treeview", rowheight=28)
        style.configure("TButton", padding=6)

        header = tk.Frame(self, bg="#071A2F")
        header.pack(fill="x", padx=16, pady=12)
        tk.Label(header, text="Encriptador de manuales", fg="#FFFFFF", bg="#071A2F",
                 font=("Segoe UI", 20, "bold")).pack(anchor="w")
        tk.Label(header, text="Flujo: crea carpetas del catálogo → pega PDFs por departamento → cifra → recompila la APK.",
                 fg="#B9C7D8", bg="#071A2F", font=("Segoe UI", 10)).pack(anchor="w")

        top = tk.Frame(self, bg="#071A2F")
        top.pack(fill="x", padx=16)

        tk.Label(top, text="Departamento:", fg="#FFFFFF", bg="#071A2F").pack(side="left")
        area_combo = ttk.Combobox(top, values=CATALOG_AREAS, textvariable=self.selected_area, state="readonly", width=22)
        area_combo.pack(side="left", padx=8)
        ttk.Button(top, text="Crear/validar carpetas", command=self.create_folders).pack(side="left", padx=4)
        ttk.Button(top, text="Abrir MANUALES_PARA_ENCRIPTAR", command=lambda: os.startfile(source_root())).pack(side="left", padx=4)
        ttk.Button(top, text="Agregar PDF(s)", command=self.add_pdfs).pack(side="left", padx=4)
        ttk.Button(top, text="Renombrar", command=self.rename_pdf).pack(side="left", padx=4)
        ttk.Button(top, text="Mover área", command=self.move_pdf).pack(side="left", padx=4)
        ttk.Button(top, text="Eliminar", command=self.delete_pdf).pack(side="left", padx=4)
        ttk.Button(top, text="Actualizar lista", command=self.refresh).pack(side="left", padx=4)

        main = tk.Frame(self, bg="#071A2F")
        main.pack(fill="both", expand=True, padx=16, pady=12)

        self.tree = ttk.Treeview(main, columns=("area", "name", "size", "path"), show="headings")
        self.tree.heading("area", text="Departamento")
        self.tree.heading("name", text="Archivo PDF")
        self.tree.heading("size", text="Tamaño")
        self.tree.heading("path", text="Ruta")
        self.tree.column("area", width=150)
        self.tree.column("name", width=320)
        self.tree.column("size", width=100)
        self.tree.column("path", width=560)
        self.tree.pack(fill="both", expand=True)

        bottom = tk.Frame(self, bg="#071A2F")
        bottom.pack(fill="x", padx=16, pady=8)
        ttk.Button(bottom, text="Cifrar todo y colocar en APK", command=self.encrypt_all).pack(side="left", padx=4)
        ttk.Button(bottom, text="Abrir assets/manuales", command=lambda: os.startfile(assets_manuals_dir())).pack(side="left", padx=4)
        ttk.Button(bottom, text="Limpiar assets cifrados", command=self.clean_assets).pack(side="left", padx=4)

        self.status = tk.Label(self, text="", fg="#E8EEF7", bg="#071A2F", anchor="w")
        self.status.pack(fill="x", padx=16, pady=(0, 10))

    def create_folders(self):
        ensure_folders()
        self.refresh()
        messagebox.showinfo("Listo", "Carpetas estándar creadas/validadas.")

    def refresh(self):
        self.tree.delete(*self.tree.get_children())
        ensure_folders()
        total = 0
        for area in CATALOG_AREAS:
            folder = source_root() / area
            for pdf in sorted(folder.glob("*.pdf")):
                total += 1
                size = f"{pdf.stat().st_size / (1024*1024):.2f} MB"
                self.tree.insert("", "end", values=(area, pdf.name, size, str(pdf)))
        by_area = []
        for area in CATALOG_AREAS:
            count = len(list((source_root() / area).glob("*.pdf")))
            if count:
                by_area.append(f"{DISPLAY_AREAS.get(area, area)}: {count}")
        extra = " | ".join(by_area) if by_area else "sin PDFs todavía"
        self.status.config(text=f"{total} PDF(s) encontrados · {extra}")

    def selected_path(self) -> Path | None:
        item = self.tree.focus()
        if not item:
            messagebox.showwarning("Selecciona archivo", "Selecciona un PDF de la lista.")
            return None
        return Path(self.tree.item(item, "values")[3])

    def add_pdfs(self):
        files = filedialog.askopenfilenames(title="Seleccionar PDF(s)", filetypes=[("PDF", "*.pdf")])
        if not files:
            return
        area = self.selected_area.get()
        dest = source_root() / area
        dest.mkdir(parents=True, exist_ok=True)
        for f in files:
            src = Path(f)
            shutil.copy2(src, dest / src.name)
        self.refresh()

    def rename_pdf(self):
        path = self.selected_path()
        if not path:
            return
        new_name = simpledialog.askstring("Renombrar", "Nuevo nombre sin extensión:", initialvalue=path.stem)
        if not new_name:
            return
        new_path = path.with_name(safe_filename(new_name) + ".pdf")
        path.rename(new_path)
        self.refresh()

    def move_pdf(self):
        path = self.selected_path()
        if not path:
            return
        win = tk.Toplevel(self)
        win.title("Mover a departamento")
        win.geometry("340x120")
        var = tk.StringVar(value=self.selected_area.get())
        ttk.Combobox(win, values=CATALOG_AREAS, textvariable=var, state="readonly").pack(padx=20, pady=15, fill="x")

        def ok():
            dest = source_root() / var.get()
            dest.mkdir(parents=True, exist_ok=True)
            shutil.move(str(path), str(dest / path.name))
            win.destroy()
            self.refresh()

        ttk.Button(win, text="Mover", command=ok).pack(pady=8)

    def delete_pdf(self):
        path = self.selected_path()
        if not path:
            return
        if messagebox.askyesno("Eliminar", f"¿Eliminar {path.name}?"):
            path.unlink(missing_ok=True)
            self.refresh()

    def clean_assets(self):
        out = assets_manuals_dir()
        if messagebox.askyesno("Limpiar assets", "¿Eliminar .bin e index.json de assets/manuales?"):
            out.mkdir(parents=True, exist_ok=True)
            for p in out.glob("*.bin"):
                p.unlink()
            idx = out / "index.json"
            if idx.exists():
                idx.unlink()
            self.status.config(text="Assets cifrados limpiados.")

    def encrypt_all(self):
        if AESGCM is None:
            messagebox.showerror("Falta dependencia", "Instala: pip install cryptography")
            return
        try:
            key = load_or_create_key()
        except Exception as exc:
            messagebox.showerror("Clave de documentos", str(exc))
            return

        out = assets_manuals_dir()
        out.mkdir(parents=True, exist_ok=True)
        for p in out.glob("*.bin"):
            p.unlink()

        manifest = []
        counter = 1
        errors = []
        counts_by_area = {}
        for area in CATALOG_AREAS:
            for pdf in sorted((source_root() / area).glob("*.pdf")):
                try:
                    name, raw, item = encrypt_pdf(pdf, area, key, counter)
                    (out / name).write_bytes(raw)
                    manifest.append(item)
                    counts_by_area[area] = counts_by_area.get(area, 0) + 1
                    counter += 1
                except Exception as e:
                    errors.append(f"{pdf}: {e}")

        (out / "index.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

        msg = (
            f"Documentos cifrados: {len(manifest)}\n"
            f"Salida: {out}\n"
            f"Clave local guardada en: {secrets_properties_path()}\n\n"
            "Ahora sincroniza/compila la APK. No subas visor-secrets.properties a Git."
        )
        if errors:
            msg += "\n\nErrores:\n" + "\n".join(errors[:8])
        self.refresh()
        messagebox.showinfo("Cifrado terminado", msg)


if __name__ == "__main__":
    DocumentEncryptor().mainloop()
