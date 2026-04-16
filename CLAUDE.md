## Workflow für nicht-triviale Aufgaben

1. Bei jeder Aufgabe, die mehr als eine Datei betrifft oder unklare Schritte hat, erstelle zuerst `docs/PLAN.md` mit einer Zusammenfassung deines Verständnisses der Aufgabe, den Annahmen und dem geplanten Vorgehen. Stoppe und warte auf meine Bestätigung.

2. Nach meiner Bestätigung erstelle `docs/TASKS.md` mit konkreten Unteraufgaben
   als Markdown-Checkboxes: `- [ ] Task-Beschreibung`

3. Arbeite die Tasks einzeln ab. Nach jedem abgeschlossenen Task:
   - Aktualisiere `TASKS.md` und ändere `- [ ]` zu `- [x]`
   - Füge bei Bedarf eine kurze Notiz hinzu, was gemacht wurde
   - Fahre erst dann mit dem nächsten Task fort

4. Wenn alle Tasks abgehakt sind, gib eine Abschluss-Zusammenfassung.

## Für Bugfixes

Bei Bugfix-Anfragen beginne IMMER mit einer Analyse-Phase in PLAN.md
(Hypothesen, geplante Messungen) und warte auf Bestätigung, bevor du
Code änderst.