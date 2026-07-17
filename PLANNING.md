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
| 16 | Importi inseriti con la tastiera di sistema (`OutlinedTextField` + `KeyboardType.Decimal`) in tutti gli editor; rimosso il tastierino custom | Coerenza tra tutti i campi importo (prima solo l'editor movimenti aveva un tastierino, gli altri la tastiera di sistema), accessibilità nativa (TalkBack, Switch Access, incolla, tastiera hardware) e meno codice da mantenere. Le regole di dominio restano garantite da `MoneyInput`/`MoneyMapper`, con cap cifre intere spostato in `MoneyInput.sanitize` così ogni campo è protetto dall'overflow. Decade l'haptic del tastierino (subentrano feedback/suoni tasto secondo le impostazioni di sistema) |
| 17 | Backup cloud (Google Sign-In + Drive App Data + backup automatico) fuori dal percorso v1.0: il backup della release è quello manuale locale su file (ADR 13); la parte cloud è una fase dedicata da valutare a fine roadmap | Nessun account Google nel percorso critico, coerente con offline/privacy-first; il formato JSON versionato (ADR 5) resta l'unico code path di export/restore, quindi l'eventuale fase cloud lo riusa senza migrazioni. Decisione di prodotto di luglio 2026 |
| 18 | Budget mensili: un budget complessivo opzionale (categoryId NULL) più budget per singola categoria di spesa, una riga per budget con valuta esplicita; la spesa confrontata è quella "statistica" (rimborsi a nettare, TRANSFER/ADJUSTMENT/esclusi/pending mai contati), non quella di cassa della card Mese; unicità del budget complessivo garantita a livello applicativo (upsert transazionale), perché l'indice UNIQUE di SQLite non vincola i NULL | I budget per categoria devono combaciare con gli aggregati delle statistiche; nelle categorie "entrambi" le entrate pure non riducono il consumato (filtro spesa refund-netted dedicato). Soglie 🟢<80% 🟡80-99% 🔴>=100% calcolate in aritmetica intera sui minor units, mai float. Anticipato dalla v1.5 prima della release: la tabella nasce nello schema di produzione e il backup resta a version 1 (campo additivo) |
| 19 | Notifiche soglia budget (80%/100%): use case one-shot idempotente con watermark mensile per budget (`lastNotified80/100EpochMonth`), invocato sia dal worker giornaliero delle ricorrenze sia da un watcher reattivo application-scoped (segnali di spesa debounced) | Il worker copre gli addebiti automatici a device fermo, il watcher notifica entro pochi istanti una spesa manuale che supera la soglia; il watermark rende la doppia via innocua (una notifica per soglia per mese per budget, superamento diretto del 100% = un solo avviso, nessun riarmo se la spesa rientra nel mese) |
| 20 | Carta di credito a saldo come nuovo `AccountType.CREDIT_CARD` con saldo negativo, non come strumento separato: le spese sono EXPENSE sul conto carta (già contate nelle statistiche/budget alla data d'acquisto), l'addebito dell'estratto è un singolo TRANSFER dal conto collegato che azzera il ciclo (escluso dalle statistiche come ogni trasferimento). Ciclo calcolato da un `BillingCycleCalculator` puro (giorno di chiusura + giorno di addebito configurabili, mesi corti gestiti col clamp), idempotenza via watermark `lastSettledClosingEpochDay` sul conto, seminato alla creazione così la storia pregressa non viene mai riaddebitata. Colonne additive (migration 8->9, schema v9), nessuna FK su `linkedAccountId` (self-reference: integrità in logica applicativa). L'importo dell'estratto è la negazione della somma dei soli movimenti propri del conto carta nel ciclo | Riusa il modello contabile esistente (trasferimento a record singolo, ADR 2; saldo calcolato, ADR 3; esclusione trasferimenti dalle statistiche, ADR 8) senza doppio conteggio: l'acquisto pesa nel mese in cui avviene, l'estratto è cassa e non statistica, il saldo totale riflette il debito come patrimonio netto reale. Auto-post e conferma riusano il pattern ricorrenze (worker giornaliero + catch-up all'avvio + notifiche). Il bancomat resta fuori dall'app (si registra sul conto corrente): è uno strumento che preleva subito dal conto, non un contenitore di denaro |
| 21 | Tassonomia carte esplicita: il tipo generico CARD è rimosso dall'enum e sostituito da `DEBIT_CARD` e `PREPAID_CARD` (migration dati 9->10: le righe CARD diventano DEBIT_CARD; app in test su un solo device, nessuna retrocompatibilità da mantenere). La carta di credito non ha saldo iniziale: parte sempre da zero e il debito già maturato si inserisce con la rettifica saldo, che essendo un movimento entra nel ciclo e viene addebitata col prossimo estratto | Un tipo "Carta" generico crea attrito ("la Postepay è Carta o Altro?"); debito e prepagata hanno semantiche diverse (la debito spende dal conto corrente, guida contestuale nell'editor; la prepagata è un contenitore autonomo, modello standard delle app premium). Il saldo iniziale su una carta di credito sarebbe debito fantasma: non è un movimento, quindi nessun estratto potrebbe mai addebitarlo e il conto non tornerebbe mai a zero |
| 22 | `DEBIT_CARD` ritirato dopo una release (migration dati 10->11: le righe diventano CHECKING) e sostituito dall'educazione contestuale: ogni tipo di conto mostra nell'editor una descrizione d'uso sotto il selettore, e quella del conto corrente spiega che le spese con bancomat/carte di debito si registrano lì. Nuovo tipo `SAVINGS` (conto di risparmio): contenitore per i soldi messi da parte, alimentato con trasferimenti, che alla selezione pre-imposta "Includi nel budget" su off (scelta esplicita dell'utente sempre vincente). Niente tipo dedicato per investimenti/titoli: fuori scope per VISION (niente quotazioni, offline-first); la liquidità destinata a investimenti si traccia col conto di risparmio, come dice la sua descrizione | Una carta di debito non è un contenitore di denaro (spende dal conto corrente): un tipo la cui guida scoraggia l'uso è un errore di tassonomia, e nessuna app premium la modella come conto. Il risparmio invece è il "recinto" più richiesto: la semantica giusta esiste già nel flag `isIncludedInBudget` (ADR 18/Fase 9.8), quindi il tipo aggiunge il default corretto senza nuovo schema; l'obiettivo con target resta agli Obiettivi di risparmio (v2.0), niente duplicazioni |
| 23 | Storia delle migration azzerata a un unico baseline v1 (schema di produzione attuale), una tantum finché l'app non è pubblicata. Rimossi gli 11 oggetti `Migration` (1->2 ... 11->12), i relativi schemi esportati (2.json ... 12.json) e i migration test; `SaldoDatabase.version` torna a 1 e `1.json` viene rigenerato dalle entità correnti. La POLICY dell'ADR resta invariata e vincolante dai prossimi giri: ogni cambio di schema richiede una `Migration` esplicita, un bump di versione e un test strumentato sullo schema esportato; mai `fallbackToDestructiveMigration`. L'infrastruttura (export schema, `ALL_MIGRATIONS`, assets per `MigrationTestHelper`) resta in piedi, vuota, pronta per la prossima migration | Le vecchie migration servivano solo a portare avanti i device di test dello sviluppatore (ADR 20/21/22 citano quelle trasformazioni dati): nessun database esiste in produzione, quindi non c'è storia da preservare e la loro presenza era solo debito. Un baseline unico elimina anche una classe di bug reale: lo schema creato da zero divergeva da quello migrato (colonne con `DEFAULT` solo via `ALTER TABLE`), divergenza che aveva già causato il crash all'avvio su installazione pulita. Vincolo operativo: dopo il reset l'app apre solo su database creato da zero; un device con un DB v2-v12 va reinstallato o svuotato (l'unico device di test era già stato azzerato) |
| 24 | Trasferimenti ricorrenti dentro il motore delle ricorrenze esistente (non un modulo separato). La regola ricorrente supporta il tipo `TRANSFER` aggiungendo la gamba di destinazione (`transferAccountId`/`transferAmountMinor`/`transferCurrency`, con la FK verso `accounts`) e riusa `RecurrenceCalculator`, il worker WorkManager, il catch-up, la coda pending e le notifiche. Le colonne sono ripiegate nel baseline v1 (`SaldoDatabase.version` resta 1, schema `1.json` rigenerato), senza migration: un primo tentativo di `Migration(1,2)` con `ALTER TABLE ADD COLUMN` crashava all'avvio perche' quell'istruzione SQLite non puo' aggiungere la foreign key, e lo schema migrato divergeva da quello atteso; collasso una tantum come ADR 23, lecito perche' l'app non e' pubblicata e gira su un solo device (dati di test svuotati). Automatico consentito solo per conti stessa valuta (destinazione = sorgente, esatto); i trasferimenti cross-currency sono forzati in modalità CONFIRM: la regola fissa la sola gamba sorgente e l'importo ricevuto è inserito alla conferma di ogni occorrenza. I trasferimenti restano esclusi dalle statistiche (ADR 8). L'hub mostra "Risparmio pianificato: X/mese" derivato dai soli trasferimenti verso conti `SAVINGS` | Il motore e il modello movimenti sono già type-agnostic (ADR 2/3/4): un modulo separato duplicherebbe engine, worker e coda pending contro ADR 12. Congelare un tasso di cambio e riapplicarlo ogni mese sarebbe disonesto (il cambio deriva): la modalità conferma, che già esiste per gli importi variabili, cattura l'importo reale ricevuto. Il riepilogo risparmio è il seme onesto degli Obiettivi di risparmio (v2.0) senza costruirne il modulo, riusando il tipo conto `SAVINGS` (ADR 22) |

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
- [ ] Widget: saldo totale, spese oggi, aggiunta rapida (Glance)
- [ ] Import CSV con wizard di mappatura colonne
- [ ] Export Excel (.xlsx)
- [ ] Export Google Sheets (nuovo foglio o aggiornamento foglio esistente; richiede verifica OAuth Google per lo scope "sensitive" `spreadsheets` - avviare la review per tempo)
- [ ] Miglioramenti UX dal feedback della v1.0

# Roadmap v2.0

- [ ] Obiettivi di risparmio (target, progressi, suggerimento mensile) - la primitiva di alimentazione esiste già: i trasferimenti ricorrenti verso conti `SAVINGS` (Fase 9.15/ADR 24), da cui deriva "Risparmio pianificato"
- [ ] Conversione valuta automatica (provider tassi, cache offline, indicazione "stimato")
- [ ] Export PDF report con grafici
- [ ] Cifratura backup con passphrase
- [ ] Rimborsi collegati alla spesa originale
- [ ] Commissioni sui trasferimenti
- [ ] Analisi avanzate (anno su anno, pattern di spesa)
- [ ] Valutare: sottocategorie, periodo budget personalizzato

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
  - Rilevamento automatico ricorrenze: euristica on-device che nota spese simili ripetute a cadenza regolare e propone di creare la regola. "Intelligenza senza cloud", coerente col posizionamento privacy. Rimandata di proposito (luglio 2026): da implementare in una fase successiva, agganciandola all'hub Ricorrenze.
  - Recap mensile condivisibile (stile Wrapped): report generato sul device, esportabile come immagine, zero dati che escono.
  - Quick-add ovunque: widget home (già in v1.5), ~~app shortcut statici~~ (implementati in Fase 9.6), Quick Settings tile: spesa registrata in 2 tap senza aprire l'app.
  - Quick entry testuale: parser offline di "12,50 pizza" → importo + categoria suggerita.

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

Trovati a luglio 2026:

- [x] Crash all'avvio su installazione pulita o dopo "cancella dati": lo splash appariva e l'app si chiudeva subito. Causa: `DatabaseSeedCallback` inseriva le categorie predefinite omettendo la colonna `sortOrderIncome`. Sui DB aggiornati in-place la colonna esiste con `DEFAULT 0` (aggiunta via `ALTER TABLE` nella migration 6→7), quindi l'insert reggeva; su uno schema creato da zero Room non genera default, la colonna è `NOT NULL` senza default e l'insert falliva con constraint violation, abortendo `onCreate` a ogni avvio. Non era visibile finché si aggiornava sempre in-place senza reinstallare. Fix: il seed valorizza `sortOrderIncome`; aggiunto test strumentato `DatabaseCreationTest` che esercita il percorso di creazione da zero, prima non coperto (v0.9.26)
