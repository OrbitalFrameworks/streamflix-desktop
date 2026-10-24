# Streamflix Desktop — evidencia ejecutable Windows verificada

**Fecha:** 2026-10-24. **Estado:** prueba funcional reproducida; no equivale a certificación estética integral ni publicación de release.

## Fuente de verdad

- GitHub PR #14: https://github.com/OrbitalFrameworks/streamflix-desktop/pull/14 (**MERGED**).
- Commit integrado en main: `7b2dd10b97355ba8fd89c1ddca72c15a8eaefd90` (squash).
- Commit exacto sometido a CI: `474baf46b1ed65a635cfdef3df13a9936f75e10d`.
- CI para ese commit: https://github.com/OrbitalFrameworks/streamflix-desktop/actions/runs/40115489593
- Jobs obligatorios `Windows tests` y `Linux compile/tests`: PASS.
- Artefactos de GitHub Actions: `windows-native-gui-screenshots-474baf46b1ed65a635cfdef3df13a9936f75e10d` y `windows-native-gui-report-474baf46b1ed65a635cfdef3df13a9936f75e10d`.

El informe `manifest.json` descargado de Actions registra `os=Windows Server 2025`, `isHeadless=false`, `nativeGuiVerified=true`, `nativePlaybackVerified=true`, `screenshotsCount=8`, `verdict=PASS`. Se comprobaron los SHA-256 de las ocho PNG contra el manifiesto original. Las capturas Home corresponden realmente a 1480×900, 1280×800 y 1024×768 y no fueron recortadas. El reproductor integrado mostró vídeo sintético mpv `av://lavfi:testsrc2` y el tiempo de reproducción llegó a unos 2,04 segundos.

## Alcance y límites explícitos

- **Sí validado:** compilación y 31 suites Java 17; navegación y ventanas de Home, búsqueda, ficha, ajustes, player en MainFrame y restauración; reproducción sintética mpv y evidencia visual del runner Windows.
- **No validado por esa prueba:** reproducción de contenido comercial real, calidad GPU/audio en el ordenador del usuario, proveedor externo en vivo, biblioteca real completa o acabado visual premium de todos los estados.
- El catálogo E2E utiliza **fixtures** sin posters licenciados. El fallback gráfico abstracto del producto es deliberado y no debe confundirse con fotografías, artwork oficial ni un catálogo de producción.
- La mejora editorial es un progreso verificable frente a espacios vacíos; **no** implica aprobación estética final del usuario.
- No hubo publicación de una release nueva, instalador estable ni actualización automática. La release estable pública permanece en 1.3.9.

## Evidencias antiguas no autoritativas

Los directorios `docs/screenshots/`, `docs/e2e-evidence/gui-e2e-remediated/` y `docs/candidate-evaluation/` contienen materiales históricos de runs anteriores, incluidos reportes y capturas Linux y claims de QA no corroborados. **No deben utilizarse para afirmar que una captura es Windows nativa o que existen controles específicos de Ajustes**. Ante contradicción prevalecen los artefactos descargables del run Windows indicado y el código probado por ese SHA.

La diferencia entre `PASS` técnico y aceptación de misión debe permanecer explícita: que Java/GUI/mpv funcionen en los fixtures no demuestra por sí solo que el diseño visual global satisfaga el objetivo.
