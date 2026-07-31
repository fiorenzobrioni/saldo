# Checklist QA manuale - release v2.0

Checklist di verifica su device per la Fase 23 (release v2.0) in [PLANNING.md](../PLANNING.md). Estende quella usata per la v1.0, che viveva come voce di fase, con le funzionalità arrivate dopo: crediti e debiti, movimenti futuri, blocco app, tag, multi-valuta, ricorrenze suggerite, tile, inserimento testuale, backup cifrato e impostazioni nel backup.

Serve a coprire ciò che la CI non può verificare (ADR 26): la CI esegue build, unit test, lint e detekt, i test strumentati girano sul workflow dedicato, ma nessuno dei due tocca un device reale. Le voci sono scritte come esito atteso, non come azione: se l'esito è diverso, il giro si ferma e il difetto va in "Bug conosciuti" di PLANNING.

## Preparazione

- [ ] Device principale con Android recente e un secondo device o emulatore su **API 33**, più un **tablet o schermo grande** (anche emulatore)
- [ ] Un'installazione con **dati reali** su cui provare l'aggiornamento in place, e un device o profilo **pulito** per l'installazione da zero
- [ ] Backup esportato **prima** di iniziare, e un backup **in chiaro esportato da una 1.0.x** messo da parte: serve alla prova di compatibilità
- [ ] Versione in prova: `versionName` 2.0.0, `versionCode` allineato, APK scaricato dalla release o dall'artifact della CI

## A. Installazione e aggiornamento

- [ ] L'APK si installa **sopra la 1.0** senza disinstallare e senza perdere dati: conti, movimenti, budget, obiettivi e impostazioni sono quelli di prima
- [ ] Le migrazioni del database si applicano al primo avvio senza crash e senza perdite (schema 1 -> 4 se si parte dalla 1.0.0)
- [ ] Installazione **pulita** su un device senza dati: nessun crash all'avvio, si arriva all'onboarding
- [ ] Dopo il giro, l'APK della release si reinstalla sopra la build di CI senza perdere i dati (stesso keystore)

## B. Onboarding

- [ ] Primo avvio: benvenuto con l'animazione dell'icona, privacy, valuta, primo conto, notifiche. Le pagine avanzano solo dai CTA, il gesto indietro torna di una pagina
- [ ] La pagina privacy dichiara la lettura dei tassi di cambio e il backup protetto da passphrase
- [ ] La pagina notifiche elenca rinnovi, occorrenze da confermare, scadenze e budget
- [ ] Il permesso notifiche: sia concesso sia rifiutato portano a fine onboarding, e l'app funziona in entrambi i casi
- [ ] "Lo faccio dopo" sul primo conto: si arriva alla Dashboard con lo stato vuoto che invita a crearne uno
- [ ] Ripristino da backup dall'onboarding, con file **in chiaro** e con file **cifrato** (passphrase chiesta prima dell'anteprima)
- [ ] Giro completo dell'onboarding con il telefono in **tema scuro**: nessuna schermata chiara, nessun lampo bianco al primo frame
- [ ] Giro con **locale inglese**: tutti i testi tradotti, valuta e date coerenti

## C. Movimenti, il giro quotidiano

- [ ] Spesa registrata in **2-3 tap** dal FAB: importo col tastierino, categoria e conto sulla prima schermata, salvataggio
- [ ] Entrata e **trasferimento** fra due conti: il trasferimento non chiede categoria e non compare nelle statistiche
- [ ] **Rettifica saldo** da un conto: allinea il saldo e resta fuori dalle statistiche
- [ ] Modifica e eliminazione di un movimento: il saldo si aggiorna, l'undo dell'eliminazione dall'editor funziona
- [ ] Uscendo da un editor con dati non salvati compare la richiesta di conferma
- [ ] Movimento con **nota**, con **tag** (anche nuovi, creati inline) e con **controparte**
- [ ] Tastierino: separatore decimale della lingua, migliaia raggruppate, importo negativo dove ammesso, chiusura che lascia visibili le categorie
- [ ] Movimento datato nel **futuro**: non incide su saldo "ad oggi", statistiche e card Oggi/Mese, ma compare in "In arrivo"

## D. Conti

- [ ] Creazione di ogni tipo di conto, ognuno con la sua descrizione d'uso nell'editor
- [ ] Interruttori indipendenti "includi nel saldo totale" e "includi nel budget", con l'effetto atteso su Dashboard e budget
- [ ] Archiviazione: il conto sparisce dai selettori e dai totali, i suoi movimenti restano
- [ ] Riordino manuale nella lista Conti e sottototali per tipo
- [ ] **Carta di credito**: ciclo con giorno di chiusura e addebito, estratto pagato con un tap come trasferimento dal conto collegato, saldo che torna a zero, barra del fido se impostato
- [ ] **Prestito**: residuo che scende con la rata (trasferimento ricorrente), rate mancanti come stima, di default fuori da saldo totale e budget
- [ ] **Conto di risparmio**: di default fuori dal budget, dentro il patrimonio
- [ ] Saldo "ad oggi" mostrato sul conto quando differisce dal saldo pieno

## E. Ricorrenze

- [ ] Regola **automatica**: al passaggio della data il movimento compare da solo, con notifica informativa e indicatore di origine sulla riga
- [ ] Regola **con conferma**: l'occorrenza resta da confermare, la conferma la registra con l'importo reale
- [ ] Regola di **trasferimento**, anche fra valute diverse (forzata a conferma)
- [ ] **Catch-up**: con il device spento o l'app chiusa per giorni, alla riapertura le occorrenze arretrate si generano una sola volta
- [ ] Mese corto: una regola al giorno 31 cade il 28 o 29 febbraio e torna al 31 dopo
- [ ] **Promemoria pre-rinnovo** con l'anticipo configurato, e nessuna doppia notifica per la stessa occorrenza
- [ ] Hub: totale mensile, proiezione annua, risparmio pianificato, eliminazione di una regola con dialog
- [ ] **Ricorrenze suggerite**: la riga "Cerca ricorrenze non registrate" trova le spese ripetute, la CTA apre l'editor precompilato, "scarta" non ripropone il suggerimento, e senza toccare la riga non parte nessuna ricerca

## F. In arrivo

- [ ] Elenco con movimenti futuri e occorrenze da confermare, raggruppati per giorno, con i due totali separati e l'origine visibile
- [ ] Filtro "Da confermare" presente solo quando c'è qualcosa da confermare
- [ ] Promemoria per un movimento futuro: arriva con l'anticipo previsto; spostare la data in avanti lo riarma
- [ ] Con i promemoria disattivati in Impostazioni, l'editor dichiara che la notifica non arriverà
- [ ] La **proiezione a fine mese** in Dashboard e lo **spendibile oggi** contano gli stessi impegni

## G. Budget e spendibile

- [ ] Budget complessivo e per categoria, con la spesa che combacia con le Statistiche dello stesso periodo
- [ ] Avvisi all'**80%** e al **100%**: una notifica per soglia, per mese, per budget
- [ ] Spendibile oggi coerente col budget residuo e con gli addebiti in arrivo
- [ ] Un conto escluso dal budget non contribuisce al consumato

## H. Obiettivi di risparmio

- [ ] Obiettivo su un conto di risparmio: progresso sul saldo reale, suggerimento mensile, stima della data di arrivo
- [ ] Un trasferimento verso quel conto muove il progresso

## I. Crediti e debiti

- [ ] Spesa con controparte = credito, entrata con controparte = debito o restituzione; i due totali sono separati, mai un netto unico
- [ ] Il movimento con controparte resta fuori da statistiche, budget, spendibile e saldo totale, ma incide sul saldo del conto
- [ ] "Segna come rientrato" apre l'editor precompilato col verso opposto e l'importo residuo
- [ ] Rientro parziale: il saldo per persona scende della cifra giusta
- [ ] Autocompletamento del nome della controparte dai valori già usati
- [ ] Card Dashboard assente quando non c'è nulla di aperto

## J. Multi-valuta

- [ ] Con un conto in valuta estera e la conversione attiva: saldo totale, card Oggi e Mese, statistiche, budget, spendibile, obiettivi e "In arrivo" mostrano il controvalore con **"≈"**
- [ ] La riga sotto il saldo dichiara la **data dei tassi** e non tronca il testo
- [ ] Una spesa vecchia mantiene il controvalore **del suo giorno** anche a tassi cambiati (retrodatare un movimento nell'editor sposta la stima)
- [ ] Schermata **Tassi di cambio**: valute scaricate, variazione sull'ultima pubblicazione, mini-grafico, sezioni "Le tue valute" e "Altre valute BCE"
- [ ] **Convertitore rapido**: importo col tastierino, valuta sorgente, direzione invertibile
- [ ] Dettaglio di una valuta: grafico a 1 e 3 mesi, minimo, massimo, variazione
- [ ] Controvalore nell'editor di un movimento su conto in valuta estera, e sulle righe del registro; mai sui trasferimenti
- [ ] **Offline** (modalita aereo): vale l'ultimo tasso noto con la sua data, nessun blocco e nessun errore
- [ ] **Conversione disattivata** dalle Impostazioni: i totali tornano mono-valuta e non parte alcuna richiesta di rete
- [ ] Con una sola valuta e la conversione attiva: nessuna richiesta di rete (verificabile in modalita aereo, tutto identico)
- [ ] Una valuta fuori dal paniere BCE resta esclusa dai totali, con la riga informativa

## K. Statistiche, filtri e recap

- [ ] Statistiche: anello per categoria con tap sulla fetta, trend mensile, entrate vs uscite, andamento saldo; le cifre combaciano con i drill-down
- [ ] Filtri combinabili (data, tipo, categoria, conto, tag, importo, origine, senza categoria) con il totale della vista filtrata
- [ ] Ricerca nel registro, anche con accenti e maiuscole diverse
- [ ] **Recap mensile**: pagine a storia su un mese concluso, immagine condivisa che segue il tema corrente, raggiungibile anche dalle Statistiche
- [ ] Export CSV dei movimenti filtrati, con il separatore scelto nelle Impostazioni

## L. Tag

- [ ] Schermata Tag: conteggio per tag, ordinamento per uso o alfabetico, ricerca quando i tag sono molti
- [ ] **Rinomina**, anche verso un nome esistente: propone l'unione invece di creare un doppione
- [ ] **Unione** di due o più tag: i movimenti che li avevano entrambi non risultano duplicati
- [ ] **Eliminazione**: i movimenti restano, il chip del tag sparisce dai filtri attivi del registro

## M. Widget, tile e inserimento rapido

- [ ] Widget in entrambe le forme e nelle tre dimensioni, configurazione del conto per istanza, resa in tema chiaro e scuro
- [ ] Anteprime nel picker dei widget corrette in entrambi i temi
- [ ] Registrare un movimento **non** ridisegna il widget; cambiare un conto o una categoria si
- [ ] **Tile** delle Impostazioni rapide: aggiunta dalla tendina, tap a schermo sbloccato e bloccato, tendina che si chiude, salvataggio
- [ ] Sheet di setup (nessun conto o nessuna categoria) al posto di un form che non potrebbe salvare
- [ ] **Inserimento testuale**: "12,50 pizza ieri" compila importo, descrizione e data; un importo ambiguo lascia il tastierino aperto e non inventa nulla; una categoria incerta non viene preselezionata; il tastierino vince sul parser
- [ ] IME e tastierino si scambiano il focus senza sovrapporsi

## N. Blocco app

- [ ] Attivazione del PIN con doppia digitazione, sblocco all'avvio, disattivazione che richiede il PIN corrente
- [ ] Sblocco **biometrico**: prompt automatico, annullamento che lascia il tastierino, tasto che lo ripropone
- [ ] **Blocco automatico** nelle tre impostazioni: rotazione e telefonata non contano come uscita, chiudere dalle recenti riblocca
- [ ] Cooldown dopo 5 tentativi errati, che sopravvive alla chiusura dell'app
- [ ] La sheet del widget e della tile e protetta dallo stesso blocco
- [ ] **Nascondi nelle app recenti**: miniatura oscurata e screenshot bloccati
- [ ] "Cancella tutti i dati" rimuove anche il PIN

## O. Dati: backup, ripristino, import, cancellazione

- [ ] Export **non cifrato**, ripristino su dati veri: saldi, movimenti e conteggi identici a prima
- [ ] Export **cifrato**: passphrase chiesta prima del selettore di destinazione, doppia digitazione, minimo 8 caratteri
- [ ] Ripristino del file cifrato: passphrase sbagliata segnalata **dentro** il dialogo senza perdere il testo; con quella giusta compare l'anteprima con "Impostazioni incluse" e "File cifrato"
- [ ] Un file estraneo e un file danneggiato danno errori distinti e non toccano i dati
- [ ] **Impostazioni ripristinate**: cambia tema, valuta principale, conto predefinito e card della Dashboard, esporta, cancella tutti i dati, ripristina; l'app torna configurata come prima
- [ ] **Compatibilità all'indietro**: ripristino di un backup in chiaro esportato da una **1.0.x** su questa installazione, senza errori e senza toccare le impostazioni locali
- [ ] Import CSV: file di formato diverso, riconoscimento di separatore, decimali e colonne, duplicati rilevati, report finale, nessuna modifica ai movimenti esistenti
- [ ] "Cancella tutti i dati": l'app torna all'onboarding con le categorie predefinite, la conferma ricorda la data dell'ultimo backup

## P. Resa, accessibilità, dispositivi

- [ ] Tema **chiaro**, **scuro** e **di sistema**, più **Material You** attivo: nessun testo illeggibile, nessuna card che si perde nel fondo
- [ ] Font di sistema al **200%**: nessun testo tagliato nelle schermate principali e negli editor
- [ ] **TalkBack** sui flussi principali: importi annunciati, spese ed entrate distinte anche senza colore, indicatore di pagina dell'onboarding annunciato
- [ ] **Tablet o schermo grande**: nessun layout rotto, larghezze ragionevoli
- [ ] **API 33** e ultimo Android stabile: nessuna differenza di comportamento, nessun crash
- [ ] Rotazione dello schermo negli editor e nei flussi con tastierino: niente dati persi
- [ ] Giro rapido con locale **inglese** sulle schermate nuove della 2.0

## Q. Verifiche non manuali da allegare al giro

- [ ] Workflow "Instrumented tests" verde: `MigrationsTest` (catena completa fino alla versione corrente), `TagDaoTest`, `TransactionDaoUpcomingTest`, `TransactionDaoRecurrenceCandidatesTest`
- [ ] CI verde sul commit di rilascio (build, unit test, lint, detekt)
- [ ] Baseline profile generato e misurato, se entra nella release

## Chiusura del giro

- [ ] Ogni difetto trovato registrato in "Bug conosciuti" di PLANNING, con la sua gravità
- [ ] Voce nel devlog con la data del giro, i device usati e cosa e stato verificato
- [ ] `versionCode` incrementato e `versionName` a 2.0.0
- [ ] Note di rilascio in [docs/release-notes/v2.0.0.md](./release-notes/v2.0.0.md) rilette e completate
