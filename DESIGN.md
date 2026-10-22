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
- Artwork cards use 16:9 framing, title metadata, a distinct focus outline, and a separate red progress indicator.
- Discovery uses horizontal rails with visible arrows. Continue Watching is a bounded grid/empty state, never a horizontal rail.
- Loading, empty, recovery error and success feedback use human-readable text. Technical exception strings are not UI copy.
- Hero copy must fit its available width: hero overlay bounds may shrink but must not be positioned outside the visible canvas.

## Accessibility and responsive floor

- Tab order follows navigation, search, content, then contextual actions.
- Focus, progress, loading, selected, and error states never rely on color alone.
- Search remains enabled and focused during background work; Escape returns to its originating section.
- Small desktop windows preserve critical playback actions and do not clip hero copy behind fixed minimum-width overlays.

## Motion & Transition Invariants

- Navigation transitions between sections (Home, Movies, Series, Live, Sports, Details) use a subtle, interruptible 140 ms crossfade at 60 fps without blocking the EDT.
- Reduced motion: when `win.animations.enabled` is false or `streamflix.reducedMotion` is active, transition duration is 0 ms (instant switch).
- Interactivity is immediate: clicking during a transition terminates the animation instantly.
- State preservation: vertical scroll position, active filters, search inputs, and keyboard focus are preserved across tab switches and detail view round-trips.
- RAM caching: catalog and Home data are cached in a bounded, thread-safe in-memory cache (5-minute TTL, LRU eviction) to eliminate redundant network round-trips and skeleton flickering.
- Detailed navigation and caching invariants are documented in [`docs/NAVIGATION_UX_INVARIANTS.md`](docs/NAVIGATION_UX_INVARIANTS.md).
