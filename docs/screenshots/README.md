# Streamflix Desktop Visual Artifacts & Redesign Verification

This directory documents executable visual proof and before/after redesign comparisons for Streamflix Desktop across key application surfaces adhering to Apple Design principles and `DESIGN.md`.

---

## Captured Artifacts

| Screen | File | Visual Invariants Verified |
|---|---|---|
| **Startup Loading** | [`01-startup-loading.png`](01-startup-loading.png) | Non-dominant contextual state card ("Preparando tu inicio"), honest progress bar, elimination of legacy 81% spinner, fully interactive top navigation. |
| **Home Populated** | [`02-home-populated.png`](02-home-populated.png) | Cinematic `HeroPanel` with dynamic backdrop, eyebrow badge, gold rating (`#FFDD68`), action buttons with vector glyphs, "Seguir viendo" rail with independent 2 px focus outline and red progress bar. |
| **Search View** | [`03-search-view.png`](03-search-view.png) | High-contrast search input (`Ctrl+F`), category chips, 16:9 artwork cards with metadata and persistent keyboard focus outline. |
| **Title Detail** | [`04-detail-view.png`](04-detail-view.png) | Editorial banner, quality badges (4K UHD, HDR10, TMDb score), synopsis, primary play action, and multi-provider source selector cards. |
| **Settings Dialog** | [`05-settings-dialog.png`](05-settings-dialog.png) | Accessible tabbed navigation (`JTabbedPane`), dark theme controls, in-frame player toggle, hardware acceleration switch, and quality defaults. |
| **Embedded Player** | [`06-embedded-player.png`](06-embedded-player.png) | In-frame player stage mounted directly under `PLAYER` card inside `MainFrame`, 100% video surface canvas, overlay top/bottom chrome without resizing flash, contextual return (`Esc`), and explicit pop-out mode. |

---

## Design System Invariants Addressed
1. **Color Contrast & Hierarchy**: Deep charcoal backgrounds (`#0A0C11`), tonal surfaces (`#131720`, `#191E2A`), borders (`#2A3140`), off-white typography (`#F6F7F9`), and warm red (`#C92A36`) reserved for actions and progress.
2. **Independent Keyboard Focus**: Persistent 2 px `Theme.FOCUS` (`#FFDD68`) outline independent of mouse hover state on catalog cards and buttons.
3. **Motion & Transitions**: Subordinate, interruptible motion with instant fallback (0 ms) when reduced motion is detected.
4. **In-Frame Continuity**: Default playback integrates into `MainFrame` navigation without opening secondary windows; pop-out remains an explicit user action.
