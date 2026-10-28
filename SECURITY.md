# Security Policy

## Supported versions

LSY Free is an independently maintained fork. Security fixes are considered for the current state of the `main` branch. There is no promise of a fixed support window for older commits or builds; check the repository for current releases and maintenance activity.

| Version / source | Security support |
| --- | --- |
| Latest code on `main` | Best-effort review and fixes |
| Older commits and unofficial builds | Not guaranteed |

## Reporting a vulnerability

Please report suspected vulnerabilities privately and do not publish exploit details before the maintainer has had a reasonable opportunity to investigate.

1. Open the repository's **Security** tab and check whether **Report a vulnerability** / private vulnerability reporting is available.
2. If private reporting is unavailable, contact the maintainer through the [@daddyyour0786-dev GitHub profile](https://github.com/daddyyour0786-dev). Do not include exploit details in a public issue.
3. Include a concise description, affected version or commit, impact, reproduction steps, and any relevant logs or screenshots after redacting secrets and personal information.

Please do not send real credentials, private add-on URLs, access tokens, or release signing keys as part of a report.

## What to expect

The maintainer will review the report and may ask for clarification or a minimal reproduction. Response and remediation times are best-effort; no guaranteed response deadline is currently offered. If the issue is confirmed, the maintainer will assess impact, prepare a fix where feasible, and coordinate public disclosure when appropriate. Reports may be closed if they cannot be reproduced, are outside the project's control, or do not represent a security vulnerability.

## Security notes for users and contributors

- Treat add-on URLs as secrets when they contain tokens.
- Never commit keystores, signing credentials, API keys, passwords, access tokens, or local environment files.
- Use HTTPS for add-on endpoints when supported and trusted.
- Be cautious when installing APKs from untrusted sources. Debug builds are for testing and are not equivalent to production-signed releases.
- Review permissions, third-party integrations, and the terms of services you connect to the app.
- If a secret is accidentally committed, revoke or rotate it first. Removing it from the latest commit does not guarantee it has been removed from Git history or copies of the repository.
