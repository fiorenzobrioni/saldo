# CLAUDE.md

**Saldo** - app Android (Kotlin + Compose) di gestione spese personali, offline-first e privacy-first.

## Documenti di riferimento

- **VISION.md** (root): cosa è il prodotto e perché. Consultala prima di prendere decisioni di prodotto/UX.
- **PLANNING.md** (root): roadmap a fasi con checkbox e ADR (decisioni architetturali). È la fonte di verità sullo stato di avanzamento.
  - Quando completi un task, **spunta la checkbox** corrispondente.
  - Bug trovati → aggiungili in "Bug conosciuti" (spunta al fix, con riferimento al commit).
  - Idee/spunti emersi → "Note e appunti". Non implementarli senza chiedere.
- Rispetta gli ADR in PLANNING.md. Se un ADR va cambiato, proponilo e motivalo prima, non aggirarlo.
- **`devlog/`**: registro storico di *cosa è successo* (lavoro completato e decisioni prese). File attivo `devlog/devlog.md`, voce più recente in alto; regole e template in `devlog/README.md` (non modificare quel file). Aggiungi una voce datata al completamento di uno step o di una fase: cosa è stato implementato, come è stato verificato (test eseguiti, device usato), decisioni rilevanti o problemi incontrati.
- **`README.md` sempre aggiornato**: alla fine di ogni implementazione rileggi `README.md` e aggiornalo se la modifica tocca qualcosa di visibile all'utente o qualcosa che vi è descritto. Se non serve alcun aggiornamento, nessuna azione.

## Stile di Scrittura e Documentazione (valido SOLO per i file di prosa)

Quando scrivi, modifichi o generi testo per i file di documentazione del repository (come `CLAUDE.md`, `README.md`, file all'interno di `docs/` o `devlog/`, `PLANNING.md`, ecc.), applica rigorosamente queste regole:

### 1. Regole Tipografiche

- Non usare MAI l'em-dash (`—`) o l'en-dash (`–`). Utilizza un trattino standard (`-`), i due punti (`:`), la virgola o le parentesi tonde, a seconda del contesto.
- Non usare MAI il punto mediano (`·`). Sostituiscilo con un trattino standard (`-`) o una virgola.

### 2. Tono e Stile

- Stile ingegneristico: Mantieni un tono oggettivo e diretto. Elimina qualsiasi forma di entusiasmo artificiale, linguaggio di marketing o aggettivi superflui (es. "fantastico", "potente", "robusto").
- Nessuna conclusione: Non inserire paragrafi conclusivi o riassunti finali; termina la frase non appena l'informazione tecnica è stata esposta.

### 3. Eccezioni per il Codice (ATTENZIONE)

- Le regole sopra elencate (in particolare quelle tipografiche) **NON si applicano** al codice sorgente o alle risorse di stringa. All'interno di file di codice (es. `.js`, `.py`, `.java`, `.cpp`, file XML, JSON di traduzione, ecc.), l'uso di em-dash, en-dash o punto mediano è assolutamente consentito ovunque il design della UI, le stringhe di testo o la sintassi lo richiedano.

## Regole di dominio (non negoziabili)

- **Importi**: `Long` in unità minori (centesimi) nel DB → `BigDecimal` nel dominio → `String` localizzata solo nella UI. **Mai Float/Double per denaro**, mai aritmetica monetaria nella UI. La scale dipende dalla valuta (`Currency.getDefaultFractionDigits()`).
- Tipi movimento: `EXPENSE`, `INCOME`, `TRANSFER`, `ADJUSTMENT`.
- `TRANSFER` e `ADJUSTMENT` sono **sempre esclusi** dalle statistiche, a livello di query.
- Un trasferimento è **un singolo record** con `fromAccountId`/`toAccountId`, mai due movimenti.
- Il saldo di un account è **calcolato** (`initialBalance + Σ movimenti`), mai denormalizzato/salvato.
- I debiti sono conti a saldo negativo che si riducono (carta di credito, prestito): il rimborso è un **trasferimento**, mai una spesa, altrimenti il denaro viene contato due volte (ADR 20 e 33). L'app non calcola interessi né piani di ammortamento.
- Prestare denaro non è una spesa e riaverlo non è un'entrata: il movimento con controparte è sempre escluso dalle statistiche, ma incide sul saldo del conto come ogni altro (ADR 34).
- Offline-first: nessuna funzione core deve richiedere rete o account. Rete solo per backup/export opzionali.

## Stack e vincoli tecnici

- **applicationId: `com.callbackdev.saldo`** (brand: Callback Dev). Tutti i package del codice vivono sotto `com.callbackdev.saldo.*`. Non cambiarlo mai: è immutabile dopo la pubblicazione su Play Store.
- Kotlin 100%, Jetpack Compose + Material 3 (dynamic color, sempre disponibile), minSDK 33, targetSdk/compileSdk fissati esplicitamente (attualmente 36). Non inseguire l'ultima versione: l'aggiornamento del targetSdk è una chore dedicata e testata (ADR 14 in PLANNING.md).
- Room + KSP (mai KAPT), Hilt, Coroutines/Flow, DataStore Preferences, **Navigation 3** (`androidx.navigation3`, stabile da novembre 2025) - non Navigation Compose/Nav2, WorkManager, Vico per i grafici.
- Navigation 3 è recente: **non andare a memoria sulle API** (le alpha differiscono dalla stabile). In caso di dubbi consulta la documentazione ufficiale (https://developer.android.com/guide/navigation/navigation-3) e il repo delle recipes ufficiali. Pattern base: route come `NavKey`, back stack con `rememberNavBackStack`, destinazioni in `entryProvider`, rendering con `NavDisplay`.
- Dipendenze solo via Version Catalog (`libs.versions.toml`). **Non aggiungere nuove librerie senza chiedere.**
- Package-by-feature nel modulo `:app`: `core/{database,designsystem,common,domain}` + `feature/*`.
- La UI osserva Flow dal database (single source of truth); UI state immutabile nei ViewModel.
- Use Case **solo dove c'è logica di dominio reale** (ricorrenze, rettifiche saldo, statistiche, rimborsi, backup); per il CRUD banale il ViewModel usa direttamente il Repository. Non creare use case passacarte.
- Test: JUnit5 per unit test JVM; JUnit4 per test strumentati e Compose UI Test (le rule Compose lo richiedono).
- Date: `Instant` UTC + offset salvato.
- Migration Room sempre esplicite e testate; **mai** `fallbackToDestructiveMigration`.

## Convenzioni

- Codice, identificatori e commit in **inglese**; documentazione e stringhe utente in **italiano + inglese**.
- Nessuna stringa hardcoded: tutto in `strings.xml` (values + values-it) fin da subito.
- Commit: Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- Accessibilità: contentDescription sugli elementi interattivi; spese/entrate distinte anche da segno/icona, non solo dal colore.
- Se esistono mockup in `docs/design/`, usali come **riferimento di layout e gerarchia** per le schermate corrispondenti — non come spec al pixel. Implementa sempre con componenti Material 3; non tradurre né importare mai HTML/CSS/JS provenienti dai mockup.

## Qualità e verifica

- Prima di considerare concluso un task: `./gradlew assembleDebug testDebugUnitTest lint detekt` deve passare. È lo stesso comando del workflow CI (`.github/workflows/ci.yml`): se i due divergono, la CI boccia lavoro già dato per concluso.
- **Versioning delle build di test**: al termine di ogni implementazione che modifica l'app (non per modifiche solo alla documentazione), incrementa `versionCode` di 1 in `app/build.gradle.kts` e allinea `versionName` allo schema `2.1.<incremento>` (lo schema segue sempre l'ultima versione pubblicata: era `0.<ultima fase completata>.<incremento>` fino alla 1.0.0, `1.0.<incremento>` fra la 1.0.0 e la 2.0.0, `2.0.<incremento>` fra la 2.0.0 e la 2.1.0; il prossimo traguardo pianificato è la 3.0.0, ma una minor pubblicata nel frattempo sposta lo schema, come ha fatto la 2.1.0). Serve ad aggiornare in place l'APK debug sul device di test senza reinstallare (e senza perdere i dati inseriti). Il bump fa parte del commit dell'implementazione, non richiede una build dedicata.
- **Keystore di debug condiviso**: `keystore/debug.keystore` (committato di proposito, `signingConfigs.debug` in `app/build.gradle.kts`) firma allo stesso modo ogni build, locale o CI. Senza un keystore condiviso il bump di versione da solo non basta: ogni macchina firmerebbe con il proprio `~/.android/debug.keystore` e Android rifiuterebbe comunque l'update in-place per firma diversa. Non toccare né rigenerare questo file: è una chiave di solo debug (nessun valore ai fini della pubblicazione), non ha bisogno di segreti/CI secrets.
- **Release**: si pubblica su GitHub, non sul Play Store (ADR 38 in PLANNING.md). Scrivere le note di rilascio in `docs/release-notes/vX.Y.Z.md`.
- Unit test obbligatori per: mapper importi, motore ricorrenze (mesi corti, idempotenza, catch-up), calcolo saldi, round-trip backup export→import.
- Non introdurre regressioni sui saldi: se tocchi query o mapper, riesegui i test relativi.
