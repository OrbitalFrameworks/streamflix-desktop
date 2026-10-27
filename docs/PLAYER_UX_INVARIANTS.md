# Player UX invariants

These rules are regression guards for the Streamflix desktop player.

## 1. Fullscreen chrome must never resize video

The native mpv video surface always occupies the full player bounds.
Top and bottom controls are overlays on that surface.
Showing or hiding controls must not change the bounds of the media stage or native Canvas.

Why: the previous BorderLayout implementation removed the top/bottom bars from layout
when the 2.8 s fullscreen auto-hide timer fired. The media stage then expanded for a frame
and shrank again as soon as mouse movement revealed the controls. In screen recordings this
looked like a flash or scene change. It was reproduced repeatedly at ~1.73 s, 4.57 s and 7.40 s.

Regression test: MpvPlayerTest.testPlayerChromeOverlayKeepsMediaStable.

## 2. Continue Watching is not a horizontal rail

Continue Watching uses a bounded grid with no horizontal wheel handling and no rail arrows.
The normal Home vertical scroll remains active around and through this section.

## 3. Track preferences are explicit

Preferred audio/subtitle languages are applied after mpv exposes track-list.
A requested Spanish subtitle may match es/spa/Spanish/Español/Castellano/Latino aliases.
If no requested subtitle language exists, subtitles are disabled instead of choosing
an unrelated forced/default track.


## 4. Windowed player must respect the usable Windows desktop

The normal pop-out player must fit inside the monitor work area after taskbar insets.
It must never rely on a fixed 1320x820 rectangle that can extend behind the Windows taskbar.

Windowed playback supports:
- drag-to-move from the player title area;
- edge/corner resize while remaining inside the current monitor work area;
- maximize/restore against the usable work area, not raw screen bounds;
- minimize;
- Pin/Fijado always-on-top mode;
- Mini mode anchored near the lower-right usable corner and always on top.

Critical controls (play/pause, mute and fullscreen) remain visible at every supported responsive size.

## 5. Cross-provider playback failover with resume preservation and strict content safety

When initiating VOD playback or recovering from an unrecoverable server failure, Streamflix executes data-driven sequential failover across candidate providers.

Rules:
- Primary and alternate provider candidates are ranked by local health stats (success rate, latency, cooldown, quality tier, and user language preferences);
- Loop prevention: each provider candidate is attempted at most once per playback session;
- Content safety: alternative candidates are strictly verified against target title, release year (tolerance <= 1 yr), show type, and exact season/episode numbers before resolution;
- Resume preservation: `resumeAtSeconds` is forwarded without loss to the fallback candidate;
- Real-time status: player window displays honest, reassuring progress during failover (e.g. `Probando PelisflixHD…`);
- Issue handler recovery: mid-stream errors trigger immediate failover to the next candidate provider.

Regression coverage:
- ProviderHealthStatsTest
- ProviderFallbackCoordinatorTest

## 6. In-Frame Embedded Playback as Default

Playback occurs directly inside `MainFrame`'s `contentStack` under the `PLAYER` route by default.
- Selecting Play replaces the MainFrame content view with `EmbeddedPlayerView` without opening a secondary window or tab;
- Hitting Escape or clicking 'Volver a Streamflix' smoothly returns to the origin (Detail, Catalog, or Sports) with scroll position, season/episode selections, and focus preserved without refetching;
- Fullscreen applies directly to `MainFrame`, keeping controls as overlays that auto-hide without resizing the native video Canvas;
- Standalone/pop-out player exists only as an explicit secondary action;
- Native video surface, lifecycle, keyboard controls, and cleanup are managed with zero duplicate playback sessions.

## 7. Pop-out can return to the main window

- The top-right player action opens a separate window, and that window exposes **Volver a integrar en Streamflix**.
- When docking, the current media source, server, request identifier and last known time are carried to a new native in-frame surface. The mpv Canvas is never reparented while its native WID is active.
- Docking restarts the stream at the saved position. It is not guaranteed to be visually gapless and depends on the same upstream source remaining accessible.
- Never reuse a request ID from a stopped surface: `stopPlayback()` invalidates it. Obtain a fresh mpv request ID for each transfer, and run the IPC/media handshake outside the Swing event-dispatch thread.

## 8. Fullscreen versus maximization

- A fullscreen pop-out covers the **physical display bounds**, not `MAXIMIZED_BOTH` or taskbar-excluded work area.
- Exit restores the previous frame size, maximization state and Pin always-on-top preference.
- Windows GUI E2E verifies monitor bounds and round-trip window restoration.

## 9. Home featured banner and scrolling

- Home features several unique available movie/series titles rather than always picking the globally highest-rated series.
- The hero rotates every 12 seconds when shown and offers Previous/Next actions. Its height does not change when the selected item changes.
- Home's scroll viewport uses `SIMPLE_SCROLL_MODE`: never blit cached window pixels across the layered artwork while scrolling, because these can leave stale horizontal image fragments on Windows.
- Hero slides are mounted once per Home render; changing selection must not rebuild Home or shift Continue Watching.
- Native GUI E2E covers the fixed height, rotation, viewport mode and stable Home geometry. A user-visible Windows scroll recording is still required to close the original flicker report conclusively.
- After a Windows report confirmed tearing persisted in v1.4.4, Home vertical scrolling stopped using the custom 16-ms easing timer. Native wheel scrolling is required on Home; the frame-by-frame timer remains only for smaller horizontal rails. E2E verifies wheel motion changes the position immediately and does not create `streamflix.smoothScrollTimer` on the vertical scrollbar. Avoid reintroducing timer-driven full-Home repaints without genuine Windows frame-level validation.

Regression coverage:
- EmbeddedPlayerIntegrationTest
- MpvPlayerTest
- PlaybackFallbackTest
