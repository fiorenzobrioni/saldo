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

## 2026-07-26 - Cornice pari, icona dell'app opzionale, e sempre "Aggiorna"

**Fatto:** tre rifiniture dopo lo screenshot del formato a riga. Margine uniforme e piu largo attorno ai due bottoni, icona dell'app opzionale alla loro destra per aprire Saldo, e l'azione della configurazione che dice sempre "Aggiorna il widget".

**Decisioni:** il padding smette di essere una costante globale e diventa un campo di `WidgetLayout`, perche i due stili hanno vincoli opposti: la griglia ha un budget verticale da difendere e continua a spendere meno in alto e in basso, la riga non ce l'ha e prende 14dp pari sui quattro lati. Era la scelta giusta anche a prescindere dalla segnalazione - una cornice sbilenca attorno a due bottoni grandi e la prima cosa che l'occhio nota, mentre in una griglia fitta non si vede. Sull'icona dell'app: nessuno sfondo proprio, perche l'icona e gia una forma e un colore e un terzo bottone tinto accanto ai due che contano competerebbe con loro; di default e spenta, perche i formati piu alti hanno gia la tile "Apri Saldo" e la riga e la dimensione dove ogni elemento deve guadagnarsi la larghezza. Un limite dichiarato: l'utente l'ha chiesta quadrata, con il lato pari all'altezza dei bottoni, e l'altezza si ottiene con `fillMaxHeight`, ma la larghezza no - `SizeMode.Responsive` riporta il bucket che ha fatto match e non la dimensione reale del widget, quindi l'altezza effettiva non e conoscibile in composizione e il quadrato esatto non e calcolabile. La larghezza e fissa a 52dp, che a un'altezza di riga tipica legge quadrata. Sul testo dell'azione l'utente ha ragione e la motivazione e piu pulita di quella che avevo implementato: quando la schermata si apre il launcher **ha gia creato** il widget, quindi anche la prima visita sta modificando qualcosa che esiste. Il marcatore `Configured`, che serviva solo a distinguere i due casi, e stato rimosso invece di restare come stato morto.

**Problemi:** nessuno. L'anteprima nella configurazione mostra anche l'icona quando l'interruttore e acceso, altrimenti l'unico controllo nuovo sarebbe stato l'unico a non avere riscontro visivo nel posto costruito apposta per darlo.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` verde, 645 test, 0 falliti. `QuickAddWidgetPrefsTest` perde il caso sul marcatore rimosso e guadagna quello sull'icona (spenta finche non la si chiede, e round-trip). versionCode 134 -> 135, versionName 0.9.95 -> 0.9.96.

**Prossimo:** prova su device del margine pari a una riga e dell'icona accesa - in particolare se i 52dp fissi leggano davvero quadrati all'altezza di riga di questo launcher, che e l'unica cosa che non ho potuto calcolare.

---

## 2026-07-26 - Il widget a una riga: due bottoni e basta

**Fatto:** su richiesta dell'utente il widget si ridimensiona fino a una riga di launcher, e a quell'altezza cambia natura: niente griglia, due soli bottoni Spesa ed Entrata che si dividono la larghezza e crescono con le colonne. Rosso e verde tenui, icone `TrendingDown`/`TrendingUp`, e il tap apre la stessa sheet dell'importo che l'utente ha detto di apprezzare.

**Decisioni:** la struttura chiave e che `layoutFor` decide **prima sull'altezza**: un widget alto una riga puo essere largo due colonne o cinque, e nessuna di quelle larghezze regge una griglia, quindi la larghezza li non ha voce. I bottoni usano `defaultWeight()`, percio il ridimensionamento in colonne e gratis e non serve un bucket per ogni larghezza. Sul colore c'e una deroga dichiarata: `MoneyColors` tiene `expense` neutro di proposito, documentando che colorare ogni spesa in un registro urlerebbe e che segno e icona portano la distinzione. Qui pero non c'e un registro: due bottoni soli su un widget non hanno altro contesto con cui essere letti, e il colore e il discriminante piu rapido. La deroga e stata segnalata all'utente prima di scriverla, ed e comunque parziale - le icone restano, perche la regola di accessibilita del progetto (spese ed entrate distinte anche da segno o icona) non e negoziabile e vale a maggior ragione dove il colore fa piu lavoro. Il rosso e `scheme.error`, l'unico del tema, ma indossato come velatura al 16%: a quella intensita legge come tenue e non come allarme. Il punto meno ovvio e cosa succede al tap: quei bottoni non sanno dire una categoria, e una sheet senza categoria non puo salvare, quindi i due bottoni sarebbero decorativi. La sheet preseleziona la piu usata per quel tipo, con la stessa finestra di 60 giorni della griglia - una supposizione, ma in piena vista, in cima alla sheet e a un tap dall'essere cambiata.

**Problemi:** nessuno. `layoutFor` e diventata `internal` e ha i suoi test: quale layout tocca a quale dimensione e una decisione silenziosa, sbagliarla non rompe niente e non la segnala nessun build.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` verde, 647 test, 0 falliti. Test nuovi: `WidgetLayoutTest` (una riga e due bottoni a qualunque larghezza, due righe tornano griglia, il bucket grande tiene sette categorie, crescere non fa mai perdere tile) e tre casi in `QuickEntryViewModelTest` sulla preselezione (piu usata, ricaduta sulla prima senza storia, e la categoria mandata dal widget mai sovrascritta dalla supposizione). versionCode 133 -> 134, versionName 0.9.94 -> 0.9.95.

**Prossimo:** prova su device del ridimensionamento continuo dall'alto verso una riga, del passaggio griglia/bottoni mentre si trascina, e dei due colori sul wallpaper in tema chiaro e scuro - il rosso al 16% su sfondo scuro e il caso in cui la velatura ha meno contrasto da spendere.

---

## 2026-07-26 - Il widget dimagrisce di opzioni e ingrassa di corpo

**Fatto:** quarto giro sul widget dopo la prova su device. L'utente chiede di rimuovere il colore di sfondo personalizzato e lo slider di opacita, segnala che l'azione della schermata dovrebbe dire "Aggiorna" quando il widget esiste gia, e chiede icone e testi piu grandi piu un po' d'aria a destra dell'importo.

**Decisioni:** le due opzioni rimosse erano state costruite bene e sono state tolte volentieri: il lavoro del widget e somigliare a Saldo, e ogni grado di liberta in piu era un modo in piu per non somigliargli. Con loro se ne va anche il clamp dell'opacita, la palette e la scacchiera dell'anteprima, che senza trasparenza non comunicava piu niente; l'anteprima invece resta, perche serve ancora a scegliere chiaro/scuro guardando invece che piazzando. Sul testo dell'azione: Android consegna la stessa activity e lo stesso intent al primo piazzamento e a una modifica successiva, non esiste un flag di sistema che li distingua, quindi l'unica fonte possibile e lo stato salvato - un marcatore `Configured` scritto alla conferma. Sulle dimensioni la leva piu efficace non era la tile ma il rapporto del glifo dentro la tile: dal 55% al 62%, che si vede subito e non costa un dp di altezza. Le tile crescono comunque (44 sul bucket grande, 52 sul 2x2) e i testi salgono di un punto, ripagati riducendo padding verticale e gap. Il padding orizzontale invece **sale** a 12dp: la larghezza non ha un budget da difendere, e la stessa mossa risolve l'importo troppo vicino al bordo, a cui sono stati aggiunti 4dp propri perche un testo abbraccia i suoi glifi molto piu stretto di quanto una pillola abbracci la propria etichetta - lo stesso padding letto ai due lati non appare uguale.

**Problemi:** nessuno. La pillola del selettore scende da 36 a 34dp per far quadrare i conti verticali: resta ben lontana dai 20dp da cui era partita e la larghezza non cambia, quindi il bersaglio che era stato il problema regge.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` verde, 637 test, 0 falliti. Test aggiornati: `QuickAddWidgetPrefsTest` perde i casi su colore e opacita e guadagna quello sul marcatore `Configured`; `QuickAddWidgetThemeTest` passa dai casi sul colore personalizzato a quelli sull'override chiaro/scuro (inchiostro leggibile in entrambi i versi, sfondo sempre opaco). versionCode 132 -> 133, versionName 0.9.93 -> 0.9.94.

**Prossimo:** prova su device delle nuove taglie, con attenzione al bucket 4x2 (largo e basso), che e quello dove il conto verticale e piu stretto: intestazione da 34dp piu una riga sola di tile da 40 con etichetta.

---

## 2026-07-26 - Il widget prende l'aspetto dell'app, e poi lo si puo cambiare

**Fatto:** tre richieste dell'utente dopo che il widget ha iniziato a funzionare. (1) Icone categoria nello stile dell'app. (2) Sfondo uguale a quello della Dashboard nei due temi. (3) In configurazione: forzatura del tema, colore di sfondo a scelta (bianco e nero puri inclusi) e slider di trasparenza. Aggiunta anche un'anteprima live in cima alla schermata.

**Decisioni:** la richiesta (1) si e chiarita guardando il codice invece che lo screenshot. `CategoryCell` disegna una categoria **non selezionata** come squircle col colore al 16% e glifo nel colore pieno, e quella **selezionata** come squircle pieno con glifo bianco. Il widget usava la seconda per tutte: ecco perche stonava, ogni tile sembrava selezionata. Ora usa la prima, con la velatura che resta nel canale alfa del bitmap invece di essere appiattita su un colore di sfondo - condizione perche un widget trasparente si componga bene sul wallpaper. Sulla (3) il modello e un unico quadrivio (Sistema / Chiaro / Scuro / Colore) invece di due impostazioni separate, perche l'utente ha scritto "oppure" e due controlli che si contraddicono sono peggio di uno. La parte che vale la pena annotare e come si sceglie l'inchiostro: con un colore personalizzato lo schema viene deciso dalla **luminanza del colore scelto** e non dalle impostazioni dell'app, altrimenti un widget nero su app chiara avrebbe etichette scure su nero. L'opacita tocca solo lo sfondo: le tile tengono la propria velatura e il testo resta pieno, quindi un widget trasparente resta leggibile per costruzione. L'anteprima non e un ornamento: l'opacita non si sceglie alla cieca, un numero da solo non dice nulla. E su scacchiera perche leggere il wallpaper vero costerebbe un permesso che questa app non chiede - e su quel punto l'anteprima e onesta, mostra la trasparenza ma non puo promettere la leggibilita sopra la *tua* foto. Il tema dell'anteprima passa dalla stessa `resolveWidgetTheme` del widget, cosi non puo divergere.

**Problemi:** una conseguenza dichiarata: la tile "Apri Saldo" era volutamente disegnata a contorno per non somigliare a una categoria, e la richiesta (1) chiedeva esplicitamente lo stesso stile anche per lei. Ora la distinzione regge su colore brand, glifo `MoreHoriz` ed etichetta, non piu sulla forma. E abbastanza, ma e un margine in meno di quello di prima. L'opacita ha un rischio suo, gestito: un valore fuori range renderebbe il widget invisibile e la configurazione si raggiunge dal widget, quindi il valore e clampato in lettura oltre che in scrittura.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` verde, 638 test, 0 falliti. Test JVM nuovi in `QuickAddWidgetPrefsTest` (round-trip dell'aspetto, aspetto sconosciuto che ricade su Sistema, opacita clampata, palette che contiene bianco e nero puri). Strumentati nuovi `QuickAddWidgetThemeTest` (sfondo dell'app come default, widget scuro su app chiara, nero puro che prende lo schema scuro, bianco puro che prende quello chiaro su app scura, opacita che tocca solo lo sfondo) e casi riscritti in `CategoryIconBitmapsTest` per la velatura. versionCode 131 -> 132, versionName 0.9.92 -> 0.9.93.

**Prossimo:** verifica su device delle tile nel nuovo stile accanto alla schermata di inserimento (devono essere indistinguibili), dello sfondo nei due temi contro la Dashboard, e dell'anteprima mentre si trascina lo slider. Da guardare con attenzione: un'opacita bassa su wallpaper chiaro con tema chiaro, che e il caso peggiore per la leggibilita e quello in cui l'anteprima aiuta di meno.

---

## 2026-07-26 - Due bug nel widget: la guardia di troppo e il segnale che mancava

**Fatto:** terza prova su device. L'utente riferisce due cose: il selettore di tipo "va una volta e poi non va piu", e appena installata l'app il widget apre sempre l'app anche toccando una categoria. Sono due bug distinti, entrambi trovati leggendo il codice e non ipotizzando.

**Decisioni:** la frase "va una volta e poi non va piu" e stata quella decisiva, perche esclude sia il bersaglio sia lo stato e punta a una condizione che diventa falsa dopo il primo cambio. Era la guardia dentro il `produceState`: `if (inputs != initialInputs) value = load(...)`. `initialInputs` e il valore letto alla creazione della sessione e resta fisso per tutta la sua vita, quindi il primo tocco ricarica (gli input sono diversi) ma il ritorno sullo stato di partenza trova la guardia soddisfatta, il producer non fa nulla e in `value` restano i dati dell'altro tipo. L'avevo scritta per evitare una query ridondante al primo frame: una micro-ottimizzazione che ha rotto il giro di ritorno, e il costo che risparmiava era una query per sessione. Rimossa. Il secondo bug e piu insidioso perche non e in cio che il codice fa ma in cio che non guarda: `WidgetRefreshWatcher` osservava movimenti e categorie e non i conti. Un widget piazzato prima dell'onboarding non ha conti, quindi `isReady` e falso e il widget diventa un unico testo cliccabile largo quanto la tile - da cui "apre sempre l'app". La creazione del primo conto, cioe l'evento che lo rende usabile, non emetteva alcun segnale, e il widget restava una tile morta finche non capitava altro. Colta l'occasione per due cose che rendono il difetto meno probabile in futuro: i segnali sono estratti in `refreshSignals()`, nominati e testabili, perche un'omissione li e invisibile in build; e il selettore si colora da `currentState()` invece che dai dati caricati, cosi il controllo appena premuto risponde subito e la griglia si allinea un istante dopo.

**Problemi:** nessuno. Vale la pena annotare il pattern, perche e la terza volta di fila che il widget si rompe per lo stesso motivo di fondo: in Glance tutto cio che vive fuori dalla composizione e congelato, e ogni scorciatoia che confronta il presente con un valore catturato all'avvio e destinata a sbagliare appena l'utente torna sui suoi passi.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` verde, 631 test, 0 falliti. Test nuovi: `WidgetRefreshWatcherTest` (primo conto, categoria, movimento, e nessun segnale per lo stato gia a schermo) e un caso in `QuickAddWidgetDataLoaderTest` che verifica che cambiare tipo e tornare indietro ricarichi entrambe le volte - cioe che il loader resti senza stato, che e la premessa su cui la correzione (1) si regge. versionCode 130 -> 131, versionName 0.9.91 -> 0.9.92.

**Prossimo:** prova su device del selettore avanti e indietro piu volte di seguito, e del percorso da installazione pulita: widget piazzato prima dell'onboarding, creazione del primo conto, e le categorie che devono comparire da sole senza toccare nulla.

---

## 2026-07-26 - Il selettore del widget non era rotto, era piccolo

**Fatto:** terzo giro sul widget dopo la prova su device. L'utente riferisce che ora passa a Entrata ma non torna a Spesa, e poco dopo che fa fatica anche nell'altro verso. Corretto il bersaglio del selettore di tipo: la pillola diventa un `Box` alto 36dp col testo centrato e tutta l'area cliccabile, invece di un `Text` con padding alto circa 20dp.

**Decisioni:** l'asimmetria della segnalazione ("a Entrata si, a Spesa no") suggeriva un bug di stato e la prima ipotesi era una collisione di `PendingIntent`: due `actionRunCallback` sulla stessa classe, nella stessa composizione, che differiscono solo per i parametri, e gli extra non entrano in `filterEquals`. Verificata nel bytecode prima di scrivere qualsiasi cosa, e **scartata**: `ActionTrampolineKt.createUniqueUri` costruisce per ogni azione un URI `glance-action` con `appWidgetId`, `viewId` e `viewSize`, quindi due controlli distinti hanno intent distinti per costruzione. Con quella strada chiusa la spiegazione residua era la piu ovvia: 20dp di altezza contro i 48dp minimi di un target Android, e "Spesa" e la parola piu corta quindi anche la pillola piu stretta - da cui l'illusione che il difetto avesse una direzione. La seconda segnalazione dell'utente, arrivata mentre indagavo ("faccio fatica anche a passare a Entrata"), ha confermato che il verso non c'entrava. L'altezza si paga in un budget verticale che era gia stretto, e la si e pagata dove costava meno: padding del widget 12 -> 10dp, gap dell'intestazione 10 -> 8dp e, solo nel bucket 4x2 dove c'e una riga sola sotto un'intestazione da 36dp, tile 40 -> 36dp. Non toccando invece le tile del 4x3 e del 2x2, che restano il bersaglio principale del widget: pagare un target rendendone un altro difficile sarebbe stato uno scambio alla pari.

**Problemi:** nessuno. Rinominata anche la chiave dei parametri dell'azione (`quick_add_type` -> `quick_add_requested_type`): coincideva con quella dello stato del widget e, pur essendo namespace diversi, faceva sembrare una sola chiave letta in due modi - esattamente il tipo di somiglianza che mi aveva portato fuori strada sull'ipotesi PendingIntent.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` verde, 626 test, 0 falliti. Nessun test nuovo: la correzione e di layout, e la logica dell'azione non e cambiata. versionCode 129 -> 130, versionName 0.9.90 -> 0.9.91.

**Prossimo:** riprova su device del selettore nei due versi e a piu riprese, e controllo che le tile non siano state tagliate nel bucket 4x2 (widget largo ma basso, due celle di altezza).

---

## 2026-07-26 - Il widget non ascoltava: la sessione Glance compone una volta sola

**Fatto:** due segnalazioni dell'utente dopo la prima prova su device del widget. (1) Il selettore Spesa/Entrata non cambiava quasi mai: si vedeva la pillola lampeggiare al tocco ma il widget restava com'era. (2) In Entrata comparivano due tile "Altro", una che apriva l'inserimento rapido della categoria omonima del seed e una che portava nell'app. Correggendo la seconda e saltato fuori un terzo problema che nessuno aveva ancora notato: quella tile apriva **sempre** l'editor di una spesa, `ACTION_ADD_EXPENSE` cablata, anche dal widget Entrata.

**Decisioni:** la causa del punto (1) e stata cercata nel bytecode prima di toccare qualsiasi cosa, perche le ipotesi plausibili erano tre e sbagliarne una avrebbe voluto dire riscrivere il file due volte. `AppWidgetSession.processEvent`, su `UpdateGlanceState` (l'evento che manda `update()`), rilegge lo stato con `ConfigManager.getValue`, lo scrive nel `MutableState` `glanceState` dentro uno snapshot e si ferma li: e la ricomposizione a propagarlo, e `provideGlance` non viene mai richiamato - gira una volta sola, alla creazione della sessione. Il codice leggeva configurazione, dati e tema *prima* di `provideContent`, cioe catturava una fotografia che nessun update poteva piu aggiornare; il tap arrivava davvero, scriveva davvero lo stato e chiamava davvero `update()`, ma la composizione si ridisegnava identica. Le volte in cui funzionava erano quelle in cui la sessione era stata ricreata per conto suo (launcher riavviato, ridimensionamento, memoria), da cui il "quasi mai" della segnalazione. La correzione non e una toppa sul selettore ma sulla classe di bug: tutto quello che il widget disegna ora si legge *dentro* la composizione, lo stato da `currentState()` e i dati da `produceState` chiavato su di esso, il tema da `collectAsState`. Il caricamento iniziale in `provideGlance` resta, ma solo perche il primo frame non lampeggi. Conseguenza da gestire: se `provideGlance` non riparte, nemmeno un movimento registrato altrove fa rileggere il database, perche la configurazione non cambia. Da qui `QuickAddWidgetPrefs.Revision`, bumpata dal watcher prima di `updateAll`: lo stato del widget e l'unico canale che una sessione ascolta, quindi un cambio di dati deve entrare da li. Sul punto (2) l'utente aveva ragione e la risposta giusta non era rinominare l'etichetta: la tile di uscita non e una categoria e non deve somigliarne a una. Diventa una squircle **outline** nel colore brand invece del pieno della palette categorie, glifo `MoreHoriz` che non appartiene a nessun set di icone categoria, etichetta "Apri Saldo". Forma, colore e testo diversi: la collisione non e attenuata, e resa impossibile.

**Problemi:** nessuno bloccante. Il terzo bug (tipo cablato) non aveva modo di emergere da una build ne da un test di composizione, quindi la riga di instradamento e stata estratta in `quickActionFor` e coperta da un test JVM, che e l'unico posto dove poteva essere colta.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` verde in locale, 626 test, 0 falliti, nessun warning. Test JVM nuovi: `QuickActionForTest` (instradamento per tipo) e un caso in `QuickAddWidgetPrefsTest` sulla revisione accanto alla configurazione. Strumentati nuovi (da eseguire su device): la tile azione e outline e non piena, e disegna comunque il proprio glifo. versionCode 128 -> 129, versionName 0.9.89 -> 0.9.90.

**Prossimo:** riprova su device dei due punti segnalati - il selettore che ora deve cambiare a ogni tocco, e la tile "Apri Saldo" distinguibile dalla categoria "Altro" in entrambi i tab - piu la verifica che il totale di oggi si aggiorni dopo un movimento registrato in app (e il percorso che passa dalla revisione). Restano aperti i punti della prima consegna: i tre temi, TalkBack, font scale, e lo stack di due sheet nei picker.

---

## 2026-07-26 - Widget di inserimento rapido: la scelta sul launcher, l'importo in una sheet

**Fatto:** widget home anticipato dalla roadmap v1.5 su richiesta utente (Fase 10.18 in PLANNING.md), a partire da una nota a mano con due schermate. Tre pezzi: `SaldoQuickAddWidget` in Glance (selettore Spesa/Entrata, totale di oggi, griglia categorie in tre bucket di dimensione, tile "Altro" che apre l'editor completo), `QuickEntryActivity` traslucida che apre un `ModalBottomSheet` sopra il launcher col tastierino dell'ADR 31 gia aperto, e `QuickAddWidgetConfigActivity` per conto, tipo, categorie fissate e totale di oggi. Sotto: tre estrazioni condivise (`TransactionSign`, `DefaultAccountResolver`, `QuickTransactionFactory`), la query `mostUsedCategories`, `CategoryIconBitmaps` che rasterizza gli `ImageVector` dell'app per Glance, e `WidgetRefreshWatcher` che ridisegna i widget piazzati.

**Decisioni:** la nota chiedeva il tastierino *dentro* il widget e questa e l'unica parte che non e stata fatta come disegnata, motivata nell'ADR 32 e concordata con l'utente prima di scrivere codice. Un widget e `RemoteViews`: ogni tap e un broadcast al processo app piu un giro fino al launcher, 60-150ms a caldo e molto di piu a freddo perche il primo tap deve avviare il processo, senza haptics e con una scrittura su disco per cifra. Su "12,50" sono quattro tap che si sentono tutti. La scelta della categoria invece costa una sola interazione ed e contenuto leggibile a colpo d'occhio, che e cio in cui un widget e bravo: resta li. Il conteggio dei tap della nota non cambia (categoria, cifre, Salva), cambia dove si sente la latenza. Il conto e salito nella configurazione per istanza invece di essere un controllo a runtime: un picker di conti dentro `RemoteViews` sarebbe una seconda schermata, e "due conti, due widget" e il caso reale. Sulle icone la scelta era tra 40 vector drawable duplicati e rasterizzare a runtime lo stesso `ImageVector`: duplicare significa una seconda mappa che diverge dalla prima al primo aggiornamento, quindi si rasterizza, con fallback alla squircle colorata se un vettore non si disegna e due test (strutturale in CI, a pixel su device) a coprire il rischio. Le tre dimensioni sono layout progettati e non un layout fluido, perche `RemoteViews` non ha una passata di misurazione a cui appoggiarsi. La query delle categorie piu usate e deliberatamente non statistica: conta ogni valuta, ogni conto e anche i movimenti esclusi dalle statistiche, perche "cosa tocco di solito" non e "cosa sommano i grafici"; restano fuori solo i pending.

**Problemi:** i conti dei dp hanno bocciato il 2x2 come lo mostrava la nota. Una cella di launcher e circa 55dp, quindi un 2x2 sono circa 110dp per lato: intestazione piu otto categorie con etichetta non ci stanno compresse, non ci stanno e basta. Quella dimensione mostra quattro tile sole icone e prende il tipo dalla configurazione; il layout completo della nota vive a 4x3. Il test del doppio tap su Salva ha poi trovato un buco vero: la guardia `isSaving` si azzera a coroutine finita, quindi due tap in sequenza scrivevano due movimenti. La sheet tiene la conferma per 700ms prima di chiudersi, che e esattamente la finestra in cui un secondo tap ci sta: `canSave` ora include `!isSaved`, cosi il bottone si spegne e il ViewModel rifiuta comunque. Detekt ha bocciato la guardia di `save()` come condizione troppo complessa, riscritta a guard clause.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` verde in locale, 619 test, 0 falliti, nessun warning nuovo (l'unico prodotto da questo giro, un Overdraw sul layout di anteprima del widget, e stato corretto). Test JVM nuovi: `TransactionSignTest`, `DefaultAccountResolverTest`, `QuickTransactionFactoryTest`, `QuickEntryViewModelTest`, `QuickAddWidgetDataLoaderTest`, `QuickAddWidgetPrefsTest`, `CategoryIconStructureTest`. Strumentati nuovi, compilati ma **non ancora eseguiti** (serve un device): `CategoryIconBitmapsTest` e `TransactionDaoMostUsedTest`. versionCode 127 -> 128, versionName 0.9.88 -> 0.9.89.

**Prossimo:** verifica su device, che qui e piu del solito perche il widget e una superficie nuova e mezza dipende dal launcher. Da guardare: le tre dimensioni, il salvataggio dal launcher a processo freddo, i tre temi, TalkBack, font scale al massimo, la tile "Altro", e almeno due launcher diversi (gli update Glance hanno jank noto su alcune build OEM). Un punto e apertamente incerto: i picker di conto e categoria sono `ModalBottomSheet` aperti sopra la sheet principale, e se lo stack di due sheet risultasse sgradevole il ripiego e sostituirli con un dialog.

---

## 2026-07-26 - Il bug dei chip rapidi (slot Box di material3) e le gambe del trasferimento

**Fatto:** secondo giro di correzioni dalla prova su device. (1) I chip rapidi "Oggi"/"Ieri" nel dialog della data non si sono mai visti dalla Fase 10.12. Causa trovata nel bytecode di material3 1.4.0: `DatePickerDialog` dichiara lo slot come `content: @Composable ColumnScope.() -> Unit` ma lo dispone in un **Box** (`checkcast BoxScope` all'offset 807, con `ColumnScopeInstance` passato come receiver del lambda), quindi i figli si sovrappongono invece di impilarsi verticalmente. Il `DatePicker`, composto per ultimo, copriva i chip. Correzione: una `Column` esplicita dentro lo slot. (2) Nel trasferimento i due chip conto andavano a capo su righe sfalsate, senza nulla che dicesse quale fosse la partenza: ora sono due righe piene allineate su una colonna di etichette ("Da" / "A"), con lo scambio delle gambe accanto come icona verticale (`SwapVert`) invece della freccia in mezzo.

**Decisioni:** la `Column` esplicita e preferita a qualsiasi altra soluzione perche e una riga, non dipende dal comportamento del Box (funziona anche se un domani material3 lo cambiasse in una Column vera) e non tocca il dialog condiviso con obiettivi e ricorrenze. Nel trasferimento due nomi di conto piu una freccia non stanno su una riga a nessuna larghezza realistica: invece di sperare nel wrap si sceglie la disposizione verticale e le si da una struttura, che e anche l'unico modo per etichettare le gambe. Il testo di `EditorChip` passa a `weight(1f, fill = false)` con ellissi: cosi la stessa pillola funziona sia a larghezza libera (chip del conto) sia a larghezza imposta (gamba del trasferimento), e un nome lungo si accorcia invece di spingere la freccia fuori.

**Problemi:** la diagnosi del punto (1) e costata piu della correzione. Le prime due ipotesi (contenuto tagliato per mancanza di spazio, contenuto sotto la piega del dialog) erano incompatibili con lo screenshot, che mostrava calendario e bottoni interi; e stato il dettaglio riferito dall'utente - toccando l'intestazione "Seleziona data" si apriva l'orologio - a indicare la sovrapposizione, perche l'intestazione del `DatePicker` non e cliccabile e il tap cadeva sulla riga sottostante. Il bytecode ha poi confermato.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` verde, 576 test, 0 falliti, nessun warning. Nessun test nuovo (entrambe le correzioni sono di layout). versionCode 126 -> 127, versionName 0.9.87 -> 0.9.88.

**Prossimo:** verifica su device: i chip "Oggi"/"Ieri" sopra il calendario, e la schermata del trasferimento con nomi di conto lunghi.

---

## 2026-07-26 - Due correzioni dalla prova su device: ora scopribile, selettore tipo che respira

**Fatto:** due segnalazioni dell'utente dopo la prova su device della Fase 10.17. (1) Il chip data+ora apriva il dialog della data, che pero non mostrava da nessuna parte come cambiare l'ora: l'utente l'ha trovata per caso toccando l'intestazione "Seleziona data". Il chip diventa quindi due controlli in una pillola sola: meta sinistra (glifo calendario + data) apre il calendario, meta destra (glifo orologio + ora) apre il time picker, separate da un divider verticale. La riga dell'ora dentro il dialog e lo slot `timeRow` di `SaldoDatePickerDialog` sono stati rimossi: il dialog torna esattamente com'era. (2) Nel selettore di tipo la voce "Trasferimento" toccava il bordo del proprio segmento: via il glifo di spunta (`icon = {}`) e label a `labelMedium` con `maxLines = 1`.

**Decisioni:** due meta tappabili invece di una riga dentro il dialog perche il problema era di scopribilita, e una riga in fondo a un dialog non la risolve: l'icona dell'orologio accanto all'ora dice cosa fa quel pezzo di chip senza costare una riga del form, che era il vincolo iniziale. Niente chevron sulle due meta (ne servirebbero due): i glifi di testa bastano, e senza chevron la pillola divisa e larga circa quanto quella unita, quindi non torna a capo accanto al chip del conto. Sul selettore, togliere la spunta e la leva vera: il segmento selezionato resta evidente dal contenitore pieno e TalkBack legge comunque lo stato `selected`; il solo restringimento del testo non sarebbe bastato, perche da selezionato la spunta si prende altri 26dp.

**Problemi:** resta un punto aperto che lo screenshot dell'utente fa sospettare ma che non posso verificare senza device: nel dialog della data non compaiono nemmeno i chip rapidi "Oggi"/"Ieri", che stanno nello stesso slot `content` di `DatePickerDialog` dove stava la riga dell'ora. Se e cosi sono invisibili dalla Fase 10.12 e nessuno se n'era accorto. Il bytecode di material3 1.4.0 mostra che quello slot finisce dentro un `Box(Modifier.weight(1f, false))`, quindi un'ipotesi c'e, ma prima di toccare un dialog condiviso da altre tre schermate serve una conferma visiva. Segnalato all'utente, non corretto alla cieca.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` verde, 576 test, 0 falliti, nessun warning. Nessun test nuovo: entrambe le correzioni sono presentazionali (la logica di data e ora non e cambiata, `onTimeSelected` ha lo stesso percorso di prima). versionCode 125 -> 126, versionName 0.9.86 -> 0.9.87.

**Prossimo:** verifica su device delle due correzioni e, con lo stesso giro, conferma se i chip "Oggi"/"Ieri" siano visibili sopra il calendario.

---

## 2026-07-26 - Inserimento movimenti: tastierino in-app, chip data+ora, form a due zone

**Fatto:** review UI/UX della schermata di inserimento movimenti su richiesta utente, partendo dagli screenshot dei due percorsi (Fase 10.17 in PLANNING.md). Quattro commit. (1) Tastierino importi in-app condiviso (`AmountKeypad`, `AmountKeypadHost` + `AmountTarget`, `AmountInputEditor` puro) e `HeroAmountField` che non e piu un `BasicTextField`: display con caret e migliaia raggruppate (`MoneyInput.grouped`), tastiera hardware via `onKeyEvent` e incolla al long-press. (2) Data e ora in un solo chip, con l'ora modificabile da una riga dentro il dialog della data (nuovo slot `timeRow` opzionale su `SaldoDatePickerDialog`). (3) Form a due zone: tipo, importo e chip fissi, zona scorrevole che apre con le categorie; `ScrollingCategoryGrid` mostra tutte le categorie in un box alto due righe che scorre da solo, al posto delle prime otto con la selezionata infilata dentro. (4) `AmountTextField` (read-only che apre il tastierino al tap) sui campi importo non-hero: saldo iniziale, massimale carta, saldo in onboarding, min/max del filtro registro; la conferma dei movimenti pending passa invece al campo hero col tastierino gia aperto.

**Decisioni:** il tastierino ribalta l'ADR 16, quindi la revisione e scritta come ADR 31 e non aggirata. Regge solo perche i tre motivi dell'ADR 16 sono coperti uno per uno: coerenza (il tastierino e su *tutti* i campi importo, non solo sui movimenti, come chiesto dall'utente), accessibilita (display `Role.Button` che recita l'importo, tasti annunciati, tastiera fisica, incolla) e codice (logica di editing pura, sanitizzazione sempre in `MoneyInput`). In cambio si ottiene l'unica cosa che l'ADR 16 non poteva dare: l'altezza del pannello e nostra e il pannello si chiude, che e esattamente la leva che libera lo spazio per le categorie. Sull'ora la risposta onesta all'utente e stata che non serve quasi a niente: non e mostrata da nessuna parte, non entra nel CSV, serve solo a ordinare dentro la giornata; resta modificabile ma smette di occupare una riga. Aritmetica sui tasti (somme in linea) valutata e lasciata fuori scope.

**Problemi:** il primo layout metteva la griglia categorie nella zona fissa, e il conto dei dp non tornava: con selettore tipo, tastierino e barra Salva servivano circa 416dp di zona fissa contro i circa 384 disponibili su un telefono da 800dp, cioe la seconda riga di categorie tagliata e irraggiungibile (una `Column` non scorre). La griglia e quindi finita in testa alla zona scorrevole: due righe restano sotto i chip senza scorrere sugli schermi normali, e su uno schermo corto o a font scale grande la zona scorre invece di tagliare. Recuperati anche 4dp per tasto sul tastierino. Da verificare su device il passaggio di scroll tra griglia e contenitore: se risultasse sgradevole, il ripiego e tornare a 8 categorie fisse senza scorrimento interno, che con un tastierino richiudibile lo spazio ce l'ha comunque. Detekt ha poi bocciato due file per `MatchingDeclarationName`: `AmountTarget` e uscito in un file suo (era comunque il posto giusto) e l'enum del campo attivo dell'editor conto e sparito del tutto, sostituito da un singolo flag piu il target scelto dal tipo di conto - saldo iniziale e massimale carta non convivono mai, esattamente come le due sezioni che si alternano in cross-fade.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` verde in locale (l'SDK Android c'e su questa macchina, a differenza dei giri precedenti), 576 test, 0 falliti, nessun warning di compilazione. Test JVM nuovi: `AmountInputEditorTest` (cifre in sequenza, separatore unico, decimali al limite della valuta, valuta a 0 decimali, backspace su stringa vuota, clear, toggle segno, cap delle cifre intere) e casi per `MoneyInput.grouped` (raggruppamento, decimali intatti, separatore in coda, segno). versionCode 124 -> 125, versionName 0.9.85 -> 0.9.86.

**Prossimo:** verifica su device: nuova spesa dalla Dashboard e nuovo movimento dal registro (due righe di categorie senza scorrere, salvataggio in 3 tap), trasferimento cross-currency col tastierino che segue il campo toccato, rettifica saldo, chip data+ora, convivenza tastierino/IME sui campi di testo, back col pannello aperto, i tre temi (chiaro, scuro, dynamic color), TalkBack e font scale al massimo.

---

## 2026-07-25 - Segnalazione multi-valuta nelle Statistiche

**Fatto:** ultimo dei quattro gap della quarta review (Fase 10.16 in PLANNING.md). Nuova query `observeOtherCurrencyCount` (stessi filtri di `observeCategoryTotals`, test sulla valuta invertito) e riga informativa sotto il selettore di periodo delle Statistiche, mostrata solo quando quel periodo contiene movimenti in altre valute; il tap apre il drill-down di quei movimenti tramite un nuovo flag `otherCurrenciesOnly` sulla route, che inverte il test di valuta in `matchesStatsScope`.

**Decisioni:** la ricognizione prima di implementare ha cambiato il piano proposto. Delle quattro superfici che sembravano scoperte due lo erano solo in apparenza: la card Saldo totale e coperta dalla Fase 9.8 e il drill-down delle card Oggi/Mese - insieme al registro - e coperto da `FilteredTotalsBar`, che stampa gia una riga di totali per valuta. Quindi la riga e stata aggiunta solo dove serviva davvero, le Statistiche, e non nel drill-down come inizialmente proposto. Dashboard invariata su scelta dell'utente. La riga e volutamente quieta (`surfaceContainerHigh`, testo attenuato) e non un warning: non c'e niente di rotto, i grafici non possono sommare due valute finche non esiste la conversione (v2.0).

**Problemi:** durante l'implementazione e emerso un caso peggiore di quello per cui era nata la feature. `hasData` guarda solo gli aggregati in valuta principale, quindi un periodo con soli movimenti esteri cadeva nell'empty state "non hai ancora registrato nulla" pur avendo movimenti dentro: il messaggio piu fuorviante possibile. La riga compare quindi anche sull'empty state, che e il posto dove serve di piu; coperto da un test dedicato.

**Verificato:** `testDebugUnitTest lint detekt` verde, 563 test, 0 falliti. Test nuovi in `StatsViewModelTest` (notice accanto alle cifre, empty state che si spiega, nessuna notice a valuta unica, nessuna notice a registro vuoto). versionCode 123 -> 124, versionName 0.9.84 -> 0.9.85.

**Prossimo:** verifica su device con un conto in valuta diversa dalla primaria (riga presente col periodo misto, empty state col periodo di sole valute estere, drill-down "Altre valute"). Con questa si chiudono tutti e quattro i gap di prodotto della quarta review.

---

## 2026-07-25 - Nota del movimento, cancellazione dati, filtro "senza categoria"

**Fatto:** tre dei quattro gap di prodotto segnalati dalla quarta review, su richiesta utente (Fase 10.15 in PLANNING.md). (1) La nota del movimento diventa scrivibile: esisteva in modello, DB, backup, export/import CSV ed era gia cercata dalla ricerca del registro, ma nessun editor la esponeva. Ora e un campo del form (dirty detection inclusa), salvata con `trim()` e con la nota di soli spazi che persiste come `null`. (2) Cancellazione di tutti i dati: `BackupRepository.eraseAll()` + `EraseAllDataUseCase` + `AppResetCoordinator`, con card "danger zone" in fondo alla schermata Dati e dialog che apre sulla data dell'ultimo backup. (3) Filtro "senza categoria" nel registro, in unione con le categorie scelte, piu unificazione del drill-down statistiche sullo stesso predicato.

**Decisioni:** la nota su un movimento nuovo resta dietro un'azione testuale ("Aggiungi una nota") invece di essere un riquadro sempre aperto: il form della spesa tipica non deve allungarsi di uno spazio vuoto che quasi nessuno compila, mentre un movimento che una nota ce l'ha la mostra sempre. Il ripianto delle categorie predefinite dentro `eraseAll` non e un extra ma un requisito: il seed vive nel callback `onCreate` di Room, che su un file esistente non gira mai piu, quindi la pulizia secca lascerebbe l'app senza categorie e senza rimedio se non reinstallando. L'ordine delle scritture nel reset (DB, poi preferenze, poi segnale) e deliberato: se il wipe fallisce le preferenze restano intatte, perche un database integro che ha dimenticato valuta, tema e conto predefinito e peggio del non aver cancellato. Il ritorno all'onboarding passa da un coordinator singleton e non da una lettura reattiva del flag onboarding: quel flag si scrive anche a meta onboarding (installazione esistente che crea il primo conto), quindi un gate che lo osservasse salterebbe fuori dal flusso nel momento sbagliato. Il filtro "senza categoria" e in unione con `categoryIds`, non una modalita alternativa, cosi resta un pari grado dei chip categoria e conta nel loro stesso gruppo del badge.

**Problemi:** nessuno bloccante. Un warning di build sull'icona `Icons.Outlined.LabelOff` deprecata, risolto passando alla variante auto-mirrored (il progetto tiene il build senza warning).

**Verificato:** `testDebugUnitTest lint detekt` verde, 551 test, 0 falliti. Test nuovi: `EraseAllDataUseCaseTest` (ordine delle scritture, preferenze intatte su fallimento), casi in `TransactionFilterEngineTest` (unione, solo-senza-categoria, nessun termine, badge) e `TransactionEditorViewModelTest` (nota salvata e trimmata, soli spazi -> null, dirty detection). versionCode 122 -> 123, versionName 0.9.83 -> 0.9.84.

**Prossimo:** verifica su device dei tre flussi (nota su movimento nuovo e in modifica, cancellazione con e senza backup, filtro senza categoria dal registro e dall'anello). Resta aperta la quarta voce della review, la segnalazione multi-valuta: proposta all'utente di metterla in Statistiche e nel drill-down delle card Oggi/Mese, tenendo la Dashboard invariata, in attesa di conferma.

---

## 2026-07-25 - Fix dalla quarta review completa (11 punti, nessun cambio di schema)

**Fatto:** review completa su richiesta utente (dominio, data layer, use case, ViewModel, import/export, notifiche, navigazione) e fix di tutti gli 11 punti emersi, su branch dedicato (Fase 10.14 in PLANNING.md). I piu rilevanti: (1) l'editor conto apriva un conto nuovo sulla valuta del locale di sistema invece che sulla valuta principale dell'app, e un conto nella valuta sbagliata sparisce da saldo totale, card Oggi/Mese, statistiche e budget senza dire niente; (2) le regole ricorrenti non ancora iniziate entravano subito in tutte le cifre "al mese", card Dashboard e risparmio pianificato inclusi; (3) la CTA "Paga estratto" mostrava il ciclo scaduto piu vecchio con importo mentre il settlement pagava il piu vecchio in assoluto, quindi con un ciclo vuoto arretrato il tap non registrava nulla. Poi: `sortOrderIncome` scritto con la chiave del tab sbagliato nell'import CSV, ticker di mezzanotte esteso a registro e hub Ricorrenze (erano gli ultimi due senza), baseline dell'editor obiettivi che si sporcava da sola dopo la scorciatoia "crea conto", candidati di riassegnazione categoria filtrati sul tipo dichiarato invece che sui movimenti reali, `isPending` non propagato in `buildTransaction`, card Oggi/Mese che contavano i conti archiviati, tie-break di `primaryCurrency()` dipendente dall'ordine della lista, e l'unico warning del build (`budgets_empty_body` con `80%`/`100%` non escapati).

**Decisioni:** tre punti erano scelte di prodotto piu che difetti, decisi con l'utente prima di toccarli. (a) Le card Oggi/Mese ora escludono i conti archiviati: erano l'unica cifra della Dashboard a contarli, in contraddizione col saldo totale immediatamente sopra; il prezzo e che archiviare riscriva la cifra retroattivamente, esattamente come gia fa per il saldo e la sua storia. Le statistiche restano invariate: sono analisi storica, e cancellarvi un conto archiviato disallineerebbe i drill-down. (b) Tie-break deterministico in `primaryCurrency()` (fallback, poi codice ISO): senza, aggiungere un conto poteva ribaltare la valuta di ogni aggregato. (c) `isPending` propagato come guardia, anche se oggi non raggiungibile dall'editor. Sulle ricorrenze la regola futura resta *elencata* nell'hub con la sua prima data di addebito: nasconderla farebbe sparire una regola appena creata.

**Problemi:** il fix sulle ricorrenze e nato sbagliato e i test lo hanno intercettato. La prima versione usava `startDate <= oggi`, ma i fixture dell'hub e della Dashboard hanno mostrato il caso che rompe: un abbonamento aggiunto il 9 con primo addebito il 12 e un costo mensile reale, e prezzarlo a zero fino al primo addebito e sbagliato quanto contare una regola che parte a settembre. La soglia corretta e la fine del mese corrente, non "oggi": predicato rinominato di conseguenza in `runsInMonthOf`, con `hasEndedBy` per i soli elenchi. Due test esistenti (uno nell'hub, uno in Dashboard) codificavano il comportamento gonfiato e sono stati aggiornati con la motivazione nel commento.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` verde. Test nuovi: `PrimaryCurrencyTest` (maggioranza, esclusi/archiviati, tie-break indipendente dall'ordine, override), piu casi in `AccountEditorViewModelTest`, `RecurrencesViewModelTest`, `SettleCreditCardStatementUseCaseTest`, `CategoryEditorViewModelTest`, `SavingsGoalEditorViewModelTest`. Unica aggiunta al data layer: `TransactionDao.distinctTypesForCategory`, query di sola lettura. versionCode 121 -> 122, versionName 0.9.82 -> 0.9.83.

**Prossimo:** verifica su device dei tre cambi visibili (valuta del conto nuovo, totale mensile delle ricorrenze, card Oggi/Mese con un conto archiviato). Restano aperti dalla review, come gap di prodotto non fixati: il campo `note` dei movimenti esiste nel modello, nel backup, nell'export/import CSV e nella ricerca ma non e scrivibile da nessun editor; manca un "cancella tutti i dati" in Impostazioni; il registro non sa filtrare "senza categoria" mentre le statistiche hanno la fetta dedicata; e i totali multi-valuta non segnalano quanto resta fuori dalla valuta principale.

---

## 2026-07-22 - Squircle ovunque sia un avatar: swatch picker e skeleton

**Fatto:** dopo feedback utente (icone/categorie ancora rotonde in alcune schermate), censimento completo di `CircleShape` nel codice (12 file) e correzione dei due casi che rappresentano avatar: (1) `ColorSwatchPicker` e `IconSwatchPicker` in `core/designsystem` (usati da editor categoria, conto, abbonamenti, obiettivi) passano ad `AvatarShape`: le celle icona sono di fatto una griglia di avatar e lo swatch colore fa da anteprima dello sfondo avatar; (2) il segnaposto avatar 40dp di `SkeletonRow` passa ad `AvatarShape`, cosi la forma non salta quando arrivano i dati reali.

**Decisioni:** restano deliberatamente rotondi (motivati all'utente): barre pill (recap, share card, statistiche, `ThresholdProgressBar`, skeleton del selettore periodo: `CircleShape` su un rettangolo produce estremita a pill, non un cerchio), dot di legenda e indicatori di pagina (4-10dp, squircle indistinguibile), FAB e speed dial (spec Material 3), ripple tondo sulle icone (feedback touch standard), badge decorativo dell'illustrazione onboarding, colonne pill dei grafici.

**Problemi:** nessuno.

**Verificato:** verifica statica + gate CI GitHub. Nessun test nuovo (cambio di sola forma). versionCode 120 -> 121, versionName 0.9.81 -> 0.9.82.

**Prossimo:** verifica visiva su device dei picker colore/icona nei quattro editor che li usano.

---

## 2026-07-22 - Coerenza "premium" per tutti gli editor (conto, rettifica, budget, obiettivo, ricorrenze, categoria)

**Fatto:** estesa la review della Fase 10.12 a tutti gli altri editor (Fase 10.13 in PLANNING.md), su branch `claude/editors-premium-polish` basato su quello dei movimenti (non ancora mergiato, ne riusa i componenti). Cinque commit: (1) promozione in `core/designsystem/component` di `HeroAmountField`, `AnimatedSection` e `SaldoDatePickerDialog` (unifica i due date dialog, chip rapidi opzionali), con l'editor movimenti che delega; (2) editor conto: `LoadingState`, cifre tabulari su saldo iniziale e massimale carta, cross-fade animato tra saldo iniziale e sezione carta di credito, Salva sempre attivo; rettifica saldo con importo hero compatto nel dialog e preview a cifre tabulari; editor categoria allineato (LoadingState, Salva); (3) budget: limite mensile hero, Salva sempre attivo, eliminazione senza dialog con undo cross-screen, coordinator generalizzato (`core/domain/undo/UndoDeleteCoordinator` con sealed `UndoableDelete` e `UndoDeleteViewModel` in `navigation`, snackbar con messaggio per entita); (4) obiettivo di risparmio: target hero, reveal animato della data obiettivo, eliminazione con undo; (5) ricorrenze: importo hero con swap animato verso la nota importo variabile, sezioni animate (mode selector, nota cross-currency, data fine), `LoadingState`, Salva sempre attivo.

**Decisioni:** importo hero solo dove l'importo e il dato principale della schermata (scelta utente): nel form conto saldo iniziale e massimale restano `OutlinedTextField` con cifre tabulari, i protagonisti li sono nome e tipo. Undo al posto della conferma solo per budget e obiettivo (ripristino totale); le ricorrenze mantengono il dialog perche eliminare una regola stacca definitivamente i movimenti gia generati e un undo non li ricollegherebbe (dialog piu onesto); categorie (riassegnazione) e conti (flusso archivia/elimina della lista) invariati. Ripristino budget via write path transazionali del repository (setOverallBudget/upsertCategoryBudget): i watermark di notifica ripartono, accettabile e anzi desiderabile. La rettifica mantiene il bottone abilitato in modo eager: il preview del delta ("Nessuna modifica" / delta firmato) spiega gia lo stato, meglio di una validazione al tap in un dialog.

**Problemi:** nessuno bloccante. Attenzione alla compilabilita dei singoli commit: le stringhe del dialog obiettivi sono state rimosse solo nel commit che rimuove il dialog.

**Verificato:** nessun SDK su questa macchina: verifica statica accurata + gate CI GitHub (`assembleDebug testDebugUnitTest lint`). Test nuovi/aggiornati: `BudgetEditorViewModelTest` (mancava del tutto: scope in create, save, validazione, edit, delete+undo), `UndoDeleteViewModelTest` (ripristino movimento/budget overall/budget categoria/obiettivo, fallimento), delete+undo nell'editor obiettivi, costruttori aggiornati. versionCode 119 -> 120, versionName 0.9.80 -> 0.9.81. Da verificare visivamente su device: i sei editor, gli stati di errore al tap su Salva, undo di budget e obiettivo dalle schermate di provenienza, rettifica con campo hero.

**Prossimo:** verifica visiva sull'APK debug dal device di test; eventuali micro-correzioni da screenshot.

---

## 2026-07-22 - Editor movimenti "premium": importo hero, ora, quick date, undo, consolidamenti

**Fatto:** giro di rifinitura dell'editor movimenti (Fase 10.12 in PLANNING.md) nato da una review UI/UX richiesta dall'utente sulle schermate di inserimento/modifica (spesa, entrata, trasferimento, rettifica: un solo editor riusato). Quattro commit separati sul branch. (1) Campo importo "hero" borderless e centrato (`displayMedium` + cifre tabulari, simbolo valuta a fianco, placeholder 0), errore a colore + testo di supporto, sign-toggle per la rettifica, variante compatta per la seconda gamba cross-currency; bottone Salva sempre attivo con validazione completa al tap; freccia direzionale tra i chip conto dei trasferimenti che inverte le gambe al tap; tasso di cambio implicito sotto il secondo importo nei trasferimenti cross-currency; celle categoria a `AvatarShape` (erano cerchi, incoerenti con registro e lista categorie), cifre tabulari nei saldi del picker conti, `LoadingState` condiviso; sezioni del form animate al cambio tipo. (2) Quick date "Oggi"/"Ieri" nel date picker con conferma immediata e nuovo chip ora con `TimePickerDialog` M3 (l'ora esisteva gia nel modello, ora e visibile e modificabile). (3) Picker colore/icona consolidati in `core/designsystem/component/SwatchPickers.kt`: categorie, conti e abbonamenti delegano ai componenti condivisi (erano tre copie identiche). (4) Eliminazione dall'editor senza dialog di conferma: `TransactionUndoCoordinator` singleton porta il movimento eliminato (con i tag) all'app shell, `SnackbarHost` a livello app in `SaldoApp` mostra l'undo sulla schermata di provenienza, ripristino con la stessa semantica dello swipe-delete del registro.

**Decisioni:** tastiera di sistema invariata sull'importo (ADR 16), il restyle e solo presentazionale (il VM continua a ricevere la stringa raw). Salva sempre attivo perche un bottone disabilitato non spiega il motivo: la validazione differita al tap mostra tutti gli errori insieme, incluso l'importo (nuova stringa `transaction_editor_amount_error`). Undo al posto della conferma di eliminazione come da VISION (l'editor era l'ultimo punto con dialog); niente Scaffold a livello app (scelta esistente rispettata): lo `SnackbarHost` e nel Box overlay, sopra la bottom bar sui top-level. `TimePickerDialog` verificato disponibile in material3 1.4.0 (BOM 2026.02.01). Animazioni con gating `rememberMotionEnabled`.

**Problemi:** nessuno bloccante. Attenzione a: larghezza del campo hero con importi lunghi (risolto con `weight(1f, fill = false)` sul campo interno, scroll orizzontale oltre il cap); TalkBack sul campo senza label (contentDescription esplicita "Importo").

**Verificato:** nessun SDK su questa macchina: verifica statica accurata + gate CI GitHub (`assembleDebug testDebugUnitTest lint`) sul branch `claude/transaction-editor-premium-polish`. Nuovi unit test: `ImpliedExchangeRateTest`, `TransactionUndoViewModelTest`, e nel `TransactionEditorViewModelTest` swap gambe (stessa valuta, cross-currency, no-op fuori dai trasferimenti), data+ora combinate nel salvataggio, ora locale esposta in modifica, delete che pubblica al coordinator. versionCode 118 -> 119, versionName 0.9.79 -> 0.9.80. Da verificare visivamente su device: nuovi movimenti (3 tipi), modifica dei 4 tipi, errori al tap su Salva, swap e tasso nei trasferimenti, quick date e ora, eliminazione con undo da registro/dashboard/stats.

**Prossimo:** verifica visiva sull'APK debug dal device di test; eventuali micro-correzioni da screenshot.

---

## 2026-07-22 - "Ad oggi" a icona anche nel breakdown della Dashboard (uniformita)

**Fatto:** portata la treatment compatta "ad oggi" (glifo calendario + importo, senza la parola) anche nelle righe del breakdown della card Saldo totale in Dashboard, che usavano ancora il testo "%1$s ad oggi". Estratto un componente condiviso `AsOfTodayAmount(amount, currency)` in `core/designsystem/component`, unica fonte per riga conti, intestazione di gruppo e breakdown della Dashboard: rimossa la copia privata `AsOfTodayLine` dalla schermata Conti, sostituito il `Text` nel breakdown. La riga "ad oggi" prominente della hero card (`BalanceAsOfTodayLabel`, icona + testo "ad oggi") resta invariata: e un contesto piu grande e piu esplicito, dove la parola aiuta.

**Decisioni:** componente condiviso invece di duplicare il composable tra due feature: e il senso della richiesta di uniformita (unica sorgente di verita per il trattamento). Messo in `designsystem/component` con riferimento a `MoneyFormatter` e alla stringa `dashboard_balance_as_of_today` (c'e gia precedente di componenti designsystem che usano `R.string`, es. `PlaceholderScreen`). Componente autosufficiente `(amount, currency)`: i chiamanti non ripetono formattazione, descrizione a11y e colore.

**Problemi:** nessuno.

**Verificato:** gate `assembleDebug testDebugUnitTest lint` verde (Gradle di sistema). versionCode 117 -> 118, versionName 0.9.78 -> 0.9.79. Nessun nuovo test (refactor presentazionale a parita di comportamento; i test di grouping restano validi).

**Prossimo:** niente in coda su questo.

---

## 2026-07-22 - Schermata Conti: piu spazio al nome, info sul riordino, "ad oggi" nei gruppi

**Fatto:** tre rifiniture alla schermata Conti dopo feedback su screenshot. (1) Il nome del conto veniva troncato: la causa principale era la riga secondaria "-2.115,89 € ad oggi", piu larga dell'importo, che dettava la larghezza della colonna a destra. Sostituita la parola "ad oggi" con il glifo calendario (`Today`, lo stesso che la hero card usa per "ad oggi") accanto all'importo: la colonna si stringe e il nome guadagna ~3 caratteri. Aggiunti anche una maniglia di drag piu stretta (40dp invece di 48, sempre alta 48 per il target) e padding del nome ridotto. Accessibilita preservata: il `contentDescription` recita la frase completa "X ad oggi". (2) `InfoBanner` in cima all'elenco (stesso componente della schermata Budget) che spiega che il riordino e possibile solo dentro lo stesso gruppo, cosi l'utente sa che non e un bug; mostrato solo quando un gruppo ha almeno 2 conti (riordino effettivamente possibile) e tenuto fuori dalla `LazyColumn` per non alterare lo spazio degli indici del drag. (3) Sottototale "ad oggi" nell'intestazione di gruppo sotto il totale, con lo stesso glifo, mostrato solo alla divergenza (qualche conto del gruppo ha movimenti datati nel futuro).

**Decisioni:** icona al posto della parola "ad oggi" sulle righe conto: e la leva a maggiore impatto sullo spazio del nome (recupera ~3 caratteri, contro ~1 di maniglia+padding) ed e coerente col glifo calendario gia usato dalla hero card. Piccolo componente condiviso `AsOfTodayLine` (icona + importo, rosso solo se negativo) riusato da riga e intestazione. Sottototale "ad oggi" solo alla divergenza, come tutte le righe "ad oggi" dell'app (niente rumore nel caso comune). Banner mostrato solo quando serve (un gruppo con >= 2 conti) e sopra la lista, non come primo item: cosi l'indice locale di `itemsIndexed` resta uguale all'indice assoluto della `LazyColumn` e il drag non si rompe.

**Problemi:** nessuno. Attenzione tenuta sul fatto che aggiungere un item prima della lista riordinabile avrebbe disallineato lo spazio degli indici del componente di drag: risolto tenendo il banner fuori dalla `LazyColumn`.

**Verificato:** gate `assembleDebug testDebugUnitTest lint` verde (Gradle di sistema). Nuovi unit test in `AccountsGroupingTest` (sottototale "ad oggi" solo alla divergenza, e assente altrimenti). versionCode 116 -> 117, versionName 0.9.77 -> 0.9.78.

**Prossimo:** eventuale allineamento della stessa treatment "ad oggi" (icona invece di parola) anche nel breakdown della card Saldo totale in Dashboard, per uniformita, se richiesto.

---

## 2026-07-22 - Schermata Conti: sottototali per tipo e riordino manuale con drag

**Fatto:** due rifiniture "premium" alla schermata Conti, dopo una valutazione condivisa con l'utente (punti 1, 2, 3). (1) Ogni intestazione di sezione tipo mostra ora il sottototale del gruppo (somma dei saldi delle righe), attenuato e rosso solo se negativo (es. carta di credito a metà ciclo), omesso quando il gruppo mescola valute. Niente grande totale in cima (scelta dell'utente): i sottototali fanno da riepilogo, così non si duplica l'hero della Dashboard. (2) Riordino manuale dei conti col trascinamento, confinato dentro ciascun gruppo di tipo: il raggruppamento e i sottototali restano coerenti, e l'ordine è condiviso con il breakdown della card Saldo totale in Dashboard (stesso comparatore).

**Decisioni:** riuso del componente in-repo `ReorderableListState` (già usato dalle Categorie), non una libreria esterna: l'utente chiedeva "la cosa più premium", ma guardando le Categorie si è visto che il drag è già un componente custom robusto del design system, quindi zero nuove dipendenze e due schermate unificate per costruzione. Esteso il componente con una guardia opzionale `canMove(from, to)` (default sempre true, Categorie invariate): per i Conti confina il target ai soli conti dello stesso tipo, così l'intestazione fa da barriera e il conto non esce dal gruppo. Lista attiva ristrutturata da "una card per gruppo" a righe-card individuali con intestazioni di sezione separate (stile Categorie), necessario perché il drag opera su item distinti della `LazyColumn`; archiviati invariati (card unica). Persistenza: nessuna migrazione, la colonna `sortOrder` esisteva già (schema, DAO, mapper) ma era di fatto inutilizzata (tutti a 0, la UI riordinava per tipo+nome); ora `accountOrder` ordina per `sortOrder` poi nome, `RoomAccountRepository.reorder` riscrive l'indice per-tipo, e un conto nuovo prende `nextSortOrder(type)` per accodarsi al suo gruppo. Sottototale: somma delle righe visibili (non filtrato per "escluso dal totale", coerente con le righe di questa schermata che non attenuano gli esclusi), solo se il gruppo è monovaluta.

**Problemi:** nessuno. Il target del drag poteva cadere su un'intestazione o su un altro gruppo: risolto con la guardia `canMove`, che salta i target non validi senza spostare (l'item torna al suo posto se si trascina oltre il bordo del gruppo).

**Verificato:** gate `assembleDebug testDebugUnitTest lint` verde (Gradle di sistema). Nuovi unit test in `AccountsGroupingTest` (posizione manuale vince sul nome, sottototale monovaluta, nessun sottototale multivaluta) e stub `nextSortOrder` nell'editor test. versionCode 115 -> 116, versionName 0.9.76 -> 0.9.77.

**Prossimo:** eventuale riordino anche dei gruppi tra loro (oggi l'ordine dei tipi è fisso: conto corrente per primo), se emergerà la richiesta.

---

## 2026-07-22 - Righe conti piu compatte e colore predefinito per tipo di conto

**Fatto:** due rifiniture dopo il feedback su screenshot. (1) Righe del breakdown conti piu compatte: padding verticale 4dp -> 2dp, altezza minima 44dp -> 40dp, e soprattutto altezza a due righe (caso "ad oggi") 48dp -> 44dp. Nello screenshot dell'utente un conto aveva la riga "ad oggi", che fissa tutte le righe all'altezza a due righe: e li che si vedeva l'aria, ora recuperata. (2) Alla creazione di un conto (non in modifica) la selezione del tipo preimposta anche un colore di default, con la stessa logica gia usata per l'icona (`userPickedColor` come guardia; in edit e in load il flag parte true, quindi non sovrascrive mai una scelta persistita). Nuovo `AccountVisuals.defaultColorFor(type)`. Allineato anche il conto creato in onboarding (sempre CHECKING) al nuovo default.

**Decisioni:** colori per tipo scelti su convenzioni comuni e per massima distinzione tra i sette tipi: blu per il conto corrente (colore universale della finanza), verde per il risparmio (denaro/crescita), ambra per i contanti (monete/banconote), ciano per la prepagata e indaco per il wallet digitale (due tinte "carta/digitale" tenute distinte dal blu del conto corrente), viola scuro per la carta di credito (feeling da carta, e volutamente non rosso per non confondersi col rosso "sotto zero" dell'app), grigio-blu neutro per "altro". Tutti i default sono membri della palette esistente, cosi il picker evidenzia lo swatch giusto. Altezza righe: 40/44dp sono sotto il minimo Material 48dp, scelta consapevole e limitata a questa card: senza il chip il glifo nudo lasciava troppa aria a 48dp; 44dp e il pavimento nel caso "ad oggi" (sotto si taglierebbe la seconda riga o la lista diventerebbe frastagliata).

**Problemi:** nessuno. Build locale col Gradle di sistema (il wrapper non scarica la distribuzione dietro il proxy).

**Verificato:** gate `assembleDebug testDebugUnitTest lint`. Nuovo unit test `type changes drive the color until the user picks one` (gemello di quello sull'icona). versionCode 114 -> 115, versionName 0.9.75 -> 0.9.76.

**Prossimo:** eventuale ulteriore compattazione righe solo se richiesto (sotto i 44dp servirebbe togliere la riga "ad oggi" o accettare una lista frastagliata).

---

## 2026-07-22 - Card Saldo totale: dettaglio conti configurabile e stato persistente

**Fatto:** cinque interventi sulla card Saldo totale, su richiesta utente. (1) Le icone dei conti nel breakdown perdono il chip colorato: ora sono glifi tinti col colore del conto, 24dp, allineati al bordo sinistro come le icone header delle card, cosi icona e nome cadono nella stessa colonna lungo tutta la Dashboard (stesso trattamento per la riga di overflow). (2) Righe piu compatte: padding verticale 6dp -> 4dp, altezza minima 44dp (prima 48dp erano dettati dal chip 36dp), riga a due righe "ad oggi" 52dp -> 48dp, gap divisore-prima riga 8dp -> 4dp. (3) Chiuso non mostra piu 2 conti ma nessuno: il breakdown e una sezione rivelata solo da espansa con `AnimatedVisibility` (expand + fade, comprende gap e divisore), e il chevron nell'header e sempre presente quando c'e almeno un conto. (4) Lo stato di apertura del breakdown conti e del dettaglio Spendibile oggi passa dal composable al `DashboardViewModel` (`StateFlow` + toggle): sopravvive allo scroll e alla navigazione tra schermate, torna al default solo alla riapertura dell'app. (5) Nuova preferenza `balance_accounts_expanded_default` (DataStore, default aperto) con switch "Dettaglio conti aperto" in Impostazioni > Dashboard.

**Decisioni:** stato di apertura nel ViewModel invece che in `rememberSaveable`: e l'unico modo per avere la semantica richiesta in modo deterministico (persiste per tutta la sessione, incluso scroll e cambio schermata perche il tab tiene vivi i ViewModel, ma si azzera al processo nuovo). `rememberSaveable` sopravviverebbe anche alla process death via saved instance state, quindi non tornerebbe al default in modo prevedibile. Il default conti e un `combine(override, defaultDaSettings)` con override null = "segue il default": cambiare il default in Impostazioni aggiorna la card live finche l'utente non tocca il chevron in quella sessione. Spendibile oggi resta senza default persistito (parte sempre chiuso), coerente con la richiesta che chiedeva solo di renderne lo stato non volatile durante lo scroll/navigazione. Altezza riga 44dp invece di 48dp: scelta di compromesso: senza il chip 36dp il vecchio 48dp lasciava troppa aria attorno al glifo nudo (il punto della richiesta), 44dp resta un target ampio; segnalato all'utente come lieve scostamento dal minimo Material 48dp, reversibile.

**Problemi:** build locale non eseguibile col wrapper (il proxy risponde 403 sul download della distribuzione Gradle da github); usato il Gradle di sistema 8.14.3 (stessa versione del wrapper) che scarica plugin e dipendenze dai repo Maven attraverso il proxy.

**Verificato:** gate `assembleDebug testDebugUnitTest lint` (Gradle di sistema). versionCode 113 -> 114, versionName 0.9.74 -> 0.9.75.

**Prossimo:** eventuale rifinitura ulteriore della compattezza righe se l'utente vuole spingersi sotto i 44dp.

---

## 2026-07-21 - Card Ricorrenti: spese mensili dal rosso al ruolo expense

**Fatto:** nella card Ricorrenti il totale mensile delle spese ricorrenti passa da `moneyColors.negative` (rosso, applicato ogni volta che il totale era > 0, cioe quasi sempre) al ruolo `moneyColors.expense` (neutro, `onSurface`): il segno meno porta la direzione. Il ramo condizionale sparisce: entrambe le alternative convergevano sullo stesso colore. Entrate ricorrenti invariate (verdi se > 0).

**Decisioni:** esito di una ricognizione richiesta dall'utente su come tutte le card della dashboard colorano gli importi. Tre famiglie coerenti: saldi (rosso solo se sotto zero), flussi (colore per direzione/tipo: entrate verdi, spese neutre), soglie (container + icona, mai solo colore). Unica incoerenza trovata: questa card usava `negative` (riservato dai ruoli documentati in `MoneyColors` ai saldi sotto zero come warning) per un flusso normale e pianificato, diluendo il valore del rosso nella dashboard. Deciso anche di NON rendere rosso il netto negativo delle card Oggi/mese: e lo stato normale (si spende ogni giorno, si incassa una volta al mese) e la dashboard sarebbe perennemente in allarme.

**Verificato:** verifica statica (nessun SDK in locale): sostituzione di un colore con un ruolo esistente del design system, nessuna nuova API; build, lint e unit test delegati alla CI GitHub. versionCode 112 -> 113, versionName 0.9.73 -> 0.9.74. Stesso branch/PR della linea dello zero.

---

## 2026-07-21 - Pill del forecast in errorContainer quando la stima e negativa

**Fatto:** il pill "≈ importo" sulla coda forecast della sparkline ora usa la coppia `errorContainer`/`onErrorContainer` quando la proiezione a fine mese e negativa; altrimenti resta `secondaryContainer`/`onSecondaryContainer` come prima. L'anello tratteggiato di fine mese resta neutro (colore linea attenuato).

**Decisioni:** era l'unico valore della card fuori dalla regola "rosso solo se negativo" (cifra principale, righe "ad oggi", saldi per conto la seguono gia), ed e la previsione piu actionable del grafico. Coppia container Material standard invece di tingere solo il testo: contrasto garantito in entrambi i temi. La semantica di incertezza resta alla forma ("≈" + tratteggio), il colore porta solo il segno; niente variante positiva (regola asimmetrica, come nel resto della card). Il warning sta nel pill, non nella geometria. Proposta discussa e confermata dall'utente.

**Verificato:** verifica statica (nessun SDK in locale): solo scelta di colori da `MaterialTheme.colorScheme`, nessuna nuova API; build, lint e unit test delegati alla CI GitHub. versionCode 111 -> 112, versionName 0.9.72 -> 0.9.73. Stesso branch/PR della linea dello zero.

---

## 2026-07-21 - Card Saldo totale: ritmo verticale piu compatto

**Fatto:** su feedback utente (dopo il test su device della linea dello zero) ridotto lo stacco tra importo e sparkline nella hero card: nuovo `BALANCE_SPARKLINE_TOP_GAP = 8.dp` al posto del generico `BALANCE_SECTION_GAP` (12dp) prima del grafico; col 4dp di inset del canvas il gap visivo passa da 16 a 12dp. Lo stacco prima della sezione conti resta 12dp.

**Decisioni:** gap asimmetrico voluto: la sparkline e una lettura dell'importo (prossimita = appartenenza), la sezione conti e un blocco distinto e tiene lo stacco pieno. Provata anche la compattazione delle righe conto del breakdown (`BALANCE_ROW_PADDING_VERTICAL` 6 -> 4dp, riga da 48 a 44dp tappabili): scartata e ripristinata a 6dp su decisione dell'utente, che vuole attenersi allo standard Material del touch target 48dp.

**Verificato:** verifica statica (nessun SDK in locale): solo costanti dp e un rename di Spacer, nessuna nuova API; build, lint e unit test delegati alla CI GitHub. versionCode 109 -> 111, versionName 0.9.70 -> 0.9.72 (0.9.71 il giro intermedio con le righe a 44dp). Stesso branch/PR della linea dello zero.

---

## 2026-07-21 - Sparkline Saldo totale: linea dello zero e fill ancorato allo zero

**Fatto:** la sparkline della card "Saldo totale" ora disegna una baseline dello zero quando il range plottato (storico + forecast) attraversa lo zero: linea puntinata fine (1dp, trattini da 1dp con cap arrotondato che rendono come puntini, gap 3dp) in `outlineVariant` (lo stesso colore hairline dei divider della card), disegnata per prima dentro la clip del reveal cosi resta dietro a fill, curva e coda forecast. Quando la baseline e visibile, il gradiente di riempimento si ancora alla quota zero invece che al fondo del canvas (clip a `zeroY` + `endY` del gradiente a `zeroY`): l'area tinta esiste solo sopra lo zero, sotto resta la sola curva, cosi il tratto negativo non porta "massa" visiva. Nuova funzione pura `zeroLineFraction(min, max)` in `BalanceSparkline.kt` con 5 test JVM in `SparklineGeometryTest` (range che attraversa, simmetrico, tutto positivo, tutto negativo, range che tocca lo zero senza attraversarlo).

**Decisioni:** la linea compare solo con attraversamento stretto (`min < 0 && max > 0`): se il minimo o il massimo toccano esattamente lo zero coinciderebbe col bordo della curva e leggerebbe come una sottolineatura spuria. Stile puntinato (non tratteggiato) per non confondersi semanticamente con la coda forecast, che usa trattini lunghi e significa "stima". Niente etichetta "0": su 56dp di altezza sarebbe rumore. Scelte (puntinato + fill ancorato allo zero) confermate dall'utente tra le alternative proposte.

**Verificato:** verifica statica (nessun SDK Android in locale): riletto il diff, controllate firme di `drawLine`/`PathEffect.dashPathEffect`/`clipRect` gia usate nel file, detekt ok per costruzione (`LongMethod` ignora `@Composable`, `MagicNumber` disattivo). Build, lint e unit test delegati alla CI GitHub. versionCode 108 -> 109, versionName 0.9.69 -> 0.9.70.

---

## 2026-07-21 - Saldo ad oggi in rosso quando negativo

**Fatto:** la riga "ad oggi" (per-conto in Conti e nel breakdown, piu quella globale sotto il Saldo totale nella hero card) ora e rossa (`moneyColors.negative`) quando il valore e negativo, altrimenti resta grigia attenuata (`onSurfaceVariant`). Sulla riga globale (`BalanceAsOfTodayLabel`) il colore si applica in modo coerente a icona e testo.

**Decisioni:** rosso solo nel negativo (non specchio pieno del colore dell'importo principale): nel positivo la riga resta attenuata per non diventare un secondo numero forte. Il caso che la scelta serve a evidenziare e "saldo totale positivo ma saldo ad oggi negativo" (entrata futura gia registrata, ma oggi si e in rosso): li la cifra principale e neutra e l'unico segnale di allerta e la riga ad oggi. La subordinazione resta garantita dalla dimensione (`labelSmall`/`bodyMedium`) e il segno meno accompagna il colore (accessibilita). Idea e conferma dell'utente.

**Verificato:** `gradle testDebugUnitTest assembleDebug lint` verde (Gradle di sistema). versionCode 107 -> 108, versionName 0.9.68 -> 0.9.69.

---

## 2026-07-21 - Saldo ad oggi per singolo conto (Dashboard + Conti)

**Fatto:** esteso il concetto di "saldo ad oggi" (fin qui solo globale, sulla hero card) al singolo conto, mostrato solo quando diverge dal saldo totale del conto, cioè quando esistono movimenti confermati datati nel futuro. Nuova query DAO `AccountDao.observeAllBalancesAsOf(endEpochDayExclusive)`: gemella di `observeAllWithBalance` con in piu il filtro sul giorno locale del movimento (`(timestampEpochMilli/1000 + zoneOffsetSeconds)/86400 < :endEpochDayExclusive`), stesso pattern della serie giornaliera della sparkline; nuova relation `AccountBalanceAsOfRow(accountId, balanceMinor)`. Il modello `AccountWithBalance` ha ora un campo opzionale `balanceAsOfToday: BigDecimal? = null`, valorizzato solo alla divergenza. Nuovo metodo repository `observeAccountsWithBalanceAsOfToday(todayEpochDayExclusive)` che combina saldo totale e saldo ad oggi per conto (`balanceAsOfToday = today.takeIf { it != total }`). `AccountsViewModel` inietta `Clock` e usa `midnightTicker` per ri-ancorare "oggi"; `DashboardViewModel` arricchisce la lista del breakdown dentro il `flatMapLatest` dove "oggi" e gia disponibile. Il vecchio `observeAccountsWithBalance()` (30+ chiamanti) resta invariato.

UI: in `AccountRowContent` (schermata Conti) e `AccountBreakdownRow` (card Saldo totale) l'importo diventa una `Column` allineata a destra; sotto, solo alla divergenza, una riga `labelSmall` attenuata "%1$s ad oggi" (riuso della stringa esistente `dashboard_balance_as_of_today`).

**Decisioni:** vincolo dell'utente: la riga non deve crescere ne saltare quando compare il valore. Nella schermata Conti l'altezza e gia dettata dall'avatar 44dp, quindi la seconda riga (~40dp con l'importo) ci rientra senza modifiche. Nel breakdown Dashboard (avatar 36dp) la seconda riga sforerebbe: si riserva l'altezza a due righe (`BALANCE_ROW_TWO_LINE_HEIGHT = 52.dp`) su tutte le righe **solo quando almeno un conto diverge** (`accounts.any { it.balanceAsOfToday != null }`), cosi il caso comune resta compatto e, quando serve, tutte le righe sono uniformi (niente lista frastagliata). Divergenza calcolata come per il globale (`compareTo != 0`).

**Verificato:** `gradle testDebugUnitTest assembleDebug lint` verde (usato il Gradle di sistema `/opt/gradle`: il wrapper non puo scaricare la distribuzione da github per policy di egress). Aggiornati gli stub MockK in `AccountsViewModelTest` (nuovo param `clock`) e `DashboardViewModelTest`. Nuovo test strumentato `SaldoDatabaseTest.balancesAsOfExcludeMovementsDatedAfterCutoff` (movimento datato oggi contato, movimento e gamba trasferimento datati in futuro esclusi; il totale li include).

**Prossimo:** verifica su device del comportamento (nessun salto di altezza) e coerenza somma per-conto vs saldo ad oggi globale.

---

## 2026-07-21 - Icona app: gradiente + thumb-notch, e app icon nell'onboarding

**Fatto:** rivisto il disegno dell'icona (placeholder) su due assi. (1) Colore: ogni elemento passa da tinta piena a un gradiente lineare 45 gradi dalla tonalità base (in basso a destra) a una più chiara (in alto a sinistra), colori base invariati; aggiornati `ic_launcher_foreground.xml` (gradienti via `aapt:attr`) e, in sincrono, `ic_launcher_monochrome.xml`. (2) Dettaglio focale: rimosso il bottone-clasp tondo verde (troppo comune tra le icone-wallet del Play Store) e sostituito con un thumb-notch, un incavo semicircolare sul bordo superiore della tasca che lascia intravedere il blu del corpo (dettaglio da portacarte reale). Il launcher, lo splash di sistema (Android 12+, nessun tema splash custom) e il logo della About (`AppLogo` usa `painterResource(ic_launcher_foreground)`) si allineano da soli.

Onboarding: le pagine statiche 1/2/5 (benvenuto, privacy, notifiche) non usano più i glifi Material ma l'icona dell'app. Su richiesta dell'utente l'icona è **nuda** (nessuno sfondo bianco: il disegno vive direttamente sulla schermata) e circa al doppio, `ONBOARDING_APP_ICON_SIZE = 240.dp` (prima badge 96dp). Nuovo `OnboardingIcon.kt`: `AppIconArtwork` disegna il foreground con overdraw 108/72 (`requiredSize`) dentro un box della dimensione target, così ritaglia alla finestra centrale del launcher e la riempie, con i margini trasparenti che tracimano fuori dal box senza influire sul layout. `WelcomeAppIcon` (reveal animato) e i badge d'angolo. Pagina 1: le due carte scendono da fuori schermo e si infilano dietro al wallet (prima la gialla, poi la rossa, sfalsate di 170ms), ricomponendo l'icona; realizzato stratificando tre drawable dedicati (`ic_app_icon_card_back`, `ic_app_icon_card_front`, `ic_app_icon_wallet`) che condividono viewport e group con il foreground, così a riposo coincidono col launcher. Pagina 2: badge circolare verde brand (`#34A853`) con scudo (`VerifiedUser`). Pagina 5: badge rosso brand (`#EA4335`) con campana (`NotificationsActive`). Senza il quadrato bianco l'angolo del box non coincide più con l'angolo del disegno: il chip è posizionato con un offset sulla "spalla" in alto a destra del wallet.

**Decisioni:** animazione one-shot, non in loop e non bloccante (la CTA è sempre attiva); rispetta il setting di sistema (`ANIMATOR_DURATION_SCALE == 0` -> icona finale senza moto). Approccio a layer di drawable (non Canvas/PathParser): condividendo viewport e transform, gli strati si allineano al pixel senza calcoli di coordinate. Carte non clippate al squircle così entrano "da fuori"; il wallet frontale è clippato e opaco, quindi copre la metà bassa delle carte a riposo (effetto "infilate"). Colori badge presi dalla palette brand (verde/rosso), entrambi >= 3:1 su glifo bianco; significato portato dalla forma oltre che dal colore (accessibilità). Icona ancora placeholder: il checkbox "Icona app, screenshot, scheda Play Store" (Fase 10) resta aperto perché copre anche screenshot e scheda.

Anche la About mostra ora l'icona nuda (rimosso il cerchio bianco `ic_launcher_background` in `AppLogo`, stessa tecnica overdraw 108/72), leggermente più grande (`LOGO_SIZE` 96 -> 120dp). Onboarding rifinito a 220dp (un filo meno di 240). Lo splash di sistema (Android 12+, nessun tema custom) usa già solo il foreground dell'adaptive icon sul window background: il quadrato bianco dello sfondo adattivo non compare nello splash, quindi nessuna modifica lì.

**Problemi:** nessun emulatore in questo ambiente: `assembleDebug testDebugUnitTest lint` verde, ma la resa a video dell'animazione, dei badge e dell'icona nuda su About/splash è da confermare su device.

**Prossimo:** verifica su device del reveal e dei badge; scelta definitiva del brand prima della pubblicazione.

**Verifica:** `gradle assembleDebug testDebugUnitTest lint` (BUILD SUCCESSFUL). versionCode 103 -> 106, versionName 0.9.64 -> 0.9.67 (0.9.65 primo giro con squircle bianco, 0.9.66 icona nuda 2x nell'onboarding, 0.9.67 onboarding a 220dp + About nuda 120dp - tutti su feedback utente).

---

## 2026-07-20 - Card Saldo totale: riga "ad oggi" quando ci sono movimenti futuri

**Fatto:** la cifra grande della card Saldo totale è `initialBalance + Σ di tutti i movimenti confermati`, senza vincolo di data, mentre il punto "oggi" della sparkline (e la coda di previsione) considera solo i movimenti datati fino a oggi. Con movimenti confermati datati in futuro (l'editor non vincola la data massima né li marca pending, quindi entrano subito nel saldo) le due cifre divergono e la card mostrava un totale che non coincide con il grafico. Aggiunto allo `DashboardUiState` il campo `balanceAsOfToday: BigDecimal?`, calcolato in `buildState` come ultimo punto dello storico giornaliero (`balanceHistory.last().balance`, cioè il saldo datato fino a oggi) e valorizzato **solo quando diverge** da `totalBalance` (`compareTo != 0`), altrimenti null. Quando presente, la `BalanceCard` mostra sotto la cifra grande una riga secondaria attenuata con icona calendario (`Icons.Outlined.Today`, `onSurfaceVariant`, `bodyMedium` tabellare): "€X ad oggi" / "€X as of today". Nuove stringhe `dashboard_balance_as_of_today` (values + values-it).

**Decisioni:** scelta l'opzione additiva (cifra grande invariata, all-in) e non la ridefinizione del saldo come "a oggi": quest'ultima avrebbe cambiato l'ADR 3 (`saldo = initialBalance + Σ movimenti`), reso il saldo dipendente dal tempo (ri-aggancio a mezzanotte) e richiesto coerenza su schermata Conti, rettifica saldo (correttezza dell'ADJUSTMENT) e grafico saldo in Statistiche: fuori portata per una card. La riga compare solo alla divergenza, così quando il totale è già il valore di oggi la card resta pulita. Nessuna query nuova: il valore "ad oggi" è già in stato come ultimo punto dello storico giornaliero. Niente marcatore sulla sparkline (scelta condivisa con l'utente: solo la riga testuale). Testo italiano "ad oggi" (forma eufonica).

**Verifica:** `gradle testDebugUnitTest lint assembleDebug` verde (`gradle` di sistema 8.14.3, dist in cache: il download dal wrapper è bloccato dalla policy di rete). Aggiunti due test in `DashboardViewModelTest`: divergenza (headline 120,00 / oggi 100,00 → `balanceAsOfToday` = 100,00) e coincidenza (→ null). Bump `versionCode` 102→103, `versionName` 0.9.63→0.9.64.

---

## 2026-07-20 - Card Saldo totale: stesso ordine dei conti dell'elenco Conti

**Fatto:** il dettaglio dei conti nella card Saldo totale in Dashboard usava l'ordine del DAO (`sortOrder ASC, id ASC`), che per i conti equivale all'ordine di inserimento (`sortOrder` non è mai impostato: nessuna UI di riordino, l'editor scrive `base?.sortOrder ?: 0`). L'elenco Conti invece ordina per tipo (ordine di dichiarazione dell'enum `AccountType`) poi per nome case-insensitive. Le due schermate mostravano quindi i conti in ordini diversi. `DashboardViewModel.buildState` ora applica alla lista `accounts` esposta nello UI state l'estensione esistente `sortedByTypeThenName()` (la stessa che alimenta la sezione archiviati dell'elenco Conti), così la card e l'elenco concordano. Il totale (`fold` sui saldi) e `hasAccounts` sono indipendenti dall'ordine, quindi invariati.

**Decisioni:** riuso della funzione pura `sortedByTypeThenName` invece di duplicare il comparatore, per avere un'unica fonte di verità sull'ordine dei conti. Caveat annotato: se in futuro si introdurrà un riordino manuale basato su `sortOrder`, quella diventerà la fonte di verità e andrà applicata a entrambe le schermate insieme.

**Verifica:** `gradle testDebugUnitTest lint assembleDebug` verde (usato `gradle` di sistema 8.14.3 con dist in cache: il download della distribuzione dal wrapper è bloccato dalla policy di rete). Aggiunto in `DashboardViewModelTest` un test che alimenta conti in ordine mescolato (tipi e nomi vari) e verifica l'ordine tipo-poi-nome nel dettaglio; esteso l'helper `account()` con `name`/`type`. Bump `versionCode` 101→102, `versionName` 0.9.62→0.9.63.

---

## 2026-07-20 - Ordinamento Obiettivi di risparmio (per nome) e Budget (spareggio per nome)

**Fatto:** due ordinamenti resi deterministici.
- Obiettivi di risparmio: `ObserveSavingsGoalsProgressUseCase` ora restituisce gli obiettivi ordinati alfabeticamente per nome (case-insensitive, tie-break sull'id) invece che nell'ordine di creazione (`sortOrder ASC, id ASC` del DAO, di fatto creazione perché non esiste UI di riordino). Il totale risparmiato resta nella hero card, indipendente dall'ordine.
- Budget: `ObserveBudgetProgressUseCase` manteneva già i budget per categoria ordinati per frazione decrescente (più vicini al tetto per primi), con il budget complessivo in cima. A inizio mese, con spese a zero, tutte le frazioni pareggiano e l'ordine ripiegava sull'id (aspetto casuale). Aggiunto lo spareggio per nome categoria (case-insensitive) prima dell'id: la lista è alfabetica a spesa zero e diventa "a rischio in cima" man mano che si spende.

**Decisioni:** per gli obiettivi si è scelto il nome e non la scadenza (`targetDate`): molti obiettivi non hanno data e mischiare con/senza data rende la lista incoerente; il nome è prevedibile e coerente con Conti. Per i budget si è scelto di NON passare all'alfabetico puro (perderebbe il segnale "a rischio sforamento", l'informazione utile della schermata) ma solo di correggere lo spareggio. Entrambe le scelte confermate dall'utente.

**Verifica:** `gradle testDebugUnitTest detekt lint` verde. Aggiunti test: spareggio per nome a frazioni pari in `ObserveBudgetProgressUseCaseTest`, ordinamento alfabetico case-insensitive in `ObserveSavingsGoalsProgressUseCaseTest`. Bump `versionCode` 100→101, `versionName` 0.9.61→0.9.62.

**Nota CI:** il commit precedente (raggruppamento Conti) aveva fatto fallire detekt con `MatchingDeclarationName` (`AccountsGrouping.kt` con la sola classe top-level `AccountTypeGroup`); risolto spostando il data class in `AccountsUiState.kt`, così il file *Grouping contiene solo funzioni, come nel feature transactions.

---

## 2026-07-20 - Elenco Conti raggruppato per tipo e ordinato per nome

**Fatto:** la schermata elenco Conti (`AccountsScreen`) non aveva ordinamento e mostrava i conti nell'ordine di creazione. Ora i conti attivi sono raggruppati per tipo conto e ordinati alfabeticamente per nome all'interno di ogni gruppo. Aggiunto `AccountsGrouping.kt` con la funzione pura `buildAccountTypeGroups` (raggruppa per `AccountType`, ordina i gruppi per ordine di dichiarazione dell'enum e i conti per nome case-insensitive, con tie-break stabile sull'id) e l'estensione `sortedByTypeThenName` usata dalla sezione archiviati. Lo `AccountsUiState` espone `activeGroups: List<AccountTypeGroup>` (calcolato nel ViewModel) al posto della lista piatta `active`; `archived` è ora ordinata con lo stesso criterio. La UI rende ogni gruppo con un'intestazione di sezione (etichetta del tipo, `titleMedium`) seguita dalla card raggruppata, replicando il ritmo header/card dell'elenco movimenti (pattern `DayHeader`). L'etichetta del tipo è rimossa dalla riga dei conti attivi (ridondante con l'header, `showType = false`) e mantenuta nella card archiviati (senza header).

**Decisioni:** l'ordine dei gruppi usa l'ordinale dell'enum `AccountType`, già dichiarato con `CHECKING` (conto corrente) per primo: rispetta la richiesta senza introdurre una tabella di priorità da mantenere. Nessun subtotale per gruppo nell'intestazione: mischierebbe conti inclusi/esclusi dal totale e valute diverse, creando ambiguità su cosa somma. La sezione archiviati resta una card unica collassabile (area secondaria) ma ordinata con lo stesso criterio per coerenza.

**Verifica:** `gradle assembleDebug testDebugUnitTest lint` verde (wrapper offline per il download della distribuzione, usato `gradle` di sistema con la dist in cache). Aggiunto `AccountsGroupingTest` (ordine dei gruppi, alfabetico case-insensitive, tie-break per id, flatten `sortedByTypeThenName`); aggiornato `AccountsViewModelTest` al nuovo `activeGroups`. Bump `versionCode` 99→100, `versionName` 0.9.60→0.9.61.

**Prossimo:** eventuale valutazione di header anche per la sezione archiviati se il numero di conti archiviati cresce.

---

## 2026-07-20 - Messaggio dell'editor obiettivi di risparmio quando i conti sono tutti occupati

**Fatto:** l'empty-state dell'editor obiettivi di risparmio (`SavingsGoalEditorScreen`) ora distingue due situazioni prima confuse sotto lo stesso testo "Serve un conto di risparmio ... Creane uno per iniziare". Il modello resta 1:1 (un obiettivo per conto di risparmio, `UNIQUE(accountId)`, ADR 25): quando tutti i conti di risparmio hanno già un obiettivo l'editor non ha conti "liberi" e mostrava lo stesso messaggio del caso "nessun conto di risparmio", facendolo sembrare un bug ("mi chiede di creare un conto ogni volta"). Aggiunto in `SavingsGoalEditorUiState` il flag `hasSavingsAccounts` (esiste almeno un conto SAVINGS non archiviato, a prescindere che sia libero), calcolato in `onData` e propagato da `initCreate`/`refreshCreateOptions`. La schermata sceglie il testo: se `hasSavingsAccounts` è true usa le nuove stringhe `savings_editor_all_taken_title/body` ("I tuoi conti di risparmio sono tutti in uso ... crea un altro conto o modifica un obiettivo esistente"), altrimenti quelle esistenti. La CTA resta "Crea conto di risparmio".

**Verificato:** verifica statica severa (nessun SDK Android in locale, build/test in CI GitHub). Aggiunti due unit test JUnit5 in `SavingsGoalEditorViewModelTest`: nessun conto risparmio (`noAvailableAccounts` true, `hasSavingsAccounts` false) e unico conto già occupato da un obiettivo (`noAvailableAccounts` true, `hasSavingsAccounts` true). Parità chiavi stringhe IT/EN.

**Decisioni:** solo chiarimento del messaggio, nessun cambio al modello 1:1 (confermato con l'utente): un secondo obiettivo richiede un secondo conto di risparmio, coerente con il modello pot/vault e col saldo come single source of truth (ADR 25). Riuso della stessa CTA perché l'azione utile resta creare un nuovo conto.

**Problemi:** nessuno in scrittura; build e test da confermare in CI.

**Prossimo:** nessuno pianificato.

---

## 2026-07-20 - Rifinitura premium della card Saldo (Dashboard)

**Fatto:** revisione UI/UX della hero card del saldo (`BalanceCard` in `DashboardCards.kt`), sette interventi. (1) La cifra del saldo totale ha ora una banda di auto-size dedicata e più grande (`BALANCE_MONEY_MIN/MAX` = 24/34sp) rispetto agli altri numeri hero (`HERO_MONEY_*` restano 20/28sp, usati dalla card Spendibile oggi), così è il numero primario della schermata. (2) Il breakdown per conto mostra i primi 2 conti (`ACCOUNT_PREVIEW_COUNT`) e, quando ce ne sono di più, un chevron `ExpandMore` nell'header (stessa icona/dimensione/posizione della card Spendibile oggi) espande in place i restanti con `animateContentSize` fino a un massimo di 10 (`ACCOUNT_EXPANDED_MAX`); oltre i 10 una riga di overflow con glifo `MoreHoriz` e conteggio (nuovo plural `dashboard_accounts_overflow`) rimanda all'elenco conti completo. L'espansione è transitoria (`remember`, non `rememberSaveable`): alla riapertura dell'app torna collassata. (3) Il count-up del saldo usa `rememberSaveable`, così sopravvive allo scroll fuori/dentro il `LazyColumn`: parte da zero solo alla prima apertura e poi anima solo al cambio reale del valore, non più da zero a ogni rientro in viewport (allineati commento e implementazione). (4) Un `HorizontalDivider` separa il blocco totale+sparkline dalle righe dei conti. (5) Le righe dei singoli conti sono ora toccabili e aprono il dettaglio del conto (`AccountEditorRoute(id)` via nuovo callback `onNavigateToAccount`), mentre il resto della card resta "Gestisci conti"; nuova azione vocale `dashboard_account_open`. (6) Spaziatura interna tokenizzata (`BALANCE_AMOUNT_TOP_GAP`, `BALANCE_SECTION_GAP`, `BALANCE_BREAKDOWN_TOP_GAP`, `BALANCE_ROW_PADDING_VERTICAL` ridotto a 6dp per conti più ravvicinati). (7) Gradiente decorativo da diagonale (`linearGradient`) a verticale top-down (`verticalGradient`), lettura più pulita.

**Verificato:** verifica statica severa (nessun SDK Android in locale, build/test in CI GitHub). Controllati a mano: nessun chiamante residuo con la firma vecchia di `BalanceCard`/`DashboardScreen`/`AccountBreakdownRow`, `mutableLongStateOf` non più usato e import rimosso, `HERO_MONEY_*` ancora referenziati da `BudgetDashboardCards`, riuso delle stringhe già esistenti `dashboard_accounts_expand/collapse`, parità chiavi stringhe IT/EN per le nuove risorse, nessuna stringa hardcoded. Nessun test referenzia le composable modificate; i test del `DashboardViewModel` restano validi (nessun cambio di stato/dominio).

**Decisioni:** costanti di dimensione separate per il saldo invece di alzare `HERO_MONEY_*` condivise, per non ingrandire anche la cifra della card Spendibile oggi e creare la gerarchia saldo > spendibile > card compatte. Il breakdown espande in place (chevron nell'header come Spendibile oggi) invece di navigare via, così l'utente vede tutti i conti senza lasciare la Dashboard; cap a 10 con riga di overflow verso l'elenco per non far crescere la card all'infinito. Stato di espansione con `remember` (non `rememberSaveable`) perché deve tornare collassato alla riapertura dell'app, non restare fisso. Chevron con click proprio (annidato nell'header cliccabile) così il tap espande invece di navigare a "Gestisci conti". Count-up con `rememberSaveable { mutableStateOf(0L) }` (Long boxed, valore di sola presentazione: costo trascurabile) invece di sollevare lo stato nel ViewModel. Le righe conto usano `clickable` annidato dentro la card cliccabile: il tap sulla riga viene consumato dalla riga, il resto dalla card. Nessuna modifica a query, mapper o regole importi: il count-up resta puramente presentazionale con snap finale al valore esatto.

**Problemi:** nessuno in scrittura; build e test da confermare in CI.

**Prossimo:** nessuno pianificato.

---

## 2026-07-20 - Indicatore movimenti da ricorrenza (lista + editor) e filtro per origine

**Fatto:** i movimenti generati da una regola ricorrente sono ora riconoscibili nell'app. Nella lista (registro Movimenti, ultimi movimenti della Dashboard e drill-down delle statistiche, tutti resi da `TransactionRowContent`) compare un piccolo segno prima dell'importo: icona `Repeat` da 16dp in `onSurfaceVariant`, con `contentDescription` dedicata per TalkBack (non affidato al solo colore), accanto all'icona già esistente di "escluso dalle statistiche". Nell'editor movimento, quando si modifica un record generato, un `InfoBanner` in cima al form lo segnala con il nome della regola (recuperato via `RecurringRuleRepository.getRule`, sempre risolvibile perché la FK `recurringRuleId` è `SET_NULL` alla cancellazione della regola) o con un testo generico se la regola non è più risolvibile; il banner chiarisce che le modifiche valgono solo per quel movimento e la regola resta invariata. Nel registro, nuovo filtro per origine: sezione "Origine" nel filter sheet con due chip tri-state (Ricorrenti / Manuali), chip rimovibile nella barra dei filtri attivi e conteggio nel badge. Aggiunta l'estensione di dominio condivisa `Transaction.isRecurring` (`recurringRuleId != null`), che centralizza il discriminatore prima duplicato nel CSV builder e ora usato anche da riga, motore filtri ed editor.

**Verificato:** verifica statica severa (nessun SDK Android in locale, build/test in CI GitHub). Test unit JUnit5: `TransactionFilterEngineTest` (filtro origine ricorrenti/manuali, `activeCount` con il nuovo gruppo), `TransactionEditorViewModelTest` (flag `isRecurring` e nome regola caricati per un movimento generato, movimento manuale non marcato; aggiornato il mock del nuovo `RecurringRuleRepository` nel costruttore). Controllati a mano: righe <120, parità chiavi stringhe IT/EN, nessuna stringa hardcoded.

**Decisioni:** riuso del dato esistente senza toccare schema o query - il filtro resta in memoria come tutto il motore filtri (un solo code path). Segno di riga discreto (icona muta + descrizione) invece di badge testuale, per non appesantire il registro come richiesto. I campi read-only della ricorrenza nell'editor stanno nel `Form` ma fuori dallo snapshot di dirty detection, così non marcano mai il form come modificato. Icona `Repeat` per coerenza con l'editor delle regole ricorrenti (stessa icona).

**Problemi:** nessuno in scrittura; build e test da confermare in CI.

**Prossimo:** nessuno pianificato.

---

## 2026-07-20 - Import CSV dei movimenti (con riconoscimento formato e report) + colonna "Ricorrente" in export

**Fatto:** aggiunta l'importazione dei movimenti da file CSV, dalla schermata Movimenti (menu overflow "Importa CSV", disponibile anche a registro vuoto). Il layer di parsing/analisi è puro e testabile (package `feature/transactions/importer`): `CsvReader` (RFC 4180 tollerante: BOM, quote, CRLF/LF/CR), `CsvSeparatorSniffer` (rileva `;`/`,`/tab dalla riga di intestazione, non dai dati, così una virgola decimale non viene mai scambiata per separatore di campo), `CsvFieldParsers` (importi con entrambe le convenzioni decimali e separatori di migliaia, parentesi = negativo; date ISO ed europee; valute ISO 4217), `CsvHeaderMapper` (mappa le colonne per nome con etichette localizzate + alias IT/EN, ordine libero, minimo data+importo), `TransactionCsvAnalyzer` (risolve conti/categorie/tag per nome, deduce il tipo dal segno, normalizza il segno, deriva la valuta dal conto, rileva i duplicati contro il registro e nel file, produce un esito per riga). `TransactionsCsvImporter` (Android) legge l'Uri su dispatcher IO, costruisce il contesto dai repository, e in commit crea le entità mancanti e inserisce i movimenti in un'unica transazione (`TransactionRunner`), restituendo un report. UI: `CsvImportSheet` a tre stati (lettura, anteprima con conteggi + opzioni, report finale). Wiring in `TransactionsViewModel`/`TransactionsScreen`. Esportazione: aggiunta la colonna informativa "Ricorrente" (Sì quando il movimento viene da una regola ricorrente); non viene reimportata (un movimento importato è manuale).

**Verificato:** verifica statica severa (niente SDK Android in locale, build/test delegati alla CI GitHub). Test unit JUnit5 nuovi: `CsvParsingTest` (reader, sniffer, parser di importi/date/valute, mapper header), `TransactionCsvAnalyzerTest` (riga pulita, duplicato su registro, duplicato nel file, normalizzazione segno, inferenza tipo, creazione conto/categoria/tag, conto sconosciuto con creazione off, mismatch valuta, riga con errori multipli, drop tag, trasferimento stessa valuta, trasferimento incompleto, righe vuote ignorate, categoria su trasferimento non creata). Aggiornato `TransactionCsvBuilderTest` per la nuova colonna e aggiunto il test del flag ricorrente. `TransactionsViewModelTest` riceve il mock del nuovo importer. Controllati a mano: nessuna riga >120 nei sorgenti main, nessun `import` keyword come segmento di package (uso `importer`), plurals e stringhe presenti in IT ed EN.

**Decisioni:** solo inserimento, mai modifica/eliminazione dell'esistente, e commit tutto-o-niente (sicurezza). Duplicati riconosciuti per data+tipo+importo+valuta+conto+descrizione, in `MovementSignature` condiviso tra registro e righe importate. Il segno e la convenzione decimale dell'export restano abbinati al separatore di colonna, così non collidono mai (nota confermata con l'utente). La colonna "Ricorrente" è solo informativa in export: reimportare `recurringRuleId` collegherebbe a una regola inesistente nel database di destinazione e toccherebbe il vincolo anti-doppia-generazione, quindi non viene ricostruita. Deroga mirata in `detekt.yml` per `ReturnCount` (parser a guardie con return anticipato) invece di `@Suppress` sparsi.

**Protezione formula injection (dalla review dell'export):** i soli campi testuali (descrizione, nota, categoria, conto, tag) che iniziano con `=`/`+`/`-`/`@`/tab/CR vengono prefissati con un apostrofo in export (`CsvFormulaGuard.guard`), così non vengono eseguiti come formule aprendo il file in un foglio di calcolo; l'import rimuove lo stesso prefisso (`strip`) mantenendo fedele il round-trip. Importi, date e valute non sono toccati (un importo negativo `-12.50` resta tale). Helper condiviso in `core/common/csv`, con test dedicato più test su builder e analyzer.

**Problemi:** nessuno in fase di scrittura; build e test da confermare in CI.

**Prossimo:** su indicazione dell'utente, in uno step separato, mostrare nella lista Movimenti un indicatore visivo per i movimenti generati da ricorrenza (annotato in Note e appunti).

---

## 2026-07-20 - Statistiche: riuso di FilterDateRangeSheet per il periodo custom

**Fatto:** la schermata statistiche ora apre `FilterDateRangeSheet` (la bottom sheet ridisegnata dei movimenti) al posto del vecchio `StatsDateRangePickerDialog`, che è stato rimosso. Nessuna modifica alla sheet: solo riuso (è `internal` nello stesso modulo `:app`). Per far leggere ai grafici anche i periodi aperti che la sheet consente (solo "dal" o solo "fino al"), `StatsPeriod.Custom` passa da bound obbligatori a `LocalDate?`: `dateRange(today)` risolve l'estremo aperto (fine aperta -> oggi, inizio aperto -> `EARLIEST_LEDGER_DATE`, una data anteriore a qualsiasi movimento e convertibile in epoch millis, a differenza di `LocalDate.MIN` che va in overflow). La label del periodo custom riusa il formatter condiviso `periodLabel(start, end, today)` dei movimenti, così un periodo aperto si legge allo stesso modo nelle due schermate ("Dal 5 lug", "Fino al 5 lug"). `selectCustomRange` accetta bound nullable; con entrambi null (caso che la sheet non produce) ripiega sul mese corrente. Il pulsante "Cancella" della sheet, che nei movimenti torna a "tutte le date", qui torna alla vista mese (le statistiche non hanno uno stato "nessun periodo").

**Verificato:** `gradle testDebugUnitTest assembleDebug lint` verde. Aggiunti test in `StatsPeriodTest` per range chiuso, "dal" (fine -> oggi), "fino al" (inizio -> floor) e per la conversione del floor in epoch millis senza overflow. Verificato che l'unico altro punto dell'app che chiede un range di date è questa schermata: editor movimento, obiettivi di risparmio e regole ricorrenti usano date-picker a data singola, non range.

**Decisioni:** supporto ai periodi aperti anche nelle statistiche (scelta confermata dall'utente) invece di forzare la sheet a soli range chiusi: preserva le funzionalità della sheet e le query dei grafici restano corrette. Floor `LocalDate.of(1, 1, 1)` invece di `LocalDate.MIN` per evitare l'overflow di `toEpochMilli()` nel repository.

**Problemi:** nessuno.

**Prossimo:** nessuno.

---

## 2026-07-20 - Chore: azzerati i warning di compilazione (deprecazioni e KT-73255)

**Fatto:** ripulite tutte le famiglie di warning emerse nel log di compilazione della CI. (1) `hiltViewModel()`: deprecato in `androidx.hilt:hilt-navigation-compose`, migrato al nuovo artifact `androidx.hilt:hilt-lifecycle-viewmodel-compose` (stessa versione 1.3.0, package `androidx.hilt.lifecycle.viewmodel.compose`), import aggiornato in 20 screen; la vecchia dipendenza è rimossa dal catalog (portava con sé androidx.navigation, che il progetto non usa: Nav3). (2) `MenuAnchorType` rinominato `ExposedDropdownMenuAnchorType` (typealias deprecato M3) in 4 file. (3) Vico: `columnSeries`/`lineSeries` -> `columnModel`/`lineModel` in `StatsCharts.kt` (rename puro, firma identica, verificato sui sorgenti v3.2.3). (4) KT-73255: aggiunto `-Xannotation-default-target=param-property` ai compiler args, il default futuro di Kotlin; i soli siti interessati sono qualifier Hilt su parametri di costruttore (`@ApplicationContext`, `@IoDispatcher`, `@ApplicationScope`), inerti sul field. (5) Rimossi safe-call e `!!` superflui segnalati dal compilatore (`ObserveDueStatementsUseCase`, `SavingsGoalsScreen`).

**Verificato:** verifica statica; le firme delle nuove API sono state controllate su release notes androidx (hilt 1.3.0) e sorgenti Vico v3.2.3. Build delegata alla CI GitHub.

**Decisioni:** flag del compilatore invece di annotare i 10 siti con `@param:`: è il default che Kotlin adotterà comunque e mantiene puliti i costruttori.

**Problemi:** nessuno.

**Prossimo:** nessuno.

---

## 2026-07-20 - Chore: detekt, disattivate le regole che producevano solo rumore

**Fatto:** aggiornato `config/detekt/detekt.yml`. Disattivate `LongParameterList`, `TooManyFunctions` e `MagicNumber`: il codice contava 44 `@Suppress` per queste sole regole, tutti con motivazioni strutturali (costruttori Hilt con una dipendenza per concern, DAO/repository con una funzione per query, editor ViewModel con un handler per campo, palette e valori dp/sp letterali). Una regola che richiede una deroga a ogni occorrenza non segnala più nulla. `CyclomaticComplexMethod` resta attiva ma ignora i `@Composable` (le UI Compose sono alberi di `when` per natura), come già `LongMethod` e `LongParameterList` prima della disattivazione. I `@Suppress` esistenti restano nel codice: sono innocui e documentano l'intento; rimozione eventuale come chore separata.

**Decisioni:** disattivazione mirata invece che per categoria: `CyclomaticComplexMethod` sul codice di dominio è un segnale reale e non ha mai richiesto deroghe, quindi resta.

**Problemi:** motivato dal secondo giro di CI consecutivo causato da detekt su codice legittimo (`FilterDateRangeSheet`, complessità 18 per i `when` sulle modalità).

**Prossimo:** nessuno.

---

## 2026-07-20 - Redesign del periodo personalizzato del filtro date, con range aperti

**Fatto:** sostituito il `DatePickerDialog` + `DateRangePicker` del preset "Personalizzato" con un bottom sheet dedicato (`FilterDateRangeSheet`, aperto già espanso): selettore a tre modalità con segmented buttons (Intervallo / Da / Fino a), card di riepilogo live della selezione (con conteggio giorni per gli intervalli chiusi e indicazione del lato aperto per gli altri), calendario Material 3 incorporato (range picker o single picker a seconda della modalità, header nativo nascosto), pulsante Applica a tutta larghezza, Annulla e "Rimuovi il periodo" (torna a "Tutto", visibile solo con un periodo attivo). Le modalità "Da" e "Fino a" applicano un bound solo: `TransactionsViewModel.setCustomRange` ora accetta bound nulli (entrambi nulli = fallback ad ALL); il motore filtri supportava già i range aperti (`LocalDate.MIN`/`MAX`), nessuna modifica alle query. Il chip "Personalizzato" mostra l'etichetta anche per i range aperti ("Dal 5 lug" / "Fino al 5 lug"). Nuove stringhe IT+EN. Bump versione 92 -> 93 / 0.9.53 -> 0.9.54.

**Verificato:** nessun SDK Android in locale: verifica statica (rilettura del diff, firme Material 3 confermate sui sorgenti androidx) più una review incrociata multi-agente ad alto sforzo; build e test delegati alla CI GitHub. Nuovi unit test JUnit5: range aperti inclusivi/esclusivi nel `TransactionFilterEngineTest`, e in `TransactionsViewModelTest` la propagazione del range aperto alla lista (setCustomRange con un solo bound, fallback ad ALL con entrambi nulli) e a `deleteFiltered` (elimina solo la vista filtrata dal periodo aperto). Export CSV ed eliminazione filtrata consumano `uiState.days` (già filtrata dal motore), quindi ereditano i range aperti senza modifiche.

**Decisioni:** bottom sheet invece di dialog per coerenza con le altre superfici del registro (filtri, CSV, eliminazione) e per lo spazio verticale del calendario. Tre modalità esplicite invece di un range picker con bound opzionali: il `DateRangePicker` Material non permette di confermare senza data di fine, e la modalità dichiara l'intento. Al cambio modalità la selezione viene riportata dove lo stato di destinazione può rappresentarla (inizio del range -> "Da", fine -> "Fino a", e viceversa per l'inizio); una sola data di fine non è rappresentabile nello stato del range Material (rifiuta end senza start), quindi in quel caso il range riparte dalla propria selezione. Bug trovato e fixato: l'etichetta del chip chiamava `chipDayLabel(start, start)` (data passata come "oggi"), mostrando sempre "Oggi," e mai l'anno; estratto `shortDayLabel` senza prefisso e helper condiviso `periodLabel` usato da chip e riepilogo del sheet.

**Problemi:** la review incrociata ha confermato e fatto correggere: colonna del sheet non scrollabile che in landscape schiacciava il calendario a ~0dp (ora scroll esterno, con il range picker come nested scrollable a altezza limitata); padding orizzontale di 24dp attorno al calendario che su schermi da 360dp ne tagliava le colonne esterne (il calendario ora prende tutta la larghezza del sheet); etichetta ambigua sui range che attraversano anni diversi (ora l'anno compare su entrambe le date); riepilogo del range incompleto che usava la stessa dicitura "Dal X" di un filtro aperto applicato (ora "X – …"); helper UTC dei picker duplicati in 4 file (ora condivisi in `core/common/date/UtcMillis.kt`); `compactDayLabel` che duplicava il corpo di `shortDayLabel` (ora delega).

**Prossimo:** verifica visiva su device (calendario nel sheet su schermi piccoli, comportamento dello scroll annidato del range picker dentro il `ModalBottomSheet`).

---

## 2026-07-19 - Fix: bottom sheet di eliminazione tagliato in basso

**Fatto:** il `DeleteFilteredSheet` si apriva a metà altezza (stato "partially expanded" di default del `ModalBottomSheet`), lasciando fuori schermo il pulsante Elimina/Annulla finché non lo si trascinava su. Impostato `sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)` così si apre già completamente espanso, e aggiunto `verticalScroll` alla colonna come rete di sicurezza sugli schermi piccoli (stesso pattern di `TransactionFilterSheet`). Il `CsvExportSheet` non è toccato: è più basso e ci stava già tutto. Bump versione 91 -> 92 / 0.9.52 -> 0.9.53.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` verdi con Gradle 8.14.3 locale. Verifica visiva del comportamento del sheet da fare su device.

**Decisioni:** `skipPartiallyExpanded` invece di forzare un'altezza fissa: il contenuto ha altezza variabile (numero di conti nell'anteprima impatto) e l'espansione piena + scroll è robusta.

**Problemi:** nessuno.

**Prossimo:** nessuno per questo fix.

---

## 2026-07-19 - Eliminazione dei movimenti filtrati (bulk delete) con conservazione dei saldi

**Fatto:** aggiunta al registro l'eliminazione in blocco della vista filtrata corrente, WYSIWYG (si cancella esattamente ciò che i filtri mostrano, come già fa l'export CSV). Nuova voce "Elimina i movimenti filtrati" in un menu overflow (3 puntini) nella top bar, dove è stata spostata anche l'azione Esporta; restano icone dirette Cerca e Filtri. Un bottom sheet dedicato (`DeleteFilteredSheet`) mostra il conteggio, due modalità e un'anteprima dell'impatto sui saldi per conto, un link "Esporta prima di eliminare" e il pulsante distruttivo. Snackbar con undo (ripristina i movimenti e i loro tag, e rimuove le eventuali rettifiche di riporto). Le due modalità: "Ricalcola i saldi" (default, elimina e basta) e "Conserva i saldi correnti" (per pulizia dello storico: per ogni conto interessato inserisce una rettifica `ADJUSTMENT` di riporto pari al netto eliminato, così il saldo calcolato non cambia; la rettifica è esclusa dalle statistiche per tipo). Data layer: `TransactionDao.deleteByIds` (chunked, cascade sui tag) e `deleteAndInsert` atomico; nuovi metodi nel repository. Logica di dominio in `DeleteFilteredTransactionsUseCase` + `CarryOverCalculator` (puro). Stringhe IT+EN. Bump versione 90 -> 91 / 0.9.51 -> 0.9.52.

**Verificato:** `assembleDebug testDebugUnitTest lint` verdi con Gradle 8.14.3 locale (il wrapper non scarica la distribuzione dietro il proxy: usato `/opt/gradle`). Nuovi unit test JUnit5: `DeleteFilteredTransactionsUseCaseTest` (netto per conto incluse le gambe transfer, skip dei netti nulli, riporto al confine dell'intervallo, modalità recompute senza rettifiche) e aggiunte a `TransactionsViewModelTest` (deleteFiltered nelle due modalità, undo). Scritto anche il test strumentato `TransactionDaoTest` (deleteByIds + cascade tag, deleteAndInsert atomico) ma non eseguito: nessun emulatore in questo ambiente, come da prassi del progetto.

**Decisioni:** approccio WYSIWYG sul registro invece di un tool separato in Impostazioni, per riuso totale del motore filtri e massima sicurezza (si elimina solo la vista mostrata). Conservazione saldi tramite rettifica di riporto (stesso meccanismo di `AdjustBalanceUseCase`) e non tramite bump del saldo iniziale: tracciabile, escluso dalle statistiche (ADR 8), undo pulito, non altera valori impostati dall'utente. Riporto datato al movimento eliminato più recente, così il grafico saldo-nel-tempo del periodo mantenuto resta corretto. Default "Ricalcola i saldi": conservare i saldi è una scelta esplicita.

**Problemi:** il Gradle wrapper non riesce a scaricare la distribuzione (403 dal proxy su GitHub); usato il Gradle 8.14.3 già presente in `/opt/gradle`.

**Prossimo:** eventuale pagina di manuale utente per la funzione; valutare (se emergesse da misure) la selezione multipla manuale come complemento del filtro.

---

## 2026-07-19 - Indice della guida utente e pagina "Descrizione generale"

**Fatto:** sostituito il `README.md` della cartella `docs/guida-utente/` con `00-indice.md`, indice unico del manuale (introduzione + argomenti raggruppati per categoria in ordine d'uso: Per iniziare, Conti, Movimenti, Budget e pianificazione, Panoramica e analisi, Dati e impostazioni). Il README principale ora punta direttamente a `00-indice.md`, così aggiungere pagine alla guida non richiede più di toccare il README. Creata la prima pagina, `descrizione-generale.md` (cos'è Saldo, target, cosa fa e cosa non fa, i principi, i quattro tipi di movimento, saldo calcolato, lingua/valuta), derivata da VISION.md. Nell'indice per ora sono collegate solo "Descrizione generale" e "Movimenti ricorrenti"; le altre voci sono elencate senza link e verranno create man mano. Aggiunto "Torna all'indice" in cima alle pagine di contenuto.

**Decisioni:** indice come file `00-indice.md` (prefisso numerico per ordinamento nella cartella) su richiesta utente; le pagine di contenuto mantengono nomi descrittivi senza numero. README principale con un solo riferimento all'indice per evitare churn futuro.

**Problemi:** nessuno; sola documentazione, nessun bump di versione.

**Prossimo:** creare le pagine successive del manuale seguendo l'ordine dell'indice (primo avvio/onboarding, conti, movimenti singoli, ecc.).

---

## 2026-07-19 - Avviata la guida utente, prima pagina sui movimenti ricorrenti

**Fatto:** creata la cartella `docs/guida-utente/` come manuale d'uso incrementale (una pagina per funzionalità), con indice `README.md` e prima pagina `movimenti-ricorrenti.md`. La pagina copre: le tre tipologie (uscite, entrate, trasferimenti), frequenze e giorno di riferimento con clamp sui mesi corti, data di fine, le modalità di registrazione (automatica, con conferma, importo variabile e conferma per i trasferimenti cross-currency), il comportamento del motore (nessun recupero dello storico alla creazione, catch-up all'apertura e in background, assenza di doppioni), notifiche, modifica/eliminazione regola ed effetto su saldi/budget/spendibile. Aggiunto il puntatore alla guida nella tabella "Documentazione di progetto" del README.

**Decisioni:** nome cartella `guida-utente`, allineato all'italiano usato in README/PLANNING/devlog. Taglio orientato all'utente (cosa fa e cosa aspettarsi), non al codice: nessun riferimento a classi o ADR. Contenuto derivato dalla lettura del motore (`GenerateRecurringMovementsUseCase`, `RecurrenceCalculator`, `RecurringRuleEditorViewModel`, worker e catch-up in `MainActivity`) e dalla terminologia UI reale (`strings.xml` values-it).

**Problemi:** nessuno; modifica di sola documentazione, nessun bump di versione.

**Prossimo:** aggiungere le pagine successive del manuale (conti, movimenti singoli, budget, statistiche) man mano.

---

## 2026-07-19 - Rimosso il delta a 30 giorni dalla didascalia della sparkline (Fase 10.4)

**Fatto:** rimozione del numero di variazione a 30 giorni a destra della didascalia della sparkline (versionCode 89 -> 90, versionName 0.9.50 -> 0.9.51), su feedback utente.
- Problema: quel numero (`balanceTrend` = saldo di oggi meno saldo di 30 giorni fa) era senza etichetta e, dopo l'aggiunta del forecast, stava a destra di una didascalia che ora nomina "stima a fine mese" e vicino alla coda tratteggiata e alla pill `≈`: si confondeva con una cifra della stima. L'utente lo aveva scambiato per la spesa dal 1 del mese (che invece e la card mensile).
- Fix: `SparklineCaption` mostra ora la sola didascalia (nessun importo a destra); rimossi i parametri `trend`/`currency` del composable e il parametro `trend` di `BalanceCard`. Rimosso anche il campo `balanceTrend` da `DashboardUiState` e la sua assegnazione: era usato solo per quel numero, l'accessibilita della sparkline (`sparklineDescription`) ricalcola il trend dalla history in autonomia, quindi nessuno stato morto residuo.
- Test: `DashboardViewModelTest` aggiornato (rimossa l'asserzione su `balanceTrend` dal test della history, rimosso il test dedicato al trend null); i test del `balanceForecast` restano invariati.

**Decisioni:** valutate con l'utente due opzioni (etichettare il numero con una freccia, oppure rimuoverlo); scelta la rimozione: la forma della linea mostra gia l'andamento, la pill `≈` da la stima a fine mese e la card mensile da la spesa del mese, quindi il delta a 30 giorni era ridondante oltre che ambiguo. Un angolo con una sola cifra (la pill) e piu leggibile e premium.

**Problemi:** nessuno; gate `assembleDebug testDebugUnitTest lint detekt` verde con `/opt/gradle`.

**Prossimo:** verifica su device: sotto la sparkline resta solo la didascalia, a destra nessun importo; la pill `≈` sulla coda resta l'unica cifra del forecast.

---

## 2026-07-19 - Fix: ricorrenze escluse dalla media giornaliera del forecast (Fase 10.4)

**Fatto:** correzione di un doppio conteggio nel forecast di fine mese, segnalato dall'utente (versionCode 88 -> 89, versionName 0.9.49 -> 0.9.50).
- Bug: la media giornaliera si basava su `monthToDateSpend`, che include i movimenti gia generati dalle ricorrenze. Quegli importi finivano nella media *e* venivano riproiettati sulle date future dalle regole ricorrenti: doppio conteggio. Caso limite dell'utente: ricorrenza mensile di 1 EUR il giorno 1 -> appena generata, media 1 EUR/giorno -> forecast a circa -30.
- Fix: nuova colonna `monthToDateNonRecurringSpendMinor` nella query `observeDashboardTotals` (stessa finestra, con `AND recurringRuleId IS NULL`), portata fino al dominio (`DashboardTotals.monthToDateNonRecurringSpend`). Il forecast usa questa base non ricorrente per la media; le ricorrenze fisse restano modellate una sola volta, sulla loro data. Cambiamento di sola query: nessuna migration (l'indice su `recurringRuleId` esisteva gia). La figura "spesa del mese" mostrata nel confronto mese-su-mese resta invariata (continua a includere le ricorrenze).
- KDoc di `BalanceForecastCalculator` aggiornato (rimosso il caveat "la media include anche le ricorrenze gia addebitate", non piu vero). Parametro rinominato `nonRecurringMonthToDateSpend`.

**Decisioni:** scelta la lettura del dato reale dal DB (colonna `recurringRuleId IS NULL`) invece di ricalcolare le occorrenze ricorrenti con `RecurrenceCalculator` e sottrarle: la colonna legge cio che e stato effettivamente registrato (non-pending, valuta primaria), robusta a regole modificate, movimenti cancellati e confirm-mode. Le ricorrenze a importo variabile gia addebitate escono dalla media e (come gia prima) non sono riproiettate: coerente con la scelta di saltare le regole variabili nel forward.

**Problemi:** il gate in un run precedente era fallito per un errore di build-cache Gradle (`Could not get file mode` su un .dex, pressione su disco), non di codice; risolto con un run pulito.

**Prossimo:** verifica su device del caso limite (ricorrenza gia addebitata a inizio mese: coda tratteggiata piatta, non in crollo).

---

## 2026-07-19 - Proiezione saldo a fine mese nella sparkline (Fase 10.4)

**Fatto:** punto "Proiezione saldo a fine mese" della Roadmap v2.0 (versionCode 87 -> 88, versionName 0.9.48 -> 0.9.49). Design: ADR 30.
- `BalanceForecastCalculator` (dominio, puro): stima end-of-day da domani all'ultimo giorno del mese, camminando dal saldo totale con la media della spesa giornaliera (spesa del mese / giorni trascorsi, HALF_UP alla scala valuta) e applicando alla loro data le ricorrenze a importo fisso via `RecurrenceCalculator`, incluse le entrate (uno stipendio a fine mese cambia la coda da "affonda" a "risale"). Floor su `lastGeneratedDate` come `UpcomingChargesCalculator`; regole variabili e valute diverse escluse; vuoto l'ultimo giorno del mese.
- `DashboardViewModel`: nuovo campo `balanceForecast`, ancorato al saldo headline (identico all'ultimo punto storico per invariante ADR 27: aggancio senza scalino), calcolato solo quando la sparkline e visibile.
- `BalanceSparkline`: normalizzazione su storia + forecast con tangenti condivise (continuita al punto di oggi), riempimento sfumato solo sotto la parte reale, coda tratteggiata, anello sul punto di fine mese e pill "≈ importo" autoposizionata (TextMeasurer, fade-in a fine reveal); a11y estesa con la stima. Caption "Ultimi 30 giorni + stima a fine mese" quando la coda e presente. Stringhe IT/EN.

**Decisioni:** coda tratteggiata invece della riga dedicata (scelta condivisa con l'utente): zero ingombro verticale e il tratteggio comunica "stima" da solo. Sul range, il dubbio dell'utente (a inizio mese una sparkline di sola previsione) si risolve tenendo fissa la finestra storica di 30 giorni e appendendo solo i giorni residui del mese con lo stesso passo per giorno: nel caso peggiore (1 del mese, mese di 31 giorni) la coda occupa circa meta della larghezza, mai di piu. La media giornaliera include anche le ricorrenze gia addebitate nel mese (leggera sovrastima della spesa futura): approssimazione accettata e documentata, la coda e sempre marcata come stima.

**Problemi:** nessuno; gate `assembleDebug testDebugUnitTest lint detekt` verde con `/opt/gradle`.

**Prossimo:** verifica su device: coda a inizio/meta/fine mese, pill sopra e sotto il punto a seconda di dove finisce la linea, con e senza ricorrenze, TalkBack sulla card.

---

## 2026-07-19 - Recap: media giornaliera e tema adattivo (Fase 10.3)

**Fatto:** due rifiniture al recap dal feedback utente (versionCode 86 -> 87, versionName 0.9.47 -> 0.9.48).
- Pagina "Hai speso": nuova riga sempre presente "In media X al giorno". Il campo `dailyAverageSpend` nasce in `GetMonthlyRecapUseCase` (spesa del mese / giorni di calendario, HALF_UP alla scala valuta): aritmetica monetaria nel dominio, la UI formatta soltanto. Unit test su arrotondamento (giugno, 350/30 = 11.67) e mese senza spese.
- Recap a tema adattivo: rimosso `SaldoTheme(darkTheme = true)`; schermata e immagine condivisa ereditano il tema risolto dell'app (chiaro/scuro/sistema e palette scelti in Impostazioni). I token usati (surfaceContainerHigh -> background, categorie su surfaceVariant) erano gia theme-aware: nessun altro ritocco visivo. ADR 28 rivisto in PLANNING.

**Decisioni:** valutata insieme all'utente la fusione della pagina "Hai speso" con "Dove sono andati" e scartata: il formato "una pagina, un pensiero" del recap resta, la pagina si riempie con la media giornaliera (mostrata sempre, non solo senza baseline: complementa il confronto invece di sostituirlo). Sul tema: il dark fisso era una scelta legittima stile Wrapped, ma contraddiceva l'identita dell'app che rispetta il tema scelto dall'utente ovunque; l'immagine condivisa segue lo stesso tema della schermata (condividi quello che vedi).

**Problemi:** nessuno; gate `assembleDebug testDebugUnitTest lint detekt` verde con `/opt/gradle`.

**Prossimo:** verifica su device: pagina spese con media (con e senza confronto), recap in tema chiaro e scuro, immagine condivisa coerente col tema visto.

---

## 2026-07-19 - Toggle teaser recap e grafici Statistiche premium (Fase 10.2)

**Fatto:** follow-up della Fase 10.1 (versionCode 85 -> 86, versionName 0.9.46 -> 0.9.47). Design: ADR 29.
- Impostazioni > Dashboard: nuovo switch "Invito al recap mensile" (default attivo). `showRecapTeaser` in `DashboardCardPreferences`, gate in `buildState` del `DashboardViewModel`: lo switch silenzia il teaser senza toccare il flusso di dismiss per mese.
- Donut categorie riscritto in Canvas al posto dell'API pie di Vico (sperimentale nella 3.x): geometria pura in `DonutGeometry.kt` (fette proporzionali con gap clampato, partenza a ore 12, hit-test dell'angolo) coperta da `DonutGeometryTest`; fette con cap arrotondati, sweep-in d'ingresso orario (saltato a reduced motion), tap sulla fetta che apre lo stesso drill-down delle righe sottostanti (inclusa la fetta "Senza categoria"). Overlay centrale e riassunto TalkBack invariati.
- Restyling cartesiani dentro Vico 3.2.3, con le firme verificate sui binari in cache Gradle (`javap` su Fill, CartesianChartHost, AreaFill/LineFill, ColumnProvider) e non a memoria: colonne a pillola da 16dp (`CircleShape`), area sotto la linea del saldo con sfumatura verticale (`Fill(Brush)`), `animateIn` su barre e linea agganciato a `rememberMotionEnabled()`. Marker, listener del drill-down e scroll a fine serie non toccati.

**Decisioni:** l'evidenza della colonna del mese corrente e il punto sull'ultimo valore della linea sono stati rimandati di proposito: richiederebbero provider per-entry custom su interfacce Vico con overload multipli (rischio di regressione per un dettaglio); il piano prevedeva esplicitamente questi fallback. Le cifre delle statistiche non cambiano: nessuna query o ViewModel toccati.

**Problemi:** nessuno; gate `assembleDebug testDebugUnitTest lint detekt` verde con `/opt/gradle`.

**Prossimo:** verifica su device: switch teaser on/off, donut animato e tap fetta -> lista filtrata coerente con la fetta, resa di colonne a pillola e area sfumata, marker e "Vedi i movimenti di <mese>" invariati, animazioni di sistema spente.

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
