#!/usr/bin/env python3
"""Resolve git conflict hunks in a file by strategy.

Usage: resolve.py <file> <strategy>[,<strategy>...]
Strategies (one per hunk, in order; a single value applies to all hunks):
  ours   - keep HEAD side
  theirs - keep upstream side
  both   - keep ours then theirs
  botht  - keep theirs then ours
"""
import sys, re

def split_hunks(text):
    """Return list of (pre, ours, theirs) plus trailing text."""
    parts = []
    pos = 0
    pat = re.compile(
        r'^<<<<<<< [^\n]*\n(.*?)^=======\n(.*?)^>>>>>>> [^\n]*\n',
        re.S | re.M)
    for m in pat.finditer(text):
        parts.append((text[pos:m.start()], m.group(1), m.group(2)))
        pos = m.end()
    return parts, text[pos:]

def main():
    path, strat = sys.argv[1], sys.argv[2]
    strategies = strat.split(',')
    with open(path, encoding='utf-8') as f:
        text = f.read()
    hunks, tail = split_hunks(text)
    if not hunks:
        print(f'{path}: no conflicts found', file=sys.stderr)
        return 1
    if len(strategies) == 1:
        strategies = strategies * len(hunks)
    if len(strategies) != len(hunks):
        print(f'{path}: {len(hunks)} hunks but {len(strategies)} strategies',
              file=sys.stderr)
        return 1
    out = []
    for (pre, ours, theirs), s in zip(hunks, strategies):
        out.append(pre)
        if s == 'ours':
            out.append(ours)
        elif s == 'theirs':
            out.append(theirs)
        elif s == 'both':
            out.append(ours + theirs)
        elif s == 'botht':
            out.append(theirs + ours)
        elif s == 'skip':
            out.append(f'<<<<<<< HEAD\n{ours}=======\n{theirs}>>>>>>> upstream/master\n')
        else:
            print(f'unknown strategy {s}', file=sys.stderr)
            return 1
    out.append(tail)
    with open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(''.join(out))
    print(f'{path}: resolved {len(hunks)} hunk(s) -> {",".join(strategies)}')
    return 0

sys.exit(main())
