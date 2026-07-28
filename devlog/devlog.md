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

## 2026-07-28 - Riga di copyright nella schermata Informazioni

**Fatto:** sotto "Sviluppata da Callback Dev" compare `© 2026 Fiorenzo Brioni`, come nell'About di Snake (l'altro progetto Android dello stesso autore). Nuova stringa `about_copyright` in `values/strings.xml` con `translatable="false"`, `bodySmall` in `onSurfaceVariant` dentro `AppIdentity`, due dp di spazio sopra: sta sotto la riga dell'autore come una didascalia, senza contendere gerarchia al nome dell'app.

**Decisioni:** stringa non traducibile e presente nel solo `values`, come gia si fa per `about_library_names`: un nome proprio e un anno sono identici in ogni lingua, e duplicarli in `values-it` creerebbe due punti da tenere allineati per niente. Anno fisso nella risorsa e non calcolato a runtime: il copyright si riferisce all'anno di pubblicazione, non alla data in cui l'utente apre la schermata. `versionCode` a 151 con `versionName` fermo a 1.0.0: il codice identifica la build e deve avanzare perche il device di test aggiorni in place, il nome identifica il rilascio e resta quello che verra taggato.

**Problemi:** nessuno.

**Verificato:** `assembleDebug testDebugUnitTest lint detekt` in locale. Resa della riga da confermare su device insieme al resto della checklist della Fase 11.5.

**Prossimo:** invariato, la checklist di verifica su device e poi il tag `v1.0.0`.

---

## 2026-07-28 - La fase di release v1.0 diventa la 11.5

**Fatto:** rinumerata la fase di rilascio da "Fase 10" a "Fase 11.5" e aggiornati i riferimenti in PLANNING (nota dell'H1 delle fasi anticipate, riga del baseline profile nella Fase 9, nota sulle transizioni nella Fase 9.6, nota della Fase 15). Ora l'ordine di lettura del documento coincide con l'ordine dei numeri: 9.16, 10.0-10.21, 11, 11.5, 12, 13, 14, cloud, 15.

**Decisioni:** rilievo dell'utente, corretto: dopo lo spostamento della fase di release in fondo al blocco implementato, il documento passava da "Fase 11" a "Fase 10" andando in avanti, e "Fase 10" accanto a venti fasi numerate 10.x lasciava intendere una gerarchia che non esiste (le 10.x non sono sotto-passi del rilascio, sono funzionalita anticipate che hanno occupato quel numero prima che il rilascio arrivasse). Il suffisso .5 e gia la convenzione del file per le fasi inserite a valle (6.5, 9.5), quindi 11.5 dice esattamente la cosa giusta: il rilascio viene subito dopo l'ultima fase che rilascia. Regola ora esplicita nella nota della fase. Il costo e limitato ai riferimenti interni del piano: devlog archiviati e messaggi di commit precedenti continuano a dire "Fase 10", e restano come sono perche sono registri datati; la nota della fase lo dichiara, cosi il riferimento resta rintracciabile.

**Problemi:** nessuno.

**Verificato:** nessun riferimento residuo a "Fase 10" fuori dalla nota storica della fase e dai devlog archiviati; sequenza delle intestazioni verificata in ordine.

**Prossimo:** invariato, la checklist di verifica su device e poi il tag `v1.0.0`.

---

## 2026-07-28 - VISION riallineata alla release 1.0.0

**Fatto:** VISION.md portata in pari con la roadmap dopo la chiusura della v1.5 in PLANNING. Le etichette di versione inline seguono ora quello che e davvero uscito: budget e obiettivi di risparmio passano a v1.0, import CSV a v1.0 con la descrizione reale della funzione (riconoscimento del formato, anteprima, duplicati), i widget non sono piu "anticipati rispetto alla v1.5"; PIN, biometria, FLAG_SECURE, Excel e Google Sheets passano a v2.0. La sezione Roadmap perde il blocco v1.5 e diventa v1.0 (rilasciata, con l'elenco diviso tra perimetro MVP e anticipi) piu v2.0, con in testa la nota del rilascio su GitHub. Corretta anche la parte backup, che presentava Google Drive come "strategia principale" mentre la v1.0 ha il solo backup manuale su file: Drive e ora dichiarato rimandato (ADR 17), e il file di backup e presentato come il backup della versione, non come una seconda strada.

**Decisioni:** nessuna decisione di prodotto nuova, solo allineamento del documento a decisioni gia prese (ADR 17 e 38). Subito dopo, su richiesta utente, il termine "v1.5" e stato eliminato del tutto da PLANNING e VISION: dove serviva il concetto si parla di "rilascio intermedio fra l'MVP e la v2.0" o di "anticipi alla v1.0", cosi il documento non rimanda piu a una roadmap che non esiste. Allineate anche le due celle della review delle funzionalita finanziarie che rimandavano a quella roadmap (voci 18 e 20 della tabella dei candidati, ora "gia in roadmap v2.0"). Il termine resta solo nel devlog, che e un registro datato e non si riscrive.

**Modifiche puntuali della rimozione:** ADR 10 senza la nota storica; Fase 9.5 "anticipata alla v1.0" e il widget descritto come rimandato alla Fase 10.18 invece che "alla v1.5"; Fase 9.16 rinominata "Anticipi alla v1.0: budget, import CSV e widget"; note delle Fasi 10.18 e 10 riscritte; intestazione della Roadmap v2.0 che spiega il riassorbimento senza nominare la versione.

**Problemi:** nessuno.

**Verificato:** modifica di sola documentazione, nessun bump di versione (regola in CLAUDE.md). Nessun riferimento residuo alla v1.5 fuori dalla nota storica.

**Prossimo:** la checklist di verifica su device della fase di release (rinumerata 11.5 subito dopo, vedi la voce sopra), poi il tag `v1.0.0`.

---
