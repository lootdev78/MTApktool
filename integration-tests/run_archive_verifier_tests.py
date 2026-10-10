#!/usr/bin/env python3
"""Compile the production verifier and test complete ZIP/GZIP reads with the JDK.

Android/Kotlin archive adapters and their external libraries are not compiled by this test.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix="mt-archive-verifier-classes-") as temporary:
    classes = Path(temporary)
    subprocess.run(["java", "com.sun.tools.javac.Main", "-d", str(classes),
        str(root / "app/src/main/java/io/github/lootdev78/mtapktool/archive/ArchiveReadVerifier.java"),
        str(root / "integration-tests/ArchiveReadVerifierTest.java")], check=True)
    subprocess.run(["java", "-cp", str(classes), "ArchiveReadVerifierTest"], check=True, timeout=60)
