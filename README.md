<div align="center">

# 💶 Saldo

**Capire dove vanno i soldi, in modo chiaro e immediato.**

Un'app Android moderna, offline-first e privacy-first per il tracciamento delle spese personali.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-33-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/license-GPL--3.0-blue)
![Status](https://img.shields.io/badge/status-in%20sviluppo-orange)

</div>

---

## Cos'è Saldo

Saldo è un **expense tracker evoluto**, non un'app di home banking: aiuta a monitorare spese ed entrate, tenere sotto controllo il saldo dei propri account (conto, carte, contanti, wallet) e capire le proprie abitudini di spesa. Nessun collegamento ai conti bancari, nessun backend, nessun account obbligatorio: **i dati restano sul dispositivo**.

## Funzionalità (v1.0)

- 📊 **Dashboard "Oggi"** - saldo totale, spese del giorno e del mese, ultimi movimenti: tutto in 5 secondi
- 💸 **Movimenti** - spese, entrate e trasferimenti tra account, registrabili in 2-3 tap
- 🏦 **Account multipli** - con saldo iniziale, rettifica saldo e archiviazione
- 🔁 **Ricorrenze e abbonamenti** - registrazione automatica o con conferma, vista dedicata con totale mensile
- 🏷️ **Categorie e tag personalizzabili**
- 🔍 **Ricerca e filtri combinabili** con totale della vista filtrata
- 📈 **Statistiche** - spese per categoria, trend mensile, entrate vs uscite, andamento saldo
- 🌍 **Multi-valuta** - ogni movimento conserva importo e valuta originali
- ☁️ **Backup opzionale** - automatico su Google Drive (App Data Folder, privato) oppure manuale su file, portabile ovunque - più **export CSV**
- 🇮🇹 🇬🇧 Italiano e inglese

In arrivo (v1.5 / v2.0): budget, PIN e biometria, widget, import CSV, export Google Sheets/Excel/PDF, obiettivi di risparmio, conversione valuta, cifratura backup. Roadmap completa in [PLANNING.md](./PLANNING.md).

## Principi

| | |
|---|---|
| 🔌 **Offline-first** | ogni funzione core funziona senza rete |
| 🔒 **Privacy-first** | nessun dato lascia il dispositivo senza azione esplicita; nessuna telemetria di terze parti |
| 🚫 **Zero backend** | nessun server proprietario, nessun account obbligatorio |
| ⚡ **Zero frizione** | registrare una spesa richiede al massimo 2-3 tap |

## Stack tecnico

- **Kotlin** 100%, **Jetpack Compose** + Material 3 (Material You, dynamic color)
- **Navigation 3** (`androidx.navigation3`)
- **Room** (persistenza), **DataStore** (impostazioni), **Coroutines + Flow**
- **MVVM + Use Cases + Repository**, **Hilt** (DI), **KSP**
- **WorkManager** (ricorrenze, backup), **Vico** (grafici)
- minSdk **33** (Android 13), target SDK 36
- Test: JUnit 5 (unit test JVM), JUnit 4 + Compose UI Test (strumentati), MockK, Turbine

```text
UI (Compose) → ViewModel → Use Cases → Repository → Room DB - DataStore - Backup/Export
```

Gli importi monetari sono gestiti come `Long` in centesimi nel database e `BigDecimal` nel dominio: nessun errore di arrotondamento, mai.

## Build

Requisiti: Android Studio (ultima versione stabile), JDK 17+.

```bash
git clone <repo-url>
cd saldo
./gradlew assembleDebug
```

Verifica completa (build, unit test, lint, analisi statica):

```bash
./gradlew assembleDebug testDebugUnitTest lint detekt
```

La CI (GitHub Actions) esegue gli stessi task su ogni push.

## Struttura del progetto

```text
app/src/main/kotlin/com/callbackdev/saldo/
├── core/
│   ├── common/          # utility condivise
│   ├── database/        # Room (da Fase 1)
│   ├── designsystem/    # tema Material 3, componenti UI condivisi
│   └── domain/          # modelli e logica di dominio (da Fase 1)
├── feature/
│   ├── accounts/        # account: lista, editor, rettifica saldo (da Fase 2)
│   ├── categories/      # categorie: tab spese/entrate, editor, riordino drag (da Fase 4)
│   ├── dashboard/       # schermata "Oggi": saldo, oggi/mese, ultimi movimenti, FAB (da Fase 5)
│   ├── transactions/    # movimenti: editor con tastierino, lista per giorno (da Fase 3)
│   ├── recurring/       # abbonamenti: vista, editor, motore ricorrenze (da Fase 6)
│   ├── stats/           # statistiche
│   └── settings/        # impostazioni
└── navigation/          # route NavKey, scaffold, bottom bar
```

## Documentazione di progetto

| File | Contenuto |
|------|-----------|
| [VISION.md](./VISION.md) | Visione di prodotto: cosa è l'app, per chi e perché |
| [PLANNING.md](./PLANNING.md) | Roadmap di sviluppo, decisioni architetturali, stato di avanzamento |
| [CLAUDE.md](./CLAUDE.md) | Regole operative per lo sviluppo assistito da AI |
| `devlog/` | Registro storico dello sviluppo |

## Licenza

Distribuito sotto licenza **GNU General Public License v3.0** - vedi il file [LICENSE](./LICENSE) per i dettagli.
