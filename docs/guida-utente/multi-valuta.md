[Torna all'indice](README.md)

# 🌍 Multi-valuta e tassi di cambio

Ogni conto ha la sua valuta e ogni movimento conserva l'importo nella valuta in cui è avvenuto. Se hai conti o movimenti in una valuta diversa dalla tua, Saldo mostra anche quanto valgono nella tua valuta principale, così le somme dell'app sono somme vere e non pezzi separati.

Il controvalore è sempre una **stima**, e l'app lo dice ogni volta che lo mostra.

## Che cosa vedi, e dove

Con almeno un conto in valuta estera, il controvalore entra in: saldo totale, card Oggi e Mese, statistiche, budget, spendibile oggi, obiettivi, "In arrivo" e le righe dell'elenco movimenti.

Ogni cifra che contiene una conversione porta il segno **"≈"**. Sotto il saldo totale compare una riga con la data dei tassi usati, del tipo "Include controvalori stimati ai tassi BCE del 29/07". Se un giorno l'app non ha nessun tasso, le cifre tornano semplicemente a essere quelle della tua valuta, come se la conversione non esistesse: mai un vuoto, mai uno zero al posto di un numero.

## Le due regole della conversione

Sono due e non cambiano, perché sono la differenza tra una cifra stabile e una cifra che si muove da sola:

- **Quello che è successo si converte al tasso del giorno in cui è successo.** Una spesa di giugno resta convertita ai tassi di giugno per sempre: un mese concluso non cambia più, e le statistiche di un anno passato restano quelle che avevi letto.
- **Quello che hai adesso si converte all'ultimo tasso noto.** Il saldo di un conto, il saldo totale e le previsioni usano il tasso più recente, perché rispondono alla domanda "quanto vale oggi".

Il controvalore **non viene mai salvato**: viene calcolato ogni volta. Quello che l'app scrive nel database è l'importo originale, nella sua valuta. Per la stessa ragione, in un trasferimento fra due valute diverse il dato reale sono i due importi che hai digitato tu: da quelli l'app ricava il tasso effettivo dell'operazione e lo mostra nell'editor, senza usare i tassi di mercato.

## Da dove arrivano i tassi

Dai **tassi di riferimento della Banca Centrale Europea**, un endpoint pubblico che non richiede account.

- La richiesta parte **solo ad app aperta**, con un limite di frequenza, e **solo se hai davvero dati in una valuta diversa dalla principale**. Con una sola valuta non parte nessuna richiesta.
- Esce una richiesta di tassi, cioè un indirizzo IP e il fatto che qualcuno li ha chiesti. **Non escono i tuoi dati**: né importi, né conti, né quali valute usi (l'app scarica tutto il paniere BCE, non le tue valute).
- I tassi restano in cache sul dispositivo. Offline vale l'ultimo tasso noto, con la sua data dichiarata: niente errori, niente attese.
- La BCE non pubblica nei fine settimana e nei giorni festivi: per quei giorni vale l'ultimo tasso pubblicato prima.

Puoi **disattivare la conversione** da Impostazioni > Preferenze. Da disattivata i totali tornano a considerare solo la valuta principale, come prima, e l'app non fa nessuna richiesta di rete.

## La schermata Tassi di cambio

Ci si arriva da tre punti: la riga dei controvalori sotto il saldo totale, la nota in fondo alla lista Conti e Impostazioni > Preferenze.

- **Convertitore rapido** in testa: scrivi l'importo col tastierino dell'app, scegli la valuta e leggi il controvalore. Il caso normale è il prezzo visto all'estero letto nella tua valuta, e la direzione si inverte con un tocco.
- **Le tue valute** e **Altre valute BCE**: quanto vale un'unità della tua valuta principale in ognuna, la variazione sull'ultima pubblicazione e un mini-grafico delle ultime pubblicazioni. Il grafico mostra le pubblicazioni, non i giorni di calendario: per questo i fine settimana non lasciano buchi.
- Toccando una valuta si apre il dettaglio con il grafico a 1 o 3 mesi, minimo, massimo e variazione del periodo.

La schermata è di sola lettura: usa i tassi già scaricati e non ne scarica di suoi.

## Quando una valuta resta fuori

Se una valuta non è nel paniere BCE, l'app non ha un tasso per convertirla: quei movimenti restano fuori dai totali e una riga informativa lo dichiara, senza inventare una cifra. Nelle statistiche gli importi in altre valute hanno anche un drill-down dedicato, così li puoi vedere per quello che sono.

## Cosa non fa

- Non è un convertitore per operazioni reali: le banche applicano i loro tassi e le loro commissioni, che non sono quelli della BCE.
- Non tiene conto di commissioni sui cambi.
- Non converte i dati per salvarli: se cambi la valuta principale, cambia solo il modo in cui l'app somma e presenta, non quello che hai registrato.
