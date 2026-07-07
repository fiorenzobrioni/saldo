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

- [ ] Entity Room: `AccountEntity`, `CategoryEntity`, `TransactionEntity`, `TagEntity` + cross-ref, `RecurringRuleEntity`
- [ ] Enum `TransactionType` (EXPENSE, INCOME, TRANSFER, ADJUSTMENT)
- [ ] TypeConverter: Instant, enum
- [ ] DAO con query fondamentali:
  - [ ] saldo per account (`initialBalance + Σ`) come Flow
  - [ ] saldo totale (solo account inclusi e non archiviati)
  - [ ] movimenti per giorno/mese/intervallo
  - [ ] aggregati per categoria (esclusi TRANSFER/ADJUSTMENT)
- [ ] Modelli di dominio + mapper (centesimi ↔ BigDecimal)
- [ ] Repository (interfacce dominio + implementazioni Room)
- [ ] Seed categorie predefinite alla prima apertura (IT/EN in base alla locale)
- [ ] Unit test: mapper, calcolo saldi, query aggregate (Room in-memory)

## Fase 2 - Account

- [ ] Lista account con saldo corrente
- [ ] Creazione/modifica account (nome, tipo, valuta, saldo iniziale, colore, icona, incluso nel totale)
- [ ] Archiviazione account (+ vista archiviati)
- [ ] **Rettifica saldo**: inserisco il saldo reale → l'app genera il movimento ADJUSTMENT con la differenza
- [ ] Eliminazione account: consentita solo se senza movimenti, altrimenti proporre archiviazione
- [ ] Test: rettifica saldo, esclusione archiviati dal totale

## Fase 3 - Movimenti (CRUD)

- [ ] Schermata inserimento spesa/entrata: tastierino importo subito attivo, categoria a griglia, account di default preselezionato, data = oggi modificabile
- [ ] Obiettivo UX verificato: spesa tipica in ≤ 3 tap + importo
- [ ] Inserimento trasferimento (da → a, importo; due importi se valute diverse)
- [ ] Lista movimenti raggruppata per giorno con totali giornalieri
- [ ] Modifica movimento
- [ ] Eliminazione con swipe + undo (Snackbar)
- [ ] Tag: creazione inline e assegnazione
- [ ] Flag "escludi dalle statistiche" e flag "rimborso" (versione semplificata MVP)
- [ ] Test: ViewModel inserimento, effetti sul saldo per ogni tipo

## Fase 4 - Categorie

- [ ] Lista categorie divise spese/entrate
- [ ] Crea/modifica: nome, colore (palette), icona (set Material Symbols), tipo
- [ ] Eliminazione con riassegnazione movimenti (dialog: scegli categoria di destinazione o "Altro")
- [ ] Riordino manuale (drag)

## Fase 5 - Dashboard "Oggi"

- [ ] Card saldo totale + dettaglio account espandibile
- [ ] Card oggi (spese/entrate/netto)
- [ ] Card mese corrente + confronto con stesso giorno mese precedente
- [ ] Card abbonamenti del mese (totale + prossimo addebito) - placeholder finché Fase 6 non è pronta
- [ ] Ultimi 5–7 movimenti con tap → dettaglio
- [ ] FAB con 3 quick action
- [ ] Empty state prima apertura (CTA: crea il primo account)
- [ ] Performance: dashboard reattiva via Flow combinati, nessun ricalcolo manuale

## Fase 6 - Ricorrenze

- [ ] `RecurringRuleEntity`: frequenza, giorno, inizio/fine, importo fisso o variabile, modalità (auto/conferma), lastGeneratedDate
- [ ] Motore di generazione idempotente (rieseguibile senza duplicati) + gestione mesi corti (31 → ultimo giorno)
- [ ] WorkManager periodico + catch-up all'avvio app
- [ ] Modalità automatica: crea movimento + notifica informativa
- [ ] Modalità conferma / importo variabile: movimento "pending" + notifica di conferma (conferma/modifica/salta)
- [ ] CRUD regole ricorrenti; eliminazione con scelta sui movimenti futuri
- [ ] **Vista Abbonamenti**: lista, costo mensile equivalente, totale mese e proiezione annua
- [ ] Collegamento card dashboard
- [ ] Test approfonditi del motore: mesi corti, anni bisestili, catch-up dopo N giorni, idempotenza, DST

## Fase 7 - Ricerca, filtri e statistiche

- [ ] Filtri combinabili (data con preset, categorie, account, tipo, importo, tag) come chip
- [ ] Ricerca full-text su descrizione
- [ ] Totale della vista filtrata sempre visibile
- [ ] Statistiche (Vico):
  - [ ] anello spese per categoria + lista percentuali (mese/anno/custom)
  - [ ] barre trend spese 12 mesi
  - [ ] entrate vs uscite mensili
  - [ ] andamento saldo nel tempo
  - [ ] spese per account
- [ ] Drill-down: tap su grafico → lista filtrata
- [ ] Verifica esclusione TRANSFER/ADJUSTMENT e trattamento rimborsi

## Fase 8 - Backup, export, import

- [ ] Formato export JSON versionato (schema `version: 1`) di tutti i dati
- [ ] Google Sign-In via Credential Manager, scope `drive.appdata`
- [ ] Upload backup su App Data Folder + rotazione (ultimi 5)
- [ ] Backup automatico WorkManager (periodico, solo Wi-Fi, configurabile)
- [ ] Restore guidato: al primo avvio e da impostazioni
- [ ] **Backup manuale su file**: export via SAF (`ACTION_CREATE_DOCUMENT`), stesso formato JSON del backup Drive, nome `saldo-backup-YYYY-MM-DD.json`, avvertenza in UI "file non cifrato"
- [ ] Export CSV (separatore `;`/`,` configurabile, rispetta i filtri attivi, condivisione via Share Sheet)
- [ ] Restore da file di backup manuale (JSON, via SAF `ACTION_OPEN_DOCUMENT`)
- [ ] Test: round-trip export→import senza perdita dati

## Fase 9 - Impostazioni, i18n, rifinitura

- [ ] Impostazioni: valuta principale, account di default, tema, primo giorno settimana, backup
- [ ] Onboarding minimale (valuta, primo account, saldo iniziale)
- [ ] Revisione completa stringhe IT + EN
- [ ] Pass di accessibilità: TalkBack, font scaling 200%, contrasto, touch target, non solo colore per spese/entrate
- [ ] Empty state e stati di errore su tutte le schermate
- [ ] Performance: baseline profile, lista movimenti fluida con migliaia di record (paging se necessario)

## Fase 10 - Release v1.0

- [ ] QA manuale end-to-end (checklist dei flussi principali)
- [ ] Test su device reali: API 33 e ultimo Android stabile, tablet/schermi grandi (almeno layout non rotto)
- [ ] Icona app, screenshot, scheda Play Store
- [ ] Privacy policy (obbligatoria per il Play Store, anche senza raccolta dati)
- [ ] Firma release, R8/proguard rules (attenzione a Room/serialization/Drive API)
- [ ] Internal testing → closed testing → produzione

---

# Roadmap v1.5

- [ ] Budget: entità, CRUD, periodo mensile, indicatori 🟢🟡🔴, card dashboard, notifiche 80%/100%
- [ ] PIN lock + biometria (`BiometricPrompt`) + `FLAG_SECURE` opzionale
- [ ] Widget: saldo totale, spese oggi, aggiunta rapida (Glance)
- [ ] Import CSV con wizard di mappatura colonne
- [ ] Export Excel (.xlsx)
- [ ] Export Google Sheets (nuovo foglio o aggiornamento foglio esistente; richiede verifica OAuth Google per lo scope "sensitive" `spreadsheets` - avviare la review per tempo)
- [ ] Miglioramenti UX dal feedback della v1.0

# Roadmap v2.0

- [ ] Obiettivi di risparmio (target, progressi, suggerimento mensile)
- [ ] Conversione valuta automatica (provider tassi, cache offline, indicazione "stimato")
- [ ] Export PDF report con grafici
- [ ] Cifratura backup con passphrase
- [ ] Rimborsi collegati alla spesa originale
- [ ] Commissioni sui trasferimenti
- [ ] Analisi avanzate (anno su anno, pattern di spesa)
- [ ] Valutare: sottocategorie, periodo budget personalizzato

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

# Bug conosciuti

> Bug noti da risolvere. Spuntare quando fixati (con riferimento al commit).

- [ ] 
