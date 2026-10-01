<div align="center">

# 💶 Saldo

**See where your money goes, clearly and at a glance.**

An offline-first, privacy-first Android app for tracking personal expenses.
Free, no account, no ads, no tracking, no link to your bank.

![Platform](https://img.shields.io/badge/platform-Android-2E6B3E?labelColor=FCFAF6)
![Release](https://img.shields.io/github/v/release/fiorenzobrioni/saldo?label=release&labelColor=FCFAF6&color=2E6B3E)
![CI](https://img.shields.io/github/actions/workflow/status/fiorenzobrioni/saldo/ci.yml?branch=main&label=CI&labelColor=FCFAF6&color=2E6B3E)
![License](https://img.shields.io/badge/license-GPL--3.0-007DB6?labelColor=FCFAF6)
![minSdk](https://img.shields.io/badge/minSdk-33-70569C?labelColor=FCFAF6)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-F1A000?labelColor=FCFAF6)
![Compose](https://img.shields.io/badge/UI-Compose%20Material%203-007DB6?labelColor=FCFAF6)
![Account](https://img.shields.io/badge/account-none%20needed-2E6B3E?labelColor=FCFAF6)

[**⬇️ Download the latest release**](https://github.com/fiorenzobrioni/saldo/releases/latest)

</div>

## What Saldo is

Saldo is an expense tracker, not a banking app: it records expenses, income and transfers,
keeps the balance of every account (bank, cards, cash, wallets) and shows where the money
goes. An expense takes 2 or 3 taps.

No link to your bank, no server, no sign-up: the data stays on the device. An account's
balance is always computed from its transactions, never stored on its own.

## Screenshots

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/screenshots/dashboard.png" width="250" alt="Dashboard: total balance with its trend and the month-end estimate, accounts, safe to spend today, today's and this month's spending"><br><sub><b>Dashboard</b>: the balance and the month at a glance</sub></td>
    <td align="center" width="33%"><img src="docs/screenshots/dashboard-cards.png" width="250" alt="Dashboard: month comparison, budgets, savings goal, credits and debts"><br><sub><b>Budgets and goals</b>, on their cards</sub></td>
    <td align="center" width="33%"><img src="docs/screenshots/dashboard-dark.png" width="250" alt="Dashboard in the dark theme"><br><sub><b>Dark theme</b></sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/transactions.png" width="250" alt="The month's transactions grouped by day, with the period's totals"><br><sub><b>Transactions</b>, day by day</sub></td>
    <td align="center"><img src="docs/screenshots/stats.png" width="250" alt="Statistics: the month's spending by category"><br><sub><b>Statistics</b> by category</sub></td>
    <td align="center"><img src="docs/screenshots/recurrences.png" width="250" alt="Recurring transactions: the month's total, the yearly projection, the next charges"><br><sub><b>Recurring</b>: total and yearly projection</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/widgets.png" width="250" alt="The two widgets on a home screen: the expense and income bar, and the category grid"><br><sub><b>Two quick-add widgets</b></sub></td>
    <td align="center"><img src="docs/screenshots/quick-entry.png" width="250" alt="Quick entry from the widget: the chosen category, the keypad, the amount"><br><sub><b>Quick entry</b>, without opening the app</sub></td>
    <td align="center"><img src="docs/screenshots/widget-settings.png" width="250" alt="Widget settings: a live preview, a light, dark or system background or one of six colours, the opacity"><br><sub><b>Widget settings</b>, with a live preview</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/exchange-rates.png" width="250" alt="ECB exchange rates with a quick converter and the recent trend"><br><sub><b>Exchange rates</b> from the ECB, and a converter</sub></td>
    <td align="center"><img src="docs/screenshots/guide.png" width="250" alt="The guide: the four screens and what each one answers"><br><sub><b>The guide</b>, in the app</sub></td>
    <td></td>
  </tr>
</table>

Drawn by the app's own screens from realistic sample data, in English (the app also speaks
Italian). The phone's status bar is not in the pictures. The command that redraws them is in
[Build](#build).

## Features

- 📊 **Dashboard**: total balance with its trend and month-end estimate, today's and this month's spending, latest transactions.
- 💸 **Transactions**: expenses, income and transfers in 2 or 3 taps, with a built-in keypad, notes, tags and duplication.
- 🏦 **Accounts**: current, savings, prepaid, credit card, cash, wallet, each with its own detail screen.
- 💳 **Credit cards**: closing and payment days, the statement paid with one tap.
- 📉 **Loans**: the debt left, the installments paid and those still to come.
- 🔁 **Recurring transactions**: subscriptions, salary, savings transfers, confirmed or automatic, and they can be paused.
- ⏳ **Upcoming**: future transactions and occurrences to confirm, in one list.
- 💰 **Budgets**: a monthly cap and caps per category, with alerts at 80% and 100%.
- 🟢 **Safe to spend today**: what is left in the budget, with the calculation shown.
- 🎯 **Savings goals**: a target on a savings account, with the monthly amount it needs.
- 🤝 **Credits and debts**: money lent to or borrowed from people, kept out of the spending statistics.
- 📈 **Statistics**: spending by category, monthly trend, income against expenses, balance over time.
- ✨ **Saldo Wrapped**: the story of the month just closed, shareable as an image.
- 🌍 **Multi-currency**: every transaction in its own currency, converted with the ECB reference rates.
- 🏠 **Widgets and Quick Settings**: an expense recorded without opening the app, even typed in one line ("12.50 pizza yesterday").
- 💾 **Backup**: to a file of yours, optionally encrypted with a passphrase, with an optional reminder.
- 📥 **CSV**: export of the filtered transactions, import with format detection or manual column mapping.
- 🔒 **App lock**: PIN, fingerprint or face, content hidden in recent apps.
- 🔔 **Notifications**: recurring transactions, budgets, card statements, due dates, silent between 22:00 and 7:00.
- 📖 **The guide**: the four screens, and what a screen cannot say on its own.
- 🎨 **Appearance**: light or dark, the app's colours or the phone's dynamic ones.
- 🇬🇧 🇮🇹 **English and Italian**, through the system per-app language picker.

The full user guide, one page per feature, is in [docs/guida-utente/](./docs/guida-utente/)
(in Italian).

## Principles

| | |
|---|---|
| 🔌 **Offline-first** | every core feature works without a network; offline, conversions use the last known ECB rate |
| 🔒 **Privacy-first** | no data leaves the device without an explicit action, no telemetry. The only network request is the ECB rates, which carries no user data and can be turned off |
| 🚫 **Zero backend** | no server of its own, no account |
| ⚡ **Zero friction** | recording an expense takes at most 2 or 3 taps |
| 🧮 **Exact money** | amounts in cents and `BigDecimal`, never floating point; a balance is always computed from its transactions |
| 📊 **Honest statistics** | transfers, adjustments and loans to people stay out of the spending statistics |

## Install

Android 13 (API 33) or newer.

1. Download `saldo-vX.Y.Z.apk` from the [latest release](https://github.com/fiorenzobrioni/saldo/releases/latest).
2. Open it on the phone and allow installs from that source when Android asks.
3. On the first run, pick a currency and a first account, or restore a backup.

**Verify the download.** Put the APK and its `.sha256` file in one folder and run
`sha256sum -c saldo-vX.Y.Z.apk.sha256`. To check that the APK is genuine, compare its signing
certificate (`apksigner verify --print-certs`, or AppVerifier on the phone) with this SHA-256
fingerprint:

```
17:6C:88:E2:21:93:87:89:71:5C:CB:65:F1:73:F2:CA:90:09:9A:BF:B6:16:55:C1:BF:FA:F4:DE:98:E8:F8:7E
```

**Updates.** Saldo does not check for updates itself. Use GitHub's "Watch, Custom, Releases"
notifications, or [Obtainium](https://github.com/ImranR98/Obtainium). From 2.2.0 on, every
release installs over the previous one and keeps your data. Versions up to 2.1.0 were signed
with another key: moving from one of them needs an uninstall, so export a backup first and
restore it on the first run. The notes of each version are in
[docs/release-notes/](./docs/release-notes/).

## Roadmap

- **v3.0**: budgets with a custom period and rollover, installment purchases, an expense split
  across categories, search with suggestions.

The full plan, with the architecture decisions and the ideas still to weigh, is in
[PLANNING.md](./PLANNING.md) (in Italian).

## Build

Requires JDK 21 and the Android SDK (a stable Android Studio works).

```bash
./gradlew assembleDebug                                # app/build/outputs/apk/debug/
./gradlew assembleDebug testDebugUnitTest lint detekt  # the full check, as in CI
```

For an installable minified build to test with:
`./gradlew assembleRelease -PsignReleaseWithDebugKey`. It is signed with the debug key
committed in `keystore/`, on purpose, so builds from CI and any machine share one signature.
Debug builds carry `applicationIdSuffix ".debug"` and install side by side with the release.

CI runs the tests, lint and detekt **before** building the APKs. A `vX.Y.Z` tag runs the same
check, then publishes the APK signed with the release key, its checksum and the R8 mapping,
with `docs/release-notes/vX.Y.Z.md` as the release notes.

README screenshots:
`./gradlew testDebugUnitTest -PupdateScreenshots --tests "*.ReadmeScreenshots"`.

## Tech stack

- **Kotlin** 2.3, **Jetpack Compose** with Material 3, Gradle 8.14 and AGP 8.13, minSdk **33**
  (Android 13), target and compile SDK **36**
- **Room** (persistence), **DataStore** (settings), **Hilt**, **Coroutines** and **Flow**, **KSP**
- **WorkManager** (recurring transactions, reminders), **RemoteViews** (widgets), **Vico** (charts)
- **Navigation 3**, MVVM with use cases only where there is real domain logic
- Tests: JUnit 5 on the JVM (amount mapping, recurrences, balances, backup), JUnit 4 and
  Compose UI tests on a device, Robolectric for the README screenshots

```text
Compose UI → ViewModel → Use case → Repository → Room, DataStore, backup and export
```

## Project structure

```text
saldo/
├── app/src/main/kotlin/com/callbackdev/saldo/
│   ├── core/
│   │   ├── common/         # shared utilities
│   │   ├── database/       # Room: entities, DAOs, migrations
│   │   ├── designsystem/   # Material 3 theme, shared components
│   │   └── domain/         # models and domain logic
│   ├── feature/            # one folder per screen: dashboard, transactions, accounts, ...
│   ├── navigation/         # NavKey routes, scaffold, bottom bar
│   └── notifications/      # the shared notification style
├── docs/                   # user guide, release notes, screenshots
├── devlog/                 # the development log
├── tools/                  # the launcher icon script
└── keystore/               # the shared debug key (deliberately committed)
```

## Project documentation

The project documents are in Italian.

| File | Contents |
|---|---|
| [VISION.md](./VISION.md) | the product: what it is, who it is for, and why |
| [PLANNING.md](./PLANNING.md) | the phased roadmap, the architecture decisions (ADRs), progress |
| [docs/release-notes/](./docs/release-notes/) | the notes of every published version |
| [devlog/](./devlog/) | the development log |
| [CLAUDE.md](./CLAUDE.md) | the operating rules for AI-assisted development in this repo |

## The family

Saldo is one of three focused apps with the same look and the same rules:
[Chiaro](https://github.com/fiorenzobrioni/chiaro) (weather) and
[Passo](https://github.com/fiorenzobrioni/passo) (steps).

## License

[GPL-3.0](./LICENSE) © 2026 Fiorenzo Brioni

[Inter](https://github.com/rsms/inter) under the SIL Open Font License 1.1: see
[licenses/inter/OFL.txt](./licenses/inter/OFL.txt).
