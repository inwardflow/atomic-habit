# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| 0.1.x   | ✅ |
| < 0.1   | ❌ (unreleased snapshots) |

Security fixes are released as patch versions of the latest minor release.

## Reporting a Vulnerability

Please **do not open a public issue**. Report it privately through GitHub:
[Report a vulnerability](https://github.com/inwardflow/atomic-habit/security/advisories/new)
(Security tab → "Report a vulnerability").

Include:
- A clear description of the issue
- Reproduction steps
- Affected components/versions
- Impact assessment

We aim to acknowledge reports within 7 days and will coordinate a fix and disclosure timeline with
you. Do not disclose vulnerabilities publicly before a fix is released.

## Verifying releases

Release artifacts and container images carry GitHub build provenance attestations; see
[RELEASING.md](RELEASING.md#verifying-an-artifact).

## Secret handling
- Never commit API keys, tokens, or passwords.
- Use environment variables and `.env` files excluded by `.gitignore`.
- In production, `SPRING_JWT_SECRET` is mandatory; the application refuses to start without it.
