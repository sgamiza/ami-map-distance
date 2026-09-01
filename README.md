# MapDistance

Record walking, running, or cycling with **system GPS** (works offline) and draw the route plus nearby addresses on **Amap**. Distance, elapsed time, pace, and speed stay on screen. After opening the app, tap **Start** to measure (Settings can turn on auto-start). Tap **End** at the destination. Once online, map tiles and street names backfill automatically.

Launcher display name: `阿米测距`. Stack: Gradle 7.5, AGP 7.4.2, JDK 17, Java, Material.

---

## 1. What it does

Open the phone, grant location, tap **Start**, and walk, run, or cycle. The screen keeps:

- Cumulative distance (m / km)
- Elapsed time (paused time, and standing still when **Stop counting when idle** is on, is excluded)
- **Steps**: counting starts at 0 when you tap **Start**, freezes on pause and auto-idle, then resumes. Same session as GPS distance, pace, and average speed. The measure page always shows **stride** in large type (this session if available, otherwise the last saved value). Today’s chip steps are all-day and will not match this session’s kilometres. Tap those rows for help. Needs Activity Recognition.
- Pace: time per kilometre, shown as `11 min 17 s/km` (same meaning as a watch face `11'17"`; about 5.3 km/h). Tap the per-km row for an explanation.
- Current, average, and max speed (default km/h; Settings or tapping the speed numbers cycles km/h, m/s, mph)
- Estimated calories (from weight in Settings; walk / run / bike coefficients differ. Cycling uses a regular bicycle estimate)
- Track polyline on Amap (arrows along the line for direction) plus start / current addresses (offline: draw first, then backfill tiles and names)
- Each session can have a name and from → to (for example “Home → Office”)
- **Multi-device sync**: same Wi-Fi / hotspot or nearby Bluetooth; or a shared cloud folder. Existing sessions merge by uid, no duplicates.
- **Backup and restore**: auto snapshot on leave after edits (min 10 minutes), optional monthly/weekly/daily check, SAF folder (can share with folder sync) or app `backups/`, keep 3/5/10/20 files, tap a file to overwrite-restore (snapshots current data first).
- **Checkpoints**: tap once at a place to store distance and time so far; measuring continues. Settings can turn on **auto checkpoints** (by measured elapsed time). Numbered markers on the map (manual vs auto colours), also in History. The checkpoint list still shows “since last”; `选两个打点` compares any two (distance, time, average speed, pace, steps, stride). `量两点` on the map picks any two track positions the same way. Old tracks without per-point steps interpolate along distance or estimate from last stride, and the dialog says so.
- **Arrival / interval alerts**: this-session km, this-session steps, every N km, every N steps, and today’s steps are five independent cards. Ringtone and speech switches are labelled per item (`这次公里铃声` vs `每公里铃声`). The pick button says which item it changes; the picker title repeats it. Unset items show `未单独选` instead of sharing another item’s name.
- **Battery**: from app open, “since opened” whole-device drain; after **Start**, “this session”. The lock-screen notification shows percent dropped this session (whole device, not the system “this app only” column)

Measuring continues on lock screen or when you switch apps (foreground service + notification). After a process kill, a draft restores the session. Offline, GPS distance, time, and pace still write to local SQLite.

---

## 2. Features

### 2.1 Measure page (default)

- **Auto-start on open** (**off by default**, since v1.8; old installs that had it on are not reused). Turn it on in Settings if needed.
- First launch shows a location-and-map explainer, then requests location, notifications, and Activity Recognition for steps.
- Top-left chip shows the **current mode only** (Walk / Run / Bike / Auto ▾). Tap it to pick from a list; after choosing, only that label stays so the map and Leaflet +/- are not covered. Locked while measuring. **Auto** does not need you to switch modes: chip steps → walking (cadence, steps, km, time, speed); GPS moving with almost no steps → vehicle (speed, distance, time: car / bus / bike). Map: green = walk, orange = vehicle. Arrival km/steps use the walk part only. Running uses the highest calorie coefficient; cycling is a regular-bike estimate (not e-assist). Auto calories use the walk part. Cycling does not estimate stride; Auto stride uses the walk part. Leaflet zoom sits under the chip (history fullscreen drops that offset).
- **Satellite / locate**: light-background, dark-text chips at the top-right of the map (v1.3 fixed a theme bug that painted the label the same green as the chip, looking like two empty squares).
- **Lock-screen background checks**: on open, battery optimization and system background limits; on Xiaomi, autostart can also be read. If still restricted, a dialog can jump to the matching system page. Settings shows status and buttons. The system does not allow this check at install time — only at runtime.
- GPS accuracy badge: points worse than the threshold are dropped so indoor jumps do not inflate distance.
- **Flyers**: walking faster than about 27 km/h, or a jump over 10 km/h within 30 m, is ignored and the anchor does not follow (older builds still moved the anchor after 5 seconds, so the next segment started from the wrong place). While measuring, the blue dot and place name use accepted points only, so a market does not suddenly become the neighbouring tower.
- **Stop counting when idle** (Settings, on by default): if you do not move about 8 m in about 12 seconds, **or GPS speed stays ~0 that long**, time, distance, and this-session steps stop (like pause, but the button still says Pause). Badge: “Stopped, not counting”. GPS jitter under ~1.2 m is ignored and does not restart the 12 s window. Counting resumes after about 12 m. Turn it off if you want wall-clock duration.
- **Wait for GPS before counting** (Settings, on by default, independent of the next item): after **Start**, elapsed time and this-session steps stay at 0 until a GPS fix meets the accuracy threshold (default 40 m). Network location only aligns the map. Badge: waiting for GPS.
- **Keep counting if GPS is lost mid-session** (Settings, on by default, independent of the previous item): if GPS later drops or accuracy stays worse than the threshold for about 8 seconds, time and this-session steps keep adding (distance still needs a point). Turn it off to freeze until a qualified GPS returns. Badge: GPS lost. Not the same as idle.
- Large distance plus elapsed / per-km (pace) / speed / average; then **stride** (always for walk/run; bike shows “stride not used when cycling”; Auto uses walk-part stride), then in **Auto** a split panel (**total**, then walk vs vehicle), then **today’s steps**, **this-session steps / km** (from Start; frozen on pause and auto-idle; Auto this-session steps are walk-only), calories, max speed, point count. Tap speed or average to cycle km/h → m/s → mph. Tap stride or step rows for help; if Activity Recognition was denied, tapping asks again. The numbers can **fold down** so more map is visible (Start / Pause / End stay); tap the slim strip to expand. Folded strip still shows **total** distance, time, and status. Choice is remembered.
- **Start / Pause / Resume / End**. If system location is off at Start, a dialog can open Settings (returns and auto-starts; or start anyway). End discards sessions under 15 m; otherwise a summary lets you fill **name** and **from → to** (for example “Home to office”). History can edit these later.
- **Checkpoints**: while measuring (including pause), orange **Checkpoint** stores cumulative distance and time without stopping. Auto checkpoints can be enabled in Settings. Manual and auto markers use different colours. The list still shows distance since the previous mark; `选两个打点` compares any pair.
- Blue arrows along the track show direction; density reflows on zoom.
- `量两点` (top-right chip, also in history view): tap two checkpoints or any two places on the track. Shows distance along the line, time, average speed, pace, **steps and stride** (always shown). Missing per-point steps are interpolated in memory from known anchors or the session total; otherwise estimated from last stride and labelled `按这段距离估`. Orange highlight between start and end. Checkpoint list `选两个打点` does the same without tapping the map.
- Remaining distance to the arrival goal is shown. On arrival, ringtone and speech run about one minute (separate from the ongoing measure notification). Stop with `知道了` or by returning to the app.
- Notification: two lines while measuring (distance+time / steps+speed). Auto shows walk km vs vehicle km and cadence when walking. **Pause / Resume**. **End…** opens the app for confirmation; it does not stop in one tap. Tapping the notification returns to Measure.
- **Offline**: start and end without mobile data / Wi-Fi. Badge “offline recording”. The polyline still draws; visited Amap tiles are cached for later offline use. Address shows “place name after online”; Amap reverse geocode fills in when the network returns.

### 2.2 History

- Newest first: custom name, from/to, mode, distance, time, pace, steps. Auto rows show **total**, walk km, and vehicle km. Unnamed rows still show Walk/Run/Bike/Auto; empty from/to uses Amap street names. Sessions that arrived via nearby/folder sync are titled `[来自同步]`; sessions brought in by backup overwrite-restore are `[来自备份]`. Measured on this phone have no tag. Search understands 同步 / 备份.
- **Show actual movement** (top switch, same as Settings, on by default): list, track view, and pace use distance/time after flyers and idle are removed. Does not rewrite the database. Turn off for raw wall-clock. Changed rows note “flyers/idle removed” in the subtitle. History steps stay the full chip count (idle segments cannot be split afterwards).
- **Search**: name, place, steps, km, date, walk/run/bike/auto, `车程`, etc. Space-separated terms must all match.
- **Sort**: time / km / steps / speed. Default descending (time newest first); tap again to reverse. Selection is remembered.
- **Rename**: name, from, to. Amap backfill does not overwrite handwritten values.
- **View**: fullscreen track (title, tabs, and bottom bar hidden) with **start/end clock**, **total elapsed**, walk time and vehicle/bike time, **total** distance, steps, and **stride** at the bottom. Auto also lists walk vs vehicle km. The info banner can **fold** (`收起，多看地图`); folded line keeps start–end and total time; actions (two-point, back) stay. `量两点`: tap start then end on the track or on numbered checkpoints to see that segment’s distance, clock, average speed, pace, steps and stride (orange highlight). Stride is cleaned distance / chip steps, so it updates after you drop or restore points (steps stay the chip count). Per-session **speed threshold**: dialog prefills the value in use, with **km/h / m/s** toggle. Faster points count as GPS jumps and are excluded from distance/time/track. **Drop points** (hidden flag only — not deleted, restorable): after Drop, pinch or + can zoom past the usual map-tile stop so nearby numbered dots separate; tap a numbered marker to hide/restore immediately; the map **stays put** (does not jump back to the whole track). **Drop range** tap start then end; **Suspect list** numbers match the map. Distance, time, pace, average, calories, and stride recompute. Back returns to the **history list**. Cannot open while a measure is running.
- **Checkpoints**: if the session has any, a Checkpoints action on the row lists each point’s distance and time; `选两个打点` compares any pair, not only neighbours.
- **Delete**: confirm twice.

### 2.3 Stats

Filter time / walk-run-bike-auto at the top; switch **Cards / Charts** below (charts default). Data comes from saved history (not system fitness). With history cleaning on, cards and charts use post-clean distance/time.

- **Cards**: session count / km / time, steps and calories, walk/run/bike/auto split, **true walking** (manual walk + Auto walk part; vehicle km listed separately), today/week/month/year, vs previous period, pace and speed, stride, personal records, latest session, habits and gaps, streaks, hour/weekday, month ranking, calendar year, distance/time buckets, percentiles, weekly load, multiple sessions per day, 10k-step days / 5 km days, frequent places, checkpoints and track points, whole-device battery.
- **Charts** (Chart.js, bundled offline): capsule sections **Overview / Trend / Pace / Steps & calories / Habits / Buckets**. Includes per-session km, cumulative, weekly/monthly, average and max speed, speed distribution, steps, stride, calories, weekday, hour, gap heatmap, distance/time buckets, and more. Fullscreen moves that chart plus its container over the title, tabs, and filters; Close or system Back restores. Pinch-zoom. Same filters as cards.
- **Share**: current-filter card stats as text.
- Empty history prompts you to measure.

### 2.4 Settings

- Auto-start measuring on open (off by default)
- **Stop counting when idle** (on by default): standing in a shop or at a light does not add time/distance/this-session steps
- **History and stats drop flyers and idle** (on by default): list/stats use cleaned numbers; the switch itself does not rewrite the database. Per-session speed threshold and manual dropped points write to that row and recompute.
- **Speed unit**: km/h, m/s, mph. Tapping speed/average on Measure also cycles. Pace is always min/s per km, not a speed unit.
- **Steps**: system `TYPE_STEP_COUNTER`, not vendor Health. With a band, the two will not match.
- Weight (jin, for calories, default 130)
- Drop points worse than N metres accuracy (default 40)
- **Wait for GPS before counting** (on by default): Start does not run elapsed time or this-session steps until GPS accuracy is good enough. Separate from the next switch.
- **Keep counting if GPS is lost mid-session** (on by default): after counting has started, about 8 s without qualified GPS still adds time and this-session steps. Turn off to freeze that stretch. Separate from “wait for GPS before counting”.
- **Auto checkpoints**: every N minutes of measured elapsed time (1–30)
- **Arrival alerts**: this-session km and steps, each in its own card (empty = off). Ringtone/speech switches are labelled `这次公里铃声` vs `这次步数铃声`. Pick buttons `选这次公里` / `选这次步数`; picker title repeats which item. Unset shows `未单独选`, not another item’s tone name.
- **Interval alerts**: every N km, every N steps, separate cards from arrival. Buttons `选每公里` / `选每步` so this cannot overwrite the km-goal ringtone.
- **Today’s steps alert**: chip-step target; card and button `选今日步数`, separate from this-session steps.
- Amap Web service key (reverse geocode; replace with your own)
- **Lock-screen background**: on open, battery optimization and background limits; Xiaomi autostart. Still restricted → dialog and jump to the system page. Xiaomi: unrestricted power policy and recents lock; Huawei: manual app launch with all three items on. Install-time check is not allowed — runtime only.
- **Download offline maps**: 1 / 3 / 8 km around current location, or the current map viewport. Download on Wi-Fi before a trip so Amap basemap works offline. Settings shows cached tile count and size; can clear.
- **Backup and restore**: Settings → Backup and restore. Auto on by default; leave-the-app snapshot after dirty edits (10 min min interval); monthly/weekly/daily timer; SAF folder (shared with folder sync) or app `backups/`; keep 3/5/10/20; tap a file to overwrite-restore after a safety snapshot.
- **Multi-device sync**:
  - **Nearby sync (recommended)**: open Nearby sync in Settings on each phone. Same Wi-Fi, or one hotspot and the other joined; LAN HTTP port 17866 exchanges track JSON; Bluetooth RFCOMM also works. Tap the peer, or type the IP on their screen. Nickname this device; long-press a peer to add a local note. Listening only while the dialog is open. No public internet, no account. In-progress measures are not synced.
  - **Folder sync (fallback)**: each phone picks the same mutually visible folder (Nutstore and similar); each writes `sync_<device-id>.json`. Tap Folder sync to merge others. Same uid is skipped; deletes do not wipe other phones.

### 2.5 How the map is built

1. Local Leaflet + **Amap raster tiles** (`webrd0{1-4}.is.autonavi.com`). Tiles intercepted in the WebView go to `cache/tiles/`, cap about 5000.
2. Settings can **pre-download** current location or viewport; browsed areas cache automatically. Missing tiles show a blank map; **the polyline still draws**.
3. System GPS is WGS-84; convert to GCJ-02 (Mars) before drawing on Amap.
4. Addresses use Amap Web `v3/geocode/regeo`. Offline skips; when online, `TrackEngine.backfillWhenOnline()` fills the current measure and up to 12 history rows.

The track itself does **not** use the Amap location SDK. Distance uses system `GPS_PROVIDER`, fully offline.

### 2.6 Where steps come from; why vendor Health stays alive

MapDistance does **not** read the Xiaomi Mi Fitness / Health database. Side-by-side comparison will disagree because:

- All-day steps in vendor Health mainly come from the **MIUI system service** `StepCounterManagerService` (system process uid 1000), which listens to the chip `STEP_DETECTOR_WAKEUP`. Apps then read via vendor permission `miui.permission.READ_STEPS`. Third-party apps cannot install that permission.
- On-device observation: the Health **UI process is often not running**. What stays alive is the `:device` subprocess (band/watch), plus `BootCompletedReceiver`, `KeepAliveJobSchedulerService`, and `KeepAliveService`. Those keep the wearable linked; they are not what makes the step chip work — the chip accumulates while the SoC sleeps.
- So **step counting does not need the app in the foreground 24 hours**. GPS distance does need a foreground service. MapDistance only keeps GPS with a foreground service after **Start**; steps use the cumulative counter. Kill and reopen the same day still fills the gap. Since v1.15 it tries to snapshot the counter at midnight as today’s origin; if Xiaomi blocks the alarm, midnight until first open can still undercount. With a band the two sources differ anyway.

---

## 3. Stack and dependencies

| Item | Value |
|---|---|
| Language | Java 8 |
| minSdk / targetSdk / compileSdk | 24 / 33 / 33 |
| Gradle | 7.5 |
| Android Gradle Plugin | 7.4.2 |
| JDK | 17 (Adoptium path pinned in `gradle.properties`) |
| UI | AndroidX AppCompat 1.6.1, Material 1.9.0 |
| Map | Leaflet 1.9.4 + Amap tiles (bundled in assets; tiles cached on device) |
| Charts | Chart.js 4 + date-fns adapter + zoom (bundled in assets, offline) |
| Location | `LocationManager` GPS (offline) + network (map alignment only) |
| Steps | `Sensor.TYPE_STEP_COUNTER` + `ACTIVITY_RECOGNITION` (Android 10+) |
| Storage | SQLite `tracks.db` + SharedPreferences draft + `cache/tiles/` |
| Package | `com.example.mapdistance` |
| Display name | `阿米测距` |
| Current version | versionCode 44 / versionName 1.43 |

---

## 4. Build and install

### 4.1 Prerequisites

- JDK 17 via `JAVA_HOME` or `org.gradle.java.home`
- Android SDK: `sdk.dir` in `MapDistance/local.properties`
- Behind a corporate proxy: `YOUR_PROXY:8080` in `gradle.properties`

### 4.2 Build

Local Gradle can pick the wrong directory; **pass `-p` for the project root**:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "C:\path\to\Android\sdk"
$env:HTTP_PROXY = "http://YOUR_PROXY:8080"
$env:HTTPS_PROXY = "http://YOUR_PROXY:8080"
$PROJ = "PROJECT_ROOT\MapDistance"
.\gradlew.bat -p $PROJ assembleDebug --no-daemon
```

Or run `.\gradlew.bat assembleDebug --no-daemon` inside `MapDistance`. Add `--offline` when dependencies are already cached.

Output: `MapDistance\app\build\outputs\apk\debug\阿米测距.apk`

### 4.3 Install

```powershell
adb install -r "PROJECT_ROOT\MapDistance\app\build\outputs\apk\debug\阿米测距.apk"
adb shell am start -n com.example.mapdistance/.MainActivity
```

Xiaomi / Redmi USB installs often prompt “Allow install?” on the phone; without tapping, adb reports `INSTALL_FAILED_USER_RESTRICTED`. Allow, then run install again.

### 4.4 Use

1. Open the app → accept the explainer → allow location (precise recommended) and Activity Recognition (steps).
2. If still on a battery-saver list or Xiaomi autostart is off, a lock-screen-background dialog appears; **Fix** jumps to the system page.
3. Tap **Start**. If system location is off, a dialog can open it; returning starts automatically. Works offline. Status bar shows Walking / Running / Cycling / Auto. **Auto** splits walk vs vehicle by the step chip so you can leave it on all day. Standing still can badge “Stopped, not counting” (disable in Settings). At a landmark tap **Checkpoint**; measuring continues; later you can see that point’s distance and time. Tap `收起，多看地图` on the bottom bar to fold the numbers (Start / Pause / End stay); tap the slim strip to expand.
4. Walk, run, or cycle to the destination; watch distance and pace; walk/run also show **this-session steps and stride** (bike does not estimate stride). Tap **End**. Fill name, from, to in the dialog. Today’s steps estimate kilometres from the latest stride.
5. History: search; sort by time/km/steps/speed. Top checkbox **Show actual movement** drops flyers and idle. **View** fullscreen track; at the bottom change this row’s speed threshold (km/h or m/s) or drop bad points in bulk/range (hidden, restorable; numbers recompute). Back returns to the list.
6. **Stats**: filter time or walk/run/bike; **Charts** for trends (capsule sections, fullscreen fills the screen), **Cards** for numbers, share as text. With cleaning on, use post-clean numbers.
7. Settings **Lock-screen background** can re-check battery optimization, autostart, power policy, app launch.
8. **Download maps before a trip**: Settings → Download offline maps → 1/3/8 km or current viewport. Use Wi-Fi. After that, basemap works offline.
9. **New phone / several phones**: Settings → Nearby sync. Both open the window, same Wi-Fi or hotspot, tap the peer; if not discovered, type the IP. Or pick the same cloud folder and tap Folder sync.

---

## 5. Layout

```
ami-map-distance/
├── README.md
└── MapDistance/                  # Gradle project root (includes gradlew.bat)
    ├── settings.gradle
    ├── build.gradle
    ├── gradle.properties         # JDK 17 + proxy
    ├── local.properties          # sdk.dir=C:\\Android\\sdk
    ├── gradle/wrapper/
    └── app/
        ├── build.gradle
        └── src/main/
            ├── AndroidManifest.xml
            ├── assets/
            │   ├── map.html      # Amap JS / Leaflet dual engine
            │   ├── leaflet.js
            │   ├── leaflet.css
            │   ├── charts.html   # stats chart sections
            │   ├── chart.umd.min.js
            │   ├── chartjs-adapter-date-fns.bundle.min.js
            │   ├── chartjs-plugin-zoom.min.js
            │   └── hammer.min.js
            ├── java/com/example/mapdistance/
            │   ├── App.java
            │   ├── MainActivity.java
            │   ├── TrackEngine.java    # measure core: filter, accumulate, draft
            │   ├── TrackClean.java     # flyers/idle: shared by live measure and history
            │   ├── TrackService.java   # foreground service + GPS
            │   ├── TrackStore.java     # SQLite history
            │   ├── TrackSession.java
            │   ├── TrackPoint.java
            │   ├── Checkpoint.java     # mid-session points: cumulative distance / time
            │   ├── CoordTransform.java # WGS-84 ↔ GCJ-02, Haversine
            │   ├── AmapClient.java     # Amap reverse geocode (skip offline)
            │   ├── Net.java            # online check
            │   ├── TileCache.java      # Amap tiles on device
            │   ├── OfflineMapDownloader.java  # pre-download basemap by area
            │   ├── KeepAlive.java      # battery opt / Xiaomi autostart / Huawei app launch
            │   ├── NearbySession.java  # nearby Wi-Fi/Bluetooth
            │   ├── SyncPack.java       # track JSON pack and merge
            │   ├── SyncFolder.java     # shared folder I/O
            │   ├── Backups.java        # JSON snapshots, rotate, restore
            │   ├── BackupAlarm.java    # periodic backup heartbeat
            │   ├── BootReceiver.java   # re-arm backup alarm after boot
            │   ├── PhoneNotes.java     # this device / peer nicknames
            │   ├── Prefs.java
            │   ├── Formats.java
            │   ├── HistoryQuery.java   # history search and sort
            │   ├── TrackStats.java     # stats cards
            │   ├── TrackCharts.java    # chart JSON
            │   ├── ChartBridge.java    # WebView injects stats
            │   ├── GoalAlerts.java     # arrival / interval / today step alerts
            │   ├── SpanStats.java      # any two checkpoints or track points (steps interpolated)
            │   ├── AlertKind.java      # five alert kinds
              │   ├── AlertNotify.java    # alerts: per-kind ringtone+speech ~1 min
              │   ├── TonePick.java       # system alarm/notification/ringtone list
            │   ├── StepMidnight.java   # midnight step-counter snapshot
            │   ├── BatterySnap.java    # whole-device battery (session / since open)
            │   └── StepSensor.java     # system step chip, today’s steps
            └── res/                    # layouts, icons, theme (primary #0F766E)
```

---

## 6. Measurement rules (for matching numbers later)

- Distance: Haversine in WGS-84 between adjacent **accepted GPS points**.
- Dropped: accuracy worse than threshold; jitter under 1.2 m and interval < 2.5 s; flyers (walk about >27 km/h, or within 30 m still >10 km/h; run/bike thresholds higher; **per-history row can raise the cap**, e.g. 54 km/h, 10 m/s). Ignoring a flyer does not move the anchor. Manually dropped points (JSON `x=1`) are out of distance/track and can be restored.
- Time: wall-clock only while “in progress” and moving. Pause adds none. With **Stop counting when idle**, ~12 s without ~8 m **or GPS speed staying ~0** enters idle (no time, no distance, this-session steps frozen). Accepting a GPS jitter point does not restart that window. Resume after ~12 m if it does not look like a flyer. With **Wait for GPS before counting**, time and this-session steps do not start until a GPS fix is within the accuracy threshold. With **Keep counting if GPS is lost** off, after a qualified GPS has been seen, ~8 s without one freezes time and this-session steps until GPS is good again. The two GPS switches are independent; idle is a third switch.
- History cleaning (on by default): replay the same flyer rules, then drop idle with a 12 s window. Per-row speed threshold and manual drops write to the DB and recompute distance/time/calories. Steps stay the chip values.
- Current speed: prefer GPS `speed`, else last displacement / time.
- Pace: time / distance as `min s/km` (e.g. 11 min 17 s/km ≈ 5.3 km/h). Distance < 20 m, or per-km < 36 s / > 60 min, shows `--` (cycling can still show pace).
- Speed unit: `Prefs.speedUnit` stores `kmh` / `ms` / `mph`, default km/h. Notification, Measure, and end summary share it.
- Steps: `TYPE_STEP_COUNTER` boot cumulative. This session accumulates only while **in progress, not auto-idle, not waiting for the first qualified GPS, and not in a GPS-lost freeze**. Today prefers `current − midnight snapshot`; if midnight was missed, from first open, marked “may undercount”. Stride = GPS metres / steps; always shown on Measure (last saved if not enough yet, default 70 cm).
- Arrival alerts: five independent switches; ringtone/speech/file stored per kind. Fire once when this session `distanceM` / `steps` hit the target (empty = off). Intervals use floor cumulative. Today’s goal uses chip today-steps, once per day. If ringtone or speech is on, loop ~60 s; `知道了` / swipe / return to app stops immediately.
- Stats: only saved `TrackSession` rows (ended and ≥15 m). Window by `startMs`. 10k-step / 5 km days sum that day’s sessions, not system all-day steps. Charts use the same filtered set, without track points. Cleaning on → post-clean distance/time.
- Track arrows: Leaflet triangles along the line by zoom spacing, heading to the next point.
- Calories: `weight_jin/2 × distance_km × coefficient`. Walk 0.75, run 1.036, bike 0.40 (regular bicycle, not e-assist).
- Track points go into SQLite as JSON; about every 8 s a draft is written to SharedPreferences while measuring.

---

## 7. Known limits

- Indoors and dense towers: GPS is poor, distance short or stuck at 0 until outdoors. Flyers are ignored; a brief lock onto a nearby building no longer moves the blue dot and place name. A long lock on a wrong position (tens of seconds, still not moving) can still look like a move: lower that row’s speed threshold in History, or Drop the bad stretch; the track breaks at the jump and numbers recompute.
- Network location only aligns the map first; **it is not added to distance** (domestic network fixes are often already GCJ-02; converting again shifts them).
- Amap JS key is reverse geocode only; basemap is tiles. Offline, basemap is blank or cached only; **numbers stay accurate**.
- Calories are a rough estimate, not lab METs.
- Xiaomi / Huawei may still stop GPS: lift battery optimization; Xiaomi also needs autostart + unrestricted power + recents lock; Huawei needs manual app launch with all three items on. Huawei app-launch status cannot be read; only a jump to the system page.
- Checkpoints store **cumulative distance and time from the start to that point** (pause adds no time). Manual/auto counted separately. No GPS → cannot checkpoint.
- Multi-device sync sends saved tracks only, not in-progress measures. Same session deduped by uid; deletes do not wipe other phones. Nearby sync listens on 17866 or Bluetooth only while the dialog is open; nothing goes to the public internet.
- Snapshots are `ami-backup-date-time-count.json` (same JSON pack as sync). A picked folder is preferred; otherwise app `backups/`. Overwrite-restore snapshots current data first. Cannot restore while measuring.
- No accounts; uninstall drops local history and tile cache (copies already synced to other phones, or backups in a user folder, remain).
- Airplane mode on some devices also turns GPS off; then even distance cannot be recorded — system location must stay on.
- Steps do not read vendor Health. A band, or a blocked midnight alarm plus a late first open, makes today’s number lower than Health (often a thousand-plus steps in that gap). Shaking the phone indoors counts steps without GPS distance, so stride looks short.
- SQLite `tracks.db` version 9: `sessions.origin` (`local` / `sync` / `restore`) so History can mark `[来自同步]` / `[来自备份]`. Pre-v1.39 rows stay `local`.
- Stats ignore an in-progress measure; 10k-step days only count sessions saved in this app.
