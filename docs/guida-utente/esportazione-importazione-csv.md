# 🔄 Esportazione e importazione CSV

[Torna all'indice](README.md)

Il CSV è un formato di tabella che si apre con qualsiasi foglio di calcolo (Excel, Google Fogli, Numbers). Saldo lo usa per due cose: portare fuori i tuoi movimenti (per analisi o archivio) e portarne dentro da un altro file o da un'altra app.

Entrambe le funzioni sono nella schermata **Movimenti**, nel menu con i tre puntini in alto a destra: "Esporta CSV" e "Importa CSV". L'importazione è disponibile anche quando non hai ancora nessun movimento, così puoi popolare un'installazione nuova partendo da un file.

## Esportazione

L'esportazione crea un file con i movimenti **della vista corrente**: se hai dei filtri attivi (periodo, conto, categoria, ricerca), esporta solo i movimenti che passano quei filtri. Il conteggio nella finestra di esportazione ti dice quanti movimenti finiranno nel file.

### Separatore e decimali

Prima di esportare scegli il separatore delle colonne. La scelta determina anche come sono scritti i decimali, in modo che il file si apra pulito nel foglio di calcolo:

- **Punto e virgola (`;`)**: i decimali usano la virgola (`1.234,56`). È la combinazione che si apre correttamente in Excel con impostazioni italiane.
- **Virgola (`,`)**: i decimali usano il punto (`1234.56`). È la convenzione CSV internazionale.

Le due opzioni non entrano mai in conflitto: quando il separatore di colonna è la virgola i decimali usano il punto, e viceversa, così il carattere che separa i decimali non coincide mai con quello che separa le colonne. La scelta viene ricordata per la volta successiva.

### Colonne del file

Il file esportato ha una riga di intestazione e una riga per movimento, con queste colonne:

- **Data**: giorno del movimento (formato `AAAA-MM-GG`).
- **Tipo**: Spesa, Entrata, Trasferimento o Rettifica.
- **Categoria**, **Descrizione**.
- **Conto**: il conto del movimento (per i trasferimenti, il conto di partenza).
- **Verso conto**: il conto di destinazione, solo per i trasferimenti.
- **Importo**: l'effetto sul saldo del conto (negativo per spese e trasferimenti in uscita, positivo per le entrate).
- **Valuta**: la valuta dell'importo.
- **Importo ricevuto** e **Valuta ricevuta**: la gamba in entrata dei trasferimenti tra valute diverse.
- **Tag**, **Nota**.
- **Controparte**: il nome della persona, per i movimenti marcati come prestito o restituzione (vedi [Crediti e debiti](crediti-e-debiti.md)); vuoto su tutti gli altri.
- **Escluso dalle statistiche**: riporta "Sì" quando il movimento non entra nelle statistiche, vuoto altrimenti. È sempre "Sì" sui movimenti con una controparte, che sono esclusi per costruzione.
- **Rimborso**: riporta "Sì" sulle entrate marcate come rimborso (che nelle statistiche riducono le spese invece di contare come entrata), vuoto altrimenti.
- **Ricorrente**: riporta "Sì" quando il movimento è stato generato da una regola di Movimenti ricorrenti, vuoto se lo hai inserito a mano. È un'indicazione informativa, utile per esempio a filtrare gli abbonamenti nel foglio di calcolo.

Al termine il file viene passato al menu di condivisione del telefono: puoi salvarlo tra i file, inviarlo via email o aprirlo in un'altra app. Il file non lascia mai il dispositivo se non sei tu a condividerlo.

I campi di testo (descrizione, nota, categoria, conto, tag, controparte) che iniziano con un carattere che un foglio di calcolo interpreterebbe come formula (per esempio `=`, `+`, `@`) vengono fatti precedere da un apostrofo, così all'apertura del file restano testo e non vengono eseguiti. L'importazione rimuove di nuovo quell'apostrofo, quindi il testo originale viene ripristinato senza differenze.

## Importazione

L'importazione legge un file CSV e ne ricava dei movimenti, aggiungendoli al registro. È pensata sia per rileggere un file esportato da Saldo, sia per importare da file di formato diverso.

### Prima regola: l'importazione aggiunge soltanto

L'importazione non modifica e non elimina mai i movimenti che hai già: si limita ad aggiungere quelli nuovi. Se qualcosa va storto durante la scrittura, non viene inserito nulla (l'operazione è tutta-o-niente) e i tuoi dati restano intatti.

### Riconoscimento del formato

Non serve che il file sia identico a quello esportato da Saldo. L'importazione applica alcune regole per adattarsi:

- **Separatore di colonna**: viene riconosciuto da solo (punto e virgola, virgola o tabulazione), leggendolo dalla riga di intestazione.
- **Decimali**: sono interpretati sia con la virgola sia con il punto; i separatori delle migliaia (`1.234,56` oppure `1,234.56`) vengono gestiti. Un importo tra parentesi, come `(50)`, è letto come negativo.
- **Colonne per nome**: le colonne sono riconosciute dal nome dell'intestazione, non dalla posizione, quindi possono essere in qualsiasi ordine. Sono accettati sia i nomi italiani sia quelli inglesi (per esempio "Data"/"Date", "Importo"/"Amount", "Conto"/"Account"), oltre ad alcune varianti comuni. Le colonne che non servono vengono ignorate.
- **Colonne minime**: bastano una colonna data e una colonna importo. Le altre sono facoltative.
- **Date**: sono riconosciuti i formati più comuni (`2026-07-08`, `08/07/2026`, `08-07-2026` e simili); un eventuale orario dopo la data viene ignorato.

### Regole intelligenti sulle righe

Quando un dato non è perfetto ma è ricavabile, l'importazione lo sistema e te lo segnala nel report come "riga corretta in automatico":

- **Tipo mancante**: se manca la colonna del tipo (o il valore non è riconosciuto), il tipo viene dedotto dal segno dell'importo (negativo = spesa, positivo = entrata).
- **Segno dell'importo**: se il tipo dice "spesa" ma l'importo è positivo (o viceversa), il segno viene corretto per rispettare il tipo.
- **Valuta mancante**: se la riga non indica la valuta, si usa quella del conto.

### Conti, categorie e tag

I conti, le categorie e i tag citati nel file sono cercati per nome tra quelli esistenti (senza distinzione tra maiuscole e minuscole). Se un nome non esiste, il comportamento dipende dalle opzioni:

- **Crea i conti mancanti** (attiva di default): i conti nuovi vengono creati, con la valuta indicata nella riga (o la valuta principale se manca). Se la disattivi, le righe con un conto sconosciuto vengono scartate.
- **Crea le categorie mancanti** (attiva di default): le categorie nuove vengono create. Se la disattivi, la categoria sconosciuta viene semplicemente lasciata vuota sul movimento.
- **Crea i tag mancanti** (attiva di default): i tag nuovi vengono creati. Se la disattivi, i tag sconosciuti vengono ignorati.

Una riga senza conto, con un importo o una data illeggibili, o un trasferimento senza conto di destinazione, viene scartata e conteggiata tra gli errori.

### Controparte, esclusione dalle statistiche e rimborso

Anche queste tre colonne vengono rilette, con le stesse regole che valgono nell'app:

- **Controparte**: il nome viene ripreso così com'è, senza doverlo creare da nessuna parte (non esiste un'anagrafica delle persone). Una riga con una controparte viene automaticamente **esclusa dalle statistiche**, anche se la colonna dell'esclusione dice altro: prestare denaro non è una spesa e riaverlo non è un'entrata. Il movimento compare quindi subito nella schermata Crediti e debiti.
- Solo le spese e le entrate possono avere una controparte. Se un trasferimento o una rettifica ne indica una, il nome viene lasciato cadere e la riga è conteggiata tra quelle corrette in automatico.
- **Escluso dalle statistiche**: vale anche da sola, per i movimenti che hai escluso a mano senza che ci sia di mezzo una persona.
- **Rimborso**: viene letto solo sulle entrate, perché solo un'entrata può essere il rimborso di una spesa.

Le colonne che indicano "sì" o "no" sono lette con tolleranza (`Sì`, `Yes`, `X`, `1`, `true` e simili valgono "sì"). Un valore vuoto o non riconoscibile vale sempre "no", così un contenuto inatteso non attiva mai un flag per sbaglio.

### Rilevazione dei duplicati

Con l'opzione **Salta i duplicati** attiva (default), l'importazione riconosce ed evita i doppioni in due direzioni:

- rispetto ai movimenti **già presenti** nel registro;
- rispetto alle **altre righe dello stesso file** (una riga ripetuta viene importata una volta sola).

Due movimenti sono considerati lo stesso quando coincidono per data, tipo, importo, valuta, conto e descrizione. La controparte e i due flag non entrano nel confronto: così un file esportato prima che quelle colonne esistessero continua a riconoscere i movimenti da cui proviene, invece di reimportarli in doppio. Se disattivi l'opzione, ogni riga valida viene importata anche se identica a una esistente.

### Anteprima e report

L'importazione è in due passi. Dopo aver scelto il file vedi un'**anteprima** che riassume, senza aver ancora toccato nulla:

- quanti movimenti sono pronti da importare;
- quanti duplicati verrebbero saltati;
- quante righe sono state corrette in automatico;
- quante righe verrebbero scartate per errori;
- quali conti, categorie e tag verrebbero creati.

Nell'anteprima puoi cambiare le opzioni e vedere i conteggi aggiornarsi. Quando confermi, i movimenti vengono scritti e compare un **report finale** con il riepilogo di quello che è stato fatto.

### Limiti e note

- L'importazione elabora fino a diecimila righe per file; se il tuo file è più grande, dividilo e importalo a blocchi.
- La colonna "Ricorrente" del file esportato **non** viene reimportata: un movimento importato è sempre un movimento inserito a mano, non collegato a una regola ricorrente.
- Per una rilettura fedele conviene partire da un file esportato da Saldo: colonne, valute e segni sono già nel formato atteso.
- Attenzione al formato dei numeri: se il file usa la virgola sia come separatore di colonna sia come separatore dei decimali, gli importi con i decimali vengono spezzati in due colonne. In quel caso esporta o salva il file con il punto e virgola come separatore di colonna.
