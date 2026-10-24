---
version: alpha
name: Streamflix Desktop
description: Cinematic editorial UI for a keyboard-friendly Windows streaming app.
colors:
  primary: "#C92A36"
  primary-hover: "#E43D49"
  background: "#0A0C11"
  surface: "#131720"
  surface-raised: "#191E2A"
  border: "#2A3140"
  text: "#F6F7F9"
  muted: "#979FB2"
  focus: "#FFDD68"
  success: "#68D391"
  error: "#FF7676"
typography:
  headline-lg:
    fontFamily: Segoe UI Variable Display
    fontSize: 30px
    fontWeight: 700
    lineHeight: 1.15
  body-md:
    fontFamily: Segoe UI Variable Text
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.4
  label-sm:
    fontFamily: Segoe UI Variable Text
    fontSize: 11px
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: 0.04em
spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 34px
rounded:
  sm: 6px
  md: 12px
  full: 999px
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.text}"
    rounded: "{rounded.md}"
    padding: 9px
  button-focus:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.text}"
    rounded: "{rounded.md}"
  player-stage:
    backgroundColor: "#000000"
    overlayBackground: "rgba(8, 10, 15, 0.90)"
    controlsHeightTop: 54px
    controlsHeightBottom: 72px
  card-landscape:
    aspectRatio: "16:9"
    rounded: "{rounded.md}"
    border: "{colors.border}"
    focusOutline: "{colors.focus}"
  state-card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.md}"
    padding: 24px
    maxWidth: 560px
  progress-bar:
    trackColor: "{colors.surface-raised}"
    fillColor: "{colors.primary}"
    height: 4px
    rounded: "{rounded.sm}"
---

# Streamflix Desktop Design System

## Overview

Cinema editorial nocturno: superficies de carbón profundo, tipografía de interfaz sobria y un rojo cálido reservado para acciones y progreso. El artwork proporciona el contexto emocional; la UI mantiene contraste y jerarquía sin neón, glassmorphism ni métricas inventadas.

## Brand

The approved Streamflix mark is an abstract segmented **S**: a warm-red cinematic ribbon cut by negative space and anchored by two off-white angular blocks. It is intentionally simple enough to survive at Windows taskbar size while remaining distinct from generic play-button streaming marks.

Canonical source assets:

- `assets/brand/streamflix-mark.svg` — transparent symbol.
- `assets/brand/streamflix-app-icon.svg` — symbol on the charcoal app tile.
- `BrandMark.java` — runtime/vector renderer used by Swing and build-time icon export.

Do not redraw the mark into a play triangle, add gradients/glow, or introduce a Netflix-like wordmark treatment.

## Colors

- Background: `Theme.BG`; surfaces: `Theme.PANEL` and `Theme.PANEL_ALT`; dividers: `Theme.BORDER`.
- Primary action and playback progress: `Theme.ACCENT`; keyboard focus: `Theme.FOCUS`; success: `Theme.SUCCESS`; recoverable error: `Theme.DANGER`.
- Brand mark: `#C92A36` red + `#F6F7F9` off-white on `#0A0C11` charcoal.

## Typography

- Display type: Segoe UI Variable Display. Interface/body: Segoe UI Variable Text.

## Layout

Spacing uses 4 px increments. Primary content gutter is 34 px at desktop widths and contracts only when the available width requires it.

## Elevation & Depth

Depth comes from tonal surface layers and restrained borders, never glass effects or heavy shadows.

## Shapes

Controls use a 12 px corner radius; compact labels and progress affordances preserve a softer 6 px radius.

## Iconography

Functional controls use the vector `StreamflixIcons` system rather than Unicode glyphs. Icons are geometric, line-weight consistent, DPI-independent, and subordinate to text labels where clarity/accessibility benefits from both. Tooltips and accessible names remain required for icon-only controls.

The brand mark supplies personality; control icons stay neutral. Do not generate each functional icon independently with image models.

## Components and behavior

- Navigation and controls always expose a visible keyboard-focus outline in addition to hover and selected state.
- Catalog-card focus is a persistent 2 px `Theme.FOCUS` outline, independent of pointer hover; button press feedback darkens on pointer-down and clears on release/cancel.
- Artwork cards use 16:9 framing, title metadata, a distinct focus outline, and a separate red progress indicator.
- When a locally cached image is unavailable, cards and editorial stages retain hierarchy with a deterministic tonal title treatment built from the provider title. This is an honest fallback, not generated artwork; it must never add fabricated cast, ratings, or imagery.
- Discovery uses horizontal rails with visible arrows. Continue Watching is a bounded grid/empty state, never a horizontal rail.
- Loading, empty, recovery error and success feedback use human-readable text. Technical exception strings are not UI copy.
- Hero copy must fit its available width: hero overlay bounds may shrink but must not be positioned outside the visible canvas.

## Accessibility and responsive floor

- Tab order follows navigation, search, content, then contextual actions.
- Focus, progress, loading, selected, and error states never rely on color alone.
- Search remains enabled and focused during background work; Escape returns to its originating section.
- Small desktop windows preserve critical playback actions and do not clip hero copy behind fixed minimum-width overlays.
- At compact desktop widths, the primary navigation remains intact while search and settings move to a second toolbar row. Controls are never hidden, reduced to icon-only ambiguity, or removed from keyboard order to make a single row fit.

## Motion & Transition Invariants

- Navigation transitions between sections (Home, Movies, Series, Live, Sports, Details) use a subtle, interruptible 140 ms crossfade driven by 16 ms timer intervals without blocking the EDT. Interactive 60 fps smoothness depends on native display hardware and is tracked as native validation debt.
- Reduced motion: when `win.animations.enabled` is false or `streamflix.reducedMotion` is active, transition duration is 0 ms (instant switch).
- Interactivity is immediate: clicking during a transition terminates the animation instantly.
- State preservation: vertical scroll position, active filters, search inputs, and keyboard focus are preserved across tab switches and detail view round-trips.
- RAM caching: catalog and Home data are cached in a bounded, thread-safe in-memory cache (5-minute TTL, LRU eviction) to eliminate redundant network round-trips and skeleton flickering.
- Detailed navigation and caching invariants are documented in [`docs/NAVIGATION_UX_INVARIANTS.md`](docs/NAVIGATION_UX_INVARIANTS.md).

## Playback & In-Frame Player Architecture

- Default Playback in MainFrame: Playing media switches `contentStack` to the `PLAYER` card within `MainFrame`. No secondary window is opened on standard play actions.
- Overlay Chrome: Top and bottom control bars are overlays on the native mpv Canvas. Auto-hiding or revealing controls never resizes or flashes the video stage.
- Fullscreen Continuity: Fullscreen operates directly on `MainFrame`. Overlay controls auto-hide after 2.8 s of inactivity and reappear upon mouse movement or key press.
- Contextual Return: Pressing Escape or the Back button exits the player and restores the detail view or origin without reloading data or resetting scroll/focus.
- Pop-Out Mode: Standalone floating window (`EmbeddedPlayerWindow`) is accessible as an explicit secondary action, never as the default playback mode.

## Loading & Honest State Invariants

- Non-Dominant Start: Shimmering skeleton blocks and arbitrary percentage indicators (such as legacy "81%") are eliminated from dominant loading states.
- Contextual Preparation: Empty-cache starts present a compact contextual card ("Preparando tu inicio") while keeping navigation and search fully interactive.
- Background Refresh: Existing catalog and search items remain interactive while new results load in the background, eliminating layout flicker.
- Error Recovery: Actionable recovery cards with clear, non-technical explanations and direct retry actions ("Reintentar") replace unhandled exceptions or blank screens.

## Transversal Design Tokens & Component Geometry

- Standard Spacing Scale: `Theme.SPACE_1` (4 px), `Theme.SPACE_2` (8 px), `Theme.SPACE_3` (12 px), and `Theme.SPACE_4` (16 px) govern margins, paddings, and control separations.
- Corner Radii: `Theme.RADIUS_CONTROL` (12 px) on standard buttons, input fields, state cards, sports cards, and catalog cards; `Theme.RADIUS_COMPACT` (6 px) on checkboxes, progress bars, and compact badges.
- Antialiased Surfaces: `Theme.RoundedPanel` renders tonal panels (`#131720`) with smooth antialiased borders (`#2A3140`), preventing sharp rectangular clipping artifacts across Home, Settings, Sports Hub, and Guide dialogs.
- Interactive Feedback: `PillButton` implements tactile pointer-down darkening, hover luminescence, and distinct 2 px `#FFDD68` keyboard focus outlines.
- Tabbed Navigation: `SettingsDialog` tabs incorporate mnemonic shortcuts, tooltips, accessible descriptions, and scroll-protected viewports for compact screen sizes.
- Timer Disambiguation: All EDT-driven animations, transitions, debounces, and polling loops use explicit `javax.swing.Timer` instances to guarantee toolchain determinism.
