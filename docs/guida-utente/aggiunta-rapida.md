[Torna all'indice](README.md)

# ⚡ Aggiunta rapida: scorciatoie, widget, tile e testo

Registrare una spesa è l'operazione più frequente, quindi Saldo offre più porte per farlo. Nessuna di queste sostituisce l'editor completo: sono scorciatoie per il caso normale, e da tutte si può passare all'app.

## Scorciatoie dall'icona dell'app

Una pressione prolungata sull'icona di Saldo apre tre voci: nuova spesa, nuova entrata, nuovo trasferimento. Ognuna apre direttamente l'editor completo, già impostato sul tipo scelto.

## Widget sulla schermata home

Il widget si aggiunge dal menu dei widget del launcher e ne esistono due forme:

- **griglia di categorie**: le categorie nell'ordine della schermata Categorie, oppure quelle che scegli e riordini tu, un tocco per scegliere;
- **barra Spesa/Entrata**: due tasti, per chi vuole meno ingombro.

Toccando il widget si apre una **schermata rapida** sopra il launcher, con il tastierino già pronto: scrivi l'importo e salvi, senza che l'app si apra per intero.

Il **conto** non è un controllo del widget ma una sua impostazione: si sceglie quando lo si piazza (e si può cambiare dalla configurazione del widget), così a ogni uso c'è un'interazione in meno. Se il conto configurato non esiste più, il widget torna a usare il conto predefinito dell'app.

Due cose che il widget non fa, per scelta: non mostra saldi né totali, e non si ridisegna quando registri un movimento. È un punto di ingresso, non un pannello di lettura: così non consuma nulla per chi lo tiene sulla home e non lo usa. Cambiare un conto, una categoria o il tema invece lo aggiorna.

### L'aspetto

Il widget ha la stessa forma dei widget di Chiaro e Passo, le app sorelle, così sulla stessa home si leggono come una famiglia: una card con gli angoli arrotondati e, di default, lo sfondo blu pieno con il testo bianco. Dalla configurazione del widget (pressione prolungata sul widget, poi l'icona delle impostazioni, a seconda del launcher) scegli:

- lo **sfondo**: chiaro, scuro, come il telefono (segue il passaggio chiaro/scuro all'istante) o un colore fra sei (blu, blu chiaro, verde, verde acqua, viola, terracotta);
- l'**opacità** dello sfondo, a passi del 5%, da pieno a trasparente.

Su uno sfondo colorato le icone delle categorie sono schiarite quanto basta per leggersi sul colore scuro, senza cambiare tinta. Sotto il 50% di opacità la card lascia vedere lo sfondo del telefono, e il colore del testo lo decide quello: scuro se Android dice che lo sfondo è chiaro, bianco altrimenti. Una card chiara o scura resta invece con il testo che le è proprio. Se cambi lo sfondo del telefono, un widget trasparente aggiorna il colore del testo al suo prossimo aggiornamento (quando cambi un conto, una categoria, il tema o le sue impostazioni).

In cima alle impostazioni c'è il widget vero, disegnato come lo riceve il launcher, e cambia mentre scegli; toccarlo lì non fa nulla, è un'anteprima. Le modifiche valgono quando tocchi "Aggiorna il widget".

## Tile nelle Impostazioni rapide

Nella tendina delle impostazioni rapide di Android puoi aggiungere la tile **"Aggiungi spesa"** (dalla schermata di modifica dei riquadri; l'app non la piazza da sola). Un tocco chiude la tendina e apre la stessa schermata rapida del widget, con il tastierino pronto.

La tile porta **sempre a una spesa**: è il caso dominante, e una scelta del tipo costerebbe un tocco in più a chi vuole quello. Il conto è quello predefinito e la categoria è la più usata, entrambe cambiabili nella schermata.

A schermo bloccato Android chiede prima di sbloccare il telefono. Se hai attivato il blocco app con PIN, la schermata rapida lo chiede come ogni altra parte dell'app: una scorciatoia che aggira il blocco sarebbe un buco, non una comodità.

## Inserimento rapido testuale

Nella schermata rapida di widget e tile, sopra il form, c'è una riga di testo. Puoi scrivere in un colpo quello che hai speso:

- `12,50 pizza` - importo e descrizione
- `8 taxi ieri` - con la data
- `25.90 spesa 3/7` - con una data breve, letta nell'ordine della tua lingua
- `12,50 € pizza` - il simbolo o il codice della valuta si possono scrivere, prima o dopo

L'app compila importo, descrizione e data, ed evidenzia i campi che ha dedotto. La categoria la propone da quello che hai già scritto in passato: prima guarda i nomi delle tue categorie, poi le descrizioni che hai usato per quella parola. Per questo migliora con l'uso, invece di invecchiare come farebbe un dizionario di parole chiave.

Il principio è uno solo, e vale sopra ogni comodità: **il testo precompila e non salva mai da solo, e quando non è sicuro tace invece di indovinare.**

- Se l'importo è ambiguo, non ne mette nessuno e lascia il tastierino aperto sul campo importo.
- Se la categoria non è chiara (per esempio due categorie corrispondono alla stessa parola), non ne preseleziona nessuna: resta quella più usata, come sarebbe stato senza testo.
- Una scelta fatta a mano, col tastierino o dai selettori, non viene mai scavalcata da quello che scrivi dopo.

Il salvataggio è sempre un tuo tocco, e prima di toccarlo vedi esattamente cosa verrà salvato.

## Se l'app è ancora vuota

Se non hai nemmeno un conto (o nessuna categoria del tipo richiesto), la schermata rapida non mostra un form che non potrebbe salvare: spiega cosa manca e offre un tasto per aprire Saldo e crearlo.
