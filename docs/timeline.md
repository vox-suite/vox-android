# Android Timeline

Android follows the desktop day-cache flow described in `vox-core/docs/timeline.md`.

- Fetch `/v1/timeline/events/days` for the visible range using the device time zone. Keep summary counts, category counts, and revisions independently for each group filter and day.
- Skip empty days and reuse event pages whose day revision is unchanged. Load changed days with up to six requests in parallel. Every event query requests 30 events.
- Each day owns its cursor. Paging appends server-ordered events and deduplicates by event id. A changed revision replaces the first page and resets that day's cursor.
- Day starts with two neighboring days on each side. Its hourly horizontal axis adds three days near either edge, up to 30 days. Prepending days preserves the position on the axis. Approaching a loaded day's oldest event requests the next page. Explicit per-day paging buttons remain available.
- Week loads summaries and first pages for non-empty days. Month renders counts and category dots as soon as summaries arrive, then prefetches events in seven-day batches with three requests at a time.
- Live timeline updates and reconnection signals debounce for 350 ms. Returning to the foreground also rechecks summaries. Groups load once and are persisted for cached rendering.
- Save after 1.5 seconds of inactivity to private, non-backup app storage: `noBackupFilesDir/timeline/<hashed user id>.json`. Keep the newest 90 day/filter entries and 800 summaries. Restore before contacting the server; reject snapshots for a different time zone. These are plain JSON files.
- Sign-out deletes the cache and clears registered in-memory stores. A session generation prevents delayed responses, hydration, or writes from restoring cleared data. Signing in again creates a fresh ViewModel even for the same user.

Implementation: `timeline/TimelineDayStore.kt`, `TimelinePersistence.kt`, `TimelineViewModel.kt`, `TimelineApi.kt`, `TimelineScreen.kt`, and `PlanningTimeline.kt`.

Live database and authenticated device behavior require separate validation.

# Pulse and Spaces

Both libraries restore an account-scoped snapshot before checking the server. Pulse checks chart count and data revision, pages the chart list, and fills individual chart results with three concurrent requests and loading placeholders. Spaces checks count and revision, then requests summary rows without agent specifications. Live Spaces events reload the list or selected graph directly. Sign-out clears all three disk caches and cancels their pending work.

Spaces uses dependency columns, saved node positions, straight connectors, stop and retry controls, and node-targeted chat. Creation starts with an intent and derives the title. Empty Spaces and Pulse pages follow the desktop layered illustration and wireframe heart.

# Voice and app control

Android registers its foreground device with app-control capability, maintains the authenticated Core device socket, and sends that device ID when requesting a single-use voice ticket. It connects to Bridge's `/bridge/desktop/voice` endpoint using the same start, binary mu-law audio, clear, playback mark, and stop protocol as desktop. Muting discards microphone packets.

The device socket supplies current page, selected resources, node IDs, timeline filters, and a revision. Commands reject expired or stale context, validate resource ownership, and deduplicate command IDs. Space mutations use Core's durable action receipts. Pulse creation uses an idempotency key. Android supports navigation, spending filters, Spaces and Pulse actions; desktop terminal commands return an unsupported-action error. Foreground exit and sign-out stop the links and cancel pending commands.
