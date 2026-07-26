# PLANNING - Saldo

> Piano di implementazione operativo. La visione di prodotto è in [VISION.md](./VISION.md).
> Le checkbox tracciano lo stato di avanzamento. Questo file va tenuto aggiornato durante lo sviluppo.

---

## Convenzioni di progetto

- **Codice, identificatori, commit e nomi file in inglese**; documentazione in italiano
- Commit: Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`)
- Branch: `main` sempre buildabile; feature branch `feat/<nome>` per lavori grossi
- Ogni fase termina con: build verde, test verdi, app avviabile e funzionante
- Importi: `Long` centesimi nel DB → `BigDecimal` nel dominio → `String` localizzata nella UI (mai il contrario)
- Nessuna stringa hardcoded: tutto in `strings.xml` (IT + EN) fin dal primo giorno

## Decisioni architetturali chiave (ADR sintetici)

| # | Decisione | Motivazione |
|---|-----------|-------------|
| 1 | Importi come `Long` (centesimi) in Room, `BigDecimal` nel dominio | Room non supporta BigDecimal; i Long evitano errori di arrotondamento e ordinano/aggregano in SQL |
| 2 | Trasferimento = singolo record con `fromAccountId`/`toAccountId` | Evita disallineamenti di due movimenti gemelli |
| 3 | Saldo account = `initialBalance + Σ movimenti` calcolato via query, mai denormalizzato | Single source of truth, niente saldi corrotti |
| 4 | Ricorrenze: generazione via WorkManager + catch-up all'avvio dell'app | Copre device spenti/Doze; WorkManager da solo non basta |
| 5 | Backup = export JSON versionato (schema con `version`) su Drive App Data | Più robusto di copiare il file .db tra versioni di schema Room; restore = import |
| 6 | Package-by-feature in modulo singolo `:app` per il MVP | Multi-modulo solo se/quando i tempi di build lo giustificano |
| 7 | Date: `Instant` UTC + offset salvato | Raggruppamenti giornalieri corretti anche cambiando timezone |
| 8 | Statistiche escludono TRANSFER e ADJUSTMENT a livello di query | Regola di dominio, non filtro UI |
| 9 | min SDK 33 (Android 13) | Dynamic color e `POST_NOTIFICATIONS` con un solo code path; niente fallback né supporto device legacy |
| 10 | Export Google Sheets rimandato a v1.5 | Lo scope OAuth `spreadsheets` è "sensitive" e richiede la verifica Google: fuori dal percorso critico del MVP |
| 11 | Navigation 3 (`androidx.navigation3`) al posto di Navigation Compose/Nav2 | Stabile da novembre 2025, raccomandata da Google per la produzione; back stack come stato Compose di proprietà dello sviluppatore, coerente con il nostro modello a single source of truth. Progetto greenfield: nessun costo di migrazione |
| 12 | Domain layer pragmatico: Use Case solo dove c'è logica di dominio reale | Ricorrenze, rettifiche, statistiche, rimborsi, backup sì; per il CRUD banale il ViewModel usa direttamente il Repository. Evita boilerplate passacarte (per Google il domain layer è opzionale) |
| 13 | Backup manuale su file (SAF) accanto al backup Drive, stesso formato JSON | Backup completo possibile senza account Google (coerente coi principi) e portabilità totale dei dati; un solo code path di export/restore, nessun permesso di storage richiesto |
| 14 | targetSdk/compileSdk fissati esplicitamente (attualmente 36), mai "ultimo stabile" implicito | Build riproducibili e niente ricerca della versione corrente a ogni intervento; l'aggiornamento è una chore deliberata e testata. Nota: Google Play richiede comunque un targetSdk recente (policy annuale), quindi la chore va pianificata quando esce una nuova release stabile di Android |
| 15 | Palette brand statica di default, dynamic color opt-in da Impostazioni | Identità visiva riconoscibile su Play Store e screenshot coerenti tra device; Material You resta disponibile come scelta esplicita dell'utente. Rivede la parte "solo dynamic color" dell'ADR 9 (min SDK 33 resta invariato) |
| 16 | ~~Importi inseriti con la tastiera di sistema (`KeyboardType.Decimal`) in tutti gli editor; rimosso il tastierino custom~~ **Rivisto dall'ADR 31: il tastierino in-app torna, su tutti i campi importo** | Coerenza tra tutti i campi importo, accessibilità nativa (TalkBack, Switch Access, incolla, tastiera hardware) e meno codice da mantenere. Le regole di dominio restano garantite dal mapper importi, che protegge ogni campo dall'overflow |
| 17 | Backup cloud (Google Sign-In + Drive App Data + backup automatico) fuori dal percorso v1.0: il backup della release è quello manuale locale su file (ADR 13); la parte cloud è una fase dedicata da valutare a fine roadmap | Nessun account Google nel percorso critico, coerente con offline/privacy-first; il formato JSON versionato (ADR 5) resta l'unico code path di export/restore, quindi l'eventuale fase cloud lo riusa senza migrazioni. Decisione di prodotto di luglio 2026 |
| 18 | Budget mensili: un budget complessivo opzionale (categoryId NULL) più budget per singola categoria di spesa, con valuta esplicita; la spesa confrontata è quella "statistica" (rimborsi nettati, TRANSFER/ADJUSTMENT/esclusi/pending mai contati), non quella di cassa; unicità del budget complessivo garantita a livello applicativo, perché l'indice UNIQUE di SQLite non vincola i NULL | I budget devono combaciare con gli aggregati delle statistiche; nelle categorie "entrambi" le entrate pure non riducono il consumato. Soglie (80%/100%) confrontate in aritmetica intera sui minor units, mai float |
| 19 | Notifiche soglia budget (80%/100%): use case idempotente con watermark mensile per budget, invocato sia dal worker giornaliero delle ricorrenze sia da un watcher reattivo application-scoped (segnali di spesa debounced) | Il worker copre gli addebiti automatici a device fermo, il watcher notifica entro pochi istanti una spesa manuale che supera la soglia; il watermark rende la doppia via innocua (una notifica per soglia, per mese, per budget) |
| 20 | Carta di credito a saldo come nuovo `AccountType.CREDIT_CARD` con saldo negativo, non come strumento separato: le spese sono EXPENSE sul conto carta (contate nelle statistiche/budget alla data d'acquisto), l'addebito dell'estratto è un singolo TRANSFER dal conto collegato che azzera il ciclo (escluso dalle statistiche come ogni trasferimento). Ciclo calcolato da una funzione pura (giorno di chiusura e di addebito configurabili, mesi corti col clamp), idempotenza via watermark sul conto seminato alla creazione così la storia pregressa non viene mai riaddebitata. Nessuna FK su `linkedAccountId` (self-reference: integrità in logica applicativa). L'importo dell'estratto è la negazione della somma dei soli movimenti propri del conto carta nel ciclo | Riusa il modello contabile esistente (trasferimento a record singolo, ADR 2; saldo calcolato, ADR 3; esclusione trasferimenti dalle statistiche, ADR 8) senza doppio conteggio: l'acquisto pesa nel mese in cui avviene, l'estratto è cassa e non statistica, il saldo totale riflette il debito come patrimonio netto reale. Auto-post e conferma riusano il pattern ricorrenze. Il bancomat resta fuori dall'app (si registra sul conto corrente): preleva subito dal conto, non è un contenitore di denaro |
| 21 | Tassonomia carte esplicita: il tipo generico CARD è rimosso dall'enum e sostituito da tipi specifici, `DEBIT_CARD` (poi ritirato dall'ADR 22) e `PREPAID_CARD`. La carta di credito non ha saldo iniziale: parte sempre da zero e il debito già maturato si inserisce con la rettifica saldo, che essendo un movimento entra nel ciclo e viene addebitata col prossimo estratto | Un tipo "Carta" generico crea attrito ("la Postepay è Carta o Altro?"); prepagata (contenitore autonomo) e carta di credito hanno semantiche diverse. Il saldo iniziale su una carta di credito sarebbe debito fantasma: non è un movimento, quindi nessun estratto potrebbe mai addebitarlo e il conto non tornerebbe mai a zero |
| 22 | `DEBIT_CARD` ritirato (le righe diventano CHECKING) e sostituito dall'educazione contestuale: ogni tipo di conto mostra nell'editor una descrizione d'uso sotto il selettore, e quella del conto corrente spiega che le spese con bancomat/carte di debito si registrano lì. Nuovo tipo `SAVINGS` (conto di risparmio): contenitore alimentato con trasferimenti, che alla preselezione imposta "Includi nel budget" su off (la scelta esplicita dell'utente vince sempre). Niente tipo dedicato per investimenti/titoli: fuori scope per VISION (niente quotazioni, offline-first), la liquidità si traccia col conto di risparmio | Una carta di debito non è un contenitore di denaro (spende dal conto corrente): un tipo la cui guida ne scoraggia l'uso è un errore di tassonomia. Il risparmio invece è il "recinto" più richiesto: la semantica esiste già nel flag `isIncludedInBudget` (ADR 18), quindi il tipo aggiunge solo il default corretto senza nuovo schema; l'obiettivo con target resta agli Obiettivi di risparmio (v2.0) |
| 23 | Storia delle migration azzerata a un unico baseline v1 (schema corrente), una tantum finché l'app non è pubblicata: rimossi i vecchi oggetti `Migration`, gli schemi esportati e i relativi test; `SaldoDatabase.version` torna a 1 e `1.json` viene rigenerato dalle entità correnti. La POLICY resta invariata e vincolante dai prossimi giri: ogni cambio di schema richiede una `Migration` esplicita, un bump di versione e un test sullo schema esportato; mai `fallbackToDestructiveMigration` | Le vecchie migration servivano solo a portare avanti i device di test dello sviluppatore: nessun database esiste in produzione, quindi non c'è storia da preservare e la loro presenza era solo debito. Un baseline unico elimina anche una classe di bug reale: lo schema creato da zero divergeva da quello migrato (colonne con `DEFAULT` solo via `ALTER TABLE`), divergenza che aveva già causato il crash all'avvio su installazione pulita |
| 26 | `savings_goals` ripiegata nel baseline v1 dopo un crash all'aggiornamento in-place sul device di test: ultima collapse-una-tantum (app non pubblicata, un solo device, dati svuotabili). Policy resa più stringente per evitare la ripetizione: `SALDO_DATABASE_VERSION` è l'unica fonte di verità della versione schema, e ogni cambio di schema va coperto da due test generici, non uno per migrazione: un `MigrationsTest` strumentato (applica tutte le migration fino alla versione corrente, validando contro gli schemi esportati) e un `MigrationChainTest` JVM che gira in CI senza device (verifica che la catena sia contigua e raggiunga la versione corrente, intercetta il bump di versione senza migrazione). Dai prossimi giri si preferisce una migrazione forward reale, niente collasso | Ogni collasso del baseline è tecnicamente un downgrade di versione per un device già sulla versione più alta, che Room rifiuta: è la causa dei crash "all'aggiornamento" ripetuti. I migration test erano solo strumentati e in CI non gira un emulatore, quindi la divergenza sfuggiva alla verifica basata su build: il guard JVM chiude la parte strutturale in CI, quello strumentato resta da eseguire su device prima di rilasciare uno schema |
| 25 | Obiettivi di risparmio come target sovrapposto a un conto `SAVINGS` (modello pot/vault), non un registro di contributi separato: il "risparmiato" è il saldo calcolato del conto collegato (ADR 3), alimentato dai trasferimenti manuali o ricorrenti (ADR 24). Un obiettivo per conto (UNIQUE su `accountId`, FK `ON DELETE CASCADE`). Suggerimento mensile = (target - risparmiato) / mesi rimanenti alla data obiettivo, arrotondato per eccesso in minor units; proiezione "in linea" e data stimata derivate dall'equivalente mensile dei soli trasferimenti ricorrenti same-currency verso quel conto | Il modello account-linked è l'unico onesto con la filosofia Saldo (single source of truth, denaro sempre nei conti, saldo calcolato): un registro di contributi separato creerebbe "risparmiato" fantasma scollegato dai saldi reali, contro la premessa "dove finiscono i miei soldi". Copre entrambe le diciture di VISION ("alimentati manualmente o collegati a un account dedicato"): l'alimentazione manuale è un trasferimento registrato verso il conto. Zero nuove meccaniche di denaro; riusa `SAVINGS` (ADR 22) e i trasferimenti ricorrenti (ADR 24). La logica di proiezione/suggerimento (mesi corti, arrotondamenti) giustifica un use case (ADR 12) |
| 27 | Sparkline del saldo (30 giorni) nella hero card della Dashboard disegnata con Canvas Compose, non Vico: query giornaliera dedicata (net per giorno locale, stesse regole del saldo totale) cumulata in un use case, geometria con interpolazione monotona | Componente decorativo senza assi, marker o scroll: Vico resta confinato alle statistiche. Il Canvas da pieno controllo su smoothing, riempimento e reveal; la proiezione a pixel e solo presentazione, l'aritmetica resta nel dominio (ultimo punto = saldo totale in card, invariante testata) |
| 28 | Recap mensile "Saldo Wrapped" renderizzato interamente on-device: pagine a storia su un mese concluso con le stesse query e la stessa semantica delle statistiche; immagine riassuntiva 1080x1920 composta off-screen (GraphicsLayer, PNG in cache, FileProvider, share sheet: stessa via dell'export CSV). Rivisto nella Fase 10.3: schermata e immagine condivisa seguono il tema corrente dell'app (non piu dark fisso), principio "condividi quello che vedi" | Le cifre del recap coincidono con la schermata Statistiche per costruzione (ADR 8, rimborsi nettati). Zero permessi e zero rete: il PNG lascia il device solo tramite lo share sheet scelto dall'utente. Il mese corrente non si recappa: i suoi numeri cambiano ogni giorno. Il dark forzato contraddiceva il rispetto del tema scelto in Impostazioni (chiaro/scuro/sistema, dynamic color opt-in), un takeover scuro su utente in tema chiaro non e premium |
| 29 | Donut delle categorie disegnato con Canvas Compose al posto dell'API pie di Vico (sperimentale nella 3.x): geometria pura testata (angoli con gap, hit-test del tap), sweep-in d'ingresso, tap sulla fetta che apre lo stesso drill-down delle righe. I grafici cartesiani restano su Vico (assi, marker, scroll e drill-down gia maturi), rifiniti dentro l'API verificata sui binari 3.2.3 (colonne a pillola, area sfumata sotto la linea saldo, animateIn) | L'API pie era l'unico pezzo sperimentale in produzione e non espone il tap sulle fette; il Canvas custom rimuove quella dipendenza e aggiunge interazione, riusando l'approccio della sparkline (ADR 27). Riscrivere anche i cartesiani sarebbe costo e rischio di regressione senza guadagno: il restyling basta |
| 30 | Proiezione saldo a fine mese come coda tratteggiata della sparkline (non una riga dedicata): finestra storica fissa a 30 giorni con in coda i soli giorni residui del mese, stesso passo per giorno. Forecast calcolato da `BalanceForecastCalculator` (puro, nel dominio): camminata giorno per giorno dal saldo totale sottraendo la media della spesa giornaliera del mese (spesa mese corrente / giorni trascorsi) e applicando alla loro data le ricorrenze a importo fisso, spese e anche entrate; regole a importo variabile e valute diverse escluse. Pill "≈ importo" e tratteggio marcano la stima; la media include anche le ricorrenze gia addebitate (leggera sovrastima, accettata) | Zero ingombro verticale aggiuntivo e semantica visiva immediata (tratteggio = incerto), coerente con la hero card premium (ADR 27). La finestra trailing di 30 giorni garantisce che la previsione occupi al massimo circa meta della larghezza (1 del mese con mese di 31 giorni): mai una sparkline di sola previsione, il dato reale domina sempre. Le entrate ricorrenti fisse sono incluse perche uno stipendio a fine mese e la differenza tra una coda che affonda e una che risale: escluderlo renderebbe la stima sistematicamente pessimista |
| 32 | Il widget di inserimento rapido si ferma alla **scelta**: tipo e categoria sul launcher in Glance, importo in una activity traslucida (`QuickEntryActivity`) che apre una sheet sopra il launcher col tastierino vero dell'ADR 31. Il tastierino non entra nel widget. Il conto non e un controllo del widget ma della sua configurazione per istanza, con ricaduta sulla catena di default dell'app quando il conto configurato sparisce. Le icone categoria sono rasterizzate a runtime dagli `ImageVector` esistenti, non duplicate in vector drawable. Le dimensioni sono tre layout disegnati (2x2, 4x2, 4x3), non un layout fluido | Ogni tap su un widget e un broadcast al processo app piu un giro `RemoteViews` fino al launcher: 60-150ms a processo caldo e molto di piu a freddo, perche il primo tap deve avviare il processo. Su un importo sono quattro o cinque tap, senza haptics e senza animazioni, con una scrittura su disco per cifra: sarebbe l'unica superficie dell'app con un tastierino peggiore di quello appena costruito. La scelta invece e esattamente cio in cui un widget e bravo - contenuto leggibile a colpo d'occhio, una sola interazione - e il numero di tap resta quello della nota dell'utente (categoria + cifre + Salva). Il conto in configurazione toglie un'interazione a runtime e regge il caso "due conti, due widget". La rasterizzazione evita una seconda mappa di icone che divergerebbe dalla prima al primo aggiornamento. `RemoteViews` non ha una passata di misurazione a cui appoggiarsi, quindi una dimensione o e progettata o e rotta |
| 24 | Trasferimenti ricorrenti dentro il motore delle ricorrenze esistente (non un modulo separato): la regola ricorrente supporta il tipo `TRANSFER` aggiungendo la gamba di destinazione (con FK verso `accounts`) e riusa `RecurrenceCalculator`, il worker, il catch-up, la coda pending e le notifiche. Automatico consentito solo per conti stessa valuta (destinazione = sorgente, esatto); i trasferimenti cross-currency sono forzati in modalità CONFIRM: la regola fissa la sola gamba sorgente e l'importo ricevuto è inserito alla conferma di ogni occorrenza. I trasferimenti restano esclusi dalle statistiche (ADR 8). L'hub mostra "Risparmio pianificato: X/mese" derivato dai soli trasferimenti verso conti `SAVINGS` | Il motore e il modello movimenti sono già type-agnostic (ADR 2/3/4): un modulo separato duplicherebbe engine, worker e coda pending contro ADR 12. Congelare un tasso di cambio e riapplicarlo ogni mese sarebbe disonesto (il cambio deriva): la modalità conferma, che già esiste per gli importi variabili, cattura l'importo reale ricevuto. Il riepilogo risparmio è il seme onesto degli Obiettivi di risparmio (v2.0) senza costruirne il modulo, riusando il tipo conto `SAVINGS` (ADR 22) |
| 31 | Il tastierino importi in-app torna, e vale per **tutti** i campi importo dell'app (revisione dell'ADR 16). Un solo componente in `core/designsystem` disegnato con soli token di tema, in due forme: pannello agganciato all'`EditorBottomBar` sulle schermate piene e variante compatta dentro dialog e sheet. `HeroAmountField` smette di essere un campo di testo e diventa un display con caret, quindi l'IME di sistema non si apre piu su un importo (resta per i campi di testo, e il focus dell'uno chiude l'altro). L'aritmetica sui tasti (somme in linea) resta fuori scope | I tre motivi che avevano portato all'ADR 16 sono coperti: la coerenza, perche il tastierino non e piu solo su una schermata ma su tutte; l'accessibilita, perche il display e `Role.Button` con `contentDescription` che recita l'importo, i tasti sono clickable annunciati, la tastiera hardware scrive nel campo con focus e il long-press incolla; il codice, perche la logica di editing e una funzione pura e la sanitizzazione resta quella condivisa di `MoneyInput`. In cambio si ottiene quello che l'ADR 16 non poteva dare: l'altezza del pannello e dell'app e il pannello si chiude, che e la leva con cui la schermata di inserimento garantisce due righe di categorie sopra la piega, e l'aspetto non dipende piu dalla tastiera che l'utente ha installato (barra GIF/sticker inclusa) |

---

# Roadmap v1.0 (MVP)

## Fase 0 - Setup progetto

- [x] Creazione progetto Android Studio (Kotlin, Compose, min SDK 33, targetSdk 36, applicationId `com.callbackdev.saldo`)
- [x] Version Catalog (`libs.versions.toml`) con tutte le dipendenze
- [x] Setup Hilt + KSP
- [x] Struttura package-by-feature: `core/{database,designsystem,common,domain}` + `feature/*`
- [x] Tema Material 3: dynamic color, light/dark (min SDK 33: nessun fallback necessario)
- [x] Navigation 3: route come `NavKey`, back stack con `rememberNavBackStack`, `entryProvider` + `NavDisplay`; scaffold base (bottom bar: Dashboard, Movimenti, Statistiche, Impostazioni)
- [x] Setup test: JUnit5 per unit test JVM (plugin android-junit5), JUnit4 per strumentati e Compose UI Test, MockK, Turbine, Room in-memory (dipendenza `room-testing` nel catalog, si collega in Fase 1 insieme a Room)
- [x] CI GitHub Actions: build + lint + unit test su ogni push
- [x] `.editorconfig`, ktlint o detekt (scelto detekt)
- [x] README con istruzioni di build

## Fase 1 - Data layer (fondamenta)

- [x] Entity Room: `AccountEntity`, `CategoryEntity`, `TransactionEntity`, `TagEntity` + cross-ref, `RecurringRuleEntity`
- [x] Enum `TransactionType` (EXPENSE, INCOME, TRANSFER, ADJUSTMENT)
- [x] TypeConverter: enum (Instant e LocalDate salvati come `Long` epoch direttamente sulle entity)
- [x] DAO con query fondamentali:
  - [x] saldo per account (`initialBalance + Σ`) come Flow
  - [x] saldo totale (solo account inclusi e non archiviati)
  - [x] movimenti per giorno/mese/intervallo (query per intervallo `[start, end)`; giorno e mese sono intervalli calcolati dal chiamante)
  - [x] aggregati per categoria (esclusi TRANSFER/ADJUSTMENT)
- [x] Modelli di dominio + mapper (centesimi ↔ BigDecimal)
- [x] Repository (interfacce dominio + implementazioni Room)
- [x] Seed categorie predefinite alla prima apertura (IT/EN in base alla locale)
- [x] Unit test: mapper, calcolo saldi, query aggregate (Room in-memory)

## Fase 2 - Account

- [x] Lista account con saldo corrente
- [x] Creazione/modifica account (nome, tipo, valuta, saldo iniziale, colore, icona, incluso nel totale)
- [x] Archiviazione account (+ vista archiviati)
- [x] **Rettifica saldo**: inserisco il saldo reale → l'app genera il movimento ADJUSTMENT con la differenza
- [x] Eliminazione account: consentita solo se senza movimenti, altrimenti proporre archiviazione
- [x] Test: rettifica saldo, esclusione archiviati dal totale

## Fase 3 - Movimenti (CRUD)

- [x] Schermata inserimento spesa/entrata: tastierino importo subito attivo, categoria a griglia, account di default preselezionato, data = oggi modificabile
- [x] Obiettivo UX verificato: spesa tipica in ≤ 3 tap + importo (FAB → categoria → salva; default coperti da unit test, UI test strumentato rimandato a quando ci sarà un emulatore)
- [x] Inserimento trasferimento (da → a, importo; due importi se valute diverse)
- [x] Lista movimenti raggruppata per giorno con totali giornalieri
- [x] Modifica movimento
- [x] Eliminazione con swipe + undo (Snackbar)
- [x] Tag: creazione inline e assegnazione
- [x] Flag "escludi dalle statistiche" e flag "rimborso" (versione semplificata MVP)
- [x] Test: ViewModel inserimento, effetti sul saldo per ogni tipo

## Fase 4 - Categorie

- [x] Lista categorie divise spese/entrate (tab Spese/Entrate; le categorie di tipo "entrambi" compaiono in entrambe)
- [x] Crea/modifica: nome, colore (palette), icona (set Material Symbols), tipo (con anteprima live)
- [x] Eliminazione con riassegnazione movimenti (dialog: scegli categoria di destinazione; se nessuna categoria compatibile esiste, i movimenti restano senza categoria)
- [x] Riordino manuale (drag): handle di trascinamento per riga, `sortOrder` globale come unica fonte di verità

## Fase 5 - Dashboard "Oggi"

- [x] Card saldo totale + dettaglio account espandibile (con richiamo "Gestisci account")
- [x] Card oggi (spese/entrate/netto)
- [x] Card mese corrente + confronto con stesso giorno mese precedente
- [x] Card abbonamenti del mese (totale + prossimo addebito) - placeholder (teaser) finché Fase 6 non è pronta
- [x] Ultimi 5–7 movimenti con tap → dettaglio
- [x] FAB con 3 quick action (speed-dial: spesa/entrata/trasferimento, con tipo preimpostato)
- [x] Empty state prima apertura (CTA: crea il primo account)
- [x] Performance: dashboard reattiva via Flow combinati, nessun ricalcolo manuale

> Nota valuta: le card Oggi/Mese e il saldo totale usano la valuta principale (quella condivisa dalla maggioranza degli account nel totale); i movimenti in altre valute sono esclusi dalle somme finché non arriva la conversione (v2.0). I totali Oggi/Mese sono di cassa: includono i movimenti marcati "escludi dalle statistiche" (che restano esclusi solo dalle statistiche di Fase 7).
> Punti d'accesso: gli account si gestiscono dalla card saldo della Dashboard (non più da Impostazioni); le categorie restano in Impostazioni.

## Fase 6 - Ricorrenze

> Completata in tre incrementi. Incremento 1: motore, vista Abbonamenti, editor CRUD, card dashboard, generazione automatica a importo fisso con catch-up all'avvio. Incremento 2: WorkManager periodico, notifiche informative e di conferma, modalità conferma / importo variabile con movimento "pending" (migration 2→3), conferma/modifica/salta in-app (schermata "Da confermare" + card dashboard). Incremento 3 (luglio 2026): entrate ricorrenti con hub "Ricorrenze" a tab e notifica di pre-rinnovo configurabile (radar, migration 4→5).

- [x] `RecurringRuleEntity`: frequenza, giorno, inizio/fine, importo fisso o variabile, modalità (auto/conferma), lastGeneratedDate (schema dalla Fase 1; in Fase 6 aggiunti `color`/`icon` per l'avatar, migration 1→2)
- [x] Motore di generazione idempotente (rieseguibile senza duplicati) + gestione mesi corti (31 → ultimo giorno) - `RecurrenceCalculator`
- [x] WorkManager periodico + catch-up all'avvio app (catch-up in `MainActivity`; job periodico giornaliero via `RecurringGenerationWorker` + Hilt)
- [x] Modalità automatica: crea movimento + notifica informativa
- [x] Modalità conferma / importo variabile: movimento "pending" (escluso dai saldi finché non confermato) + notifica di conferma (la notifica apre l'app alla schermata "Da confermare", dove avvengono conferma/modifica/salta; azioni inline nella notifica: possibile rifinitura futura)
- [x] CRUD regole ricorrenti; eliminazione (conferma; i movimenti già registrati restano, nessun movimento futuro è pre-generato in questo modello)
- [x] **Vista Abbonamenti**: lista, costo mensile equivalente, totale mese e proiezione annua
- [x] Collegamento card dashboard
- [x] Test approfonditi del motore: mesi corti, anni bisestili, catch-up dopo N giorni, idempotenza (DST: evitato generando i movimenti a mezzogiorno; il motore lavora su `LocalDate`)
- [x] **Entrate ricorrenti**: la vista Abbonamenti diventa l'hub "Ricorrenze" con due tab (Abbonamenti / Entrate), ognuno con totale mensile, proiezione annua e prossimo addebito/accredito; l'editor prende il tipo dal tab di provenienza (il motore supportava già `INCOME`)
- [x] **Radar pre-rinnovo**: notifica opzionale prima dell'addebito/accredito ("Netflix si rinnova tra 3 giorni"), anticipo configurabile 1/2/3/7 giorni da Impostazioni (default: off); watermark `lastReminderEpochDay` per regola (migration 4→5) garantisce una sola notifica per occorrenza anche se il worker salta giorni

## Fase 6.5 - Design system e omogeneità (dalla review di luglio 2026)

> Fase intermedia nata dalla review completa dell'app (bug, refactor, omogeneità UI). Fatta prima della Fase 7 così statistiche e schermate future nascono direttamente sui componenti condivisi.

- [x] Componenti condivisi `EmptyState` e `LoadingState` in `core/designsystem/component`, adottati da tutte le schermate (prima: 5 copie hand-rolled, una divergente)
- [x] Tipografia: famiglia Inter (variable font OFL embeddato in `res/font`); headline/title a peso SemiBold in `SaldoTypography`; sugli importi `tabularNumbers()` con `tnum` (figure tabulari) + `zero` (zero barrato)
- [x] `MoneyColors`: ruoli semantici unici per colorare il denaro (income/expense/neutral/negative) al posto di 3 regole divergenti tra dashboard, conti e registro
- [x] Palette brand statica di default (seed teal, tertiary verde così le entrate leggono verde) + dynamic color opt-in e tema chiaro/scuro/sistema in Impostazioni, persistiti in DataStore (ADR 15)
- [x] Avatar squircle (`AvatarShape`) uniformi: categorie ed empty state usavano `CircleShape`
- [x] Haptics: tastierino importi (rimosso con l'ADR 16, subentrano i feedback della tastiera di sistema), conferma swipe-delete, speed-dial FAB, presa/rilascio drag reorder
- [x] Refactor dashboard: aggregati calcolati in SQL (query unica multi-finestra `observeDashboardTotals` + `LIMIT` sui movimenti recenti) invece di caricare l'intero registro in memoria; chiude anche il punto performance parcheggiato in Fase 9
- [x] Error handling uniforme negli editor: guardia anti doppio-tap, `suspendRunCatching`, evento `WriteFailed` con snackbar

## Fase 7 - Ricerca, filtri e statistiche

> Completata a luglio 2026 in quattro incrementi: motore filtri + ricerca nel registro, data layer statistiche + selettore periodo, grafici Vico, drill-down. I test strumentati delle nuove query aggregate (`TransactionDaoStatsTest`) sono scritti ma da eseguire su device.

- [x] Filtri combinabili (data con preset, categorie, account, tipo, importo, tag) come chip
- [x] Ricerca full-text su descrizione (e nota; in-memory con normalizzazione Unicode, insensibile ad accenti e maiuscole)
- [x] Totale della vista filtrata sempre visibile
- [x] Eliminazione in blocco della vista filtrata (luglio 2026): menu overflow del registro, bottom sheet con due modalità - "Ricalcola i saldi" e "Conserva i saldi correnti" (rettifica `ADJUSTMENT` di riporto per conto, stesso meccanismo di `AdjustBalanceUseCase`, esclusa dalle statistiche) - anteprima impatto saldi, export prima di eliminare e undo. Data layer `TransactionDao.deleteByIds`/`deleteAndInsert`, dominio `DeleteFilteredTransactionsUseCase` + `CarryOverCalculator`. Test JVM; `TransactionDaoTest` strumentato da eseguire su device
- [x] Statistiche (Vico 3.2.3; il donut usa il pie chart di Vico, sperimentale nella 3.x, con totale al centro come overlay Compose):
  - [x] anello spese per categoria + lista percentuali (mese/anno/custom)
  - [x] barre trend spese 12 mesi
  - [x] entrate vs uscite mensili
  - [x] andamento saldo nel tempo (saldi di fine mese: somma saldi iniziali + net mensile cumulato, entrambe le gambe dei trasferimenti, solo account inclusi nel totale)
  - [x] spese per account (lista con barre proporzionali: più leggibile di colonne con 2-5 account)
- [x] Drill-down: tap su grafico → lista filtrata (route dedicata pushata sopra le statistiche; righe di anello/account navigano al tap, le colonne mostrano il marker con bottone "Vedi i movimenti di <mese>" per evitare navigazioni accidentali durante lo scrub)
- [x] Verifica esclusione TRANSFER/ADJUSTMENT e trattamento rimborsi (rimborso = spesa negativa nelle query; coperta da `TransactionDaoStatsTest` strumentato - da eseguire su device, nessun emulatore in CI - e dai test JVM di StatsViewModel e del motore filtri)

## Fase 8 - Backup, export, import (locale)

> Ridefinita a luglio 2026 (ADR 17): questa fase copre solo il backup locale su file. La parte cloud (Google Sign-In, upload su Drive App Data con rotazione, backup automatico WorkManager, restore guidato al primo avvio) è spostata nella "Fase cloud" in fondo alla roadmap, da valutare a fasi concluse. Il restore guidato al primo avvio rientra nell'onboarding di Fase 9.

- [x] Formato export JSON versionato (schema `version: 1`) di tutti i dati (`core/domain/backup`: marker `saldo-backup`, campi primitivi stabili, `ignoreUnknownKeys`, errori tipizzati per file estraneo/versione futura/file corrotto)
- [x] **Backup manuale su file**: export via SAF (`ACTION_CREATE_DOCUMENT`), nome `saldo-backup-YYYY-MM-DD.json`, avvertenza in UI "file non cifrato"; schermata dedicata in Impostazioni > Dati con data dell'ultimo backup persistita
- [x] Restore da file di backup manuale (JSON, via SAF `ACTION_OPEN_DOCUMENT`), guidato da Impostazioni: anteprima del contenuto (data, versione app, conteggi per tabella) e conferma esplicita; sostituzione atomica in transazione (rollback su errore, id preservati) + catch-up ricorrenze subito dopo il ripristino
- [x] Export CSV (separatore `;`/`,` configurabile e persistito, con convenzione decimali abbinata; rispetta i filtri attivi del registro; condivisione via Share Sheet con FileProvider)
- [x] Test: round-trip export→import senza perdita dati (codec JSON, mapper entity↔schema campo per campo, use case con repository fake; builder CSV: escaping, separatori, BOM, trasferimenti multi-valuta)

## Fase 9 - Impostazioni, i18n, rifinitura

- [x] Impostazioni: valuta principale (override esplicito della regola a maggioranza, sezione "Preferenze"), account di default (preselezione editor: default esplicito -> ultimo usato -> primo attivo), primo giorno settimana (Lun/Sab/Dom, consumato dal nuovo preset "Questa settimana" nei filtri del registro) (tema: già fatto in Fase 6.5; backup: già fatto in Fase 8)
- [x] Onboarding al primo avvio (5 pagine: benvenuto, privacy, valuta, primo conto con saldo iniziale, notifiche contestuali; proposta di ripristino da backup nella pagina conto). Gate in `MainViewModel`: le installazioni esistenti (flag assente ma DB con conti) non lo vedono mai. Il permesso notifiche non è più chiesto a freddo all'avvio: solo in onboarding o attivando il radar rinnovi
- [x] Revisione completa stringhe IT + EN (IT: entità uniformata su "conto/conti" al posto del misto account/conto; EN: uniformata su "transaction" al posto del misto movement/transaction; parità chiavi verificata, nessuna stringa hardcoded)
- [x] Pass di accessibilità: contentDescription verificate su tutti gli interattivi a sola icona; riassunti TalkBack sui 4 grafici Vico (canvas muto); CTA editor/onboarding a `heightIn(min)` per il font scaling; spese/entrate distinte da segno esplicito (`formatSigned`) oltre che dal colore; righe cliccabili con merge semantico automatico. Verifica manuale TalkBack/200% su device: pending (nessun emulatore in questo ambiente)
- [x] Empty state e stati di errore su tutte le schermate (audit: empty/loading già coperti ovunque; aggiunta gestione errori di scrittura con snackbar a conti (archivia/elimina/rettifica), registro (elimina/undo), da confermare (conferma/salta) e riordino categorie, che prima potevano crashare su un errore Room)
- [x] Performance: registro appiattito in item lazy per riga (prima un item monolitico per giorno) con key e contentType stabili su header/righe/spaziatori, così con migliaia di record compongono e riciclano solo le righe visibili; l'aspetto a card raggruppata è preservato con forme a segmento. Paging3 non introdotto: il motore filtri e la ricerca full-text sono in-memory per design (un solo code path), da rivalutare solo se una misurazione su device con migliaia di record mostrasse problemi. Baseline profile spostato in Fase 10 (richiede modulo macrobenchmark e run su device, non disponibile in questo ambiente)

## Fase 9.5 - Budget, spendibile oggi e dashboard configurabile (anticipata dalla v1.5)

> Anticipo deciso a luglio 2026, prima della release v1.0: la tabella budget nasce nello schema che va in produzione (migration 5->6) e il campo `budgets` entra nel backup JSON senza bump di versione. Il Widget resta in v1.5. Design: ADR 18 e 19.

- [x] Budget: entità (`budgets`, migration 5->6, unique su categoryId), modello complessivo + per categoria di spesa, CRUD con schermata dedicata (hero card del complessivo con residuo e barra, categorie ordinate per vicinanza al tetto) ed editor (scope picker, importo nella valuta principale, eliminazione con conferma)
- [x] Indicatori 🟢🟡🔴: `ThresholdProgressBar` condivisa nel design system, ruolo `warning` ambra in `MoneyColors` (light/dark), soglie esatte in minor units; colore mai da solo (percentuale testuale sempre presente, icona esplicita oltre il 100%)
- [x] Card dashboard Budget: complessivo con residuo e barra + top 3 categorie per fraction, tap verso la schermata budget
- [x] Notifiche 80%/100%: canale `budget_alerts`, `CheckBudgetThresholdsUseCase` con watermark mensili, trigger doppio (worker giornaliero + `BudgetThresholdWatcher` reattivo debounced su application scope)
- [x] Backup: campo additivo `budgets` nello schema version 1 (file vecchi = lista vuota), watermark inclusi per non ri-notificare dopo un restore, conteggio nell'anteprima di ripristino
- [x] **Spendibile oggi** (dall'idea in Note e appunti): card in evidenza sotto il saldo = budget mensile - spesa statistica - pending del mese (impegnati ma non confermati) - ricorrenze a importo fisso in arrivo entro fine mese (`UpcomingChargesCalculator` puro, stesso floor di generazione della dashboard); quota giornaliera arrotondata per difetto; superamento su `errorContainer` con icona e testo espliciti; visibile solo con un budget complessivo
- [x] Dashboard configurabile: sezione "Dashboard" in Impostazioni con 3 switch (Spendibile oggi, scheda budget, ultimi movimenti), boolean DataStore default visibile; le card core (saldo, oggi/mese, da confermare, ricorrenti) restano fisse
- [x] Voce "Budget" in Impostazioni > Gestione
- [x] Test: mapper (minor units, epoch month), soglie esatte e ordinamento (`ObserveBudgetProgressUseCaseTest`), dedupe watermark (`CheckBudgetThresholdsUseCaseTest`), calculator ricorrenze in arrivo (floor, mesi corti, variabili escluse), safe-to-spend (pending contato una sola volta, FLOOR, ultimo giorno del mese), round-trip backup esteso, ViewModel dashboard; strumentati scritti ma da eseguire su device (migration 5->6, DAO budget con CASCADE e unique, query spend)

## Fase 9.6 - Rifinitura premium e ottimizzazioni (2ª review, luglio 2026)

> Giro di rifinitura "da app premium" guidato dall'utente sulla base di una seconda review completa, iterato su APK di prova da GitHub (versionCode 50 -> 54, versionName 0.9.11 -> 0.9.15). Nessuna modifica a dominio o schema.

- [x] App shortcut statici dal launcher (dall'idea in Note e appunti): pressione prolungata sull'icona -> Nuova spesa, Nuova entrata, Trasferimento. Icone adattive on-brand (glifo bianco su sfondo brand rosso/verde/teal), label brevi azionabili + label lunghe IT/EN. Intent instradato in `MainActivity` (`singleTop` + `onNewIntent`) verso il back stack Nav3: apre l'editor giusto una sola volta, a freddo o a caldo, senza ri-trigger su rotazione. Le label brevi sono azionate ("Nuova spesa/entrata") mentre il FAB resta a nomi asciutti (contesto "+"), scelta condivisa con l'utente (commit 519a7f9, 4bff471)
- [x] Skeleton di caricamento al posto dello spinner: `DashboardSkeleton` che ricalca il layout reale e `ListSkeleton` per le liste, con un unico pulse condiviso per schermata; adottati da Dashboard, Movimenti, Conti, Budget (commit 519a7f9)
- [x] Ricorrenze: generazione immediata al salvataggio di una regola (application scope, idempotente e mutex-guarded), così un'occorrenza già scaduta compare subito nel registro senza attendere il prossimo avvio o il worker giornaliero (commit ba1f5df)
- [x] Perf: `MoneyFormatter` mette in cache il `NumberFormat` per `(valuta, locale)` con `ThreadLocal` (`NumberFormat` non è thread-safe) invece di ricostruirlo a ogni chiamata, che pesava scorrendo le liste di importi (commit 519a7f9)
- [x] Perf: catch-up ricorrenze e parsing dell'intent shortcut solo all'avvio genuino (`savedInstanceState == null`), non a ogni ricreazione da cambio configurazione (rotazione ecc.); WorkManager copre comunque i giorni a device spento (commit 519a7f9)
- [x] `SwipeToDismiss` del registro migrato fuori dall'API deprecata `confirmValueChange`: l'eliminazione osserva lo stato assestato via `snapshotFlow`, con la sola direzione destra->sinistra abilitata (stesso comportamento, niente warning, a prova di rimozione futura dell'API) (commit 754767c)
- [x] Transizioni di schermata: valutato uno stile "espressivo" (scale shared-Z) e poi ripristinata la transizione originale slide + fade dopo verifica su device (decisione utente, condivisa). Lo scatto residuo percepito è di build debug / assenza di baseline profile, non dello spec dell'animazione: il tema è tracciato in Fase 10 (commit 754767c)
- [x] Verifica: gate `assembleDebug testDebugUnitTest lint detekt` verde in CI per ogni commit; APK di prova validato su device reale dall'utente (shortcut, skeleton, ricorrenza scaduta oggi, transizioni)

## Fase 9.7 - Fix dalla terza review completa (luglio 2026)

> Giro unico su branch dedicato (versionCode 55, versionName 0.9.16) nato da una terza review completa in parallelo su dominio, database, ViewModel, UI/UX e performance. Bug trovati e fixati elencati in "Bug conosciuti"; qui gli interventi strutturali.

- [x] Validazione semantica del payload di backup al decode (enum, codici ISO 4217, invarianti transfer, budget complessivo unico) più backstop nei mapper: un file malformato viene rifiutato all'ispezione, mai dopo aver sostituito i dati
- [x] Navigazione: back stack per tab (pattern Nav3 "multiple back stacks", API verificate sui sorgenti 1.1.4); ogni tab conserva ViewModel, scroll, ricerca e periodo tra i cambi, back invariato (exit through home)
- [x] Drill-down statistiche allineato alle query (`statsScope` nella route) e fetta "Senza categoria" nell'anello, così tutte le cifre della schermata coincidono; caricamento a finestra SQL
- [x] Perf: `distinctUntilChanged` sui flow delle preferenze (stop ai rebuild della pipeline dashboard a ogni salvataggio), filtri precompilati una volta per passata, campo di ricerca sincrono
- [x] Ticker di mezzanotte condiviso: dashboard e statistiche ri-ancorano "oggi" se lo schermo resta acceso oltre il cambio giorno
- [x] Rifinitura: `contentColorOn` per il contrasto dei glifi sui colori pieni, skeleton allineati alla geometria reale + `StatsSkeleton`, righe switch di Impostazioni interamente toccabili, ultimo chevron e ultimo avatar circolare rimossi, crossfade sulla ricerca del registro

## Fase 9.8 - Flag budget per conto e indicatori card Saldo totale (luglio 2026)

> Intervento su richiesta utente (versionCode 57 -> 58, versionName 0.9.18 -> 0.9.19). Nuovo asse di inclusione per conto indipendente dal saldo totale, indicatori nella card Saldo totale e chiusura del bug "Dashboard multi-valuta".

- [x] Flag conto `isIncludedInBudget` (default incluso, migration 7->8, schema v8): escludendolo, le spese registrate sul conto non entrano nel consumato di budget né nello spendibile. Asse ortogonale a `isIncludedInTotal`; non tocca saldo totale né statistiche. Campo additivo nel backup (nessun bump di versione)
- [x] Query di spesa budget account-aware: `observe/get StatsSpendTotal` e `observe/get CategorySpendTotals` con `INNER JOIN accounts ... AND isIncludedInBudget = 1`; `ObserveSafeToSpendUseCase` esclude anche pending e ricorrenze in arrivo dei conti esclusi
- [x] Editor conto: secondo toggle "Includi nel calcolo budget" (`InclusionToggleRow` riusata); badge "Escluso dal budget" nella lista conti
- [x] Indicatori card Saldo totale (`AccountBreakdownRow`): saldo attenuato per i conti che non contribuiscono al totale; marcatori solo in negazione (codice valuta ISO per i non-primari, icona esclusione dal totale, icona esclusione dal budget), con `contentDescription`
- [x] Bug "Dashboard multi-valuta" chiuso dalla presentazione (attenuazione + codice valuta), senza cambio alle query
- [x] Test: mapper e backup round-trip del nuovo campo, decode default additivo, safe-to-spend con conto escluso, editor e onboarding; migration 7->8 e DAO budget-spend con conto escluso scritti come strumentati (da eseguire su device)

## Fase 9.9 - Avviso modifiche non salvate negli editor (luglio 2026)

> Intervento su richiesta utente (versionCode 58 -> 59, versionName 0.9.19 -> 0.9.20). Il back da un editor con dati inseriti/modificati scartava tutto senza segnalazione; ora chiede conferma.

- [x] Componente condiviso `UnsavedChangesGuard` (`core/designsystem`): `rememberUnsavedChangesGuard(hasUnsavedChanges, onNavigateBack)` intercetta le due vie d'uscita (bottone X e back di sistema) e apre `DiscardChangesDialog` solo quando ci sono modifiche; `BackHandler` abilitato solo se dirty, così i form intatti conservano la predictive back
- [x] Dirty detection nei 5 ViewModel editor (Conto, Movimento, Ricorrenza, Budget, Categoria): snapshot dei soli campi editabili catturato quando il form è pronto (`captureBaseline()`), `hasUnsavedChanges: StateFlow<Boolean>` per confronto; rimettere un campo al valore iniziale torna "pulito"
- [x] Dialog a due opzioni (scelta di prodotto): "Scarta" (distruttiva, colore error) e "Continua a modificare", icona `WarningAmber`; stringhe in `values` e `values-it`. Salvataggio/eliminazione/record mancante escono senza passare dalla guardia
- [x] Test: unit sul dirty (create/edit, revert-to-baseline, e il conto di default preselezionato asincrono che non conta come modifica) per gli editor Conto, Categoria e Movimento; `assembleDebug testDebugUnitTest lint` verdi. Nessun impatto su dominio, query o saldi

## Fase 9.10 - Carte di credito a saldo (luglio 2026)

> Intervento su richiesta utente (versionCode 59 -> 60, versionName 0.9.20 -> 0.9.21). Nuovo tipo di conto per le carte ad addebito differito, con ciclo di fatturazione configurabile, addebito automatico o con conferma, indicatore di utilizzo e scheda "paga estratto". Design: ADR 20. Il bancomat resta deliberatamente fuori (si registra sul conto corrente).

- [x] `AccountType.CREDIT_CARD` + `CreditCardConfig` sul dominio (giorno di chiusura, giorno di addebito, conto collegato, fido, modalità auto/conferma, watermark ultimo ciclo saldato); colonne additive su `accounts` (migration 8->9, schema v9), mapper, backup (campi additivi, versione backup invariata) e validazione
- [x] `BillingCycleCalculator` puro: finestre di ciclo, date di chiusura/addebito con clamp sui mesi corti, rilevamento estratti scaduti (catch-up multi-ciclo, watermark) - con unit test
- [x] `SettleCreditCardStatementUseCase` (trasferimento singolo conto collegato -> carta che azzera il ciclo, avanzamento watermark, idempotente con mutex/transazione) e `ProcessDueCreditCardStatementsUseCase` (auto-post vs sola segnalazione in conferma); `ObserveDueStatementsUseCase` reattivo per le CTA
- [x] Aggancio al worker giornaliero delle ricorrenze e al catch-up all'avvio; `CreditCardNotifier` con canale dedicato (estratto addebitato / estratto da pagare)
- [x] Editor conto: sezione carta di credito con guida bancomat-vs-credito, stepper giorni, selettore conto collegato (stessa valuta), fido opzionale, selettore modalità di addebito; watermark seminato alla creazione (nessun riaddebito dello storico); dirty detection estesa
- [x] Schermata Conti: barra di utilizzo (utilizzato/fido con soglia ambra all'80%) e CTA "Paga estratto" con importo e data di addebito; scheda Dashboard "Estratto pronto" che porta ai Conti
- [x] Test: `BillingCycleCalculator`, settle use case (trasferimento, watermark, idempotenza, valuta non conforme), round-trip mapper del config; migration 8->9 come strumentato (da eseguire su device). Nessuna regressione su saldi/statistiche (l'estratto è un trasferimento, escluso)

## Fase 9.11 - Tassonomia carte e rifiniture dalla review carte di credito (luglio 2026)

> Secondo giro su richiesta utente (versionCode 60 -> 61, versionName 0.9.21 -> 0.9.22): review della Fase 9.10 con fix, rimozione del saldo iniziale dalle carte di credito e tipi carta espliciti al posto di "Carta" generica. Design: ADR 21. App in test su un solo device: implementazione pulita senza strati di compatibilità.

- [x] Fix da review: la CTA "Paga estratto" mostrava l'estratto più recente ma il settlement paga sempre il più vecchio (mismatch visibile solo con più cicli arretrati); extras carta (utilizzo/CTA) non più mostrate sui conti archiviati; il conto di addebito referenziato resta selezionabile anche se archiviato (stessa regola dell'editor ricorrenze, Fase 9.7)
- [x] Saldo iniziale rimosso dalle carte di credito: campo nascosto nell'editor, saldo forzato a zero al salvataggio, guida aggiornata che indica la rettifica saldo per il debito già maturato (la rettifica è un movimento: entra nel ciclo e viene addebitata col prossimo estratto)
- [x] Tipi carta espliciti: `DEBIT_CARD` (con guida contestuale: spende dal conto corrente, conto separato solo se il conto collegato non è tracciato) e `PREPAID_CARD` (contenitore autonomo, il caso Postepay); CARD rimosso dall'enum, migration dati 9->10 (righe CARD -> DEBIT_CARD, schema v10 invariato), icone dedicate (contactless, add_card) nel picker
- [x] Test: editor carta di credito (saldo zero forzato, config completa, watermark seminato alla chiusura precedente), tipo non-credito senza config, estratto più vecchio per conto nella lista; migration 9->10 strumentata (da eseguire su device)

## Fase 9.12 - Conto di risparmio, descrizioni dei tipi e ritiro della carta di debito (luglio 2026)

> Terzo giro sui tipi di conto, su richiesta utente (versionCode 61 -> 62, versionName 0.9.22 -> 0.9.23). Design: ADR 22. App in beta su un solo device: nessun codice legacy.

- [x] `DEBIT_CARD` rimosso dall'enum (migration dati 10->11: le righe esistenti diventano CHECKING, schema v11 invariato); l'educazione passa alla descrizione del conto corrente
- [x] Nuovo tipo `SAVINGS` (Conto di risparmio): icona salvadanaio predefinita, alla selezione pre-imposta "Includi nel budget" su off (l'attingere ai risparmi non consuma il budget del mese); la scelta esplicita dell'utente sul toggle vince sempre sul preset, e in modifica il valore salvato non viene mai sovrascritto
- [x] Descrizione d'uso per ogni tipo di conto: banner informativo sotto il selettore dei tipi che cambia col tipo selezionato (con animazione di altezza), al posto delle due guide sparse di prima; la guida della carta di credito si sposta qui (prima compariva sotto la valuta, dentro la sezione di configurazione)
- [x] Descrizioni: conto corrente (con la nota su bancomat/carte di debito), risparmio (con la nota su liquidità per investimenti: l'app traccia l'importo, non le quotazioni), prepagata, carta di credito (saldo zero + rettifica), contanti (prelievo ATM = trasferimento), wallet digitale, altro; IT + EN
- [x] Test: preset budget del risparmio (selezione, ritorno, override utente), editor aggiornati; migration 10->11 strumentata (da eseguire su device)

## Fase 9.13 - Categoria Prestiti & Finanziamenti, info budget e pulizia note (luglio 2026)

> Chiusura del filone tipi di conto (versionCode 62 -> 63, versionName 0.9.23 -> 0.9.24). Decisione di prodotto: prestiti e mutui NON diventano una feature dedicata (VISION li esclude), si gestiscono con i movimenti ricorrenti in uscita e la categoria giusta; i rimborsi di denaro prestato usano la categoria Entrate "Rimborsi" già esistente.

- [x] Nuova categoria di spesa predefinita "Prestiti & Finanziamenti" (icona request_quote, EN "Loans & Financing"): copre rate di prestiti personali e finanziamenti; "Affitto/Mutuo" resta per la rata del mutuo
- [x] Backfill sulle installazioni esistenti con migration dati 11->12 (guardia anti-duplicato se una categoria omonima esiste già); le installazioni nuove la ricevono dal seed localizzato
- [x] Note e appunti ripulite: rimosse le voci Investimenti/titoli (decisione registrata nell'ADR 22: la liquidità si traccia col Conto di risparmio) e Prestiti/Mutui (coperti da ricorrenze + categoria, nessuna feature futura)
- [x] Banner informativo promosso a componente condiviso `InfoBanner` (`core/designsystem/component`) e usato in fondo all'editor budget: spiega in tre frasi come il budget misura la spesa (mese di calendario, rimborsi a ridurre, trasferimenti/rettifiche/conti esclusi mai contati, notifiche 80%/100%), regole altrimenti invisibili dai campi del form

## Fase 9.14 - Dettaglio del calcolo nella card Spendibile oggi (luglio 2026)

> Micro-feature su richiesta utente (versionCode 63 -> 64, versionName 0.9.24 -> 0.9.25): la cifra "Spendibile oggi" è l'unica dell'app con una formula composita non visibile altrove; ora il tap sulla card la spiega.

- [x] Card espandibile inline (pattern già nel vocabolario della Dashboard, nessuna finestra separata): tap per aprire/chiudere, chevron rotante nell'header, altezza animata; stato non persistito (si riapre chiusa)
- [x] Scomposizione riga per riga: budget del mese, speso finora (negativo), da confermare e ricorrenze entro fine mese (mostrate solo se maggiori di zero), divisore e riga "Rimane per il mese" in evidenza; numeri tabulari, colori corretti anche nella variante rossa di superamento
- [x] Il tap della card non naviga più ai budget: il percorso resta col link "Gestisci budget" in fondo al dettaglio (la card Budget continua a navigare come prima)
- [x] Accessibilità: chevron con contentDescription esplicita ("Mostra/Nascondi il calcolo"); nessuna informazione affidata al solo colore

## Fase 9.15 - Trasferimenti ricorrenti (luglio 2026)

> Completa la triade delle ricorrenze (uscite, entrate, trasferimenti) su richiesta utente (versionCode 78 -> 80, versionName 0.9.39 -> 0.9.41; la 0.9.40 iniziale crashava all'avvio per la migration, vedi ADR 24). Design: ADR 24. Integrata nel motore/feature esistente, non un modulo separato.

- [x] Gamba di destinazione sulla regola ricorrente: `transferAccountId`/`transferAmountMinor`/`transferCurrency` su `RecurringRuleEntity` (mirror di `TransactionEntity`), dominio, mapper e backup; colonne ripiegate nel baseline v1 (`1.json` rigenerato, nessuna migration: vedi ADR 24, il tentativo di migration 1->2 crashava per la FK non aggiungibile via `ALTER TABLE ADD COLUMN`)
- [x] Ramo TRANSFER nel motore (`GenerateRecurringMovementsUseCase.toMovement`): gamba sorgente negativa, destinazione = sorgente per stessa valuta, `null` (pending) per cross-currency; riusa idempotenza/watermark/coda pending esistenti
- [x] Editor: terzo tipo TRANSFER con conto destinazione, nessuna categoria, automatico solo same-currency, cross-currency forzato in CONFIRM; conferma dell'importo ricevuto nella coda pending
- [x] Hub: terza tab Trasferimenti e card "Risparmio pianificato: X/mese" derivata dai soli trasferimenti verso conti `SAVINGS` (seme onesto degli Obiettivi di risparmio v2.0)
- [x] Stringhe IT/EN, notifiche pre-rinnovo transfer-aware, unit test (motore, mapper, backup, editor VM, pending VM, hub VM)

## Fase 10 - Release v1.0

- [ ] Baseline profile (spostato dalla Fase 9: richiede modulo macrobenchmark e generazione su device/emulatore)
- [ ] QA manuale end-to-end (checklist dei flussi principali)
- [ ] Test su device reali: API 33 e ultimo Android stabile, tablet/schermi grandi (almeno layout non rotto)
- [ ] Icona app, screenshot, scheda Play Store
- [ ] Privacy policy (obbligatoria per il Play Store, anche senza raccolta dati)
- [ ] Firma release, R8/proguard rules (attenzione a Room/serialization/Drive API)
- [ ] Internal testing → closed testing → produzione

---

# Roadmap v1.5

> Il Budget, originariamente in questa roadmap, è stato anticipato nella Fase 9.5.

- [ ] PIN lock + biometria (`BiometricPrompt`) + `FLAG_SECURE` opzionale
- [x] ~~Widget: aggiunta rapida~~ implementato nella Fase 10.18 (ADR 32), anticipato prima della v1.0 su richiesta utente. Il widget mostra anche le spese di oggi; restano da valutare i widget di sola lettura (saldo totale)
- [x] Import CSV (anticipato): riconoscimento automatico di separatore, decimali e colonne (mappatura per nome, alias IT/EN, ordine libero, colonne minime data+importo), regole di adattamento (tipo dedotto dal segno, normalizzazione del segno, valuta dal conto), creazione opzionale di conti/categorie/tag mancanti, rilevazione duplicati contro il registro e nel file, anteprima a due passi e report finale. Solo inserimento, in un'unica transazione. La colonna "Ricorrente" dell'export è informativa e non viene reimportata
- [ ] Export Excel (.xlsx)
- [ ] Export Google Sheets (nuovo foglio o aggiornamento foglio esistente; richiede verifica OAuth Google per lo scope "sensitive" `spreadsheets` - avviare la review per tempo)
- [ ] Miglioramenti UX dal feedback della v1.0

# Roadmap v2.0

- [x] Obiettivi di risparmio (target, progressi, suggerimento mensile) - la primitiva di alimentazione esiste già: i trasferimenti ricorrenti verso conti `SAVINGS` (Fase 9.15/ADR 24), da cui deriva "Risparmio pianificato". Implementati nella Fase 10.0 (ADR 25)
- [x] Recap mensile condivisibile stile "Wrapped" (promosso da Note e appunti): implementato nella Fase 10.1 (ADR 28)
- [ ] Rilevamento automatico ricorrenze (promosso da Note e appunti): euristica on-device che nota spese simili ripetute a cadenza regolare e propone la regola dall'hub Ricorrenze; nessun dato lascia il device
- [x] Proiezione saldo a fine mese: forecast del saldo totale da ricorrenze in arrivo + media della spesa giornaliera del mese, mostrata come coda tratteggiata della sparkline; sempre indicata come stima. Implementata nella Fase 10.4 (ADR 30) con un calcolatore dedicato (`BalanceForecastCalculator`, gemello di `UpcomingChargesCalculator` esteso alle entrate fisse)
- [ ] Gestione tag dedicata: schermata CRUD (rinomina, unione, eliminazione con conferma) per i tag, che oggi si creano inline ma non si amministrano; gap emerso dalla review di luglio 2026
- [ ] Ricerca potenziata con suggerimenti: chip di categorie, tag e descrizioni frequenti proposte sotto il campo di ricerca del registro, per arrivare al filtro giusto senza digitare
- [ ] Conversione valuta automatica (provider tassi, cache offline, indicazione "stimato")
- [ ] Export PDF report con grafici
- [ ] Cifratura backup con passphrase
- [ ] Rimborsi collegati alla spesa originale
- [ ] Commissioni sui trasferimenti
- [ ] Analisi avanzate (anno su anno, pattern di spesa)
- [ ] Valutare: sottocategorie, periodo budget personalizzato

## Fase 10.0 - Obiettivi di risparmio (luglio 2026)

> Prima feature della v2.0, su richiesta utente (versionCode 80 -> 83, versionName 0.9.41 -> 0.9.44). Design: ADR 25. Costruita sopra i conti `SAVINGS` (ADR 22) e i trasferimenti ricorrenti (ADR 24), senza nuove meccaniche di denaro. Modello confermato con l'utente: obiettivo legato a un conto risparmio (non un registro di contributi separato). La rifinitura 0.9.43 fa preselezionare il tipo SAVINGS nell'editor conto aperto dalla scorciatoia "crea conto" (chiarimento ADR 22). La 0.9.44 chiude due punti dalla prova su device: crash all'aggiornamento (schema ripiegato nel baseline v1, ADR 26) e card Obiettivi in Dashboard sempre visibile.

- [x] `SavingsGoal` (nome, target, valuta del conto, `accountId`, data obiettivo opzionale, colore, icona): entità `savings_goals` con FK `ON DELETE CASCADE` e indice UNIQUE su `accountId` (un obiettivo per conto), mapper, repository e DI. Nata come `Migration(1,2)` pulita, poi ripiegata nel baseline v1 (`1.json` rigenerato) dopo il crash all'aggiornamento sul device, ultima collapse-una-tantum (ADR 26)
- [x] Crash all'aggiornamento e disciplina migration (ADR 26): `SALDO_DATABASE_VERSION` come unica fonte di verità della versione schema; test generici (non uno per migrazione) `MigrationsTest` strumentato (baseline -> `ALL_MIGRATIONS` -> versione corrente, validati contro gli schemi esportati) e `MigrationChainTest` JVM (catena contigua fino alla versione corrente, gira in CI). Dai prossimi giri: migrazione forward reale, niente collasso
- [x] Card Dashboard "Obiettivi di risparmio" sempre visibile quando abilitata in Impostazioni, con messaggio di empty-state (come la card Budget): funge da punto d'accesso anche senza obiettivi, senza passare da Impostazioni
- [x] `ObserveSavingsGoalsProgressUseCase`: "risparmiato" = saldo calcolato del conto collegato (ADR 3); progresso non cappato (>100% = raggiunto); suggerimento mensile = (target - risparmiato) / mesi rimanenti alla data, arrotondato per eccesso in minor units; equivalente mensile pianificato dai trasferimenti ricorrenti same-currency verso il conto, con data di raggiungimento stimata e verdetto "in linea"
- [x] Schermata Obiettivi (hero totale risparmiato, card per obiettivo con barra di progresso a colore positivo, %, riga di stato: raggiunto / suggerimento con data / proiezione / mancante) ed editor (nome, target, conto risparmio con scorciatoia "crea conto" che apre l'editor conto già preselezionato su SAVINGS con budget off, data obiettivo opzionale, colore/icona, guardia modifiche non salvate, empty-state se non esiste alcun conto risparmio). Voce in Impostazioni > Gestione
- [x] Dashboard: nuova card opzionale "Obiettivi di risparmio" (toggle in Impostazioni > Dashboard) e terza metrica "Risparmio / mese" sulla card "Movimenti ricorrenti" (i trasferimenti ricorrenti prima non comparivano in Dashboard: omissione della Fase 9.15, ora chiusa)
- [x] Backup: campo additivo `savingsGoals` (versione backup invariata), mapper, validazione (valuta ISO, unicità per conto), conteggio nell'anteprima di ripristino
- [x] Stringhe IT/EN, unit test (mapper, use case con proiezione/suggerimento e mesi corti, editor VM dirty, round-trip backup esteso); migration test strumentato `Migration1To2Test` (da eseguire su device). Gate `assembleDebug testDebugUnitTest lint detekt` verde

## Fase 10.1 - Hero saldo e recap mensile "Saldo Wrapped" (luglio 2026)

> Due feature premium dalla review di luglio 2026 (versionCode 84 -> 85, versionName 0.9.45 -> 0.9.46), scelte dall'utente tra le proposte della review: parte alta della Dashboard con impatto visivo e recap mensile condivisibile stile "Wrapped" (gia in Note e appunti). Design: ADR 27 e 28. Nessun cambio di schema (solo query nuove su tabelle esistenti).

- [x] Query giornaliere per la sparkline: `observeDailyNetChanges` e `observeNetChangeBefore` (net per giorno locale, entrambe le gambe dei trasferimenti, stesse regole del saldo totale), `ObserveDailyBalanceHistoryUseCase` con zero-fill e invariante "ultimo punto = saldo dashboard" (unit test)
- [x] Hero card Dashboard: count-up presentazionale del saldo (interpolazione su minor units, frame finale esatto), sparkline 30 giorni in Canvas (interpolazione monotona Fritsch-Carlson con test di non-overshoot, riempimento sfumato, reveal d'ingresso), gradiente tonale, caption con delta 30 giorni a segno esplicito; skeleton ricalibrato
- [x] `rememberMotionEnabled()` nel design system: count-up, reveal della sparkline e reveal delle pagine recap si disattivano con le animazioni di sistema spente
- [x] Accessibilita sparkline: canvas muto con `clearAndSetSemantics` e riassunto del trend (da X a Y, in aumento/in calo/stabile) formattato con `MoneyFormatter`
- [x] Query recap one-shot con semantica statistiche esatta (`getStatsPeriodTotals`, `getCategoryTotals`, `getBiggestExpense`, `getDailyActivity`, `getRecurringSpendTotal`): le cifre del recap coincidono con la schermata Statistiche
- [x] `GetMonthlyRecapUseCase`: totali con rimborsi nettati, delta vs mese precedente (null senza baseline, come la card comparativa), top 5 categorie con percentuali sul totale del mese, spesa piu grande, giorno piu attivo (tie-break su spesa), ricorrenti addebitati, savings rate; unit test completi
- [x] Schermata recap full-screen (`feature/recap`): tema scuro brand forzato (rivisto nella Fase 10.3: segue il tema dell'app), `HorizontalPager` con pagine a storia (hero, spese, top categorie, record, entrate/uscite, ricorrenti, chiusura con riga privacy), pillole di progresso, tap zone stile storia + swipe, reveal per pagina, empty state per mese vuoto
- [x] Condivisione immagine: card riassuntiva 360x640dp composta off-screen a densita 3x (1080x1920), `GraphicsLayer.toImageBitmap()`, PNG in `cache/exports` via FileProvider esistente e share sheet; nessun permesso, nessuna rete
- [x] Punti d'accesso: teaser dismissibile in Dashboard nei primi 7 giorni del mese (dismiss persistito in DataStore per mese, auto-espirante, fuori dalle preferenze card) e azione nella toolbar Statistiche (apre il mese visualizzato se concluso, altrimenti l'ultimo mese concluso)
- [x] Stringhe IT/EN complete; unit test (use case giornaliero, geometria sparkline, dashboard VM con teaser, recap VM con eventi share); strumentati `TransactionDaoRecapTest` scritti (da eseguire su device)

## Fase 10.2 - Toggle teaser recap e grafici premium (luglio 2026)

> Follow-up della Fase 10.1 su richiesta utente (versionCode 85 -> 86, versionName 0.9.46 -> 0.9.47). Design: ADR 29. Nessun cambio a query, dominio o schema: le cifre delle statistiche restano identiche.

- [x] Preferenza "Invito al recap mensile" in Impostazioni > Dashboard (default attivo): campo `showRecapTeaser` in `DashboardCardPreferences`, gate in `buildState` (il flusso di dismiss per mese resta invariato), switch con lo stesso pattern delle altre card
- [x] Donut categorie custom in Canvas: `DonutGeometry` pura (fette proporzionali con gap clampato, partenza a ore 12, hit-test dell'angolo) con unit test; fette con cap arrotondati, sweep-in d'ingresso in senso orario (saltato a reduced motion), tap sulla fetta che naviga con la stessa route delle righe (anche fetta "Senza categoria"); rimossi gli import `vico.compose.pie.*`
- [x] Restyling cartesiani dentro Vico 3.2.3 (API verificate sui binari in cache Gradle, non a memoria): colonne a pillola da 16dp (`CircleShape`), area sotto la linea saldo con sfumatura verticale (`Fill(Brush)` verificato sul costruttore), `animateIn` d'ingresso su barre e linea agganciato a `rememberMotionEnabled()`; marker, listener del drill-down e scroll ancorato a fine serie non toccati
- [x] Rinunce deliberate (fallback previsti dal piano): evidenza della colonna del mese corrente e punto sull'ultimo valore della linea richiederebbero provider per-entry custom su interfacce con overload multipli, rimandati
- [x] Test: `DonutGeometryTest` (proporzioni, gap, fetta unica, hit-test dentro/fuori/gap, angoli normalizzati), teaser nascosto con switch off in `DashboardViewModelTest`

## Fase 10.3 - Recap: media giornaliera e tema adattivo (luglio 2026)

> Feedback utente sulla Fase 10.1 (versionCode 86 -> 87, versionName 0.9.47 -> 0.9.48). La pagina "Hai speso" restava quasi vuota senza la baseline del mese precedente; valutata e scartata insieme all'utente la fusione con la pagina delle categorie (il formato "una pagina, un pensiero" resta), scelta la riga aggiuntiva sempre presente. Il tema scuro forzato e stato rivisto (ADR 28 aggiornato).

- [x] `dailyAverageSpend` su `MonthlyRecap`: spesa del mese divisa per i giorni di calendario, arrotondata alla scala della valuta nello use case (aritmetica monetaria nel dominio, mai in UI), con unit test (arrotondamento, zero senza spese)
- [x] Pagina spese del recap: riga "In media X al giorno" sempre presente sotto la cifra hero; il confronto col mese precedente resta quando esiste la baseline. Stringhe IT/EN
- [x] Recap a tema adattivo: rimosso il wrapper scuro forzato, schermata e immagine condivisa seguono il tema risolto dell'app (i token usati erano gia theme-aware); KDoc aggiornati, revisione ADR 28

## Fase 10.4 - Proiezione saldo a fine mese (luglio 2026)

> Punto della Roadmap v2.0 (versionCode 87 -> 88, versionName 0.9.48 -> 0.9.49). Design: ADR 30. Coda tratteggiata della sparkline nella hero card, non riga dedicata: zero ingombro aggiuntivo. Nessun cambio di schema o query: il forecast e calcolo puro su dati gia osservati dalla dashboard.

- [x] `BalanceForecastCalculator` (dominio, puro): stima end-of-day da domani all'ultimo giorno del mese; camminata dal saldo totale con media giornaliera (spesa mese / giorni trascorsi, HALF_UP alla scala valuta) e ricorrenze fisse (spese e entrate) applicate alla loro data via `RecurrenceCalculator`; floor su `lastGeneratedDate` come `UpcomingChargesCalculator`; vuoto l'ultimo giorno del mese; unit test completi (media, arrotondamento, spese/entrate alla data, weekly, regole variabili/valuta estera/pre-generate escluse)
- [x] `DashboardViewModel`: campo `balanceForecast` nello stato, ancorato al saldo headline (stesso valore dell'ultimo punto storico, aggancio senza scalino), calcolato solo quando la sparkline e visibile; test VM (camminata con media + ricorrenza, vuoto con sparkline nascosta)
- [x] `BalanceSparkline`: normalizzazione su storia + forecast (finestra storica fissa a 30 giorni, coda con lo stesso passo per giorno, al massimo circa meta larghezza), tangenti condivise per continuita al punto di oggi, riempimento sfumato solo sotto la parte reale, coda tratteggiata con cap arrotondati, anello sul punto di fine mese, pill "≈ importo" (TextMeasurer, autoposizionata sopra/sotto e clampata nel canvas, fade-in a fine reveal), a11y estesa con la stima
- [x] Caption aggiornata quando la coda e presente ("Ultimi 30 giorni + stima a fine mese"); stringhe IT/EN
- [x] Fix doppio conteggio delle ricorrenze nella media giornaliera (versionCode 88 -> 89, versionName 0.9.49 -> 0.9.50): la media si basa sulla sola spesa non ricorrente del mese (`monthToDateNonRecurringSpend`, nuova colonna `recurringRuleId IS NULL` nella query dashboard, nessuna migration). Un addebito ricorrente gia registrato non gonfia piu la media ne viene riproiettato sulla data futura. Test: mapper (nuovo campo), calcolatore (coda piatta col caso 1 EUR mensile), VM (media da non ricorrente, esclusione ricorrenza gia addebitata)
- [x] Rimosso il numero di variazione a 30 giorni dalla didascalia della sparkline (versionCode 89 -> 90, versionName 0.9.50 -> 0.9.51): dopo l'aggiunta del forecast quel numero senza etichetta, vicino alla coda e alla pill `≈`, si confondeva con una cifra della stima (feedback utente). L'andamento resta leggibile dalla forma della linea, la pill da la stima a fine mese e la card mensile da la spesa del mese; rimosso anche il campo `balanceTrend` dal ViewModel (stato morto, l'a11y della sparkline ricalcola il trend dalla history)

## Fase 10.5 - Indicatore movimenti da ricorrenza e filtro per origine (luglio 2026)

> Richiesta utente (versionCode 95 -> 96, versionName 0.9.56 -> 0.9.57), promossa dalle Note e appunti. Nessun cambio di schema o query: il dato esiste gia (`Transaction.recurringRuleId`, FK `SET_NULL` alla cancellazione della regola), il filtro e in memoria come tutto il motore filtri. Estensione condivisa `Transaction.isRecurring` per centralizzare il discriminatore (prima duplicato nel CSV builder).

- [x] Segno di riga non invasivo: icona `Repeat` (16dp, `onSurfaceVariant`) prima dell'importo in `TransactionRowContent`, quindi automaticamente in registro, Dashboard (ultimi movimenti) e drill-down statistiche, tutti resi dallo stesso composable; `contentDescription` dedicata per TalkBack (non solo colore)
- [x] Editor movimento: `InfoBanner` in cima al form quando il movimento e generato da una regola, con il nome della regola (`RecurringRuleRepository.getRule`, risolvibile perche la FK e `SET_NULL`) o testo generico di fallback; chiarisce che le modifiche valgono solo per quel movimento. Campi read-only nel Form, fuori dallo snapshot di dirty detection
- [x] Filtro per origine nel registro (`TransactionOrigin` RECURRING/MANUAL, tri-state): sezione "Origine" nel filter sheet, chip rimovibile nella barra dei filtri attivi, conteggio nel badge; predicato `matchesOrigin` nel motore filtri
- [x] Stringhe IT/EN; unit test (motore filtri origine + activeCount, editor VM: flag e nome regola caricati, movimento manuale non marcato). Gate `assembleDebug testDebugUnitTest lint detekt` verde

## Fase 10.6 - Saldo ad oggi per singolo conto (luglio 2026)

> Richiesta utente (versionCode 106 -> 107, versionName 0.9.67 -> 0.9.68). Estende al singolo conto il "saldo ad oggi" finora solo globale (ADR 27, hero card), mostrato solo alla divergenza dal saldo totale del conto (movimenti confermati datati nel futuro). Nessun cambio di schema: nuova query di sola lettura con lo stesso filtro sul giorno locale della serie giornaliera.

- [x] Query DAO `AccountDao.observeAllBalancesAsOf(endEpochDayExclusive)` (gemella di `observeAllWithBalance` con filtro `(timestampEpochMilli/1000 + zoneOffsetSeconds)/86400 < :endEpochDayExclusive`, entrambe le gambe dei trasferimenti); relation `AccountBalanceAsOfRow`. Campo `AccountWithBalance.balanceAsOfToday: BigDecimal? = null` (non-null solo alla divergenza). Metodo repository `observeAccountsWithBalanceAsOfToday(todayEpochDayExclusive)`; `observeAccountsWithBalance()` invariato per gli altri chiamanti
- [x] `AccountsViewModel` inietta `Clock` + `midnightTicker` per ri-ancorare "oggi"; `DashboardViewModel` arricchisce la lista del breakdown nel `flatMapLatest` esistente
- [x] UI: riga `labelSmall` attenuata "%1$s ad oggi" sotto l'importo in `AccountRowContent` (schermata Conti) e `AccountBreakdownRow` (card Saldo totale), solo alla divergenza. Vincolo "la riga non cresce ne salta": nei Conti l'altezza e gia dettata dall'avatar 44dp; nel breakdown si riserva l'altezza a due righe su tutte le righe solo quando almeno un conto diverge (`BALANCE_ROW_TWO_LINE_HEIGHT`), cosi il caso comune resta compatto e la lista non e frastagliata. Riuso della stringa `dashboard_balance_as_of_today`
- [x] Test strumentato `SaldoDatabaseTest.balancesAsOfExcludeMovementsDatedAfterCutoff` (futuro escluso, oggi contato); stub MockK aggiornati (Accounts/Dashboard VM). Gate `assembleDebug testDebugUnitTest lint` verde
- [x] Riga "ad oggi" in rosso (`moneyColors.negative`) quando negativa, altrimenti grigia attenuata; per-conto e riga globale della hero card (icona + testo coerenti). Rosso solo nel negativo per non trasformare il positivo in un secondo numero forte (versionCode 107 -> 108, versionName 0.9.68 -> 0.9.69)

## Fase 10.7 - Card Saldo totale: dettaglio conti configurabile e stato di apertura persistente (luglio 2026)

> Richiesta utente (versionCode 113 -> 114, versionName 0.9.74 -> 0.9.75). Rifinitura della card Saldo totale: dettaglio conti come sezione rivelabile (chiuso non mostra alcun conto), icone di conto senza sfondo allineate in colonna con le icone delle altre card, righe piu compatte, stato di apertura tenuto nel ViewModel e default configurabile in Impostazioni. Nessun cambio di schema o query.

- [x] Icona di conto nel breakdown senza chip colorato: glifo `AccountVisuals.icon` tinto col colore del conto, 24dp, allineato al bordo sinistro come le icone header delle card, cosi icona e nome stanno nella stessa colonna dell'icona e del titolo lungo tutta la card. Stesso trattamento per la riga di overflow (`MoreHoriz`). Rimossi `AVATAR_TINT_ALPHA` e la `Box` avatar in `AccountBreakdownRow`
- [x] Righe piu compatte: padding verticale 6dp -> 4dp e altezza minima 44dp (era 48dp, dettata dal chip 36dp), riga a due righe "ad oggi" 52dp -> 48dp, gap divisore-prima riga 8dp -> 4dp
- [x] Chiuso = nessun conto (prima ne mostrava sempre 2): il breakdown e ora una sezione mostrata solo da espansa, con `AnimatedVisibility` (expand + fade) che comprende gap superiore e divisore; il chevron nell'header e sempre presente quando esiste almeno un conto. Rimosso `ACCOUNT_PREVIEW_COUNT`
- [x] Stato di apertura del breakdown conti e del dettaglio Spendibile oggi spostato dal composable (`remember`/`rememberSaveable`) al `DashboardViewModel` (`StateFlow` + toggle): sopravvive allo scroll fuori dalla lista e alla navigazione tra schermate/tab (il tab tiene i ViewModel vivi), torna al default solo alla riapertura dell'app (ViewModel nuovo). Supera la nota "stato non persistito (si riapre chiusa)" della Fase 9.14
- [x] Nuova preferenza `balance_accounts_expanded_default` (DataStore, default true = aperto alla prima installazione): switch "Dettaglio conti aperto" nella sezione Dashboard delle Impostazioni. Il `DashboardViewModel` combina l'override di sessione col default persistito (override null = segue il default live finche l'utente non tocca il chevron)
- [x] Stringhe IT/EN; unit test `DashboardViewModel` (breakdown conti dal default aperto/chiuso, toggle conti, toggle spendibile). Gate `assembleDebug testDebugUnitTest lint` verde

## Fase 10.8 - Righe conti piu compatte e colore predefinito per tipo (luglio 2026)

> Richiesta utente (versionCode 114 -> 115, versionName 0.9.75 -> 0.9.76). Due rifiniture dopo il feedback su screenshot: compattazione delle righe del breakdown conti e preimpostazione del colore per tipo alla creazione di un conto, gemella di quella gia esistente per l'icona.

- [x] Righe breakdown conti piu compatte: padding verticale 4dp -> 2dp, altezza minima 40dp e altezza a due righe ("ad oggi") 48dp -> 44dp. Nel caso comune con una riga "ad oggi" tutte le righe sono fissate all'altezza a due righe, quindi 44dp e il pavimento (sotto si taglierebbe la seconda riga o la lista diventerebbe frastagliata). Scelta consapevole sotto il minimo Material 48dp, limitata a questa card
- [x] `AccountVisuals.defaultColorFor(type)`: colore di palette per tipo (blu conto corrente, verde risparmio, ambra contanti, ciano prepagata, indaco wallet, viola scuro carta di credito - non rosso per non confondersi col "sotto zero" - grigio-blu altro). Tutti membri della palette, cosi il picker evidenzia lo swatch
- [x] `AccountEditorViewModel`: alla creazione il cambio tipo preimposta il colore (guardia `userPickedColor`, gemella di `userPickedIcon`; in edit/load parte true e non sovrascrive mai la scelta persistita). Stato iniziale e fallback usano `defaultColorFor`. Onboarding (conto CHECKING) allineato al nuovo default
- [x] Unit test `type changes drive the color until the user picks one` (gemello di quello sull'icona). Gate `assembleDebug testDebugUnitTest lint` verde

## Fase 10.9 - Schermata Conti: sottototali per tipo e riordino manuale (luglio 2026)

> Richiesta utente (versionCode 115 -> 116, versionName 0.9.76 -> 0.9.77), dopo una valutazione condivisa. Due rifiniture premium alla schermata Conti: sottototale del gruppo in ogni intestazione di tipo e riordino manuale dei conti col trascinamento, confinato dentro il gruppo. Nessuna migrazione: la colonna `sortOrder` esisteva gia (era inutilizzata, tutti a 0).

- [x] Sottototale per tipo nell'intestazione di sezione (`AccountTypeGroup.subtotal/currency` calcolati in `buildAccountTypeGroups`): somma delle righe del gruppo, attenuato e rosso solo se negativo, omesso quando il gruppo mescola valute. Nessun grande totale in cima (scelta utente): i sottototali fanno da riepilogo senza duplicare l'hero della Dashboard
- [x] Riordino manuale col drag riusando il componente in-repo `ReorderableListState` (già delle Categorie, nessuna libreria esterna). Esteso con una guardia opzionale `canMove(from, to)` (default sempre true): per i Conti confina il target ai conti dello stesso tipo, così l'intestazione fa da barriera e il conto resta nel suo gruppo. Categorie invariate
- [x] Lista attiva ristrutturata a righe-card individuali con intestazioni di sezione (stile Categorie), necessario perché il drag opera su item distinti della `LazyColumn`; archiviati invariati (card unica collassabile)
- [x] Persistenza dell'ordine: `accountOrder` ordina per `sortOrder` poi nome (tutti a 0 = comportamento attuale, nessun salto); `AccountRepository.reorder(orderedActive)` riscrive l'indice per-tipo; un conto nuovo prende `nextSortOrder(type)` per accodarsi al suo gruppo. L'ordine è condiviso con il breakdown della card Saldo totale in Dashboard (stesso `sortedByTypeThenName`)
- [x] Stringhe IT/EN (`accounts_reorder`); unit test `AccountsGroupingTest` (posizione manuale vince sul nome, sottototale monovaluta, nessun sottototale multivaluta). Gate `assembleDebug testDebugUnitTest lint` verde

## Fase 10.10 - Schermata Conti: spazio al nome, info riordino, "ad oggi" nei gruppi (luglio 2026)

> Richiesta utente (versionCode 116 -> 117, versionName 0.9.77 -> 0.9.78), tre rifiniture dopo feedback su screenshot. Nessun cambio di schema o query.

- [x] Piu spazio al nome del conto: la riga secondaria "%1$s ad oggi" era piu larga dell'importo e dettava la larghezza della colonna a destra. La parola "ad oggi" e sostituita dal glifo calendario (`Today`, come nella hero card) accanto all'importo (`AsOfTodayLine`, componente condiviso riga+intestazione): il nome guadagna ~3 caratteri. Maniglia di drag piu stretta (40dp, alta 48) e padding del nome ridotto; `contentDescription` mantiene la frase completa per TalkBack
- [x] `InfoBanner` in cima all'elenco (stesso componente della schermata Budget) che chiarisce che il riordino e solo dentro lo stesso gruppo (non un bug). Mostrato solo quando un gruppo ha >= 2 conti, e tenuto fuori dalla `LazyColumn` per non disallineare lo spazio degli indici del drag
- [x] Sottototale "ad oggi" nell'intestazione di gruppo sotto il totale (`AccountTypeGroup.subtotalAsOfToday`), stesso glifo, mostrato solo alla divergenza (qualche conto del gruppo ha movimenti datati nel futuro)
- [x] Stringhe IT/EN (`accounts_reorder_info`); unit test `AccountsGroupingTest` (sottototale "ad oggi" solo alla divergenza). Gate `assembleDebug testDebugUnitTest lint` verde

## Fase 10.11 - "Ad oggi" a icona anche nel breakdown della Dashboard (luglio 2026)

> Richiesta utente (versionCode 117 -> 118, versionName 0.9.78 -> 0.9.79). Uniformita: la stessa treatment compatta "ad oggi" (glifo + importo) della schermata Conti portata anche nel breakdown della card Saldo totale. Refactor presentazionale, nessun cambio di comportamento.

- [x] Componente condiviso `AsOfTodayAmount(amount, currency)` in `core/designsystem/component`: unica sorgente per riga conti, intestazione di gruppo (schermata Conti) e breakdown della Dashboard. Rimossa la copia privata `AsOfTodayLine`, sostituito il `Text` "%1$s ad oggi" nel breakdown della `AccountBreakdownRow`
- [x] Hero card (`BalanceAsOfTodayLabel`, riga prominente sotto la cifra) lasciata com'e (icona + parola "ad oggi"): contesto piu grande dove la parola aiuta. Gate `assembleDebug testDebugUnitTest lint` verde

## Fase 10.12 - Editor movimenti "premium" (luglio 2026)

> Review UI/UX delle schermate di inserimento/modifica movimenti richiesta dall'utente (versionCode 118 -> 119, versionName 0.9.79 -> 0.9.80). Un solo editor riusato per tutti i tipi; interventi scelti dall'utente tra le proposte della review. Nessun cambio di dominio o schema (l'ora del movimento esisteva gia nel modello).

- [x] Campo importo "hero": borderless, centrato, `displayMedium` con cifre tabulari e simbolo valuta a fianco; errore a colore + testo di supporto al tentativo di salvataggio; sign-toggle per la rettifica; variante compatta per la seconda gamba cross-currency. Tastiera di sistema invariata (ADR 16)
- [x] Bottone Salva sempre attivo: al tap la validazione mostra tutti gli errori insieme (prima il bottone era muto e disabilitato senza importo)
- [x] Trasferimenti: freccia direzionale tra i chip conto, tappabile per invertire le gambe (gli importi seguono la propria valuta); tasso di cambio implicito sotto il secondo importo nei cross-currency (calcolo locale, unit test)
- [x] Chip ora accanto al chip data (`TimePickerDialog` M3) e quick date "Oggi"/"Ieri" nel date picker con conferma immediata
- [x] Eliminazione dall'editor senza dialog di conferma: undo cross-screen via `TransactionUndoCoordinator` (singleton) + `SnackbarHost` a livello app in `SaldoApp`, stessa semantica di ripristino dello swipe-delete del registro
- [x] Coerenza design system: celle categoria a `AvatarShape`, cifre tabulari nei saldi del picker conti, `LoadingState` condiviso; sezioni del form animate al cambio tipo (con rispetto di `rememberMotionEnabled`)
- [x] Picker colore/icona consolidati: `ColorSwatchPicker`/`IconSwatchPicker` condivisi in `core/designsystem/component`, i tre editor (categorie, conti, abbonamenti) delegano

## Fase 10.13 - Coerenza "premium" per tutti gli editor (luglio 2026)

> Estensione della review della Fase 10.12 a tutti gli altri editor (versionCode 119 -> 120, versionName 0.9.80 -> 0.9.81): conto, rettifica saldo, budget, obiettivo di risparmio, ricorrenze (3 tipi), categoria. Scelte utente: importo hero solo dove l'importo e il dato principale; undo al posto della conferma solo per budget e obiettivo (le ricorrenze mantengono il dialog: eliminare una regola stacca definitivamente i movimenti gia generati, un undo non li ricollegherebbe). Nessun cambio di schema.

- [x] Componenti promossi in `core/designsystem/component`: `HeroAmountField` (dal campo importo dei movimenti, ora prende il simbolo valuta come stringa), `AnimatedSection`, `SaldoDatePickerDialog` (unifica i due date dialog esistenti, chip rapidi Oggi/Ieri opzionali e nascosti sotto `minDate`)
- [x] Importo hero in: limite mensile budget, target obiettivo, importo ricorrenza (con swap animato verso la nota importo variabile), saldo reale della rettifica (variante compatta nel dialog)
- [x] Salva sempre attivo con validazione differita in tutti gli editor (conto, categoria, budget, ricorrenze; obiettivo lo era gia); la rettifica mantiene l'abilitazione live perche il preview del delta spiega gia lo stato
- [x] `LoadingState` condiviso in tutti e cinque gli editor a schermata piena (prima spinner grezzo)
- [x] Cifre tabulari su ogni campo/preview monetario rimasto `OutlinedTextField` (saldo iniziale, massimale carta, preview rettifica)
- [x] Sezioni animate: saldo iniziale <-> sezione carta di credito nell'editor conto (rimosso anche un import `animateContentSize` morto), data obiettivo nel risparmio, importo variabile/mode selector/nota cross-currency/data fine nelle ricorrenze
- [x] Undo cross-screen generalizzato: `UndoDeleteCoordinator` in `core/domain/undo` con `UndoableDelete` sealed (Movement, Budget, Goal) e `UndoDeleteViewModel` in `navigation`; snackbar con messaggio per entita. Budget ripristinato via write path transazionali del repository (i watermark di notifica ripartono), obiettivo re-inserito com'era
- [x] Fix minori: dialog di eliminazione ricorrenza chiuso prima del delete (come gli altri editor)
- [x] Test: nuovo `BudgetEditorViewModelTest` (mancava del tutto), `UndoDeleteViewModelTest` sui tre percorsi di ripristino, test delete/undo per obiettivo
- [x] Follow-up forma squircle (versionCode 120 -> 121): `ColorSwatchPicker`/`IconSwatchPicker` e il segnaposto avatar degli skeleton passano da cerchio ad `AvatarShape`. Restano deliberatamente rotondi: barre pill, dot di legenda e indicatori pagina (a 4-10dp la forma non si distingue), FAB/speed dial (spec M3), ripple sulle icone, badge illustrativi onboarding, colonne pill dei grafici

## Fase 10.14 - Fix dalla quarta review completa (luglio 2026)

> Giro unico su branch dedicato (versionCode 121 -> 122, versionName 0.9.82 -> 0.9.83) nato da una quarta review completa su dominio, data layer, use case, ViewModel e import/export. Nessun cambio di schema: l'unica aggiunta al data layer e una query di sola lettura. Tre interventi sono decisioni di prodotto esplicite, marcate sotto.

- [x] Valuta del conto nuovo: l'editor apriva sempre sulla valuta del locale di sistema (`fallbackCurrency`) invece che sulla valuta principale dell'app (override esplicito, altrimenti maggioranza dei conti). Un conto creato nella valuta sbagliata spariva silenziosamente da saldo totale, card Oggi/Mese, statistiche e budget, che sono tutti scoped alla valuta principale. Preselezione asincrona con baseline catturata dopo, cosi il form non apre "sporco"; guardia `userPickedCurrency` come per icona e colore, e in modifica la valuta persistita non viene mai sovrascritta
- [x] Regole ricorrenti non ancora iniziate: il filtro "attiva" guardava solo `endDate`, quindi una regola con partenza futura entrava subito in "X/mese", nella proiezione annua, nel conteggio attivi, nella card Dashboard e nel risparmio pianificato (falsando anche verdetto "in linea" e data stimata degli obiettivi). Due predicati nel dominio: `RecurringRule.runsInMonthOf(date)` (non finita e partenza entro la fine di quel mese) per le cifre "al mese", `hasEndedBy(date)` per gli elenchi. **Decisione di prodotto**: la soglia di partenza e la fine del mese, non "oggi" - un abbonamento aggiunto il 9 con primo addebito il 12 e un costo mensile reale, prezzarlo a zero fino al primo addebito sarebbe sbagliato quanto l'errore opposto (i test dell'hub lo hanno dimostrato: due fixture su tre partivano piu avanti nel mese corrente). La regola che parte il trimestre dopo resta comunque *elencata* nell'hub con la sua prima data di addebito, nasconderla farebbe sparire una regola appena creata
- [x] Carte di credito: la CTA "Paga estratto" mostrava il ciclo scaduto piu vecchio *con importo* (i cicli vuoti sono filtrati dalla segnalazione) mentre il settlement pagava il piu vecchio *in assoluto*, vuoti inclusi. Con un ciclo arretrato vuoto il tap non registrava nulla e la CTA restava li. `SettleCreditCardStatementUseCase` ora salta i cicli vuoti avanzando il watermark in un colpo solo, cosi salda esattamente l'estratto annunciato
- [x] Import CSV: `sortOrderIncome` valorizzato con `nextSortOrder(tipo della categoria)`, cioe la chiave d'ordinamento del tab sbagliato. Allineato alla logica dell'editor categorie (una chiave per tab, inerte nel tab in cui la categoria non compare)
- [x] Ticker di mezzanotte esteso a registro e hub Ricorrenze: erano le due schermate rimaste senza (Dashboard, Statistiche e Conti lo avevano dalla Fase 9.7/10.6). A schermo acceso oltre la mezzanotte i preset "Questa settimana"/"Questo mese" e le date di prossimo addebito restavano ancorati al giorno prima
- [x] Editor obiettivo di risparmio: la preselezione del conto dopo la scorciatoia "crea conto" muoveva lo stato oltre la baseline, quindi il back apriva "Scartare le modifiche?" su un form mai toccato. La baseline segue la preselezione solo se il form e ancora intatto; un form gia modificato conserva la propria
- [x] Riassegnazione in eliminazione categoria: i candidati erano filtrati sul tipo *dichiarato* della categoria, quindi da una categoria "entrambi" si potevano spostare spese dentro una categoria di sole entrate (che poi le contava nel proprio budget e nella propria fetta dell'anello). Nuova query di sola lettura `TransactionDao.distinctTypesForCategory` -> i candidati sono filtrati sui tipi di movimento *realmente* presenti, il che evita anche l'eccesso opposto (mandare all'orfanotrofio la storia di una categoria "entrambi" usata solo per spese)
- [x] Movimento pending nell'editor: `buildTransaction` non propagava `isPending`, che sarebbe ricaduto sul default `false`. Non raggiungibile oggi (la coda "Da confermare" non naviga all'editor), propagato come guardia perche un salvataggio confermerebbe silenziosamente il movimento il giorno in cui lo diventasse
- [x] **Decisione di prodotto**: le card Oggi/Mese escludono i movimenti sui conti archiviati (`observeDashboardTotals` ora fa `INNER JOIN accounts ... isArchived = 0`). Prima erano l'unica cifra della Dashboard a contarli, in contraddizione col saldo totale immediatamente sopra. Come per il saldo e la sua storia, archiviare riscrive la cifra retroattivamente: e il prezzo della coerenza tra due numeri affiancati. Il flag `isIncludedInBudget` resta un asse distinto e non si applica qui (governa solo budget e spendibile). Le statistiche restano volutamente invariate: sono una superficie di analisi storica, e cancellarvi retroattivamente un conto archiviato disallineerebbe anche i drill-down
- [x] **Decisione di prodotto**: tie-break deterministico in `primaryCurrency()` (prima `maxByOrNull` prendeva il primo incontrato). A parita di conti vince `fallbackCurrency`, poi il codice ISO in ordine alfabetico: senza, aggiungere un conto poteva ribaltare la valuta di ogni aggregato in base all'ordine della lista
- [x] `budgets_empty_body` marcata `formatted="false"` (IT + EN): gli `80%`/`100%` non escapati producevano l'unico warning del build (`Multiple substitutions specified in non-positional format`)
- [x] Test: nuovo `PrimaryCurrencyTest` (maggioranza, esclusi/archiviati, tie-break indipendente dall'ordine, override); nuovi casi in `AccountEditorViewModelTest` (valuta primaria esplicita e implicita, form pulito, valuta del conto in modifica), `RecurrencesViewModelTest` (regola del trimestre dopo elencata ma non prezzata, regola che parte piu avanti nel mese gia contata, risparmio pianificato), `SettleCreditCardStatementUseCaseTest` (ciclo vuoto saltato), `CategoryEditorViewModelTest` (candidati per tipi realmente presenti), `SavingsGoalEditorViewModelTest` (preselezione che non sporca il form, modifica reale preservata). Aggiornato il caso dell'hub che codificava il conteggio sbagliato delle regole future

## Fase 10.15 - Nota del movimento, cancellazione dati e filtro "senza categoria" (luglio 2026)

> Tre gap di prodotto emersi dalla quarta review, implementati su richiesta utente (versionCode 122 -> 123, versionName 0.9.83 -> 0.9.84). Nessun cambio di schema: la colonna `note` esisteva gia inutilizzata, il filtro e in memoria come tutto il motore filtri, la cancellazione riusa il write path del restore.

- [x] **Nota del movimento scrivibile**: il campo esisteva nel modello, nel DB, nel backup, nell'export/import CSV ed era gia cercato dalla ricerca del registro, ma nessun editor lo esponeva (`buildTransaction` si limitava a riportare `base?.note`). Ora e un campo del form a tutti gli effetti: entra nello snapshot di dirty detection, si salva con `trim()` e una nota di soli spazi persiste come `null` (una stringa vuota resterebbe per sempre nell'indice della ricerca)
- [x] UX della nota: campo multilinea borderless, stessa estetica piatta di importo e descrizione. **Scelta di prodotto**: su un movimento nuovo resta dietro un'azione testuale discreta ("Aggiungi una nota"), cosi il form della spesa tipica non si allunga di un riquadro vuoto che quasi nessuno compila; un movimento che una nota ce l'ha gia la mostra sempre. Rivelata una volta resta aperta anche se svuotata, e prende il focus solo quando e l'utente a chiederla (aprire un vecchio movimento non deve rubare il focus all'importo)
- [x] **Cancellazione di tutti i dati**: `BackupRepository.eraseAll()` svuota ogni tabella e **ripianta le categorie predefinite** nella stessa transazione. Il ripianto non e un optional: il seed vive nel callback `onCreate` di Room, che su un file esistente non gira mai piu, quindi una pulizia secca lascerebbe l'app senza categorie e senza modo di riaverle se non reinstallando
- [x] `EraseAllDataUseCase`: prima il database (transazione propria), poi le preferenze, poi il segnale di reset. L'ordine e la parte importante - se il wipe fallisce le preferenze restano intatte, perche un database integro che ha dimenticato valuta, tema e conto predefinito e peggio del non aver cancellato
- [x] Ritorno all'onboarding dopo la cancellazione via `AppResetCoordinator` (singleton, pattern di `UndoDeleteCoordinator`). **Scelta di design**: un segnale esplicito e non una lettura reattiva del flag onboarding, perche quel flag viene scritto anche a meta onboarding (installazione esistente che crea il primo conto) e un gate che lo osservasse salterebbe fuori dal flusso nel momento sbagliato. Il gate resta una decisione one-shot e si mette in ascolto
- [x] UI della cancellazione: card "danger zone" in fondo alla schermata Dati, outlined in colore error invece dei pannelli pieni sopra, cosi non si legge come una delle operazioni di routine. Sta li perche "fai prima un backup" e il consiglio che le va accanto, a un tap dal bottone che lo produce. Il dialog elenca cosa sparisce e apre col fatto che decide davvero: la data dell'ultimo backup, o l'avviso in rosso che un backup non c'e mai stato. Nessuna snackbar di successo (la schermata non esiste piu: il ritorno all'onboarding e il feedback)
- [x] **Filtro "senza categoria"** nel registro: `TransactionFilters.includeUncategorized`, in unione con `categoryIds` e non in alternativa ("queste categorie, piu quelle senza"). Chip in testa alla sezione Categorie del filter sheet, distinto dall'icona `LabelOff`, piu chip rimovibile nella barra dei filtri attivi; conta nel gruppo categoria del badge, non come gruppo a se
- [x] Drill-down statistiche unificato: la fetta "Senza categoria" dell'anello passava da un predicato suo dentro `matchesStatsScope`, ora semina lo stesso flag e risolve attraverso il motore filtri condiviso. Un solo posto dove "senza categoria" e definito
- [x] Anche la sezione Categorie del filter sheet non e piu nascosta a lista vuota: senza categorie il chip "Senza categoria" resta l'unico modo di isolare quei movimenti
- [x] Stringhe IT/EN (parita verificata); test: `EraseAllDataUseCaseTest` (ordine delle scritture, preferenze intatte su fallimento), casi nuovi in `TransactionFilterEngineTest` (unione, solo-senza-categoria, nessun termine, conteggio del badge) e `TransactionEditorViewModelTest` (nota salvata e trimmata, nota di soli spazi -> null, dirty detection). Gate `assembleDebug testDebugUnitTest lint detekt` verde

## Fase 10.16 - Segnalazione multi-valuta nelle Statistiche (luglio 2026)

> Ultimo dei quattro gap della quarta review (versionCode 123 -> 124, versionName 0.9.84 -> 0.9.85). Ogni cifra delle statistiche e scoped alla valuta principale (la conversione e in v2.0), quindi un periodo che contiene anche movimenti in altre valute veniva sotto-riportato in silenzio. Nessun cambio di schema: una query di sola lettura.

- [x] Ricognizione prima di implementare: delle superfici che sembravano scoperte, due lo erano gia solo in apparenza. La card Saldo totale e coperta dalla Fase 9.8 (saldo attenuato + codice ISO sui conti non primari) e il **drill-down delle card Oggi/Mese e il registro sono coperti da `FilteredTotalsBar`, che stampa gia una riga di totali per valuta** (`filteredTotals` raggruppa per valuta). Restava scoperta solo la schermata Statistiche
- [x] **Dashboard invariata** (scelta utente): niente banner ne riga aggiuntiva sulla schermata piu densa dell'app, dove l'informazione e comunque a un tap di distanza nel drill-down
- [x] `TransactionDao.observeOtherCurrencyCount`: gli stessi filtri di `observeCategoryTotals` col test sulla valuta invertito, cioe esattamente i movimenti che sarebbero entrati nelle statistiche se solo fossero stati nella valuta principale
- [x] Riga informativa sotto il selettore di periodo, mostrata solo quando il conteggio e maggiore di zero: "N movimenti in altre valute non sono conteggiati qui: questi dati sono solo in EUR", con chevron e tap che apre il drill-down di quei movimenti. Superficie quieta (`surfaceContainerHigh`, testo `onSurfaceVariant`), non un warning: non c'e niente di rotto, i grafici semplicemente non possono sommare due valute finche non esiste la conversione
- [x] **La riga compare anche sull'empty state**, ed e il caso che conta di piu: `hasData` guarda solo gli aggregati in valuta principale, quindi un periodo con soli movimenti esteri finiva su "non hai ancora registrato nulla" pur avendo movimenti dentro. Ora l'empty state si spiega
- [x] Drill-down: nuovo flag `otherCurrenciesOnly` sulla route, che inverte il test di valuta dentro `matchesStatsScope` e da alla schermata il titolo "Altre valute". Riusa la stessa schermata e lo stesso motore filtri degli altri drill-down
- [x] Stringhe IT/EN con plurals (parita verificata); test in `StatsViewModelTest` (notice accanto alle cifre, empty state che si spiega, nessuna notice a valuta unica, nessuna notice a registro vuoto). Gate `testDebugUnitTest lint detekt` verde

## Fase 10.17 - Inserimento movimenti: tastierino in-app e form compatto (luglio 2026)

> Review UI/UX della schermata di inserimento movimenti su richiesta utente, a partire dagli screenshot dei due percorsi (`Nuovo movimento` dal registro, `Nuova spesa` dalla Dashboard: un solo editor). Tre interventi, tutti scelti dall'utente. Nessun cambio di dominio o schema. Design: ADR 31, che revisiona l'ADR 16.

- [x] **Tastierino importi in-app su tutti i campi importo** (revisione dell'ADR 16, scelta utente): `AmountKeypad` in `core/designsystem/component`, griglia piatta a 3 colonne disegnata con soli token di tema (corretta in chiaro, scuro, palette brand e dynamic color), separatore decimale dalla locale, tasto separatore assente per le valute a 0 decimali, backspace che azzera al long-press, haptics per tasto e maniglia per chiudere. Tasti a 48dp: il pannello e piu basso di una tastiera di sistema e, soprattutto, la sua altezza e nostra
- [x] `AmountKeypadHost(target)` + `AmountTarget(value, fractionDigits, allowNegative, onValueChange)`: nessun registry globale, ogni schermata tiene lo stato del campo attivo e costruisce il target. `AmountInputEditor.apply` (puro, in `core/common/money`) applica il tasto e delega ogni regola a `MoneyInput.sanitize`
- [x] `HeroAmountField` non e piu un `BasicTextField` (era l'unico motivo per cui si apriva l'IME): display con caret lampeggiante, migliaia raggruppate da `MoneyInput.grouped`. Le due cose che il campo di testo dava gratis sono mantenute esplicitamente - tastiera hardware sul campo con focus (`onKeyEvent`) e incolla al long-press - insieme a `Role.Button` e `contentDescription` che recita l'importo, che e la condizione per cui la revisione dell'ADR 16 sta in piedi
- [x] `AmountTextField`: la forma non-hero dello stesso input (saldo iniziale, massimale carta, saldo in onboarding, min/max del filtro registro). `OutlinedTextField` read-only che apre il tastierino al tap, con la pressione letta dall'`interactionSource` (un campo read-only si tiene i tap per il proprio cursore). Nel filtro un solo tastierino segue il campo toccato
- [x] Convivenza con l'IME di sistema, che resta per i campi di testo: il focus su descrizione, nota o nome chiude il tastierino; il tap sull'importo chiude l'IME; il back chiude il pannello prima della guardia delle modifiche non salvate. Il pannello vive dentro `EditorBottomBar`, quindi il contenuto riceve l'inset giusto e Salva resta sempre sopra
- [x] **Data e ora in un solo chip** (scelta utente): il chip ora costava una riga intera (tre chip non stanno su una linea) per una cifra che l'app non mostra da nessuna parte - non nel registro, non nell'export CSV - e che serve solo a ordinare i movimenti dentro la stessa giornata. Ora e un unico chip (data e ora separate dal punto medio, come le card della Dashboard), con l'ora modificabile da una riga dentro il dialog della data (nuovo slot `timeRow` opzionale su `SaldoDatePickerDialog`, cosi gli altri date dialog non cambiano); modificare l'ora non perde la data in corso di modifica
- [x] **Form a due zone**: la parte che decide un movimento (tipo, importo, conto, data) e fissa, e la zona scorrevole apre con le categorie, cosi su qualsiasi schermo normale due righe di categorie stanno sotto i chip senza scorrere. Prima, con la tastiera aperta, si vedeva mezza riga
- [x] Griglia categorie: `ScrollingCategoryGrid` mostra **tutte** le categorie in un box alto due righe che scorre da solo (rimossi il cap `CATEGORY_GRID_CAP` e `visibleCategories`, che mostravano le prime otto con la selezionata infilata dentro); "Tutte" resta per l'elenco completo. L'altezza segue il font scale e la griglia sta dentro la zona scorrevole, cosi uno schermo corto scorre invece di tagliare
- [x] Spaziature ricalibrate nella zona fissa; scroll-to della griglia sulla categoria selezionata quando si apre un movimento esistente
- [x] Stringhe IT/EN (`keypad_clear`, `keypad_decimal_separator`, `keypad_hide`, `action_paste`, etichette d'azione del chip data/ora); test JVM `AmountInputEditorTest` (cifre, separatore unico, decimali al limite della valuta, valuta a 0 decimali, backspace, clear, segno, cap delle cifre intere) e casi nuovi in `MoneyInputTest` per `grouped`. Gate `assembleDebug testDebugUnitTest lint detekt` verde (576 test, 0 falliti)
- [x] Correzioni dalla prova su device (versionCode 125 -> 126, versionName 0.9.86 -> 0.9.87): il chip data+ora diventa **due controlli in una pillola** (meta calendario, divider, meta orologio), perche l'ora dentro il dialog della data non era scopribile - l'utente l'ha trovata toccando l'intestazione. Lo slot `timeRow` di `SaldoDatePickerDialog` e rimosso e il dialog torna com'era
- [x] Selettore di tipo: "Trasferimento" toccava il bordo del segmento. Rimosso il glifo di spunta (`icon = {}`, la selezione resta leggibile dal contenitore pieno e dalle semantics) e label a `labelMedium` con `maxLines = 1`
- [x] **Chip rapidi "Oggi"/"Ieri" invisibili dalla Fase 10.12**, confermato su device e diagnosticato nel bytecode di material3 1.4.0: `DatePickerDialog` dichiara il proprio slot come `content: @Composable ColumnScope.() -> Unit` ma lo dispone in un **Box** (`checkcast BoxScope`, con `ColumnScopeInstance` passato come receiver), quindi i figli si sovrappongono invece di impilarsi. Il calendario, composto per ultimo, copriva i chip; la sua intestazione non e cliccabile, ed e per questo che il tap ci cadeva attraverso e apriva la riga dell'ora finche c'era. Correzione: `Column` esplicita dentro lo slot, una riga, innocua anche se un domani il comportamento cambiasse
- [x] Trasferimenti: i due chip conto andavano a capo su righe sfalsate e senza dire quale fosse la partenza. Diventano due righe piene allineate su una colonna di etichette ("Da" / "A"), con lo scambio delle gambe spostato accanto come icona verticale (`SwapVert`). Il nome del conto ellissa invece di spingere la freccia fuori dalla pillola (`weight(1f, fill = false)` sul testo di `EditorChip`)

## Fase 10.18 - Widget di inserimento rapido (luglio 2026)

> Widget home anticipato dalla roadmap v1.5 su richiesta utente, prima della release v1.0. Parte da una nota a mano: prima schermata con tipo, conto e griglia categorie, seconda schermata con importo e tastierino. La struttura e confermata, con due correzioni motivate: il tastierino esce dal widget (ADR 32) e il conto sale nella configurazione del widget. Nessun cambio di schema: `SALDO_DATABASE_VERSION` resta 1, il backup non e toccato. Design: ADR 32.

- [x] Dipendenza Glance (`androidx.glance:glance-appwidget` + `glance-material3`, 1.2.0-rc01) nel version catalog, approvata dall'utente. La 1.3.0-alpha richiede AGP 9.2 e compileSdk 37, fuori dai pin attuali (Hilt 2.58 tiene AGP a 8.x); la 1.2.0 e compilata su compileSdk 35 con AGP 8.1+ e ha l'API congelata. Ripiego dichiarato in caso di problemi: 1.1.1 stable
- [x] **Estrazioni condivise** perche le regole del denaro non esistano in due copie: `TransactionSign.signed` (convenzione di segno, era privata nell'editor), `DefaultAccountResolver.resolve` (catena default esplicito -> ultimo usato -> primo attivo, era dentro `preselectDefaultAccount`), `QuickTransactionFactory.create` (movimento EXPENSE/INCOME con importo riscalato alla valuta del conto e offset preso alla data del movimento, non a "adesso"). L'editor completo usa i primi due, il ramo trasferimenti resta dov'era
- [x] `SaldoQuickAddWidget` (Glance) con `SizeMode.Responsive` a tre bucket disegnati, non spremuti: 2x2 quattro tile sole icone (tipo dalla configurazione), 4x2 selettore tipo + quattro tile con etichetta, 4x3 selettore tipo + totale di oggi + otto tile. L'ultima tile e sempre "Altro" e apre l'editor completo riusando la catena shortcut esistente (`MainActivity.ACTION_ADD_EXPENSE`): senza, il widget sarebbe un vicolo cieco per categorie fuori griglia, trasferimenti, note e date diverse da oggi
- [x] **Icone attraverso il confine Glance**: `CategoryIconBitmaps` rasterizza a runtime lo stesso `ImageVector` che disegna l'app (walk dell'albero vettoriale, `pathData.toPath()`, trasformazioni di gruppo applicate, cache LRU), invece di duplicare 40 vector drawable che diverrebbero dall'unica mappa di `CategoryVisuals`. La tile e la squircle di `AvatarShape` col colore della categoria e il glifo in `contentColorOn`; se un vettore non si disegna resta la squircle colorata, mai un crash e mai un buco
- [x] Tema del widget dalle stesse Impostazioni dell'app: palette brand di default, dynamic color se attivo, e il tema chiaro/scuro scelto in-app passato come **stesso** schema su entrambi i rami dei `ColorProviders`, altrimenti il launcher deciderebbe col proprio night mode e annullerebbe la scelta
- [x] **`QuickEntryActivity`**: activity traslucida che apre un `ModalBottomSheet` sopra il launcher, col vero `AmountKeypad` (ADR 31) gia aperto, `HeroAmountField`, categoria e conto correggibili in loco (riuso di `AccountPickerSheet` e `CategoryPickerSheet`), Salva, stato di conferma con spunta e importo formattato e chiusura automatica. `SaldoTheme` prende `applyBackground = false` perche il backdrop opaco (fix del flash bianco, commit 6dc7675) qui nasconderebbe il launcher
- [x] Query `TransactionDao.mostUsedCategories`: categorie piu usate negli ultimi 60 giorni per tipo, ordinate per conteggio e a parita per uso piu recente. **Non** e una query statistica: conta ogni valuta, ogni conto e anche i movimenti esclusi dalle statistiche, perche "cosa tocco di solito" non ha niente a che vedere con cosa sommano i grafici; restano fuori solo i pending. Le piu usate guidano e l'ordine dell'utente riempie i posti restanti, cosi la griglia non e mai corta a installazione nuova
- [x] `QuickAddWidgetConfigActivity`: conto, tipo di partenza, categorie adattive o fissate, totale di oggi. Provider dichiarato `reconfigurable|configuration_optional`, quindi il widget funziona appena piazzato e la configurazione e riapribile ma non e un casello; annullare lascia comunque un widget funzionante sui default. Stato per istanza nelle preferenze Glance (niente Long nullable ne liste: sentinella e stringa separata da virgole, coperte da test)
- [x] `WidgetRefreshWatcher` application-scoped (stampo di `BudgetThresholdWatcher`): osserva movimenti e categorie con debounce a 500ms e ridisegna, **solo mentre almeno un widget e piazzato** (il receiver riporta i cambi da `onEnabled`/`onDisabled`/`onUpdate`), cosi chi non usa il widget non paga un osservatore sul database. Il worker giornaliero chiama `refresh()` perche il totale di oggi scade a mezzanotte anche a device fermo
- [x] Un conto configurato e poi archiviato o cancellato non lascia il widget morto: si ricade sulla catena di default dell'app, sia nel widget sia nella sheet
- [x] Stringhe IT/EN, `contentDescription` su ogni tile, `maxLines = 1` sulle etichette
- [x] Test JVM (girano in CI): `TransactionSignTest`, `DefaultAccountResolverTest`, `QuickTransactionFactoryTest`, `QuickEntryViewModelTest`, `QuickAddWidgetDataLoaderTest`, `QuickAddWidgetPrefsTest`, `CategoryIconStructureTest` (ogni chiave icona e disegnabile dal renderer: intercetta a build time una 41esima icona di forma non gestita). Strumentati da eseguire su device: `CategoryIconBitmapsTest` (rasterizza tutte le 40 icone e verifica che ci siano pixel di glifo) e `TransactionDaoMostUsedTest`
- [x] **Fix dalla prima prova su device** (versionCode 128 -> 129, versionName 0.9.89 -> 0.9.90). (1) Il selettore Spesa/Entrata non cambiava quasi mai: la pillola lampeggiava ma il widget restava com'era. Causa trovata nel bytecode di `AppWidgetSession`: su `UpdateGlanceState` (l'evento che manda `update()`) la sessione rilegge lo stato con `ConfigManager.getValue`, lo scrive nel `MutableState` `glanceState` dentro uno snapshot e lascia fare alla ricomposizione - **`provideGlance` non viene mai richiamato**, gira una volta sola alla creazione della sessione. Configurazione, dati e tema erano letti prima di `provideContent`, quindi congelati per tutta la vita della sessione; le volte in cui funzionava erano quelle in cui la sessione veniva ricreata da sola. Correzione: lo stato arriva da `currentState()`, i dati da `produceState` chiavato su di esso, il tema da `collectAsState` sulle preferenze. Il caricamento iniziale in `provideGlance` resta solo per non far lampeggiare il primo frame
- [x] `QuickAddWidgetPrefs.Revision`, bumpato da `WidgetRefreshWatcher.refresh()` prima di `updateAll`: lo stato del widget e l'unico canale che una sessione Glance ascolta, quindi un movimento registrato deve arrivare come cambio di stato o la ricomposizione ridisegnerebbe lo stesso identico snapshot
- [x] La tile di uscita non si confonde piu con la categoria "Altro" del seed (segnalazione utente): squircle **outline** nel colore brand invece del pieno della palette categorie, glifo `MoreHoriz`, etichetta "Apri Saldo" / "Open Saldo". Forma, colore e testo diversi, nessuna collisione possibile
- [x] Bug trovato mentre si correggeva la tile: apriva **sempre** l'editor di una spesa (`ACTION_ADD_EXPENSE` cablata), anche dal widget Entrata. Ora instrada sul tipo mostrato, con `quickActionFor` estratta e coperta da test
- [x] **Selettore tipo difficile da premere** (seconda prova su device, versionCode 129 -> 130, versionName 0.9.90 -> 0.9.91): non era piu un problema di stato ma di bersaglio. La pillola era un `Text` con padding, circa 20dp di altezza contro i 48dp minimi di un target Android; "Spesa", parola piu corta, era anche la piu stretta, ed e per questo che il difetto sembrava colpire una sola direzione. Ora e un `Box` alto 36dp con il testo centrato e tutta la pillola cliccabile, padding orizzontale 14dp. Il budget verticale e stato ribilanciato per pagarla senza tagliare le tile: padding del widget 12 -> 10dp, gap dell'intestazione 10 -> 8dp, tile del bucket 4x2 40 -> 36dp
- [x] Ipotesi collisione di `PendingIntent` tra le due pillole **verificata e scartata** prima di intervenire: `ActionTrampolineKt.createUniqueUri` costruisce per ogni azione un URI con `appWidgetId`, `viewId` e `viewSize`, quindi due controlli distinti non possono condividere un intent
- [x] **Il selettore andava una volta sola** e **il widget appena installato apriva sempre l'app** (terza prova su device, versionCode 130 -> 131, versionName 0.9.91 -> 0.9.92). Due bug distinti, entrambi miei. (1) Il `produceState` che ricarica i dati aveva una guardia `if (inputs != initialInputs)`, messa per risparmiare una query all'avvio: `initialInputs` e fisso per tutta la sessione, quindi al ritorno sullo stato di partenza la guardia diceva "uguale", il producer non faceva nulla e restavano in `value` i dati dell'altro tipo. Rimossa: si ricarica sempre. (2) `WidgetRefreshWatcher` osservava movimenti e categorie ma **non i conti**, e un widget piazzato prima dell'onboarding non ha conti, quindi `isReady` e falso e il widget e un unico testo "Apri Saldo per iniziare" largo quanto la tile - qualunque tocco apre l'app. La creazione del primo conto, cioe esattamente l'evento che lo rende usabile, non produceva alcun segnale. Aggiunto `observeAccountsWithBalance` ai segnali
- [x] Il selettore di tipo ora si colora da `currentState()` e non dai dati caricati: il controllo appena premuto risponde subito e la griglia si allinea un istante dopo, invece di aspettare la query
- [x] Segnali di refresh estratti in `WidgetRefreshWatcher.refreshSignals()` e coperti da test (primo conto, categoria, movimento, e nessun segnale per lo stato gia a schermo): un'omissione li e invisibile in build e si manifesta solo come widget che smette di aggiornarsi
- [x] **Aspetto del widget allineato all'app e configurabile** (richiesta utente, versionCode 131 -> 132, versionName 0.9.92 -> 0.9.93). Le tile categoria ora sono disegnate come le disegna l'app per una categoria **non selezionata**: squircle col colore della categoria al 16% e glifo nel colore pieno (`TransactionEditorComponents.kt:310-316`). Prima erano piene con glifo bianco, che nell'app e lo stato *selezionato*: ogni tile sembrava selezionata e nessuna somigliava all'app. La tile "Apri Saldo" segue lo stesso stile nel colore brand
- [x] Sfondo del widget su `colorScheme.background`, lo stesso della Dashboard, invece di `GlanceTheme.colors.widgetBackground`
- [x] `WidgetAppearance` per istanza: Sistema / Chiaro / Scuro / Colore. Con Colore la palette include **bianco puro e nero puro** in testa (sono i due che un wallpaper chiede piu spesso) e per il resto neutri, perche il colore su questo widget appartiene alle icone e uno sfondo tinto le disturberebbe. Lo schema di colori viene scelto dalla **luminanza dello sfondo scelto**, non dalle impostazioni dell'app: e la condizione perche un widget nero su app chiara non finisca con etichette scure su nero
- [x] Slider di opacita 0-100% sullo sfondo. Tocca solo lo sfondo: etichette, selettore e icone restano pieni, e le tile mantengono la propria velatura, quindi restano leggibili anche a sfondo trasparente. Valore clampato **in lettura** oltre che in scrittura: un'opacita fuori range renderebbe un widget invisibile, e l'unico modo per tornare indietro sarebbe la schermata di configurazione che serve vedere il widget per raggiungere
- [x] Anteprima live in cima alla configurazione, su scacchiera: l'opacita e impossibile da scegliere alla cieca, e la scacchiera e il modo onesto di mostrare la trasparenza senza chiedere il permesso che leggere il wallpaper vero costerebbe. Il tema dell'anteprima e risolto dalla stessa `resolveWidgetTheme` che usa il widget, quindi non puo divergere da cio che viene disegnato
- [x] **Semplificazione e taglia** (prova su device, versionCode 132 -> 133, versionName 0.9.93 -> 0.9.94). Colore di sfondo personalizzato e slider di opacita **rimossi su decisione dell'utente**: `WidgetAppearance` torna a Sistema / Chiaro / Scuro, e con loro spariscono `WidgetPalette`, la palette di swatch e il clamp dell'opacita. L'anteprima resta (serve ancora per la scelta chiaro/scuro) ma perde la scacchiera, che senza trasparenza non dice piu nulla
- [x] L'azione della schermata dice "Aggiorna il widget" quando il widget e gia configurato. Android usa la stessa activity e lo stesso intent per il primo piazzamento e per una modifica successiva, quindi l'unico modo per distinguerli e un marcatore `Configured` scritto alla conferma
- [x] Tutto piu grande, su segnalazione: glifo dal 55% al 62% della tile (il guadagno piu visibile e costa zero in altezza), tile 40 -> 44 / 36 -> 40 / 44 -> 52 per bucket, etichette 11 -> 12sp, pillole e importo 12 -> 13sp. Il budget verticale e stato ripagato riducendo padding verticale e gap; il padding orizzontale invece **sale** a 12dp, che e anche la correzione dell'importo troppo attaccato al bordo destro (piu 4dp di padding suo, perche un testo abbraccia i glifi molto piu stretto di quanto una pillola abbracci la propria etichetta)
- [ ] Verifica su device: piazzamento a 2x2 / 4x2 / 4x3, spesa salvata dal launcher, i tre temi, TalkBack, font scale al massimo, tile "Altro" a processo caldo e freddo, e almeno due launcher diversi (gli update Glance hanno jank noto su alcune build OEM)
- [ ] Punto aperto da verificare su device: i picker di conto e categoria sono `ModalBottomSheet` aperti sopra la sheet principale. Se lo stack di due sheet risultasse sgradevole, il ripiego e sostituirli con un dialog

# Fase cloud - Backup su Google Drive (da valutare a fine roadmap)

> Parte cloud della Fase 8, spostata qui a luglio 2026 (ADR 17). Da valutare quando le fasi delle roadmap saranno concluse: il formato JSON versionato e il code path di export/restore della Fase 8 si riusano così come sono.

- [ ] Google Sign-In via Credential Manager, scope `drive.appdata`
- [ ] Upload backup su App Data Folder + rotazione (ultimi 5)
- [ ] Backup automatico WorkManager (periodico, solo Wi-Fi, configurabile)
- [ ] Restore guidato dal backup Drive (primo avvio e da impostazioni)

---

# Strategia di test

- **Unit test** (priorità massima): mapper importi, motore ricorrenze, calcolo saldi, use case, round-trip backup
- **Test Room** in-memory: query aggregate, esclusioni statistiche, migration test da schema 1 in poi
- **UI test Compose** sui flussi critici: inserimento spesa in ≤3 tap, rettifica saldo, restore
- Le migration Room sono **obbligatorie e testate** da subito dopo la prima release (mai `fallbackToDestructiveMigration` in produzione)

# Definition of Done (per ogni feature)

- [ ] Funziona offline
- [ ] Stringhe IT + EN
- [ ] Stati empty/loading/error gestiti
- [ ] Accessibile (TalkBack, font scaling)
- [ ] Test verdi (unit + eventuali UI)
- [ ] Nessuna regressione sui saldi

---

# Note e appunti

> Idee, spunti e appunti raccolti durante lo sviluppo. Da smistare periodicamente nella roadmap.

- Chore da pianificare: migrazione ad AGP 9.x + Gradle 9.1+ e compileSdk/targetSdk 37, da fare quando Android 17 (API 37) diventa stabile: a luglio 2026 è ancora in Beta (verificato su developer.android.com/about/versions/17), quindi il valore fissato 36 (ADR 14) è anche l'ultimo stabile disponibile. Vincoli attuali: Hilt 2.59+ richiede AGP 9; androidx core 1.19, lifecycle 2.11 e Compose BOM 2026.06 richiedono compileSdk 37 (AGP 9.1+). Fino ad allora restano fissati: AGP 8.13.2, Hilt 2.58, compileSdk/targetSdk 36, core-ktx 1.18.0, lifecycle 2.10.0, BOM 2026.02.01, activity-compose 1.12.4.
- Nota ambiente Claude Code web: il download delle distribuzioni Gradle è bloccato dal proxy (il redirect finale punta a un asset GitHub fuori dallo scope di rete della sessione); i build locali usano il Gradle preinstallato in `/opt/gradle`. AGP e le librerie si scaricano normalmente da Google Maven/Maven Central; `sdkmanager` funziona (dl.google.com), incluso `platforms;android-37.0` quando servirà.
- Idee "wow" dalla review completa di luglio 2026 (tutte compatibili con VISION: offline-first, privacy-first, niente open banking). Non implementarle senza deciderlo esplicitamente. Il "Radar abbonamenti" è stato implementato a luglio 2026 (Fase 6, incremento 3); "Spendibile oggi" è stato implementato nella Fase 9.5 insieme al budget:
  - ~~"Spendibile oggi" (safe-to-spend)~~: implementata (Fase 9.5).
  - ~~Rilevamento automatico ricorrenze~~: promosso nella Roadmap v2.0 (luglio 2026).
  - ~~Recap mensile condivisibile (stile Wrapped)~~: implementato (Fase 10.1).
  - Quick-add ovunque: ~~widget home~~ (implementato in Fase 10.18), ~~app shortcut statici~~ (implementati in Fase 9.6), Quick Settings tile: spesa registrata in 2 tap senza aprire l'app.
  - Quick entry testuale: parser offline di "12,50 pizza" → importo + categoria suggerita.
- ~~Indicatore "generato da ricorrenza" nella lista Movimenti~~: implementato nella Fase 10.5 (luglio 2026). Icona `Repeat` sulla riga (registro, Dashboard e drill-down statistiche), banner nell'editor con il nome della regola, e filtro per origine (ricorrenti/manuali) nel registro.

# Bug conosciuti

> Bug noti da risolvere. Spuntare quando fixati (con riferimento al commit).

Trovati dalla review completa di luglio 2026:

- [x] Generazione ricorrenze non atomica: un'interruzione (rotazione, morte del processo) o l'esecuzione concorrente di worker e catch-up poteva duplicare i movimenti, perché gli insert e l'avanzamento di `lastGeneratedDate` erano scritture separate senza transazione e il catch-up girava nel `lifecycleScope`. Fix: transazione unica per regola, mutex, catch-up su application scope, unique index (recurringRuleId, recurringOccurrenceEpochDay) come backstop con migration 3→4 (commit 74c805e)
- [x] Editor: `isSaving` mai resettato e scritture repository senza gestione errori; un salvataggio fallito bloccava l'editor movimento per sempre e gli altri editor erano esposti al doppio tap (commit 74c805e)
- [x] Seed categorie non crash-safe: insert asincrona fuori dalla transazione di creazione del DB; se il processo moriva nel mezzo il DB restava senza categorie predefinite (commit 74c805e)
- [x] `spentMoreThanLastMonth` calcolato anche senza baseline del mese precedente (commit 74c805e)
- [x] Conteggio abbonamenti su tutte le valute ma totale mensile solo nella valuta principale: "N abbonamenti - X/mese" incoerente (commit 74c805e)
- [x] Cambio cadenza di una regola ricorrente manteneva il vecchio `lastGeneratedDate`, disallineato con la nuova schedule (commit 74c805e)
- [x] Notifica di conferma con il conteggio del solo batch appena generato invece dei pending totali in attesa (commit 74c805e)
- [x] Riordino categorie non funzionante: al primo scambio la categoria trascinata restava sospesa sopra le altre e non rispondeva più (entrambi i tab). Causa: `reorderableHandle` chiavava `pointerInput` sull'indice della riga; il riordino live cambia l'indice al primo scambio, restartando (e quindi cancellando) il gesto in corso. Fix: `pointerInput` chiavato sull'id stabile della riga, indice letto lazily via `rememberUpdatedState` (v0.9.17)
- [x] Riordino categorie: il `sortOrder` globale accoppiava i tab; riordinare Spese poteva rimescolare l'ordine relativo delle categorie "entrambi" nel tab Entrate. Fix: `sortOrder` per tipo. Aggiunta la colonna `sortOrderIncome` (chiave d'ordinamento del tab Entrate, `sortOrder` resta quella del tab Spese), migration 6→7 che la popola da `sortOrder` per preservare l'ordine esistente. Ogni tab ora si riordina in modo indipendente e una categoria "entrambi" tiene una posizione distinta in ciascuno (v0.9.18)
- [x] Dashboard multi-valuta: la card "Saldo totale" somma solo i conti nella valuta principale, ma il breakdown sotto elenca tutti i conti attivi ciascuno nella propria valuta; con conti in valute diverse il totale sembra non tornare rispetto alle righe. Non è un errore di calcolo (valute diverse non si sommano senza cambio), ma la presentazione era ambigua. Fix (Fase 9.8, v0.9.19): i conti che non contribuiscono al totale hanno il saldo attenuato e i conti non-primari mostrano il codice valuta ISO come marcatore; nessun cambio alle query, solo presentazione. Il cambio valuta resta rimandato alla v2.0.
- [x] Tema scuro forzato dall'app con sistema in chiaro: `enableEdgeToEdge()` senza argomenti segue solo il uiMode di sistema, quindi le icone della status bar restavano scure su sfondo scuro (barra illeggibile, "tutta nera"). Fix: `enableEdgeToEdge` riapplicata in `setContent` con `SystemBarStyle` agganciato al tema risolto in-app (commit 15eb056)
- [x] Sfarfallio bianco nelle transizioni di navigazione con tema scuro forzato: durante il fade delle transizioni Nav3 si intravedeva la finestra Android chiara sotto Compose (stessa causa del bug status bar). Fix: `SaldoTheme` avvolge il contenuto in un `Surface` a tutto schermo con `colorScheme.background`, backdrop opaco a tema sempre presente (commit 6dc7675)
- [x] Saluti della dashboard troncati: header a riga singola (`maxLines = 1`) con varianti più lunghe dello spazio disponibile, testo tagliato con ellipsis. Fix: varianti accorciate (IT+EN) e `maxLines = 2` come margine per il font scaling (v0.7.2)
- [x] Statistiche, grafici "Spese" e "Entrate e uscite": il bottone "Vedi i movimenti di <mese>" compariva solo mentre il dito restava sul grafico e spariva al rilascio, rendendolo impremibile. Causa: il listener del marker Vico azzerava la selezione in `onHidden`, che scatta al touch-up. Fix: la selezione resta dopo il rilascio, il bottone rimane visibile e cliccabile (v0.8.8)
- [x] Data estesa italiana sulla card "Saldo totale" con iniziali maiuscole ("Lunedì 13 Luglio") su alcuni device: certe build OEM di ICU applicano ai nomi di giorni e mesi la capitalizzazione da contesto standalone, mentre la data italiana in prosa è minuscola. Fix: normalizzazione esplicita a minuscolo per la locale italiana in `fullWeekdayDate` (commit 64eb59e)

Trovati dalla terza review completa di luglio 2026 (fix nella Fase 9.7):

- [x] Restore backup: un codice valuta non ISO 4217 superava inspect e restore, poi `Currency.getInstance` crashava ogni lettura a dati vecchi già eliminati. Fix: validazione semantica del payload al decode + backstop nei mapper (commit 2f42f30)
- [x] Eliminazione conto con regole ricorrenti: il guard contava solo i movimenti, la FK faceva fallire la DELETE con un generico "scrittura fallita". Fix: conteggio regole nel guard e messaggio con il motivo reale (commit 1b02e32)
- [x] Editor regola ricorrente inutilizzabile se il conto della regola era archiviato (salvataggio fallito senza errore visibile). Fix: il conto referenziato resta selezionabile, come nell'editor movimenti (commit 21bef39)
- [x] Watermark di generazione avanzato con upsert full-row: una modifica dell'utente salvata durante una run poteva essere sovrascritta. Fix: UPDATE mirato `updateLastGenerated` (commit 21bef39)
- [x] Modifica di una regola azzerava `lastReminderDate`: il promemoria pre-rinnovo veniva rinotificato. Fix: il watermark sopravvive all'edit (commit 21bef39)
- [x] Drill-down statistiche incoerente con la cifra toccata (includeva esclusi, altre valute e, per i conti, trasferimenti/rettifiche/entrate pure); il totale al centro del donut escludeva la spesa senza categoria che i 12 mesi contavano. Fix: scope statistiche nella route e fetta "Senza categoria" (commit bf9f6b9)
- [x] Day header del registro con maiuscole OEM italiane ("Lunedì 13 Luglio"), stesso bug già fixato sulla dashboard. Fix: normalizzazione condivisa `withLocaleDateCasing` su tutti i formatter con nomi (commit ac4f16d)
- [x] Riassegnazione in eliminazione categoria: le recurring rules andavano a NULL (movimenti futuri senza categoria) e il budget spariva in CASCADE senza avviso. Fix: riassegnazione estesa alle regole e avviso nel dialog (commit 4bd08a8)
- [x] Filtro importi con minimo > massimo: lista sempre vuota senza spiegazione. Fix: bounds normalizzati all'apply (commit a3fb9a9)
- [x] Card "Oggi" congelata oltre la mezzanotte a schermo acceso. Fix: ticker di mezzanotte condiviso su dashboard e statistiche (commit a3fb9a9)
- [x] Insets di sistema applicati due volte nell'onboarding (padding dello Scaffold + statusBars/navigationBars espliciti). Fix: insets consumati una volta sola (commit a3fb9a9)
- [x] Cambio tab distruggeva i ViewModel (skeleton, ri-query e perdita di scroll/ricerca/periodo a ogni rientro). Fix: back stack per tab (commit 436d8cd)
- [x] Icona bianca sui colori chiari della palette (~1.6-2.5:1 di contrasto su lime, verde chiaro, ambra, azzurro). Fix: `contentColorOn` per luminanza (commit ecc2d1c)

Trovati durante il redesign del periodo personalizzato (luglio 2026):

- [x] Chip "Personalizzato" del filtro date: l'etichetta chiamava `chipDayLabel(start, start)` passando la data stessa come "oggi", quindi mostrava sempre il prefisso "Oggi," su entrambe le date e non mostrava mai l'anno per i range di anni passati. Fix: nuovo formatter `shortDayLabel` senza prefisso, con l'anno quando differisce da quello corrente (v0.9.54)

Trovati a luglio 2026:

- [x] Crash all'avvio su installazione pulita o dopo "cancella dati": lo splash appariva e l'app si chiudeva subito. Causa: `DatabaseSeedCallback` inseriva le categorie predefinite omettendo la colonna `sortOrderIncome`. Sui DB aggiornati in-place la colonna esiste con `DEFAULT 0` (aggiunta via `ALTER TABLE` nella migration 6→7), quindi l'insert reggeva; su uno schema creato da zero Room non genera default, la colonna è `NOT NULL` senza default e l'insert falliva con constraint violation, abortendo `onCreate` a ogni avvio. Non era visibile finché si aggiornava sempre in-place senza reinstallare. Fix: il seed valorizza `sortOrderIncome`; aggiunto test strumentato `DatabaseCreationTest` che esercita il percorso di creazione da zero, prima non coperto (v0.9.26)
- [x] Budget e Obiettivi di risparmio mostravano il FAB anche a lista vuota, insieme al bottone centrale dell'empty-state (due CTA con lo stesso scopo). Le altre liste (Conti, Movimenti, Movimenti ricorrenti) nascondono il FAB quando vuote. Fix: guard del FAB esteso con `&& !uiState.isEmpty` in `BudgetsScreen` e `SavingsGoalsScreen`, uniformando al comportamento delle altre schermate (v0.9.45)
