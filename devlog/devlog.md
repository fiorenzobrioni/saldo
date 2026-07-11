# DEVLOG Saldo

Diario di sviluppo del progetto. Le voci più recenti vanno in alto.
Ogni voce annota cosa è stato fatto, decisioni prese, problemi incontrati e cosa viene dopo.

Formato suggerito per ogni voce:

## YYYY-MM-DD - Titolo breve

**Fatto:** cosa è stato completato  
**Decisioni:** scelte tecniche/di design e il perché  
**Problemi:** cosa si è bloccato e come (o se) è stato risolto  
**Prossimo:** il passo successivo  

---

## 2026-07-11 - Rifiniture dashboard, movimenti ricorrenti, filtro mese e schermata Informazioni

**Fatto:**
- **Saluti dashboard**: varianti accorciate (max ~28 caratteri, IT+EN) perché stiano su una riga a font scale normale; `maxLines` da 1 a 2 come rete di sicurezza per il font scaling (prima i messaggi lunghi venivano troncati con ellipsis).
- **Padding card dashboard**: verticale ridotto a 12dp (`SaldoDimens.cardPaddingVertical`) su card saldo, Oggi/mese, pending e ricorrenti; spacer interni delle card periodo ritoccati (6->4, 12->10). Card Ultimi movimenti invariata.
- **Card saldo cliccabile**: rimossi divider e riga "Gestisci account"; l'intera card apre la gestione account, con chevron accanto alla data come affordance e label semantica (`onClick(label = ...)`) per TalkBack. La stringa `dashboard_manage_accounts` resta come label.
- **Card "Movimenti ricorrenti"** al posto della card Abbonamenti: due metriche affiancate Uscite/mese e Entrate/mese (equivalente mensile normalizzato, valuta principale, importi firmati e colorati - non solo colore) + prossimo evento (addebito o accredito) tra entrambi i tipi. `RecurringSummary`/`NextRecurringEvent` nel ViewModel sostituiscono `SubscriptionsSummary`/`NextSubscription` (prima solo EXPENSE).
- **Rinomina**: "Ricorrenze" -> "Movimenti ricorrenti" (EN "Recurring transactions") in titolo schermata, voce impostazioni e card dashboard; tab "Abbonamenti" -> "Uscite" (EN "Expenses"); adeguate le stringhe di editor, empty state, plurali e canale notifiche conferme. Chiavi `subscriptions_*` mantenute (cambiati solo i valori, commento in strings.xml); categoria seed "Abbonamenti" intatta (è una categoria).
- **Movimenti**: filtro data di default "Questo mese" (`TransactionFilters.DEFAULT`), allineato al default delle statistiche; "Tutto" resta selezionabile; `clearFilters` resetta al default; il badge dei filtri ignora sia ALL sia THIS_MONTH (segnala solo ciò che l'utente ha cambiato).
- **Impostazioni**: voce "Account" in testa alla sezione Gestione (prima raggiungibile solo da Dashboard/Movimenti), colonna resa scrollabile, nuova sezione "Informazioni" con la versione nell'hint.
- **Schermata Informazioni** (`feature/about`, `AboutRoute`): logo ricavato dal launcher icon (foreground scalato 108/72 dentro un cerchio col colore di background dell'icona), nome, versione via `BuildConfig.VERSION_NAME` (`buildConfig = true`), tagline, Callback Dev, licenza GPL-3.0 e lista statica delle librerie open source (nessuna nuova dipendenza).
- Versione a 0.7.2 (versionCode 23).

**Decisioni:**
- Card saldo cliccabile intera invece del link testuale: meno cromo visivo, area di tocco ampia, la card è già "la card degli account". Sicura senza stato disabled: è composta solo quando esistono account.
- `TransactionFilters.NONE` resta "nessuna restrizione" e il default della data class resta ALL: i costruttori espliciti (drill-down statistiche, sheet filtri) non ereditano il vincolo del mese; il nuovo default vive solo in `DEFAULT`.
- La barra dei totali filtrati ora compare anche all'apertura (mostra il totale del mese corrente): intenzionale, è informativa e coerente con la vista ristretta.
- I saluti restano una feature: il problema era il troncamento, non l'idea; feature mantenuta con copy più corto.

**Verifica:** `gradle assembleDebug testDebugUnitTest lint` verdi (Gradle di sistema in `/opt/gradle`, wrapper bloccato dal proxy come da nota in PLANNING). Test nuovi/aggiornati: `DashboardViewModelTest` (summary ricorrenti con segno, misto uscite+entrate, esclusione endDate), `TransactionsViewModelTest` (default mese con preset ALL che lo toglie, clear al default, badge a 0 su DEFAULT/NONE). Da verificare su device: resa dei nuovi padding e della card ricorrenti, schermata Informazioni in chiaro/scuro.

**Prossimo:** Fase 8 (backup, export, import).

---

## 2026-07-11 - Rifinitura statistiche dal feedback su device

**Fatto:**
- I grafici a colonne e linea aprono sul mese corrente (`rememberVicoScrollState(initialScroll = Scroll.Absolute.End)`): prima partivano da 12 mesi fa e serviva trascinare.
- Titoli "Entrate e uscite, ultimi 12 mesi" e "Saldo, ultimi 12 mesi": i tre grafici trend ignorano il selettore periodo by design (finestra fissa), ma senza etichetta non si capiva a che periodo si riferissero (il trend spese la aveva già).
- "Spese per account" spostata subito sotto "Spese per categoria": le due card pilotate dal selettore periodo stanno insieme, prima dei grafici a finestra fissa.
- Icona del tab Statistiche da `BarChart` a `Insights` (la stessa famiglia dell'empty state della schermata).
- Versione a 0.7.1 (versionCode 22).

**Verifica:** `assembleDebug testDebugUnitTest lint detekt` verdi; nessun test toccato (modifiche solo UI/risorse). Resa dello scroll iniziale da confermare sul device.

**Prossimo:** Fase 8 (backup, export, import).

---

## 2026-07-11 - Fase 7 completata: ricerca, filtri e statistiche (+ review saluti e fix data)

**Fatto:**
- **Fix data in minuscolo (IT)**: rimosso il titlecase esplicito dalla data della card saldo (`fullWeekdayDate`) e dagli header giorno del registro (`dayLabel`): il CLDR rende già "venerdì 10 luglio" in italiano e "Friday, July 10" in inglese. Il titolo della card mese ("Luglio") resta maiuscolo: è un titolo, non una data in linea.
- **Review saluti dashboard**: neutralizzate le forme flesse al maschile ("Ancora sveglio?" -> "Ancora in piedi?", "Nottambulo? Bentornato." -> "Notte fonda? Un'occhiata veloce e a letto.", "Benvenuto in Saldo" -> "Ti diamo il benvenuto in Saldo") e sostituite due frasi che promettevano cose non vere ("Stai andando alla grande." -> "Anche le piccole spese contano.", "Rilassati, ai conti pensiamo noi." -> "Un minuto ai conti, poi relax."), IT+EN allineate.
- **Ricerca e filtri nel registro** (`feature/transactions/filter/`): motore in-memory puro (`TransactionFilterEngine`) con ricerca accent/case-insensitive via `java.text.Normalizer` su descrizione e nota, filtri combinabili (preset data + range custom, tipi, categorie, account con entrambe le gambe dei trasferimenti, tag, importo min/max su magnitudine). UI: ricerca in-app-bar, chip preset data, chip attivi rimuovibili, sheet filtri completo con badge, barra del totale filtrato sempre visibile (netto + spese/entrate per valuta), empty state dei risultati. Nuova query `TagDao.observeAllCrossRefs` per il filtro tag.
- **Statistiche**: nuove query aggregate in SQL (totali mensili con rimborsi come spesa negativa, spese per account, net mensile del saldo con `UNION ALL` delle due gambe dei trasferimenti sugli account inclusi, somma saldi iniziali) raggruppate sul mese locale del movimento (ADR 7); `ObserveBalanceHistoryUseCase` cumula saldi di fine mese con invariante "ultimo punto == saldo dashboard". Schermata con selettore periodo (mese/anno/custom + chevron), donut per categoria con totale al centro e lista percentuali con barre, trend spese 12 mesi, entrate vs uscite con legenda, andamento saldo, spese per account con barre proporzionali.
- **Grafici con Vico 3.2.3** (`vico-compose-m3` collegato): serie in unità minori (il Double posiziona solo i pixel), label assi/marker riconvertite via `MoneyMapper`/`MoneyFormatter`, tema `ProvideVicoTheme(rememberM3VicoTheme())`.
- **Drill-down**: `FilteredTransactionsRoute` pushata sopra le statistiche (il back torna ai grafici); righe di anello e account navigano al tap, sulle colonne il marker mostra i valori e un bottone "Vedi i movimenti di <mese>". La lista riusa motore filtri, raggruppamento giorni e righe del registro, in sola lettura con tap verso l'editor.
- Dedup: `primaryCurrency`/`fallbackCurrency` estratti in `core/domain/model/PrimaryCurrency.kt` (prima 2 copie + 2 fallback duplicati); raggruppamento giorni estratto in `TransactionGrouping.kt`.
- Versioni per step: 0.6.8 (fix+saluti), 0.6.9 (filtri), 0.6.10 (data layer stats), 0.6.11 (grafici), **0.7.0** (drill-down e chiusura fase, versionCode 21).

**Decisioni:**
- Filtri/ricerca in-memory nel ViewModel invece di FTS o `@RawQuery`: il registro è già caricato per intero, la normalizzazione Unicode batte il case-folding ASCII di `LIKE`, zero migration, tutto testabile in JVM. Il punto paging resta in Fase 9.
- Rimborsi nelle statistiche = spesa negativa (mai entrata), coerente con il SUM firmato di `observeCategoryTotals`; un mese nettato sopra zero viene clampato a zero nei grafici (caso testato).
- Andamento saldo come figura di cassa: include `isExcludedFromStats`, esclude i pending, solo account inclusi nel totale e non archiviati (stessa semantica del totale dashboard: archiviare riscrive la storia retroattivamente, documentato nel KDoc dell'use case).
- Donut con il pie chart di Vico (nuovo nella 3.x, dichiarato sperimentale): niente tap sulle fette via API, quindi drill-down sulle righe della lista e totale al centro come overlay Compose. Fallback Canvas non necessario.
- Drill-down dalle colonne con bottone esplicito invece della navigazione sul tap: lo scrub del marker non deve far lasciare la schermata.

**Problemi:** i docs di Vico (scritti per la 3.0.x) non rispecchiano la 3.2.3: non esiste il package `core.*` (tutto vive sotto `compose.*`) e `columnModel`/`lineModel` convivono con i vecchi `columnSeries`/`lineSeries`. Risolto validando ogni firma con `javap` sui jar pubblicati prima di scrivere i wrapper; lo spike iniziale con gli import dei docs non compilava.

**Verifica:** `assembleDebug testDebugUnitTest lint detekt compileDebugAndroidTestKotlin` verdi a ogni step; 192 unit test JVM (0 falliti), nuovi: `TransactionFilterEngineTest`, filtri in `TransactionsViewModelTest`, `ObserveBalanceHistoryUseCaseTest`, `StatsPeriodTest`, `StatsViewModelTest`, `FilteredTransactionsViewModelTest`. I test strumentati delle query (`TransactionDaoStatsTest`) sono scritti ma vanno eseguiti su device (nessun emulatore in sessione), insieme alla verifica visiva di grafici, ricerca e drill-down.

**Prossimo:** Fase 8 (backup, export, import).

---

## 2026-07-10 - Tastiera di sistema per gli importi, header dashboard, fix sfarfallio

**Fatto:**
- **Fix sfarfallio bianco nelle transizioni (tema scuro)**: con tema scuro in-app e sistema chiaro, durante il fade delle transizioni Nav3 si intravedeva la finestra Android chiara sotto Compose (stessa famiglia del bug status bar). `SaldoTheme` ora avvolge il contenuto in un `Surface` a tutto schermo con `colorScheme.background`: backdrop opaco a tema sempre presente, niente flash, e come effetto collaterale l'area delle system bar viene dipinta col colore del tema.
- **Tastiera di sistema per tutti gli importi (ADR 16)**: rimosso il tastierino custom dall'editor movimenti; ora l'importo si inserisce con `OutlinedTextField` + `KeyboardType.Decimal` come in tutti gli altri editor. Campo prominente (stile testo grande, simbolo valuta come prefisso), auto-focus sul nuovo movimento così la tastiera compare subito; il segno della rettifica passa a un toggle trailing-icon. Eliminati `AmountKeypad`, `AmountInputEditor`/`KeypadKey`, `AmountDisplay`, `rememberDecimalSeparator`. Cap cifre intere e normalizzazione zeri iniziali spostati in `MoneyInput.sanitize`, così ogni campo importo è protetto dall'overflow di `Long` (prima solo il tastierino lo era).
- **Header dashboard**: tolta la scritta "Saldo"; la data del giorno è passata in alto a destra nella card "Saldo totale" (formato esteso "Venerdì 10 luglio", mese minuscolo secondo norma IT, via `getBestDateTimePattern`). L'header ospita un saluto per fascia oraria (notte/mattina/pomeriggio/sera), scelto a caso una volta per apertura: banda e roll [0,1) fissati alla costruzione del `DashboardViewModel` (stabili a ricomposizione/rotazione), il composable indicizza l'array della banda. Primi `<string-array>` dell'app, testi curati e brevi in IT+EN, senza riferimenti a budget/obiettivi (feature non ancora presenti).
- Versione a 0.6.7 (versionCode 16).

**Decisioni:**
- Tastiera di sistema ovunque su scelta esplicita dell'utente (feel nativo, accessibilità out-of-the-box, meno codice). La raccomandazione tecnica era di estendere invece il tastierino custom al resto degli editor, ma l'incoerenza andava chiusa e la scelta dell'utente è legittima; le regole di dominio restano garantite da `MoneyInput`/`MoneyMapper`. Registrata come ADR 16.
- Cap cifre intere in `MoneyInput.sanitize`: rimuovendo `AmountInputEditor` si perdeva la protezione dall'overflow; spostarla nel sanitizer condiviso protegge tutti gli editor in un colpo solo (prima account/abbonamenti/rettifica erano esposti allo stesso rischio latente).
- Saluto tenuto nel ViewModel (banda + roll) e non ripescato a ogni ricomposizione: resta stabile nella sessione e cambia solo a nuova apertura.
- Backdrop a tema a livello di `SaldoTheme` invece che sul singolo container di navigazione: fix globale e canonico (pattern Now-in-Android), copre anche il primo frame.

**Problemi:** detekt inizialmente lamentava complessità/return multipli nel codice del piano precedente (ricorrenze), già risolto; in questa fase nessun blocco. Nessun emulatore in sessione: la verifica visiva (campo importo con IME sopra il bottone salva, auto-focus, saluto e data, assenza di sfarfallio) resta da fare sul device.

**Verifica:** `assembleDebug testDebugUnitTest lint detekt compileDebugAndroidTestKotlin` verdi; 155 unit test (0 falliti). Test aggiornati: `TransactionEditorViewModelTest` ora imposta l'importo via `onAmountChanged`/`onToAmountChanged` (logica di validazione/segno/transfer invariata); casi di cap cifre intere e zeri iniziali migrati in `MoneyInputTest`; nuovo `GreetingBandTest` per i confini delle fasce orarie. Rimosso `AmountInputEditorTest`.

**Prossimo:** Fase 7 (ricerca, filtri, statistiche con Vico).

---

## 2026-07-10 - Fase 6 incremento 3: entrate ricorrenti, radar pre-rinnovo, fix status bar

**Fatto:**
- **Fix status bar in tema scuro forzato**: con tema app scuro e sistema chiaro le icone della status bar restavano scure su sfondo scuro (barra illeggibile). `enableEdgeToEdge()` senza argomenti segue solo il uiMode di sistema: ora viene riapplicata dentro `setContent` con `SystemBarStyle.auto` agganciato al tema risolto in-app (`DisposableEffect(darkTheme)`), per status e navigation bar. La chiamata no-arg in `onCreate` resta per il primo frame in modalità sistema.
- **Entrate ricorrenti + hub "Ricorrenze"**: la vista Abbonamenti diventa un hub con due tab (`PrimaryTabRow`, stesso pattern di Categorie): Abbonamenti (spese, figure invariate) ed Entrate (stipendio, affitti attivi), ognuno con totale mensile equivalente, proiezione annua, conteggio attivi e prossimo addebito/accredito. Il motore supportava già `INCOME`: l'editor non hard-coda più `EXPENSE` e prende il tipo dal tab di provenienza via `RecurringRuleEditorRoute.initialTypeName` (stesso pattern di `CategoryEditorRoute`); categorie filtrate per tipo (INCOME/BOTH vs EXPENSE/BOTH), etichette, icona default ("payments") e categoria default (Stipendio) adattate. Rinominati route e file (`RecurrencesRoute`, `RecurrencesScreen/ViewModel/UiState`); etichette di ordinamento per tab ("Per prossimo accredito", "Per importo"). Card dashboard invariata (resta focalizzata sugli abbonamenti).
- **Radar pre-rinnovo**: notifica opzionale prima dell'addebito ("Netflix si rinnova tra 3 giorni") e degli accrediti ("Stipendio in arrivo domani"). Nuova sezione Notifiche in Impostazioni: toggle (default off) + anticipo 1/2/3/7 giorni (`SingleChoiceSegmentedButtonRow`), persistiti in DataStore. Nuovo use case `CheckUpcomingRenewalsUseCase` eseguito dal worker giornaliero esistente dopo la generazione (un addebito dovuto oggi viene registrato, non annunciato). Watermark `lastReminderEpochDay` su `recurring_rules` (migration 4→5, DB v5): una sola notifica per occorrenza, robusta ai giorni saltati dal worker (device spento: avvisa comunque alla prima occasione utile). Avanzamento watermark con UPDATE mirato per non interferire con l'upsert della generazione. Terzo canale notifiche `recurring_upcoming` (id 1003): notifica singola nominale con importo nel body, oppure summary con i nomi per più rinnovi.
- Versione a 0.6.6 (versionCode 15). PLANNING.md aggiornato: radar spostato da "Note e appunti" alla Fase 6, "Spendibile oggi" e "Rilevamento automatico ricorrenze" annotati come rimandati di proposito, bug status bar spuntato in "Bug conosciuti".

**Decisioni:**
- Entrate ricorrenti in un hub unico a tab invece di una vista separata: meno navigazione, totali per tab coerenti (mai segni misti in uno stesso totale), un solo punto di accesso da Impostazioni ("Ricorrenze") e dalla card dashboard.
- Tipo della regola fissato dal tab di provenienza, nessun selettore nell'editor: un cambio tipo live dovrebbe ricaricare e potenzialmente invalidare la categoria scelta e rietichettare mezzo form; il contesto del tab disambigua già (stesso approccio dell'editor categorie).
- Promemoria pre-rinnovo globale e opt-in (default off): una notifica che compare non richiesta dopo un update è peggio di un tap in più in Impostazioni. Anticipo per-regola rimandato: il caso d'uso reale è un unico anticipo per tutto.
- Watermark promemoria come colonna sulla regola (non DataStore): stesso ciclo di vita di `lastGeneratedDate` (cancellato con la regola, incluso nel backup di Fase 8), migration banale e testata col pattern esistente.

**Problemi:** detekt sulla prima stesura (complessità/numero funzioni nell'editor screen, return multipli nel use case): risolti estraendo le etichette per tipo in `RecurringLabels.kt` e compattando i guard clause. Nessun emulatore in sessione: migration test 4→5 compilato ma da eseguire su device; verifica visiva di tab, notifiche e status bar rimandata al device.

**Verifica:** `assembleDebug testDebugUnitTest lint detekt compileDebugAndroidTestKotlin` verdi; 163 unit test (0 falliti). Nuovi test: `CheckUpcomingRenewalsUseCaseTest` (finestra a 1/2/3/7 giorni, watermark anti-duplicato, catch-up giorni saltati, setting off senza scritture, income, regole terminate, occorrenza generata oggi non annunciata, importo variabile senza importo), editor con tipo INCOME (categorie filtrate, salvataggio, edit che conserva il tipo, fallback a EXPENSE), `RecurrencesViewModelTest` (sezione entrate con totali e prossimo accredito, separazione spese/entrate, test esistenti ritargetizzati).

**Prossimo:** Fase 7 (ricerca, filtri, statistiche con Vico). In coda dalle idee di luglio 2026: rilevamento automatico ricorrenze (da agganciare all'hub) e "Spendibile oggi" (dopo il budget v1.5).

---

## 2026-07-10 - Review completa: bug fix e Fase 6.5 (design system e omogeneità)

**Fatto:**
- **Review completa dell'app** (bug, refactor, UI/UX, omogeneità): esiti tracciati in PLANNING.md ("Bug conosciuti" per i bug, "Note e appunti" per le idee emerse, nuova Fase 6.5 per il lavoro UI).
- **Fix generazione ricorrenze non atomica** (il bug più serio trovato): insert dei movimenti e avanzamento di `lastGeneratedDate` ora avvengono in un'unica transazione Room per regola (`TransactionRunner`); mutex nel use case (ora `@Singleton`) per serializzare worker e catch-up; catch-up spostato dal `lifecycleScope` (cancellabile a rotazione) a uno scope applicativo; backstop a livello DB con colonna `recurringOccurrenceEpochDay` + unique index su (recurringRuleId, occorrenza), migration 3→4 con backfill che non scarta i duplicati pre-esistenti (restano a NULL). Insert della generazione con `OnConflictStrategy.IGNORE`: un'occorrenza già presente viene saltata senza rigenerarla né rinotificarla.
- **Error handling uniforme negli editor** (movimento, conto, categoria, abbonamento): guardia anti doppio-tap `isSaving` con reset, `suspendRunCatching` (rilancia le cancellazioni), evento `WriteFailed` + snackbar; prima un errore di scrittura lasciava l'editor bloccato per sempre.
- **Fix minori**: seed categorie ora sincrono dentro `onCreate` sulla stessa connessione (crash-safe, prima era fire-and-forget su scope detached); `spentMoreThanLastMonth` coerente con la baseline (prima poteva essere true senza confronto); conteggio abbonamenti allineato alla valuta del totale (dashboard e vista Abbonamenti); watermark ri-seedato quando cambia la cadenza di una regola; notifica di conferma con il totale dei pending invece del solo batch; placeholder "0" hardcoded portato in strings.
- **Fase 6.5 - design system**: componenti condivisi `EmptyState`/`LoadingState` (via le 5 copie), `SaldoTypography` con headline/title SemiBold, `tabularNumbers()` sugli importi, `MoneyColors` come unica fonte semantica dei colori denaro, avatar squircle uniformi, haptics (tastierino, swipe-delete, FAB, drag reorder).
- **Fase 6.5 - tema**: palette brand statica di default (seed teal, tertiary verde per le entrate) con dynamic color opt-in e tema chiaro/scuro/sistema in Impostazioni (DataStore + `SettingsViewModel`); ADR 15.
- **Refactor dashboard**: aggregati oggi/mese/confronto calcolati in SQL con una query multi-finestra (`observeDashboardTotals`) e recenti con `LIMIT`; il ViewModel non carica più l'intero registro in memoria.

**Decisioni:**
- ADR 15 (rivede in parte ADR 9): palette brand di default, Material You come scelta esplicita; identità riconoscibile su store e screenshot.
- Colore spese deliberatamente neutro in `MoneyColors`: il segno e l'icona distinguono (anche per daltonici), colorare tutto il registro urlerebbe; entrate in verde (tertiary).
- Finestre della dashboard risolte nella zona del device: un movimento registrato in un'altra timezone può cadere nel giorno dell'istante, non della sua data locale; approssimazione accettabile per i totali a colpo d'occhio (il registro conserva l'offset per movimento).
- Riordino categorie con `sortOrder` globale accoppiato tra i tab: documentato in "Bug conosciuti", non fixato (richiede decisione: accettare o passare a sortOrder per tipo).

**Verifica:** `assembleDebug testDebugUnitTest lint detekt compileDebugAndroidTestKotlin` verdi; 146 unit test (0 falliti). Nuovi test: idempotenza generazione con occorrenze già persistite (watermark stale), serializzazione esecuzioni concorrenti, BIMONTHLY/SEMIANNUAL, catch-up daily/weekly multi-settimana, migration 3→4 con duplicati e backfill (strumentato), mapper occorrenza round-trip, totali dashboard (NULL→zero, negazione to-date), finestre aggregate derivate dal clock, confronto mese senza baseline. Nessun emulatore in sessione: verifica visiva del tema/haptics rimandata al device.

**Prossimo:** Fase 7 (ricerca, filtri, statistiche con Vico), che nasce direttamente sui componenti condivisi della 6.5.

---

## 2026-07-09 - Fase 6 completa (incremento 2): movimenti pending, WorkManager, notifiche

**Fatto:**
- **Movimenti pending** (modalità conferma / importo variabile): nuovo flag `isPending` su `transactions` (migration 2→3, `NOT NULL DEFAULT 0`) con test di migration. I pending sono **esclusi da saldi e statistiche** finché non confermati (filtro `isPending = 0` nelle query di saldo per conto/totale, negli aggregati per categoria e nel ledger; nuovo `observePending`).
- **Generazione estesa** (`GenerateRecurringMovementsUseCase`): crea movimenti pending per le regole con conferma o importo variabile (variabile: importo 0 finché non inserito) e restituisce l'elenco dei movimenti creati per le notifiche. Le automatiche a importo fisso restano invariate.
- **Editor abbonamento**: switch "Importo variabile" (nasconde l'importo e forza la conferma) e selettore segmentato "Registrazione" (Automatica / Con conferma).
- **Schermata "Da confermare"** + card dashboard: elenco dei pending con avatar e data; tap → bottom sheet con campo importo (precompilato per i fissi, vuoto per i variabili) e azioni Conferma / Salta. Conferma applica il segno e toglie il pending; Salta elimina il movimento.
- **WorkManager**: job periodico giornaliero `RecurringGenerationWorker` (@HiltWorker) che rigenera in background e notifica; `SaldoApplication` è `Configuration.Provider` con `HiltWorkerFactory` (initializer di default disabilitato nel manifest). Catch-up all'avvio invariato.
- **Notifiche**: due canali (attività ricorrenti / conferme), notifica informativa per gli automatici e notifica di conferma per i pending; il tap apre l'app (conferma/modifica/salta avvengono nella schermata "Da confermare"). Permesso `POST_NOTIFICATIONS` richiesto a runtime (API 33+), con icona di notifica dedicata.

**Decisioni:**
- **Pending escluso dai saldi**: un addebito in attesa (importo da confermare o variabile) non deve muovere il saldo finché l'utente non conferma; filtro a livello di query SQL, così saldi e statistiche restano corretti.
- **Notifiche tap-to-app** invece di azioni inline (conferma/salta dalla notifica): la conferma di un importo variabile richiede un valore, e le azioni via BroadcastReceiver non sono verificabili senza device; conferma/modifica/salta vivono nella schermata in-app, testata. Le azioni inline restano una rifinitura futura.
- **`androidx.hilt` riportato a 1.3.0** (era 1.4.0 nel catalog, non ancora usato): la 1.4.0 tira lifecycle 2.11 che richiede compileSdk 37 (ADR 14); la 1.3.0 copre hilt-work, hilt-compiler e navigation-compose su compileSdk 36.

**Problemi:** lint `MissingPermission` sul `notify()`: l'analisi è intraprocedurale e non vede la guardia in un metodo helper, quindi `@SuppressLint("MissingPermission")` motivato sul metodo `post` (che chiama comunque `hasNotificationPermission()`).

**Verifica:** `assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin detekt lintDebug` verdi; 134 unit test (0 falliti). Nuovi test: generazione pending (conferma/variabile), skip automatiche senza importo, `PendingMovementsViewModel` (conferma applica il segno e toglie il pending, salta elimina), editor (variabile forza conferma, fisso con conferma). Test di migration 2→3 strumentato. WorkManager/notifiche verificati a livello di build e grafo Hilt; il comportamento runtime (job periodico, notifiche, permesso) resta da provare sul device.

**Prossimo:** Fase 7 (ricerca, filtri, statistiche).

---

## 2026-07-09 - Rifinitura UI 2: date picker, card saldo compatta, elenco account a lista

**Fatto (feedback):**
- **Date picker**: `showModeToggle = false` applicato anche al selettore data dell'editor movimenti (prima solo su quello abbonamenti); elimina l'animazione lenta/scattosa del toggle penna/calendario ovunque venga usato il controllo Data.
- **Card saldo (dashboard)**: resa più compatta in verticale - righe conto meno distanziate (padding verticale 8→4), meno spazio prima/dopo il divisore, "Gestisci account" con meno aria sotto (padding di fondo della card 16→8 e riga 10→6). Eliminato lo spazio vuoto percepito.
- **Elenco account**: da schede separate per conto a un'unica card con righe divise da hairline, come Movimenti e Abbonamenti (attivi e archiviati in due card distinte). `AccountRow` (Surface-card) sostituito da `AccountRowContent` flat dentro `AccountsCard`.

**Verifica:** `assembleDebug testDebugUnitTest detekt lintDebug` verdi; 128 unit test invariati.

**Prossimo:** Fase 6 incremento 2 (WorkManager periodico, notifiche, conferma/importo variabile con movimento pending).

---

## 2026-07-09 - Rifinitura UI: schede compatte, editor abbonamenti premium, titolo movimento contestuale

**Fatto (feedback post-Fase 6):**
- **Spaziatura schede più compatta e omogenea** (`SaldoDimens`, nuovo `theme/SaldoDimens.kt`): padding interno delle card ridotto (hero 20→16, standard 16/20→14), righe dei gruppi 12→10 in verticale, spazio tra le card 12→8. Applicato a Dashboard, Abbonamenti, Movimenti, Account (le Categorie erano già a questi valori). Più dati per videata senza risultare compresso.
- **Abbonamenti**: il "+" in alto a destra è diventato un Extended FAB "Nuovo abbonamento" in basso, come Account/Movimenti/Categorie; padding di fondo adeguato.
- **Editor abbonamento più premium e omogeneo**: ogni campo ha un glifo colorato a sinistra (avatar del conto e della categoria, icona ricorrenza, calendario); importo con simbolo valuta come prefisso e testo più grande; **Frequenza e Primo addebito su una sola riga**; la data di fine è ora uno switch "Con scadenza" che rivela il campo data.
- **Bug data di fine risolto**: impostata la data non si poteva più tornare a "Nessuna scadenza" perché il vecchio tasto X era coperto dall'overlay di tap del campo; lo switch la azzera in modo pulito.
- **Fix animazione date picker**: selettore bloccato in modalità calendario (`showModeToggle = false`), eliminando l'animazione lenta e scattosa del toggle penna/calendario; l'oggetto `SelectableDates` è ora `remember`izzato (niente ricreazione a ogni recomposition).
- **Titolo Nuovo movimento contestuale**: dal FAB generico dei Movimenti (selettore tipo visibile) il titolo è "Nuovo movimento", coerente con "Modifica movimento"; dalle quick action della dashboard, dove il tipo è preimpostato e il selettore è nascosto, resta il titolo specifico ("Nuova spesa/entrata/trasferimento") che conferma l'azione toccata.

**Decisioni:**
- Scala di spaziatura centralizzata in `SaldoDimens` per garantire l'omogeneità richiesta su tutte le schermate a schede.
- Data di fine come switch (default all'attivazione: primo addebito + 1 anno, modificabile) invece di un campo sempre presente: più pulito e azzerabile.
- Date picker in sola modalità calendario: l'input testuale aggiungeva poco ed era la causa del jank riportato.

**Verifica:** `gradle assembleDebug testDebugUnitTest detekt lintDebug` verdi in locale; 128 unit test invariati verdi (modifiche UI/spaziatura, API dei ViewModel stabile). Senza emulatore, verifica visiva rimandata al device.

**Prossimo:** Fase 6 incremento 2 (WorkManager periodico, notifiche, conferma/importo variabile con movimento pending).

---

## 2026-07-09 - Fase 6 (incremento 1): motore ricorrenze e vista Abbonamenti

**Fatto:**
- **Motore ricorrenze** `RecurrenceCalculator` (dominio puro, senza Android): occorrenze per frequenza (giornaliera → annuale), clamp dei mesi corti (giorno 31 → ultimo giorno, ri-derivato dal giorno di riferimento ogni periodo e mai trascinato), anni bisestili, prossima occorrenza, occorrenze in un intervallo chiuso (catch-up), ultima occorrenza prima di una data (per non retro-generare storia), costo mensile equivalente.
- **Use case** `GenerateRecurringMovementsUseCase`: materializza i movimenti dovuti fino a oggi, idempotente (avanza `lastGeneratedDate`), con catch-up all'avvio in `MainActivity`. Per ora solo regole automatiche a importo fisso (conferma/variabile: incremento 2).
- **Vista Abbonamenti** (`feature/recurring`) sul mockup: card "Questo mese" (totale mensile-equivalente + conteggio attivi), card tinta "Di questo passo, in un anno" (totale × 12), lista ordinabile (prossimo addebito / costo / nome) con avatar tinto, badge Oggi/Domani, sottotitolo frequenza · addebito · conto, importo mensile-equivalente con etichetta "equiv. / mese" per i non-mensili, nota a piè di pagina.
- **Editor abbonamento** (CRUD) nello stile degli altri editor: anteprima avatar, nome, importo, conto (deriva la valuta), categoria (preselezione "Abbonamenti"), frequenza, primo addebito, data di fine opzionale, colore/icona, eliminazione con conferma.
- **Card dashboard** Abbonamenti reale (totale mensile, conteggio, prossimo addebito) collegata alla vista; voce anche in Impostazioni.
- **Dati**: aggiunti `color`/`icon` a `RecurringRuleEntity` con migration esplicita 1→2 (colonne nullable) e test di migration strumentato; `getAll`/`getRules` per il motore. Aggiunte alcune icone al set condiviso (directions_car, live_tv, wifi, cloud).

**Decisioni:**
- **Fase spezzata**: incremento 1 (visibile e verificabile via build/unit test) ora; automazione in background (WorkManager periodico, notifiche informative e di conferma, modalità conferma/variabile con movimento "pending") come incremento 2, perché richiede infrastruttura non verificabile senza emulatore e una migration per lo stato pending.
- **"Questo mese" e proiezione annua** usano il costo mensile-equivalente (i costi non mensili ripartiti sul mese; annuo = mensile × 12), coerente col mockup (47,97 → 575,64).
- **Niente retro-generazione**: alla creazione `lastGeneratedDate` è seminato all'ultima occorrenza prima di oggi, così un abbonamento con primo addebito passato non inserisce spese storiche; l'addebito dovuto oggi viene comunque creato.
- Movimenti generati a mezzogiorno per far coincidere la data locale con l'occorrenza ed evitare i bordi DST.
- Abbonamenti = ricorrenze di spesa (come da mockup); il motore è agnostico al tipo, le entrate ricorrenti (es. stipendio) restano per un incremento successivo.

**Problemi:** un import errato di `matchParentSize` (membro di `BoxScope`, non importabile) risolto usandolo dallo scope del `Box`. Rilievi detekt sistemati: `TooManyFunctions`/`ReturnCount`/`ComplexCondition` sull'editor VM (suppress motivato + estrazione di `buildValidRule`), `ReturnCount` sul motore di generazione, `MagicNumber` sugli step dei mesi, `SpreadOperator` nel modulo DB, `TooManyFunctions` di file su `DashboardCards` (helper data inlineato).

**Verifica:** `gradle assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin detekt lintDebug` verdi in locale. Nuovi unit test: `RecurrenceCalculator` (mesi corti, bisestili, next/range/idempotenza, equivalente mensile), `GenerateRecurringMovementsUseCase` (catch-up, resume, idempotenza, segno, skip conferma/variabile, endDate), `SubscriptionsViewModel` (totali, ordinamenti, esclusione entrate/scaduti), `RecurringRuleEditorViewModel` (default, save, validazione, no-backfill, edit), più un assert sulla card dashboard. Test di migration 1→2 strumentato (compila; esecuzione rimandata all'emulatore). Nessun emulatore in sessione: UI test strumentati rimandati.

**Prossimo:** incremento 2 (WorkManager periodico, notifiche, movimento pending con migration, conferma/modifica/salta).

---

## 2026-07-09 - Icona app: nudge verso il basso per centratura visiva

**Fatto:** l'icona launcher (adaptive `ic_launcher_foreground` + monocromatica) passa da `translateY -1.0` a `translateY 2.5` sul gruppo esterno (scala 0.80 invariata). Il disegno scende di 3.5 unità sul canvas 108, così il corpo pieno del portafoglio straddle la linea centrale invece di restare sopra: prima l'insieme risultava alto, con più aria in basso. Foreground e monocromatica tenute in sync.

**Decisioni:** la posizione non è la centratura geometrica del bounding box (che, col corpo pieno in basso e le due carte appuntite in alto, apparirebbe bassa) ma un compromesso visivo: si ferma poco prima, dove il baricentro percepito coincide con il centro. Valore scelto confrontando le rese mascherate (cerchio/squircle) a -1.0, +1.5, +2.5, +3.5: +2.5 è il punto in cui l'icona appare centrata senza sembrare bassa.

**Prossimo:** Fase 6 (ricorrenze e abbonamenti).

---

## 2026-07-09 - Icona app rimpicciolita e ricentrata; icone interne ripristinate

**Fatto:**
- **Icone interne** (avatar di movimenti, conti, dashboard, cella categoria) riportate alle dimensioni precedenti, che andavano bene: la richiesta di margine riguardava l'icona dell'app, non queste.
- **Icona launcher** (adaptive `ic_launcher_foreground` + monocromatica): il gruppo esterno passa da scala 0.88 / translateY +5.4 a scala 0.80 / translateY -1.0. Rimpicciolita per più margine dal bordo mascherabile e alzata leggermente.

**Decisioni:** l'alzata segue il baricentro visivo, non quello geometrico. Il +5.4 spingeva il corpo pieno del portafoglio in basso (centro attorno a y=60 su un canvas 108, centro 54), facendolo sembrare basso rispetto alle due carte appuntite in alto; centrare geometricamente non basta perché sopra è vuoto/arrotondato e sotto è pieno. Con translateY -1.0 il corpo pieno del portafoglio cade sul centro del canvas, quindi appare centrato. Foreground e monocromatica tenute in sync.

**Prossimo:** Fase 6 (ricorrenze).

---

## 2026-07-09 - Editor uniformi, lista Movimenti a card, margini icone

**Fatto:**
- **Bottone Salva uniforme**: spostato in basso a tutta larghezza anche negli editor Conto e Categoria (label "Salva account" / "Salva categoria"), come già fatto per i movimenti. Nuovo componente condiviso `EditorBottomBar` + `EditorSaveButton` (Surface con inset nav bar unito a IME); rimosso il vecchio `SaveButton` in alto a destra. I tre editor ora hanno lo stesso layout.
- **Lista Movimenti** ridisegnata come la sezione "Ultimi movimenti" della dashboard: ogni giorno è un'unica card (`DayCard`) con righe flat separate da una linea divisoria; swipe-to-delete mantenuto (sfondo rosso rivelato sotto la riga, angoli tagliati dalla card). Rimossi gli header sticky a tutta larghezza che creavano il disallineamento coi bordi laterali delle card.
- **Intestazione dei giorni**: da `titleSmall` (più piccola dei dettagli) a `titleMedium`, senza banda bianca di sfondo; il totale del giorno resta a destra, tenue.
- **Margine icone**: ridotto il disegno dentro gli avatar squircle (movimenti, conti, dashboard) per dare più aria al bordo (44dp: 22 -> 20; 40dp: 22 -> 20; 36dp: 20 -> 18; cella categoria: 22 -> 20). Gli avatar delle categorie restano cerchi, come da mockup.

**Decisioni (concordo con le richieste):**
- Salva in basso su tutti gli editor: coerenza tra schermate e CTA primaria più evidente e comoda; esteso anche all'editor Conto, oltre alla Categoria richiesta, per uniformità piena.
- Lista Movimenti a card con divisori: elimina il mismatch header-a-tutta-larghezza contro card-inset e allinea lo stile alla dashboard.
- Header giorni ingrandito: essere più piccolo dei dettagli lo rendeva poco leggibile come intestazione, ora ha il peso giusto.
- Trade-off: persi gli header sticky durante lo scroll, ma per liste lunghe la data resta comunque sopra ogni card.

**Problemi:** nessun emulatore in sessione: niente UI test strumentati, verifica affidata al build (`gradle assembleDebug testDebugUnitTest lint detekt` verde).

**Prossimo:** Fase 6 (ricorrenze).

---

## 2026-07-09 - Redesign inserimento movimenti + transizioni più rapide

**Fatto:**
- **Schermata inserimento movimento** ridisegnata sul mockup:
  - Importo senza bordo, grande e centrato, con cursore lampeggiante; zero placeholder scalato sulla valuta ("0,00").
  - Chip conto e data centrati, con icona e caret a discesa; data compatta tipo "Oggi, 6 lug" (nuovo `chipDayLabel`).
  - Sezione Categoria con link "Tutte": griglia limitata a 8 (la categoria selezionata resta sempre visibile) più un bottom sheet con l'elenco completo (`CategoryPickerSheet`).
  - Descrizione come campo inline senza bordo, con icona.
  - Tastierino piatto a 3 colonne (1-9, separatore, 0, backspace); rimossi il tasto "00" e il tasto Salva dalla colonna azioni. Toggle segno (solo rettifiche) sopra la griglia.
  - Azione primaria "Salva spesa/entrata/..." come bottone a tutta larghezza sotto il tastierino; rimosso il bottone Salva in alto a destra (l'editor tiene solo Chiudi ed Elimina). Titolo e label specifici per tipo.
  - Selettore tipo mostrato solo per il "nuovo generico" (FAB della lista Movimenti) e in modifica; nascosto quando il tipo è scelto a monte dalle quick action del FAB dashboard (nuovo campo `isTypePreset`).
- **Bottom bar dell'editor**: tastierino (visibile quando l'importo è in modifica) più bottone Salva, con inset corretti (nav bar unito a IME) così il bottone sale sopra la tastiera quando si scrive la descrizione.
- **Transizioni tra schermate**: sostituito il default di Navigation 3 (fade da 700ms, percepito lento) con uno slide orizzontale più fade da 300ms (easing FastOutSlowIn), con specifiche dedicate per push, pop e predictive-pop; allineata a 300ms anche la comparsa/scomparsa della bottom bar.

**Decisioni:**
- Salva spostato in basso a tutta larghezza (come nel mockup) e abilitato quando l'importo è valido; gli altri errori (categoria, conto) restano evidenziati al tap.
- "Tutte" con griglia limitata a 8 più sheet completo, invece di elencare inline tutte le categorie (le 16 spese di default sarebbero 4 righe).
- Toggle segno per le rettifiche mantenuto (caso di modifica) ma spostato sopra la griglia, per non rompere la griglia a 3 colonne.

**Problemi:** un paio di rilievi detekt nell'editor (TooManyFunctions, ReturnCount) risolti con l'inline di un helper e meno return; un `padding(horizontal=, bottom=)` inesistente corretto. Nessun emulatore in sessione: niente UI test strumentati, verifica affidata al build.

**Prossimo:** popolare la card Abbonamenti con la Fase 6.

---

## 2026-07-08 - Redesign Dashboard, sistema di forme, fix animazione bottom bar

**Fatto:**
- **Sistema di forme** (`SaldoShapes`, nuovo `theme/Shapes.kt`) con raggi corti al posto dei default Material 3 (che arrotondano le card fino a 28dp): extraLarge 16dp, large 12dp, medium 8dp, small 6dp, extraSmall 4dp. I frame diventano pannelli netti, "quasi ad angolo". Applicato a tutta l'app via `MaterialTheme(shapes = ...)`, quindi lo stile si propaga a tutte le schermate già fatte.
- **`AvatarShape`** (squircle basato su percentuale, 30%) per gli avatar di conti e movimenti, prima cerchi.
- **Dashboard ridisegnata** sul mockup fornito:
  - Header con titolo "Saldo" e data compatta (es. "mar 8 lug").
  - Card saldo (hero) con dettaglio conti sempre visibile (avatar squircle a tinta tenue) e richiamo "Gestisci account"; rimosso il toggle espandi/comprimi.
  - Card Oggi e mese corrente affiancate, stessa altezza: netto in evidenza + righe Spese/Entrate sotto.
  - Riga di confronto separata "A questo punto del mese scorso avevi speso X" (nuovo campo `previousMonthSpendToDate` nel ViewModel; `monthVsPreviousToDate` resta per la direzione dell'icona e per gli unit test).
  - Ultimi movimenti in un'unica card raggruppata con righe flat e divisori: estratto `TransactionRowContent` e riusato da lista movimenti e dashboard.
- **Bottone Salva** in alto a destra (editor conto/categoria/movimento): da `FilledTonalButton` piccolo al componente condiviso `SaveButton` (filled, altezza minima 44dp), più prominente e con touch target comodo.
- **Fix animazione bottom bar**: la navigation bar non è più uno slot dello `Scaffold` ma un overlay in `SaldoApp`. Il `NavDisplay` occupa sempre tutta l'altezza e solo le schermate top-level riservano lo spazio della barra (`BottomBarHeight = 80dp`). Prima l'`AnimatedVisibility` nello slot bottomBar manteneva l'altezza misurata fino a fine animazione, quindi il padding di fondo condiviso dal `NavDisplay` crollava di colpo a transizione conclusa e il contenuto ancorato in basso (il tastierino importo) scattava verso il basso. Ora ogni destinazione è alla dimensione finale dal primo frame e solo la barra scorre.

**Decisioni:**
- Overlay invece dello slot Scaffold: in transizione le due schermate coesistono e servono inset di fondo indipendenti e stabili; un unico padding animato condiviso era la causa dello scatto. Il system inset lo aggiunge lo Scaffold interno di ciascuna schermata, quindi non va sommato a mano.
- Teaser Abbonamenti lasciato come placeholder (Fase 6 non pronta): nessun dato finto, solo restyle coerente col resto.
- Avatar dei conti: tinta tenue sulla dashboard (come nel mockup), colore pieno nella schermata Account (identità forte); entrambi squircle.

**Problemi:** nessun emulatore disponibile in sessione, quindi niente UI test strumentati; verifica affidata al build (`gradle assembleDebug testDebugUnitTest lint detekt` verde).

**Prossimo:** Fase 6 (ricorrenze) per popolare davvero la card Abbonamenti.

---

## 2026-07-08 - Fase 5: Dashboard "Oggi" + rifinitura UI

**Fatto:** implementata la Dashboard "Oggi", la schermata iniziale.
- **Card saldo totale** (hero) con dettaglio account espandibile e richiamo "Gestisci account".
- **Card Oggi** (spese/entrate/netto) e **Card Questo mese** (spese/entrate/saldo) con confronto rispetto allo stesso giorno del mese precedente (icona trend + testo).
- **Teaser Abbonamenti** (placeholder finché la Fase 6 non porta le ricorrenze).
- **Ultimi movimenti** (max 7, riusano la riga della lista movimenti) con tap → modifica, e "Vedi tutti" → tab Movimenti.
- **FAB speed-dial**: il FAB principale si espande in 3 quick action (spesa/entrata/trasferimento) con scrim; ogni azione apre l'editor col tipo preimpostato (nuovo campo `initialTypeName` su `TransactionEditorRoute`).
- **Empty state** prima apertura con CTA "Crea il primo account".
- `DashboardViewModel` reattivo: combina account + movimenti + categorie e deriva tutto (saldo, finestre oggi/mese, confronto, recenti) senza ricalcoli manuali. Unit test su saldo/valuta principale, finestre oggi/mese + confronto, cap a 7 e risoluzione account/categoria, empty state.

**Rifinitura UI richiesta:**
- **Bottone Salva** negli editor (account, categoria, movimento) da `TextButton` a `FilledTonalButton` in alto a destra: più visibile.
- **Top app bar** delle schermate a lista (Account, Categorie, Movimenti) da `LargeTopAppBar` a `TopAppBar` compatta: eliminato lo spazio vuoto in alto, look più coerente e "content-first".
- **Punti d'accesso rivisti**: la gestione account si raggiunge dalla card saldo della Dashboard; rimossa da Impostazioni. Le categorie restano in Impostazioni (configurazione dell'app, nessuna casa contestuale più naturale; Impostazioni si popolerà in Fase 9).

**Decisioni:**
- **Valuta principale** derivata dagli account (quella condivisa dalla maggioranza degli account inclusi nel totale, fallback locale). Somme Oggi/Mese ristrette a quella valuta: multi-valuta con conversione è v2.0 (VISION).
- **Totali di cassa**: Oggi/Mese includono i movimenti "esclusi dalle statistiche" (hanno comunque mosso il saldo); l'esclusione vale solo per le statistiche di Fase 7. Trasferimenti e rettifiche sempre esclusi.
- **Speed-dial fatto a mano** (nessuna libreria aggiunta): Column nel slot FAB + scrim gestito a livello schermata.
- Riuso della riga movimento resa `internal` invece di duplicarla.

**Problemi:** nessuno. `assembleDebug`/`testDebugUnitTest`/`lint`/`detekt` verdi. Verifica visiva su emulatore rimandata (coerente con le fasi precedenti); logica coperta da unit test.

**Prossimo:** Fase 6, ricorrenze e abbonamenti (sbloccano anche la card abbonamenti reale).

---

## 2026-07-08 - Fase 4: gestione categorie

**Fatto:** implementata la Fase 4 (categorie), raggiungibile da Impostazioni → Categorie.
- **Lista a tab** Spese/Entrate (`PrimaryTabRow`): ogni categoria compare nel tab del suo tipo; le categorie `BOTH` ("entrambi") compaiono in entrambi i tab. Tap sulla riga → editor; FAB → nuova categoria col tipo del tab corrente preimpostato.
- **Editor** con anteprima live dell'avatar: nome, tipo (Spesa/Entrata/Entrambi), colore da palette condivisa (18 tinte), icona da un set ampliato di Material Symbols (~35 icone). Validazione del nome.
- **Eliminazione con riassegnazione**: dal pulsante Elimina nell'editor. Se la categoria non etichetta movimenti → conferma semplice. Se ne etichetta ed esistono categorie compatibili → dialog con picker della destinazione (preselezionata la categoria "Altro" predefinita quando presente) che riassegna i movimenti e poi elimina, in un'unica transazione Room. Se non esistono categorie compatibili → conferma che i movimenti resteranno senza categoria (FK `SET_NULL`).
- **Riordino manuale (drag)**: handle di trascinamento per riga con componente reorderable custom (`core/designsystem/component/ReorderableListState.kt`), auto-scroll ai bordi. Il riordino di un tab riscrive solo gli slot di quel tab nell'ordine globale `sortOrder`, che resta l'unica fonte di verità (usata anche dalla griglia categorie dell'inserimento movimento).
- Data layer: `CategoryDao` (maxSortOrder, updateAll, reassign+delete atomico), `TransactionDao.countForCategory`, estensioni ai repository. Stringhe IT+EN. Unit test dei due ViewModel (split tab, riordino incluso il caso BOTH, drop stale ignorato, flussi di eliminazione/riassegnazione). `versionCode` 3, `versionName` 0.4.0.

**Decisioni:**
- **Tab invece di sezioni** per "diviso spese/entrate": più pulito e scalabile; la scelta rende il riordino non ambiguo perché ogni tab è una lista senza header intermedi.
- **`BOTH` mantenuto** (VISION lo prevede) senza complicare il riordino: il drag opera sull'ordine globale proiettato attraverso il filtro del tab; una categoria `BOTH` spostata in un tab si sposta coerentemente anche nell'altro.
- **Eliminazione nell'editor** (non nella lista): la riga resta focalizzata su riordino + navigazione, il flusso di riassegnazione vive dove si modifica la categoria (come il delete nell'editor movimento).
- **Nessuna libreria di reorder aggiunta** (vincolo CLAUDE.md): componente scritto a mano, ~180 righe, testabile a livello di logica nel ViewModel.
- Allineati 4 colori del seed alla palette dell'editor, così il colore di ogni categoria predefinita risulta selezionato nel picker (il test del seed verifica solo il conteggio).

**Problemi:** nessuno. `assembleDebug`/`testDebugUnitTest`/`lint`/`detekt` verdi. Test UI strumentato del drag rimandato a quando ci sarà un emulatore (coerente con le fasi precedenti); verificata la logica via unit test.

**Prossimo:** Fase 5, dashboard "Oggi".

---

## 2026-07-08 - Chore: keystore di debug condiviso

**Fatto:** aggiunto `keystore/debug.keystore` (committato, validità 30 anni, alias `androiddebugkey`) e `signingConfigs.debug` in `app/build.gradle.kts` che lo usa esplicitamente. Prima ogni build (locale o CI) firmava con il keystore di debug di default della macchina che compilava: build diverse avevano firme diverse e Android rifiutava l'aggiornamento in-place dell'APK, costringendo l'utente a disinstallare/reinstallare (perdendo i dati di test) a ogni nuova build scaricata dalla CI. Con la firma condivisa, unita al bump di `versionCode` già in atto (regola CLAUDE.md), l'APK si aggiorna in place mantenendo i dati.

**Decisioni:**
- Password/alias sono i default storici di Android (`android`/`androiddebugkey`): non sono un segreto (chiunque conosce questi valori per il keystore di debug di AGP), quindi nessun bisogno di GitHub Secrets o di escludere il file dal repo.
- `.gitignore` aveva un blanket `*.keystore`: aggiunta un'eccezione esplicita (`!keystore/debug.keystore`) con commento, per non escludere involontariamente il file firmato di release in futuro (quello resta privato, mai committato).
- Verificato che l'APK prodotto sia firmato con il certificato del keystore condiviso (`apksigner verify --print-certs`, fingerprint SHA-256 corrispondente).

**Problemi:** nessuno.

**Prossimo:** Fase 4, categorie.

---

## 2026-07-08 - Fase 3: movimenti (CRUD)

**Fatto:** implementata l'intera feature Movimenti. Editor movimento (`TransactionEditorRoute`, create/edit) con selettore tipo a segmented buttons (Spesa/Entrata/Trasferimento), tastierino numerico custom in-app (concordato con l'utente al posto della tastiera di sistema: attivo subito all'apertura, separatore decimale della locale, tasto `00`, backspace con long press per azzerare, tasto salva prominente), display importo grande e cliccabile, griglia categorie a 4 colonne colorata per categoria, chip account/data (data = oggi, modificabile con il date picker Material), campo descrizione, tag con creazione inline da bottom sheet (riuso case-insensitive dei nomi esistenti), switch "escludi dalle statistiche" e "rimborso" (solo entrate: il rimborso usa le categorie di spesa e netta la categoria nelle statistiche, semantica di Fase 1). Trasferimento nella stessa schermata: due account picker (gamba opposta disabilitata nel sheet), secondo importo mostrato solo se le valute differiscono. Flusso spesa tipica in 3 tap + importo: FAB → categoria → salva, con tipo spesa, account di default e data odierna preimpostati. Account di default = ultimo usato, persistito in DataStore Preferences (`UserPreferencesRepository`, `core/common/prefs`); in Fase 9 arriverà l'impostazione esplicita. Lista movimenti raggruppata per giorno con sticky header (giorno calcolato con l'offset salvato per movimento, ADR 7), etichette Oggi/Ieri/data localizzata, totale giornaliero netto per valuta (solo spese+entrate: trasferimenti e rettifiche esclusi), riga con avatar categoria (o icona transfer/rettifica), descrizione, account (per i trasferimenti "da → a"), importo con segno e colore per tipo, icona "esclusa dalle statistiche". Swipe end-to-start per eliminare con Snackbar + Annulla (l'undo reinserisce il movimento e riattacca i tag, catturati prima della cancellazione); eliminazione anche dall'editor con dialog di conferma. Empty state doppio: senza account la CTA porta alla creazione account, con account alla registrazione del primo movimento. Modifica: tap sulla riga; il tipo resta bloccato per trasferimenti e rettifiche (spesa/entrata intercambiabili), campi non toccati dal form (nota, regola ricorrente) preservati. Stringhe IT/EN complete.

**Verifica:** `assembleDebug testDebugUnitTest lint detekt` verdi in locale (Gradle 8.14.3 preinstallato, JDK 21), più compilazione dei test strumentati. 77 unit test JVM verdi (30 nuovi): `AmountInputEditor` (zeri iniziali, separatore per valute a 0 decimali, cap sulle cifre intere perché i centesimi stiano in un Long, toggle segno solo per le rettifiche), `TransactionEditorViewModel` (default nuova spesa con ultimo account usato e fallback al primo attivo, segni per tipo: spesa negativa/entrata positiva/trasferimento gamba doppia, trasferimento cross-valuta con secondo importo obbligatorio, categoria obbligatoria, rimborso con categorie di spesa, riscalatura importo al cambio account EUR→JPY, load in modifica con lock del tipo, salvataggio che preserva id/nota/regola, delete, riuso tag), `TransactionsViewModel` (raggruppamento per giorno con offset per movimento, totali giornalieri per valuta con esclusione transfer/rettifiche, undo con ripristino tag, `hasAccounts` solo su account attivi). Obiettivo ≤3 tap verificato by design e sui default a livello ViewModel; il Compose UI test end-to-end resta rimandato a quando ci sarà un emulatore (serve anche l'infra Hilt per i test strumentati, non ancora introdotta).

**Decisioni:**
- Tastierino custom in-app e trasferimento nella stessa schermata con selettore tipo: opzioni sottoposte all'utente e confermate (chip AskUserQuestion).
- Collegata la dipendenza `androidx.datastore:datastore-preferences` (già nel Version Catalog dalla Fase 0, nessuna libreria nuova).
- `AccountVisuals` spostato da `feature/accounts` a `core/designsystem/visuals` (ora serve anche al picker account dell'editor movimenti); creato `CategoryVisuals` accanto, con le 20 icone del seed: servirà anche a Fase 4 (editor categorie) e Fase 5 (dashboard).
- Undo dell'eliminazione: il movimento viene reinserito con un nuovo id (Room `@Update` su id inesistente sarebbe un no-op silenzioso); l'id non è esposto all'utente e nessun dato va perso, i tag vengono riattaccati al nuovo id.
- Importo sempre digitato positivo (il tipo decide il segno alla persistenza); il toggle segno compare solo modificando una rettifica, l'unico tipo con delta firmato.
- Modifica del timestamp: la data scelta mantiene l'ora originale del movimento (per i nuovi, l'ora corrente) e l'offset viene ricalcolato sulla zona corrente.
- Totali giornalieri = netto spese+entrate per valuta, senza filtrare il flag "escludi dalle statistiche": la lista è un registro, non una statistica (il flag agisce solo sulle query statistiche, ADR 8).

**Problemi:** detekt: `TooManyFunctions` risolto estraendo le righe della lista in `TransactionListRow.kt` e con suppress motivato sul ViewModel dell'editor (un callback per campo è la forma naturale di un form); `ReturnCount`/`CyclomaticComplexMethod` su `save`/`buildTransaction` risolti ristrutturando le guard e estraendo `signedAmount`. lint: `LocalDate.EPOCH` richiede API 34 (sostituito con `ofEpochDay(0)`); regola `NonObservableLocale` sulla lettura di `Locale.getDefault()` nei composable: locale letta da `LocalConfiguration.current`.

**Prossimo:** Fase 4, categorie: lista divisa spese/entrate, editor (nome, colore, icona, tipo), eliminazione con riassegnazione movimenti, riordino manuale.

---

## 2026-07-08 - Fase 2: account

**Fatto:** implementata l'intera feature Account. Lista account (`feature/accounts`) con saldo corrente calcolato via Flow, sezione archiviati collassabile, empty state con CTA e FAB "Nuovo account"; accesso dalla voce "Account" in Impostazioni (la card della dashboard arriverà in Fase 5). Tap su un account: bottom sheet con azioni rapide (modifica, rettifica saldo, archivia/ripristina, elimina). Editor account (creazione/modifica) con nome, tipo (chip), valuta (dropdown con ~35 valute, prima quella della locale), saldo iniziale (input con toggle segno, sanitizzato sui fraction digits della valuta), palette di 14 colori, griglia di 16 icone Material Symbols (default guidata dal tipo finché l'utente non ne sceglie una), switch "includi nel saldo totale". Rettifica saldo: dialog con saldo attuale, input del saldo reale e anteprima della differenza; `AdjustBalanceUseCase` (core/domain) crea il movimento ADJUSTMENT con il delta (no-op se il saldo coincide). Eliminazione: consentita solo senza movimenti (conferma), altrimenti dialog che propone l'archiviazione; archiviazione immediata con Snackbar + Annulla (undo al posto dei dialog di conferma, come da VISION). Archiviati esclusi dal totale (già a livello query, Fase 1). Stringhe IT/EN complete, plurals inclusi.

**Verifica:** `assembleDebug testDebugUnitTest lint detekt` verdi in locale (Gradle 8.14.3, JDK 21). 47 unit test JVM verdi (25 nuovi): `AdjustBalanceUseCase` (delta positivo/negativo, no-op, arrotondamento HALF_UP, valute a 0 decimali, account mancante), `MoneyInput`/`MoneyFormatter` (sanitizzazione input, parsing con virgola/punto, formato localizzato e segno esplicito), `AccountsViewModel` (split attivi/archiviati, archiviazione con evento undo, guardia di eliminazione, flusso rettifica), `AccountEditorViewModel` (salvataggio, validazione, load in modifica con lock valuta, icona di default per tipo, cambio valuta con riscalatura). Test strumentati aggiornati/aggiunti (compilano; esecuzione rimandata a quando ci sarà un emulatore): `BalanceAdjustmentTest` (rettifica end-to-end su Room in-memory, idempotenza alla ripetizione), navigazione Impostazioni → Account. Lint: 0 errori; warning solo sui pin di versione deliberati (ADR 14).

**Decisioni:**
- Aggiunta dipendenza `androidx.hilt:hilt-navigation-compose` (concordato): pattern ufficiale Nav3 per `hiltViewModel()`, con assisted injection della route (`AccountEditorRoute` passata al ViewModel via `@AssistedFactory`, come nelle recipes Google). Versione fissata a 1.3.0: la 1.4.0 trascina lifecycle 2.11 che richiede compileSdk 37/AGP 9.1 (nota SDK 37 in PLANNING.md); nota nel Version Catalog.
- `NavDisplay` ora con `entryDecorators` espliciti (`rememberSaveableStateHolderNavEntryDecorator` + `rememberViewModelStoreNavEntryDecorator`): i ViewModel sono scopati alla singola entry dello stack. API verificate sugli artefatti 1.1.4/2.10.0, non a memoria.
- Scaffold esterno con `contentWindowInsets` a zero e bottom bar animata, visibile solo sulle destinazioni top-level: le schermate interne (Account, editor) gestiscono i propri insets con il proprio `Scaffold`/top bar.
- `Clock` iniettato via Hilt (`ClockModule`): rettifiche deterministiche nei test (timestamp e offset dal clock, non da `Instant.now()`).
- Valuta non modificabile se l'account ha movimenti (i movimenti conservano la valuta dell'account: cambiarla mischierebbe valute nel saldo); cambio valuta in creazione riscala l'importo digitato sui nuovi fraction digits invece di strippare il separatore (evita un 12,34 → 1234 passando a JPY).
- Icone account come chiavi stringa (nomi Material Symbols) risolte da `AccountVisuals`: la palette può crescere senza migration.
- `countForAccount` esposto sul `TransactionRepository` per la guardia di eliminazione (la FK `NO_ACTION` della Fase 1 resta l'ultima difesa a livello DB).

**Problemi:** lint (regola nuova `LocalContextGetResourceValueCall`) rifiuta `LocalContext` per leggere stringhe negli effect: usato `LocalResources.current`. detekt: `TooManyFunctions` sull'editor risolto estraendo i picker colore/icona in `AccountEditorPickers.kt`.

**Prossimo:** Fase 3, movimenti: inserimento spesa/entrata in ≤3 tap, trasferimenti, lista raggruppata per giorno, modifica ed eliminazione con undo, tag.

---

## 2026-07-07 - Fase 1: data layer (fondamenta)

**Fatto:** implementato l'intero data layer. Entity Room (`accounts`, `categories`, `transactions`, `tags`, `transaction_tag_cross_ref`, `recurring_rules`) con foreign key e indici; enum di dominio (`TransactionType`, `AccountType`, `CategoryType`, `RecurrenceFrequency`, `RecurrenceMode`) persistiti via `Converters` come `name`. Modelli di dominio + mapper entity↔dominio; `MoneyMapper` per la conversione centesimi↔`BigDecimal` basata sui fraction digits della valuta (HALF_UP, mai float/double). DAO con le query fondamentali: saldo per account e saldo totale (solo account inclusi e non archiviati) come Flow, movimenti per intervallo/account, aggregati per categoria. Repository (interfacce nel dominio, implementazioni Room) esposti via Hilt (`DatabaseModule` + `RepositoryModule`). Seed delle 21 categorie predefinite (16 spese + 5 entrate) alla prima creazione del DB, localizzate da `strings.xml` (IT/EN) in base alla locale. Schema Room esportato in `app/schemas` (versione 1) per i futuri test di migration. Rifinita l'icona launcher (vedi sotto).

**Verifica:** `assembleDebug testDebugUnitTest lint detekt` verdi in locale (Gradle 8.14.3, JDK 21). 18 unit test JVM verdi (JUnit5): `MoneyMapper` (arrotondamenti, valute a 0 decimali come JPY, pseudo-valuta XXX, round-trip), mapper transazioni/account (segno, transfer multi-valuta, scale), conteggio categorie di default. Test Room strumentati (JUnit4, Room in-memory) su saldo per account (tutti i tipi di movimento), saldo totale (esclusione archiviati e non inclusi), aggregati per categoria (netting rimborsi, esclusione TRANSFER/ADJUSTMENT e flag "escludi dalle statistiche") e query per intervallo: compilano; esecuzione su device rimandata a quando un emulatore sarà disponibile (coerente con la Fase 0). Lint: 0 errori, solo i warning sui pin di versione deliberati (ADR 14).

**Decisioni:**
- Importo come valore con segno = effetto sull'account (`accountId`): spesa negativa, entrata positiva, rettifica con segno, trasferimento negativo sulla sorgente. Il saldo diventa una semplice `SUM(amountMinor)` in SQL e i rimborsi (entrata con flag e categoria della spesa) nettano la spesa di categoria senza logica extra.
- Trasferimento come singolo record (ADR 2): `accountId` sorgente + `transferAccountId` destinazione, con `transferAmountMinor`/`transferCurrency` per la destinazione (supporta le valute diverse: l'utente inserisce entrambe le gambe). Assunzione: `currency` del movimento = valuta dell'account, così i saldi per account non mischiano valute.
- Instant e LocalDate salvati come `Long` (epoch millis / epoch day) direttamente sulle colonne invece che via TypeConverter: nessuna denormalizzazione del saldo (ADR 3 rispettato), grouping per giorno fatto dal chiamante usando l'offset salvato per movimento (ADR 7).
- Saldo mai denormalizzato (ADR 3): tre query di saldo (per account con `@Embedded` + colonna calcolata, singolo account, totale per valuta) sommano `initialBalance` + gamba sorgente + gamba destinazione dei trasferimenti.
- Seed via `RoomDatabase.Callback.onCreate` con `Provider<SaldoDatabase>` (rompe il ciclo callback→DB) e nomi da `strings.xml`: categorie come dato editabile, localizzate ma non hardcoded.
- FK: `NO_ACTION` sugli account (un account con movimenti non è cancellabile a livello DB, coerente con la regola della Fase 2), `SET_NULL` su categoria/regola ricorrente, `CASCADE` sui cross-ref dei tag.
- detekt `LongParameterList`: `ignoreDefaultParameters = true`, così le colonne opzionali (espresse come parametri con default) non contano; i costruttori delle entity/modelli restano ergonomici senza indebolire la regola per le funzioni normali.
- `RecurringRuleEntity` e relativi modello/mapper/repository creati come fondamenta: il motore di generazione resta alla Fase 6.

**Problemi:** nessun blocco. Room in-memory richiede runtime Android: i test dei saldi sono strumentati e non girano in CI finché non c'è un emulatore, quindi la copertura JVM eseguibile in CI è su `MoneyMapper` e sui mapper (parte "centesimi↔BigDecimal" del calcolo saldi), mentre la somma SQL è coperta dai test strumentati.

**Icona:** aumentato il margine dal bordo (l'artwork arrivava a filo): tutto il disegno è ora dentro un group che lo scala a 0.88 e lo ricentra nel canvas 108dp (margine ~28dp per lato invece di ~20 in alto). Ispessita la linea di cucitura del wallet (pill da 3 a 5 unità di altezza). Aggiornati sia `ic_launcher_foreground` sia `ic_launcher_monochrome` per restare coerenti.

**Prossimo:** Fase 2, account: lista con saldo corrente, creazione/modifica, archiviazione, rettifica saldo (movimento ADJUSTMENT con la differenza), regole di eliminazione.

---

## 2026-07-07 - Fase 0: setup progetto completato

**Fatto:** creato il progetto Android da zero: modulo `:app` (applicationId `com.callbackdev.saldo`, minSdk 33, target/compileSdk 36), Version Catalog con tutte le dipendenze del MVP, Hilt + KSP funzionanti, tema Material 3 con dynamic color (light/dark, nessun fallback), Navigation 3 stabile 1.1.4 (route `NavKey` serializzabili, `rememberNavBackStack`, `entryProvider` + `NavDisplay`) con scaffold e bottom bar a 4 tab (Dashboard, Movimenti, Statistiche, Impostazioni) su schermate placeholder. Struttura package-by-feature: `core/{common,database,designsystem,domain}` + `feature/{dashboard,transactions,stats,settings}`. Test: JUnit5 via plugin android-junit5 per gli unit test JVM (4 test sulla logica del back stack dei tab, verdi), JUnit4 + Compose UI Test per gli strumentati (2 test di navigazione, compilano; esecuzione su device rimandata a quando disponibile). detekt + `.editorconfig`, workflow CI GitHub Actions (build + lint + unit test + detekt su ogni push). Stringhe IT/EN complete fin d'ora, icona launcher adattiva placeholder.

**Verifica:** `assembleDebug testDebugUnitTest lint detekt` verdi in locale (Gradle 8.14.3, JDK 21); lint: 0 errori, 14 warning (tutti avvisi "newer version available" sui pin deliberati). Nessun device/emulatore disponibile nell'ambiente: gli UI test strumentati sono solo compilati.

**Decisioni:**
- AGP 8.13.2 + Hilt 2.58: Hilt 2.59+ impone AGP 9 (e Gradle 9.1+); il salto ad AGP 9 (built-in Kotlin, nuova DSL) è rimandato a una chore dedicata, annotata in PLANNING.md.
- compileSdk/targetSdk 36: le versioni androidx di giugno 2026 (core 1.19, lifecycle 2.11, BOM 2026.06) richiedono compileSdk 37; fissate le ultime versioni compatibili con SDK 36 (core 1.18.0, lifecycle 2.10.0, BOM 2026.02.01, activity 1.12.4).
- Backup di sistema disabilitato (`allowBackup=false` + `dataExtractionRules` che escludono tutto): i dati finanziari non lasciano il device tramite backup automatici di sistema, coerente con privacy-first; il backup esplicito arriva in Fase 8.
- Bottom bar: la Dashboard è sempre la root del back stack; il cambio tab sostituisce la cima dello stack (logica estratta in `switchTopLevelTab`, coperta da unit test).
- JUnit 6.1.1 (junit-jupiter): richiesto l'allineamento esplicito di `junit-platform-launcher` alla stessa versione, altrimenti il launcher iniettato dal plugin non scopre i test.

**Problemi:** il proxy dell'ambiente blocca il download della distribuzione Gradle dal redirect GitHub: build locali eseguite con il Gradle 8.14.3 preinstallato, il wrapper resta standard per CI e sviluppo locale. La validazione dell'URL del task `wrapper` fallisce per lo stesso motivo (generato con `--no-validate-url`).

**Follow-up (stesso giorno):** CI verificata verde su GitHub (run 1: build, unit test, lint, detekt tutti ok). Aggiunto alla CI l'upload dell'APK debug come artifact (retention 14 giorni). Verificato su developer.android.com che Android 17 (API 37) è ancora in Beta: targetSdk/compileSdk 36 confermati come "ultimo stabile"; la migrazione a SDK 37 + AGP 9 resta una chore da fare alla release stabile di Android 17 (nota in PLANNING.md). Nuovo ADR 14: targetSdk/compileSdk fissati esplicitamente nei documenti (36) al posto della dicitura "target ultimo stabile"; l'aggiornamento diventa una chore deliberata (CLAUDE.md, VISION.md e PLANNING.md allineati).

**Prossimo:** Fase 1, data layer: entity Room, DAO con query dei saldi, modelli di dominio e mapper centesimi/BigDecimal, repository, seed categorie, unit test su mapper e calcolo saldi.

---
