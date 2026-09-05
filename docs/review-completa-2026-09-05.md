# Review completa dell'app (5 settembre 2026)

Review statica del codice sorgente alla versione 2.2.0 (`versionCode` 180, `main` pulito su `af7969c`). Quattro domande: correttezza dei calcoli sugli importi, aderenza della UI/UX a Material 3, widget (grafica e implementazione), funzionalità da migliorare o mancanti con valore reale. Nessuna esecuzione su device: dove il codice e la documentazione divergono lo si dice, dove servirebbe una prova sul launcher lo si dice.

**Esito (5 settembre 2026, versione 2.2.1).** I bug B1, B2, B3, B4, B5, B7 e B8 sono corretti nella Fase 38 di [PLANNING.md](../PLANNING.md), che riporta per ciascuno cosa è cambiato; il B6 è documentato nella guida utente e nel README invece di essere allineato; le divergenze di documentazione della sezione 5 sono corrette. Le proposte F1, F2, F3, F4 e F6 sono la Fase 39; F5 e F7 sono assorbite dai fix B4 e B1. Le osservazioni UI che richiedono un device largo (layout adattivo, collaudo dei widget sul launcher) sono nella Fase 35; il tastierino compatto è a 48dp. Le sezioni che seguono descrivono lo stato **prima** dei fix.

---

## 1. Calcoli sugli importi

### Cosa regge

- **Pipeline degli importi.** `Long` in unità minori nel database, `BigDecimal` nel dominio con scala derivata dalla valuta e `HALF_UP` in un solo punto (`MoneyMapper`), `String` solo nella UI tramite `MoneyFormatter`. Nel codice di produzione non esiste alcun `Float`/`Double` applicato a denaro: le sole conversioni `toFloat()` sono frazioni per barre e grafici. La convenzione del segno vive in `TransactionSign` ed è condivisa da editor, quick entry e generazione ricorrente.
- **Saldi.** `AccountDao.observeAllWithBalance`, `observeBalance`, `observeTotalBalance` e `observeAllBalancesAsOf` applicano la stessa regola (saldo iniziale, più movimenti propri, più gambe entranti dei trasferimenti, pending esclusi), e le serie storiche (`observeMonthlyNetChanges`, `observeDailyNetChanges`, `observeNetChangeBefore`) la ripetono con gli stessi filtri di conto. L'ultimo punto della sparkline coincide con il saldo di testata per costruzione.
- **Statistiche e budget.** Trasferimenti e rettifiche esclusi a livello di query (ADR 8), rimborsi nettati come spesa negativa, pending mai contati, `isIncludedInBudget` applicato in tutte le query di budget (comprese le gemelle per valuta). Le soglie (`BudgetLevel.of`) usano aritmetica intera, quindi 79,99% non diventa mai 80%.
- **Ricorrenze.** Clamp dei mesi corti riderivato dal giorno di riferimento a ogni periodo (mai propagato), idempotenza a tre livelli (mutex, transazione per regola, indice unico su regola più occorrenza), catch-up con floor `lastGeneratedDate + 1`. `monthlyEquivalent` arrotonda alla scala della valuta.
- **Previsione a fine mese.** Ancorata al saldo "ad oggi", media giornaliera dalla sola spesa manuale, guard `RuleOccurrence` contro il doppio conteggio fra regole e movimenti già generati, entrambe le gambe dei trasferimenti valutate contro l'insieme dei conti inclusi.
- **Conversione valute.** Tutto `BigDecimal`, scala intermedia 12 sulla gamba in euro, flussi al tasso del giorno del movimento e stock all'ultimo tasso, giorno del tasso più vecchio dichiarato, nessun controvalore persistito.
- **Carte di credito.** `BillingCycleCalculator` non crea né buchi né sovrapposizioni con giorni di chiusura 29-31; settlement con mutex, transazione e watermark; i cicli vuoti vengono consumati senza generare trasferimenti a zero.
- **Pulizia con conservazione dei saldi.** `CarryOverCalculator` produce una sola rettifica per conto, nella valuta del conto, datata al confine dell'intervallo rimosso.

### Problemi trovati

**B1. Finestra mensile e movimenti datati nel futuro (alta).**
`DashboardWindows.around` ([DashboardTotals.kt:53](../app/src/main/kotlin/com/callbackdev/saldo/core/domain/model/DashboardTotals.kt)) chiude `monthEnd` al primo giorno del mese successivo. Con quella finestra lavorano la card Mese (`observeDashboardTotals`, [TransactionDao.kt:613](../app/src/main/kotlin/com/callbackdev/saldo/core/database/dao/TransactionDao.kt)), i progressi dei budget (`ObserveBudgetProgressUseCase`), lo Spendibile oggi (`ObserveSafeToSpendUseCase`) e le notifiche di soglia (`CheckBudgetThresholdsUseCase`). Un movimento confermato datato fra domani e la fine del mese entra quindi subito nella spesa del mese, nel consumato dei budget e nel calcolo dello spendibile.
La documentazione promette il contrario: README (riga "In arrivo": "Un movimento futuro non tocca statistiche, budget, spendibile e schede Oggi/Mese finché non arriva il suo giorno"), [guida in-arrivo.md:58](./guida-utente/in-arrivo.md), Fase 13 di PLANNING (checkbox "Verificato che il movimento futuro resta fuori da ... budget e card Oggi/Mese"). Il test citato da quella checkbox, [TransactionDaoUpcomingTest.kt:153-167](../app/src/androidTest/kotlin/com/callbackdev/saldo/core/database/TransactionDaoUpcomingTest.kt), passa alla query una finestra chiusa a `today + 1` che la produzione non usa: verifica una promessa, non il codice.
Conseguenze concrete: una bolletta registrata oggi con data 25 fa scattare oggi l'avviso 80%/100% del budget e riduce lo Spendibile come se fosse già pagata, mentre la card Oggi e la sparkline (giustamente) non la vedono; la card Mese mostra una spesa del mese superiore a quella del registro filtrato "fino a oggi".
Proposta: card Mese e progressi budget su `[monthStart, todayEnd)`; lo Spendibile aggiunge i movimenti confermati futuri del mese come voce "impegnati" accanto alle occorrenze pending (restano visibili e sottratti, ma non figurano come spesi); le notifiche di soglia seguono i progressi. Il test DAO va riscritto per usare `DashboardWindows` e non una finestra propria. Se invece si decide che il comportamento attuale è quello voluto (la spesa futura è già impegnata e il budget deve vederla), vanno corretti README, guida e Fase 13, e va chiarito perché la card Oggi e la card Mese seguono regole diverse.

**B2. Categorie di tipo "entrambi" nell'anello delle categorie e nel recap (media-alta).**
`observeCategoryTotals` ([TransactionDao.kt:188](../app/src/main/kotlin/com/callbackdev/saldo/core/database/dao/TransactionDao.kt)), la gemella one-shot `getCategoryTotals` (riga 666) e la gemella per valuta `observeForeignCategoryTotals` ([ForeignFlowDao.kt:107](../app/src/main/kotlin/com/callbackdev/saldo/core/database/dao/ForeignFlowDao.kt)) sommano per categoria tutti i movimenti `EXPENSE` e `INCOME`, entrate ordinarie comprese. `StatsViewModel.categorySlices` ([StatsViewModel.kt:262](../app/src/main/kotlin/com/callbackdev/saldo/feature/stats/StatsViewModel.kt)) e `GetMonthlyRecapUseCase.topCategories` tengono poi i soli totali negativi. Per una categoria di tipo `BOTH` (offerta nell'editor categoria, [CategoryEditorScreen.kt:269](../app/src/main/kotlin/com/callbackdev/saldo/feature/categories/CategoryEditorScreen.kt)) le entrate compensano le spese: con "Regali" a -100 di spese e +80 di entrate la fetta vale 20, il conteggio della fetta include l'entrata, il totale al centro dell'anello e le percentuali delle altre categorie sono sbagliati, mentre la barra del trend dello stesso mese (`observeMonthlyTotals`) conta 100 di spesa e 80 di entrata. La query dei budget per categoria (`observeCategorySpendTotals`, riga 408) usa già il predicato corretto, `type = 'EXPENSE' OR (type = 'INCOME' AND isRefund = 1)`.
Proposta: allineare le tre query a quel predicato, allineare `matchesStatsScope` del drill-down per categoria ([FilteredTransactionsViewModel.kt:198](../app/src/main/kotlin/com/callbackdev/saldo/feature/stats/FilteredTransactionsViewModel.kt)), che oggi include tutte le entrate, e correggere il commento della query, che dichiara una coerenza con le barre del trend che vale solo senza categorie miste. Test DAO e `StatsViewModelTest` con una categoria `BOTH` che ha entrambi i flussi.

**B3. Import CSV: "1,234" e "2.500" letti come 1,23 e 2,50 (media).**
`CsvFieldParsers.parseAmount` ([CsvFieldParsers.kt:27](../app/src/main/kotlin/com/callbackdev/saldo/feature/transactions/importer/CsvFieldParsers.kt)) tratta sempre l'ultimo separatore come decimale, cella per cella. Un importo con separatore delle migliaia e senza decimali (frequente negli export di fogli di calcolo: `1,234` in inglese, `2.500` in italiano) diventa `1.234` e `2.500`, poi arrotondato alla scala della valuta: l'importo entra diviso per mille, supera la validazione e la rilevazione dei duplicati non lo intercetta. I test ([CsvParsingTest.kt:72-78](../app/src/test/kotlin/com/callbackdev/saldo/feature/transactions/importer/CsvParsingTest.kt)) coprono solo celle con entrambi i separatori o con separatori ripetuti. L'export dell'app non è toccato (scrive `toPlainString()` senza raggruppamento).
Proposta: inferire la convenzione decimale una volta per colonna (una cella con entrambi i separatori la fissa per tutto il file; altrimenti le celle con una o due cifre dopo il separatore la fissano, e "tre cifre dopo un separatore singolo" segue la convenzione così trovata o la lingua delle intestazioni già rilevata); quando nulla la fissa, la riga è ambigua e va segnalata in anteprima invece di indovinata, con lo stesso principio dell'ADR 42.

**B4. Estratto carta e pagamenti manuali (media).**
`sumOwnMovementsInWindow` ([TransactionDao.kt:828](../app/src/main/kotlin/com/callbackdev/saldo/core/database/dao/TransactionDao.kt)) e `statementAmount` ([SettleCreditCardStatementUseCase.kt:104](../app/src/main/kotlin/com/callbackdev/saldo/core/domain/usecase/SettleCreditCardStatementUseCase.kt)) calcolano il dovuto dai soli movimenti di cui la carta è sorgente: un trasferimento manuale dal conto alla carta non riduce l'estratto. Due scenari: (a) l'utente paga a mano prima della scadenza; con `autoPost` il worker addebita comunque l'intero estratto e la carta finisce in credito (pagamento doppio), in modalità conferma la CTA continua a proporre l'importo pieno e un tap lo duplica; (b) un ciclo con netto positivo (rimborso superiore alle spese) viene azzerato da `max(0)` e il credito non riduce il ciclo successivo, mentre una carta reale lo riporta.
Proposta, indipendente dal pagamento parziale della Fase 27: dovuto = movimenti del ciclo, meno i trasferimenti entranti datati dopo la chiusura e fino al calcolo, mai sotto zero; il credito residuo di un ciclo si riporta sul successivo. In alternativa minima, l'editor avvisa quando si registra un trasferimento manuale verso una carta con un estratto in attesa. Manca inoltre una pagina della guida utente sulle carte di credito: è la funzione con più regole implicite e non ne ha una.

**B5. Media giornaliera della previsione e conti fuori dal saldo totale (bassa).**
`observeDashboardTotals` filtra solo gli archiviati, quindi `monthToDateNonRecurringSpend` include le spese dei conti con `isIncludedInTotal = 0`, mentre la coda tratteggiata è applicata al totale dei soli conti inclusi. Con un conto cointestato escluso dal totale ma con spese manuali la coda scende più del dovuto. Proposta: una colonna dedicata (o un filtro) sui soli conti inclusi per la base della media.

**B6. Card Oggi/Mese e Statistiche seguono semantiche diverse senza dirlo (bassa, UX).**
Le card sono cifre di cassa (contano movimenti esclusi dalle statistiche e prestiti a persone, e i rimborsi come entrate), Statistiche, Recap e budget sono cifre statistiche. È documentato nei commenti del codice, non all'utente: la card Mese e il mese nelle Statistiche divergono appena c'è un rimborso o un prestito, e il drill-down della card apre un elenco il cui totale non è quello della card. Proposta: una riga nella guida e, nella card, la stessa etichetta "cassa" che il drill-down già distingue, oppure allineare la card Mese alla semantica statistica lasciando la card Oggi in cassa.

**B7. Data di estinzione stimata del prestito (bassa).**
`projectedPayoffDate = today.plusMonths(remaining)` ([ObserveLoanProgressUseCase.kt:81](../app/src/main/kotlin/com/callbackdev/saldo/core/domain/usecase/ObserveLoanProgressUseCase.kt)) parte da oggi anziché dalla prossima rata, quindi può sbagliare di un mese rispetto alla data in cui la rata che azzera il debito viene davvero generata. Proposta: `nextInstallmentDate` più `remaining - 1` periodi della regola.

**B8. Codice morto (pulizia).**
`ObserveUpcomingMovementsUseCase.kt:131`: estensione privata `List<UpcomingMovement>.magnitudeOf` mai chiamata.

### Verificato e non problematico

Punti controllati di proposito perché sono le trappole classiche: confronto "stesso giorno del mese scorso" con `minusMonths` che clampa i mesi corti; aggregati per giorno locale con l'offset della riga (ADR 7) nelle serie storiche e nelle gemelle per valuta; `previousReference` nullo quando il mese scorso non ha spesa; rimborsi che superano le spese di una categoria (fetta scartata, budget a zero); cambio valuta di un conto bloccato quando ha movimenti (`isCurrencyLocked`); valuta dell'obiettivo sempre quella del conto collegato; totali del registro e dei giorni per valuta, senza sommare valute diverse; totali per controparte fusi su chiave case- e accent-insensitive senza mai fondere valute; `deleteAndInsert` atomico per la pulizia con conservazione dei saldi.

---

## 2. UI/UX e Material 3

### Aderenza

- Solo `androidx.compose.material3`: nessun import di Material 2. Componenti M3 usati dove servono: `NavigationBar` a quattro destinazioni, `TopAppBar` con `pinnedScrollBehavior` su tutte le schermate, `SingleChoiceSegmentedButtonRow`, `FilterChip`, `ModalBottomSheet`, `ListItem`, `Switch`, `DatePicker`/`TimePicker` nei dialog, `SwipeToDismissBox` per l'eliminazione con undo via `Snackbar`, `DropdownMenu` per le azioni secondarie.
- `ColorScheme` brand completo in entrambi i temi (tutti i ruoli, container inclusi), dynamic color opt-in senza fallback (minSdk 33). I ruoli di denaro (`moneyColors`) sono derivati dallo schema: spesa neutra, entrata su `tertiary`, saldo negativo su `error`, avviso ambra fisso per tema. La distinzione spesa/entrata è portata da segno e icona, come richiede la regola di accessibilità del progetto.
- Tipografia: scala M3 su Inter con tracking ritoccato, figure tabulari e zero barrato sugli importi.
- Stringhe: nessuna stringa letterale nel codice Compose; 8 voci `translatable="false"`, parità IT/EN altrimenti.
- Accessibilità: `contentDescription` su ogni `IconButton` (nessuna a `null`), tastierino e campo importo con semantica `Role.Button` e descrizioni; riduzione animazioni rispettata su sparkline, anello, top bar e speed dial (`rememberMotionEnabled`); tasti del tastierino a 48dp nella forma piena.

### Osservazioni

- **Nessun layout adattivo.** Non esistono `WindowSizeClass`, `NavigationRail` o layout a due colonne: su tablet e in landscape la `NavigationBar` resta in basso e le card della Dashboard si allargano a tutta larghezza. Material 3 prevede la rail da 600dp in su. È già nel perimetro della Fase 35 (QA su tablet), ma va detto che oggi è un'assenza, non un dettaglio da verificare.
- **Scala delle forme più stretta di M3** (4/6/8/12/16 contro 4/8/12/16/28). Scelta dichiarata e applicata in modo coerente tramite `MaterialTheme.shapes`; Material 3 Expressive va nella direzione opposta, e la chore material3 1.5 (SDK 37) sarà il momento per decidere se tenerla.
- **Tastierino compatto a 42dp** nei dialog e nelle sheet (`CompactKeyHeight`): sotto il minimo di 48dp del target di tocco M3. La larghezza dei tasti è ampia (pesi su tutta la riga), quindi l'area resta usabile, ma un `heightIn(min = 48.dp)` costa poco.
- **Speed dial emulato** con spring standard: corretto sulla versione stabile del BOM; con material3 1.5 passare a `FloatingActionButtonMenu`, come già annotato nel codice.
- **Bottom bar come overlay** con `padding(bottom = 80.dp)` fisso nelle schermate di primo livello: funziona perché ogni `Scaffold` aggiunge l'inset di sistema, ma il valore è accoppiato all'altezza del `NavigationBar` M3 e va ricontrollato a ogni aggiornamento della libreria.
- **Card flat con hairline** al posto dell'elevazione: è lo stile "outlined" di M3, coerente in tutta l'app; l'unica superficie con ombra rimasta è il FAB.

---

## 3. Widget

### Implementazione

La riscrittura su `RemoteViews` (ADR 46) è corretta e sobria: sizes map API 31+ con un layout per breakpoint, `onAppWidgetOptionsChanged` volutamente vuoto, ogni colore come coppia giorno/notte via `setColorInt`, velature pre-fuse sul colore del container (la tinta `SRC_ATOP` non composita alpha), `PendingIntent` distinti per data URI (gli extra non contano per l'identità), `goAsync()` consumato una sola volta in `onReceive`, `onDeleted` e `onRestored` gestiti, `updatePeriodMillis = 0`, nessun osservatore a widget assente (ADR 37). La geometria è in un modulo puro testato su JVM (`WidgetLayoutTest`) e il budget d'altezza tiene conto della scala font di sistema.

### Grafica

- Provider info con `previewLayout` reale, `targetCellWidth/Height`, `maxResizeHeight` che blinda la barra a una riga, `configuration_optional | reconfigurable`.
- Sfondo sul token M3 `widgetBackground` (secondaryContainer spostato in tono HCT), placeholder neutro con variante `drawable-night` per l'istante prima del bind.
- Tile 44dp con etichetta 12sp nella griglia larga, 52dp senza etichetta nella stretta, pill 34dp per il selettore; il target di tocco è la cella (colonna di almeno 56dp per riga di 64dp), quindi sopra i 48dp. Immagini decorative con `importantForAccessibility="no"`, `contentDescription` su celle, pill e scorciatoia.
- Eccezione dichiarata a `MoneyColors`: sulla barra la spesa è colorata di `error`, motivata dal fatto che due bottoni soli non hanno altro contesto; le icone restano il canale accessibile.

### Da fare

- **Collaudo su device non registrato.** La voce del devlog del 19 agosto chiude con "Non ancora provato su device" e nessuna voce successiva lo documenta, mentre la 2.1.0 e la 2.2.0 sono state pubblicate con questo codice. Se il giro sul launcher è stato fatto (piazzamento, resize, selettore, picker, tema chiaro/scuro, import delle impostazioni Glance dei widget già piazzati) va scritto nel devlog; se non è stato fatto, è la verifica più importante rimasta aperta di tutta la review.
- Il quadrato della scorciatoia app si dimensiona sull'altezza dichiarata del breakpoint (`setViewLayoutWidth`): approssimazione documentata, da controllare sui launcher con righe più alte del previsto.

---

## 4. Funzionalità: miglioramenti e mancanze

Criterio: valore quotidiano per l'utente tipo di VISION, costo contenuto, nessuna nuova meccanica di denaro dove non serve. Sono escluse le voci già pianificate (Fasi 24, 25, 26, 34, 35) e quelle già "da valutare" (Fasi 18, 20, 21, 27-31, cloud), che restano valide: fra queste, la Fase 27 (pagamento parziale carta) e la Fase 31 (rimborsi collegati) sono quelle che chiudono asimmetrie reali e meriterebbero priorità sulle altre.

**F1. Dettaglio del conto (alta).** Il tap su un conto apre l'editor; non esiste una schermata con i movimenti del conto e l'andamento del suo saldo. `observeForAccount` esiste nel DAO ma nessuna schermata la usa, e l'unica via è il filtro per conto nel registro. Dopo "quanto ho" la domanda successiva è "da cosa è fatto": una schermata per conto con saldo, riga "ad oggi", sparkline del conto e i movimenti raggruppati per giorno riusa `FilteredTransactionsRoute(accountId)` e il walk giornaliero già esistenti, e diventa il posto naturale per rettifica saldo, estratto carta e stato del prestito, oggi sparsi fra lista e editor.

**F2. Duplica movimento (alta, costo basso).** Nessuna azione "duplica" nel registro o nell'editor. Le spese irregolari ma ripetitive (caffè, parcheggio, pranzo) si reinseriscono identiche e oggi si riparte da zero o dal widget: una voce nel menu dell'editor e una nel long-press della riga che aprono l'editor precompilato (tipo, importo, conto, categoria, descrizione, tag, data di oggi) sono la forma più diretta di "zero frizione" che manca.

**F3. Pausa di una regola ricorrente (media).** `RecurringRule` non ha uno stato di pausa: per sospendere un abbonamento si cancella la regola (i movimenti perdono il legame) o si inventa una data di fine. Una colonna additiva `isPaused`, esclusa da generazione, totali mensili e previsione, mostrata nell'hub come "in pausa", copre palestra estiva, streaming sospeso e rata rinegoziata.

**F4. Promemoria di backup (media).** `lastBackupAtEpochMilli` esiste ed è mostrato nella schermata Backup; una notifica opt-in "ultimo backup più di N giorni fa" riusa il worker giornaliero e il pattern watermark. Per un'app offline-first senza cloud è la protezione dati più economica che manca.

**F5. Estratto carta robusto (media).** È il B4 letto come funzionalità: dovuto che tiene conto dei pagamenti manuali e riporto del credito. Va fatto prima della Fase 27, che lo presuppone.

**F6. Import CSV: mappatura manuale delle colonne (media).** Quando il riconoscimento fallisce l'import si ferma con "formato non riconosciuto". Il modello `CsvColumnMapping` esiste già; una sheet in cui l'utente assegna data, importo, descrizione e tipo alle colonne del file sblocca gli export delle banche e delle app che non usano intestazioni note, che sono il caso d'uso dichiarato dell'import.

**F7. Spendibile con i movimenti futuri confermati come impegno.** È la parte propositiva del B1: la voce esiste già per le occorrenze pending e i futuri confermati sono l'altra metà dell'elenco "In arrivo".

**Da non fare**, perché coperte o fuori scope per decisione: sottocategorie, entità beneficiario, widget con saldo o totali (ADR 37), riconciliazione a spunta, conti non monetari.

---

## 5. Documentazione non allineata al codice

- [README.md:84-88](../README.md) e [README.md:131](../README.md): "Installazione" e "Build" descrivono ancora release con l'APK di debug firmato dal keystore condiviso, aggiornabile in place, e "release create a mano". Dalla 2.2.0 (ADR 47) la release pubblica un APK release minificato firmato con la chiave vera tramite `release.yml`, e il passaggio dalla 2.1.0 richiede disinstallazione (lo dice CLAUDE.md, non il README).
- [CLAUDE.md:70](../CLAUDE.md): lo schema di versione dichiarato è `2.1.<incremento>` mentre l'ultima pubblicata è la 2.2.0 e `versionName` è già `2.2.0`.
- README, [guida in-arrivo.md:58](./guida-utente/in-arrivo.md) e Fase 13 di PLANNING: vedi B1.
- Guida utente: manca una pagina sulle carte di credito a saldo (ciclo, addebito, rettifica del debito iniziale, cosa non fare: pagare a mano con un trasferimento mentre l'addebito è automatico).
