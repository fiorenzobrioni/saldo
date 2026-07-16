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

Saldo è un **expense tracker evoluto**, non un'app di home banking: aiuta a monitorare spese ed entrate, tenere sotto controllo il saldo dei propri conti (banca, carte, contanti, wallet) e capire le proprie abitudini di spesa. Nessun collegamento ai conti bancari, nessun backend, nessuna registrazione: **i dati restano sul dispositivo**.

## Funzionalità (v1.0)

- 👋 **Onboarding al primo avvio** - benvenuto, scelta della valuta, primo conto e, per chi torna, ripristino diretto da un backup
- 📊 **Dashboard "Oggi"** - saldo totale, spese del giorno e del mese, ultimi movimenti: tutto in 5 secondi, e ogni scheda apre il proprio dettaglio con un tap
- 💸 **Movimenti** - spese, entrate e trasferimenti tra conti, registrabili in 2-3 tap
- ⚡ **Scorciatoie dal launcher** - pressione prolungata sull'icona dell'app per una nuova spesa, una nuova entrata o un trasferimento, senza passare dalla Dashboard
- 🏦 **Conti multipli** - con tipi espliciti (conto corrente, carta di debito, carta prepagata, carta di credito, contanti, wallet digitale), saldo iniziale, rettifica saldo, archiviazione e due interruttori indipendenti di inclusione: nel saldo totale e nel calcolo del budget (per tenere, ad esempio, un conto di risparmio fuori da budget e spendibile pur contandolo nel patrimonio)
- 💳 **Carte di credito a saldo** - un tipo di conto dedicato per le carte ad addebito differito: le spese si accumulano come saldo negativo nel ciclo e vengono addebitate in un'unica soluzione sul conto collegato. Ciclo con giorno di chiusura e giorno di addebito configurabili, addebito automatico o con conferma alla scadenza, barra di utilizzo opzionale rispetto al fido, e scheda in Dashboard/Conti per pagare l'estratto con un tap. Il saldo parte sempre da zero: il debito già maturato si inserisce con la rettifica saldo e viene addebitato col prossimo estratto
- 🔁 **Movimenti ricorrenti** - uscite ricorrenti (abbonamenti, affitto, assicurazioni) ed entrate ricorrenti (stipendio, affitti attivi) con registrazione automatica o con conferma, hub dedicato con totale mensile e proiezione annua per tab, notifica di pre-rinnovo opzionale ("Netflix si rinnova tra 3 giorni") con anticipo configurabile
- 💰 **Budget mensili** - un tetto complessivo per il mese e tetti per singole categorie di spesa, con barre di avanzamento verde/giallo/rosso e avvisi all'80% e al 100%
- 🟢 **Spendibile oggi** - quanto puoi ancora spendere restando nel budget: tiene conto di quanto speso, dei movimenti da confermare e degli addebiti ricorrenti in arrivo entro fine mese
- 🏷️ **Categorie e tag personalizzabili**
- 🔍 **Ricerca e filtri combinabili** con totale della vista filtrata e preset rapidi (inclusa "Questa settimana", che rispetta il primo giorno scelto)
- 📈 **Statistiche** - spese per categoria, trend mensile, entrate vs uscite, andamento saldo
- 🌍 **Multi-valuta** - ogni movimento conserva importo e valuta originali
- 🎨 **Tema personalizzabile** - chiaro/scuro/sistema, palette dell'app o colori dinamici Material You
- ⚙️ **Preferenze** - valuta principale (automatica o esplicita), conto predefinito per i nuovi movimenti, primo giorno della settimana, scelta delle card visibili in Dashboard
- 💾 **Backup e ripristino su file** - export JSON versionato dove vuoi tu (nessun account richiesto), ripristino guidato con anteprima del contenuto - più **export CSV** dei movimenti filtrati, condivisibile
- 🛟 **Niente modifiche perse per sbaglio** - uscendo da un editor (conto, movimento, ricorrenza, budget, categoria) con dati non salvati, l'app chiede conferma prima di scartarli
- 🇮🇹 🇬🇧 Italiano e inglese

In arrivo (v1.5 / v2.0): PIN e biometria, widget, import CSV, export Google Sheets/Excel/PDF, obiettivi di risparmio, conversione valuta, cifratura backup, backup automatico su Google Drive (da valutare). Roadmap completa in [PLANNING.md](./PLANNING.md).

## Principi

| | |
|---|---|
| 🔌 **Offline-first** | ogni funzione core funziona senza rete |
| 🔒 **Privacy-first** | nessun dato lascia il dispositivo senza azione esplicita; nessuna telemetria di terze parti |
| 🚫 **Zero backend** | nessun server proprietario, nessun account obbligatorio |
| ⚡ **Zero frizione** | registrare una spesa richiede al massimo 2-3 tap |

## Stack tecnico

- **Kotlin** 100%, **Jetpack Compose** + Material 3 (palette brand di default, Material You/dynamic color attivabile dalle impostazioni)
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
│   ├── accounts/        # conti: lista, editor, rettifica saldo (da Fase 2)
│   ├── categories/      # categorie: tab spese/entrate, editor, riordino drag (da Fase 4)
│   ├── dashboard/       # schermata "Oggi": saldo, oggi/mese, ultimi movimenti, FAB (da Fase 5)
│   ├── transactions/    # movimenti: lista per giorno, ricerca e filtri, editor (da Fase 3)
│   ├── recurring/       # movimenti ricorrenti: hub uscite/entrate, editor, motore (da Fase 6)
│   ├── stats/           # statistiche: grafici Vico, periodo, drill-down (da Fase 7)
│   ├── backup/          # backup su file: export e ripristino guidato (da Fase 8)
│   ├── onboarding/      # primo avvio: benvenuto, valuta, primo conto, notifiche (da Fase 9)
│   ├── settings/        # impostazioni
│   └── about/           # schermata informazioni: versione, licenza, librerie
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
