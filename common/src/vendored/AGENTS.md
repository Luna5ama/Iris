# VENDORED SOURCE KNOWLEDGE

## OVERVIEW

Packaged third-party Odysseus Ithaka directed-graph sources. This source set preserves foreign package identity and licensing while allowing Iris to ship the dependency in its jar.

## STRUCTURE

```text
java/de/odysseus/ithaka/digraph/
├── core graph interfaces and adapters
├── io/    # Graph readers/writers
└── util/  # Algorithms and feedback-arc-set utilities
```

## CONVENTIONS

- Preserve the `de.odysseus.ithaka.digraph` package namespace; do not relocate it into `net.irisshaders`.
- Preserve Apache-2.0 attribution and existing source headers.
- Prefer an upstream-aligned patch over Iris-specific redesign. Keep any unavoidable divergence small and traceable.
- The `vendored` output is compiled separately, then placed on `main` compile/runtime classpaths and included in the jar.
- Treat graph API behavior as third-party compatibility, even when Iris has only one current consumer.
- Match the established Java style in these files rather than rewriting them to surrounding Iris idioms.

## ANTI-PATTERNS

- Do not mix Iris runtime state, Minecraft types, rendering code, or loader APIs into vendored classes.
- Do not bulk-format or rename the tree during an unrelated change.
- Do not remove apparently unused algorithms without checking upstream provenance and packaged API reach.
- Do not duplicate these classes under `common/src/main`; fix the source-set wiring if resolution breaks.

## VALIDATION

```powershell
.\gradlew.bat :common:compileVendoredJava
.\gradlew.bat :fabric:build
```

After packaging changes, inspect the produced jar for `de/odysseus/ithaka/digraph` classes and retained licensing.
