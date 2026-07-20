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
- 📊 **Dashboard "Oggi"** - saldo totale con andamento degli ultimi 30 giorni (sparkline nella card principale) e proiezione a fine mese come coda tratteggiata (stima da ricorrenze in arrivo e media di spesa giornaliera), spese del giorno e del mese, ultimi movimenti: tutto in 5 secondi, e ogni scheda apre il proprio dettaglio con un tap
- ✨ **Recap mensile "Saldo Wrapped"** - a inizio mese, il racconto del mese appena concluso in schermate a storia: netto, spese e confronto col mese prima, top categorie, record (spesa più grande, giorno più attivo), entrate vs uscite e quota risparmiata. Generato interamente sul dispositivo e condivisibile come immagine; raggiungibile anche dalle Statistiche per ogni mese passato
- 💸 **Movimenti** - spese, entrate e trasferimenti tra conti, registrabili in 2-3 tap
- ⚡ **Scorciatoie dal launcher** - pressione prolungata sull'icona dell'app per una nuova spesa, una nuova entrata o un trasferimento, senza passare dalla Dashboard
- 🏦 **Conti multipli** - con tipi espliciti (conto corrente, conto di risparmio, carta prepagata, carta di credito, contanti, wallet digitale) e una descrizione d'uso per ciascuno direttamente nell'editor; saldo iniziale, rettifica saldo, archiviazione e due interruttori indipendenti di inclusione: nel saldo totale e nel calcolo del budget. L'elenco è raggruppato per tipo conto (conto corrente per primo) con intestazione di sezione e ordinato alfabeticamente per nome all'interno di ogni gruppo
- 🐖 **Conto di risparmio** - il recinto dei soldi messi da parte: si alimenta con trasferimenti, conta nel patrimonio ma di default resta fuori dal budget, così attingere ai risparmi non consuma il budget del mese
- 💳 **Carte di credito a saldo** - un tipo di conto dedicato per le carte ad addebito differito: le spese si accumulano come saldo negativo nel ciclo e vengono addebitate in un'unica soluzione sul conto collegato. Ciclo con giorno di chiusura e giorno di addebito configurabili, addebito automatico o con conferma alla scadenza, barra di utilizzo opzionale rispetto al fido, e scheda in Dashboard/Conti per pagare l'estratto con un tap. Il saldo parte sempre da zero: il debito già maturato si inserisce con la rettifica saldo e viene addebitato col prossimo estratto
- 🔁 **Movimenti ricorrenti** - uscite ricorrenti (abbonamenti, affitto, assicurazioni), entrate ricorrenti (stipendio, affitti attivi) e trasferimenti ricorrenti (accantonamenti tra conti) con registrazione automatica o con conferma, hub dedicato con totale mensile e proiezione annua per tab, notifica di pre-rinnovo opzionale ("Netflix si rinnova tra 3 giorni") con anticipo configurabile. I trasferimenti tra valute diverse chiedono l'importo ricevuto a ogni occorrenza; i trasferimenti verso il conto di risparmio alimentano la card "Risparmio pianificato" (X/mese). Nell'elenco movimenti i record generati da una regola ricorrente portano un piccolo segno (🔁) e in modifica un banner lo indica con il nome della regola
- 💰 **Budget mensili** - un tetto complessivo per il mese e tetti per singole categorie di spesa, con barre di avanzamento verde/giallo/rosso e avvisi all'80% e al 100%
- 🟢 **Spendibile oggi** - quanto puoi ancora spendere restando nel budget: tiene conto di quanto speso, dei movimenti da confermare e degli addebiti ricorrenti in arrivo entro fine mese
- 🎯 **Obiettivi di risparmio** - un traguardo su un conto di risparmio: il risparmiato è il saldo reale del conto, che alimenti con i trasferimenti (manuali o ricorrenti). Con data obiettivo opzionale, suggerimento del versamento mensile necessario e stima di quando lo raggiungerai al ritmo dei tuoi trasferimenti ricorrenti
- 🏷️ **Categorie e tag personalizzabili**
- 🔍 **Ricerca e filtri combinabili** con totale della vista filtrata, preset rapidi (inclusa "Questa settimana", che rispetta il primo giorno scelto), periodo personalizzato (intervallo chiuso, solo "da una data" o solo "fino a una data") e filtro per origine (solo ricorrenti / solo manuali)
- 🧹 **Eliminazione dei movimenti filtrati** - dal registro elimini in blocco tutti i movimenti della vista filtrata (per data, tipo, categoria, conto, tag, importo), con anteprima dell'impatto e undo. Due modalità: "Ricalcola i saldi" (per rimuovere voci errate) e "Conserva i saldi correnti" (per fare pulizia dello storico senza spostare i saldi, tramite una rettifica di riporto per conto). Con export della selezione prima di eliminare
- 📈 **Statistiche** - spese per categoria (anello animato, tap sulla fetta per i movimenti), trend mensile, entrate vs uscite, andamento saldo
- 🌍 **Multi-valuta** - ogni movimento conserva importo e valuta originali
- 🎨 **Tema personalizzabile** - chiaro/scuro/sistema, palette dell'app o colori dinamici Material You
- ⚙️ **Preferenze** - valuta principale (automatica o esplicita), conto predefinito per i nuovi movimenti, primo giorno della settimana, scelta delle card visibili in Dashboard
- 💾 **Backup e ripristino su file** - export JSON versionato dove vuoi tu (nessun account richiesto), ripristino guidato con anteprima del contenuto - più **export CSV** dei movimenti filtrati, condivisibile
- 📥 **Import CSV** - importa i movimenti da un file CSV, anche di formato diverso da quello esportato: riconoscimento automatico di separatore, decimali e colonne (per nome, IT/EN, in qualsiasi ordine), regole di adattamento (tipo dedotto dal segno, segno normalizzato, valuta dal conto), creazione opzionale di conti/categorie/tag mancanti, rilevazione dei duplicati (contro il registro e nel file), anteprima e report finale. Solo inserimento: non modifica né elimina i movimenti esistenti
- 🛟 **Niente modifiche perse per sbaglio** - uscendo da un editor (conto, movimento, ricorrenza, budget, categoria) con dati non salvati, l'app chiede conferma prima di scartarli
- 🇮🇹 🇬🇧 Italiano e inglese

### Roadmap futura (v1.5 / v2.0)

PIN e biometria, widget, export Google Sheets/Excel/PDF, conversione valuta, cifratura backup, backup automatico su Google Drive (da valutare). Roadmap completa in [PLANNING.md](./PLANNING.md).

## Principi

| | |
|---|---|
| 🔌 **Offline-first** | ogni funzione core funziona senza rete |
| 🔒 **Privacy-first** | nessun dato lascia il dispositivo senza azione esplicita; nessuna telemetria di terze parti |
| 🚫 **Zero backend** | nessun server proprietario, nessun account obbligatorio |
| ⚡ **Zero frizione** | registrare una spesa richiede al massimo 2-3 tap |

## Stack tecnico

- **Kotlin** 100%, **Jetpack Compose** + Material 3 (palette brand di default, Material You/dynamic color attivabile dalle impostazioni); typeface **Inter** (variable font embeddato, figure tabulari per gli importi)
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

Requisiti: Android Studio (ultima versione stabile), JDK 21+.

```bash
git clone https://github.com/fiorenzobrioni/saldo.git
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
├── MainActivity.kt          # activity host
├── MainViewModel.kt         # stato globale (tema, navigazione)
├── SaldoApplication.kt      # Application + Hilt entry point
├── budget/                   # notifiche e watcher soglie budget
├── creditcard/               # notifiche carte di credito a saldo
├── recurring/                # worker e notifiche movimenti ricorrenti
├── core/
│   ├── common/              # utility condivise
│   ├── database/            # Room DB, DAO, migrazioni
│   ├── designsystem/        # tema Material 3, componenti UI condivisi
│   └── domain/              # modelli e logica di dominio
├── feature/
│   ├── about/               # schermata informazioni: versione, licenza, librerie
│   ├── accounts/            # conti: lista, editor, rettifica saldo
│   ├── backup/              # backup su file: export e ripristino guidato
│   ├── budgets/             # budget mensili: tetto globale e per categoria
│   ├── categories/          # categorie: tab spese/entrate, editor, riordino drag
│   ├── dashboard/           # schermata "Oggi": saldo, oggi/mese, ultimi movimenti, FAB
│   ├── onboarding/          # primo avvio: benvenuto, valuta, primo conto, notifiche
│   ├── recap/               # Saldo Wrapped: recap mensile a storia
│   ├── recurring/           # movimenti ricorrenti: hub, editor, motore
│   ├── savings/             # obiettivi di risparmio
│   ├── settings/            # impostazioni
│   ├── stats/               # statistiche: grafici Vico, periodo, drill-down
│   └── transactions/        # movimenti: lista per giorno, ricerca e filtri, editor
└── navigation/              # route NavKey, scaffold, bottom bar
```

## Documentazione di progetto

| File | Contenuto |
|------|-----------|
| [VISION.md](./VISION.md) | Visione di prodotto: cosa è l'app, per chi e perché |
| [PLANNING.md](./PLANNING.md) | Roadmap di sviluppo, decisioni architetturali, stato di avanzamento |
| [CLAUDE.md](./CLAUDE.md) | Regole operative per lo sviluppo assistito da AI |
| [docs/CLAUDE.md](./docs/CLAUDE.md) | Linee guida per la documentazione del progetto |
| [Guida utente](./docs/guida-utente/) | Manuale d'uso, una pagina per funzionalità (indice) |
| `devlog/` | Registro storico dello sviluppo |

## Licenza

Distribuito sotto licenza **GNU General Public License v3.0** - vedi il file [LICENSE](./LICENSE) per i dettagli.

Il font **Inter** incluso nell'app è distribuito sotto **SIL Open Font License 1.1** - vedi [licenses/inter/OFL.txt](./licenses/inter/OFL.txt).
