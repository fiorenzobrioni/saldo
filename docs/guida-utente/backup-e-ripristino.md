[Torna all'indice](README.md)

# 💾 Backup e ripristino

Il backup di Saldo è un file che scegli tu dove salvare. Non serve nessun account, non serve la rete e non c'è nessun cloud in mezzo: l'app scrive il file dove indichi (memoria del telefono, Drive, qualsiasi provider di documenti) e il resto è tuo. Tutto avviene da Impostazioni > Backup.

## Cosa c'è dentro un backup

Il file contiene tutto quello che hai messo nell'app:

- conti (con saldo iniziale, tipo, colore, ordine, archiviati compresi) e movimenti, tag inclusi
- categorie, il loro ordine e le categorie personalizzate
- movimenti ricorrenti, budget e obiettivi di risparmio
- controparti, note, esclusioni dalle statistiche, rimborsi, promemoria: i movimenti tornano esattamente come li avevi registrati
- le **impostazioni**: tema, valuta principale, conversione automatica delle valute, conto predefinito, promemoria pre-rinnovo, primo giorno della settimana, separatore CSV, card visibili in Dashboard

L'ultima riga è la differenza pratica quando cambi telefono: ripristini il backup e ritrovi l'app configurata come l'avevi lasciata, non solo i numeri.

Restano fuori due cose, di proposito:

- il **PIN del blocco app** e le sue opzioni: un file di backup può finire in mille posti, e un PIN di sei cifre dentro un file si indovina in fretta. Dopo un ripristino il blocco si riattiva da Impostazioni > Sicurezza, in trenta secondi.
- la **cache dei tassi di cambio** e l'esito della ricerca ricorrenze: non sono dati tuoi, si ricostruiscono da soli.

## Esportare un backup

1. Apri Impostazioni > Backup.
2. Tocca "Esporta backup".
3. Scegli nome e destinazione nella finestra di sistema.

Il nome proposto contiene la data (`saldo-backup-2026-07-31.json`), così i file si ordinano da soli. La schermata mostra sempre la data dell'ultimo backup riuscito, o dice che non ne hai mai fatto uno.

## Proteggere il file con una passphrase

Sotto il bottone di export c'è l'interruttore "Proteggi con una passphrase". Spento, il file è un JSON leggibile: chiunque lo apra vede i tuoi movimenti. Acceso, il file viene cifrato (AES-256) con una chiave derivata dalla passphrase sul dispositivo, e senza quella passphrase non è leggibile da nessuno, Saldo compresa.

Con la protezione attiva il bottone diventa "Cifra ed esporta" e l'app chiede la passphrase **prima** di farti scegliere la destinazione, due volte, per essere sicura che non ci sia un errore di battitura nell'unica copia che esiste.

**Non c'è modo di recuperare la passphrase.** Saldo funziona offline e non la salva da nessuna parte: se la perdi, quel file non è più ripristinabile. I dati sul telefono non sono toccati, e puoi esportare un backup nuovo in qualsiasi momento. Vale lo stesso patto del PIN: nessuna email di recupero, nessuna domanda di sicurezza.

Il file cifrato resta un `.json` e continua a dichiarare in testa che cosa è (un backup di Saldo, cifrato): ciò che non si legge è il contenuto. Il nome proposto porta un `-enc` in coda (`saldo-backup-2026-07-31-enc.json`), solo per riconoscerlo a occhio in una cartella: al momento del ripristino Saldo guarda il contenuto del file, non il nome, quindi puoi rinominarlo come vuoi.

## Ripristinare

1. Apri Impostazioni > Backup.
2. Tocca "Ripristina da backup" e scegli il file.
3. Se il file è cifrato, l'app chiede la passphrase. Una passphrase sbagliata te lo dice subito e ti lascia riprovare senza cancellare quello che hai scritto.
4. Prima di toccare qualsiasi cosa, Saldo mostra **cosa contiene il file**: la data dell'export, quanti conti, movimenti, categorie, ricorrenze, tag, budget e obiettivi, e se porta con sé le impostazioni.
5. Confermi, e i dati attuali vengono sostituiti.

Il ripristino **sostituisce** tutto quello che c'è nell'app, non lo aggiunge (per aggiungere movimenti a quelli esistenti c'è l'[importazione CSV](esportazione-importazione-csv.md)). Se qualcosa va storto durante la scrittura, l'operazione viene annullata per intero e i dati attuali restano come erano.

Il ripristino è disponibile anche al primo avvio: nella schermata di benvenuto, invece di creare il primo conto, puoi partire da un backup, cifrato o no.

## Note pratiche

- I backup esportati da versioni precedenti di Saldo restano importabili, anche se non contengono le impostazioni: in quel caso i dati tornano al loro posto e le impostazioni di questo telefono non vengono cambiate.
- La cifratura è una scelta per file: puoi tenerla spenta per il backup che archivi in casa e accenderla per quello che carichi su un servizio online.
- Cambiare telefono: esporta un backup, copia il file sul telefono nuovo, installa Saldo e ripristina dal primo avvio.
- Nella stessa schermata c'è anche **Cancella tutti i dati**, che riporta l'app a com'era il primo giorno. La conferma ricorda quando hai fatto l'ultimo backup, proprio perché è l'unico modo di tornare indietro.
