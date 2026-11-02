# Cognia Desktop

The renderer is shared between the web app and Electron. Desktop mode is selected
by Vite's `desktop` mode, so normal web development and deployment are unchanged.

## Commands

```bash
# Web
pnpm dev
pnpm build

# Electron in development (starts Vite and Electron together)
pnpm dev:desktop

# Build renderer + main + preload
pnpm build:desktop

# Build installers for the current platform/arch
pnpm dist:desktop

# Build an unpacked app for quick local verification
pnpm dist:desktop:dir
```

## API origin

Electron loads the production renderer from `file://`, so the API cannot use the
web-relative `/api/v1` URL. `.env.desktop` supplies an absolute origin:

```dotenv
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

Change this value before building if the desktop app should talk to a remote
backend. The main process also adds CORS headers for the local API origin
(`localhost:8080` / `127.0.0.1:8080`); the renderer authenticates with bearer
tokens rather than cookies.

## Routing

The router uses `createWebHistory()` in the browser and
`createWebHashHistory()` inside Electron. The fallback also checks for the
`file:` protocol, so deep links and refreshes keep working in packaged builds.

## Packaging

`electron-builder` targets:

| Platform | Packages |
| --- | --- |
| Windows | NSIS `.exe` (x64, arm64) |
| macOS | `.dmg`, `.zip` (x64, arm64) |
| Linux | AppImage, deb, rpm (x64, arm64) |

The main process and preload are built into `dist-electron/`; renderer output is
in `dist/`. Packaged output goes to `release/` and is intentionally ignored by
git.

Linux rpm packaging requires `rpmbuild`. The GitHub Actions workflow installs
the `rpm` package on the Ubuntu runner before invoking electron-builder.

## GitHub Actions

`.github/workflows/desktop-release.yml` packages:

- macOS x64 and arm64
- Windows x64 and arm64
- Linux x64 and arm64

It runs on manual dispatch or tags matching `v*`, uploads one artifact per
platform/arch, and leaves signing/notarization disabled by default. Add the
relevant Apple/Windows signing secrets and remove the unsigned defaults if
release signing is needed.
