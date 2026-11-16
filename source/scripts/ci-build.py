#!/usr/bin/env python3
"""Bound Gradle execution and retain output/thread dumps if compilation stalls."""
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

tasks = sys.argv[1].split(',')
limit = int(sys.argv[2])
extra_args = sys.argv[3:]
logs = Path('ci-logs')
logs.mkdir(exist_ok=True)
log = logs / (tasks[0] + '.log')
command = ['gradle', '--no-daemon', '--max-workers=1', '--console=plain', '--stacktrace', '--info',
           '-Dorg.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8',
           '-Pkotlin.compiler.execution.strategy=in-process',
           '-Pkotlin.daemon.useFallbackStrategy=false', *[':app:' + t for t in tasks], *extra_args]

def diagnostics(label):
    with (logs / (tasks[0] + '-' + label + '.txt')).open('w') as out:
        for cmd in [['free', '-h'], ['ps', '-eo', 'pid,ppid,%cpu,%mem,rss,etime,comm'], ['jcmd', '-l']]:
            subprocess.run(cmd, stdout=out, stderr=out, timeout=10, check=False)
        result = subprocess.run(['jcmd', '-l'], capture_output=True, text=True, timeout=10)
        for row in result.stdout.splitlines():
            if 'GradleDaemon' in row or 'KotlinCompileDaemon' in row:
                pid = row.split()[0]
                for action in ['GC.heap_info', 'Thread.print']:
                    try:
                        subprocess.run(['jcmd', pid, action], stdout=out, stderr=out, timeout=15, check=False)
                    except subprocess.TimeoutExpired:
                        out.write('Diagnostic timed out: ' + action + '\n')

with log.open('wb') as output, log.open('r', errors='replace') as reader:
    p = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
    start = time.monotonic()
    snapshot = False
    timed_out = False
    try:
        while p.poll() is None:
            text = reader.read()
            if text: print(text, end='', flush=True)
            elapsed = int(time.monotonic() - start)
            print(f'[CI heartbeat] {tasks[0]}: {elapsed}s elapsed; limit {limit}s', flush=True)
            if elapsed >= limit - 120 and not snapshot:
                try: diagnostics('before-timeout')
                except Exception as exc: print('Diagnostics unavailable:', type(exc).__name__, flush=True)
                snapshot = True
            if elapsed >= limit:
                timed_out = True
                os.killpg(p.pid, signal.SIGTERM)
                try: p.wait(timeout=10)
                except subprocess.TimeoutExpired: os.killpg(p.pid, signal.SIGKILL)
                break
            time.sleep(20)
    finally:
        if p.poll() is None:
            os.killpg(p.pid, signal.SIGKILL)
        p.wait()
        print(reader.read(), end='', flush=True)
    if timed_out:
        print('::error::Gradle exceeded its time budget; inspect Halaqati-build-diagnostics.')
        sys.exit(124)
    sys.exit(p.returncode)
