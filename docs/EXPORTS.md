# Exporter l'emploi du temps

Agenda → bouton « Exporter ». Le fichier est écrit dans le cache privé du téléphone (`exports/`, effacé
à chaque lancement et à la fermeture), puis remis à l'application que tu choisis : partage ou ouverture.
ecole2recte n'envoie rien nulle part et n'écrit pas dans ton calendrier.

## Formats

- **PDF** : A4 paysage, une page par semaine type (« Semaine 1 », « Semaine 2 » ; une seule page si
  l'emploi du temps n'alterne pas). Grille jours × heures sur la même échelle d'une page à l'autre, cours
  colorés comme dans l'application ou en niveaux de gris (impression noir et blanc), doubles heures
  fondues, cours simultanés côte à côte, légende des matières avec le professeur.
- **ICS** (iCalendar, RFC 5545) : un événement récurrent par créneau (`FREQ=WEEKLY`, `INTERVAL=1` pour
  un cours de toutes les semaines, `INTERVAL=2` pour un cours d'une semaine sur deux), du jour choisi
  (par défaut aujourd'hui) jusqu'à la date choisie (par défaut le 30 juin). Heures locales
  `TZID=Europe/Paris` avec son `VTIMEZONE`, lignes en CRLF pliées à 75 octets, texte échappé, `UID` stables
  (réimporter le fichier met à jour au lieu de dupliquer), `DTSTAMP`, `EXDATE` pour les cours annulés
  connus. Salle en `LOCATION`, professeur et semaine en `DESCRIPTION`.

Hors de l'export d'emploi du temps, la fiche d'un cours (agenda) et celle d'un devoir ou d'un contrôle
proposent « Ajouter au calendrier du téléphone » : l'écran « nouvel événement » du Calendrier s'ouvre,
rempli (`ACTION_INSERT`) ; rien n'est enregistré sans ton geste et aucune permission n'est demandée.

## Semaines 1 / 2 (A / B)

**Établi.** Dans les réponses d'`emploidutemps.awp` que nous connaissons (la capture documentée
`app/src/test/resources/captures/timetable_documented.json` et le client web du même auteur), il n'y a
**aucun champ** « semaine A / B » ni « une semaine sur deux ». Les champs vus sont : `id`, `text`,
`matiere`, `codeMatiere`, `typeCours` (`COURS`, `PERMANENCE`, `CONGE`), `start_date`, `end_date`, `color`,
`prof`, `salle`, `classe`, `classeCode`, `groupe`, `groupeCode`, `isFlexible`, `isModifie`, `isAnnule`,
`contenuDeSeance`, `devoirAFaire`. `groupe` désigne un sous-groupe de la classe, pas une semaine.

**Supposé, non vérifié.** Un établissement qui alterne le montre seulement par ses effets : des cours
présents une semaine sur deux. Aucune réponse réelle d'un tel établissement n'a été vue. L'application ne
détecte donc pas l'alternance toute seule ; elle suggère seulement (« tes deux semaines se
ressemblent peu ») quand au moins 30 % des créneaux n'existent que dans l'une des deux semaines connues.

**Ce que fait l'application.**

1. Réglage « Mon emploi du temps alterne » (par profil) et choix de la semaine de référence : « la
   semaine du 2 novembre, en cours, est la semaine 1 (ou 2) ». Les autres semaines s'en déduisent par la
   parité, y compris à travers le changement d'année.
2. Avec les deux semaines connues en cache (la semaine en cours et la suivante), chaque créneau est
   classé : présent **toutes les semaines**, **semaine 1 seulement** ou **semaine 2 seulement**
   (même jour, mêmes heures, même matière). Les doubles heures sont fondues, les congés (`CONGE`) écartés.
3. Sans alternance, la semaine la plus fournie fait référence ; les exceptions de l'autre semaine
   (cours déplacé, remplacé) ne deviennent pas des créneaux.

## Limites

- Seules **deux semaines** sont connues (la semaine en cours et la suivante). Si l'une est vide
  (vacances) ou n'a jamais été chargée, la semaine concernée est dite inconnue : la page ou les événements
  qui en dépendent sont incomplets, et rien n'est inventé.
- Les **vacances** et jours fériés ne sont pas connus : un événement récurrent revient aussi pendant ces
  semaines (à supprimer à la main dans le calendrier).
- Les exceptions (cours déplacé, remplacé, ajouté) ne sont pas dans le modèle de la semaine type. Seules
  les annulations connues deviennent des `EXDATE`, et seulement pour les deux semaines connues.
- Les changements d'une année à l'autre ne se devinent pas : refaire l'export en début d'année.
- Le dessin PDF n'est vérifiable que sur un téléphone (`PdfDocument`) ; la mise en page, elle, est testée.
