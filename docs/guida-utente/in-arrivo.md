# ⏳ In arrivo: movimenti futuri e scadenze

[Torna all'indice](README.md)

Un movimento con una data futura è un movimento a tutti gli effetti: lo registri quando lo sai, non quando succede. Il bollo dell'auto che scade fra tre settimane, l'IMU, la rata della scuola, lo stipendio che arriverà il 27: sono cose che conosci già e che non hanno bisogno di una regola ricorrente inventata per contenerle.

Saldo tiene questi movimenti in un posto solo, la schermata **In arrivo**, e li usa per rispondere a una domanda che il registro da solo non copre: cosa sta per succedere.

## Cosa finisce in "In arrivo"

Tre cose, in un'unica lista ordinata per data:

- i **movimenti con una data futura** che hai registrato tu;
- i **movimenti futuri generati da una regola ricorrente**;
- le **occorrenze da confermare**, cioè i movimenti ricorrenti in modalità conferma o a importo variabile che aspettano una tua risposta.

Le prime due sono decise: sono già nel registro. La terza no, ed è per questo che la riga lo dice ed è l'unica su cui puoi agire direttamente.

Un'occorrenza da confermare resta in elenco anche quando la sua data è passata: sta ancora aspettando una risposta, e nasconderla perché il giorno è trascorso è il modo migliore per far crescere una coda senza accorgersene.

## Dove si trova

- **Dashboard**: la card "In arrivo" con i prossimi movimenti e quanti sono in tutto. Si disattiva da Impostazioni > Dashboard; come la card dei crediti non compare quando non c'è niente in arrivo.
- **Dashboard, card "Da confermare"**: apre la stessa schermata già filtrata sulle occorrenze da confermare.
- **Impostazioni > Gestione > Ricorrenze**: una riga in cima all'hub porta qui. L'hub amministra le regole, "In arrivo" mostra cosa quelle regole (e ogni movimento datato) stanno per produrre.

## La schermata

In cima ci sono **due totali separati**, in uscita e in entrata, nella valuta principale. Non vengono mai sommati fra loro: 300 € che escono e 300 € che entrano non sono "non succederà niente". I trasferimenti non entrano in nessuno dei due, perché spostare denaro fra conti propri non è né un'uscita né un'entrata.

Se qualcosa è ancora da confermare, una riga sotto i totali lo dice: quegli importi non sono ancora dentro i totali, e la schermata non fa finta del contrario.

Sotto, i movimenti raggruppati per giorno. Toccando una riga:

- se è **da confermare**, si apre il pannello di conferma con il tastierino: inserisci l'importo reale e confermi, oppure salti l'occorrenza se quell'addebito non c'è stato;
- altrimenti si apre il **movimento nell'editor**, dove data, importo e promemoria si cambiano come su qualunque altro movimento.

Quando ci sono occorrenze da confermare compare anche un selettore in cima, **Tutto / Da confermare**, per isolare la coda.

## Il promemoria

Nell'editor di un movimento, appena scegli una **data futura**, compare l'interruttore **Ricordamelo**. Attivandolo riceverai una notifica prima della scadenza.

L'anticipo non è una nuova impostazione: è lo stesso che hai già scelto per il promemoria dei rinnovi ricorrenti (Impostazioni > Notifiche). "Con quanto anticipo vuoi saperlo" è una domanda sola, e chiederla due volte significherebbe solo avere due risposte che prima o poi divergono.

Alcune cose che vale la pena sapere:

- il promemoria arriva **una volta sola** per ogni data. Se sposti la data più avanti, si riarma: la nuova data non è ancora stata annunciata;
- se il dispositivo è rimasto spento, l'avviso arriva alla prima occasione utile, anche più vicino alla scadenza dell'anticipo previsto;
- se hai disattivato i promemoria in Impostazioni > Notifiche, l'interruttore continua a salvare la tua scelta ma l'editor te lo dice chiaramente: nessuna notifica arriverà finché non li riattivi.

## Come i movimenti futuri incidono sui numeri

Questa è la parte che conta, e la regola è una sola: **un movimento futuro non tocca nulla di ciò che riguarda oggi, finché non arriva il suo giorno.**

Nel dettaglio:

- **non entra** nelle statistiche, nel budget, nello spendibile di oggi, né nelle card "Oggi" e "Mese" della Dashboard;
- **entra** nel saldo del conto, perché è un movimento registrato. Quando il saldo totale corre avanti rispetto a ciò che hai davvero disponibile oggi, la card del saldo mostra anche la riga **"ad oggi"** con la cifra reale;
- **compare** nella coda tratteggiata della sparkline, nel giorno in cui cadrà.

La stima di fine mese parte proprio dal saldo "ad oggi", cioè dal punto in cui finisce la linea continua, e applica ogni cosa nota nel giorno in cui arriva: le ricorrenze a importo fisso, i movimenti futuri confermati e le occorrenze da confermare del mese. Così ogni voce viene contata una volta sola, e un movimento futuro generato da una regola non finisce contato due volte (una come movimento e una come occorrenza della regola).

Come sempre, la coda tratteggiata è una **stima**: le regole a importo variabile non hanno una cifra prevedibile e restano fuori.
