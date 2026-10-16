[Torna all'indice](README.md)

# 🏠 Widget del mese

Due widget leggono il mese invece di registrare un movimento: "In parole" e "Il mese". Si aggiungono dal menu dei widget del launcher, come "Aggiunta rapida", e si configurano con una pressione prolungata.

## In parole

Il mese in una frase, con i fatti che la misura del widget lascia stare sotto:

- con un budget mensile complessivo, quanto puoi ancora spendere fino a fine mese e quanto al giorno (lo stesso calcolo della card "Spendibile" della Dashboard, che conta anche gli addebiti già in arrivo); se spese e addebiti superano il budget, di quanto;
- senza budget, quanto hai speso nel mese;
- sotto, quanto hai speso sul budget, i giorni rimasti e il **prossimo addebito**: la prossima spesa ricorrente, con il giorno ("oggi", "domani", il giorno della settimana o la data) e l'importo quando è fisso.

Una riga stretta mostra solo la cifra che conta ("Ancora 410,00 €"); una riga larga la frase intera; da due righe in su anche il mese e i fatti. Una riga che non sta intera non viene mostrata a metà: resta fuori, cominciando da quella che si legge per ultima.

## Il mese

L'anello del budget mensile complessivo: si riempie con quanto hai speso, e una linea sottile all'interno segna quanto del mese è passato, così "64% speso a metà mese" si legge senza parole. Al centro la percentuale; accanto o sotto, quanto hai speso su quale budget e i giorni rimasti. Superato il budget, l'anello e la percentuale prendono il colore delle spese e la riga dice "oltre".

Senza un budget mensile complessivo il widget mostra "Imposta un budget": toccandolo, Saldo si apre sui budget.

## Quando si aggiornano

Diversamente da "Aggiunta rapida", questi due widget seguono i movimenti: si ridisegnano quando registri, modifichi o elimini un movimento, quando cambi un budget o una regola ricorrente, e poco dopo mezzanotte, perché i giorni rimasti scendano e "domani" diventi "oggi". La sveglia di mezzanotte non sveglia il telefono: scatta al primo risveglio, che è l'unico momento in cui la home si vede.

Un addebito ricorrente che scade oggi viene registrato appena salvi la regola, quindi il widget lo mostra subito; finché non è registrato (l'app chiusa da giorni, per esempio) "In parole" lo indica come addebito di oggi. Se lo registra il lavoro giornaliero di Saldo ad app chiusa, i widget si aggiornano in quel momento.

Senza nessuno dei due widget sulla home, Saldo non fa niente di tutto questo.

## Cifre e blocco dell'app

Con il blocco dell'app attivo i due widget non mostrano importi: la home non è dietro il PIN. "In parole" dice la percentuale del budget usata, i giorni rimasti e il prossimo addebito senza cifra; "Il mese" mostra l'anello e la percentuale. Lo stesso vale per l'anteprima nelle loro impostazioni.

Ogni widget ha anche l'interruttore "Gli importi", per nascondere le cifre senza il PIN; con il PIN attivo restano nascoste comunque.

## Impostazioni

Come per "Aggiunta rapida": in cima il widget vero alle misure che può avere, poi lo sfondo (chiaro, scuro, come il telefono o uno dei sei colori) con l'opacità, poi il contenuto ("Gli importi" e, per "In parole", "Il prossimo addebito"). Ogni scelta vale subito; "Ripristina questo widget" rimette quelle di partenza.
