# 🎯 Obiettivi di risparmio

[Torna all'indice](README.md)

Un obiettivo di risparmio è un traguardo che fissi su un conto di risparmio: un importo da raggiungere ("Vacanze: 2.000 €"), con una data facoltativa entro cui arrivarci. L'obiettivo non è un salvadanaio a parte: si appoggia a un conto di risparmio esistente e il "risparmiato" è il saldo reale di quel conto. Non c'è denaro doppio o fittizio, solo una lettura di quanto hai già sul conto rispetto al traguardo.

## Dove si trovano

- **Impostazioni > Gestione > Obiettivi di risparmio**: la schermata con tutti gli obiettivi e il pulsante per crearne di nuovi.
- **Dashboard**: la card "Obiettivi di risparmio" mostra i progressi a colpo d'occhio. È opzionale e si attiva da Impostazioni > Dashboard; resta visibile anche senza obiettivi, come invito a crearne uno.

## Come è fatto un obiettivo

Il modello è semplice: un obiettivo vive sopra un conto di tipo Risparmio.

- Il **risparmiato** è il saldo attuale del conto collegato. Se sul conto ci sono già dei soldi quando crei l'obiettivo, sono già conteggiati.
- Alimenti l'obiettivo **mettendo denaro sul conto**, con un trasferimento manuale o ricorrente da un altro conto. Non c'è un pulsante "aggiungi contributo": si tratta sempre di normali trasferimenti verso il conto di risparmio.
- Togliere denaro dal conto fa scendere il risparmiato: l'obiettivo riflette sempre la realtà del conto.

## Un obiettivo per conto di risparmio

Ogni obiettivo è legato a **un solo** conto di risparmio, e ogni conto di risparmio può avere **un solo** obiettivo. È il modello più onesto con la filosofia dell'app: il progresso è il saldo reale del conto, e un saldo non può essere diviso tra due traguardi diversi.

Conseguenza pratica: per avere due obiettivi servono due conti di risparmio. Quando crei un nuovo obiettivo l'app ti propone i conti di risparmio ancora liberi (senza obiettivo). Se non ce ne sono, mostra un invito a crearne uno, in due varianti:

- **Non hai ancora un conto di risparmio**: ti invita a crearne il primo.
- **Hai già dei conti di risparmio, ma ognuno ha già un obiettivo**: te lo dice esplicitamente e ti propone di creare un altro conto di risparmio per il nuovo obiettivo, oppure di modificare un obiettivo esistente.

Dall'editor puoi creare un nuovo conto di risparmio al volo con la scorciatoia "Crea un nuovo conto di risparmio": apre l'editor conto già impostato sul tipo Risparmio.

## Creare un obiettivo

Nell'editor imposti:

- **Nome**: come chiami il traguardo (Vacanze, Fondo emergenze, Auto nuova).
- **Obiettivo**: l'importo da raggiungere. La valuta è quella del conto di risparmio collegato.
- **Conto di risparmio**: il conto su cui poggia l'obiettivo, scelto tra quelli liberi.
- **Data obiettivo** (facoltativa): la data entro cui vuoi arrivarci. Serve a calcolare il suggerimento di versamento mensile (vedi sotto).
- **Colore e icona**: per riconoscere l'obiettivo a colpo d'occhio nella lista e in Dashboard.

## Progresso e stato

Ogni obiettivo mostra una barra di avanzamento, la percentuale raggiunta (il risparmiato sul totale) e una riga di stato che cambia in base alla situazione. La riga segue questo ordine di priorità:

- **Obiettivo raggiunto**: il saldo del conto ha toccato o superato il traguardo.
- **Entro una data, con suggerimento**: se hai impostato una data obiettivo futura, l'app indica la data e quanto mettere da parte ogni mese per arrivarci in tempo, per esempio "Entro il 31 dicembre - metti da parte 150 €/mese". Il suggerimento è quanto manca diviso per i mesi che restano, arrotondato per eccesso.
- **Entro una data, senza suggerimento**: se la data obiettivo è nel mese corrente o già passata, mostra solo la data (non ha senso suggerire un versamento mensile).
- **Proiezione al ritmo attuale**: senza data obiettivo, ma con trasferimenti ricorrenti che alimentano il conto, l'app stima quando raggiungerai il traguardo a quel ritmo, per esempio "A questo ritmo, pronto entro marzo 2027".
- **Quanto manca**: negli altri casi, l'importo che resta da mettere da parte.

Quando esiste sia un suggerimento sia un ritmo di versamenti ricorrenti, la riga di stato diventa verde se i tuoi trasferimenti ricorrenti coprono già il versamento suggerito: sei in linea con il traguardo.

### Suggerimento e proiezione: da dove arrivano

- Il **suggerimento mensile** richiede una data obiettivo: è quanto manca diviso per i mesi di calendario che restano, arrotondato per eccesso.
- La **proiezione** guarda i trasferimenti ricorrenti che versano sul conto dell'obiettivo, nella stessa valuta, e ne calcola l'equivalente mensile per stimare la data di arrivo. I trasferimenti tra valute diverse non entrano nella stima: il loro importo effettivo si conosce solo al momento della conferma.

## Totale risparmiato e valute

In cima alla schermata degli obiettivi c'è il totale risparmiato, somma del risparmiato di tutti gli obiettivi nella valuta principale dell'app, con il relativo totale dei traguardi. Gli obiettivi in altre valute sono elencati sotto, fuori da questo totale, per non mescolare valute diverse in un'unica somma.

## Modificare o eliminare

- **Modifica**: puoi cambiare nome, importo obiettivo, data, colore e icona. Il conto di risparmio collegato invece è fisso: cambiarlo cambierebbe l'identità dell'obiettivo (e il suo risparmiato).
- **Eliminazione**: l'obiettivo viene rimosso, ma il conto di risparmio collegato e tutti i suoi movimenti restano. Elimini il traguardo, non i soldi.
