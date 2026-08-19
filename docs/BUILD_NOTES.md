# Jarvis app — build notes

Companion to `jarvis-app-plan.md`. Records what the plan deliberately refused to
pin (§2: "do not pin numbers from this document"), plus anything discovered
while building that the plan could not have known.

---

## 1. Resolved toolchain

Machine: `joy` (Windows 11). JDK comes from the Android Studio bundle —
there is no separate JDK on PATH.

```
JAVA_HOME = C:\Program Files\Android\Android Studio\jbr    (OpenJDK 21)
Android SDK = C:\Users\ahmed\AppData\Local\Android\Sdk
```

| component | version | note |
|---|---|---|
| Gradle | 9.7.1 | wrapper, distribution SHA-256 pinned in `gradle-wrapper.properties` |
| AGP | 9.3.1 | latest stable; 9.4.0 was still rc at the time |
| Kotlin | 2.4.10 | see §2 — **not** applied as a plugin |
| KSP | 2.3.11 | KSP now versions independently of Kotlin |
| Compose BOM | 2026.08.00 | every Compose artifact takes its version from here |
| Hilt | 2.60.1 | works with AGP 9 + KSP2 |
| `compileSdk` | 37 | forced: current AndroidX refuses to compile against 36 |
| `targetSdk` | 36 | behaviour opt-in, moved deliberately and separately |
| `minSdk` | 31 | per plan §2 — `canScheduleExactAlarms()` exists from 31 |

`compileSdk = 37` was not a preference. `androidx.core:core-ktx:1.19.0` and
`androidx.compose.ui:ui-android:1.12.0` both hard-fail below it. AGP downloaded
the platform itself; there are no command-line tools installed on this machine.

Build from the shell with:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
```

## 2. AGP 9 has built-in Kotlin — the one real migration surprise

AGP 9.0 applies the Kotlin compiler itself. Applying
`org.jetbrains.kotlin.android` **fails the build outright** with "no longer
required for Kotlin support since AGP 9.0".

Two consequences worth knowing before touching `build.gradle.kts`:

- The `kotlin-android` plugin is absent from `app/build.gradle.kts` on purpose.
  Do not add it back. The other Kotlin plugins — `plugin.compose`,
  `plugin.serialization`, KSP — are still applied normally.
- AGP pins its own KGP version, which is **older** than the catalog's. The
  `buildscript { }` block in the root `build.gradle.kts` raises that pin. Delete
  it and the project silently compiles against AGP's bundled Kotlin instead of
  2.4.10, which is the kind of drift that shows up as an inexplicable compiler
  error six weeks later.

## 3. Contract items for the gw03 side

§13 of the plan already lists the open questions. Phase A added two, both
discovered by writing the types out in full:

### 3.1 `Confirm` needs a `status` (blocking for §5.3)

§4.5 defines the confirm component as `proposal_id` + `summary`. That is enough
for a **live** card and not enough for **history**: §5.3 requires a resolved card
to stay visible in the message stream in its resolved state, and with only those
two fields the app cannot tell a card it already accepted from one still waiting.

`agent.proposals.status` already holds exactly this
(`pending|confirmed|rejected|superseded`), so it costs the gateway nothing.

```jsonc
{ "type": "confirm", "proposal_id": "9f2c", "status": "confirmed", "summary": { … } }
```

The app defaults it to `pending` when absent, so a gateway that has not added it
yet still parses — it just renders every historical card as live.

### 3.2 `GET /sessions/{id}/messages` has no defined message shape

§4.4 names the endpoint and its paging parameters but never says what a message
looks like. The app assumes:

```jsonc
{ "messages": [ { "id": 41, "role": "assistant", "text": "…",
                  "components": [ … ], "created_at": "2026-08-19T22:00:00Z" } ],
  "has_more": true }
```

`components` on a persisted assistant message is the load-bearing part — it is
the same array `AgentResponse` carries, and it is what makes scrolling back show
the cards rather than bare text.

### 3.3 Still open from §13

- `GET /occurrences/upcoming` does not exist in the parent plan's API list. No
  reminders without it.
- `min_supported_app` / `current_app` from the gateway (§10.3).
- Off-tailnet: everything here assumes Tailscale-only.

## 4. Deviations from the plan's package layout

§2 lists `core/{network,data,di,time}`. Two additions:

- `core/model` — the wire types. They are shared by `data` and `network` and
  belong to neither.
- `core/ui` — `LoadState` only. It is UI vocabulary, not data.

`core/time` exists and is empty until phase E needs it.

## 5. Fonts

`res/font/outfit_variable.ttf` (Outfit) and `res/font/inter_variable.ttf`
(Inter), both SIL Open Font License — licences in `docs/licenses/`. Variable
fonts, so one file covers every weight; minSdk 31 is well above the API 26 floor
for font variation settings.

Bundled rather than downloadable, per §2: downloadable fonts need Play Services
and can silently fall back, and a brand face that sometimes does not load is
worse than no brand face.

Inter's file is ~877 KB because it ships every script it supports. Not worth
subsetting for a single-user sideloaded app; revisit only if APK size ever
becomes a real complaint.

## 6. Device verification

Target device is attached and phase A is verified on it:

```
SM-S918B (Galaxy S23 Ultra) · Android 16 · API 36 · adb RFCW20Z4AND
```

Install and drive it from the shell:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug
adb shell am start -n com.ar13x.jarvis.debug/com.ar13x.jarvis.MainActivity
adb shell cmd uimode night yes    # or: no
adb exec-out screencap -p > shot.png
```

Note the debug build installs as `com.ar13x.jarvis.debug` — `applicationIdSuffix`
keeps it side-by-side with a release build rather than replacing it.

Three defects were found this way and could not have been found any other way:

1. The `awaiting` indicator drew `rowFill` rather than the accent, so the one
   status meant to draw attention had a near-invisible dot in both themes.
2. Cards vanished into the ground in dark. A drop shadow needs something darker
   to cast onto; on a near-black ground it does nothing. Dark now uses a
   hairline, light keeps the shadow.
3. The noise dither was clipped to the wash, leaving a visible horizontal seam
   where the texture stopped against flat ground.

Still worth an eye on a real panel as the app grows: gradient banding in dark
(the wash now carries more dither than light for exactly this reason), and
whether `BrandTintDark` stays readable as the awaiting wash once real rows sit
in it.
