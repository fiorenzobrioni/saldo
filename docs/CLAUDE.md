# CLAUDE.md - docs/

Istruzioni per creare e modificare i documenti in `docs/`. Le regole di stile prosa (tipografia, tono ingegneristico, niente conclusioni, eccezioni per il codice) sono nel `CLAUDE.md` di root e valgono qui: seguile, non ripeterle.

## Regole generali

- Lingua: italiano.
- Verifica il comportamento reale nel codice e nelle stringhe (`strings.xml`) prima di descriverlo. Per prodotto e principi la fonte è `VISION.md`.

## Guida utente (`docs/guida-utente/`)

- Prospettiva utente: spiega cosa fa una funzione e cosa aspettarsi, non come è implementata. Nessun riferimento a classi, package o ADR.
- Indice: `README.md` (GitHub lo renderizza aprendo la cartella). Niente `index.md` o prefissi numerici finché non c'è un generatore di siti docs.
- Pagine di contenuto: un file per funzionalità, nome descrittivo in kebab-case senza numero (es. `movimenti-ricorrenti.md`).
- Ogni pagina di contenuto inizia con `[Torna all'indice](README.md)`.
- L'indice collega solo le pagine già scritte; le altre voci restano testo semplice, come promemoria di cosa manca.
- Il `README.md` di root punta una volta alla cartella della guida: non modificarlo per aggiungere una pagina.
- **Emoji nei titoli**: stesso stile del `README.md` di root. Un'emoji iniziale su: il titolo H1 di ogni pagina (incluso l'indice) e ogni voce elenco che rappresenta una funzionalità (nell'indice, linkata o no). Riusa l'emoji già assegnata alla stessa funzionalità nel `README.md` di root quando esiste una corrispondenza; scegline una nuova, sobria e pertinente altrimenti, ed evita di riusarla per concetti diversi nella stessa pagina. Le intestazioni H2/H3 interne alle pagine di contenuto restano senza emoji (come nel README di root).
