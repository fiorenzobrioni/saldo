# 📉 Prestiti e finanziamenti

[Torna all'indice](README.md)

Un prestito personale, un finanziamento o un mutuo si tracciano in Saldo come un **conto con saldo negativo che si riduce nel tempo**. Il conto risponde alla domanda che conta: quanto manca da restituire. È lo stesso modello della carta di credito, con il verso opposto: lì il debito nasce dalle spese del ciclo e si azzera a ogni estratto, qui il debito esiste già quando crei il conto e scende a ogni rata.

## L'idea in tre punti

- Il **saldo iniziale è il debito residuo di oggi**, con il segno meno: la cifra che la banca dichiara se chiedi "quanto mi resta da pagare", interessi del piano compresi. Non è il capitale che ti hanno erogato. Se sul residuo hai un dubbio, lo trovi sull'ultimo estratto o nell'area clienti della banca.
- La **rata è un trasferimento** dal conto di pagamento al conto del prestito, tipicamente una regola ricorrente mensile. Non è una spesa: sposta denaro da una tua tasca a un tuo debito, quindi non compare nelle statistiche di spesa, come ogni trasferimento. Rata dopo rata il saldo del conto sale verso lo zero, e l'ultima rata lo porta esattamente a zero.
- L'app **non calcola interessi e non costruisce piani di ammortamento**. Il residuo è la somma di quello che hai dichiarato e delle rate registrate; se nel tempo diverge da quello della banca (per esempio dopo una rinegoziazione o un'estinzione parziale), lo riallinei con la rettifica saldo, come per ogni altro conto.

## Creare il conto

Nell'editor del conto scegli il tipo **Prestito o finanziamento** e inserisci come saldo iniziale il residuo di oggi con il segno meno (per esempio `-8.400`). Per questo tipo il saldo iniziale è obbligatorio e deve essere negativo: zero o un importo positivo non descrivono un debito, e l'editor lo segnala.

Alla selezione del tipo il conto parte **escluso dal saldo totale e dal budget**. Sono preimpostazioni, non vincoli: due letture possibili, a tua scelta.

- **Escluso dal totale** (default): la Dashboard continua a rispondere a "quanto ho", e la rata si legge come uscita reale di cassa, perché il denaro lascia un conto incluso nel totale.
- **Incluso nel totale**: il saldo totale diventa la lettura patrimoniale, quanto possiedi al netto dei debiti. Un mutuo a sei cifre coprirà le oscillazioni quotidiane degli altri conti: è una scelta consapevole.

## Registrare le rate

Crea un **trasferimento ricorrente** dal conto da cui paghi verso il conto del prestito, con l'importo della rata e la cadenza reale (mensile, trimestrale). È la stessa meccanica degli accantonamenti verso un conto di risparmio. Una rata straordinaria o un'estinzione parziale si registrano come trasferimento manuale, sempre verso il conto del prestito.

## La scheda del prestito

Nella schermata Conti, sotto la riga del conto, compare una scheda con:

- la **barra di avanzamento** di quanto hai già rimborsato rispetto al debito dichiarato, con la percentuale;
- il **debito residuo**, cioè il saldo attuale del conto letto in positivo;
- la **prossima rata**, con importo e data, presa dalla regola ricorrente;
- una **stima delle rate mancanti**: il residuo diviso per la rata, arrotondato per eccesso. È marcata come stima perché l'app non conosce il piano della banca: conosce il residuo e il ritmo delle tue rate.

La scheda compare solo se esiste una regola ricorrente che versa sul conto: senza una rata registrata non c'è nulla da stimare. Se più regole versano sullo stesso prestito, la stima somma i loro equivalenti mensili; le regole in un'altra valuta non entrano nella stima, perché il loro importo effettivo si conosce solo alla conferma.

## Quando il debito arriva a zero

A residuo azzerato la scheda mostra lo stato **estinto** e suggerisce di **archiviare** il conto, non di eliminarlo: i movimenti storici (tutte le rate pagate) restano visibili e il conto sparisce dai selettori. Ricordati anche di chiudere o dare una data di fine alla regola ricorrente della rata.

## E la categoria "Prestiti & Finanziamenti"?

Chi preferisce vedere la rata **dentro** le statistiche di spesa può continuare a non tracciare il prestito come conto: registra la rata come spesa ricorrente nella categoria "Prestiti & Finanziamenti". In cambio rinuncia al residuo e alle rate mancanti. Le due modalità **non si mescolano** sullo stesso prestito: conto più categoria conterebbero due volte lo stesso denaro.

Un prestito **ricevuto da una persona** (non da una banca), senza piano né rata fissa, non ha bisogno di un conto: se ne occuperà la gestione di crediti e debiti verso persone, prevista dalla roadmap.
