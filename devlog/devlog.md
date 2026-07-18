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
