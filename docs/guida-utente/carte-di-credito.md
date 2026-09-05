[Torna all'indice](README.md)

# 💳 Carte di credito a saldo

Una carta di credito "a saldo" (o ad addebito differito) è quella con cui spendi durante il mese e paghi tutto insieme il mese dopo, con un unico addebito sul conto corrente. In Saldo è un tipo di conto dedicato: le spese si registrano sulla carta come su qualunque altro conto, e l'addebito dell'estratto è un trasferimento dal conto collegato alla carta.

## Come si imposta

Crea un conto di tipo **Carta di credito** e compila i tre campi del ciclo:

- **Giorno di chiusura**: il giorno del mese in cui la banca chiude l'estratto. Un valore oltre la lunghezza del mese vale "ultimo giorno" (31 in febbraio significa il 28 o il 29).
- **Giorno di addebito**: il giorno del mese successivo in cui l'estratto viene addebitato sul conto.
- **Conto collegato**: il conto da cui parte l'addebito. Deve avere la stessa valuta della carta e non può essere un'altra carta di credito.

Puoi scegliere se l'addebito è **automatico** (l'app registra il trasferimento alla scadenza e ti avvisa con una notifica) oppure **con conferma** (alla scadenza compare una scheda "Estratto pronto" in Dashboard e nella lista Conti, con l'importo e il pulsante per pagarlo). Il **fido** è facoltativo: se lo indichi, la riga della carta mostra una barra di utilizzo.

## Come funziona il ciclo

Ogni spesa fatta con la carta riduce il suo saldo, che diventa negativo: è il debito che stai accumulando nel ciclo. Le spese contano nelle statistiche e nel budget nel giorno in cui le fai, esattamente come quelle da un conto corrente.

Alla scadenza l'app calcola l'**estratto**: quanto devi ancora per il ciclo chiuso, e lo addebita (o te lo propone) come trasferimento dal conto collegato alla carta. Il trasferimento riporta la carta a zero e non è una spesa: le spese le hai già contate quando le hai fatte, e contare anche l'addebito le raddoppierebbe.

Se il telefono è rimasto spento a cavallo di una scadenza, al primo avvio l'app recupera l'arretrato: ogni ciclo dovuto viene addebitato (o proposto) in ordine, dal più vecchio.

## Pagamenti fatti a mano

L'estratto tiene conto di tutto il denaro che la carta ha ricevuto. Se prima della scadenza trasferisci a mano una somma dal conto alla carta (per esempio un acconto, o perché hai pagato in anticipo dal sito della banca), l'importo proposto o addebitato alla scadenza si riduce di quella somma; se hai coperto tutto, l'app non addebita nulla e chiude il ciclo. Puoi quindi pagare una parte a mano e lasciare che l'app addebiti il resto.

Vale anche il contrario: se un ciclo si chiude in credito, perché un rimborso ricevuto sulla carta supera le spese del mese, il credito riduce l'estratto del ciclo successivo invece di andare perso.

Quando i cicli dovuti sono più di uno, i pagamenti coprono prima il debito più vecchio.

## Debito già maturato

Il saldo di una carta appena creata parte sempre da zero. Se al momento di creare il conto hai già speso qualcosa nel ciclo in corso, usa **Rettifica saldo** sulla carta e inserisci il saldo negativo reale: la differenza entra nel ciclo corrente e viene addebitata col prossimo estratto. La stessa rettifica serve, in qualunque momento, per riallineare la carta all'estratto della banca.

## Cosa non fa

- Non calcola interessi né il saldo minimo di una carta revolving: l'importo dell'estratto è la somma di quanto hai registrato, e un eventuale addebito di interessi da parte della banca lo registri tu come spesa.
- Non programma un pagamento parziale: puoi pagare in parte a mano, come descritto sopra, e l'app propone o addebita il resto.
- Non conosce le spese che non hai registrato: come per ogni conto, la rettifica saldo è il modo per rimettere la carta in pari.
