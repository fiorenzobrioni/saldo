# Saldo

> Documento di visione del prodotto. Descrive **cosa** è l'app, **per chi** e **perché**.
> Il piano di implementazione dettagliato è in [PLANNING.md](./PLANNING.md).

---

## Identità del progetto

> **Un'app Android moderna, offline-first e privacy-first che aiuta l'utente a monitorare le proprie spese, mantenere sotto controllo il saldo dei propri account e comprendere come vengono utilizzati i propri soldi - senza trasformarsi in un'app di home banking o di gestione patrimoniale.**

L'obiettivo non è sostituire la banca, ma offrire **consapevolezza finanziaria quotidiana**.

### Filosofia

L'utente deve capire subito:

> "Questa app mi aiuta a controllare dove finiscono i miei soldi."

Non:

> "Questa app sostituisce la banca."

### Il target

L'utente tipo è una persona che:

- vuole sapere quanto spende e in cosa, senza fatica
- ha più "contenitori" di denaro (conto, carta, contanti, PayPal, Revolut)
- non si fida di dare le credenziali bancarie ad app di terze parti (niente open banking / aggregazione conti)
- vuole inserire una spesa in meno di 5 secondi
- vuole che i dati restino suoi, sul suo telefono

---

# Vision del prodotto

L'app è un **Expense Tracker evoluto**, non un sistema bancario.

**Si concentra su:**

- tracciamento spese e entrate (inserimento manuale, velocissimo)
- saldo aggiornato per ogni account e saldo totale
- trasferimenti tra account
- ricorrenze e abbonamenti
- analisi semplice e immediata delle abitudini di spesa

**Non include (per scelta, non per limite):**

- collegamento automatico ai conti bancari (open banking): niente credenziali bancarie a terze parti. Chi vuole evitare di digitare tutto usa l'import CSV dell'estratto
- investimenti, trading, crypto: nessuna quotazione, nessun prezzo di mercato. La liquidità destinata a investimenti si traccia come importo su un conto, il suo valore no
- gestione di beni non monetari (immobili, veicoli, portafogli valorizzati a mano): l'app tiene solo contenitori di denaro reale. Il saldo totale resta la somma di quel denaro, debiti tracciati inclusi, e in questo senso è già una lettura di patrimonio netto della parte liquida: non serve un modulo per averla
- **piani di ammortamento e calcolo degli interessi**: il debito residuo di un prestito si traccia (è un conto a saldo negativo che si riduce), la matematica del piano no. Il residuo lo dichiara l'utente, come fa già con la rettifica saldo
- funzioni social o condivisione multi-utente (valutabile molto in là): le spese anticipate per altri si tracciano come crediti verso una persona, che è la parte utile senza la macchina della condivisione

**Principi non negoziabili:**

- **Offline-first**: ogni funzione core funziona senza rete
- **Privacy-first**: nessun dato lascia il dispositivo senza azione esplicita dell'utente
- **Zero backend obbligatorio**: nessun server proprietario
- **Nessun account obbligatorio**: l'account Google serve solo per backup/export opzionali
- **Zero frizione**: registrare una spesa deve richiedere 2–3 tap

---

# Piattaforma e stack tecnico

- **Android only** (inizialmente) - min SDK 33 (Android 13), target SDK fissato esplicitamente (attualmente 36) e aggiornato con chore dedicata (ADR 14 in PLANNING.md). Scelta deliberata: niente supporto a device legacy, niente code path duplicati (dynamic color e `POST_NOTIFICATIONS` sempre disponibili)
- **Kotlin** (100%, no Java)
- **Jetpack Compose** con **Material 3 / Material You** (dynamic color sempre disponibile grazie a min SDK 33, nessuna palette di fallback necessaria)
- **Room** per la persistenza
- **DataStore (Preferences)** per le impostazioni
- **Kotlin Coroutines + Flow** per reattività
- **Architettura MVVM + Use Cases + Repository** (vedi sezione Architettura)
- **Hilt** per dependency injection
- **KSP** (non KAPT) per Room e Hilt
- **Version Catalog** (`libs.versions.toml`) per la gestione delle dipendenze

---

# Modello dati concettuale

## Transaction (Movimento)

Ogni movimento è di uno di questi tipi:

| Tipo | Effetto sul saldo | Ha categoria | Compare nelle statistiche |
|------|------------------|--------------|---------------------------|
| **Spesa** (EXPENSE) | riduce un account | sì | sì |
| **Entrata** (INCOME) | aumenta un account | sì | sì |
| **Trasferimento** (TRANSFER) | sposta fondi tra due account | no | no (mai) |
| **Rettifica saldo** (ADJUSTMENT) | allinea il saldo al valore reale | no | no (di default) |

Campi:

- **importo**: `BigDecimal` nel dominio; **persistito come `Long` in unità minori (centesimi)** nel database, poiché Room non supporta `BigDecimal` nativamente. Questo garantisce precisione assoluta ed evita gli errori tipici di float/double.
- **valuta**: codice ISO 4217 (`EUR`, `USD`, …), salvata per ogni movimento
- **data/ora**: `Instant` UTC + timezone del dispositivo al momento dell'inserimento (per raggruppamenti giornalieri corretti anche in viaggio)
- **categoria**: solo per spese/entrate
- **account**: obbligatorio (per i trasferimenti: account di origine + destinazione)
- **descrizione**: testo libero, opzionale
- **tag**: zero o più, opzionali
- **flag "escludi dalle statistiche"**: per casi particolari (es. anticipo per amici che verrà rimborsato)
- **riferimento alla ricorrenza**: se il movimento è stato generato da una regola ricorrente
- **note**: campo lungo opzionale
- **controparte**: nome della persona, quando il movimento è denaro prestato o restituito (vedi Crediti e debiti verso persone)
- **allegati**: zero o più foto (scontrino, garanzia, ricevuta), opzionali - funzione da valutare, vedi Allegati

### Rimborsi

Un rimborso (es. un reso Amazon, un amico che restituisce la cena) **non è un'entrata "vera"**: gonfierebbe le statistiche di income. Modellazione:

- il rimborso è un movimento di tipo entrata **collegato opzionalmente alla spesa originale**
- nelle statistiche, il rimborso riduce la spesa della categoria di origine invece di comparire come entrata

```text
15/03  Ristorante        -60,00 €   (categoria: Ristorante)
16/03  Rimborso amici    +40,00 €   (collegato → riduce Ristorante a -20,00 €)
```

L'app usa la versione semplificata: entrata con flag "rimborso" e categoria della spesa scelta dall'utente, che l'editor restringe alle sole categorie di spesa. Il collegamento esplicito alla spesa originale è **da valutare** e non è pianificato (Fase 31 in PLANNING.md, che ne spiega il perché).

---

## Account

Un **Account** rappresenta il luogo dove si trovano i soldi.

Esempi: Conto Corrente Intesa, Conto Fineco, Carta Visa, Carta Mastercard, PayPal, Revolut, Contanti, Buoni pasto.

Campi:

- nome
- tipo (conto corrente, conto di risparmio, carta prepagata, carta di credito, prestito o finanziamento, contanti, wallet digitale, altro) - a fini di icona/raggruppamento, con una descrizione d'uso mostrata nell'editor. Due tipi hanno comportamento proprio: la carta di credito (ciclo di addebito differito con estratto sul conto collegato) e il prestito (saldo negativo che si riduce a ogni rata). Le carte di debito non sono un tipo: spendono dal conto corrente e i loro movimenti si registrano lì
- valuta principale dell'account
- **saldo iniziale** (impostato alla creazione: il saldo corrente è sempre `saldo iniziale + Σ movimenti`)
- colore e icona
- **incluso nel saldo totale** (sì/no - es. un conto cointestato che si vuole tracciare ma non sommare)
- **archiviato** (sì/no)

### Saldo iniziale e rettifica saldo (fondamentale)

È la funzione che tiene in vita un expense tracker nel tempo. La realtà è che l'utente dimenticherà dei movimenti e il saldo dell'app divergerà da quello reale. Deve poter dire:

> "Il saldo reale di questo account oggi è 1.312,45 €"

e l'app crea automaticamente un movimento di tipo **Rettifica** con la differenza, senza inquinare le statistiche di spesa. Senza questa funzione, dopo 2 mesi l'app diventa inaffidabile e viene abbandonata.

### Account archiviati

Un account chiuso (es. vecchia carta prepagata) non si elimina: si **archivia**. I movimenti storici restano visibili nelle statistiche, ma l'account sparisce dai selettori e dal saldo totale.

### Prestiti e finanziamenti

Un prestito personale, un finanziamento o un mutuo si tracciano come **account a saldo negativo che si riduce**, non come una semplice categoria di spesa. È lo stesso modello della carta di credito con il verso opposto, e risponde alla domanda che la categoria non poteva soddisfare: quanto manca.

- il **saldo iniziale è il debito residuo di oggi**, quello dichiarato dalla banca (interessi del piano compresi), non il capitale erogato. L'app non calcola interessi e non costruisce piani di ammortamento
- la **rata è un trasferimento** (tipicamente ricorrente) dal conto di pagamento al conto prestito: non è consumo, quindi non compare nelle statistiche, e porta il residuo esattamente a zero all'ultima rata
- il **residuo** è il saldo calcolato dell'account, come per ogni altro conto. Le rate mancanti sono una stima derivata (residuo diviso importo della rata)
- di default il conto **resta fuori dal saldo totale e dal budget**: la Dashboard risponde a "quanto ho", e un mutuo a sei cifre coprirebbe quella risposta. Includerlo è una scelta esplicita dell'utente e trasforma il saldo totale nella lettura patrimoniale
- il riallineamento annuale con l'estratto della banca si fa con la **rettifica saldo**, che esiste già

Chi preferisce vedere la rata dentro le statistiche di spesa continua a non tracciare il prestito come account e usa la categoria "Prestiti & Finanziamenti". Le due modalità non si mescolano: insieme conterebbero due volte lo stesso denaro.

### Trasferimenti tra Account

Un trasferimento **non è** una spesa né un'entrata. Serve solo a spostare fondi:

```text
Conto Corrente → Carta Visa     200,00 €
Contanti → Conto Corrente        50,00 €
```

Regole:

- non ha categoria
- non compare **mai** nelle statistiche di spesa/entrata
- è un singolo record con `fromAccountId` e `toAccountId` (non due movimenti separati: evita disallineamenti)
- eventuale **commissione di trasferimento** (es. prelievo ATM con fee): l'utente registra la fee come spesa a parte. Modellarla dentro l'operazione è una funzione da valutare, non pianificata (Fase 18 in PLANNING.md)
- trasferimenti tra account in valute diverse: l'utente inserisce entrambi gli importi (quanto esce e quanto entra). Il tasso implicito che ne risulta è il dato reale dell'operazione e la conversione automatica (ADR 40) non lo tocca: i tassi BCE servono ai controvalori stimati degli aggregati, mai a riscrivere un movimento

Questo richiede supporto architetturale già in fase iniziale (è nel data model dal giorno 1).

---

## Categorie

Set predefinito alla prima apertura (personalizzabile e cancellabile):

**Spese:** Casa, Affitto/Mutuo, Spesa alimentare, Ristoranti & Bar, Trasporti, Auto & Carburante, Salute, Shopping, Viaggi, Intrattenimento, Abbonamenti, Bollette & Utenze, Istruzione, Regali fatti, Tasse, Prestiti & Finanziamenti, Altro

**Entrate:** Stipendio, Freelance, Regali ricevuti, Rimborsi, Altro

Ogni categoria ha:

- nome
- colore (da palette predefinita, per coerenza visiva nei grafici)
- icona (da set Material Symbols)
- tipo (spesa / entrata / entrambi)

Note di design:

- **niente sottocategorie nel MVP**: i tag coprono il 90% del bisogno con molta meno complessità UI. Rivalutare in v2.0 se emergono richieste reali.
- una categoria eliminata non elimina i movimenti: chiede a quale categoria riassegnarli (o "Altro")

---

# Dashboard "Oggi" (CORE FEATURE)

## Obiettivo

Fornire in **5 secondi** una visione completa della situazione finanziaria, senza tap.

Gerarchia visiva (dall'alto):

### 1. Saldo totale

Somma di tutti gli account inclusi nel totale, nella valuta principale:

```text
Saldo totale: 2.450,00 €
▸ Conto Intesa   1.800,00 €
▸ Carta Visa       420,00 €
▸ Contanti         230,00 €
```

(dettaglio account espandibile con un tap)

### 2. Oggi

```text
Spese oggi:    -18,90 €
Entrate oggi:   +0,00 €
Netto oggi:    -18,90 €
```

### 3. Mese corrente

```text
Spese mese:    -1.230,00 €
Entrate mese:  +2.000,00 €
Saldo mese:      +770,00 €
```

Con mini-indicatore di confronto rispetto allo stesso giorno del mese precedente ("a questo punto del mese scorso avevi speso 1.410 €").

### 4. Abbonamenti / ricorrenze del mese

```text
Abbonamenti attivi questo mese: 47,97 €
Prossimo: Netflix -12,99 € il 07/07
```

### 5. Budget (se attivo - v1.0)

```text
Budget mese: 320,00 € rimanenti su 1.500,00 €
█████████░░░░  79%
```

### 6. Ultimi movimenti (5–7)

```text
Supermercato Esselunga   -45,00 €   Spesa · Carta Visa
Stipendio             +2.000,00 €   Stipendio · Conto Intesa
Netflix                  -12,99 €   Abbonamenti · Carta Visa  ↻
Benzina                  -30,00 €   Auto · Contanti
```

Il simbolo ↻ indica un movimento generato da una ricorrenza.

### FAB / Quick Actions

Floating Action Button sempre visibile con tre azioni:

- ➕ Aggiungi spesa (azione primaria, un tap)
- ➕ Aggiungi entrata
- ⇄ Aggiungi trasferimento

**Requisito UX:** registrare una spesa "tipica" (importo + categoria, account di default) deve richiedere **massimo 2–3 tap più la digitazione dell'importo**. La schermata di inserimento apre direttamente il tastierino numerico.

---

# Ricorrenze (v1.0)

Le spese e le entrate possono essere ricorrenti. È la killer feature per gli abbonamenti.

### Esempi

```text
Netflix     -12,99 € / mese, il giorno 7
Spotify      -9,99 € / mese, il giorno 15
Affitto    -750,00 € / mese, il giorno 1
Stipendio +2.000,00 € / mese, il giorno 27
Assicurazione auto  -320,00 € / 6 mesi
```

### Regola di ricorrenza

- frequenza: giornaliera, settimanale, mensile, bimestrale, trimestrale, semestrale, annuale
- giorno di riferimento (con gestione dei mesi corti: "il 31" → ultimo giorno del mese)
- data di inizio e data di fine opzionale (o numero di ripetizioni)
- importo **fisso** oppure **variabile** (es. bolletta luce: l'app crea il movimento in stato "da confermare" e chiede l'importo)
- modalità di registrazione, configurabile per regola:
  - **automatica** (default): il movimento viene creato alla data prevista, con notifica informativa
  - **con conferma**: notifica "È arrivato l'addebito Netflix?" → conferma/modifica/salta

### Comportamento tecnico

- generazione affidata a **WorkManager** (job periodico + catch-up all'apertura dell'app, per coprire i casi in cui il device era spento)
- modificare una regola non altera i movimenti già generati
- eliminare una regola chiede: "eliminare anche i movimenti futuri già generati?"

### Vista abbonamenti

Schermata dedicata che risponde a:

> "Questo mese hai 47,97 € di abbonamenti attivi"

con lista, costo mensile equivalente (un abbonamento annuale da 120 € è mostrato anche come "10 €/mese"), e totale annuo proiettato. È il tipo di insight che fa dire "non sapevo di spendere così tanto".

---

# Ricerca e filtri

Ricerca full-text sulla descrizione + filtri combinabili:

- intervallo di date (con preset: oggi, settimana, mese, anno, personalizzato)
- categoria (multipla)
- account (multiplo)
- tipo (spesa / entrata / trasferimento)
- intervallo di importo
- tag
- solo ricorrenti / solo manuali

I filtri attivi sono visibili come chip rimovibili. Il risultato mostra sempre il **totale filtrato** ("47 movimenti, -1.238,50 €").

---

# Statistiche

Grafici realizzati con **Vico** (Compose-native):

- **spese per categoria**: grafico a torta/anello + lista ordinata con percentuali, per mese/anno/periodo custom
- **trend mensile**: barre delle spese degli ultimi 12 mesi
- **entrate vs uscite**: barre affiancate o cascata mensile
- **andamento saldo**: linea del saldo totale nel tempo (ricostruito dai movimenti)
- **spese per account**
- tap su una fetta/barra → drill-down alla lista dei movimenti corrispondenti (riusa il sistema di filtri)

Le statistiche **escludono sempre** trasferimenti e rettifiche, e trattano i rimborsi come riduzione di spesa (vedi sezione Rimborsi).

---

# Budget (v1.0)

Limiti di spesa mensili, globali o per categoria:

```text
Categoria: Ristoranti    Budget: 200 €   Speso: 180 €   ████████░░ 90%  🟡
Categoria: Spesa         Budget: 400 €   Speso: 420 €   ██████████ 105% 🔴
```

Indicatori: 🟢 sotto budget (< 80%) · 🟡 vicino al limite (80–100%) · 🔴 superato.

Comportamento:

- il periodo di budget è il mese di calendario (configurabile in futuro: es. "dal 27 al 27" per chi ragiona per stipendio)
- notifica opzionale all'80% e al superamento
- card riassuntiva in dashboard

---

# Obiettivi di risparmio (v1.0, anticipati dalla v2.0)

L'utente crea obiettivi finanziari alimentati manualmente o collegati a un account dedicato:

```text
🏖 Vacanze     Target: 2.000 €   Risparmiato: 1.450 €   ███████░░░ 72%
💻 Nuovo PC    Target: 1.200 €   Risparmiato:   300 €   ██░░░░░░░░ 25%
```

Con data obiettivo opzionale e suggerimento del risparmio mensile necessario ("ti servono 110 €/mese per arrivare a giugno").

---

# Crediti e debiti verso persone

Il denaro prestato a un amico, la cena anticipata per il gruppo, i soldi ricevuti in prestito da un parente. Non è una funzione di spese condivise multi-utente (resta fuori): è la risposta a "quanto mi devono, e chi".

```text
Marco      ti devono   65,00 €   (3 movimenti)
Giulia     ti devono   20,00 €   (1 movimento)
Papà       devi       200,00 €   (1 movimento)
```

Modellazione, senza inventare un registro parallelo:

- il movimento resta quello che è, una spesa o un'entrata sul conto da cui il denaro esce o entra davvero: il saldo del conto lo registra come qualunque altro movimento
- prestare denaro non è spesa e riaverlo non è entrata, quindi il movimento marcato come prestito o restituzione è **sempre escluso dalle statistiche**
- il legame è il nome della **controparte**; il saldo per persona è la somma dei suoi movimenti, quindi i rientri parziali funzionano da soli
- i crediti **non** si sommano al saldo totale: quel denaro ha già lasciato il conto, contarlo altrove lo conterebbe due volte

Un prestito ricevuto da una persona si traccia qui e non come account prestito: non ha un piano, spesso non ha nemmeno una rata.

---

# Allegati

> Funzione **da valutare**, non pianificata: Fase 30 in PLANNING.md, che ne spiega il perché.

Un movimento può portare con sé una o più foto: lo scontrino, la ricevuta, il cartellino della garanzia.

- acquisizione dalla galleria o dalla fotocamera, **senza alcun permesso** (photo picker di sistema e intent della fotocamera)
- le immagini vivono nello spazio privato dell'app, non nella galleria del telefono, e non lasciano il dispositivo se non tramite una condivisione esplicita
- ridimensionamento e ricompressione automatici alla scrittura: uno scontrino non ha bisogno della risoluzione piena della fotocamera
- **niente OCR e niente lettura automatica dell'importo**: sarebbe un'altra app. L'allegato è una prova da ritrovare, non una fonte di dati
- il backup completo comprende gli allegati (vedi Backup): un backup che perde le foto non sarebbe un backup

---

# Multi-valuta

Supporto completo dal MVP a livello di **dato**: ogni movimento conserva importo e valuta originali, ogni account ha la sua valuta.

- valuta principale dell'app scelta nell'onboarding (default dalla locale)
- nel MVP: gli account in valuta diversa mostrano il saldo nella loro valuta; il saldo totale somma solo gli account nella valuta principale (gli altri sono elencati a parte)
- **Conversione automatica (consegnata, ADR 40)**: tassi di riferimento BCE con cache offline, controvalore stimato nella valuta principale in ogni aggregato, sempre indicato con "≈" e con la data del tasso:

```text
125,00 USD ≈ 115,30 € (tasso del 02/07, stimato)
```

- i flussi (spese, statistiche, budget) si convertono al tasso del giorno del movimento, così un mese concluso resta stabile; i saldi al tasso più recente, perché uno stock vale quanto vale oggi; nessun controvalore viene mai salvato
- attiva di default e disattivabile dalle Impostazioni: senza dati in valuta estera non parte alcuna richiesta di rete

---

# Internazionalizzazione

- Italiano + Inglese dal v1.0
- tutte le stringhe in `strings.xml` dal primo giorno (nessuna stringa hardcoded)
- formattazione di numeri, valute e date tramite le API di localizzazione (`NumberFormat`, `java.time`), mai formattazione manuale
- struttura pronta per nuove lingue

---

# Backup e sincronizzazione

Il backup della v1.0 è quello **manuale su file**, descritto più sotto: non richiede account e non richiede rete. Il backup su Google Drive resta la strategia automatica desiderata, ma è stato rimandato a una fase da valutare a fine roadmap (ADR 17 in PLANNING.md), fuori dal percorso di rilascio della v1.0.

## Backup automatico su Google Drive (App Data Folder) - rimandato

Backup automatico su **Google Drive → App Data Folder** (spazio nascosto e privato dell'app):

```text
Drive
└── App Data
    ├── saldo-backup-2026-07-03.json   (ultimo)
    └── saldo-backup-2026-06-26.json   (precedente, rotazione)
```

Caratteristiche:

- invisibile all'utente nel suo Drive, privato dell'app
- backup automatico periodico via WorkManager (solo Wi-Fi, configurabile)
- restore guidato al primo avvio su nuovo device
- rotazione: mantieni gli ultimi N backup (default 5)
- nessun backend proprietario

Note tecniche importanti:

- la vecchia Drive Android API è deprecata: si usa la **Google Drive REST API** con **Credential Manager / Google Sign-In** e scope `drive.appdata`
- richiede un account Google, **ma resta 100% opzionale**: l'app è pienamente funzionante senza (coerente con "nessun account obbligatorio")
- formato: **export JSON versionato** (decisione già presa, ADR 5 in PLANNING.md) - più robusto di uno snapshot del file `.db` tra versioni diverse dello schema Room; il restore è un import
- modello **single-device con restore**, non sync multi-device in tempo reale (fuori scope: eviterebbe di dover risolvere conflitti)
- **cifratura del backup** con passphrase utente: consegnata con la v2.0, e vale per il file esportato di qualunque destinazione (il contenitore cifrato avvolge lo stesso JSON, ADR 44)

## Backup manuale su file (v1.0)

L'utente può esportare in qualsiasi momento un **backup completo su file**, senza alcun account. È il backup della v1.0, e resta valido anche se un domani arriverà quello automatico su Drive:

- **stesso formato JSON versionato** del backup Drive: un solo code path, e il restore funziona indistintamente da entrambe le fonti
- salvataggio tramite **Storage Access Framework** (`ACTION_CREATE_DOCUMENT`): l'utente sceglie la destinazione dal picker di sistema (memoria locale, Drive, qualunque provider di documenti) e l'app **non richiede permessi di storage**
- nome file con data: `saldo-backup-2026-07-05.json`
- quando esistono **allegati** il backup diventa un archivio `saldo-backup-2026-07-05.zip` con dentro lo stesso JSON più i file: il formato JSON e il suo numero di versione non cambiano, e il ripristino accetta indistintamente l'archivio o il JSON nudo. Chi non usa allegati continua a esportare un file di testo leggibile
- il file contiene anche le **impostazioni** scelte dall'utente (tema, valuta principale, conto predefinito, promemoria, card della Dashboard, separatore CSV): un ripristino su un dispositivo nuovo non chiede di riconfigurare l'app. Restano fuori il PIN del blocco app e la cache dei tassi, per ragioni scritte nell'ADR 45
- **cifratura opzionale con passphrase** (v2.0): un interruttore accanto all'export protegge il file con AES-256 e chiave derivata sul dispositivo. Con la cifratura spenta il file resta in chiaro e la UI lo dichiara, come prima; con la cifratura attiva la stessa riga spiega che la passphrase non è recuperabile. Il ripristino riconosce il contenitore dal contenuto e lo decifra prima di mostrare l'anteprima, mai dopo aver sostituito i dati; i backup non cifrati restano importabili per sempre

Questa opzione rafforza i principi del progetto: backup completo possibile **senza account Google** e piena portabilità dei dati - il file può essere copiato a mano su Google Drive, NAS, Syncthing o qualsiasi altro servizio.

---

# Export

Il formato dell'app è il **CSV** (separatore configurabile `,`/`;` - in Italia Excel si aspetta `;`), con export completo o filtrato. Si apre già in Excel e in Google Sheets, ed è la ragione per cui gli altri tre formati sono **da valutare** e non pianificati (Fase 21 in PLANNING.md):

- **Excel (.xlsx)** - da valutare: richiederebbe una libreria nuova per un file che il CSV già copre
- **PDF report** mensile/annuale con grafici - da valutare: è l'unico dei tre con un uso proprio, il resoconto da mandare a qualcuno, ed è anche il più economico (`PdfDocument` di piattaforma)
- **Google Sheets** - da valutare: lo scope OAuth `spreadsheets` è classificato "sensitive" da Google e richiede la verifica dell'app, molta frizione per un foglio che si ottiene già importando il CSV. Creerebbe un nuovo foglio o aggiornerebbe lo stesso (es. un foglio "Spese 2026" con un tab per mese):

| Data | Tipo | Categoria | Account | Descrizione | Importo | Valuta | Tag |
|------|------|-----------|---------|-------------|---------|--------|-----|
| 05/01/2026 | Spesa | Spesa alimentare | Carta Visa | Supermercato | -45,00 | EUR | |

L'export rispetta i filtri attivi ("esporta questa vista").

# Import

- restore da file di backup manuale (`.json`) con anteprima del contenuto e conferma esplicita
- restore da backup Google Drive (insieme al backup automatico, rimandato)
- **import CSV** (v1.0) con riconoscimento automatico di separatore, decimali e colonne, anteprima e rilevazione dei duplicati (fondamentale per chi migra da altre app: Money Manager, Wallet, fogli Excel personali)

---

# Sicurezza e privacy

- dati solo in locale; nessuna telemetria di terze parti nel MVP (eventuali crash report solo opt-in)
- la lettura dei cambi BCE per la conversione multi-valuta (ADR 40) è l'unico traffico di rete dell'app oltre a backup ed export opzionali: traffico in entrata, nessun account, nessun dato dell'utente in uscita (esce solo la richiesta dei tassi, cioè un IP e il fatto che qualcuno ha chiesto i cambi). Con la conversione disattivata, o senza dati in valuta estera, l'app non fa alcuna richiesta di rete
- **PIN lock** (consegnato, PIN a 6 cifre opzionale)
- **sblocco biometrico** via `BiometricPrompt` (consegnato)
- oscuramento del contenuto nelle app recenti (`FLAG_SECURE`, opzionale) - consegnato; la stessa protezione blocca anche gli screenshot
- **cifratura backup** con passphrase (consegnata, opzionale): AES-256-GCM con chiave derivata dalla passphrase sul dispositivo, nessun recupero possibile e dichiarato prima di attivarla
- il blocco è un gate di accesso all'app, non cifratura del database: il PIN non è mai salvato in chiaro (hash con salt) e non entra nei backup
- permessi Android richiesti: praticamente nessuno (niente contatti, niente posizione, niente SMS). Anche gli allegati fotografici restano a zero permessi: il photo picker di sistema non ne richiede e la fotocamera si usa via intent, senza dichiarare `CAMERA` nel manifest

---

# Accessibilità

- Material You / dynamic color con tema chiaro/scuro/sistema
- font scaling rispettato (layout testati fino a 200%)
- TalkBack: contentDescription su ogni elemento interattivo, semantica Compose curata
- riduzione animazioni se impostata a sistema
- alto contrasto e non affidarsi solo al colore (es. spese/entrate distinte anche da segno e icona, non solo rosso/verde - importante per daltonici)
- touch target ≥ 48dp

---

# UI / UX - Principi

- **zero frizione**: inserire una spesa è l'azione più frequente, va ottimizzata sopra ogni cosa
- massimo 2–3 tap per registrare un movimento
- dashboard immediata, leggibile in 5 secondi
- valori monetari sempre formattati secondo locale, sempre con segno esplicito
- gesti rapidi sulla lista movimenti: swipe per eliminare (con undo via Snackbar), tap per modificare
- undo ovunque sia possibile invece di dialog di conferma
- empty state curati (prima apertura, nessun movimento, ecc.) con call-to-action

# Widget

- widget "aggiunta rapida" in due forme, griglia e barra (implementati nella v1.0)
- i widget sono punti di ingresso statici, non superfici di visualizzazione: niente saldi, totali o contenuti derivati dai movimenti (decisione di luglio 2026, Fase 10.21). I widget di sola lettura (saldo totale, spese del giorno) sono fuori scope per questo motivo: costerebbero un refresh a ogni movimento

---

# Architettura

```text
UI (Compose + Navigation 3)
        ↓
   ViewModel (StateFlow → UI State immutabile)
        ↓
   Use Cases (logica di dominio pura, testabile)
        ↓
   Repository (interfacce nel dominio, implementazioni nel data layer)
        ↓
 Room DB  ·  DataStore  ·  Backup/Export layer (Drive, CSV, Sheets)
```

Linee guida:

- moduli o package-by-feature: `core/` (database, design system, common) + `feature/` (dashboard, transactions, accounts, categories, recurring, stats, settings, backup)
- il dominio non dipende da Android (Use Cases testabili con unit test puri)
- single source of truth: la UI osserva solo Flow dal database
- gli importi viaggiano come `BigDecimal` nel dominio, `Long` (centesimi) nel DB, `String` formattata solo nella UI

# Librerie

- Jetpack Compose (BOM) + Material 3
- Navigation 3 (`androidx.navigation3` - runtime + ui)
- Room (+ KSP)
- DataStore Preferences
- Hilt
- Kotlin Coroutines / Flow
- WorkManager
- **Vico** (grafici Compose-native)
- kotlinx-serialization (export/backup JSON)
- Google Drive REST API + Credential Manager (solo feature backup)
- Test: JUnit5 per gli unit test JVM, JUnit4 per test strumentati e Compose UI Test (requisito delle rule Compose), Turbine (Flow), MockK, Room in-memory

Nota sulle immagini: le icone dell'app sono e restano risorse vettoriali locali, renderizzate nativamente da Compose, mai caricate da una libreria. Gli allegati fotografici ai movimenti si acquisiscono con il photo picker e la fotocamera di sistema e si decodificano con le API della piattaforma (`ImageDecoder`, decodifica con sample size per le miniature): per qualche foto locale per movimento non serve una libreria di image loading, e la scelta resta confinata in un componente condiviso, così un domani è rivedibile in un punto solo. Come ogni altra dipendenza, l'eventuale aggiunta passa da una decisione esplicita, non da una comodità.

---

# Roadmap (sintesi - dettaglio in PLANNING.md)

> La v1.0.0 esce a luglio 2026 come release su GitHub con l'APK allegato, non sul Play Store (ADR 38 in PLANNING.md). Il rilascio intermedio che era previsto fra l'MVP e la v2.0 è stato riassorbito: parte di quello che conteneva è uscito con la v1.0 (budget, widget, import CSV), il resto è confluito nella v2.0.

## v1.0 (rilasciata)

Perimetro dell'MVP:

- Movimenti: spese, entrate, trasferimenti, rettifiche saldo
- Account con tipi espliciti (conto corrente, risparmio, prepagata, carta di credito, prestito, contanti, wallet digitale), saldo iniziale, archiviazione
- Dashboard "Oggi"
- Categorie personalizzabili
- **Ricorrenze e abbonamenti** (inclusa vista abbonamenti)
- Ricerca e filtri
- Statistiche base (categoria, trend mensile, entrate vs uscite)
- Multi-valuta a livello dato
- Backup manuale su file ed export CSV
- IT + EN

Anticipato dalle roadmap successive prima del rilascio:

- Budget per categoria e globale, con "spendibile oggi"
- Widget home screen (griglia e barra)
- Import CSV con riconoscimento automatico del formato
- Obiettivi di risparmio
- Prestiti e finanziamenti come tipo di account (residuo, rate mancanti)
- Recap mensile condivisibile e proiezione del saldo a fine mese

Rimasto fuori rispetto al piano iniziale dell'MVP: il backup su Google Drive, spostato a una fase da valutare a fine roadmap (ADR 17). Il backup della v1.0 è quello manuale su file.

## v2.0

- Crediti e debiti verso persone
- Movimenti futuri e scadenze una tantum (elenco "in arrivo", promemoria, stima a fine mese che li conta)
- PIN + biometria + FLAG_SECURE (consegnato in anticipo, dopo la 1.0.0)
- Conversione valuta automatica
- Gestione tag dedicata e ricerca con suggerimenti
- Rilevamento automatico delle ricorrenze
- Aggiunta rapida dalla tendina delle impostazioni rapide, e inserimento rapido testuale ("12,50 pizza")
- Cifratura backup (opzionale, con passphrase) e backup che include anche le impostazioni
- Miglioramenti UX dal feedback della v1.0
- Da valutare, fuori dal piano: allegati fotografici ai movimenti, rimborsi collegati alla spesa originale, commissioni sui trasferimenti, analisi avanzate, export PDF/Excel/Google Sheets, pagamento parziale dell'estratto carta, arrotondamento degli spiccioli, riepilogo settimanale, backup automatico su Google Drive

---

# Note finali

Questa app è progettata per essere:

- semplice ma potente
- focalizzata sulla spesa, non sulla finanza complessa
- immediata nell'uso quotidiano
- affidabile nel tempo (rettifica saldo, ricorrenze robuste)
- estendibile senza rifattorizzazioni radicali
- completamente offline-first con sync opzionale

Il focus rimane sempre:

> **capire dove vanno i soldi, in modo chiaro e immediato.**
