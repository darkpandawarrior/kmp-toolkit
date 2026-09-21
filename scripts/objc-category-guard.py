#!/usr/bin/env python3
"""objc-category-guard — catch the Obj-C category / CEnum import defect before a build.

    python3 scripts/objc-category-guard.py            # scan the repo, exit 1 on findings
    python3 scripts/objc-category-guard.py --root .   # scan a specific tree
    python3 scripts/objc-category-guard.py --self-test  # prove the guard itself still works

WHY THIS EXISTS. Kotlin/Native lowers Objective-C CATEGORY members to PACKAGE-LEVEL extensions,
not to members of the class. Importing the class alone does not bring them into scope, so
`session.setActive(...)` fails with "Unresolved reference 'setActive' on receiver of type
'AVAudioSession'" even though the header plainly declares it. The fix is always an added import
of the MEMBER name. Separately, NS_ENUM constants lower to entries of a CEnum CLASS, so
`import platform.PassKit.PKPaymentAuthorizationStatusSuccess` is never valid — the entry must be
reached through its enum class. Six gate cycles across four repos were spent on those two shapes
between 2026-07 and 2026-09; each cost a full Mac CI leg to discover. This is pure text analysis,
so it runs on the Linux leg in under a second instead.

HOW TO ADD A MEMBER (this list will grow — that is expected).
  * Category member (symptom: "Unresolved reference 'x' on receiver of type 'Y'"):
    add one row to CATEGORY_MEMBERS below — ("Framework", "memberName", "what declares it").
    "Framework" is the segment after `platform.`, e.g. "AVFAudio" for platform.AVFAudio.
  * NS_ENUM constant (symptom: "Unresolved reference 'FooBarSuccess'" on an imported constant):
    add one row to ENUM_CLASSES — ("Framework", "EnumClassName"). Every imported symbol whose
    name starts with EnumClassName and is longer than it is then treated as an entry.
  * False positive on your own identically-named property: put `objc-category-guard:allow` in a
    comment on that line. Prefer that over deleting a row.
  * After any edit, run `--self-test`, and add a case to the fixtures under
    scripts/fixtures/objc-category-guard/ if the new row is a shape the fixtures do not cover.

WHAT THIS CANNOT CATCH — read before trusting it.
  1. Only members in the list below. An unlisted category member sails through. This is a
     regression guard for known shapes, NOT a substitute for the Mac compile leg.
  2. Only dotted receiver access (`x.member`). A member reached inside `apply { }` / `with(x) { }`
     with no receiver dot is invisible to it.
  3. It cannot tell YOUR `foo.title` from CoreSpotlight's. The package-anchor rule (a file is only
     checked for a framework's members when it imports something else from that framework) is what
     keeps that survivable, and it is a heuristic, not a type check.
  4. It reads text, not types. Wrong-receiver, wrong-arity and nullability errors are out of scope.
"""
import argparse
import os
import re
import sys

# ---------------------------------------------------------------------------
# Seed list — the six real gate failures. See "HOW TO ADD A MEMBER" above.
# ---------------------------------------------------------------------------
CATEGORY_MEMBERS = [
    # (framework, member, what declares it)
    ("Foundation", "writeToFile", "NSData (NSDataCreation) — kmp-toolkit feedback, 2026-07"),
    ("UIKit", "popoverPresentationController", "UIViewController (UIPopoverPresentationController)"),
    ("AVFAudio", "setActive", "AVAudioSession (Activation) — Kursi SoundPlayer, 2026-09"),
    ("GameKit", "setAuthenticateHandler", "GKLocalPlayer (UI) — Kursi GameCenterServices, 2026-09"),
    ("GameKit", "saveGameData", "GKLocalPlayer (GKSavedGame)"),
    ("GameKit", "fetchSavedGamesWithCompletionHandler", "GKLocalPlayer (GKSavedGame)"),
    ("GameKit", "loadDataWithCompletionHandler", "GKSavedGame"),
    ("CoreSpotlight", "title", "CSSearchableItemAttributeSet (General) — HireSignal, 2026-09"),
    ("CoreSpotlight", "contentDescription", "CSSearchableItemAttributeSet (General)"),
    ("CoreSpotlight", "keywords", "CSSearchableItemAttributeSet (General)"),
    ("CoreSpotlight", "contentURL", "CSSearchableItemAttributeSet (General)"),
]

# NS_ENUM classes whose constants must be reached through the class, never imported bare.
ENUM_CLASSES = [
    # (framework, enum class)
    ("PassKit", "PKPaymentAuthorizationStatus"),  # kmp-toolkit provider/applepay, 2026-09
]

SOURCE_SET = re.compile(r"^(ios|apple|watchos|tvos|macos|darwin)[A-Za-z0-9]*Main$")
IMPORT = re.compile(r"^\s*import\s+platform\.([A-Za-z0-9_]+)\.([A-Za-z0-9_*]+)")
ALLOW = "objc-category-guard:allow"
FIXTURES = os.path.join("scripts", "fixtures", "objc-category-guard")


def apple_sources(root):
    """Every .kt file under an Apple source set, fixtures excluded."""
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in ("build", ".git", ".gradle")]
        if FIXTURES in dirpath:
            continue
        parts = dirpath.split(os.sep)
        if not any(SOURCE_SET.match(p) for p in parts):
            continue
        for name in sorted(filenames):
            if name.endswith(".kt"):
                yield os.path.join(dirpath, name)


def strip_noise(line):
    """Blank out string literals and line comments so their text cannot match."""
    line = re.sub(r'"(?:[^"\\]|\\.)*"', '""', line)
    return line.split("//", 1)[0]


def scan_file(path):
    findings = []
    with open(path, encoding="utf-8", errors="replace") as handle:
        lines = handle.read().splitlines()

    imported = set()          # (framework, symbol)
    frameworks = set()        # frameworks this file touches at all
    for number, raw in enumerate(lines, 1):
        match = IMPORT.match(raw)
        if not match:
            continue
        framework, symbol = match.group(1), match.group(2)
        imported.add((framework, symbol))
        frameworks.add(framework)
        if ALLOW in raw:
            continue
        for enum_framework, enum_class in ENUM_CLASSES:
            if framework == enum_framework and symbol.startswith(enum_class) and symbol != enum_class:
                findings.append((
                    path, number, match.start(2) + 1,
                    "'%s' is an entry of the CEnum class %s, not a package-level declaration." % (symbol, enum_class),
                    "import platform.%s.%s and write %s.%s at the use site." % (enum_framework, enum_class, enum_class, symbol),
                ))

    for framework, member, origin in CATEGORY_MEMBERS:
        if framework not in frameworks:
            continue  # package anchor: this file does not use the framework at all
        if (framework, member) in imported or (framework, "*") in imported:
            continue
        pattern = re.compile(r"\.\s*(%s)\b" % re.escape(member))
        for number, raw in enumerate(lines, 1):
            if IMPORT.match(raw) or ALLOW in raw:
                continue
            body = strip_noise(raw)
            for hit in pattern.finditer(body):
                before = body[:hit.start()]
                if re.search(r"platform(\.[A-Za-z0-9_]+)*$", before):
                    continue  # already fully qualified
                findings.append((
                    path, number, hit.start(1) + 1,
                    "'%s' is an Obj-C category member (%s); Kotlin/Native exposes it as a package-level extension." % (member, origin),
                    "add `import platform.%s.%s`." % (framework, member),
                ))
    return findings


def scan(root):
    findings = []
    for path in apple_sources(root):
        findings.extend(scan_file(path))
    return findings


def report(findings, root):
    for path, line, col, why, fix in findings:
        rel = os.path.relpath(path, root)
        print("%s:%d:%d: [objc-category-guard] %s\n    fix: %s" % (rel, line, col, why, fix))
    return findings


def self_test():
    """The guard's own check: one fixture tree that must trip, one that must not."""
    here = os.path.dirname(os.path.abspath(__file__))
    fixtures = os.path.join(here, "fixtures", "objc-category-guard")

    # apple_sources() skips anything under scripts/fixtures, so the self-test calls scan_file
    # directly on the fixture files — discovery is covered by the repo scan in main().
    def files(kind):
        found = []
        for dirpath, _, filenames in os.walk(os.path.join(fixtures, kind)):
            found += [os.path.join(dirpath, n) for n in sorted(filenames) if n.endswith(".kt")]
        assert found, "no .kt fixtures under " + kind
        return found

    bad = [f for path in files("trip") for f in scan_file(path)]
    tripped = {f[3].split("'")[1] for f in bad}
    expected = {"writeToFile", "setActive", "title", "contentURL", "PKPaymentAuthorizationStatusSuccess"}
    missing = expected - tripped
    assert not missing, "trip fixture no longer trips on: %s" % sorted(missing)

    good = [f for path in files("clean") for f in scan_file(path)]
    assert not good, "clean fixture produced findings:\n%s" % "\n".join(str(f) for f in good)

    print("self-test ok: %d findings on the trip fixture (%s), 0 on the clean fixture"
          % (len(bad), ", ".join(sorted(tripped))))
    return 0


def main():
    parser = argparse.ArgumentParser(description="Guard against Obj-C category / CEnum import mistakes.")
    parser.add_argument("--root", default=".", help="tree to scan (default: cwd)")
    parser.add_argument("--self-test", action="store_true", help="verify the guard against its fixtures")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    findings = report(scan(args.root), args.root)
    if findings:
        print("\n%d finding(s). Each is a missing member import or a CEnum entry imported bare."
              % len(findings), file=sys.stderr)
        return 1
    print("objc-category-guard: clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
