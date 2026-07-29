# 🤝 Crediti e debiti verso persone

[Torna all'indice](README.md)

Prestare denaro a una persona non è una spesa, e riaverlo non è un'entrata: i soldi escono e rientrano davvero dal conto, ma non sono consumi. Saldo lo tratta esattamente così: il movimento incide sul saldo del conto come ogni altro, resta sempre fuori dalle statistiche e dal budget, e va ad alimentare una schermata che tiene il conto di chi ti deve e a chi devi.

Non c'è un registro separato dei prestiti: la lettura "chi deve cosa" è la somma dei movimenti che hai già registrato, con un nome sopra.

## Dove si trova

- **Impostazioni > Gestione > Crediti e debiti**: la schermata completa.
- **Dashboard**: la card "Crediti e debiti" con i due totali e le persone con qualcosa di aperto. Si disattiva da Impostazioni > Dashboard; a differenza delle altre card non compare quando non c'è niente di aperto, così chi non presta denaro non se la ritrova mai.

## Segnare un movimento come prestito

Nell'editor di una spesa o di un'entrata, in fondo al modulo, c'è l'interruttore **Prestito o debito**. Attivandolo compare il campo con il nome della persona e, sotto, una riga che spiega cosa significa il movimento nel verso in cui lo stai registrando.

I due versi sono quelli che già conosci, non ce ne sono di nuovi:

- **Spesa con una persona** = hai prestato tu. Il denaro esce dal conto e resta fuori: è un tuo credito.
- **Entrata con una persona** = ti hanno restituito, oppure hai ricevuto tu un prestito. Il denaro entra nel conto.

Il campo del nome è a testo libero, con **completamento automatico**: i nomi già usati compaiono come suggerimenti da toccare, così non ti ritrovi lo stesso amico scritto in tre modi diversi. E se capita, non è un problema: "Luca", "luca" e "Lucà" contano comunque come la stessa persona.

### L'esclusione dalle statistiche è automatica

Attivando l'interruttore, il movimento viene escluso dalle statistiche e l'apposito interruttore si disattiva mostrando il valore imposto: non è una scelta, è la definizione stessa di prestito. Se togli il segno di prestito, il controllo torna a te con il valore che avevi scelto prima.

Quel denaro quindi non compare nei grafici, non consuma il budget e non entra nello spendibile di oggi. Il saldo del conto, invece, lo registra come qualunque altro movimento: quei soldi hanno lasciato il conto per davvero.

## La schermata Crediti e debiti

In cima ci sono **due totali separati**: quanto ti devono e quanto devi. Non vengono mai sommati fra loro: 200 € prestati a una persona e 200 € ricevuti da un'altra non sono la stessa cosa di "non devo niente a nessuno".

Sotto, una riga per persona con:

- il **saldo aperto**, con l'indicazione se è denaro **da riavere** o **da restituire**;
- quanti movimenti lo compongono e la data dell'ultimo;
- il pulsante per registrare il rientro (vedi sotto).

Le persone con qualcosa di aperto stanno in cima, dalla più recente; quelle in pari restano più sotto, con la loro storia consultabile.

Toccando una riga si apre l'elenco completo dei movimenti con quella persona, senza limiti di periodo: un prestito di due anni fa è rilevante quanto quello di ieri.

## Rientri parziali

Non serve nessuna funzione dedicata: il saldo di una persona è la somma con segno dei suoi movimenti, quindi ogni restituzione, anche parziale, avvicina il totale allo zero. Hai prestato 100 e te ne hanno resi 30? La riga mostra 70 da riavere e conta due movimenti.

## Registrare il rientro

Il pulsante sulla riga apre l'editor **già precompilato**: verso opposto (se ti devono, un'entrata; se devi, una spesa), importo residuo, stessa persona, interruttore già attivo. Restano da confermare l'importo e la data reali, che conosce solo chi ha ricevuto o restituito il denaro: se il rientro è diverso da quanto proposto, basta correggerlo e la differenza resta aperta.

## Che cosa non tocca

I crediti e i debiti verso persone non entrano nel saldo totale come voce a sé, non compaiono nelle statistiche, non consumano il budget e non modificano lo spendibile di oggi. L'unica schermata che li somma è la loro.

Il motivo è semplice: sommare i crediti al saldo totale conterebbe quel denaro due volte, perché ha già lasciato il conto ed è già scalato dal saldo.

## Prestito da una persona o conto Prestito?

Sono due situazioni diverse:

- **Un prestito da una banca o una finanziaria** (mutuo, finanziamento, cessione) ha un piano, un residuo e una rata: si traccia come [conto di tipo Prestito](prestiti-e-finanziamenti.md), con la rata come trasferimento ricorrente.
- **Denaro preso in prestito da una persona** di solito non ha né piano né rata: si traccia qui, segnando l'entrata come prestito e le eventuali restituzioni come spese con la stessa persona.

## Backup e ripristino

Il nome della controparte fa parte del movimento, quindi viene salvato ed è ripristinato dal backup insieme a tutto il resto. Anche l'esportazione CSV ha una colonna **Controparte**, e l'importazione la rilegge: una riga con una controparte viene esclusa dalle statistiche da sola, senza che tu debba ricordartene. Vedi [Esportazione e importazione CSV](esportazione-importazione-csv.md).
