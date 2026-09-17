# Publishing Color Zen to Google Play

End-to-end checklist for taking this repository from source to a live Play
listing. Everything below matches what the code actually does — if a Play
Console field is listed here with a value, that value is enforced somewhere in
the build or the sources.

---

## 0. What you are publishing

| Field | Value | Where it is enforced |
|---|---|---|
| Package / application id | `com.colorzen.puzzle` | `app/build.gradle.kts` |
| versionCode | `1` | `app/build.gradle.kts` |
| versionName | `1.0.0` | `app/build.gradle.kts` |
| compileSdk / targetSdk | `36` (Android 16) | `app/build.gradle.kts` |
| minSdk | `23` (Android 6.0) | `app/build.gradle.kts` |
| Artifact type | **AAB only** (`bundleRelease`) | CI workflow + this guide |
| 64-bit | No native code at all → ABI-neutral | `app/build.gradle.kts` comment |
| Permissions | `com.android.vending.BILLING` only | `AndroidManifest.xml` |
| Internet permission | **none** | `AndroidManifest.xml` |
| Ads | none (no ad SDK present) | dependencies |
| Music | none (SFX only, mutable) | `core/SoundManager.kt` |
| Languages | English (default), Arabic (RTL) | `res/values-ar`, `res/xml/locales_config.xml` |
| Content rating target | Everyone | see §5 |
| Data collection | none | see §6 |

Play requires new apps to target API 34+ and, since **31 August 2026, API 36**.
This build targets 36.

---

## 1. Build the bundle

Locally (needs JDK 17 + Android SDK 36):

```bash
./gradlew bundleRelease          # unsigned-or-debug-signed AAB
# or, with keystore.properties present (see keystore.properties.example):
./gradlew bundleRelease          # signed with your upload key
```

The artifact lands in `app/build/outputs/bundle/release/app-release.aab`.

In CI, push to the branch and download the `colorzen-release-aab` artifact from
the **Build AAB** workflow. Add the four `KEYSTORE_*` repository secrets first
if you want a Play-uploadable signature (§2).

Run the JVM test suite any time:

```bash
./gradlew testDebugUnitTest
```

---

## 2. Signing keys

1. Create an **upload key** (keep the keystore file offline and backed up):

   ```bash
   keytool -genkeypair -v -keystore colorzen-upload.jks \
     -alias colorzen -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Copy `keystore.properties.example` → `keystore.properties` and fill it in
   (git-ignored).
3. In Play Console use **Play App Signing** (mandatory for new apps): Google
   holds the *app signing key*; your `colorzen-upload.jks` is only the *upload
   key*. If you ever lose it, Play can reset the upload key.
4. For CI: `base64 -w0 colorzen-upload.jks` → secret `KEYSTORE_BASE64`, plus
   `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

---

## 3. Play Console setup

1. **Create app** → name `Color Zen`, default language English, **App or game:
   Game**, **Free or paid: Free** (monetisation is IAP).
2. Category: **Game → Puzzle**. Tags: *Puzzle*, *Casual*, *Offline*.
3. Store listing (see `store/listing.md` for the copy):
   * App icon `store/icon_512.png` (512×512).
   * Feature graphic `store/feature_graphic_1024x500.png` (1024×500).
   * Phone screenshots: the three `store/screenshot_*_1080x1920.png` files
     (≥2 required; replace with real device screenshots before launch if you
     prefer — Play accepts these mock-style shots because they depict the UI,
     but real captures are always stronger).
   * Short description (≤80 chars) and full description (≤4000 chars) from
     `store/listing.md`. Add the Arabic translation as a second store-listing
     language.
4. **Privacy policy URL** (required): host `privacy.html` from this repo:
   * Play Console → *App content → Privacy policy* → paste the URL.
   * GitHub: repo **Settings → Pages → Deploy from a branch → `main` / root**.
     The URL becomes
     `https://<your-org>.github.io/Color-Zen/privacy.html`
     (update `core/AppConfig.PRIVACY_POLICY_URL` if your org/repo differs).

---

## 4. In-app products (Monetise → Products → In-app products)

Create **exactly** these IDs (they are hard-referenced by
`billing/ProductIds.kt`; prices are *not* in the code — the shop renders
`formattedPrice` from Play at runtime):

| Product ID | Type | Price | Grants |
|---|---|---|---|
| `level_pack_1` | Non-consumable | $0.99 | Levels 31–60 |
| `level_pack_2` | Non-consumable | $0.99 | Levels 61–90 |
| `level_pack_3` | Non-consumable | $0.99 | Levels 91–120 |
| `hint_pack_10` | **Consumable** | $0.99 | +10 hints |
| `color_themes` | Non-consumable | $1.99 | 4 palettes + 3 bottle designs |
| `full_game` | Non-consumable | $2.99 | All 120 levels |

Set each to **Active**. No subscriptions exist in this app.

Licence testing: add your test account under *Setup → License testing* and use
it on a device to exercise purchases without real charges.

---

## 5. Content rating

*App content → Content ratings* → start questionnaire:

* Violence: **No** · Sexual content: **No** · Profanity: **No**
* Controlled substances: **No** · Gambling: **No**
* User-generated content: **No** · Chat/social: **No**
* In-app purchases: **Yes** (disclosed; no loot boxes, no randomised paid items)
* Sharing of location / personal info: **No**

Expected result: **Everyone / PEGI 3 / USK 0**. The in-app Settings screen states
the same rating.

---

## 6. Data safety form

*App content → Data safety*:

* “Does your app collect or share any of the required user data types?” → **No**.
* Then confirm: data is not collected, not shared, no analytics, no ads.
* “Is all user data encrypted in transit?” → n/a (no transmission).
* “Do you provide a way for users to request data deletion?” → n/a; the privacy
  page explains that uninstalling removes the on-device data.

This is truthful because the manifest requests no `INTERNET` permission and the
only third-party interaction is Play Billing over binder IPC.

---

## 7. Target API & app bundles declarations

* *App content → Target API level*: 36 is declared in the bundle; Play accepts
  it (new apps must target ≥36 after 31 Aug 2026).
* *App bundles*: nothing to configure — a single AAB, no asset packs, no Play
  Feature Delivery, no native libraries (so the 64-bit requirement is met by
  having no 32-bit code at all).

---

## 8. Testing track → production

1. **Internal testing** first: upload the AAB, add your licence-test account.
   Internal testing does **not** need Play review and activates in minutes —
   use it to verify: billing flow, restore purchases, Arabic RTL layout, and
   that levels 31+ lock/unlock correctly after purchase.
2. **Closed testing** for ≥12 testers / 14 days is only required for *new
   personal-organisation* accounts created after Nov 2023; check your account’s
   banner.
3. **Production**: roll out 10% → 50% → 100%.

Post-launch checks: Play Vitals (no ANRs expected; the board renders on a
single canvas), and the *Pre-launch report* screenshots for RTL.

---

## 9. Release hygiene

* Bump `versionCode` (and `versionName` when user-visible) in
  `app/build.gradle.kts` **and** `core/AppConfig.kt` for every release.
* Regenerate levels only via `tools/generate_levels.py` (it re-proves every
  level solvable and writes `docs/levels-diagnostics.json`).
* Regenerate icons/SFX/store art via `tools/generate_icons.py`,
  `tools/generate_sfx.py`, `tools/generate_assets.py`; preview PNGs land in
  `tools/preview/` (git-ignored).
* Keep `privacy.html` and the in-app `privacy_*` strings in sync; update the
  “Last updated” date in both when the text changes.
