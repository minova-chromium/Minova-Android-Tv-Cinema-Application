# Browse performance and polish checks

This work is local and unpublished. Public version/signing, logos, Plex artwork sources,
and the splash screen are unchanged.

## Changes

- Removed the catalog-wide artwork prefetch burst. Only two neighbouring posters are
  prefetched, sequentially, after focus settles for 800 ms. Moving again cancels that work.
- Shared, bounded Coil decode sizes for visible and prefetched artwork (400×600 posters,
  1920×1080 hero), retaining the previous decoded backdrop until the next image succeeds.
- Catalog readiness no longer waits for disk cache writes or Android TV channel publishing.
  Metadata cache disk operations run on IO, and cache namespaces separate authentication contexts.
- Browse state survives details/playback and tab changes: layout, genre, scroll, carousel,
  and focused title. Home shelves retain title keys when newly added content shifts positions.
- Settings → Playback → Customize home screen: reorder/hide discovery shelves and choose
  the cold-launch tab. Preferences are scoped to server and Plex profile UUID.
- Hold OK on a poster/Continue Watching card for title actions. Movies/episodes support
  Resume/Play from beginning; all titles offer Watchlist, watched state and details.
- Settings → Cinema lights → Configure Tapo lights → Test dim and restore. Only selected
  lights are tested. Originally off lights remain off. Every changed bulb is restored in
  a cancellation-safe cleanup with bounded retries and per-bulb results. Restore failure
  explicitly tells the user the original percentage to set in the Tapo app.
- Settings → Network shows in-memory aggregate artwork timings, cache hits and failures.
  Metrics never include URLs, tokens, account identifiers or titles.

## Run the regression suite

PowerShell from the repository root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\scripts\Test-MinovaTv.ps1
```

The script builds/tests `com.minova.cinema.debug` independently of the installed signed app.
It does not clear the production application's data. Promotional recording automation is
excluded. Android Studio does not need to be open; a connected emulator/device is needed.
With one connected device, the runner also leaves the debug APK installed and checks a
fresh process launch against a 5-second first-frame budget. This is launcher timing,
not Plex library readiness. The public app and its login remain separate.

Reports: `app/build/reports/tests/testDebugUnitTest/`,
`app/build/reports/androidTests/connected/debug/`, and `app/build/reports/lint-results-debug.html`.

Coverage includes 1,000-title grid traversal, repeated multi-title shelf changes, exact
return after a catalog insertion, tab restoration, long-OK behavior, settings navigation,
home layout persistence/profile isolation, and Tapo failure/cancellation cleanup.

The HTTP artwork fixture adds 250 ms response latency and checks eight images: cold load
under 3,500 ms, memory revisit under 750 ms, disk revisit under 1,500 ms, and zero extra
network requests on both revisits. The limits describe the controlled emulator fixture,
not a performance guarantee for arbitrary Plex servers or TVs.

## Live acceptance checks still required

Latest completed full pass (6 September 2026, Television_4K emulator): 19 unit tests,
17 instrumentation tests, no lint errors (43 debug / 41 release warnings remain), and a 2,240 ms cold
launcher first frame. The controlled artwork fixture also verified memory and disk
revisits without additional HTTP requests.

- Release candidate 2.8.2 (versionCode 38) was installed over 2.8.1 on the emulator with
  the established certificate and without clearing data. The existing Plex library opened
  successfully; cold release first-frame startup was 2,069 ms. The debug copy is now labelled
  "Minova Cinema (Test)" and retains separate storage.
- On the configured app, compare cold startup and repeated poster browsing with real Plex
  artwork; check Settings → Network timings. Full live performance acceptance is still manual.
- Test selected physical Tapo bulbs, including L630, through the new test button. Automated
  tests use fake bulbs and cannot verify the visual smoothness of a physical light.
- Verify resume/from-beginning and returning from actual media playback on the TV.
- No release should be published solely because a compile or fixture test succeeds;
  follow RELEASING.md before public distribution.
