from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKIP_DIRS = {
    ".git", ".gradle", ".idea", "build", "caches", "daemon", "native",
    "DocumentVault", "MANUALES_PARA_ENCRIPTAR", "MANUALES_CIFRADOS", "temp", "backup",
}
SKIP_FILES = {"visor-secrets.properties", "local.properties"}
TEXT_SUFFIXES = {
    ".kt", ".java", ".gradle", ".kts", ".py", ".md", ".xml", ".json", ".txt",
    ".properties", ".bat", ".sh", ".yml", ".yaml",
}

PATTERNS = [
    ("Telegram bot token", re.compile(r"\b\d{6,12}:[A-Za-z0-9_-]{30,}\b")),
    ("Private key PEM", re.compile(r"-----BEGIN (?:EC |RSA )?PRIVATE KEY-----")),
    ("OpenAI-style API key", re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b")),
    ("Hardcoded BOT_TOKEN", re.compile(r"BOT_TOKEN\s*=\s*[\"'][^\"']{8,}[\"']")),
    ("Hardcoded legacy HMAC secret", re.compile(r"APP_VERIFY_SECRET\s*=\s*(?:b)?[\"'][^\"']+[\"']")),
]


def should_scan(path: Path) -> bool:
    if path.name in SKIP_FILES:
        return False
    if any(part in SKIP_DIRS for part in path.parts):
        return False
    return path.suffix.lower() in TEXT_SUFFIXES or path.name in {"gradlew", "gradlew.bat"}


def main() -> int:
    findings: list[str] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or not should_scan(path):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        relative = path.relative_to(ROOT)
        for label, pattern in PATTERNS:
            for match in pattern.finditer(text):
                line = text.count("\n", 0, match.start()) + 1
                findings.append(f"{relative}:{line}: {label}")

    if findings:
        print("Se detectaron posibles secretos versionables:\n")
        for item in findings:
            print(f" - {item}")
        print("\nCorrige los hallazgos antes de publicar/mergear.")
        return 1

    print("OK: no se detectaron patrones de secretos en archivos versionables.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
