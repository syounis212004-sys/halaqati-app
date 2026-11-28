#!/usr/bin/env python3
"""Guard database module boundaries, package and old on-disk schema."""
import json
from pathlib import Path
root = Path(__file__).resolve().parents[1]
app = (root / "app/build.gradle.kts").read_text()
project = (root / "build.gradle.kts").read_text()
library = (root / "offline-db/build.gradle.kts").read_text()
settings = (root / "settings.gradle.kts").read_text()
assert '":offline-db"' in settings and 'implementation(project(":offline-db"))' in app
assert 'org.jetbrains.kotlin.kapt' not in app + project and 'kapt(' not in app + project
assert 'ksp("androidx.room:room-compiler:2.8.5")' in library
assert 'room-compiler' not in app
assert 'room.generateKotlin", "false"' in library
schema = json.loads((root / "app/schemas/com.mohammedsamir.halaqati.data.HalaqatiOfflineDatabase/1.json").read_text())
assert schema["database"]["identityHash"] == "d792bf2c454b8220073d218edc360386"
assert schema["database"]["version"] == 1
base = "src/main/java/com/mohammedsamir/halaqati/data"
db = root / "offline-db" / base / "OfflineDatabase.kt"
assert db.exists() and not (root / "app" / base / "OfflineDatabase.kt").exists()
assert '"halaqati-rc7.db"' in db.read_text()
assert 'fallbackToDestructiveMigration' not in db.read_text()
print("Room module, original schema identity, and non-destructive upgrade contract verified")
