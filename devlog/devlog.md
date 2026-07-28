# DEVLOG Saldo

Diario di sviluppo del progetto. Le voci più recenti vanno in alto.
Ogni voce annota cosa è stato fatto, decisioni prese, problemi incontrati e cosa viene dopo.

Formato suggerito per ogni voce:

## YYYY-MM-DD - Titolo breve

**Fatto:** cosa è stato completato  
**Decisioni:** scelte tecniche/di design e il perché  
**Problemi:** cosa si è bloccato e come (o se) è stato risolto  
**Prossimo:** il passo successivo  

---

## 2026-07-28 - VISION riallineata alla release 1.0.0

**Fatto:** VISION.md portata in pari con la roadmap dopo la chiusura della v1.5 in PLANNING. Le etichette di versione inline seguono ora quello che e davvero uscito: budget e obiettivi di risparmio passano a v1.0, import CSV a v1.0 con la descrizione reale della funzione (riconoscimento del formato, anteprima, duplicati), i widget non sono piu "anticipati rispetto alla v1.5"; PIN, biometria, FLAG_SECURE, Excel e Google Sheets passano a v2.0. La sezione Roadmap perde il blocco v1.5 e diventa v1.0 (rilasciata, con l'elenco diviso tra perimetro MVP e anticipi) piu v2.0, con in testa la nota del rilascio su GitHub. Corretta anche la parte backup, che presentava Google Drive come "strategia principale" mentre la v1.0 ha il solo backup manuale su file: Drive e ora dichiarato rimandato (ADR 17), e il file di backup e presentato come il backup della versione, non come una seconda strada.

**Decisioni:** nessuna decisione di prodotto nuova, solo allineamento del documento a decisioni gia prese (ADR 17 e 38). Subito dopo, su richiesta utente, il termine "v1.5" e stato eliminato del tutto da PLANNING e VISION: dove serviva il concetto si parla di "rilascio intermedio fra l'MVP e la v2.0" o di "anticipi alla v1.0", cosi il documento non rimanda piu a una roadmap che non esiste. Il termine resta solo nel devlog e nella review delle funzionalita finanziarie, che sono registri datati e non si riscrivono.

**Modifiche puntuali della rimozione:** ADR 10 senza la nota storica; Fase 9.5 "anticipata alla v1.0" e il widget descritto come rimandato alla Fase 10.18 invece che "alla v1.5"; Fase 9.16 rinominata "Anticipi alla v1.0: budget, import CSV e widget"; note delle Fasi 10.18 e 10 riscritte; intestazione della Roadmap v2.0 che spiega il riassorbimento senza nominare la versione.

**Problemi:** nessuno.

**Verificato:** modifica di sola documentazione, nessun bump di versione (regola in CLAUDE.md). Nessun riferimento residuo alla v1.5 fuori dalla nota storica.

**Prossimo:** la checklist di verifica su device della Fase 10, poi il tag `v1.0.0`.

---
