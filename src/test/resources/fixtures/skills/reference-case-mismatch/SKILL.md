---
name: reference-case-mismatch
description: Use this skill when a body link uses different case than the actual file path.
---

# Reference Case Mismatch

Use this skill when verifying the `reference.case-mismatch` rule.

See [usage](references/usage.md) — actual directory is `References/` (note the capital `R`).

On macOS the file is reachable thanks to case-insensitive filesystem, but the rule still flags the divergence because Linux deployments will break.
