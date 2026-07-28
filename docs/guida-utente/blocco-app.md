[Torna all'indice](README.md)

# 🔒 Blocco app e privacy dello schermo

Saldo può chiedere un PIN a ogni apertura e, se il telefono lo supporta, sbloccarsi con l'impronta o il volto. È anche possibile nascondere il contenuto dell'app nella schermata delle app recenti. Tutto è disattivato di default e si configura da Impostazioni > Sicurezza.

## Attivare il blocco app

1. Apri Impostazioni > Sicurezza.
2. Attiva "Blocco app".
3. Scegli un PIN di 6 cifre e confermalo digitandolo una seconda volta.

Da quel momento Saldo chiede il PIN a ogni apertura. La verifica si conclude da sola all'ultima cifra, senza tasto di conferma.

Per disattivare il blocco serve il PIN corrente: è la conferma che a spegnerlo sia chi lo ha impostato.

## Sblocco biometrico

Se il dispositivo ha un'impronta o un volto registrati, sotto "Blocco app" compare l'interruttore "Sblocco biometrico". All'attivazione il sistema chiede subito una verifica biometrica: l'opzione si accende solo se va a buon fine.

Con lo sblocco biometrico attivo, la schermata di blocco apre da sola la richiesta biometrica; se la annulli resta il tastierino del PIN, e il tasto con l'impronta la ripropone.

## Blocco automatico

L'opzione "Blocco automatico" decide per quanto tempo Saldo può restare in background prima di richiedere lo sblocco:

- "Subito": ogni uscita dall'app riattiva il blocco. È l'impostazione più protettiva, ma richiede lo sblocco anche al ritorno da una condivisione o dalla scelta di un file.
- "1 min" / "5 min": una breve assenza non richiede un nuovo sblocco.

Ruotare lo schermo o ricevere una telefonata non conta come uscita: il blocco scatta solo quando lasci davvero l'app. Chiudere l'app dalle recenti la fa ripartire bloccata in ogni caso.

## Tentativi sbagliati

Dopo 5 PIN errati consecutivi il tastierino si ferma per 30 secondi, e ogni errore successivo raddoppia l'attesa fino a un massimo di 5 minuti. Il conteggio non si azzera chiudendo l'app. Lo sblocco biometrico resta disponibile anche durante l'attesa.

## Se dimentichi il PIN

Saldo funziona interamente offline e non ha un meccanismo di recupero del PIN: nessuna email, nessuna domanda di sicurezza.

- Se lo sblocco biometrico è attivo, entra con l'impronta o il volto e cambia il PIN da Impostazioni > Sicurezza > "Cambia PIN".
- Altrimenti l'unica strada è cancellare i dati dell'app dalle impostazioni di sistema di Android, che elimina anche tutti i movimenti e i conti. Un backup periodico (Impostazioni > Backup) è la rete di sicurezza per questo scenario.

## Il blocco vale anche per il widget

Con il blocco attivo, anche l'inserimento rapido dal widget chiede il PIN o la biometria. Lo sblocco vale per l'intera sessione: se l'app è stata sbloccata da poco (entro il tempo del blocco automatico), il widget si apre direttamente.

## Nascondere il contenuto nelle app recenti

L'interruttore "Nascondi nelle app recenti", indipendente dal blocco app, oscura la miniatura di Saldo nella schermata delle app recenti. La stessa protezione di sistema blocca anche gli screenshot e la registrazione dello schermo mentre Saldo è visibile.

## Cosa protegge il blocco (e cosa no)

Il blocco impedisce a chi ha in mano il telefono sbloccato di aprire Saldo e vedere i tuoi dati. Non cifra il database sul dispositivo: la protezione dei dati a riposo resta quella offerta da Android con il blocco schermo del telefono. Il PIN non viene mai salvato in chiaro e non entra nei file di backup.
