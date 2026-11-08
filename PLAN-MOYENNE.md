# Plan — moyenne fiable, pièces jointes, envoi de messages

Branche `moyenne-fiable` (voie 1) et `pj-messages` (voie 2), issues de `moidefr-plan-ecoledirecte-mobile` (5e193b9).
Rien n'est poussé ni fusionné : la fusion se décide avec le propriétaire.

## Règles communes (valables pour chaque lot)

1. **Hors réseau.** Aucune requête vers ÉcoleDirecte, aucun identifiant réel. Tout se teste avec des fixtures
   (`app/src/test/resources/captures/`) et des faux (fake client / fake repository).
2. **Tests d'abord.** Pour chaque correctif : un test qui échoue sur le code actuel, puis le correctif, puis le test passe.
3. **Un commit par lot**, message en français, impératif court. Jamais de `push`, jamais de `merge`, jamais de `--amend` sur un lot terminé.
4. **Vérifier avant d'affirmer.** Commande de test : `bash C:/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh <dossier-du-worktree> [tâches]`
   (défaut `testDebugUnitTest` ; avant la fin : `testDebugUnitTest lintDebug assembleDebug`). Si le build est impossible, l'écrire tel quel : ne jamais annoncer « ça passe » sans l'avoir vu passer.
5. **Textes d'interface en français**, accents complets, virgule décimale. Le vocabulaire existant prime (« officielle », « calculée par l'app »).
6. **Ne pas élargir.** Pas de refonte hors périmètre, pas de dépendance nouvelle sans nécessité, pas de modification des garde-fous réseau (`ApiSession`, intervalles de synchro).
7. **Fin de voie** : écrire `RESULTAT-<voie>.md` à la racine du worktree : ce qui est fait, ce qui ne l'est pas, tests ajoutés, sorties réelles des commandes, points à vérifier sur le téléphone.

---

# VOIE 1 — `moyenne-fiable` (A1 à A9, et le point 30)

Fichiers au cœur du sujet : `data/EcoleDirecteRepository.kt` (`overview`, `weightedSubjects`), `data/api/Parsers.kt` (`grades`),
`domain/GradeMath.kt`, `domain/GradeSimulation.kt`, `domain/GradeCurves.kt`, `domain/FrenchFormat.kt`, `settings/AppSettings.kt`,
`ui/grades/*`, `widget/WidgetLogic.kt`, `domain/AverageMethod.kt`, `docs/MOYENNES.md`.

## Lot 0 — Base de référence
- Lancer `testDebugUnitTest` tel quel, noter le nombre de tests dans `RESULTAT-MOYENNE.md`.
- Lire `docs/MOYENNES.md`, `GradesRealShapeTest.kt` et `captures/grades_documented.json` (forme réelle de l'API : `ensembleMatieres.dateCalcul`, `sousMatiere`, `codeSousMatiere`, `groupeMatiere`, `rang`, `effectif`).

## Lot 1 — Moteur de moyenne pur et testable (A2, A4 cœur, A5 cœur)
Extraire le calcul de `overview()` dans un objet Kotlin pur `domain/AverageEngine.kt` (sans Android), appelé par le dépôt.
- **A2 — coefficient 0.** Une matière dont `coef == 0` est **exclue** de la moyenne générale. Un coefficient **inconnu** (`null`) vaut 1. Si toutes les matières sont exclues, retomber sur l'étape suivante (notes). Cohérent avec les notes, déjà exclues à coef 0.
- **A4 — simulateur cohérent avec l'affichage.** La projection part de la moyenne **affichée** (officielle ou recalculée) : `projetée = (affichée × W + note20 × c) / (W + c)`, `W` = somme des coefficients des notes comptées de la matière. « Avant » = le chiffre de l'écran. Ajouter l'effet sur la **moyenne générale** : remplacer la matière par sa valeur projetée dans la pondération par coefficients de matière ; si la générale est officielle et non recalculable, estimer `générale + (matière' − matière) × coefMatière / Σcoef` et marquer « estimation ».
- **A5 — barème libre.** `projection` et `need` acceptent un barème (défaut 20) : saisie `x / barème`, validation `0 ≤ x ≤ barème`, conversion sur 20 par `GradeMath.on20`. « Quelle note pour atteindre X » rend la note dans le barème choisi.
- Tests : coef 0 ; coef inconnu ; toutes matières à 0 ; barèmes 10 / 20 / 40 ; avant = affiché ; effet sur la générale.

## Lot 2 — Lecture fidèle des matières (A3, A9)
- `ApiSubjectAverage` gagne : `subjectCode`, `subCode`, `isSubSubject` (`sousMatiere` ou `codeSousMatiere` non vide), `isGroup` (`groupeMatiere`). `ApiGrade` gagne `subjectCode` (`codeMatiere`).
- **A3.** Dans la moyenne générale calculée, une **sous-matière n'est jamais comptée** (elle est déjà dans sa mère). `isGroup` : ne rien changer au calcul sans preuve dans la capture ; ajouter un test qui fixe le comportement choisi et le consigner dans `RESULTAT`. À l'écran, une sous-matière est **indentée sous sa mère**.
- La déduplication ne se fait plus sur `période + libellé` seul : clé `période + code + sous-code + libellé`.
- **A9.** Rapprocher une note d'une matière par **code** d'abord, puis par nom **normalisé** (sans accents, casse, espaces multiples, `prettySubject`). Une note qui ne correspond à aucune matière crée une ligne, mais jamais en double d'une matière existante.
- Tests : fixture avec spécialité sous tronc commun ; deux matières de même libellé ; « FRANCAIS » vs « Français ».

## Lot 3 — Moyenne officielle en retard (A1)
- Lire `ensembleMatieres.dateCalcul` dans `ApiPeriod.computedOn` (date seule, tolérer l'absence).
- Créer `domain/AverageReconciliation.kt` : `reconcile(official, grades, computedOn): AverageVerdict(value, origin, official, behind)` avec `origin ∈ {Official, Recomputed, Computed, None}`.
  - Notes « en retard » : notes comptées dont `date > computedOn`. Si `date == computedOn`, ne conclure que si la méthode ci-dessous le confirme.
  - Sans `computedOn` : retirer les notes de la plus récente à la plus ancienne ; si la moyenne officielle réapparaît (tolérance 0,055), ces notes sont celles qu'elle ignore. Une note sans date arrête la recherche. Un écart inexpliqué laisse le **bulletin avoir raison**.
- Même arbitrage par matière, puis la générale est **recalculée** (matières, coefficients, sans sous-matières) dès qu'une matière est en retard.
- Écran Notes : pastille « Recalculée · bulletin 14,26 » + phrase « calculée avant la note X du JJ/MM » avec lien vers la note ; même information, plus courte, sur l'accueil et le widget Notes. Le chiffre affiché reste étiqueté honnêtement.
- Tests : bulletin à jour ; en retard d'une note ; de deux notes ; notes du même jour ; sans date ; écart inexpliqué ; arrondis dans la tolérance.

## Lot 4 — Affichage, réglages, courbes, tendance (A6, A7, A8, interface A1 à A5)
- **A6 — décimales.** Réglage **« Décimales des moyennes : 1 ou 2 »** (défaut **2**, comme le bulletin) dans Plus > Apparence, via `AppSettings`. `FrenchFormat.decimal(value, digits)` existe déjà. S'applique aux moyennes (générale, matière, classe, min/max), au simulateur et au widget Notes — pas aux notes individuelles.
- **A7 — courbe de matière.** Par défaut, **limitée à la période affichée** : son dernier point égale la moyenne calculée de la période. Interrupteur « Toute l'année » pour l'ancien comportement, avec libellé clair.
- **A8 — tendance.** Flèche affichée seulement si les deux moyennes comparées ont la **même origine** (officielle/officielle, ou calculée/calculée avec « ≈ »). Sinon rien, plutôt qu'une flèche trompeuse.
- Brancher l'interface du simulateur sur le Lot 1 (champ « barème », « avant » = affiché, ligne « Moyenne générale : x → y (estimation) »).
- Tests d'interface minimaux si faisables ; sinon tests de la logique de présentation.

## Lot 5 — Documentation (point 30)
- Mettre à jour `docs/MOYENNES.md` **et** `domain/AverageMethod.kt` en même temps (un test vérifie qu'ils sont identiques) : coef 0, sous-matières, `dateCalcul` et retard du bulletin, décimales, courbe par période, tendance, simulateur (avant = affiché, barème, effet sur la générale).
- Corriger la phrase « 1 quand il est inconnu ou nul » et la section « Pourquoi la moyenne calculée peut être plus basse ».

## Lot 6 — Clôture
- `testDebugUnitTest lintDebug assembleDebug` ; écrire `RESULTAT-MOYENNE.md` (règle 7).

---

# VOIE 2 — `pj-messages` (points 26 et 27)

**Constat de départ : les deux fonctions existent déjà** (`downloadAttachment`, `AttachmentSafety`, `sendMessage` avec trois verrous, `WriteAccess`, `MessageDraft`, `Recipients`). Cette voie est un **audit, des tests et des finitions**, pas une réécriture.

## Lot 7 — Pièces jointes (26)
- Audit de `ui/common/Attachments.kt`, `EcoleDirecteApi.attachment`, `EcoleDirecteClient.download` : types refusés, nom de fichier jamais hors du dossier prévu, plafond de 25 Mo, confirmation avant ouverture, effacement à la fermeture, type MIME.
- Tests unitaires (hors réseau, faux client) : extensions refusées (`apk`, `html`, `svg`, `js`…), traversée de chemin (`../`, séparateurs, noms vides ou très longs), dépassement du plafond, type annoncé ≠ extension, source Devoir / Message / Document.
- Finitions si absentes : action **« Enregistrer dans Téléchargements »** (Storage Access Framework, sans permission) à côté de « Ouvrir » ; taille et type affichés avant téléchargement ; message d'erreur utile pour 401 / fichier absent / trop gros.
- Les pièces jointes des **devoirs** (`FICHIER_CDT`) et des **messages** passent par le même contrôle.

## Lot 8 — Envoi de messages (27)
- Audit des verrous dans `EcoleDirecteRepository.sendMessage` : confirmation explicite, autorisation « Plus > Envoi de messages » (désactivée par défaut), brouillon valide, **une seule tentative**, aucun renvoi automatique.
- Tests : avec l'autorisation coupée ou sans confirmation → **zéro requête** ; brouillon invalide (sans destinataire, sans objet, corps vide, trop long) → refus ; un échec réseau ne déclenche pas de deuxième envoi ; **instantané figé des octets** de la requête d'envoi (clés et formes, valeurs fictives).
- Finitions si absentes : écran de confirmation qui **récapitule destinataires, objet, début du texte** ; brouillon conservé si l'envoi échoue ; réponse à un message avec destinataire prérempli ; recherche de destinataires (`Recipients.kt`) testée sur fixture.
- **Interdit :** envoyer un vrai message, même à soi-même, pendant ce travail. La vérification réelle se fera à la main, sur un compte de test, par le propriétaire.

## Lot 9 — Clôture
- `testDebugUnitTest lintDebug assembleDebug` ; écrire `RESULTAT-PJ-MESSAGES.md` (règle 7).
