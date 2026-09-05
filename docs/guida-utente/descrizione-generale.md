# 🧭 Descrizione generale e filosofia

[Torna all'indice](README.md)

Saldo è un'app Android per tenere sotto controllo le spese personali. Serve a capire quanto spendi e in cosa, a sapere quanti soldi hai su ogni conto e a vedere le tue abitudini di spesa, senza collegarsi alla banca e senza registrazioni.

## Cos'è (e cosa non è)

Saldo è un tracciatore di spese, non un'app di home banking. Non si collega ai tuoi conti bancari e non legge i movimenti al posto tuo: i movimenti li inserisci tu, in pochi secondi. In cambio i dati restano sul telefono e non devi dare le credenziali della banca a un'app di terze parti.

L'obiettivo è la consapevolezza quotidiana: rispondere a "dove sono finiti i miei soldi?" in modo chiaro e immediato, non sostituire la banca né gestire investimenti o patrimoni complessi.

## A chi si rivolge

L'app è pensata per chi:

- vuole sapere quanto spende e in cosa, senza fatica.
- tiene i soldi in più posti (conto corrente, carte, contanti, wallet come PayPal o Revolut).
- preferisce non collegare i propri conti bancari ad app di terze parti.
- vuole registrare una spesa in pochi secondi.
- vuole che i propri dati restino sul proprio telefono.

## Cosa fa

- Registra spese, entrate e trasferimenti tra conti, con inserimento manuale rapido.
- Tiene aggiornato il saldo di ogni conto e il saldo totale.
- Gestisce movimenti ricorrenti e abbonamenti.
- Mostra un'analisi semplice e immediata delle abitudini di spesa.

## Cosa non fa (per scelta)

Alcune cose sono escluse di proposito, per restare un'app semplice e focalizzata:

- nessun collegamento automatico ai conti bancari (open banking).
- niente investimenti, trading o crypto.
- niente gestione patrimoniale complessa (immobili, asset).
- niente prestiti e piani di ammortamento.
- niente funzioni social o multi-utente.

## I principi

Questi principi guidano ogni scelta dell'app e non vengono derogati:

- **Funziona offline**: tutte le funzioni principali funzionano senza connessione. L'unica cosa che l'app scarica sono i tassi di cambio, e solo se hai conti in valuta diversa dalla principale: quella richiesta non porta con sé nessun tuo dato, i tassi restano in cache e senza rete vale l'ultimo tasso noto. Si può disattivare (vedi [Multi-valuta](multi-valuta.md)).
- **Privacy prima di tutto**: nessun dato lascia il dispositivo senza un'azione esplicita da parte tua. Nessuna telemetria, nessun account, nessun servizio di terze parti.
- **Nessun account obbligatorio**: non serve registrarsi, e non esiste nessuna registrazione da fare. Il backup è un file che scegli tu dove salvare.
- **Poca frizione**: registrare una spesa richiede pochi tap, con il tastierino numerico che si apre da subito.
- **Affidabile nel tempo**: quando il saldo dell'app si discosta da quello reale (capita di dimenticare movimenti), la rettifica saldo lo riallinea senza sporcare le statistiche.

## I tipi di movimento

Ogni movimento è di uno di questi quattro tipi. La differenza sta in come incide sul saldo e se rientra nelle statistiche:

| Tipo | Effetto sul saldo | Categoria | Nelle statistiche |
|------|-------------------|-----------|-------------------|
| Spesa | riduce un conto | sì | sì |
| Entrata | aumenta un conto | sì | sì |
| Trasferimento | sposta fondi tra due conti | no | no, mai |
| Rettifica saldo | allinea il saldo al valore reale | no | no |

I trasferimenti e le rettifiche non sono spese né entrate: spostano o correggono denaro, quindi restano fuori dalle statistiche per non falsare i totali.

## Due letture dello stesso mese: cassa e statistiche

Le schede **Oggi** e **Mese** della Dashboard rispondono a "quanto denaro è uscito ed entrato": sono cifre di cassa. Contano ogni spesa ed entrata confermata fino a oggi, anche quelle che hai escluso dalle statistiche o segnato come prestito a una persona, e un rimborso ricevuto vi compare come entrata.

Le **Statistiche**, il **Recap mensile**, i **budget** e lo **Spendibile** rispondono invece a "quanto ho consumato": sono cifre statistiche. Lasciano fuori i movimenti esclusi e i prestiti (prestare non è spendere), e un rimborso riduce la spesa della sua categoria invece di essere un'entrata.

Per questo, in un mese con un rimborso o con un prestito, la scheda Mese e il mese nelle Statistiche mostrano due cifre diverse: non è un errore, sono due domande diverse. In entrambe le letture un movimento con data futura entra solo nel giorno in cui cade.

## Il saldo è sempre calcolato

Il saldo di un conto non è un numero salvato che l'app aggiorna a mano: è sempre ricalcolato come saldo iniziale più la somma dei movimenti di quel conto. Per questo correggere, aggiungere o eliminare un movimento aggiorna il saldo in modo coerente, senza rischio di disallineamenti.

## Lingua e valuta

L'app è disponibile in italiano e inglese e segue le convenzioni locali per numeri, valute e date. Ogni movimento conserva il proprio importo e la propria valuta, e ogni conto ha la sua valuta; la valuta principale dell'app si sceglie al primo avvio e si cambia dalle Impostazioni. Se hai conti in valuta estera, l'app ne mostra il controvalore stimato nella valuta principale, sempre dichiarato come stima: vedi [Multi-valuta e tassi di cambio](multi-valuta.md).
