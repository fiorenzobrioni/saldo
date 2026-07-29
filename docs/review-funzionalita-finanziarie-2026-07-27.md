# Review delle funzionalità finanziarie (27 luglio 2026)

Review del comportamento finanziario di Saldo e confronto con le funzionalità delle app di tracciamento spese a pagamento (Wallet by BudgetBakers, Money Manager, Bluecoins, Spendee, MoneyWiz, 1Money, Toshl).

Eseguita il 27 luglio 2026 su versionCode 145, versionName 0.9.106, leggendo dominio, DAO, use case ed editor, non solo la documentazione. Il nome del file porta la data della review: una review futura sarà un documento nuovo con la propria data, questo non si riapre.

Questo documento non implementa nulla: la tabella finale è un elenco di candidati con un ordine consigliato, da smistare in PLANNING solo dopo una decisione esplicita.

---

## 1. Come Saldo tratta il denaro oggi

### Rappresentazione degli importi

Un solo percorso, rispettato ovunque: `Long` in unità minori nel DB, `BigDecimal` nel dominio, stringa localizzata solo nella UI. `MoneyMapper` deriva la scala dalla valuta (`Currency.getDefaultFractionDigits`, con clamp a zero per le pseudo-valute) e arrotonda `HALF_UP`. Nessun `Float`/`Double` compare in un percorso monetario, e le divisioni del dominio dichiarano scala e modalità di arrotondamento al punto di chiamata (`FLOOR` per la quota giornaliera dello spendibile, arrotondamento per eccesso in aritmetica intera per il versamento mensile suggerito di un obiettivo).

### Il libro mastro

Quattro tipi di movimento con una sola convenzione di segno: l'importo è l'effetto firmato sul conto di partenza. Il trasferimento è un record unico con la gamba di destinazione separata in importo e valuta, quindi un trasferimento tra valute diverse è rappresentabile senza inventare un tasso. La rettifica è un movimento come gli altri, e questo è ciò che le permette di entrare nel ciclo di una carta di credito ed essere addebitata.

Il saldo non è mai memorizzato: `initialBalance + Σ movimenti`, calcolato in SQL con due sotto-select (movimenti propri più gamba entrante dei trasferimenti). Esiste la gemella "ad oggi" con il filtro sul giorno locale, che è ciò che rende onesta la presenza di movimenti datati nel futuro.

### Due semantiche distinte, tenute separate a livello di query

È il punto più solido dell'impianto e vale la pena scriverlo per esteso, perché è anche la cosa che si rompe per prima quando si aggiungono funzionalità:

| Superficie | Semantica | Cosa esclude |
|---|---|---|
| Saldo totale, sparkline, andamento saldo | cassa, tutti i tipi | pending, conti archiviati o esclusi dal totale, valute diverse dalla principale |
| Card Oggi e Mese | cassa, solo spese ed entrate | pending, conti archiviati, trasferimenti e rettifiche (per tipo), altre valute |
| Statistiche, recap | statistica | trasferimenti, rettifiche, movimenti esclusi dalle statistiche, pending, altre valute. I rimborsi riducono la spesa invece di contare come entrata |
| Budget, spendibile oggi | statistica più il filtro per conto | tutto quanto sopra, più i conti con "Includi nel budget" spento |
| Griglia del widget | nessuna, è una scorciatoia | solo i pending |

Le esclusioni sono nelle query, non nei filtri della UI: una nuova schermata che riusa il DAO eredita la regola invece di doverla ricordare.

### Funzionalità costruite sopra questo impianto

- **Rettifica saldo**: inserisci il saldo reale, l'app genera l'`ADJUSTMENT` con la differenza.
- **Ricorrenze** nei tre tipi (uscita, entrata, trasferimento), a importo fisso o variabile, automatiche o con conferma, con motore idempotente (transazione per regola, mutex, watermark, indice unico come rete di sicurezza), catch-up all'avvio, worker giornaliero e notifica di pre-rinnovo.
- **Budget** complessivo più per categoria, con soglie 80/100 in aritmetica intera sui minor units e notifiche a watermark mensile.
- **Spendibile oggi**: budget meno spesa statistica, meno pending del mese, meno ricorrenze fisse in arrivo entro fine mese, con quota giornaliera arrotondata per difetto e scomposizione riga per riga nella card.
- **Carte di credito a saldo**: ciclo con giorno di chiusura e di addebito, estratto come trasferimento singolo dal conto collegato, watermark per l'idempotenza, indicatore di utilizzo sul fido.
- **Obiettivi di risparmio** come target sovrapposto a un conto di risparmio: il risparmiato è il saldo reale del conto, mai un contatore parallelo.
- **Proiezione a fine mese**: coda tratteggiata della sparkline, media della sola spesa non ricorrente più le ricorrenze fisse applicate alla loro data.
- **Filtri e ricerca** combinabili (data, tipo, categoria, senza categoria, conto, tag, importo, origine) con totale della vista filtrata per valuta, eliminazione in blocco con le due modalità "ricalcola i saldi" e "conserva i saldi".
- **Backup JSON versionato**, export e import CSV, cancellazione totale dei dati con ripianto delle categorie.

## 2. Cosa regge bene

- **Nessuna aritmetica monetaria nella UI.** Le medie, le quote giornaliere e i suggerimenti mensili sono calcolati nel dominio e arrivano alla UI già arrotondati alla scala della valuta.
- **Idempotenza dove conta.** Generazione ricorrenze e saldo dell'estratto carta usano lo stesso schema: watermark aggiornato con UPDATE mirato (mai upsert full-row, che sovrascriverebbe una modifica salvata dall'utente durante la run), transazione unica, mutex.
- **Le cifre affiancate raccontano la stessa storia.** L'esclusione dei conti archiviati dalle card Oggi/Mese e la fetta "Senza categoria" nell'anello sono entrambe scelte fatte per far quadrare due numeri vicini sullo schermo.
- **Il flag di inclusione nel budget è un asse davvero ortogonale** a quello del saldo totale, e le query di spesa lo applicano con una join, non con un filtro in memoria a valle.
- **Le pending sono contate una volta sola**: fuori dai saldi e dalle statistiche, dentro lo spendibile oggi, che è l'unico posto dove hanno senso.

## 3. Limiti e asimmetrie rilevate

Nessuno di questi è un bug: sono confini del modello attuale, elencati perché sono anche i punti da cui nascono le funzionalità mancanti della sezione 4.

1. **Ogni aggregato è mono-valuta.** Saldo totale, card Oggi e Mese, statistiche, budget, spendibile, obiettivi: tutti scoped alla valuta principale. Le statistiche lo dichiarano con la riga informativa, il saldo totale con il codice ISO sui conti non primari, ma un utente con un conto in valuta estera vede una parte del proprio denaro che non entra mai in una somma. È il gap strutturale più grande.

2. **Il budget vive nella valuta in cui è nato.** `ObserveBudgetProgressUseCase` filtra per valuta e `ObserveSafeToSpendUseCase` restituisce null senza un budget complessivo nella valuta principale: se la valuta principale cambia (regola a maggioranza o override), i budget esistenti spariscono dalla schermata senza spiegazione, per ricomparire se la valuta torna quella. Il comportamento è documentato nel codice ma non nella UI.

3. **Il rimborso non è collegato alla spesa.** Il flag riduce la spesa della categoria scelta al momento del rimborso, nel mese del rimborso. Due conseguenze concrete: un rimborso che arriva il mese dopo alleggerisce il mese sbagliato, e nulla garantisce che la categoria sia quella della spesa originale. Il caso limite (mese di soli rimborsi in una categoria) è già gestito, la fetta negativa viene filtrata dall'anello e il budget azzera invece di andare sotto zero.

4. **La coda di previsione ignora ciò che è già certo.** `BalanceForecastCalculator` applica solo le regole ricorrenti a importo fisso. Restano fuori i movimenti manuali datati nel futuro (che l'app sa già, tanto che li mostra nella divergenza "ad oggi") e le occorrenze pending già generate ma non confermate. Lo spendibile oggi invece i pending li conta: le due stime del mese non usano lo stesso insieme di impegni.

5. **L'estratto della carta si paga tutto o niente.** `SettleCreditCardStatementUseCase` genera un trasferimento per l'intero ciclo e avanza il watermark. Il pagamento parziale, il saldo minimo e la carta revolving non sono modellati: chi paga a rate deve inserire i trasferimenti a mano e la CTA continua a proporre l'estratto pieno.

6. **Le ricorrenze non contano le ripetizioni.** Esiste solo `endDate`, mentre VISION cita anche "numero di ripetizioni". Per un acquisto in 12 rate l'utente deve calcolarsi la data di fine, e l'hub non sa dire quante rate mancano.

7. **Il budget è solo il mese di calendario.** Nessun periodo "dal 27 al 27", nessun budget settimanale o annuale, nessun riporto del residuo al mese successivo. VISION prevede già il periodo configurabile come evoluzione.

8. **Una spesa appartiene a una sola categoria.** Lo scontrino misto (spesa alimentare più casalinghi) si registra come due movimenti separati, e in quel caso il totale dello scontrino non esiste da nessuna parte.

9. **Non esiste un aggregato dei crediti verso persone.** Le primitive ci sono e VISION le cita esplicitamente (flag "escludi dalle statistiche" per l'anticipo agli amici, flag rimborso per il rientro, tag per raggrupparli), ma nessuna schermata risponde a "quanto mi devono in totale, e chi".

10. **I tag non si amministrano.** Si creano inline e si filtrano, ma non si rinominano, non si uniscono e non si eliminano. È un limite che pesa di più se i tag diventano il meccanismo con cui si tracciano crediti o spese rimborsabili.

11. **La commissione di un trasferimento è a carico dell'utente**, come movimento separato. È una scelta già presa in VISION, ma sul prelievo con fee costringe a due inserimenti per un'operazione sola.

## 4. Funzionalità mancanti rispetto alle app premium

Ordine consigliato di implementazione. Il criterio è: prima ciò che aggiunge risposte senza aggiungere meccaniche di denaro (costo basso, rischio basso sui saldi), poi ciò che chiude un'asimmetria del modello esistente, poi ciò che sblocca più superfici insieme, infine le estensioni che aprono un dominio nuovo.

Le prime quattro righe sono state **approvate a luglio 2026** e sono pianificate in dettaglio come Fasi 11-14 di PLANNING (ADR 33, 34, 35, 36). Due di esse rivedono decisioni precedenti, e la revisione è parte della decisione: i prestiti erano stati chiusi come "sola categoria" nella Fase 9.13, e gli allegati erano esclusi da VISION per l'assenza di una libreria di image loading, cioè per una premessa tecnica. Le righe restanti sono state smistate il 28 luglio 2026: l'esito, riga per riga, è nella sezione 5.

Legenda della colonna "In Saldo": **Sì** già presente, **Parziale** le primitive esistono ma manca la funzionalità come tale, **No** assente.

| # | Funzionalità | Descrizione | In Saldo | Funzione esistente da usare (e quindi non implementare) |
|---|---|---|---|---|
| 1 | **Prestiti e finanziamenti come tipo di conto** (approvata, Fase 11) | Prestito, finanziamento o mutuo come conto a saldo negativo che si riduce: residuo, quota rimborsata, rate mancanti stimate, rata come trasferimento ricorrente | No | La copertura attuale (ricorrenza più categoria "Prestiti & Finanziamenti") registra il flusso di cassa ma non sa dire quanto manca, pur avendo in memoria ogni rata. Resta valida per chi vuole la rata dentro le statistiche e non traccia il prestito: le due modalità non si mescolano |
| 2 | **Crediti e debiti verso persone** (approvata, Fase 12) | Quanto hai prestato e quanto devi, con saldo per persona e rientri parziali | Parziale | Le primitive ci sono (spesa esclusa dalle statistiche, rientro come rimborso, tag con il nome), ma richiedono una convenzione che l'utente deve inventarsi e mantenere. Manca solo l'aggregato: si implementa come vista sui movimenti esistenti, non come registro parallelo |
| 3 | **Movimenti futuri e scadenze una tantum** (approvata, Fase 13) | Elenco "In arrivo", promemoria su un movimento datato nel futuro, e stima di fine mese che conta ciò che è già certo | Parziale | Il movimento futuro si registra già oggi e compare nella riga "ad oggi", ma nessuna schermata lo elenca e la coda di previsione lo ignora. La scadenza annuale si può ancora modellare come regola ricorrente, che resta la via giusta quando la scadenza si ripete davvero |
| 4 | **Allegati fotografici** (Fase 30, da valutare) | Foto dello scontrino o della garanzia sul movimento, senza permessi e senza OCR | No | Nessuna. Include la decisione sul backup, che diventa uno zip quando ci sono allegati: un backup che perde le foto non sarebbe un backup |
| 5 | **Rimborsi collegati alla spesa originale** (Fase 31, da valutare) | Il rimborso punta alla spesa che compensa, invece di limitarsi a scegliere la stessa categoria: la spesa risulta ridotta nel proprio mese e nella propria categoria | Parziale (flag rimborso) | Nel frattempo: entrata con flag "rimborso" e stessa categoria della spesa. Il flag non copre il caso a cavallo di due mesi |
| 6 | Gestione tag dedicata | Schermata per rinominare, unire ed eliminare i tag, che oggi si creano solo inline | No | Nessuna, i tag si amministrano solo cancellandoli dai singoli movimenti |
| 7 | Multi-divisa con gestione dei cambi | Tassi aggiornati con cache offline, controvalore nella valuta principale in saldo totale, statistiche e budget, sempre marcato come stimato | No (multi-valuta solo a livello di dato) | Oggi si usa la valuta per conto e per movimento, il tasso implicito mostrato nei trasferimenti cross-currency e la riga informativa delle statistiche. È il gap strutturale che sblocca più schermate insieme |
| 8 | Commissioni sui trasferimenti | La fee di un prelievo o di un bonifico registrata dentro l'operazione che la genera | No | Oggi: spesa separata subito dopo il trasferimento. Funziona ma richiede due inserimenti |
| 9 | Budget con periodo personalizzato e riporto | Periodo diverso dal mese di calendario (tipico "dal giorno dello stipendio") e residuo che si somma al mese dopo | No | Nessuna. Il budget è il mese di calendario, e il residuo si perde alla fine del mese |
| 10 | Acquisti a rate e numero di ripetizioni | Regola ricorrente che si spegne dopo N occorrenze, con "rata 3 di 12" leggibile nell'hub | Parziale (solo data di fine) | Oggi: regola ricorrente con data di fine calcolata a mano. Con la Fase 11 il conteggio delle rate esiste come stima sul conto prestito, ma resta derivato e non esatto |
| 11 | Spesa divisa su più categorie (split) | Uno scontrino unico ripartito su due o più categorie, mantenendo il totale dell'operazione | No | Oggi: due movimenti separati con la stessa data e descrizione. Il totale dello scontrino non esiste da nessuna parte |
| 12 | Rilevamento automatico delle ricorrenze | Euristica on-device che nota spese simili a cadenza regolare e propone la regola | No (già in roadmap v2.0) | Oggi: creazione manuale della regola dall'hub Ricorrenze |
| 13 | Pagamento parziale dell'estratto carta | Saldo parziale o minimo di un ciclo, con il residuo che resta a debito | No | Oggi: trasferimento manuale verso la carta. La CTA "Paga estratto" continua però a proporre l'estratto pieno |
| 14 | Beneficiario o esercente (payee) | Entità separata dalla descrizione, con storico e suggerimenti per beneficiario | No | Coperto in buona parte da descrizione più ricerca full-text e dai tag. Non implementare come nuova entità senza una richiesta reale: raddoppierebbe i selettori dell'editor |
| 15 | Sottocategorie | Un secondo livello sotto la categoria | No (rinviato per scelta in VISION) | Coperto dai tag, che assolvono lo stesso bisogno senza un secondo livello nei picker e nei grafici. Non implementare senza rivedere la decisione |
| 16 | Analisi avanzate (anno su anno, pattern) | Confronto tra periodi omologhi e ricorrenze di spesa individuate nello storico | No (già in roadmap v2.0) | Oggi: statistiche con periodo personalizzato più recap mensile |
| 17 | Arrotondamento spiccioli verso un obiettivo | Ogni spesa arrotondata all'euro superiore, la differenza trasferita al conto di risparmio | No | Nessuna. Si costruisce interamente sui trasferimenti esistenti e sugli obiettivi di risparmio, nessun modello nuovo |
| 18 | Export PDF, Excel, Google Sheets | Report formattati oltre al CSV | No (già in roadmap v2.0) | Oggi: export CSV filtrato, che si apre in Excel e in Sheets |
| 19 | Report periodico ricorrente | Riepilogo settimanale o mensile recapitato come notifica | Parziale | Coperto dal recap mensile "Saldo Wrapped" più le notifiche di soglia budget. Una notifica settimanale sarebbe una preferenza in più, non una funzionalità nuova |
| 20 | PIN, biometria, oscuramento in recenti | Blocco dell'app all'apertura e contenuto nascosto nelle app recenti | No (già in roadmap v2.0) | Nessuna. È la funzionalità premium più attesa che non tocca il modello finanziario |
| 21 | Backup automatico su cloud | Copia periodica fuori dal dispositivo, senza azione manuale | No (fase cloud da valutare) | Oggi: backup manuale su file via picker di sistema, salvabile su qualunque provider (Drive incluso) |
| 22 | Cifratura del backup | File di backup protetto da passphrase | No (già in roadmap v2.0) | Nessuna. Il file JSON è in chiaro e la UI lo dichiara |

### Funzionalità premium già coperte, da non implementare

Sono elencate perché nel confronto con le app concorrenti risultano "mancanti" a una lettura veloce, mentre in Saldo esistono con un altro nome o si ottengono con una funzione già presente.

| Funzionalità nelle app concorrenti | Come si fa in Saldo |
|---|---|
| Promemoria bollette e scadenze | Regola ricorrente più notifica di pre-rinnovo, con anticipo configurabile. La scadenza una tantum (bollo, IMU) passa alla Fase 13 |
| Rate di prestiti, finanziamenti e mutuo | Copertura rivista: la rata resta una ricorrenza, ma il prestito diventa un conto con il proprio residuo (Fase 11). La categoria "Prestiti & Finanziamenti" resta la via per chi non traccia il prestito e vuole la rata nelle statistiche |
| Carte di debito e bancomat come strumento separato | Si registrano sul conto corrente, che è da dove il denaro esce davvero. Il tipo dedicato è stato ritirato di proposito |
| Prelievo al bancomat | Trasferimento da conto corrente a contanti |
| Conto "salvadanaio" o envelope | Conto di risparmio, che di default resta fuori dal budget |
| Saldo previsto a fine mese | Coda tratteggiata della sparkline nella card Saldo totale |
| Quanto posso spendere oggi | Card Spendibile oggi, con il dettaglio del calcolo espandibile |
| Spese condivise o anticipate per altri | Flag "escludi dalle statistiche" sul movimento più flag rimborso sul rientro; l'aggregato per persona arriva con la Fase 12 |
| Riconciliazione con il saldo della banca | Rettifica saldo: si inserisce il saldo reale, l'app genera la differenza |
| Widget e inserimento rapido | Widget in due forme più scorciatoie dal launcher |

### Fuori perimetro per VISION

Elencate per chiudere il confronto: sono assenze deliberate, non gap. VISION è stata aggiornata a luglio 2026 perché due voci di questo elenco poggiavano su premesse che non reggevano il confronto con il bisogno reale.

| Funzionalità | Motivo dell'esclusione |
|---|---|
| Collegamento automatico ai conti bancari (open banking) | Nessuna credenziale bancaria a terze parti, nessun backend. Il motivo non è solo di principio: aggregare conti richiede una licenza da aggregatore o un provider a pagamento. Il bisogno sottostante è coperto dall'import CSV dell'estratto |
| Investimenti, titoli, crypto | Nessuna quotazione e nessun prezzo di mercato: sarebbe un'app diversa, con la rete nel percorso critico. La liquidità destinata a investimenti si traccia come importo su un conto |
| Beni non monetari (immobili, veicoli, portafogli valorizzati a mano) | L'app tiene contenitori di denaro reale. Il saldo totale è però già la lettura del patrimonio netto della parte liquida, debiti tracciati inclusi: non serve un modulo per averla |
| Piani di ammortamento e calcolo degli interessi | Confine spostato: il **debito residuo si traccia** (Fase 11, è un conto a saldo negativo), la matematica del piano no. Il residuo lo dichiara l'utente, come già fa con la rettifica saldo |
| Spese condivise multi-utente in stile Splitwise | Nessuna funzione social o multi-utente. La parte utile per un'app a utente singolo è l'aggregato dei crediti verso persone (Fase 12), che non richiede alcuna macchina di condivisione |
| OCR e lettura automatica dello scontrino | La foto dell'allegato è una fase a sé (Fase 30), la lettura automatica dell'importo no: sarebbe un'altra app. L'allegato è una prova da ritrovare, non una fonte di dati |

## 5. Decisioni prese su questa review (luglio 2026)

**Approvate**: prestiti e finanziamenti come tipo di conto, crediti e debiti verso persone, movimenti futuri con promemoria e stime allineate, allegati fotografici. Sono le Fasi 11, 12, 13 e 30 di PLANNING, con i rispettivi ADR 33, 34, 35 e 36.

**Valutate e scartate nella stessa sessione**, per non lasciarle come domande aperte:

- **Riconciliazione con flag "spuntato" per movimento**: è più utile della rettifica per chi tiene l'app per anni, perché dice *perché* i saldi divergono e non solo *che* divergono. Costa però una colonna, una modalità dedicata e un'abitudine che l'utente tipo non ha. La rettifica saldo continua a coprire il riallineamento.
- **Conti "bene" non transazionali** (casa, auto, portafoglio valorizzato a mano): poco codice, ma rompono la semantica di Account ("il luogo dove si trovano i soldi") e si infilerebbero in ogni picker di conto degli editor. Se un giorno rientrassero, dovrebbero essere un tipo esplicitamente escluso dai selettori dei movimenti.

**Vincoli rimossi dai documenti**, perché bloccavano decisioni implementative senza una ragione di prodotto: la nota di VISION contro qualsiasi libreria di image loading (riscritta: le icone restano vettori locali, gli allegati si decodificano con le API di piattaforma, e ogni dipendenza nuova resta una decisione esplicita) e l'esclusione secca di "prestiti e ammortamenti" (ora separa il residuo, che si traccia, dal piano di ammortamento, che resta fuori).

## 6. Smistamento completato (28 luglio 2026)

La tabella della sezione 4 è interamente smistata in PLANNING con una decisione esplicita del 28 luglio 2026. L'esito per riga:

- **Righe 1-4** (prestiti come conto, crediti e debiti verso persone, movimenti futuri, allegati): già approvate a luglio 2026 come Fasi 11, 12, 13 e 30; le Fasi 11, 12 e 13 sono state implementate, gli allegati sono fra le fasi da valutare.
- **Riga 20** (PIN, biometria, oscuramento in recenti): implementata nella Fase 14 (ADR 39), prima release dopo la v1.0.0.
- **Righe 6, 7, 8, 12, 16, 18, 22** (gestione tag, multi-divisa, commissioni, rilevamento ricorrenze, analisi avanzate, export PDF/Excel/Sheets, cifratura backup): erano già voci del backlog v2.0 e diventano le Fasi 16-22 di PLANNING, dettagliate e da eseguire prima della release v2.0 (Fase 23). Gli ADR di design si propongono all'avvio di ciascuna fase.
- **Riga 5** (rimborsi collegati alla spesa originale): era la Fase 15, spostata il 29 luglio 2026 fra le fasi da valutare come Fase 31, dove la sezione della fase spiega perché e la divide nelle sue due parti.
- **Righe 9, 10, 11, 13, 17, 19** (budget con periodo personalizzato, acquisti a rate, split, pagamento parziale dell'estratto, arrotondamento spiccioli, report periodico): pianificate nella Roadmap v3.0 di PLANNING come Fasi 24-29, dettagliate ora e da eseguire dopo la release v2.0.
- **Righe 14 e 15** (payee, sottocategorie): restano non implementate, come raccomandato nella tabella.
- **Riga 21** (backup automatico su cloud): resta nella Fase cloud di PLANNING, da valutare e non bloccante.

Dei limiti della sezione 3, il punto 2 (i budget in valuta diversa dalla principale spariscono dalla schermata senza spiegazione se la valuta principale cambia) è stato riverificato nel codice alla data dello smistamento e confermato: la chiusura è un punto esplicito della Fase 17. Gli altri limiti sono coperti dalle fasi corrispondenti (1 e 2 dalla Fase 17, 4 e 9 dalle Fasi 13 e 12 già implementate, 5 dalla 27, 6 dalla 25, 7 dalla 24, 8 dalla 26, 10 dalla 16, 11 dalla 18). Il limite 3 (il rimborso non è collegato alla spesa) resta aperto per scelta: la sua fase è fra quelle da valutare.
