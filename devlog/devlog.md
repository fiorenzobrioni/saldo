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
