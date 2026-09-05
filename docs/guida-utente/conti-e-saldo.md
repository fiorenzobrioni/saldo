[Torna all'indice](README.md)

# 🏦 Conti e saldo

Un conto è un posto dove stanno i tuoi soldi: il conto in banca, una carta, i contanti nel portafoglio, un wallet digitale. Ogni movimento appartiene a un conto, e il saldo di ogni conto è sempre ricalcolato dai suoi movimenti.

## Tipi di conto

Il tipo serve a raggruppare i conti, a preimpostare icona e colore e, per due tipi, ad attivare un comportamento proprio:

- **Conto corrente**: il conto di tutti i giorni. Anche le spese fatte con bancomat e carte di debito si registrano qui, perché è da qui che escono i soldi.
- **Conto di risparmio**: il recinto dei soldi messi da parte. Si alimenta con trasferimenti e di default resta fuori dal budget, così attingere ai risparmi non consuma il budget del mese. Puoi appoggiarci un [obiettivo di risparmio](obiettivi-di-risparmio.md).
- **Carta prepagata**, **Contanti**, **Wallet digitale**, **Altro**: contenitori semplici.
- **Carta di credito**: ha un ciclo di addebito differito, spiegato in [Carte di credito a saldo](carte-di-credito.md).
- **Prestito o finanziamento**: un debito che si riduce a ogni rata, spiegato in [Prestiti e finanziamenti](prestiti-e-finanziamenti.md).

Nell'editor del conto ogni tipo ha una breve descrizione d'uso sotto il selettore.

## Saldo iniziale e saldo calcolato

Alla creazione indichi il **saldo iniziale**, cioè quanto c'è sul conto in quel momento. Da lì in avanti il saldo mostrato è sempre: saldo iniziale, più le entrate, meno le spese, più e meno i trasferimenti. Non è un numero salvato che può disallinearsi: correggere o eliminare un movimento aggiorna il saldo da solo.

Quando il saldo dell'app si discosta da quello reale (capita di dimenticare movimenti), la **rettifica saldo** lo riallinea: inserisci il saldo reale e l'app registra la differenza come movimento di rettifica, che non entra nelle statistiche.

Se hai registrato movimenti con una data futura, sotto il saldo compare la riga **"ad oggi"** con quanto è davvero disponibile oggi: il saldo principale li conta già, la riga no.

## Due interruttori

- **Includi nel saldo totale**: se spento, il conto resta tracciato ma non entra nel Saldo totale della Dashboard (utile per un conto cointestato). Prestiti e finanziamenti nascono con questo interruttore spento.
- **Includi nel budget**: se spento, le spese fatte da questo conto non consumano i budget del mese né lo Spendibile. Il conto di risparmio nasce con questo interruttore spento.

## La lista dei conti

I conti attivi sono raggruppati per tipo, con il sottototale del gruppo nell'intestazione (e il suo "ad oggi" quando differisce). Puoi riordinarli trascinando la maniglia, solo all'interno del proprio tipo: l'ordine vale anche nel dettaglio della card Saldo totale in Dashboard. I conti archiviati stanno in una sezione richiudibile in fondo.

Per una carta di credito in modalità con conferma, la riga mostra anche l'estratto pronto e il pulsante per pagarlo.

## Il dettaglio del conto

Toccando un conto (dalla lista o dal dettaglio della card Saldo totale) si apre la sua schermata:

- **saldo** e, quando servono, la riga "ad oggi" e il controvalore stimato nella valuta principale per un conto in valuta estera;
- **mini-grafico degli ultimi 30 giorni** del saldo di quel solo conto;
- la **scheda del tipo**: per una carta di credito la barra di utilizzo e l'estratto da pagare, per un prestito rimborsato, residuo e rate, per un conto di risparmio l'obiettivo collegato con il suo avanzamento;
- i **movimenti del conto**, un mese alla volta: le frecce scorrono i mesi che hanno movimenti (compresi i mesi futuri, se hai registrato qualcosa in avanti), la riga sotto il mese riporta quanti movimenti sono, le spese e le entrate del mese. Un tocco su un movimento lo apre in modifica, uno scorrimento verso sinistra lo elimina con la possibilità di annullare;
- le **azioni**: il pulsante **Nuovo movimento** apre l'editor già sul conto, l'icona della matita apre l'editor del conto, il menu in alto a destra offre Rettifica saldo, Archivia (o Ripristina) ed Elimina.

## Archiviare o eliminare

Un conto chiuso non si elimina: si **archivia**. I suoi movimenti restano nello storico e nelle statistiche, il conto sparisce dai selettori e dal saldo totale. L'archiviazione si annulla con un tocco dall'avviso che compare, o con Ripristina dal dettaglio.

L'**eliminazione** è possibile solo per un conto senza movimenti e senza regole ricorrenti che lo usano: negli altri casi l'app propone l'archiviazione e dice perché.
