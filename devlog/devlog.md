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

## 2026-07-28 - Smistamento completo della review finanziaria: Fasi 15-22 e Roadmap v3.0

**Fatto:** la tabella dei candidati di [docs/review-funzionalita-finanziarie-2026-07-27.md](../docs/review-funzionalita-finanziarie-2026-07-27.md) è ora interamente smistata in PLANNING, su decisione esplicita dell'utente. Le voci della review già presenti nel backlog v2.0 diventano fasi dettagliate da eseguire prima della release: Fase 15 rimborsi collegati alla spesa originale, 16 gestione tag dedicata, 17 multi-valuta con conversione automatica, 18 commissioni sui trasferimenti, 19 rilevamento automatico delle ricorrenze, 20 analisi avanzate, 21 export PDF/Excel/Google Sheets, 22 cifratura del backup con passphrase. I candidati restanti sono pianificati in una nuova sezione "Roadmap v3.0", dopo la fase di release: Fase 24 budget con periodo personalizzato e riporto, 25 acquisti a rate e numero di ripetizioni, 26 split su più categorie, 27 pagamento parziale dell'estratto carta, 28 arrotondamento degli spiccioli, 29 report periodico ricorrente. La fase di release v2.0 passa da "Fase 15" a "Fase 23" (riferimenti aggiornati nelle Fasi 9 e 11.5 e nel gate della release, che ora richiede le Fasi 12-22); ogni riga della tabella e ogni nota della review (funzioni esistenti da riusare, cose da non implementare) è riportata nella fase corrispondente. Aggiornati anche il backlog v2.0 (puntatori alle fasi), la nota in "Note e appunti", la sezione roadmap del README e il documento di review, che ora chiude con la sezione "Smistamento completato" e l'esito riga per riga.

**Decisioni:** distinzione v2.0/v3.0 scelta dall'utente: in v2.0 solo i candidati che erano già nel backlog, tutto il resto dettagliato subito ma collocato dopo la release, così nessuna nota della review va persa e la v2.0 resta finita. Il documento di review si tiene (contiene la tabella "già coperte, da non implementare" e la mappa delle semantiche del denaro, che non vivono altrove) e prende la data nel nome del file, `review-funzionalita-finanziarie-2026-07-27.md`: una review futura sarà un documento nuovo con la propria data, questo non si riapre. Restano esclusi payee e sottocategorie, come raccomanda la review stessa; il backup cloud resta nella sua fase da valutare. Le nuove fasi non hanno ADR: ogni fase elenca le decisioni di design da proporre come ADR all'avvio (le più delicate: attribuzione temporale dei rimborsi collegati nelle query statistiche, provider e cache dei tassi nella Fase 17, che è l'unica a portare la rete fuori dal perimetro backup/export, spesa collegata vs campo fee nella Fase 18, tabella delle fette nella Fase 26).

**Problemi:** nessuno. Verificato prima dello smistamento che nessun candidato fosse già stato implementato nel frattempo (il PIN della riga 20 sì, Fase 14.5) e riverificato nel codice il limite 2 della sezione 3 della review: i budget filtrano per valuta in `ObserveBudgetProgressUseCase` e l'unica traccia in UI è l'hint dell'editor, quindi la segnalazione è corretta e la chiusura è un punto della Fase 17. Nessun bump di versione: modifica di sola documentazione.

**Prossimo:** Fasi 12-14 nell'ordine pianificato, poi le nuove 15-22.

---

## 2026-07-28 - Fase 14.5: blocco app con PIN, biometria e FLAG_SECURE

**Fatto:** primo punto della Roadmap v2.0 consegnato dopo la release 1.0.0 (ADR 39, nuova Fase 14.5 in PLANNING). Layer `core/common/applock`: `PinHasher` (PBKDF2-HMAC-SHA256, salt casuale, 150k iterazioni salvate accanto all'hash), `AppLockRepository` su un DataStore dedicato `app_lock` con corruption handler, `AppLockManager` (stato EVALUATING/LOCKED/UNLOCKED, cooldown progressivo persistito: 30s dopo 5 errori, raddoppio, tetto 5 minuti), `ForegroundTracker` puro + `AppLockLifecycleObserver` per il re-lock all'uscita dal foreground. UI in `feature/applock`: lock screen come overlay sopra il Crossfade di MainActivity (i back stack Nav3 sopravvivono al re-lock), `PinKeypad` dedicato con slot biometrico, `PinIndicator` a 6 pallini con shake e haptic, `PinEntryPane` condiviso, schermata Sicurezza raggiunta da una nuova sezione delle Impostazioni (setup crea+conferma, cambio PIN, switch biometrico confermato da un prompt reale, blocco automatico Subito/1 min/5 min, FLAG_SECURE indipendente). Biometria con il `BiometricPrompt` del framework (`BIOMETRIC_STRONG`), permesso normale `USE_BIOMETRIC` nel manifest. La sheet del widget è bloccata dallo stesso manager; FLAG_SECURE applicato a tutte e tre le activity. `EraseAllDataUseCase` rimuove il lock con una chiamata esplicita. Le primitive delle righe impostazioni sono state estratte in `core/designsystem/component/SettingsRows.kt` e riusate da Impostazioni e Sicurezza. Bump a versionCode 153, versionName 1.0.1.

**Decisioni:** zero dipendenze nuove: il framework `BiometricPrompt` esiste da API 28 e minSdk 33 lo copre con un solo code path, mentre la androidx stabile avrebbe richiesto `FragmentActivity` (e il supporto ComponentActivity è solo in alpha); `lifecycle-process` sostituita da un tracker di ~20 righe su `ActivityLifecycleCallbacks`, che esclude i cambi di configurazione via `isChangingConfigurations`. Store separato così "cancella tutti i dati" rimuove il PIN per decisione scritta e non per effetto collaterale di `clear()`, e l'hash non entra mai nel backup JSON. Fail-closed all'avvio (superficie opaca finché il gate non decide, inversione dichiarata del fail-open dell'onboarding) ma con il limite onesto: store corrotto = lock disattivato, mai app brickata. PIN fisso a 6 cifre con auto-submit (scelta utente, come biometria framework e widget bloccato). Nessun recupero PIN: app offline, la biometria è la via alternativa, altrimenti si cancellano i dati dell'app; scritto nell'hint di attivazione e nella guida utente, non sul lock screen. Il PIN in corso di digitazione vive solo nel ViewModel, mai in SavedStateHandle.

**Problemi:** nessuno bloccante. Due dettagli da conoscere: una Surface overlay non consuma i tocchi da sola, serve un `pointerInput` che ingoia il gesto (altrimenti i tap sulle aree vuote passerebbero all'app sotto); il contenuto coperto perde anche la semantica (`clearAndSetSemantics`) così TalkBack non attraversa la UI dietro il lock.

**Verificato:** verifica statica in locale (nessun SDK Android sulla macchina), build e test demandati alla CI GitHub al push (`assembleDebug testDebugUnitTest lint detekt`). Nuovi unit test JVM: `PinHasherTest`, `AppLockManagerTest`, `ForegroundTrackerTest`, `LockViewModelTest`, `SecurityViewModelTest`, più l'estensione di `EraseAllDataUseCaseTest`. Resta la checklist su device elencata nella Fase 14.5 (biometria, cooldown, re-lock, FLAG_SECURE, widget, TalkBack).

**Prossimo:** verifica su device della checklist della Fase 14.5; poi le Fasi 12-14 nell'ordine pianificato.

---

## 2026-07-28 - Fase 11.1: anteprime del picker statiche, residuo a zero

**Fatto:** dalla prova sul launcher e emerso che le card del picker mostravano le anteprime **generate** (`setWidgetPreviews`, API 35): categorie reali dell'utente, ma tile senza icone e bottoni della barra con i glifi mancanti. Rimosso l'intero percorso generato: `WidgetPreviews` e il suo `WidgetPreviewWorker`, la chiamata in `SaldoApplication.onCreate`, gli override `previewSizeMode` e `providePreview` dei due widget, `provideQuickAddPreview`, i bucket `PreviewBucket`/`PreviewRowBucket` e `WidgetEntryPoint.userPreferences()`, rimasto senza chiamanti. Il picker torna al solo `previewLayout`, e quello della griglia e stato ridisegnato come mock statico: selettore Spesa/Entrata piu due file di quattro tile arrotondate, quattro tinte dai token `system_accent*` con variante `values-night`. Nessuna icona, nessun nome di categoria, niente letto dal database. Bump a versionCode 152, versionName fermo a 1.0.0.

**Decisioni:** scelta utente fra quattro alternative, presa quella che azzera il costo e tiene un'anteprima somigliante. La ragione tecnica per cui il percorso generato non era sanabile a buon mercato: l'anteprima non e una risorsa dell'app ma stato tenuto da `system_server`, che la butta via a ogni aggiornamento in place e a ogni riavvio, mentre `setWidgetPreview` accetta circa due chiamate l'ora per provider. Da qui il check al cold start, la lettura del DB per comporla e il worker di retry, cioe tutto cio che l'ADR 37 aveva dovuto ammettere come "unico residuo": ora quel residuo non c'e piu. L'ADR e stato riscritto di conseguenza e in modo piu vincolante: il livello raggiunto e dichiarato un tetto e non un traguardo, il punto (a) elenca cosa l'app non deve fare a riposo con un criterio verificabile, e un punto (b) nuovo mette le anteprime generate fuori scope in modo definitivo, con la causa tecnica scritta nella colonna delle motivazioni. Quella causa viveva nel KDoc di `WidgetPreviews`, che con questo giro sparisce: senza trascriverla, la prima persona che rilegge il piano fra sei mesi rifarebbe esattamente il lavoro appena tolto. Il mock e volutamente povero di elementi: il picker inflaziona il `previewLayout` come RemoteViews, e le due volte in cui questa superficie ha sbagliato a disegnare era per un drawable troppo furbo (adaptive icon), quindi shape drawable e viste dell'allow-list e nient'altro. Le anteprime generate gia pubblicate sul device spariscono da sole al primo aggiornamento: azzerando `generatedPreviewCategories`, il sistema fa ricadere il launcher sul `previewLayout`.

**Problemi:** nessuno. Da sapere: l'anteprima della griglia ora e dichiaratamente un segnaposto e non mostra le categorie vere, il che e anche l'unica scelta onesta per una card che il sistema disegna prima che un utente esista.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` in locale. Su device restano da confermare le due card del picker in chiaro e scuro e la sparizione delle vecchie anteprime dopo l'aggiornamento (checklist della Fase 11.1).

**Prossimo:** checklist di verifica su device, poi il tag `v1.0.0`.

---

## 2026-07-28 - Riga di copyright nella schermata Informazioni

**Fatto:** sotto "Sviluppata da Callback Dev" compare `© 2026 Fiorenzo Brioni`, come nell'About di Snake (l'altro progetto Android dello stesso autore). Nuova stringa `about_copyright` in `values/strings.xml` con `translatable="false"`, `bodySmall` in `onSurfaceVariant` dentro `AppIdentity`, due dp di spazio sopra: sta sotto la riga dell'autore come una didascalia, senza contendere gerarchia al nome dell'app.

**Decisioni:** stringa non traducibile e presente nel solo `values`, come gia si fa per `about_library_names`: un nome proprio e un anno sono identici in ogni lingua, e duplicarli in `values-it` creerebbe due punti da tenere allineati per niente. Anno fisso nella risorsa e non calcolato a runtime: il copyright si riferisce all'anno di pubblicazione, non alla data in cui l'utente apre la schermata. `versionCode` a 151 con `versionName` fermo a 1.0.0: il codice identifica la build e deve avanzare perche il device di test aggiorni in place, il nome identifica il rilascio e resta quello che verra taggato.

**Problemi:** nessuno.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` in locale. Resa della riga da confermare su device insieme al resto della checklist della Fase 11.5.

**Prossimo:** invariato, la checklist di verifica su device e poi il tag `v1.0.0`.

---

## 2026-07-28 - La fase di release v1.0 diventa la 11.5

**Fatto:** rinumerata la fase di rilascio da "Fase 10" a "Fase 11.5" e aggiornati i riferimenti in PLANNING (nota dell'H1 delle fasi anticipate, riga del baseline profile nella Fase 9, nota sulle transizioni nella Fase 9.6, nota della Fase 15). Ora l'ordine di lettura del documento coincide con l'ordine dei numeri: 9.16, 10.0-10.21, 11, 11.5, 12, 13, 14, cloud, 15.

**Decisioni:** rilievo dell'utente, corretto: dopo lo spostamento della fase di release in fondo al blocco implementato, il documento passava da "Fase 11" a "Fase 10" andando in avanti, e "Fase 10" accanto a venti fasi numerate 10.x lasciava intendere una gerarchia che non esiste (le 10.x non sono sotto-passi del rilascio, sono funzionalita anticipate che hanno occupato quel numero prima che il rilascio arrivasse). Il suffisso .5 e gia la convenzione del file per le fasi inserite a valle (6.5, 9.5), quindi 11.5 dice esattamente la cosa giusta: il rilascio viene subito dopo l'ultima fase che rilascia. Regola ora esplicita nella nota della fase. Il costo e limitato ai riferimenti interni del piano: devlog archiviati e messaggi di commit precedenti continuano a dire "Fase 10", e restano come sono perche sono registri datati; la nota della fase lo dichiara, cosi il riferimento resta rintracciabile.

**Problemi:** nessuno.

**Verificato:** nessun riferimento residuo a "Fase 10" fuori dalla nota storica della fase e dai devlog archiviati; sequenza delle intestazioni verificata in ordine.

**Prossimo:** invariato, la checklist di verifica su device e poi il tag `v1.0.0`.

---

## 2026-07-28 - VISION riallineata alla release 1.0.0

**Fatto:** VISION.md portata in pari con la roadmap dopo la chiusura della v1.5 in PLANNING. Le etichette di versione inline seguono ora quello che e davvero uscito: budget e obiettivi di risparmio passano a v1.0, import CSV a v1.0 con la descrizione reale della funzione (riconoscimento del formato, anteprima, duplicati), i widget non sono piu "anticipati rispetto alla v1.5"; PIN, biometria, FLAG_SECURE, Excel e Google Sheets passano a v2.0. La sezione Roadmap perde il blocco v1.5 e diventa v1.0 (rilasciata, con l'elenco diviso tra perimetro MVP e anticipi) piu v2.0, con in testa la nota del rilascio su GitHub. Corretta anche la parte backup, che presentava Google Drive come "strategia principale" mentre la v1.0 ha il solo backup manuale su file: Drive e ora dichiarato rimandato (ADR 17), e il file di backup e presentato come il backup della versione, non come una seconda strada.

**Decisioni:** nessuna decisione di prodotto nuova, solo allineamento del documento a decisioni gia prese (ADR 17 e 38). Subito dopo, su richiesta utente, il termine "v1.5" e stato eliminato del tutto da PLANNING e VISION: dove serviva il concetto si parla di "rilascio intermedio fra l'MVP e la v2.0" o di "anticipi alla v1.0", cosi il documento non rimanda piu a una roadmap che non esiste. Allineate anche le due celle della review delle funzionalita finanziarie che rimandavano a quella roadmap (voci 18 e 20 della tabella dei candidati, ora "gia in roadmap v2.0"). Il termine resta solo nel devlog, che e un registro datato e non si riscrive.

**Modifiche puntuali della rimozione:** ADR 10 senza la nota storica; Fase 9.5 "anticipata alla v1.0" e il widget descritto come rimandato alla Fase 10.18 invece che "alla v1.5"; Fase 9.16 rinominata "Anticipi alla v1.0: budget, import CSV e widget"; note delle Fasi 10.18 e 10 riscritte; intestazione della Roadmap v2.0 che spiega il riassorbimento senza nominare la versione.

**Problemi:** nessuno.

**Verificato:** modifica di sola documentazione, nessun bump di versione (regola in CLAUDE.md). Nessun riferimento residuo alla v1.5 fuori dalla nota storica.

**Prossimo:** la checklist di verifica su device della fase di release (rinumerata 11.5 subito dopo, vedi la voce sopra), poi il tag `v1.0.0`.

---
