# 🔁 Movimenti ricorrenti

[Torna all'indice](README.md)

Un movimento ricorrente è una regola che registra da sola un movimento a intervalli regolari: un abbonamento ogni mese, lo stipendio ogni 27, un accantonamento settimanale sul conto risparmio. Definisci la regola una volta e l'app crea i singoli movimenti alle date previste.

Attenzione alla distinzione: la **regola** è il modello ricorrente (nome, importo, frequenza, conto). Il **movimento** è la singola registrazione che la regola genera a ogni scadenza. Eliminare una regola non cancella i movimenti che ha già creato.

## Dove si trovano

L'hub dei movimenti ricorrenti è raggiungibile dalle Impostazioni ("Movimenti ricorrenti") e mostra tre schede:

- **Uscite**: spese ricorrenti.
- **Entrate**: accrediti ricorrenti.
- **Trasferimenti**: spostamenti ricorrenti tra due conti.

Ogni scheda riporta il totale mensile e la proiezione su un anno delle regole che contiene. Le uscite e le entrate ricorrenti alimentano anche la card "Movimenti ricorrenti" in Dashboard, con il prossimo addebito in arrivo.

## Le tre tipologie

### Uscite ricorrenti

Spese che si ripetono: abbonamenti, affitto passivo, assicurazioni, rate. Ogni addebito riduce il saldo del conto scelto e, se assegni una categoria, rientra nelle statistiche e nel budget come qualsiasi altra spesa.

### Entrate ricorrenti

Accrediti che si ripetono: stipendio, pensione, affitti attivi. Ogni occorrenza aumenta il saldo del conto scelto.

### Trasferimenti ricorrenti

Spostamenti periodici da un conto di partenza a un conto di destinazione, per esempio un accantonamento mensile verso il conto di risparmio. Come ogni trasferimento in Saldo:

- È un **singolo movimento** con conto di partenza e conto di destinazione, non due movimenti separati.
- È **escluso dalle statistiche e dal budget**: sposta denaro, non lo spende.
- Un trasferimento ricorrente verso un conto di risparmio alimenta la card "Risparmio pianificato".

Quando i due conti usano **valute diverse**, l'importo ricevuto dipende dal cambio del giorno e non può essere fissato in anticipo: il trasferimento diventa quindi da confermare a ogni occorrenza (vedi "Importo variabile e conferma" più sotto).

## Frequenza e prima data

Ogni regola ha una frequenza e una data di partenza (chiamata "Primo addebito", "Primo accredito" o "Primo trasferimento" a seconda della tipologia).

### Frequenze disponibili

- Giornaliera
- Settimanale
- Mensile
- Bimestrale (ogni 2 mesi)
- Trimestrale (ogni 3 mesi)
- Semestrale (ogni 6 mesi)
- Annuale

### Giorno di riferimento e mesi corti

Per le frequenze mensili e più lunghe, il giorno del mese viene preso dalla data di partenza. Se quel giorno non esiste in un mese più corto, l'occorrenza viene spostata all'ultimo giorno disponibile: una regola impostata al 31 cade il 30 a novembre e il 28 (o 29) a febbraio, poi torna al 31 nei mesi che lo hanno. Lo spostamento è ricalcolato ogni mese dal giorno di riferimento, non trascinato dal mese precedente.

Le frequenze giornaliera e settimanale contano semplicemente i giorni o le settimane dalla data di partenza.

### Data di fine (opzionale)

Puoi impostare una scadenza ("Con scadenza"): dopo quella data la regola smette di generare movimenti. Senza scadenza la regola continua a tempo indeterminato.

## Modalità di registrazione

Sotto "Registrazione" scegli come deve comportarsi la regola a ogni scadenza. Le modalità sono due, più un caso particolare per gli importi che cambiano.

### Automatica

Alla data prevista il movimento viene registrato da solo, con l'importo fisso della regola, e ne ricevi una notifica informativa. Non devi fare nulla: è la modalità adatta ad addebiti di importo noto e costante (un abbonamento a prezzo fisso, l'affitto).

### Con conferma

Alla data prevista il movimento viene creato ma resta **in attesa**: non tocca i saldi finché non lo confermi. Lo trovi nella lista "Da confermare", dove puoi:

- **Confermare**: il movimento entra nei saldi con il suo importo.
- **Saltare**: il movimento viene scartato, come se quell'occorrenza non fosse avvenuta.

È la modalità adatta quando vuoi decidere caso per caso se e quando registrare l'addebito.

### Importo variabile e conferma

Se attivi "Importo variabile" non indichi un importo nella regola: l'app non può conoscerlo in anticipo (bolletta, spesa che cambia ogni volta). Una regola a importo variabile lavora sempre in modalità con conferma. A ogni scadenza crea un movimento in attesa a importo zero, che non incide sui saldi; nella lista "Da confermare" inserisci l'importo effettivo e confermi, oppure salti l'occorrenza.

Lo stesso vale per i trasferimenti tra valute diverse: l'importo ricevuto nella valuta di destinazione si inserisce alla conferma.

## Quando vengono creati i movimenti

Il motore che genera i movimenti segue tre principi: non riempie lo storico passato, recupera i giorni saltati, non crea doppioni.

### Alla creazione: nessun recupero dello storico

Quando salvi una regola nuova, l'app **non** genera i movimenti a ritroso fino alla data di partenza. Se crei oggi una regola giornaliera con data di partenza due settimane fa, viene registrato solo il movimento di oggi, non i quattordici giorni precedenti.

Questo è voluto. La data di partenza serve ad ancorare la cadenza (su quale giorno cadono le occorrenze), non a ricostruire il passato: aggiungere un abbonamento che paghi da mesi non deve generare a ritroso decine di addebiti che falserebbero i saldi. Se ti servono i movimenti passati, inseriscili a mano come movimenti normali.

### Recupero all'apertura e in background

Se resti qualche giorno senza usare l'app, o con il telefono spento, le occorrenze mancate non vanno perse:

- **All'apertura dell'app**: a ogni avvio l'app registra tutte le occorrenze dovute dall'ultima generazione fino a oggi. Se tieni il telefono spento due giorni, riaccendendolo e aprendo l'app al terzo giorno trovi registrati i movimenti dei due giorni spenti più quello di oggi.
- **In background**: un controllo giornaliero registra le occorrenze dovute anche nei giorni in cui non apri l'app (a telefono acceso).

Ogni movimento recuperato porta la **sua data di competenza**, non la data in cui l'app lo ha registrato: i due addebiti dei giorni spenti risultano datati ai giorni giusti, non ammucchiati sul giorno di riaccensione.

Le occorrenze in modalità con conferma o a importo variabile vengono recuperate allo stesso modo, ma come movimenti in attesa: al riavvio trovi un elemento "Da confermare" per ciascuna scadenza dovuta.

### Nessun doppione

Recupero all'apertura e controllo in background possono capitare a ridosso l'uno dell'altro senza creare duplicati: ogni occorrenza già generata viene riconosciuta e saltata. Riaprire l'app più volte nello stesso giorno non produce movimenti ripetuti.

## Notifiche

- **Movimenti registrati**: quando una o più regole automatiche registrano un addebito.
- **Da confermare**: quando ci sono movimenti in attesa (modalità con conferma o importo variabile) che aspettano la tua conferma.
- **Pre-rinnovo (opzionale)**: un promemoria prima della scadenza, per esempio "Netflix si rinnova tra 3 giorni", con anticipo configurabile. Va attivato: di default è spento.

## Ricorrenze suggerite

Nell'hub, sopra l'elenco, c'è la riga **"Cerca ricorrenze non registrate"**. Quando la tocchi, l'app guarda gli ultimi 12 mesi dei tuoi movimenti e propone quelli che registri a mano con una cadenza regolare: l'abbonamento sempre allo stesso importo, la bolletta che cambia cifra ma torna ogni due mesi.

- La ricerca parte **solo da quel tap**. Non c'è nessuna analisi automatica in background: se non la usi, non costa niente in batteria né in tempo.
- Ogni suggerimento mostra il nome che deduce dalle tue descrizioni, l'importo con la cadenza (con "≈" quando l'importo varia), la prossima occorrenza prevista e quante volte l'ha trovato.
- Un tap sul suggerimento apre l'editor della regola **già precompilato**: nulla viene creato finché non salvi tu. Lo storico non viene rigenerato, quindi non ti ritrovi movimenti doppi per i mesi passati.
- La **X** scarta un suggerimento: non riappare più.
- L'esito resta salvato con la sua data, così riaprendo l'hub lo ritrovi come l'avevi lasciato; toccare di nuovo la riga rifà la ricerca.
- Le serie ferme non vengono proposte, e nemmeno quelle già coperte da una regola che hai. Trasferimenti, rettifiche e movimenti esclusi dalle statistiche restano fuori: una regola non saprebbe riprodurli.

Tutto avviene sul dispositivo: nessun dato esce per essere analizzato.

## Modificare o eliminare una regola

- **Modifica**: cambiare importo, nome, conto o altri campi non tocca i movimenti già registrati; vale per le occorrenze future. Se cambi la cadenza (frequenza, data di partenza o giorno di riferimento), la generazione riparte allineata alla nuova cadenza, sempre senza recuperare il passato della nuova pianificazione.
- **Eliminazione**: la regola viene rimossa e non genera più movimenti. I movimenti che ha già creato **restano**: se vuoi eliminarli, agisci sui singoli movimenti.

## Effetto su saldi, budget e spendibile

- I movimenti **automatici** incidono sui saldi appena registrati. Le uscite con categoria contano nel budget e nelle statistiche.
- I movimenti **da confermare** (conferma o importo variabile) non incidono su nulla finché non li confermi.
- I **trasferimenti**, ricorrenti o no, restano esclusi da statistiche e budget.
- Lo "Spendibile oggi" tiene conto anche degli addebiti ricorrenti previsti entro fine mese e dei movimenti ancora da confermare, così la cifra riflette gli impegni già noti.
