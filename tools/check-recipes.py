#!/usr/bin/env python3
"""check-recipes.py - read-only check of lpatch/HMG-API recipes against a stock tree.

usage: check-recipes.py <stock tree> <recipe> [<recipe> ...]

For each recipe: whole-file md5 vs its `native` lines, then every `verify`
region (offset, length) of the stock file vs the recipe's "before" and
"after" md5s. Writes nothing.

  NATIVE   the file is one the recipe knows needs no patch
  STOCK    every region matches the recipe's stock bytes: the patch applies
  PATCHED  every region already holds the patched bytes
  MISMATCH some region matches neither: this build differs, the recipe can't be used
"""
import hashlib, os, sys


def md5(b):
    return hashlib.md5(b).hexdigest()


def check(tree, recipe):
    path = native = None
    natives, verifies = {}, []
    for line in open(recipe, encoding="utf-8"):
        f = line.split()
        if not f or f[0].startswith("#"):
            continue
        if f[0] == "id":
            rid = f[1]
        elif f[0] == "file":
            path = f[1]
        elif f[0] == "native":
            natives[f[1]] = " ".join(f[2:])
        elif f[0] == "verify":
            verifies.append((int(f[1], 0), int(f[2]), f[3], f[4]))
    local = os.path.join(tree, os.path.basename(path)) if path else None
    for sub in ("lib", "app", "framework", "bin", ""):
        cand = os.path.join(tree, sub, os.path.basename(path))
        if os.path.isfile(cand):
            local = cand
            break
    if not local or not os.path.isfile(local):
        return f"{rid}: {path}: not in tree"
    data = open(local, "rb").read()
    whole = md5(data)
    if whole in natives:
        return f"{rid}: NATIVE ({natives[whole]}) {path} md5={whole}"
    states = []
    for off, n, before, after in verifies:
        h = md5(data[off:off + n])
        states.append("stock" if h == before else "patched" if h == after else "other")
    if all(s == "stock" for s in states):
        verdict = "STOCK (patch applies)"
    elif all(s == "patched" for s in states):
        verdict = "PATCHED (already applied)"
    else:
        verdict = "MISMATCH"
    detail = " ".join(f"{hex(v[0])}:{s}" for v, s in zip(verifies, states))
    return f"{rid}: {verdict} {path} md5={whole} regions[{detail}]"


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    for r in sys.argv[2:]:
        print(check(sys.argv[1], r))
