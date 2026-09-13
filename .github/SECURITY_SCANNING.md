# Security scanning

CI fails on any fixed, high- or critical-severity dependency or container-image vulnerability.
The filesystem scan covers application dependencies; the image scans cover the runnable Gateway,
Ledger, Worker, and PSP simulator images.

An exception requires a tracked security issue containing the finding identifier, affected package
or image, compensating control, owner, and expiry date. Add the finding to `.trivyignore` only
with that issue identifier and remove it before the expiry date.
