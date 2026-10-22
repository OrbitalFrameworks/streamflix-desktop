# Third-party notices

Streamflix Desktop is a desktop adaptation/port derived from ideas and portions of the architecture of:

- **Streamflix Reborn** — https://github.com/streamflix-reborn2/streamflix
  - License: Apache License 2.0

Third-party dependencies and runtime components include:

- **JNA / JNA Platform 5.19.1**
  - License: Apache-2.0 OR LGPL-2.1-or-later
- **FlatLaf 3.7.2**
  - License: Apache License 2.0
- **jsoup 1.19.1**
  - License: MIT
- **TwelveMonkeys ImageIO 3.12.0** (imageio-webp, core/metadata/common modules)
  - License: BSD
- **mpv** (`e471496c56` Windows runtime)
  - mpv source: https://github.com/mpv-player/mpv/tree/e471496c56f0f49c133bd88b50aed62b845f1909
  - Windows build release: https://github.com/shinchiro/mpv-winbuild-cmake/releases/tag/20261021
  - The Streamflix public ZIP does **not** redistribute `mpv.exe`. On first Windows launch, Streamflix downloads the pinned upstream binary asset directly, verifies its SHA-256 and stores it under `%LOCALAPPDATA%\Streamflix\runtime\mpv`.
  - Exact asset provenance and SHA-256 are documented in `third_party/mpv/SOURCE.txt`.
  - Matching mpv `Copyright`, `LICENSE.GPL` and `LICENSE.LGPL` texts are retained under `third_party/mpv/` for attribution and transparency.

The desktop port is not affiliated with the original Streamflix Reborn maintainers, TMDb, mpv, or third-party provider sites.

No third-party video content is hosted by this project. Provider sites and media hosts are independent services; users are responsible for complying with their terms and applicable law.
