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

## 2026-07-17 - Cambio typeface: da Roboto (default di sistema) a Inter

**Fatto:** sostituito il font di sistema (Roboto) con Inter su tutta l'app (versionCode 68 -> 69, versionName 0.9.29 -> 0.9.30). Unico punto di codice toccato: `Type.kt`.

- **Font embeddato:** aggiunto `app/src/main/res/font/inter_variable.ttf` (Inter variable, ~857 KB, dal repo `google/fonts`, SIL OFL 1.1). Un solo file: l'asse `wght` 100-900 copre Regular/Medium/SemiBold/Bold richiesti dalla type scale.
- **`SaldoTypography`:** creato `InterFamily` (`FontFamily` con le entry di peso sul variable font) e applicato `fontFamily = InterFamily` a tutti i 15 stili della scala Material 3, mantenendo gli override SemiBold su headline/title già presenti. Material 3 non ha `defaultFontFamily` nel costruttore `Typography()` (a differenza di Material 2), quindi il font va settato per stile: esteso il blocco `base.copy(...)` esistente.
- **Importi invariati:** verificato con fontTools che il file Inter espone la feature `tnum` (oltre a `zero`, `case`, ecc.), quindi `tabularNumbers()` (`fontFeatureSettings = "tnum"`) continua a incolonnare le cifre senza alcuna modifica ai call site.
- **Licenza:** aggiunto `licenses/inter/OFL.txt` (testo OFL 1.1 con il copyright del progetto Inter). Citato in README (stack + sezione Licenza) e in PLANNING.

**Decisioni:** scelto Inter e non "Google Sans" (pure OFL, ma rilasciato aperto solo a dicembre 2025 e con forte connotazione di brand Google): Inter è progettato per UI con numeri in lista, usa figure tabulari di default, ed è rodato su Android. Embedding come risorsa e non tramite il provider "downloadable Google Fonts": quest'ultimo richiede Play Services e rete, incompatibile con il principio offline-first. Asse `opsz` lasciato al default 14 (dimensione ottica adatta al testo UI). Non toccate line-height e letter-spacing: si usano i valori di default Material, che reggono bene con le metriche di Inter.

**Problemi:** il wrapper Gradle non scarica la distribuzione (403 di policy egress, come da voci precedenti); usato `/opt/gradle/bin/gradle` 8.14.3 per la verifica. `fonttools` non preinstallato: installato via pip solo per l'ispezione del font (non è una dipendenza del progetto).

**Verifica:** `gradle assembleDebug testDebugUnitTest lint` verde.

**Prossimo:** verifica su device della resa di Inter (testo e importi) in chiaro e scuro; valutare se aggiungere un credito font nella schermata About (oggi elenca solo le librerie), da fare solo se richiesto.

---

## 2026-07-17 - Separazione tonale delle top app bar allo scroll e allineamento cifre card Oggi/Mese

**Fatto:** rifiniture UI dopo una review UX (versionCode 67 -> 68, versionName 0.9.28 -> 0.9.29).

- **Top app bar reattive allo scroll:** aggiunto `TopAppBarDefaults.pinnedScrollBehavior()` a tutte le 16 schermate con title bar (tab Movimenti/Statistiche/Impostazioni, editor e schermate di dettaglio). La barra ora vira da `surface` a `surfaceContainer` quando il contenuto le scorre sotto, il comportamento Material 3 canonico che prima mancava (0 usi di `scrollBehavior` in tutta l'app). Per ogni schermata: `val scrollBehavior = pinnedScrollBehavior()`, `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` sullo `Scaffold` e `scrollBehavior = scrollBehavior` sulla `TopAppBar`. La Dashboard non è toccata: non ha top bar per scelta. La `SearchTopBar` dei Movimenti resta senza behavior (è la barra di ricerca, non un title bar).
- **Cifre Spese/Entrate incolonnate nelle card Oggi/Mese (Dashboard):** `StatLine` metteva l'importo attaccato alla label con uno spacer fisso, quindi le due righe partivano a x diverse. Ora la label prende `weight(1f)` e spinge l'importo a fine riga; le due cifre condividono il bordo destro e restano allineate. Aggiunto `tabularNumbers()` allo stile dell'importo per allineamento verticale delle cifre. La `Column` della card passa a `fillMaxWidth()` perché il `weight` abbia una larghezza su cui distribuirsi.

**Decisioni:** scelto `pinnedScrollBehavior` (barra fissa, solo cambio tonale) invece di `enterAlways`/large collapsing: nessun costo di spazio verticale, coerente con l'identità compatta e densa del layout (niente large top app bar, che contraddirebbe `SaldoDimens`). Ritirato in fase di review un terzo spunto (segno delle spese): verificato che spese ed entrate hanno già il segno (`-`/`+`), l'asimmetria è solo di colore ed è voluta.

**Problemi:** il wrapper Gradle non scarica la distribuzione (403 di policy egress, come da voci precedenti); usato `/opt/gradle/bin/gradle` 8.14.3 per la verifica.

**Verifica:** `gradle assembleDebug testDebugUnitTest lint` verde. Nessuna riga oltre i 120 caratteri (limite `.editorconfig`/detekt).

**Prossimo:** verifica su device dello stacco tonale allo scroll (chiaro e scuro) e dell'allineamento nelle card Oggi/Mese.

---

## 2026-07-16 - Revisione ed espansione dei messaggi di benvenuto della dashboard

**Fatto:** revisione dei saluti a tempo del `DashboardHeader` (array `dashboard_greetings_*` in `values` e `values-it`) e ampliamento da 6 a 10 varianti per fascia. Solo stringhe, nessuna modifica al codice (versionCode 66 -> 67, versionName 0.9.27 -> 0.9.28).

- **Modificate:** notte var. 2 e 4 (rimossa la ripetizione di "occhiata" e il doppione "conti" con la sera), pomeriggio var. 4 (da "Controllo di metà giornata." a "Pausa e due conti?", meno meccanico), notte EN var. 4 (da "A calm look at the books." a "Two quick sums, then rest.").
- **Aggiunte:** 4 nuove varianti per ciascuna delle 4 fasce (notte, mattina, pomeriggio, sera), sia IT che EN. Registro invariato: brevi (<= ~35 caratteri, compatibili con l'header a due righe), cordiali, neutre rispetto al genere (evitati aggettivi/participi con genere).

**Decisioni:** portate a 10 varianti perché il `greetingRoll` è fissato una volta per apertura app: con 6 sole frasi un utente che apre più volte al giorno rivede in fretta le stesse. La selezione in `DashboardCards.kt` usa già `(roll * greetings.size).toInt()` con `size` letto a runtime dall'array, quindi le nuove stringhe entrano nel pool senza toccare il codice.

**Problemi:** il wrapper Gradle non ha potuto scaricare la distribuzione da github.com (403 di policy egress); usato il Gradle di sistema `/opt/gradle` (stessa versione 8.14.3) per la verifica.

**Verifica:** `gradle testDebugUnitTest lint` verde. `GreetingBandTest` verifica solo la mappatura fascia->ora, nessuna assunzione sul numero di stringhe.

---

## 2026-07-16 - Reset della storia delle migration a un baseline v1

**Fatto:** azzerata la storia delle migration Room in un unico schema baseline v1, intervento una tantum possibile perché l'app non è ancora pubblicata (versionCode 65 -> 66, versionName 0.9.26 -> 0.9.27). Nuovo ADR 23.

- **Rimosso:** gli 11 oggetti `Migration` (1->2 ... 11->12) in `Migrations.kt`, gli schemi esportati storici (`2.json` ... `12.json`), il migration test `RecurringRuleMigrationTest`. `SaldoDatabase.version` torna da 12 a 1; `1.json` rigenerato da KSP dalle entità correnti (7 tabelle: accounts, categories, transactions, tags, transaction_tag_cross_ref, recurring_rules, budgets).
- **Mantenuto:** la policy ADR (migration esplicite, versionate e testate; mai `fallbackToDestructiveMigration`) resta vincolante dai prossimi giri, e con essa l'infrastruttura: `Migrations.kt` conserva `ALL_MIGRATIONS` (ora `emptyArray()`) con il commento che spiega come aggiungere la prossima migration, `DatabaseModule` continua a iterare l'array (vuoto = nessuna migration), l'export schema (`room.schemaLocation`) e gli assets `MigrationTestHelper` restano in `build.gradle.kts`. Il test di creazione da zero `DatabaseCreationTest` (aggiunto col fix del crash) copre il percorso `onCreate` + seed.
- **Motivazione:** le vecchie migration esistevano solo per portare avanti i device di test dello sviluppatore (gli ADR 20/21/22 citano quelle trasformazioni dati). Nessun DB in produzione, quindi nessuna storia da preservare: erano solo debito. Il baseline unico elimina anche la divergenza fra schema creato da zero e schema migrato (colonne con `DEFAULT` solo via `ALTER TABLE`) che aveva già causato il crash all'avvio su installazione pulita.

**Decisioni:** baseline a version 1 (non "12 senza migration") come richiesto: numerazione pulita da capo, coerente con un progetto senza storia da mantenere. Gli ADR storici che citano le migration rimosse NON sono stati riscritti: restano registro delle decisioni prese.

**Problemi:** nessuno. Vincolo operativo noto: dopo il reset l'app apre solo su DB creato da zero; un device con un DB v2-v12 va reinstallato o svuotato. L'unico device di test era già stato azzerato (era la causa del crash originale).

**Verifica:** `./gradlew assembleDebug testDebugUnitTest lint` verde; `compileDebugAndroidTestKotlin` verde dopo la rimozione del migration test; `1.json` ispezionato (version 1, `categories` con `sortOrderIncome`).

**Prossimo:** alla prossima modifica di schema, ripristinare la trafila completa (Migration + bump + test strumentato) partendo da v1.

---

## 2026-07-16 - Fix crash all'avvio su installazione pulita

**Fatto:** risolto un crash allo splash che colpiva solo le installazioni da zero (installazione pulita, disinstalla/reinstalla, o "cancella dati"); gli aggiornamenti in-place non erano toccati (versionCode 64 -> 65, versionName 0.9.25 -> 0.9.26).

- **Causa:** `DatabaseSeedCallback.onCreate` inserisce le categorie predefinite via SQL raw (`ContentValues`) e non valorizzava la colonna `sortOrderIncome`. Quella colonna è stata introdotta con la migration 6->7 come `ALTER TABLE categories ADD COLUMN sortOrderIncome INTEGER NOT NULL DEFAULT 0`: sui DB aggiornati in-place la colonna porta con sé il `DEFAULT 0`, quindi un insert che la omette regge. Su uno schema creato da zero, invece, Room genera il `CREATE TABLE` dall'entità senza clausole `DEFAULT` (`sortOrderIncome INTEGER NOT NULL`, verificato in `schemas/.../12.json`): l'insert del seed falliva con `SQLiteConstraintException`, abortendo `onCreate`. La creazione veniva rollbackata e ritentata a ogni avvio, con crash immediato. Il bug era latente da quando `sortOrderIncome` è stata aggiunta (v0.9.18) ma invisibile perché il device di test veniva sempre aggiornato in-place.
- **Fix:** il seed valorizza `sortOrderIncome` (`put("sortOrderIncome", category.sortOrderIncome)`), con un commento che ricorda che nel percorso di creazione da zero ogni colonna NOT NULL va impostata esplicitamente (niente default SQL, a differenza delle colonne aggiunte poi via ALTER TABLE).
- **Verifica:** aggiunto il test strumentato `DatabaseCreationTest` che costruisce il `SaldoDatabase` reale con il seed callback ed esercita `onCreate` (percorso prima non coperto: i test di migrazione coprivano solo gli upgrade ALTER TABLE). Verifica che tutte le categorie predefinite siano seminate e che `sortOrderIncome` sia popolato. `./gradlew assembleDebug testDebugUnitTest lint` verde.

**Decisioni:** fix mirato sull'insert del seed invece di aggiungere `@ColumnInfo(defaultValue = "0")` all'entità: quest'ultima strada avrebbe cambiato lo schema esportato v12 (e l'identity hash) senza bump di versione, o richiesto una v13 con migration no-op, per eliminare un disallineamento di default che Room tollera. Il percorso di creazione da zero già costruisce l'intero `CategoryEntity`: valorizzare tutte le sue colonne è la correzione onesta della svista.

**Problemi:** nessuno oltre alla diagnosi.

**Prossimo:** verifica su device di un ciclo "cancella dati" -> riapertura, che ora deve arrivare all'onboarding/app invece di chiudersi.

---

## 2026-07-16 - Dettaglio del calcolo nella card Spendibile oggi

**Fatto:** la card "Spendibile oggi" ora si espande con un tap e mostra il calcolo dietro la cifra (versionCode 63 -> 64, versionName 0.9.24 -> 0.9.25). Era l'unica cifra composita dell'app la cui formula non era visibile da nessuna parte.

- **Espansione inline nella card**, non una finestra separata (decisione condivisa con l'utente): il gesto è già nel vocabolario della Dashboard e quattro righe non giustificano un bottom sheet che porti via dalla schermata. Chevron rotante nell'header, altezza animata con `animateContentSize`, stato in `rememberSaveable` (sopravvive alla rotazione, riparte chiusa a ogni apertura).
- **Scomposizione**: budget del mese, speso finora (negativo esplicito via `formatSigned`), da confermare e ricorrenze entro fine mese (righe mostrate solo se maggiori di zero: a zero non cambiano la somma), divisore e riga "Rimane per il mese" in evidenza. Numeri tabulari; nella variante rossa di superamento tutte le righe usano `onErrorContainer`.
- **Navigazione preservata**: il tap della card ora apre/chiude il dettaglio invece di andare ai budget; il percorso resta col link "Gestisci budget" in fondo al dettaglio (e la card Budget continua a navigare come prima). Nessuna modifica a dominio o ViewModel: `SafeToSpend` esponeva già tutte le gambe del calcolo.

**Decisioni:** righe a zero omesse; stato di espansione non persistito su DataStore (è curiosità momentanea, non una preferenza).

**Problemi:** nessuno.

**Prossimo:** verifica su device: espansione su entrambe le varianti (normale e superamento), rotazione a card aperta, link "Gestisci budget".

---

## 2026-07-16 - Categoria Prestiti & Finanziamenti, info budget e chiusura del filone tipi di conto

**Fatto:** chiusura del filone tipi di conto (versionCode 62 -> 63, versionName 0.9.23 -> 0.9.24), con due decisioni di prodotto condivise con l'utente.

- **Prestiti/Mutui: nessuna feature dedicata.** La rata di un prestito o di un mutuo è un movimento ricorrente in uscita, e il piano di ammortamento (quota capitale/interessi, debito residuo) è esattamente ciò che VISION esclude ("prestiti e ammortamenti"). Il caso si copre con le ricorrenze esistenti più la categoria giusta: "Affitto/Mutuo" c'era già per la rata del mutuo, e ora si aggiunge **"Prestiti & Finanziamenti"** (EN "Loans & Financing", icona `request_quote` nuova nel set, colore ciano) tra le categorie di spesa predefinite, per prestiti personali e acquisti a rate. Per i prestiti fatti da noi ad altri, i rientri usano la categoria Entrate "Rimborsi" già esistente: una categoria Entrate "Prestiti" avrebbe creato solo ambiguità.
- **Backfill sul device esistente:** il seed delle categorie gira solo alla prima apertura, quindi la nuova categoria arriva alle installazioni esistenti con la migration dati 11->12 (INSERT con guardia anti-duplicato sul nome e sortOrder in coda; nome italiano hardcoded nella migration: gli unici DB pre-v12 sono i device di test italiani dello sviluppatore, le installazioni nuove ricevono il seed localizzato).
- **Note e appunti ripulite** su richiesta: rimosse le voci Investimenti/titoli (decisione già registrata nell'ADR 22: mai un tipo di conto, la liquidità destinata a investire si traccia col Conto di risparmio, la cui descrizione in-app lo dice) e Prestiti/Mutui (coperti come sopra, nessuna feature futura). La documentazione di progetto non contiene più riferimenti a lavori che si è deciso di non fare.
- **Info "come funziona il budget" nell'editor budget:** il banner delle descrizioni dei tipi di conto è stato promosso a componente condiviso `InfoBanner` in `core/designsystem/component` e usato in fondo alla schermata Nuovo/Modifica budget. Tre frasi sulle regole altrimenti invisibili dal form: mese di calendario, spesa effettiva con rimborsi a ridurre, trasferimenti/rettifiche/conti esclusi mai contati, notifiche 80%/100%. È la feature con più regole implicite dell'app, e questa è la schermata in cui l'utente definisce il tetto: il posto giusto per spiegarle.

**Decisioni:** nome "Prestiti & Finanziamenti" nello stile dell'elenco esistente ("Bollette & Utenze"); posizione dopo "Tasse" e prima di "Altro" nel seed (in coda sul device migrato, riordinabile col drag).

**Problemi:** nessuno.

**Prossimo:** verifica su device: la categoria deve comparire nell'elenco dopo l'aggiornamento; creare una ricorrenza in uscita con la nuova categoria per una rata.

---

## 2026-07-16 - Conto di risparmio, descrizioni dei tipi, ritiro della carta di debito

**Fatto:** terzo giro sui tipi di conto (versionCode 61 -> 62, versionName 0.9.22 -> 0.9.23). Design: ADR 22. App in beta su un solo device: nessun codice legacy.

- **`DEBIT_CARD` ritirato dopo una sola release:** ammissione onesta emersa dal confronto con l'utente: un tipo la cui guida dice "probabilmente non usarmi" è un errore di tassonomia. Una carta di debito non è un contenitore di denaro (spende dal conto corrente) e nessuna app premium la modella come conto. Migration dati 10->11: le righe DEBIT_CARD diventano CHECKING; l'enum non ha più il valore. L'educazione passa alla descrizione del tipo Conto corrente ("registra qui anche le spese col bancomat...").
- **Descrizione d'uso per ogni tipo:** banner informativo sotto il selettore dei tipi, che cambia col tipo selezionato (altezza animata con `animateContentSize`). Sostituisce le due guide sparse di prima e risolve anche il difetto di layout segnalato dall'utente: la guida della carta di credito compariva sotto la valuta (viveva dentro la sezione di configurazione), ora sta sotto i Tipi dove uno la cerca. Testi per tutti e sette i tipi, IT + EN, con note operative: prelievo ATM = trasferimento (contanti), wallet senza saldo = registra sul conto, prepagata = si ricarica con trasferimenti.
- **Nuovo tipo `SAVINGS` (Conto di risparmio):** il "recinto" per i soldi messi da parte, alimentato e svuotato con trasferimenti. Icona salvadanaio predefinita. Unica opzione dedicata, ed è un default non un campo nuovo: alla selezione l'editor pre-imposta "Includi nel budget" su off (attingere ai risparmi non deve consumare il budget del mese: è il caso d'uso per cui il flag era nato, Fase 9.8). Il preset non tocca mai una scelta esplicita: toggle toccato dall'utente o valore salvato vincono sempre (`userToggledBudget`, stesso pattern di `userPickedIcon`). Niente campo "obiettivo": gli Obiettivi di risparmio restano in v2.0.
- **Investimenti / titoli: niente tipo dedicato.** VISION li esclude (quotazioni = rete, contro l'offline-first). La liquidità destinata a investimenti si traccia col Conto di risparmio e la sua descrizione lo dice. Prestiti/Mutui annotati in Note e appunti come valutazione futura (richiederebbero una revisione di VISION, che oggi li esclude per scelta).

**Decisioni:** mapping delle righe DEBIT_CARD esistenti su CHECKING (il conto corrente è la semantica reale di una carta di debito); "Conto di risparmio" come nome del tipo (standard; "Salvadanaio" resta nel lessico della descrizione). Ordine dei chip: corrente, risparmio, prepagata, credito, contanti, wallet, altro.

**Problemi:** nessuno.

**Prossimo:** verifica su device: il conto ex-bancomat deve comparire come Conto corrente dopo la migrazione; creazione di un Conto di risparmio (toggle budget pre-spento); banner descrizione che cambia con i tipi.

---

## 2026-07-16 - Tassonomia carte e review delle carte di credito

**Fatto:** secondo giro sulla gestione carte (versionCode 60 -> 61, versionName 0.9.21 -> 0.9.22): review della feature carte di credito con tre fix, saldo iniziale rimosso dalle carte di credito, tipi carta espliciti al posto di "Carta" generica. Design: ADR 21. App in test su un solo device: su indicazione dell'utente, implementazione pulita senza strati di retrocompatibilità.

- **Fix da review (1, il più rilevante):** la mappa degli estratti dovuti per conto teneva l'estratto *più recente* (`associateBy` sovrascrive), ma il settlement paga sempre il *più vecchio*: con più cicli arretrati (device spento a lungo) la CTA "Paga X" avrebbe mostrato l'importo di un ciclo pagandone un altro. Ora tiene il primo (più vecchio) per conto, con test.
- **Fix da review (2):** barra di utilizzo e CTA non compaiono più sui conti archiviati (la riga archiviata è storia, non uno strumento operativo).
- **Fix da review (3):** se il conto di addebito veniva archiviato dopo la selezione, il selettore lo escludeva e il campo si svuotava visivamente pur avendo l'id salvato. Il conto referenziato resta selezionabile anche se archiviato (stessa regola introdotta in Fase 9.7 per l'editor ricorrenze).
- **Saldo iniziale via dalle carte di credito:** il campo è nascosto nell'editor e il salvataggio forza zero. Motivo: l'importo dell'estratto somma i *movimenti* del ciclo, e il saldo iniziale non è un movimento; un "debito iniziale" sarebbe debito fantasma che nessun estratto potrebbe mai addebitare (dopo ogni saldo la carta tornerebbe al valore iniziale invece che a zero). Il percorso giusto per "ora sono a -1000" è la rettifica saldo: crea un ADJUSTMENT datato oggi, che entra nel ciclo corrente e viene addebitato col prossimo estratto. La guida nell'editor ora lo spiega.
- **Tipi carta espliciti:** rimosso il tipo generico CARD dall'enum (attrito: "la Postepay è Carta o Altro?"); aggiunti `DEBIT_CARD` (guida contestuale nell'editor: una carta di debito spende direttamente dal conto corrente, registra lì se il conto è già tracciato, conto separato solo in caso contrario) e `PREPAID_CARD` (contenitore autonomo che si ricarica con trasferimenti, il caso Postepay, modello standard delle app premium). Migration dati 9->10: `UPDATE accounts SET type = 'DEBIT_CARD' WHERE type = 'CARD'` (necessaria comunque per i conti già presenti sul device di test, l'enum non ha più il valore). Icone dedicate nel picker: `contactless` (default debito) e `add_card` (default prepagata). Banner guida generalizzato (`AccountEditorGuidance`) e riusato da credito e debito.
- **Idea annotata, non implementata** (Note e appunti): carta di debito come *alias* di un conto tracciato (registrare sulla carta registra sul conto collegato). Eliminerebbe il rischio di doppio conteggio alla radice; nessuna app premium mainstream lo fa; complica editor/filtri/statistiche, da valutare.

**Decisioni:** niente valore legacy CARD nell'enum né mapping di compatibilità nel backup (richiesta esplicita dell'utente: solo lui usa l'app, in test). La migration dati resta perché le migration esplicite sono regola di progetto e i dati sul device di test vanno preservati. Onboarding non toccato: il primo conto è sempre un conto corrente.

**Problemi:** nessuno di rilievo; il fix (1) è emerso proprio dalla review richiesta prima di iniziare le nuove implementazioni.

**Prossimo:** verifica manuale su device: migrazione dei conti esistenti di tipo Carta (devono comparire come Carta di debito), creazione carta prepagata, rettifica saldo su carta di credito e addebito col ciclo successivo.

---

## 2026-07-15 - Carte di credito a saldo (addebito differito)

**Fatto:** nuovo tipo di conto `CREDIT_CARD` per le carte ad addebito differito, con ciclo di fatturazione configurabile, addebito automatico o con conferma, indicatore di utilizzo e "paga estratto" da Dashboard/Conti. versionCode 59 -> 60, versionName 0.9.20 -> 0.9.21. Bancomat deliberatamente escluso (si registra sul conto corrente). Design: ADR 20.

- **Modello di dominio:** aggiunto `AccountType.CREDIT_CARD` e `CreditCardConfig` (giorno di chiusura estratto, giorno di addebito, conto collegato, fido opzionale, modalità auto/conferma, watermark `lastSettledClosing`). Colonne additive su `accounts` (migration 8->9, schema v9): tutte null/false per i conti esistenti. Nessuna FK su `linkedAccountId` (self-reference sulla stessa tabella: integrità gestita in logica applicativa, migrazione con sole `ADD COLUMN`). Backup: campi additivi, versione del file invariata; validazione del payload estesa ai giorni 1..31.
- **`BillingCycleCalculator` (puro):** date di chiusura/addebito con clamp sui mesi corti (chiusura 31 = ultimo giorno del mese), finestra di ciclo `[start, closing]`, rilevamento estratti scaduti (`paymentDue <= today` e non ancora saldati). Il rilevamento parte dal ciclo più recente e scorre all'indietro (il ciclo appena chiuso spesso non è ancora scaduto: si salta, non si ferma), fermandosi al primo ciclo già saldato e limitato a 24 cicli. Unit test dedicati.
- **Modellazione contabile (nessun doppio conteggio):** le spese sulla carta sono EXPENSE che portano il saldo del conto carta in negativo (il debito del ciclo) e contano nelle statistiche/budget alla data d'acquisto. L'addebito dell'estratto è **un singolo TRANSFER** dal conto collegato alla carta, che azzera il ciclo ed è escluso dalle statistiche come ogni trasferimento (ADR 8). Il saldo totale riflette il debito come patrimonio reale. L'importo dell'estratto è la negazione della somma dei soli movimenti propri del conto carta nella finestra del ciclo (nuova query DAO `sumOwnMovementsInWindow`, che esclude la gamba entrante del trasferimento di saldo).
- **Settlement:** `SettleCreditCardStatementUseCase` salda il ciclo scaduto più vecchio (trasferimento se dovuto > 0, avanzamento del watermark in un'unica transazione, mutex a livello di processo, idempotente). `ProcessDueCreditCardStatementsUseCase` auto-posta i cicli scaduti delle carte in modalità automatica e segnala (senza scrivere) quelli in modalità conferma; agganciato al worker giornaliero delle ricorrenze e al catch-up all'avvio (stessa infrastruttura, ADR 4). `CreditCardNotifier` con canale dedicato (estratto addebitato / estratto da pagare). `ObserveDueStatementsUseCase` reattivo (si ricalcola sui cambi di conti/movimenti) alimenta le CTA.
- **Watermark seminato alla creazione:** una carta appena configurata imposta `lastSettledClosing` alla chiusura precedente a oggi, così la storia pregressa non viene mai riaddebitata; solo i cicli che chiudono da lì in poi generano un estratto.
- **UI:** editor conto con sezione carta di credito (guida bancomat-vs-credito, stepper giorni, selettore conto collegato limitato alla stessa valuta e non-carta, fido opzionale, selettore modalità di addebito con segmented button); schermata Conti con barra di utilizzo (`ThresholdProgressBar`, soglia ambra all'80%, rosso oltre il fido) e CTA "Paga estratto" con importo e data; scheda Dashboard "Estratto pronto" che porta ai Conti (pattern della card "da confermare").
- **Spendibile oggi / budget:** nessuna modifica. Modellando la carta come conto a saldo negativo, gli acquisti pesano già come "speso" alla data d'acquisto e il saldo totale mostra già il debito; sottrarre di nuovo il debito dallo spendibile sarebbe stato un doppio conteggio. La visibilità dell'addebito imminente è resa dalla scheda Dashboard, non da una modifica al calcolo.

**Decisioni:** ciclo con giorno di chiusura configurabile (non solo mese di calendario) e doppia modalità di addebito (auto/conferma) selezionabile per conto, come richiesto dall'utente. Nessuna FK sul conto collegato per non complicare la migrazione self-reference. Bancomat fuori dall'app: è uno strumento che preleva subito dal conto corrente, non un contenitore di denaro (si registra direttamente sul conto).

**Problemi:** wrapper Gradle bloccato dal proxy, usata `/opt/gradle/bin/gradle`. Nessun emulatore: migration 8->9 aggiunta come test strumentato (da eseguire su device), verifica via `assembleDebug testDebugUnitTest lint detekt` (verdi) e unit test su calcolo ciclo, settlement e round-trip del mapper. Attenzione risolta in fase di test: con watermark null il calcolatore risale fino a 24 cicli storici (per questo il watermark è seminato alla creazione).

**Prossimo:** verifica manuale su device (setup carta, spesa, addebito auto/conferma, utilizzo, mesi corti). Valutabile in seguito: rilevamento automatico dell'estratto reale via import, promemoria pre-scadenza dedicato.

---

## 2026-07-15 - Avviso modifiche non salvate sul back negli editor

**Fatto:** dialog di conferma a due opzioni quando si esce da un editor con modifiche non salvate, su tutti e cinque gli editor (Conto, Movimento, Movimento ricorrente, Budget, Categoria). versionCode 58 -> 59, versionName 0.9.19 -> 0.9.20.

- **Componente condiviso `UnsavedChangesGuard`** in `core/designsystem/component`: `rememberUnsavedChangesGuard(hasUnsavedChanges, onNavigateBack)` restituisce uno stato che intercetta le due vie d'uscita (bottone X in top bar e back di sistema) e apre `DiscardChangesDialog` solo quando ci sono modifiche, altrimenti esce subito. Il `BackHandler` è abilitato solo quando `hasUnsavedChanges` è vero, così sui form intatti resta attiva l'animazione predittiva di sistema.
- **Rilevamento "dirty" nei ViewModel:** ogni editor cattura uno snapshot dei soli campi editabili (`captureBaseline()`) quando il form diventa pronto (subito in create mode, dopo il load in edit mode; per il movimento dopo la preselezione asincrona del conto di default) ed espone `hasUnsavedChanges: StateFlow<Boolean>` che confronta lo snapshot corrente col baseline. Il confronto per snapshot rende la revert-to-baseline (rimettere un campo al valore iniziale) di nuovo "pulita".
- **Dialog:** icona `WarningAmber`, titolo "Scartare le modifiche?", azione distruttiva "Scarta" (colore `error`) e "Continua a modificare". Stringhe in `values` e `values-it`.
- **Percorsi legittimi non toccati:** salvataggio, eliminazione e record mancante chiamano `onNavigateBack` direttamente, senza passare dalla guardia, quindi non mostrano mai il dialog.

**Decisioni:** dialog a due opzioni (niente "Salva" nel dialog) come da richiesta: l'editor ha già un salvataggio esplicito, il warning sul back ne è il complemento. `hasUnsavedChanges` esposto come StateFlow separato invece di essere inglobato in `uiState`, per non toccare la logica di update esistente e per uniformità (il movimento deriva `uiState` da un `combine`). Snapshot dei soli campi controllati dall'utente (esclusi `isLoading`, `showValidation`, stati dei dialog) per evitare falsi positivi. Nessun impatto su dominio, query o calcolo saldi: solo UI di navigazione.

**Problemi:** il wrapper Gradle non scarica la distribuzione dietro il proxy (403 da github). Usata la gradle di sistema `/opt/gradle/bin/gradle` (stessa 8.14.3). Nessun emulatore disponibile: verifica via `assembleDebug testDebugUnitTest lint` (verdi) e unit test mirati sul dirty (create/edit, revert-to-baseline, e il caso del conto di default preselezionato che non conta come modifica).

**Prossimo:** -

---

**Fatto:** nuovo asse di inclusione per conto, indipendente da "Includi nel saldo totale", e indicatori visivi nel breakdown della card Saldo totale (versionCode 57 -> 58, versionName 0.9.18 -> 0.9.19).

- **Flag `isIncludedInBudget`** (default `true`) sul conto: escludendolo, le spese registrate su quel conto non entrano nel consumato di budget né nello spendibile oggi. Colonna in `AccountEntity`, campo in `Account`/`AccountBackup`, mapper e migration 7->8 (`ALTER TABLE accounts ADD COLUMN isIncludedInBudget INTEGER NOT NULL DEFAULT 1`, schema Room v8). Il flag non tocca il saldo totale né le statistiche.
- **Query di spesa budget account-aware:** le quattro query in `TransactionDao` (`observe/get StatsSpendTotal`, `observe/get CategorySpendTotals`) ora fanno `INNER JOIN accounts a ON a.id = t.accountId` con `a.isIncludedInBudget = 1`. Colonne qualificate con l'alias `t.` perché `accounts` ha anch'essa `currency` e `type` (altrimenti ambigue). `ObserveSafeToSpendUseCase` esclude anche le gambe pending e ricorrenze in arrivo dei conti esclusi (nuova dipendenza `AccountRepository`, set degli id esclusi), così tutte le voci dello spendibile per un conto escluso spariscono insieme.
- **Editor conto:** `IncludeInTotalRow` generalizzata in `InclusionToggleRow` riusata due volte (saldo totale + calcolo budget). Badge testuale "Escluso dal budget" nella lista conti.
- **Indicatori card Saldo totale (`AccountBreakdownRow`):** i conti che non contribuiscono al totale hanno il saldo attenuato (`onSurfaceVariant`); marcatori solo in negazione tra nome e saldo: codice valuta ISO per i conti non-primari, icona `RemoveCircleOutline` per l'esclusione manuale dal totale, icona `MoneyOff` per l'esclusione dal budget. `contentDescription` su tutti.
- **Bug "Dashboard multi-valuta" chiuso:** i conti in valuta diversa dalla principale erano elencati nel breakdown ma esclusi dal totale, dando l'impressione che il totale non tornasse. Ora sono attenuati e marcati col codice valuta. Nessun cambio alle query (già corrette): era solo presentazione.

**Decisioni:** i due assi (totale vs budget) sono ortogonali, come confermato dall'analisi: il totale filtra per conto, budget/spendibile no (sommavano su tutti i conti della valuta principale). Scope del flag limitato a budget + spendibile, non alle statistiche, coerente col nome. Per la multi-valuta scelto il codice valuta invece di un'icona generica: spiega il motivo dell'esclusione. Attenuazione del saldo come segnale primario senza linguaggio.

**Problemi:** `assembleDebug testDebugUnitTest lint detekt` verdi; schema Room v8 esportato. Test unitari aggiornati/aggiunti (mapper e backup round-trip del nuovo campo, decode default additivo, safe-to-spend con conto escluso, editor e onboarding). Migration 7->8 e DAO budget-spend con conto escluso scritti come test strumentati, da eseguire su device (nessun emulatore in questo ambiente); la correttezza della migration è nel DDL additivo e nel default 1.

**Prossimo:** verifica su device del toggle, degli indicatori e del calcolo budget/spendibile con un conto escluso.

---

## 2026-07-15 - Uniformità UI: menu FAB a pillole, elimina in barra del titolo, sortOrder categorie per tipo

**Fatto:** tre interventi di consistenza UI/UX (versionCode 56 -> 57, versionName 0.9.17 -> 0.9.18).

- **Menu FAB (dashboard):** lo speed-dial rende ogni azione rapida (spesa/entrata/trasferimento) come un'unica pillola (icona + etichetta nello stesso contenitore arrotondato) invece della coppia staccata etichetta + mini-FAB. Colori dalla palette dell'app (`secondaryContainer`/`onSecondaryContainer`), `CircleShape`. Nessuna nuova libreria. File: `feature/dashboard/DashboardFab.kt`.
- **Elimina in barra del titolo:** gli editor Budget, Movimenti ricorrenti e Categorie spostano l'azione elimina dal bottone in fondo alla schermata a un'icona cestino tra le `actions` della `TopAppBar`, come già faceva l'editor Movimento (niente più scroll per eliminare). La logica di eliminazione (dialog di conferma/riassegnazione) era già nei rispettivi ViewModel: spostato solo il trigger. L'editor Conto resta invariato: non ha infrastruttura di delete nel suo ViewModel e l'eliminazione è già raggiungibile dal menu azioni del conto (Modifica/Rettifica/Archivia/Elimina); portarla nell'editor avrebbe duplicato il flusso di guard/riassegnazione/archivia-invece. File: `feature/{budgets,recurring,categories}/*EditorScreen.kt`.
- **sortOrder categorie per tipo:** risolto il bug noto per cui il `sortOrder` globale accoppiava i tab. Aggiunta la colonna `sortOrderIncome` (ordine manuale del tab Entrate; `sortOrder` resta quello del tab Spese). I due tab ora si riordinano in modo indipendente e una categoria "entrambi" tiene una posizione distinta in ciascuno.

**Decisioni:** per il sortOrder per tipo, una categoria "entrambi" compare in due tab e serviva una posizione per ciascuno, quindi una seconda chiave d'ordinamento invece di una sola globale. `observeByType` ordina con `CASE WHEN :type = 'INCOME' THEN sortOrderIncome ELSE sortOrder END`; `reorder` diventa per tipo e riscrive solo la chiave del tab toccato (l'update full-row porta invariata l'altra posizione); `nextSortOrder(type)` appende in coda al tab giusto; l'editor assegna la posizione del tab in cui una categoria entra (nuova o per cambio tipo). Migration 6→7: `ALTER TABLE categories ADD COLUMN sortOrderIncome INTEGER NOT NULL DEFAULT 0` + `UPDATE categories SET sortOrderIncome = sortOrder` (preserva l'ordine Entrate esistente) + indice. Backup: `CategoryBackup.sortOrderIncome` nullable, in import fallback a `sortOrder` per i backup vecchi.

**Problemi:** `assembleDebug testDebugUnitTest lint detekt` verdi; schema Room v7 esportato. Test unitari aggiornati (CategoriesViewModel decoupling per tab, editor per-type, round-trip e fallback backup). Il test di migration 6→7 e i test strumentati non girano senza emulatore, non disponibile in questo ambiente; la correttezza della migration è nel DDL (stesso pattern delle migration precedenti) e nella copia `sortOrderIncome = sortOrder`.

**Prossimo:** verifica su device del menu FAB e del riordino indipendente dei due tab quando ci sarà un emulatore.

---

## 2026-07-14 - Fix riordino categorie: gesto di drag cancellato al primo scambio

**Fatto:** corretto il drag-to-reorder in Impostazioni - Categorie, rotto su entrambi i tab (Spese ed Entrate). Trascinando una categoria, al primo scambio restava sospesa sopra le altre e non rispondeva più al dito. `versionCode` 55 -> 56, `versionName` 0.9.16 -> 0.9.17.

**Decisioni:** la causa era in `core/designsystem/component/ReorderableListState.kt`: `reorderableHandle` chiavava `pointerInput` sull'indice della riga. La lista viene riordinata live durante il drag (`onMove` muta la lista e `draggingItemIndex` diventa l'indice di destinazione), quindi al primo scambio la riga trascinata (stessa istanza, `key = category.id`) riceve un nuovo indice da `itemsIndexed`; il nuovo indice come chiave di `pointerInput` fa restartare (e quindi cancellare) il gesto `detectDragGestures` in corso, lasciando l'item bloccato a metà. Fix: `pointerInput` chiavato sull'id stabile della riga; l'indice serve solo a `onDragStart` e viene letto lazily tramite un `() -> Int` alimentato da `rememberUpdatedState`, così drag start prende sempre la posizione corrente senza restartare il gesto. La firma passa da `reorderableHandle(state, index: Int)` a `reorderableHandle(state, key, index: () -> Int)`; unico chiamante `CategoriesScreen`.

**Problemi:** il path non era mai stato esercitato su device (test UI strumentato del drag rimandato, come da voce Fase 4). Verifica: `assembleDebug testDebugUnitTest lint` verdi. Nessun emulatore disponibile per il test strumentato del gesto; la correttezza del fix è nel ciclo di vita del gesto (chiave stabile invece dell'indice mutevole).

**Prossimo:** eventuale test UI strumentato del drag quando ci sarà un emulatore.

---

## 2026-07-14 - Terza review completa: fix di correttezza, back stack per tab, allineamento premium (Fase 9.7)

**Fatto:** giro unico su branch `feat/review-fixes-premium-round3` (versionCode 54 -> 55, versionName 0.9.15 -> 0.9.16) nato da una terza review completa (5 agenti in parallelo su dominio, database, ViewModel, UI/UX, performance). Un commit per area, in ordine: sicurezza dati, correttezza, performance, navigazione, rifinitura UI.

- **Backup**: il decode valida ora enum, codici ISO 4217 e invarianti del payload (campi transfer, budget complessivo unico); un file con valuta sconosciuta veniva ripristinato e poi crashava ogni lettura a dati vecchi già eliminati. Backstop anche nei mapper dentro la transazione di restore. Test con payload ostili.
- **Conti**: il guard di eliminazione conta anche le regole ricorrenti che referenziano il conto (la FK faceva fallire la DELETE con un generico "scrittura fallita"); nuove plurals IT/EN col motivo reale.
- **Ricorrenze**: avanzamento del watermark di generazione con UPDATE mirato (l'upsert full-row poteva sovrascrivere una modifica concorrente dell'editor); l'editor tiene selezionabile il conto archiviato referenziato (prima il salvataggio falliva in silenzio); `lastReminderDate` sopravvive a un edit (niente notifiche doppie).
- **Statistiche**: il drill-down rispecchia esattamente le query statistiche quando arriva dai grafici (`statsScope` nella route: valuta principale, esclusi e pending fuori, solo righe di spesa per il tap su un conto), mentre i drill-down di cassa della dashboard restano invariati; la spesa senza categoria diventa una fetta propria dell'anello ("Senza categoria", grigio neutro), così centro del donut, lista percentuali e barre dei 12 mesi tornano a coincidere; caricamento a finestra SQL invece dell'intero registro.
- **Categorie**: la riassegnazione in eliminazione copre anche le recurring rules (prima andavano a NULL); i dialog dichiarano quando verrà eliminato anche il budget della categoria (CASCADE).
- **Date italiane**: la normalizzazione a minuscolo del commit 64eb59e estratta in `withLocaleDateCasing` e applicata a tutti i formatter con nomi di giorno/mese (registro, drill-down, editor, stats, budget, backup, onboarding, ricorrenze).
- **Fix minori**: filtro importi con min > max normalizzato allo scambio; insets doppi rimossi dall'onboarding (`consumeWindowInsets`); tre plurals IT senza `many` completate; ticker di mezzanotte condiviso che ri-ancora "oggi" su dashboard e statistiche a schermo acceso oltre la mezzanotte.
- **Perf**: `distinctUntilChanged` su tutti i flow di `UserPreferencesRepository` (ogni salvataggio movimento scriveva `lastUsedAccountId` e faceva teardown/rebuild dell'intera pipeline dashboard); `TransactionFilterEngine.compile` risolve range date e needle normalizzato una volta per passata invece che per riga a ogni keystroke; il campo di ricerca legge uno `StateFlow` sincrono invece dell'output del combine in background (niente caratteri persi digitando veloce).
- **Navigazione (il cambio più visibile)**: back stack per tab con il pattern Nav3 "multiple back stacks", verificato sui sorgenti 1.1.4 scaricati da Maven (niente API a memoria). Ogni tab conserva ViewModel, scroll, ricerca e periodo tra un cambio e l'altro: spariscono lo skeleton e le ri-query a ogni rientro. Comportamento back invariato (exit through home); tab selezionato persistito per nome enum.
- **UI premium**: `contentColorOn(color)` nel design system sceglie bianco/quasi-nero per luminanza sui colori pieni (fix contrasto su lime, verde chiaro, ambra, azzurro in avatar, righe stats e picker); skeleton allineati alla geometria reale (raggio 16dp, ritmo 8dp) e nuovo `StatsSkeleton` (ultimo spinner dei 4 tab); righe switch di Impostazioni toggleabili sull'intera riga con singolo focus TalkBack; rimosso l'ultimo chevron (hero Budget) e l'ultimo avatar circolare (picker conti dell'editor); crossfade breve sullo swap barra/ricerca dei Movimenti; lo speed dial non persiste più lo stato aperto.

**Decisioni:** idee scartate della review confermate scartate (gradienti, ombre, count-up sugli importi, chevron "per affordance"); transizioni di schermata invariate: lo scatto percepito era in gran parte il flash dello skeleton al cambio tab, ora eliminato alla radice dai back stack per tab; se dopo la prova su device si volesse riprovare lo stile espressivo, è un cambio piccolo e isolato. La spesa "senza categoria" entra nell'anello invece di allargare il totale centro-donut a una query separata: ogni cifra della schermata resta la somma visibile delle sue parti. Stato dei form su process death (SavedStateHandle) valutato e rimandato: cambieria tutti gli editor per un caso raro, non nel percorso della v1.0.

**Problemi:** nessun ambiente di build locale in questa sessione: verifica di correttezza fatta su firme reali (sorgenti Nav3 1.1.4 e lifecycle 2.10 scaricati da Google Maven) e sweep di riferimenti orfani; la validazione finale è demandata alla CI GitHub (build + lint + unit test + detekt) e all'APK di prova sul device dell'utente.

**Verifica:** unit test nuovi o aggiornati per payload backup ostili, guard eliminazione conto, watermark ricorrenze (mirato + reminder), drill-down stats-scoped e fetta senza categoria, casing date, ticker mezzanotte, stato di navigazione per tab. Da eseguire in CI al push.

**Prossimo:** push del branch, CI verde, prova su device (in particolare: cambio tab senza skeleton, drill-down coerenti, contrasto avatar sui colori chiari); poi Fase 10 (release v1.0).

---

## 2026-07-14 - Rifinitura premium: app shortcut, skeleton, ricorrenze immediate, perf (Fase 9.6)

**Fatto:** giro di rifinitura "da app premium" guidato dall'utente su una seconda review completa, iterato su APK di prova da GitHub (versionName 0.9.11 -> 0.9.15, versionCode 50 -> 54; un commit per intervento più i fix). Nessuna modifica a dominio o schema.

- **App shortcut statici** dal launcher: Nuova spesa, Nuova entrata, Trasferimento. Icone adattive on-brand (glifo bianco a freccia su sfondo brand rosso/verde/teal, `adaptive-icon` in `drawable-anydpi` come il launcher del progetto), label brevi + lunghe IT/EN. Intent instradato in `MainActivity` (`singleTop` + `onNewIntent`) verso il back stack Nav3 tramite un `MutableStateFlow` consumato una tantum in `SaldoApp`: apre l'editor giusto a freddo o a caldo, senza ri-trigger su rotazione.
- **Skeleton di caricamento**: `DashboardSkeleton` (ricalca il layout reale) e `ListSkeleton`, un solo pulse condiviso per schermata, al posto dello spinner su Dashboard, Movimenti, Conti, Budget.
- **Ricorrenze immediate**: al salvataggio di una regola parte la generazione catch-up in application scope (idempotente, mutex-guarded), così un'occorrenza già scaduta compare subito nel registro senza attendere avvio o worker.
- **Perf**: `MoneyFormatter` mette in cache il `NumberFormat` per `(valuta, locale)` con `ThreadLocal`; catch-up e parsing intent shortcut solo all'avvio genuino (`savedInstanceState == null`), non a ogni ricreazione da cambio configurazione.
- **SwipeToDismiss** del registro migrato fuori da `confirmValueChange` (deprecato) a osservazione dello stato via `snapshotFlow`.

**Decisioni:**
- **Transizioni di schermata**: esplorato uno stile "espressivo" (scale shared-Z + tuning), poi ripristinato l'originale slide + fade dopo verifica su device dell'utente. Parere condiviso: lo scatto residuo è di build debug e assenza di baseline profile, non dello spec animazione. Il baseline profile resta tracciato in Fase 10 (richiede modulo macrobenchmark e run su device).
- **Label shortcut** azionate ("Nuova spesa/entrata") perché invocate fuori dall'app senza il contesto del "+"; il FAB resta a nomi asciutti. "Trasferimento" senza "Nuovo" (il prefisso suona pesante in italiano e un trasferimento è un'operazione, non un'entità creata); label lunga "Trasferimento tra conti". Decisioni condivise con l'utente.
- **SwipeToDismiss**: osservare lo stato assestato invece di vietarlo in callback è la migrazione idiomatica (la direzione è già ristretta da `enableDismissFromStartToEnd = false`); bonus, un trascinamento breve che rimbalza non elimina più per errore.
- **Multi-valuta dashboard**: la review ha notato che la card "Saldo totale" somma solo i conti nella valuta principale mentre il breakdown li elenca tutti; non è un errore di calcolo ma la presentazione è ambigua. Non fixato in questo giro (scelta di prodotto), registrato in Bug conosciuti.

**Problemi:** CI rossa una volta su `detekt` (`LongParameterList`: il costruttore del ViewModel ricorrenze è passato a 7 parametri con i nuovi collaboratori). Fix con `@Suppress` coerente con gli altri ViewModel injected-heavy (`DashboardViewModel`, `TransactionEditorViewModel`), commit 014a8dc. In locale avevo validato con `-x lint` ma senza `detekt`: da includere sempre nella verifica pre-push.

**Verifica:** compile + `detekt` in locale (Gradle di sistema in `/opt/gradle`, il wrapper è bloccato dal proxy); CI GitHub verde (build + lint + unit test + detekt + upload APK) su ogni commit finale; `RecurringRuleEditorViewModelTest` aggiornato ai nuovi parametri e verde. APK di prova validato su device reale dall'utente (shortcut, skeleton, ricorrenza scaduta oggi, transizioni). PR #17.

**Prossimo:** Fase 10 (release v1.0). Da valutare: fix presentazione dashboard multi-valuta e baseline profile.

---

## 2026-07-13 - Rifinitura Dashboard: card omogenee, drill-down dei periodi, fix data italiana

**Fatto:** giro di rifinitura UI/UX della Dashboard guidato dall'utente, iterato su APK di prova generati da GitHub (versioni 0.9.6 -> 0.9.11, versionCode 45 -> 50, un commit per iterazione).

- **Card omogenee**: tutte le card standard ora condividono la stessa intestazione, estratta nel composable `DashboardCardHeader` (icona 24dp in tinta primary, titolo `titleMedium`, slot opzionale in coda usato dalla data del saldo). Prima convivevano tre stili diversi (label piccola sul saldo, icona 20dp grigia sui ricorrenti, icona colorata sul budget). Icone assegnate: portafoglio (Saldo totale), banconote (Spendibile oggi, che passa a `onErrorContainer` in sforamento), calendario-giorno (Oggi), calendario-mese (mese corrente), salvadanaio (Budget, invariata), ripetizione (Movimenti ricorrenti).
- **Card Budget sempre visibile**: senza budget mostra un invito con lo stesso pattern dell'empty state dei ricorrenti ("Nessun budget, tocca per aggiungerne uno") invece di sparire; resta soggetta al toggle di visibilità in Impostazioni. Fa anche da punto d'ingresso per scoprire "Spendibile oggi", che senza budget complessivo non compare.
- **Tutte le card tappabili, niente chevron**: le card Oggi e mese corrente aprono i movimenti filtrati sul rispettivo intervallo (riuso di `FilteredTransactionsRoute`, la stessa schermata drill-down delle statistiche). Con ogni card che apre il proprio dettaglio il chevron non discrimina più nulla: rimosso da tutte (saldo, spendibile, budget, ricorrenti, da confermare).
- **Data sulla card saldo**: stile `bodyMedium` attenuato (da `titleMedium`: è un metadato, non deve competere con il titolo) e minuscolo forzato per la locale italiana.
- **Impostazioni > Dashboard**: le tre voci iniziano tutte con "Scheda" (EN: suffisso "card") e seguono l'ordine delle card in Dashboard (spendibile, budget, ultimi movimenti).
- **Statistiche**: il segmento del periodo personalizzato in italiano passa da "Personalizzato" a "Date": con la spunta di selezione Material il testo lungo sbordava dalla cella del `SegmentedButton`.

**Decisioni:** "Spendibile oggi" è stata provata sotto la card Budget (coerenza col dato di origine) e poi riportata sotto il saldo: da provata su device, la posizione hero per la cifra più azionabile vale più del raggruppamento logico (decisione utente, condivisa). Stessa sorte per l'elevazione della card saldo: provata a 2dp e 3dp, poi rimossa; la gerarchia la fanno già il colore tonale più alto (`surfaceContainerHigh`), la forma `extraLarge` e la cifra in `displaySmall`, e la schermata piatta è più coerente col resto (decisione utente, condivisa: in dark theme l'ombra era comunque quasi invisibile). Drill-down dei periodi verso i movimenti filtrati e non verso le statistiche: le stats non hanno vista giornaliera e la domanda dietro l'aggregato è "quali movimenti lo compongono". La riga di confronto col mese scorso resta fuori dalle card (valutata l'integrazione nella card mese, scartata: troppo compressa).

**Problemi:** su alcuni device la data estesa italiana compariva con iniziali maiuscole ("Lunedì 13 Luglio"): alcune build OEM di ICU applicano ai nomi di giorni e mesi la capitalizzazione da contesto standalone. Fix con normalizzazione esplicita a minuscolo per la locale italiana in `fullWeekdayDate` (commit 64eb59e), registrato in Bug conosciuti.

**Verifica:** `testDebugUnitTest` e `lint` verdi a ogni iterazione; `assembleDebug` e `detekt` verdi sul commit finale. Prova visiva su device reale da parte dell'utente per ogni iterazione (APK da GitHub, update in place grazie al keystore condiviso e al bump di versione).

**Prossimo:** Fase 10 (release v1.0).

---

## 2026-07-12 - Fase 9.5: budget, spendibile oggi e dashboard configurabile (anticipo dalla v1.5)

**Fatto:** anticipati dalla Roadmap v1.5 (decisione utente; il Widget resta in v1.5) budget completi e "Spendibile oggi", più la configurabilità delle card della dashboard proposta dall'utente. Cinque commit di implementazione, gate `assembleDebug testDebugUnitTest lint detekt` verde per ognuno.

- **Data layer** (0.9.1): tabella `budgets` (migration 5->6, DDL copiata dallo schema esportato da Room), un budget complessivo opzionale (categoryId NULL) + budget per categoria (unique index, FK CASCADE), valuta esplicita per riga, watermark notifiche come epoch month. Unicità del complessivo via upsert transazionale (l'UNIQUE di SQLite non vincola i NULL). Nuove query di spesa stats-consistent (totale e per categoria, filtro `EXPENSE OR (INCOME AND isRefund)`: nelle categorie "entrambi" le entrate pure non riducono il consumato). Backup: campo additivo `budgets` nello schema version 1, watermark inclusi, conteggio nell'anteprima restore.
- **Schermata Budget + editor** (0.9.2): hero card del complessivo (residuo grande, barra animata, speso/limite, percentuale) o CTA se assente; categorie ordinate per vicinanza al tetto con avatar squircle. Editor con scope picker a chip (complessivo se non esiste, categorie di spesa senza budget), importo con tastiera di sistema nella valuta principale, eliminazione con conferma; la modifica preserva id e watermark. `ThresholdProgressBar` condivisa nel design system; ruolo `warning` ambra (light/dark) aggiunto a `MoneyColors`. Soglie 🟢<80% 🟡80-99% 🔴>=100% in aritmetica intera sui minor units.
- **Card dashboard + notifiche** (0.9.3): card Budget (complessivo + top 3 categorie) tra confronto mese e da confermare; `CheckBudgetThresholdsUseCase` idempotente con watermark mensili, invocato dal worker giornaliero e dal nuovo `BudgetThresholdWatcher` (application scope, segnali di spesa debounced 500ms): una notifica per soglia per mese per budget, superamento diretto del 100% = un solo avviso, nessun riarmo se la spesa rientra. Canale `budget_alerts`, id 1004/1005.
- **Spendibile oggi** (0.9.4): card sotto il saldo = budget - spesa statistica - pending del mese - ricorrenze a importo fisso in arrivo entro fine mese (`UpcomingChargesCalculator` puro, stesso floor di generazione della dashboard). Numero grande = quota giornaliera (FLOOR alla scala valuta), righe secondarie con residuo mese e impegni; superamento su `errorContainer` con icona e testo. Sottigliezza pending gestita: un'occorrenza generata ma non confermata è fuori sia dalla spesa sia dall'upcoming (floor), quindi è contata una sola volta come `pendingCommitted`.
- **Dashboard configurabile** (0.9.5): sezione "Dashboard" in Impostazioni con 3 switch (Spendibile oggi, scheda budget, ultimi movimenti), boolean DataStore default visibile; le card core restano fisse. La combine della dashboard, già al limite tipizzato di 5 flussi, è stata annidata (sources collassate + budget/safe-to-spend/prefs sopra).

**Decisioni:** ADR 18 (modello budget e spesa stats-consistent) e ADR 19 (trigger notifiche doppio con watermark); anticipo pre-release così la tabella nasce nello schema di produzione e il backup resta a version 1; card Budget senza teaser (chi non usa i budget non la vede); l'editor non permette di cambiare scope a un budget esistente (eliminare e ricreare, preserva i watermark).

**Problemi:** scala BigDecimal nel calculator (0 vs 0.00 nei fold: risolto saltando le regole senza occorrenze); detekt su soglie TooManyFunctions/LongParameterList: soppressioni motivate nello stile del progetto.

**Verifica:** unit test JVM nuovi: `BudgetMapperTest`, `ObserveBudgetProgressUseCaseTest` (soglie esatte 79.99/80/100, rimborsi, ordinamento), `CheckBudgetThresholdsUseCaseTest` (dedupe watermark, riarmo mese successivo), `UpcomingChargesCalculatorTest` (floor, mesi corti, settimanali), `ObserveSafeToSpendUseCaseTest` (pending una sola volta, FLOOR, ultimo giorno mese); estesi `BackupCodecTest`/`BackupMapperTest`/`DashboardViewModelTest`. Strumentati scritti ma da eseguire su device (nessun emulatore): migration 5->6, `BudgetDaoTest` (unique, CASCADE, watermark mirati), `TransactionDaoBudgetSpendTest`. Da verificare su device anche: notifiche soglia reali, resa delle card in light/dark e con dynamic color.

**Prossimo:** Fase 10 (release v1.0).

---

## 2026-07-12 - Fase 9 completata: impostazioni, onboarding, i18n, accessibilità, performance (+ fix marker grafici)

**Fatto:** fase implementata in 8 step, un commit per step, gate `assembleDebug testDebugUnitTest lint detekt` verde per ognuno.

- **Fix marker grafici** (0.8.8): nei grafici "Spese" ed "Entrate e uscite" il bottone "Vedi i movimenti di <mese>" spariva al rilascio del dito, perché Vico nasconde il marker al touch-up e il listener azzerava la selezione in `onHidden`. Ora la selezione resta e il bottone è cliccabile. Segnalato dall'utente, aggiunto ai bug conosciuti già spuntato.
- **Valuta principale** (0.8.9): preferenza esplicita in Impostazioni > Preferenze che sovrascrive la regola a maggioranza; assente = automatica (retrocompatibile). Consumata in modo uniforme da dashboard, statistiche e hub ricorrenze. Lista valute estratta in `CurrencyCatalog` condiviso (editor conti, Impostazioni, onboarding).
- **Conto predefinito** (0.8.10): preselezione dell'editor movimenti con ordine default esplicito -> ultimo usato -> primo attivo; default archiviato/eliminato ignorato.
- **Primo giorno settimana + preset "Questa settimana"** (0.8.11): segmented Lun/Sab/Dom, default dalla locale (coercizione a Lunedì per locale non offerte, es. venerdì); il preset nel registro è il primo consumatore reale. `dateRange`/`matches` del motore filtri prendono il week start come input esplicito e restano funzioni pure testate (confini di mese/anno, oggi = primo giorno).
- **Onboarding premium** (0.8.12): 5 pagine con `HorizontalPager` (benvenuto, privacy, valuta, primo conto, notifiche), avanzamento solo via CTA, back di sistema torna indietro di pagina, copy caldo e non tecnico IT+EN. Il primo conto si crea inline (nome prefill + saldo, `MoneyInput`); "Ho già un backup di Saldo" riusa `ImportBackupUseCase` con conferma amichevole e salta la creazione conto. Gate in `MainViewModel`: flag `onboarding_completed`; se assente ma il DB ha conti (installazione esistente) viene marcato in silenzio e si apre l'app; qualunque errore di lettura apre l'app. Rimosso il cold-ask del permesso notifiche all'avvio: ora è chiesto solo in contesto (pagina onboarding, o attivando il radar rinnovi in Impostazioni). Non ripristinare il cold-ask.
- **Revisione stringhe + audit stati** (0.8.13): IT uniformato su "conto/conti" per l'entità (il misto account/conto stonava e "account" in italiano evoca la registrazione online, in conflitto con "senza registrazione"); EN uniformato su "transaction" dove compariva "movement". Parità chiavi verificata (le sole differenze sono `translatable="false"`), nessuna stringa hardcoded. Empty/loading già coperti ovunque; aggiunta la gestione errori di scrittura (snackbar riutilizzando `editor_write_failed`) alle azioni dei list screen che prima potevano crashare su un errore Room: conti (archivia/ripristina/elimina/rettifica), registro (elimina/undo), da confermare (conferma/salta), riordino categorie (silenzioso: la lista torna all'ordine persistito).
- **Accessibilità** (0.8.14): riassunti TalkBack sui 4 grafici Vico (canvas muto) via `semantics`; `EditorSaveButton` e CTA onboarding a `heightIn(min)` per il font scaling 200%; audit contentDescription (tutti gli interattivi a sola icona coperti), segni espliciti accanto ai colori del denaro, merge semantico delle righe cliccabili. Verifica manuale TalkBack/200% su device: pending.
- **Performance + chiusura** (0.9.0): registro appiattito da un item monolitico per giorno a item lazy per riga con key/contentType stabili; l'aspetto a card raggruppata è ricostruito con forme a segmento (prima/ultima riga arrotondate) e il clip contiene anche lo sfondo dello swipe-delete. Conti/categorie/ricorrenze restano a card unica (liste corte, non serve). Paging3 non introdotto: filtri e ricerca sono in-memory per design; baseline profile spostato in Fase 10.

**Decisioni:** vedi sopra (override valuta con fallback a maggioranza; ordine di preselezione conto; coercizione week start; gate onboarding fail-open; permesso notifiche solo contestuale; terminologia IT "conto"; niente Paging3; baseline profile in Fase 10 - concordato con l'utente).

**Problemi:** Vico non offre una selezione persistente nativa (marker legato al gesto), risolto tenendo l'ultimo indice lato app. MockK non intercetta i default argument (`GenerateRecurringMovementsUseCase()` esplodeva sul `clock` del mock): l'onboarding passa `today` esplicito. Detekt: soppressioni motivate per `LongParameterList`/`TooManyFunctions` seguendo lo stile del progetto.

**Verifica:** unit test JVM nuovi/estesi: `MainViewModelTest` (gate), `OnboardingViewModelTest` (conto, restore, doppio tap), `TransactionFilterEngineTest` (settimana), override valuta in Dashboard/Stats/RecurrencesViewModelTest, preselezione in TransactionEditorViewModelTest. Da verificare su device: onboarding da APK pulito, update in place senza onboarding, bottone drill-down, preset "Questa settimana", TalkBack/200%, resa visiva del registro a segmenti.

**Prossimo:** Fase 10 (release v1.0): QA manuale, baseline profile, icona/screenshot/scheda Play Store, privacy policy, firma release.

---

## 2026-07-11 - Icona app: iterazioni e design definitivo (portafoglio bicolore)

**Fatto:**
- Serie di iterazioni sul disegno dell'icona partendo dalla versione 0.8.2 (carte orizzontali che sbucano a destra), valutate su render di anteprima (full, mascherata a cerchio, 72px) prima di toccare gli XML:
  - 0.8.3 (versionCode 27): carte a ventaglio che sbucano dal bordo superiore, gialla a sinistra e rossa a destra (±15°), più piccole del portafoglio.
  - 0.8.4 (versionCode 28): carte orizzontali con perno di rotazione sull'angolo alto a sinistra (gialla dietro a -18°, rossa davanti a -8°), centrate orizzontalmente sul portafoglio.
  - 0.8.5 (versionCode 29): carte più grandi e più basse, riga bianca spostata al 70% dell'altezza, inclinazione della gialla ridotta a -14° per pareggiare la sporgenza a destra.
  - 0.8.6 (versionCode 30): rossa abbassata di 4px per un ventaglio più scalato, gialla invariata.
- **Design definitivo** (0.8.7, versionCode 31), su carta bianca dopo che le varianti con carte ruotate continuavano a produrre squilibri ottici:
  - Geometria allineata agli assi, niente rotazioni: le due carte sbucano dritte dal bordo superiore ad altezze sfalsate (gialla `#FBBC04` dietro a sinistra, rossa `#EA4335` davanti a destra).
  - Portafoglio bicolore: corpo blu `#1A73E8` + taschino frontale blu scuro `#1557B0` con cucitura dritta, al posto della riga bianca a pillola.
  - Bottone di chiusura verde `#34A853` con punto bianco sulla cucitura, a destra: completa la palette Google a quattro colori.
  - Raggi coerenti (10 corpo, 4 carte), wrapper con scala 0.84 e nudge verticale -3 per centratura ottica nel frame.
- Monochrome (temi Android 13+) riallineato alla silhouette a ogni iterazione, con taschino e bottone resi tramite `fillAlpha`.
- Aperta la PR #13 per la review; build di verifica delegata al workflow GitHub.

**Decisioni:** lo stile Google flat privilegia forme allineate agli assi; le carte inclinate rendevano la composizione sensibile a ogni ritocco (una carta sembrava sempre più grande o fuori asse). Il taschino bicolore comunica "portafoglio" meglio della riga bianca, che a colpo d'occhio faceva sembrare il corpo una carta di credito.

**Verifica:** per questa serie di modifiche la compilazione locale è stata saltata su richiesta (build via workflow GitHub sulla PR). Controllo visivo su render generati da script Python/cairosvg che replica le trasformazioni dei vector drawable (full, mascherata a cerchio, 72px).

**Prossimo:** merge della PR #13, poi Fase 9 (impostazioni, i18n, rifinitura).

---

## 2026-07-11 - Icona app ridisegnata in stile Google

**Fatto:**
- Ridisegno dell'icona adattiva (`ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `ic_launcher_background`).
- **Sfondo**: da teal `#00696B` a bianco `#FFFFFF`, per la resa "in stile Google" (glifo colorato su fondo bianco).
- **Colori più saturi e vivaci**: portafoglio blu `#1A73E8`, carte gialla `#FFC400` e rossa `#EA4335`.
- **Disegno**: le due carte non sbucano più verticali dall'alto ma sono orizzontali e sbucano a destra da dietro il corpo del portafoglio (gialla in alto, rossa in basso, con leggero ventaglio di ±6°). Il portafoglio mantiene lo stesso stile (corpo bifold arrotondato con riga di cucitura a pillola), ora blu con la riga bianca `#FFFFFF`.
- Il monochrome (temi Android 13+) resta allineato alla nuova silhouette.
- Versione a 0.8.2 (versionCode 26).

**Decisioni:** colori scelti dalla palette Google (blu/giallo/rosso) ma con toni più saturi come richiesto; artwork racchiuso in un gruppo con scala 0.86 e leggera correzione verticale per margine dal bordo maskable e centratura ottica.

**Verifica:** `gradle assembleDebug testDebugUnitTest lint` verdi; anteprima renderizzata (full e mascherata a cerchio) per controllo visivo. Nessun test toccato (modifiche solo a risorse drawable/colori).

**Prossimo:** Fase 9 (impostazioni, i18n, rifinitura).

---

## 2026-07-11 - About: tagline in linguaggio utente e lista librerie ridotta

**Fatto:**
- **Tagline**: "Monitoraggio delle spese offline-first e privacy-first." era gergo da addetti ai lavori; sostituita con la frase di prodotto già usata come headline in README/VISION: "Capire dove vanno i soldi, in modo chiaro e immediato." (EN: "Understand where your money goes, clearly and instantly.").
- **Librerie open source**: da 10 voci a 2. Restano itemizzate solo le non-Google (Kotlin & kotlinx di JetBrains: linguaggio, coroutine, serializzazione JSON; Vico: grafici); lo stack Google è accreditato in blocco da una riga di chiusura ("Oltre alle librerie Android Jetpack e Material Design di Google."). Una pagina About è un credito, non un inventario; nessuna licenza Apache-2.0 richiede l'elenco puntuale.
- Versione a 0.8.1 (versionCode 25).

**Verifica:** `gradle assembleDebug testDebugUnitTest lint detekt` verdi; nessun test toccato (modifiche solo a stringhe e layout della card). Resa della card librerie da confermare su device.

**Prossimo:** Fase 9 (impostazioni, i18n, rifinitura).

---

## 2026-07-11 - Fase 8 completata (parte locale): backup su file, restore guidato, export CSV

**Fatto:**
- **Ridefinizione della fase (decisione di prodotto)**: la Fase 8 copre solo il backup locale; Google Sign-In, upload su Drive App Data con rotazione, backup automatico WorkManager e restore al primo avvio sono spostati in una "Fase cloud" in fondo alla roadmap, da valutare a fasi concluse (nuovo ADR 17). Il restore proposto al primo avvio passa all'onboarding di Fase 9.
- **Formato backup JSON versionato** (`core/domain/backup`): documento con marker `format: "saldo-backup"`, `version: 1`, istante di export e versione app; payload con tutte le tabelle (account, categorie, tag, ricorrenze, movimenti, assegnazioni tag). Schema di soli tipi primitivi (importi in unità minori, date epoch, enum come nomi), separato sia dalle entity Room sia dai modelli di dominio: rinominare una colonna non può cambiare il file. Decodifica con `ignoreUnknownKeys` (un campo aggiunto in futuro non rompe i vecchi APK) ed errori tipizzati: file estraneo, versione più recente, file corrotto, ognuno con il suo messaggio in UI. Encoding pretty-printed: il backup è ispezionabile con un editor di testo, coerente con privacy-first.
- **Snapshot e restore atomici** (`RoomBackupRepository` + DAO `getAll`/`insertAll`/`deleteAll`): lettura e sostituzione dell'intero database in una singola transazione; ordine delle tabelle rispettoso delle foreign key, id preservati (i riferimenti incrociati del file restano validi), rollback su qualsiasi errore, quindi un file malformato non tocca i dati correnti.
- **Use case**: `ExportBackupUseCase` (snapshot -> documento + riepilogo conteggi) e `ImportBackupUseCase` in due passi, `inspect` (validazione e riepilogo senza scritture) e `restore`.
- **Schermata Backup** (`feature/backup`, Impostazioni > nuova sezione "Dati"): hero card privacy ("I tuoi dati restano tuoi"), export via SAF `ACTION_CREATE_DOCUMENT` con nome proposto `saldo-backup-YYYY-MM-DD.json` e data dell'ultimo backup persistita in DataStore, avvertenza "file non cifrato" come da piano; ripristino guidato via `ACTION_OPEN_DOCUMENT` con dialog di conferma che mostra data, versione app e conteggi per tabella prima della sostituzione (avviso di irreversibilità in colore error). Dopo il ripristino parte subito il catch-up delle ricorrenze, così un backup vecchio di giorni non aspetta il prossimo avvio. Scritture su `Dispatchers.IO` (nuovo qualifier `@IoDispatcher`), stream SAF aperto in modalità "wt" (troncare un documento riscritto, mai lasciare code del contenuto precedente).
- **Export CSV dal registro** (`feature/transactions/export`): icona di condivisione nella top bar dei Movimenti apre uno sheet con conteggio dei movimenti della vista corrente (filtri e ricerca applicati), scelta del separatore `;`/`,` persistita e bottone "Esporta e condividi". Convenzione decimali abbinata al separatore (`;` -> virgola, per Excel in italiano; `,` -> punto), BOM UTF-8, escaping RFC 4180, colonne per entrambe le gambe dei trasferimenti multi-valuta e tag riuniti in un campo. File scritto in `cache/exports/` ed esposto via FileProvider al Share Sheet: nessun permesso di storage.
- **Icone hub Ricorrenze**: il tab Uscite usava ancora `Subscriptions` (empty state) ed `EventRepeat` (card proiezione annua); ora la coppia è `TrendingDown` (Uscite) / `TrendingUp` (Entrate) in entrambi i punti, lo stesso linguaggio visivo del confronto mese della dashboard.
- Versione a 0.8.0 (versionCode 24).

**Decisioni:**
- Enum salvati nel file come stringhe con `valueOf` esplicito al restore: un nome sconosciuto fallisce dentro la transazione (rollback) invece di indovinare.
- Le preferenze (tema, promemoria, separatore CSV) restano fuori dal backup: sono comodità di device, non dati; perderle non perde informazione monetaria.
- CSV pensato per i fogli di calcolo, non per il re-import (quello è il JSON): header localizzati e date ISO del giorno locale del movimento (ADR 7).
- Validazione del restore in due passi (inspect -> conferma -> restore): l'utente vede cosa sta per sostituire i suoi dati prima di qualsiasi scrittura.

**Verifica:** `gradle assembleDebug testDebugUnitTest lint detekt` verdi (Gradle di sistema, wrapper bloccato dal proxy come da nota). 226 unit test JVM (0 falliti), 34 nuovi: `BackupCodecTest` (round-trip, chiavi sconosciute, default per campi mancanti, file estraneo/array/versione futura/corrotto, riepilogo), `BackupMapperTest` (identità entity->schema->entity campo per campo, enum sconosciuto che fallisce), `BackupRoundTripTest` (export->import senza perdita con il codec reale in mezzo, timbro clock/versione, inspect in sola lettura), `TransactionCsvBuilderTest` (separatori/decimali, BOM, quoting, trasferimento multi-valuta, tag, giorno locale). Da verificare su device: flusso SAF completo di export/restore e resa della schermata Backup in chiaro/scuro.

**Prossimo:** Fase 9 (impostazioni, i18n, rifinitura).

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
