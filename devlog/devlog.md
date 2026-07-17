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
