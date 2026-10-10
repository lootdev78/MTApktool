#!/usr/bin/env python3
"""Check shared search rules and real filesystem rename/rollback without Android dependencies."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
sources = [root / 'app/src/main/java/io/github/lootdev78/mtapktool/feature/explorer/util/FileWorkflow.java',
           root / 'integration-tests/FileWorkflowTest.java']
with tempfile.TemporaryDirectory(prefix='mt-file-workflows-') as output:
    subprocess.run(['java', 'com.sun.tools.javac.Main', '-d', output, *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', output, 'FileWorkflowTest'], check=True, timeout=30)
