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

## 2026-07-19 - Hero saldo con sparkline e recap mensile "Saldo Wrapped" (Fase 10.1)

**Fatto:** due feature premium dalla review completa dell'app (versionCode 84 -> 85, versionName 0.9.45 -> 0.9.46). Design: ADR 27 e 28. Nessun cambio di schema.
- Hero card Dashboard: count-up presentazionale del saldo (interpolazione sui minor units, frame finale esatto), sparkline 30 giorni disegnata in Canvas (interpolazione monotona Fritsch-Carlson, riempimento sfumato, reveal d'ingresso, punto sull'oggi), gradiente tonale nella card, caption "Ultimi 30 giorni" con delta a segno esplicito. Dati da due query nuove (`observeDailyNetChanges`, `observeNetChangeBefore`, stesse regole del saldo totale con entrambe le gambe dei trasferimenti) cumulate in `ObserveDailyBalanceHistoryUseCase` (zero-fill; invariante testata: ultimo punto = saldo in card). Skeleton dashboard ricalibrato. Canvas muto per TalkBack con riassunto del trend formattato.
- Nuovo helper `rememberMotionEnabled()` nel design system (`ValueAnimator.areAnimatorsEnabled()`): count-up, reveal sparkline e reveal delle pagine recap si disattivano con le animazioni di sistema spente. Prima non c'era alcuna gestione reduced-motion.
- Recap mensile (`feature/recap`, route `MonthlyRecapRoute`): schermata full-screen a tema scuro brand forzato con `HorizontalPager` a pagine-storia (hero col netto, spese + delta vs mese precedente, top 5 categorie con barre, record, entrate vs uscite con savings rate, ricorrenti addebitati, chiusura con riga privacy), pillole di progresso, tap zone + swipe, empty state per mese vuoto. Dati da `GetMonthlyRecapUseCase` su cinque query one-shot nuove con la semantica esatta delle statistiche (rimborsi nettati, esclusi/pending mai contati): le cifre coincidono con la schermata Statistiche.
- Condivisione: card riassuntiva 360x640dp composta off-screen a densita 3x e registrata con `GraphicsLayer` -> `toImageBitmap()` -> PNG in `cache/exports` via FileProvider esistente -> share sheet. Zero permessi, zero rete.
- Punti d'accesso: teaser dismissibile in Dashboard nei primi 7 giorni del mese (dismiss persistito per mese in DataStore, auto-espirante) e azione `AutoAwesome` nella toolbar Statistiche (mese visualizzato se concluso, altrimenti l'ultimo concluso).
- PLANNING: Fase 10.1, ADR 27/28, Note e appunti aggiornate (Wrapped implementato, rilevamento ricorrenze promosso), 4 voci nuove in Roadmap v2.0 (rilevamento automatico ricorrenze, proiezione saldo a fine mese, gestione tag dedicata, ricerca con suggerimenti). README aggiornato.

**Decisioni:** sparkline in Canvas e non Vico (ADR 27: componente decorativo, Vico resta nelle statistiche); recap solo per mesi conclusi (i numeri del mese corrente derivano ogni giorno); un'unica immagine riassuntiva condivisibile invece di un'immagine per pagina (un solo code path, artefatto piu curato); il teaser non entra nelle preferenze card della Dashboard perche si auto-rimuove.

**Problemi:** wrapper Gradle bloccato dal proxy, usato `/opt/gradle`. La registrazione off-screen della share card (dietro lo sfondo opaco, niente alpha 0 che salterebbe il draw) e da verificare su device: fallback previsto con `android.graphics.Picture`. Test strumentati `TransactionDaoRecapTest` scritti ma non eseguiti (nessun emulatore).

**Prossimo:** verifica su device: sparkline e count-up (anche con animazioni di sistema spente), skeleton senza salto, teaser a inizio mese, recap da Statistiche, condivisione immagine (qualita PNG e temi), TalkBack su sparkline e pagine.

---

## 2026-07-18 - Rifiniture Dashboard, uniformita liste vuote e copy onboarding

**Fatto:** giro di polish UX (versionCode 83 -> 84, versionName 0.9.44 -> 0.9.45).
- Card "Movimenti ricorrenti" in Dashboard: testo di empty-state accorciato e allineato alle card Budget/Obiettivi. IT "Nessun movimento · tocca per aggiungerne uno", EN "No transactions · tap to add one" (prima era piu lungo e andava a filo della card su telefono). Il separatore era gia il punto medio `·` come le altre card, nessun bullet da correggere.
- FAB nelle liste vuote uniformato: `BudgetsScreen` e `SavingsGoalsScreen` mostravano il FAB anche a lista vuota, insieme al bottone centrale. Aggiunto `&& !uiState.isEmpty` al guard del `floatingActionButton`, come gia fanno Conti/Movimenti/Ricorrenti. A vuoto resta solo la CTA centrale; il FAB ricompare al primo elemento.
- Data sui movimenti recenti in Dashboard: la card "Ultimi movimenti" non riportava la data. Aggiunto un parametro opzionale `dateLabel` a `TransactionRowContent` (quando presente, l'importo diventa una colonna con la data sotto in `labelSmall`); la lista Movimenti resta invariata (data negli header di giorno, `dateLabel = null`). Nuovo formatter `compactDayLabel` in `TransactionFormatters.kt`: Oggi/Ieri oppure data breve "6 lug" (skeleton dMMM/dMMMy, `withLocaleDateCasing`). `today` preso da `uiState.date` (LocalDate gia esposto dal ViewModel).
- Copy onboarding: la Welcome citava solo "spese, entrate e abbonamenti"; ampliata a "spese, entrate, budget e obiettivi di risparmio" (IT+EN) per riflettere le feature spedite, senza aggiungere pagine e senza promettere funzioni non presenti. Ridotto l'uso dell'em dash nelle descrizioni italiane (welcome e notifiche: em dash -> virgola).

**Decisioni:** valutato ma scartato (scelta utente) il default del nome conto dal tipo quando lasciato vuoto: il nome resta obbligatorio. La data sui movimenti recenti sta solo in Dashboard tramite parametro opzionale della row condivisa, per non duplicarla nella lista Movimenti dove e gia negli header di giorno. Onboarding: solo ritocco copy delle 5 pagine esistenti, niente pagina "funzionalita" aggiuntiva.

**Problemi:** wrapper Gradle bloccato dal proxy (403 sul download della distribuzione da GitHub); usato il Gradle di sistema `/opt/gradle` (8.14.3, stessa versione). `assembleDebug testDebugUnitTest lint` verde. Verifica manuale su device ancora da fare (empty-state ricorrenti, FAB Budget/Obiettivi a vuoto e al primo inserimento, data sui movimenti recenti, testi onboarding IT/EN).

**Prossimo:** verifica su device dei quattro interventi.

---

## 2026-07-18 - Review e snellimento degli ADR in PLANNING.md

**Fatto:** revisione della tabella "Decisioni architetturali chiave" di PLANNING.md. Gli ADR 16, 18-26 avevano accumulato dettagli implementativi (nomi di classe/metodo/colonna, numeri di migration e di schema, narrazione dei collassi baseline, riferimenti di commit, note operative ripetute "app su un solo device") che appartengono a fasi/devlog, non all'ADR. Trimmati mantenendo intatta la logica di validità: decisione + motivazione. Gli ADR 1-15 e 17 erano già sintetici e restano invariati; numeri ADR e riferimenti incrociati non toccati.

**Decisioni:** solo prosa, nessuna modifica a codice, schema o dominio (nessun bump di versione). Rimossi in particolare: dettagli tipografici delle soglie (emoji), nomi di campo watermark, elenco degli oggetti di infrastruttura migration, la nota "Chiarimento Fase 10.0" dell'ADR 22, la narrazione del collasso baseline duplicata in 24/25 (resta in 23/26 dove è la decisione). Nessun ADR rimosso: valutata la sovrapposizione della tripletta migration 23/24/26, ma ognuno porta una decisione distinta ed è referenziato per numero, quindi consolidata solo la narrazione ripetuta invece di eliminare righe.

**Problemi:** nessuno. L'ordine delle righe in tabella resta 23, 26, 25, 24 (non numerico) come nell'originale: riordinare esulava dal task "togliere il superfluo" e i riferimenti sono per numero.

**Prossimo:** applicare lo stesso stile sintetico ai prossimi ADR fin dalla stesura.

---

## 2026-07-18 - Fix crash aggiornamento (collapse v1) + disciplina migration + card Obiettivi sempre visibile

**Fatto:** dalla prova su device, due interventi (versionCode 82 -> 83, versionName 0.9.43 -> 0.9.44).
- Schema ripiegato nel baseline v1: `savings_goals` torna nel `1.json` (rigenerato), rimossi `MIGRATION_1_2`, `2.json` e `Migration1To2Test`; `SALDO_DATABASE_VERSION` di nuovo 1, referenziato dall'annotazione `@Database`. Ultima collapse-una-tantum (ADR 26).
- Disciplina migration: due test generici (non uno per migrazione). `MigrationsTest` strumentato crea il baseline e applica `ALL_MIGRATIONS` fino a `SALDO_DATABASE_VERSION`, validando contro gli schemi esportati (intercetta lo schema migrato divergente). `MigrationChainTest` JVM verifica che la catena sia contigua e raggiunga la versione corrente: gira in CI senza device, intercetta il bump di versione senza migrazione.
- Dashboard: la card Obiettivi ora è sempre visibile quando abilitata (rimosso il guard `isNotEmpty`), con messaggio di empty-state come la card Budget; funge da punto d'accesso anche senza obiettivi.

**Decisioni:** ADR 26. Ogni collasso del baseline è un downgrade di versione per un device già sulla versione più alta, che Room rifiuta: è la causa dei crash "all'aggiornamento" ripetuti (serve reinstallare/svuotare). I migration test erano solo strumentati e in CI non c'è emulatore, quindi la divergenza sfuggiva alla verifica basata su build (già capitato in ADR 23/24). Il guard JVM chiude la parte strutturale in CI; lo strumentato resta da eseguire su device prima di rilasciare uno schema. Dai prossimi giri si preferisce una migrazione forward reale (niente downgrade). Robolectric per far girare anche lo strumentato in JVM/CI è una possibile aggiunta futura, ma è una dipendenza nuova: decisione separata (non presa qui, rispetto della regola "niente librerie senza chiedere").

**Problemi:** non ho la logcat del crash sul device, quindi non ho isolato la causa esatta della `Migration(1,2)` (lo schema esportato combaciava con la CREATE scritta a mano): il collasso lo aggira azzerando la migrazione, coerente con la scelta dell'utente. Conseguenza da comunicare: il device attualmente su schema v2 va reinstallato/svuotato una volta (il passaggio a codice v1 è un downgrade).

**Prossimo:** verifica su device dell'aggiornamento in-place dopo il collasso (partendo da un DB creato da zero con la 0.9.44), della card Obiettivi vuota in Dashboard, e - alla prossima migrazione reale - dei due test generici. Valutare Robolectric con l'utente.

---

## 2026-07-18 - Rifinitura: preselezione SAVINGS nella scorciatoia "crea conto"

**Fatto:** la scorciatoia "crea conto" dell'editor Obiettivi ora apre l'editor conto già sul tipo SAVINGS (versionCode 81 -> 82, versionName 0.9.42 -> 0.9.43). `AccountEditorRoute` guadagna `initialTypeName` (mirror di category/recurring editor); `AccountEditorViewModel` risolve `initialType` dal route e semina lo stato iniziale con tipo, icona di default e `isIncludedInBudget = initialType != SAVINGS` (preset ADR 22 allo stato iniziale). `SaldoApp` passa `AccountType.SAVINGS.name` dalla scorciatoia. Baseline catturato sullo stato seminato: l'editor non si apre "sporco". Nuovi unit test (tipo/budget/icona seminati, apertura pulita, salvataggio con budget off, fallback a CHECKING per tipo ignoto).

**Decisioni:** la decisione dell'ADR 22 non cambia (SAVINGS default budget-off, scelta esplicita vincente, edit mai sovrascritto): la preselezione applica lo stesso preset a un nuovo punto di ingresso (stato iniziale invece del solo `onTypeChanged`). Aggiunto un chiarimento di una frase all'ADR 22, nessun ADR nuovo. Il tipo `initialTypeName` è generico ma l'unico chiamante passa SAVINGS.

**Problemi:** nessuno; gate `assembleDebug testDebugUnitTest lint detekt` verde.

**Prossimo:** verifica su device del flusso completo (crea obiettivo -> "crea conto" -> editor già su SAVINGS con budget off -> salva -> obiettivo collegato).

---

## 2026-07-18 - Obiettivi di risparmio (Fase 10.0, v2.0)

**Fatto:** implementata la feature Obiettivi di risparmio, prima della v2.0 (versionCode 80 -> 81, versionName 0.9.41 -> 0.9.42). Modello legato a un conto risparmio (scelta confermata con l'utente).
- Dominio/persistenza: `SavingsGoal` + `SavingsGoalEntity` (tabella `savings_goals`, FK `accountId` -> `accounts` `ON DELETE CASCADE`, indice UNIQUE = un obiettivo per conto), mapper, `SavingsGoalRepository`/`RoomSavingsGoalRepository`, DAO e DI. `SaldoDatabase` a version 2 con `MIGRATION_1_2` (`CREATE TABLE` con FK, schema `2.json` esportato e verificato identico allo statement generato da Room).
- Dominio: `ObserveSavingsGoalsProgressUseCase` calcola risparmiato (= saldo del conto, ADR 3), progresso, suggerimento mensile (mesi rimanenti alla data, arrotondamento per eccesso in minor units) e proiezione/verdetto "in linea" dai trasferimenti ricorrenti same-currency verso il conto (riuso di `RecurrenceCalculator.monthlyEquivalent`).
- UI (`feature/savings`): schermata lista (hero totale + card obiettivo con barra positiva, %, riga di stato) ed editor (nome, target, conto risparmio con scorciatoia "crea conto", data opzionale, colore/icona, guardia modifiche non salvate, empty-state senza conti risparmio). Route Nav3, voce Impostazioni > Gestione.
- Dashboard: card opzionale "Obiettivi di risparmio" (toggle in Impostazioni) e terza metrica "Risparmio / mese" sulla card "Movimenti ricorrenti".
- Backup: campo additivo `savingsGoals` (versione invariata), mapper, validazione, conteggio nell'anteprima di ripristino.

**Decisioni:** ADR 25. Account-linked e non un registro di contributi separato: unico modello onesto con single-source-of-truth (il denaro sta nei conti, il saldo è calcolato); un registro separato creerebbe "risparmiato" fantasma. Migration pulita e non collasso del baseline: la FK è ammessa in un `CREATE TABLE` nuovo (a differenza dell'`ALTER TABLE ADD COLUMN` che aveva forzato il collasso nell'ADR 24), quindi si riprende la disciplina migration dell'ADR 23. La card "Movimenti ricorrenti" ora include i trasferimenti verso il risparmio: la loro assenza in Dashboard era un'omissione della Fase 9.15, verificata su richiesta dell'utente, non una scelta di design.

**Problemi:** wrapper Gradle non scaricabile dietro proxy: usato il Gradle di sistema `/opt/gradle`. Detekt: `CyclomaticComplexMethod` su `recurringSummary` risolto estraendo i predicati e `nextRecurringEvent`; `ComplexCondition`/`ReturnCount`/`MaxLineLength`/`TooManyFunctions` risolti su editor VM, use case e `SettingsViewModel`. Nessun emulatore in sessione: `Migration1To2Test` è scritto ma non eseguito; verifica affidata a `assembleDebug testDebugUnitTest lint detekt` (verdi) e ai nuovi unit test (mapper, use case, editor VM, round-trip backup).

**Prossimo:** verifica su device (creazione obiettivo su un conto risparmio, trasferimento ricorrente verso il conto -> suggerimento/proiezione, card dashboard, update in-place APK, round-trip backup con obiettivi). Eventuale preselezione del tipo `SAVINGS` nell'editor conto aperto dalla scorciatoia.

---

## 2026-07-17 - Fix crash all'avvio: migration ripiegata nel baseline v1

**Fatto:** rimossa la `MIGRATION_1_2` e ripiegate le colonne transfer nel baseline v1 (`SaldoDatabase.version` di nuovo 1, `1.json` rigenerato con colonne + indice + FK, `2.json` e `Migration1To2Test` eliminati, `ALL_MIGRATIONS` di nuovo vuoto). versionCode 79 -> 80, versionName 0.9.40 -> 0.9.41.

**Decisioni:** la 0.9.40 crashava all'avvio dopo l'update in-place. Causa: `Migration(1,2)` aggiungeva le colonne con `ALTER TABLE ADD COLUMN` + indice, ma `RecurringRuleEntity` dichiara una foreign key su `transferAccountId` verso `accounts`, e in SQLite `ADD COLUMN` non puo' aggiungere una FK; lo schema migrato divergeva da `2.json` e Room lanciava `IllegalStateException` all'apertura. Il fix corretto per una migration sarebbe la ricreazione della tabella (create-copy-drop-rename) con la FK; qui invece, essendo l'app non pubblicata e su un solo device (dati gia' svuotati), si collassa nel baseline v1 come ADR 23, coerente con la richiesta dell'utente. Lo schema creato da zero (`CREATE TABLE`) include la FK correttamente. ADR 24 aggiornato di conseguenza.

**Problemi:** il migration test strumentato che avevo scritto avrebbe intercettato la divergenza, ma senza emulatore in sessione non e' stato eseguito, quindi il bug e' sfuggito alla verifica basata su build. `assembleDebug testDebugUnitTest lint detekt` verdi; `1.json` verificato (version 1, FK `transferAccountId` -> `accounts` presente).

**Prossimo:** verifica update in-place su device (avvio senza crash, creazione di un trasferimento ricorrente). Chi avesse ancora un DB v2 rotto deve reinstallare o svuotare (vincolo gia' previsto da ADR 23).

---

## 2026-07-17 - Trasferimenti ricorrenti (Fase 9.15)

**Fatto:** aggiunto il terzo tipo di ricorrenza, i trasferimenti, integrato nel motore/feature esistente (versionCode 78 -> 79, versionName 0.9.39 -> 0.9.40).
- Modello/persistenza: `RecurringRule` e `RecurringRuleEntity` guadagnano la gamba di destinazione (`transferAccountId`/`transferAmountMinor`/`transferCurrency`, mirror di `TransactionEntity`), con secondo FK e indice. `RecurringRuleMapper` e il backup (`RecurringRuleBackup`, `BackupMapper`) mappano i nuovi campi (opzionali, `CURRENT_VERSION` del backup invariato). DB version 1 -> 2 con `MIGRATION_1_2` (ALTER TABLE + indice) in `Migrations.kt`, schema `2.json` esportato, migration test strumentato `Migration1To2Test`.
- Motore: ramo TRANSFER in `GenerateRecurringMovementsUseCase.toMovement` - gamba sorgente negativa, destinazione = sorgente per stessa valuta, `null` (pending) per cross-currency. Idempotenza/watermark/coda pending invariati.
- Editor (`RecurringRuleEditorViewModel`/`Screen`/`Form`): tolto il filtro che scartava TRANSFER; secondo `AccountField` per la destinazione, categoria nascosta, automatico solo same-currency, cross-currency forzato in CONFIRM con nota esplicativa.
- Coda pending: `confirm()` transfer-aware - per cross-currency l'importo inserito è la gamba di destinazione (sorgente fissa), per same-currency muove entrambe le gambe; sheet con etichetta "Importo ricevuto" e contesto "Invii X -> conto".
- Hub (`RecurrencesViewModel`/`Screen`): terza tab Trasferimenti, riepilogo "Risparmio pianificato: X/mese" derivato dai soli trasferimenti verso conti `SAVINGS`. Notifiche pre-rinnovo con wording transfer. Stringhe IT/EN complete.

**Decisioni:** ADR 24. Integrare, non un modulo separato: `RecurrenceCalculator` e il modello movimenti (ADR 2/3/4) erano già type-agnostic, un motore parallelo avrebbe duplicato worker/catch-up/coda pending contro ADR 12. Cross-currency solo in conferma: congelare un tasso di cambio e riapplicarlo ogni mese è disonesto (il cambio deriva), quindi si riusa la modalità conferma per catturare l'importo reale ricevuto. "Premium" inteso come qualità di UI/UX (confermato con l'utente): nessun paywall, feature core gratuita, coerente con VISION. Il riepilogo risparmio è il seme onesto degli Obiettivi di risparmio (v2.0) senza costruirne il modulo.

**Problemi:** wrapper Gradle non scaricabile (proxy blocca GitHub): usato il Gradle di sistema `/opt/gradle`. Detekt: `CyclomaticComplexMethod`/`ReturnCount`/`ComplexCondition` su `buildValidRule` risolti estraendo `transferLeg`/`effectiveMode`/`seededWatermark` e unificando le guard; `TooManyFunctions` di file su `RecurringRuleEditorScreen` risolto estraendo `AmountAndModeSection`/`AccountsSection` più `@file:Suppress` motivato (stile del progetto). Nessun emulatore in sessione: il migration test e gli altri strumentati non sono stati eseguiti, verifica affidata a `assembleDebug testDebugUnitTest lint detekt` (tutti verdi) e ai nuovi unit test (motore, mapper, backup, editor VM, pending VM, hub VM).

**Prossimo:** eventuale verifica su device (trasferimento automatico same-currency, cross-currency in conferma, card risparmio pianificato, round-trip backup) quando un emulatore è disponibile. Il devlog precedente è stato archiviato in `devlog-2026-07-17.md` (superate le 1000 righe).

---
