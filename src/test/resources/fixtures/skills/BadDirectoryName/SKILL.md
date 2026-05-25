---
name: BadDirectoryName
description: Use this skill when verifying that a non kebab-case directory name is rejected.
---

# Bad Directory Name

Use this skill when the parent directory uses PascalCase or another non kebab-case form.

Note: name matches the directory so `frontmatter.name.mismatch` does not fire here; the primary expectations are `skill.directory.name` and `frontmatter.name.invalid`.
