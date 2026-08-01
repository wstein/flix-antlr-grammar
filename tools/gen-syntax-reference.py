#!/usr/bin/env python3
"""Generate docs/SYNTAX.md from the canonical grammars.

The reference is derived rather than hand-written so it cannot drift from the
grammar. Regenerate with:

    python3 tools/gen-syntax-reference.py

Run it after any change to grammars/*.g4 and commit the result.
"""
from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
LEXER = ROOT / "grammars" / "FlixLexer.g4"
PARSER = ROOT / "grammars" / "FlixParser.g4"
OUT = ROOT / "docs" / "SYNTAX.md"

# A rule runs from its name to the terminating semicolon at column 0 or after a space.
# A rule runs from its name to the first semicolon that ends a line. Parser rules put
# it on its own line; lexer rules keep it inline.
RULE = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)\s*\n?\s*:(.*?);[ \t]*$", re.S | re.M)
SECTION = re.compile(r"^// =+\n// (.+?)\n(?://.*\n)*// =+$", re.M)


def strip_actions(body: str) -> str:
    """Remove embedded actions, predicates and labels; they are implementation, not syntax."""
    body = re.sub(r"\{[^{}]*\}\?", "", body)      # semantic predicates
    body = re.sub(r"\{[^{}]*\}", "", body)        # actions
    body = re.sub(r"\s*#\s*[A-Za-z_][A-Za-z0-9_]*", "", body)  # alternative labels
    body = re.sub(r"->\s*(channel|type|skip|pushMode|popMode)\([^)]*\)", "", body)
    body = re.sub(r"->\s*popMode", "", body)
    body = re.sub(r"//[^\n]*", "", body)          # trailing comments
    lines = [ln.strip() for ln in body.splitlines()]
    return "\n".join(ln for ln in lines if ln)


def rules(path: pathlib.Path) -> list[tuple[str, str]]:
    text = path.read_text(encoding="utf-8")
    found = []
    for m in RULE.finditer(text):
        name, body = m.group(1), strip_actions(m.group(2))
        if not body:
            continue
        found.append((name, body))
    return found


def render(name: str, body: str) -> str:
    alts = [a.strip() for a in body.split("\n|")]
    head = f"{name}\n    : {alts[0].lstrip(': ').strip()}"
    rest = "".join(f"\n    | {a}" for a in alts[1:])
    return f"```antlr\n{head}{rest}\n    ;\n```"


def main() -> None:
    parser_rules = rules(PARSER)
    lexer_rules = rules(LEXER)
    lexer_names = {n for n, _ in lexer_rules}

    out = [
        "# Flix syntax reference",
        "",
        "**Generated from `grammars/FlixLexer.g4` and `grammars/FlixParser.g4`.**",
        "Do not edit by hand; run `python3 tools/gen-syntax-reference.py`.",
        "",
        "This is the grammar as implemented, which is deliberately a *superset* of legal Flix.",
        "Constraints such as duplicate modifiers, non-linear patterns and unknown annotation",
        "names parse here and are rejected by a later validation pass, mirroring how the",
        "reference compiler separates `Parser2` from `Weeder2`.",
        "",
        f"Parser rules: {len(parser_rules)} · lexer rules: {len(lexer_names)}",
        "",
        "## Parser rules",
        "",
    ]
    for name, body in parser_rules:
        out += [f"### `{name}`", "", render(name, body), ""]

    out += ["## Lexer rules", "", "Keywords are omitted; see `fixtures/keywords.txt` for the 84.", ""]
    keyword = re.compile(r"^'[a-zA-Z][a-zA-Z0-9_#*]*'$")
    for name, body in lexer_rules:
        if keyword.match(body.strip()):
            continue
        out += [f"### `{name}`", "", render(name, body), ""]

    OUT.write_text("\n".join(out).rstrip() + "\n", encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)}: {len(parser_rules)} parser rules, {len(lexer_names)} lexer rules")


if __name__ == "__main__":
    main()
