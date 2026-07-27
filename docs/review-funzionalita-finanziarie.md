# Review delle funzionalità finanziarie

Review del comportamento finanziario di Saldo e confronto con le funzionalità delle app di tracciamento spese a pagamento (Wallet by BudgetBakers, Money Manager, Bluecoins, Spendee, MoneyWiz, 1Money, Toshl).

Riferimento: versionCode 145, versionName 0.9.106. La review è stata fatta leggendo dominio, DAO, use case ed editor, non solo la documentazione.

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

Ordine consigliato di implementazione. Il criterio è: prima ciò che chiude un'asimmetria del modello già esistente (costo basso, rischio basso), poi ciò che sblocca più superfici insieme, infine le estensioni che aggiungono un dominio nuovo.

Legenda della colonna "In Saldo": **Sì** già presente, **Parziale** le primitive esistono ma manca la funzionalità come tale, **No** assente, **Fuori perimetro** esclusa da VISION per scelta.

| # | Funzionalità | Descrizione | In Saldo | Funzione esistente da usare (e quindi non implementare) |
|---|---|---|---|---|
| 1 | Rimborsi collegati alla spesa originale | Il rimborso punta alla spesa che compensa, invece di limitarsi a scegliere la stessa categoria: la spesa risulta ridotta nel proprio mese e nella propria categoria | Parziale (flag rimborso) | Nel frattempo: entrata con flag "rimborso" e stessa categoria della spesa. Da implementare, il flag non copre il caso a cavallo di due mesi |
| 2 | Gestione tag dedicata | Schermata per rinominare, unire ed eliminare i tag, che oggi si creano solo inline | No | Nessuna, i tag si amministrano solo cancellandoli dai singoli movimenti |
| 3 | Prestiti e debiti verso persone (IOU) | Registro di quanto hai prestato e quanto devi, con saldo per persona e rientro parziale | Parziale | Le primitive ci sono: spesa con "escludi dalle statistiche" per l'uscita, entrata con flag "rimborso" per il rientro, tag con il nome della persona per raggruppare, filtro per tag per la lista. Manca solo l'aggregato "quanto mi devono": da implementare come vista sui movimenti esistenti, non come nuova meccanica di denaro |
| 4 | Multi-divisa con gestione dei cambi | Tassi aggiornati con cache offline, controvalore nella valuta principale in saldo totale, statistiche e budget, sempre marcato come stimato | No (multi-valuta solo a livello di dato) | Oggi si usa la valuta per conto e per movimento, il tasso implicito mostrato nei trasferimenti cross-currency e la riga informativa delle statistiche. Da implementare: è il gap strutturale che sblocca più schermate insieme |
| 5 | Commissioni sui trasferimenti | La fee di un prelievo o di un bonifico registrata dentro l'operazione che la genera | No | Oggi: spesa separata subito dopo il trasferimento. Funziona ma richiede due inserimenti |
| 6 | Budget con periodo personalizzato e riporto | Periodo diverso dal mese di calendario (tipico "dal giorno dello stipendio") e residuo che si somma al mese dopo | No | Nessuna. Il budget è il mese di calendario, e il residuo si perde alla fine del mese |
| 7 | Acquisti a rate e numero di ripetizioni | Regola ricorrente che si spegne dopo N occorrenze, con "rata 3 di 12" leggibile nell'hub | Parziale (solo data di fine) | Oggi: regola ricorrente con data di fine calcolata a mano. Copre l'addebito, non il conteggio delle rate |
| 8 | Spesa divisa su più categorie (split) | Uno scontrino unico ripartito su due o più categorie, mantenendo il totale dell'operazione | No | Oggi: due movimenti separati con la stessa data e descrizione. Il totale dello scontrino non esiste da nessuna parte |
| 9 | Rilevamento automatico delle ricorrenze | Euristica on-device che nota spese simili a cadenza regolare e propone la regola | No (già in roadmap v2.0) | Oggi: creazione manuale della regola dall'hub Ricorrenze |
| 10 | Pagamento parziale dell'estratto carta | Saldo parziale o minimo di un ciclo, con il residuo che resta a debito | No | Oggi: trasferimento manuale verso la carta. La CTA "Paga estratto" continua però a proporre l'estratto pieno |
| 11 | Beneficiario o esercente (payee) | Entità separata dalla descrizione, con storico e suggerimenti per beneficiario | No | Coperto in buona parte da descrizione più ricerca full-text e dai tag. Non implementare come nuova entità senza una richiesta reale: raddoppierebbe i selettori dell'editor |
| 12 | Sottocategorie | Un secondo livello sotto la categoria | No (rinviato per scelta in VISION) | Coperto dai tag, che assolvono lo stesso bisogno senza un secondo livello nei picker e nei grafici. Non implementare senza rivedere la decisione |
| 13 | Analisi avanzate (anno su anno, pattern) | Confronto tra periodi omologhi e ricorrenze di spesa individuate nello storico | No (già in roadmap v2.0) | Oggi: statistiche con periodo personalizzato più recap mensile |
| 14 | Arrotondamento spiccioli verso un obiettivo | Ogni spesa arrotondata all'euro superiore, la differenza trasferita al conto di risparmio | No | Nessuna. Si costruisce interamente sui trasferimenti esistenti e sugli obiettivi di risparmio, nessun modello nuovo |
| 15 | Export PDF, Excel, Google Sheets | Report formattati oltre al CSV | No (già in roadmap v1.5 e v2.0) | Oggi: export CSV filtrato, che si apre in Excel e in Sheets |
| 16 | Report periodico ricorrente | Riepilogo settimanale o mensile recapitato come notifica | Parziale | Coperto dal recap mensile "Saldo Wrapped" più le notifiche di soglia budget. Una notifica settimanale sarebbe una preferenza in più, non una funzionalità nuova |
| 17 | PIN, biometria, oscuramento in recenti | Blocco dell'app all'apertura e contenuto nascosto nelle app recenti | No (già in roadmap v1.5) | Nessuna. È la funzionalità premium più attesa che non tocca il modello finanziario |
| 18 | Backup automatico su cloud | Copia periodica fuori dal dispositivo, senza azione manuale | No (fase cloud da valutare) | Oggi: backup manuale su file via picker di sistema, salvabile su qualunque provider (Drive incluso) |
| 19 | Cifratura del backup | File di backup protetto da passphrase | No (già in roadmap v2.0) | Nessuna. Il file JSON è in chiaro e la UI lo dichiara |

### Funzionalità premium già coperte, da non implementare

Sono elencate perché nel confronto con le app concorrenti risultano "mancanti" a una lettura veloce, mentre in Saldo esistono con un altro nome o si ottengono con una funzione già presente.

| Funzionalità nelle app concorrenti | Come si fa in Saldo |
|---|---|
| Promemoria bollette e scadenze | Regola ricorrente più notifica di pre-rinnovo, con anticipo configurabile |
| Rate di prestiti, finanziamenti e mutuo | Regola ricorrente in uscita più la categoria "Prestiti & Finanziamenti" (o "Affitto/Mutuo" per la rata del mutuo). Decisione già presa: niente feature dedicata, VISION esclude prestiti e ammortamenti |
| Carte di debito e bancomat come strumento separato | Si registrano sul conto corrente, che è da dove il denaro esce davvero. Il tipo dedicato è stato ritirato di proposito |
| Prelievo al bancomat | Trasferimento da conto corrente a contanti |
| Conto "salvadanaio" o envelope | Conto di risparmio, che di default resta fuori dal budget |
| Saldo previsto a fine mese | Coda tratteggiata della sparkline nella card Saldo totale |
| Quanto posso spendere oggi | Card Spendibile oggi, con il dettaglio del calcolo espandibile |
| Spese condivise o anticipate per altri | Flag "escludi dalle statistiche" sul movimento più flag rimborso sul rientro (vedi anche la riga 3 della tabella sopra) |
| Riconciliazione con il saldo della banca | Rettifica saldo: si inserisce il saldo reale, l'app genera la differenza |
| Widget e inserimento rapido | Widget in due forme più scorciatoie dal launcher |

### Fuori perimetro per VISION

Elencate per chiudere il confronto: sono assenze deliberate, non gap.

| Funzionalità | Motivo dell'esclusione |
|---|---|
| Collegamento automatico ai conti bancari (open banking) | Nessuna credenziale bancaria a terze parti, nessun backend |
| Investimenti, titoli, crypto | Nessuna quotazione, nessuna rete: la liquidità si traccia con un conto di risparmio |
| Patrimonio netto, immobili, asset | Saldo non è un gestore patrimoniale |
| Piani di ammortamento e calcolo interessi | Prestiti e ammortamenti esclusi esplicitamente |
| Spese condivise multi-utente in stile Splitwise | Nessuna funzione social o multi-utente, valutabile molto in là |
| Foto dello scontrino e OCR | Nessuna libreria di image loading in nessuna versione, l'app non gestisce immagini reali |
