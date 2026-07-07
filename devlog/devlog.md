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
