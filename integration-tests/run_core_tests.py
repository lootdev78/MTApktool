#!/usr/bin/env python3
"""Run real JVM checks; does not replace the Android Gradle build or device tests."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
classpath = os.pathsep.join(str(p) for p in sorted((root / 'mt-ftp/libs').glob('*.jar')))
sources = sorted((root / 'mt-ftp/src/main/java').rglob('*.java')) + sorted((root / 'mt-crypto/src/main/java').rglob('*.java'))
sources.append(root / 'integration-tests/CoreIntegrationTest.java')
with tempfile.TemporaryDirectory(prefix='mt-core-tests-') as output:
    subprocess.run(['java', 'com.sun.tools.javac.Main', '-cp', classpath, '-d', output, *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', output + os.pathsep + classpath, 'CoreIntegrationTest'], check=True, timeout=90)
