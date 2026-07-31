<div align="center">

# 💶 Saldo

**Capire dove vanno i soldi, in modo chiaro e immediato.**

Un'app Android moderna, offline-first e privacy-first per il tracciamento delle spese personali.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-33-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/license-GPL--3.0-blue)
![Release](https://img.shields.io/github/v/release/fiorenzobrioni/saldo?label=release)

[**⬇️ Scarica l'ultima versione**](https://github.com/fiorenzobrioni/saldo/releases/latest)

</div>

---

## Cos'è Saldo

Saldo è un **expense tracker evoluto**, non un'app di home banking: aiuta a monitorare spese ed entrate, tenere sotto controllo il saldo dei propri conti (banca, carte, contanti, wallet) e capire le proprie abitudini di spesa. Nessun collegamento ai conti bancari, nessun backend, nessuna registrazione: **i dati restano sul dispositivo**.

## Funzionalità

- 👋 **Onboarding al primo avvio** - benvenuto, scelta della valuta, primo conto e, per chi torna, ripristino diretto da un backup
- 📊 **Dashboard "Oggi"** - saldo totale con andamento degli ultimi 30 giorni (sparkline nella card principale, con una sottile linea puntinata a quota zero quando l'andamento la attraversa) e proiezione a fine mese come coda tratteggiata (stima che parte dal saldo di oggi e applica, nel giorno in cui cadono, ricorrenze in arrivo, movimenti futuri già registrati e occorrenze da confermare, più la media di spesa giornaliera), spese del giorno e del mese (sui conti attivi: gli archiviati restano fuori, come dal saldo totale), ultimi movimenti: tutto in 5 secondi, e ogni scheda apre il proprio dettaglio con un tap. Se hai già registrato movimenti con data futura, una riga "ad oggi" sotto la cifra indica il saldo effettivamente disponibile oggi (quello rappresentato dal grafico); la stessa riga compare anche sotto il singolo conto nel dettaglio della card, ma solo per i conti il cui saldo differisce da quello di oggi
- ✨ **Recap mensile "Saldo Wrapped"** - a inizio mese, il racconto del mese appena concluso in schermate a storia: netto, spese e confronto col mese prima, top categorie, record (spesa più grande, giorno più attivo), entrate vs uscite e quota risparmiata. Generato interamente sul dispositivo e condivisibile come immagine; raggiungibile anche dalle Statistiche per ogni mese passato
- 💸 **Movimenti** - spese, entrate e trasferimenti tra conti, registrabili in 2-3 tap: importo, categoria e conto stanno tutti sulla prima schermata, senza scorrere. Data e ora stanno in un unico chip diviso in due: tocchi la data per il calendario, l'ora per l'orologio; oltre alla descrizione puoi allegare una nota lunga (nascosta dietro "Aggiungi una nota" finché non serve, e ritrovabile con la ricerca); l'eliminazione dall'editor si annulla con un tap (undo), senza dialog di conferma
- 🔢 **Tastierino importi dell'app** - gli importi si digitano su un tastierino integrato, non sulla tastiera del telefono: tasti grandi, virgola della tua lingua, migliaia raggruppate mentre scrivi e si chiude quando non serve: si ritira da solo appena scorri il resto del modulo, così categorie e campi opzionali riprendono lo spazio. Funziona con TalkBack, con una tastiera fisica e il long-press sull'importo incolla
- ⚡ **Scorciatoie dal launcher** - pressione prolungata sull'icona dell'app per una nuova spesa, una nuova entrata o un trasferimento, senza passare dalla Dashboard
- 🏠 **Widget di aggiunta rapida, in due forme** - registri una spesa senza aprire l'app, e li aggiungi alla home direttamente dalle impostazioni di Saldo (o dal picker del launcher, dove compaiono come due widget distinti). La **griglia** (fino a 4x3): tocchi la categoria e si apre una piccola schermata sopra la home, col tastierino già pronto, importo e Salva; le categorie seguono l'ordine della schermata Categorie, oppure sono quelle che scegli e riordini tu trascinandole; ingrandendola le righe crescono con lo spazio fino a mostrarle tutte, con il selettore Spesa/Entrata. La **barra** (una riga): due bottoni Spesa ed Entrata che si dividono la larghezza, con un bottone quadrato con l'icona dell'app accanto (attivo di default, disattivabile), e la scelta di tenere entrambi i bottoni o uno solo. Ogni widget può registrare su un conto diverso: se lo fissi a un conto, il widget ne mostra il nome. L'aspetto si configura per singolo widget: sfondo solido che segue il tema dell'app (e il passaggio chiaro/scuro del telefono, all'istante) oppure forzato chiaro o scuro, con un'anteprima dal vivo mentre decidi. I widget sono punti di ingresso statici: niente saldi né totali, così si aggiornano solo quando cambi conti, categorie o tema, non a ogni movimento
- 📌 **Tile nelle Impostazioni rapide** - una tile "Aggiungi spesa" da aggiungere alla tendina del telefono: un tap apre la stessa piccola schermata del widget col tastierino già pronto, sul conto predefinito e con la categoria più usata già selezionata, da qualunque schermata ti trovi. A telefono bloccato chiede prima lo sblocco (e il blocco app di Saldo resta valido); come i widget, non mostra dati e non consuma nulla a riposo
- ⌨️ **Inserimento rapido testuale** - nella schermata rapida di widget e tile puoi anche scrivere tutto in una riga: "12,50 pizza ieri" compila importo, descrizione e data, e la categoria viene suggerita dai nomi delle tue categorie o da come hai classificato quella parola in passato (mai da un dizionario: migliora con l'uso). Tutto resta correggibile prima di salvare: se l'importo è ambiguo il tastierino resta in attesa, se il suggerimento non è netto la categoria non cambia, e una tua scelta manuale non viene mai scavalcata. Capisce "ieri", i giorni della settimana e le date brevi ("3/7") nella lingua del telefono, interamente offline
- 🏦 **Conti multipli** - con tipi espliciti (conto corrente, conto di risparmio, carta prepagata, carta di credito, contanti, wallet digitale) e una descrizione d'uso per ciascuno direttamente nell'editor; alla creazione icona, colore e valuta vengono preimpostati (icona e colore in base al tipo, la valuta è quella principale dell'app), finché non li scegli a mano; saldo iniziale, rettifica saldo, archiviazione e due interruttori indipendenti di inclusione: nel saldo totale e nel calcolo del budget. L'elenco è raggruppato per tipo conto (conto corrente per primo), ogni sezione ha un'intestazione con il sottototale del gruppo (e il saldo "ad oggi" quando differisce) e i conti si possono riordinare a mano trascinandoli all'interno del proprio tipo (l'ordine scelto vale anche nel dettaglio della card Saldo totale in Dashboard). Per i conti con movimenti datati nel futuro, sotto il saldo compare una riga "ad oggi" con il saldo effettivamente disponibile oggi
- 🐖 **Conto di risparmio** - il recinto dei soldi messi da parte: si alimenta con trasferimenti, conta nel patrimonio ma di default resta fuori dal budget, così attingere ai risparmi non consuma il budget del mese
- 💳 **Carte di credito a saldo** - un tipo di conto dedicato per le carte ad addebito differito: le spese si accumulano come saldo negativo nel ciclo e vengono addebitate in un'unica soluzione sul conto collegato. Ciclo con giorno di chiusura e giorno di addebito configurabili, addebito automatico o con conferma alla scadenza, barra di utilizzo opzionale rispetto al fido, e scheda in Dashboard/Conti per pagare l'estratto con un tap. Il saldo parte sempre da zero: il debito già maturato si inserisce con la rettifica saldo e viene addebitato col prossimo estratto
- 📉 **Prestiti e finanziamenti** - un tipo di conto per prestiti, finanziamenti e mutui: il saldo iniziale è il debito residuo di oggi dichiarato dalla banca (col segno meno) e ogni rata è un trasferimento, tipicamente ricorrente, dal conto di pagamento, quindi resta fuori dalle statistiche di spesa. La scheda del conto mostra quanto hai rimborsato, il residuo, la prossima rata e una stima delle rate mancanti; a debito azzerato suggerisce l'archiviazione. Di default resta fuori dal saldo totale e dal budget (includerlo è una scelta esplicita); niente calcolo di interessi né piani di ammortamento, il riallineamento si fa con la rettifica saldo
- 🔁 **Movimenti ricorrenti** - uscite ricorrenti (abbonamenti, affitto, assicurazioni), entrate ricorrenti (stipendio, affitti attivi) e trasferimenti ricorrenti (accantonamenti tra conti) con registrazione automatica o con conferma, hub dedicato con totale mensile e proiezione annua per tab, notifica di pre-rinnovo opzionale ("Netflix si rinnova tra 3 giorni") con anticipo configurabile. Una regola programmata per iniziare dopo il mese in corso è già in elenco con la sua prima data, ma non entra nel totale mensile finché non arriva il suo mese. I trasferimenti tra valute diverse chiedono l'importo ricevuto a ogni occorrenza; i trasferimenti verso il conto di risparmio alimentano la card "Risparmio pianificato" (X/mese). Nell'elenco movimenti i record generati da una regola ricorrente portano un piccolo segno (🔁) e in modifica un banner lo indica con il nome della regola. Dall'hub, la riga "Cerca ricorrenze non registrate" analizza su richiesta gli ultimi 12 mesi e propone gli abbonamenti e le bollette che registri a mano con cadenza regolare: un tap sul suggerimento apre l'editor della regola già precompilato, la X lo scarta per sempre. La ricerca parte solo da quel tap, mai da sola, e avviene interamente sul dispositivo
- ⏳ **In arrivo** - una sola lista di cosa sta per succedere: movimenti con data futura (tuoi o generati da una regola) e occorrenze ancora da confermare, raggruppati per giorno, con i due totali in uscita e in entrata tenuti separati. La coda "da confermare" è un filtro di questa schermata, non un posto diverso. Un movimento futuro non tocca statistiche, budget, spendibile e card Oggi/Mese finché non arriva il suo giorno, ma incide sul saldo del conto e compare nella coda tratteggiata della sparkline. Su un movimento datato in avanti puoi attivare **Ricordamelo**: una notifica prima della scadenza, con lo stesso anticipo del pre-rinnovo, così una scadenza una tantum (bollo auto, IMU, rata) non ha bisogno di una regola annuale finta. Card opzionale in Dashboard
- 💰 **Budget mensili** - un tetto complessivo per il mese e tetti per singole categorie di spesa, con barre di avanzamento verde/giallo/rosso e avvisi all'80% e al 100%. Il budget complessivo resta in cima; i budget per categoria sono ordinati dal più vicino al tetto, con i pari merito (a inizio mese) in ordine alfabetico di categoria
- 🟢 **Spendibile oggi** - quanto puoi ancora spendere restando nel budget: tiene conto di quanto speso, dei movimenti da confermare e degli addebiti ricorrenti in arrivo entro fine mese
- 🎯 **Obiettivi di risparmio** - un traguardo su un conto di risparmio: il risparmiato è il saldo reale del conto, che alimenti con i trasferimenti (manuali o ricorrenti). Con data obiettivo opzionale, suggerimento del versamento mensile necessario e stima di quando lo raggiungerai al ritmo dei tuoi trasferimenti ricorrenti. Il totale risparmiato resta in cima; gli obiettivi sono elencati in ordine alfabetico
- 🤝 **Crediti e debiti verso persone** - segni una spesa (o un'entrata) come prestito e indichi con chi: il denaro esce dal conto come sempre, ma resta fuori da statistiche, budget e spendibile, perché prestare non è spendere. Una schermata dedicata tiene i due totali separati (quanto ti devono, quanto devi) e una riga per persona con il saldo aperto, i movimenti che lo compongono e l'ultima attività; i rientri parziali funzionano da soli, perché il saldo è la somma con segno dei movimenti. Un pulsante apre il rientro già precompilato nel verso opposto, il nome ha il completamento automatico dai nomi già usati (e maiuscole o accenti diversi contano come la stessa persona), e card opzionale in Dashboard
- 🏷️ **Categorie e tag personalizzabili** - i tag nascono al volo nell'editor del movimento e hanno una schermata di gestione dedicata (Impostazioni > Gestione > Tag): conteggio dei movimenti per tag, ordinamento per uso o alfabetico, ricerca quando sono molti, rinomina (verso un nome già esistente propone l'unione invece di creare un doppione), unione di più tag in uno ed eliminazione con conferma, senza mai toccare i movimenti
- 🔍 **Ricerca e filtri combinabili** con totale della vista filtrata, preset rapidi (inclusa "Questa settimana", che rispetta il primo giorno scelto), periodo personalizzato (intervallo chiuso, solo "da una data" o solo "fino a una data"), filtro per origine (solo ricorrenti / solo manuali) e chip "Senza categoria" per ritrovare i movimenti rimasti senza
- 🧹 **Eliminazione dei movimenti filtrati** - dal registro elimini in blocco tutti i movimenti della vista filtrata (per data, tipo, categoria, conto, tag, importo), con anteprima dell'impatto e undo. Due modalità: "Ricalcola i saldi" (per rimuovere voci errate) e "Conserva i saldi correnti" (per fare pulizia dello storico senza spostare i saldi, tramite una rettifica di riporto per conto). Con export della selezione prima di eliminare
- 📈 **Statistiche** - spese per categoria (anello animato, tap sulla fetta per i movimenti), trend mensile, entrate vs uscite, andamento saldo
- 🌍 **Multi-valuta con conversione automatica** - ogni movimento conserva importo e valuta originali; nei trasferimenti tra valute diverse l'editor mostra il tasso di cambio implicito nei due importi digitati, che resta il dato reale dell'operazione. I conti e i movimenti in valuta estera entrano in saldo totale, card Oggi/Mese, statistiche, budget, spendibile e obiettivi come controvalori stimati con i tassi di riferimento BCE, sempre indicati con "≈": le spese passate al tasso del giorno del movimento (così un mese concluso resta stabile), i saldi all'ultimo tasso noto, con la data del tasso dichiarata. I tassi si scaricano da internet solo quando serve e restano in cache, quindi offline vale l'ultimo tasso noto con la sua data; la conversione si può disattivare dalle Impostazioni (e senza dati in valuta estera non parte comunque alcuna richiesta). Nessun controvalore viene mai salvato: quello che è successo resta scritto nella valuta in cui è successo. Una schermata **Tassi di cambio** (dalla riga dei controvalori in Dashboard, dalla lista Conti o dalle Impostazioni) mostra tutte le valute BCE scaricate con il valore rispetto alla valuta principale, la variazione sull'ultima pubblicazione e un mini-grafico dell'andamento recente; in testa un **convertitore rapido** (prezzo visto all'estero → controvalore nella tua valuta, direzione invertibile) e, toccando una valuta, il dettaglio con grafico a 1 o 3 mesi. Nell'editor di un movimento in valuta estera compare il controvalore stimato al tasso della data del movimento
- 🎨 **Tema personalizzabile** - chiaro/scuro/sistema, palette dell'app o colori dinamici Material You
- ⚙️ **Preferenze** - valuta principale (automatica o esplicita), conversione automatica delle valute (attiva di default, disattivabile), conto predefinito per i nuovi movimenti, primo giorno della settimana, scelta delle card visibili in Dashboard e apertura predefinita del dettaglio conti nella card Saldo totale (all'avvio dell'app)
- 🔒 **Blocco app** (opzionale) - PIN di 6 cifre richiesto all'apertura, sblocco con impronta o volto, blocco automatico configurabile (subito, dopo 1 o 5 minuti in background) e attesa progressiva dopo troppi tentativi errati. Il blocco copre anche l'inserimento rapido dal widget. In più, un interruttore indipendente nasconde il contenuto nella schermata delle app recenti (e blocca gli screenshot). Il PIN non è mai salvato in chiaro e non entra nei backup
- 💾 **Backup e ripristino su file** - export JSON versionato dove vuoi tu (nessun account richiesto), con dentro **tutto**: conti, movimenti, categorie, ricorrenze, tag, budget, obiettivi e anche le tue impostazioni (tema, valuta principale, conto predefinito, promemoria, card della Dashboard), così un ripristino su un telefono nuovo non ti chiede di riconfigurare l'app. Ripristino guidato con anteprima del contenuto prima di sostituire qualsiasi cosa - più **export CSV** dei movimenti filtrati, condivisibile. Dalla stessa schermata puoi anche **cancellare tutti i dati** e riportare l'app a com'era il primo giorno: la conferma ti ricorda quando hai fatto l'ultimo backup (o che non l'hai mai fatto)
- 🔐 **Backup cifrato** (opzionale) - un interruttore accanto all'export protegge il file con una **passphrase** (AES-256, chiave derivata sul dispositivo): senza di essa il file non è leggibile da nessuno. Il ripristino riconosce un file cifrato dal contenuto e chiede la passphrase prima di mostrarti cosa contiene; una passphrase sbagliata te lo dice, senza confonderla con un file danneggiato. Non c'è modo di recuperarla, e l'app lo dichiara prima di attivare la protezione. I backup non cifrati restano importabili per sempre
- 📥 **Import CSV** - importa i movimenti da un file CSV, anche di formato diverso da quello esportato: riconoscimento automatico di separatore, decimali e colonne (per nome, IT/EN, in qualsiasi ordine), regole di adattamento (tipo dedotto dal segno, segno normalizzato, valuta dal conto), creazione opzionale di conti/categorie/tag mancanti, rilevazione dei duplicati (contro il registro e nel file), anteprima e report finale. Export e import coprono anche controparte, esclusione dalle statistiche e rimborso, così un giro CSV completo non perde i prestiti. Solo inserimento: non modifica né elimina i movimenti esistenti
- 🛟 **Niente modifiche perse per sbaglio** - uscendo da un editor (conto, movimento, ricorrenza, budget, categoria) con dati non salvati, l'app chiede conferma prima di scartarli; l'eliminazione di movimenti, budget e obiettivi dagli editor si annulla con un tap (undo), senza dialog di conferma
- 🇮🇹 🇬🇧 Italiano e inglese

### Roadmap futura

Le funzionalità della roadmap v2.0 sono implementate e verificate su device: resta la release. Una roadmap v3.0 raccoglie le estensioni successive (budget con periodo personalizzato e riporto, acquisti a rate, spesa divisa su più categorie). Restano da valutare, fuori dal piano: foto dello scontrino allegate ai movimenti, rimborsi collegati alla spesa originale, commissioni sui trasferimenti, analisi avanzate, export PDF/Excel/Google Sheets, pagamento parziale dell'estratto carta, arrotondamento degli spiccioli, riepilogo settimanale e backup automatico su Google Drive. Roadmap completa in [PLANNING.md](./PLANNING.md).

## Installazione

L'app non è (ancora) sul Play Store: si scarica dalla sezione [Releases](https://github.com/fiorenzobrioni/saldo/releases/latest) di questo repository.

1. Scarica il file `saldo-<versione>-debug.apk` dagli allegati della release.
2. Aprilo dal telefono e autorizza l'installazione da questa sorgente quando Android lo chiede.
3. Al primo avvio scegli valuta e primo conto, oppure ripristina un backup esistente.

Requisiti: Android 13 (API 33) o superiore. L'APK pubblicato è una build di debug, la stessa verificata dalla CI, firmata con il keystore di debug condiviso del repository: ogni release si installa sopra la precedente senza perdere i dati. Le note di ogni versione sono in [docs/release-notes/](./docs/release-notes/).

## Principi

| | |
|---|---|
| 🔌 **Offline-first** | ogni funzione core funziona senza rete; la lettura dei cambi BCE ha una cache locale e senza rete vale l'ultimo tasso noto |
| 🔒 **Privacy-first** | nessun dato lascia il dispositivo senza azione esplicita; nessuna telemetria di terze parti. L'unico traffico di rete oltre a backup ed export opzionali è la richiesta dei tassi BCE, che non contiene alcun dato dell'utente ed è disattivabile |
| 🚫 **Zero backend** | nessun server proprietario, nessun account obbligatorio |
| ⚡ **Zero frizione** | registrare una spesa richiede al massimo 2-3 tap |

## Stack tecnico

- **Kotlin** 100%, **Jetpack Compose** + Material 3 (palette brand di default, Material You/dynamic color attivabile dalle impostazioni); typeface **Inter** (variable font embeddato, figure tabulari per gli importi)
- **Navigation 3** (`androidx.navigation3`)
- **Room** (persistenza), **DataStore** (impostazioni), **Coroutines + Flow**
- **MVVM + Use Cases + Repository**, **Hilt** (DI), **KSP**
- **WorkManager** (ricorrenze, backup), **Vico** (grafici), **Glance** (widget home)
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

La CI (GitHub Actions) esegue gli stessi task su ogni push. Il push di un tag `vX.Y.Z` esegue la stessa verifica e pubblica una release con l'APK allegato.

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
│   ├── counterparties/      # crediti e debiti verso persone
│   ├── dashboard/           # schermata "Oggi": saldo, oggi/mese, ultimi movimenti, FAB
│   ├── onboarding/          # primo avvio: benvenuto, valuta, primo conto, notifiche
│   ├── recap/               # Saldo Wrapped: recap mensile a storia
│   ├── recurring/           # movimenti ricorrenti: hub, editor, motore
│   ├── savings/             # obiettivi di risparmio
│   ├── settings/            # impostazioni
│   ├── stats/               # statistiche: grafici Vico, periodo, drill-down
│   ├── transactions/        # movimenti: lista per giorno, ricerca e filtri, editor
│   ├── upcoming/            # in arrivo: movimenti futuri e coda da confermare
│   └── widget/              # widget home: Glance, sheet importo, configurazione
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
