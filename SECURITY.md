# Security Policy

## Supported version

Security fixes are currently targeted at the latest stable Retra 1.x release, currently **v1.0.4**.

## Reporting a vulnerability

Please do not publish exploitable security details in a public issue before a fix is available.

When reporting a problem, include:
- affected Retra version/commit;
- Android version and device model;
- reproduction steps;
- expected and observed behavior;
- whether the issue requires a crafted ROM, save, shader, network peer, or local file.

Avoid attaching copyrighted ROM/BIOS files. A minimal legal test case or hash is preferred.

## Security boundaries

Retra treats ROMs, patches, BIOS files, shaders, save imports, network peers and cloud content as potentially untrusted input. Relevant protections include bounded import sizes, WebView HTTPS asset origin, disabled cleartext traffic, restricted file URL access, bounded Remote Link snapshots/input packets, and verified persistent-data writes.

Remote Link is intended for trusted devices on local Wi-Fi/hotspot or paired Bluetooth connections. It is not designed as an Internet-facing authenticated transport.
