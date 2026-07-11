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
- [x] Tipografia: headline/title a peso SemiBold in `SaldoTypography`; numeri tabulari (`tabularNumbers()`) su tutti gli importi
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

- [ ] Impostazioni: valuta principale, account di default, primo giorno settimana (tema: già fatto in Fase 6.5; backup: già fatto in Fase 8)
- [ ] Onboarding minimale (valuta, primo account, saldo iniziale, proposta di ripristino da backup)
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
- Idee "wow" dalla review completa di luglio 2026 (tutte compatibili con VISION: offline-first, privacy-first, niente open banking). Non implementarle senza deciderlo esplicitamente. Il "Radar abbonamenti" è stato implementato a luglio 2026 (Fase 6, incremento 3):
  - "Spendibile oggi" (safe-to-spend): un numero in dashboard = budget del mese - speso - ricorrenze in arrivo. Si aggancia al budget v1.5 e alla Fase 6. Trasforma il tracker da passivo a proattivo. Rimandata di proposito (luglio 2026): richiede prima il budget della v1.5.
  - Rilevamento automatico ricorrenze: euristica on-device che nota spese simili ripetute a cadenza regolare e propone di creare la regola. "Intelligenza senza cloud", coerente col posizionamento privacy. Rimandata di proposito (luglio 2026): da implementare in una fase successiva, agganciandola all'hub Ricorrenze.
  - Recap mensile condivisibile (stile Wrapped): report generato sul device, esportabile come immagine, zero dati che escono.
  - Quick-add ovunque: widget home (già in v1.5), app shortcut statici, Quick Settings tile: spesa registrata in 2 tap senza aprire l'app.
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
- [ ] Riordino categorie: il `sortOrder` globale accoppia i tab; riordinare Spese può rimescolare l'ordine relativo delle categorie "entrambi" nel tab Entrate. Da decidere: accettare (documentato) o passare a un sortOrder per tipo.
- [x] Tema scuro forzato dall'app con sistema in chiaro: `enableEdgeToEdge()` senza argomenti segue solo il uiMode di sistema, quindi le icone della status bar restavano scure su sfondo scuro (barra illeggibile, "tutta nera"). Fix: `enableEdgeToEdge` riapplicata in `setContent` con `SystemBarStyle` agganciato al tema risolto in-app (commit 15eb056)
- [x] Sfarfallio bianco nelle transizioni di navigazione con tema scuro forzato: durante il fade delle transizioni Nav3 si intravedeva la finestra Android chiara sotto Compose (stessa causa del bug status bar). Fix: `SaldoTheme` avvolge il contenuto in un `Surface` a tutto schermo con `colorScheme.background`, backdrop opaco a tema sempre presente (commit 6dc7675)
- [x] Saluti della dashboard troncati: header a riga singola (`maxLines = 1`) con varianti più lunghe dello spazio disponibile, testo tagliato con ellipsis. Fix: varianti accorciate (IT+EN) e `maxLines = 2` come margine per il font scaling (v0.7.2)
