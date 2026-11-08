# Résultat — VOIE 1 : moyenne fiable

Travail réalisé le 8 novembre 2026, dans le worktree fourni. Les modifications restent non commitées.

## Fait

- Lot 0 : lecture intégrale de `PLAN-MOYENNE.md`, du README, de `docs/MOYENNES.md`, de `GradesRealShapeTest.kt` et de `captures/grades_documented.json` ; commande de référence tentée.
- Lot 1 : moteur pur `AverageEngine`, exclusion des coefficients de matière nuls, coefficient inconnu à 1, repli sur les notes ; simulateur fondé sur la moyenne affichée, barème libre, objectif dans ce barème et effet sur la générale.
- Lot 2 : codes matière et sous-code conservés, déduplication complète, rapprochement par code puis nom normalisé, homonymes conservés, sous-matières exclues de la pondération et placées sous leur mère quand le code le permet.
- Lot 3 : lecture de `dateCalcul`, arbitrage pur `AverageReconciliation`, conservation de l'officielle quand l'écart reste inexpliqué, recalcul des matières et de la générale avec conservation du bulletin et des notes qui expliquent le retard.
- Lot 4 : réglage persistant 1 ou 2 décimales (2 par défaut), moyennes formatées sans double arrondi, liens vers les notes expliquant le retard, accueil et widget renseignés, courbe de période par défaut avec interrupteur annuel, tendance entre origines compatibles, interface du simulateur branchée. Les nouvelles données numériques et explications sont masquées en mode « montrer ».
- Lot 5 : documentation et texte embarqué mis à jour ensemble ; test d'égalité intégrale renforcé.
- Lot 6 : commandes de clôture tentées ; présent rapport écrit.

Le marqueur `groupeMatiere` ne change pas le calcul : une ligne groupe reste pondérée comme auparavant si elle a une moyenne et un coefficient admissible. La fixture fictive et les tests fixent ce choix ; aucune capture n'apporte la preuve qu'il faudrait exclure les groupes.

## Pas fait / validation restante

- Aucun test Kotlin, lint ou APK n'a pu être exécuté/construit dans ce bac à sable : Bash échoue avant même d'exécuter le script et de démarrer Gradle. La compilation et le résultat des tests restent donc à confirmer par le hub.
- Référence de **530 tests, 0 échec** : fournie par l'orchestrateur, **non remesurée ici**. Aucun nombre de tests réussis après modification n'est affirmé.
- Les tests ont été écrits avant les correctifs principaux ; le cycle d'exécution rouge puis vert n'a pas pu être observé. Des assertions complémentaires ont été ajoutées pendant la revue statique.
- Pas de test Compose instrumenté ni d'essai sur téléphone ; la présentation est couverte par des tests de logique, non exécutés ici.
- Aucune requête vers ÉcoleDirecte, aucun compte réel, aucune modification d'`ApiSession`, des intervalles de synchronisation ou des garde-fous d'écriture. Aucun commit, push, merge ou commande de branche ; aucun accès manuel à `.git`.

## Commandes et sorties réellement observées

Commande de référence demandée, tentée avant les modifications puis après l'écriture des premiers tests :

```text
bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh .
exit_code: 1
bash.exe: *** fatal error - CreateFileMapping S-1-5-21-2892306765-1606266371-1694119132-1001.1, Win32 error 5. Terminating.
```

Commande de clôture, également retentée après les derniers correctifs :

```text
bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh . testDebugUnitTest lintDebug assembleDebug
exit_code: 1
bash.exe: *** fatal error - CreateFileMapping S-1-5-21-2892306765-1606266371-1694119132-1001.1, Win32 error 5. Terminating.
```

Contrôles statiques Python exécutés dans le worktree, code de sortie 0 :

```text
Static checks OK: no Android imports in the four new domain files; 10 identical documentation sections; 2 valid JSON fixtures.
```

Ces contrôles vérifient le texte documentaire et le JSON ; ils ne remplacent pas la compilation ou les tests Kotlin.

## Tests ajoutés et adaptés

**27 nouvelles méthodes de test déclarées** :

- `domain/AverageEngineTest.kt` : 4 tests, coefficients nul/inconnu, repli sur les notes, avant égal à l'affiché, barèmes 10/20/40, effet général et estimation si la pondération ne reproduit pas la générale.
- `domain/AverageReconciliationTest.kt` : 9 tests, bulletin à jour, dateCalcul, retard d'une/deux notes, même jour, date absente, écart inexpliqué, tolérance d'arrondi, notes exclues et absence de données.
- `domain/AveragePresentationTest.kt` : 6 tests, décimales, masque, absence de double arrondi, origines de tendance, courbe par période et description accessible.
- `data/SubjectIdentityTest.kt` : 3 tests, capture documentée/dateCalcul, sous-matières/groupes/homonymes, code prioritaire et noms normalisés.
- `widget/WidgetAverageTest.kt` : 1 test, publication et sérialisation des explications puis masquage.
- `data/GradesAverageSourceTest.kt` : 4 tests supplémentaires dans le dépôt avec faux serveur local, fixture de matières, rapprochement, générale recalculée et repli si tous les coefficients de matière sont nuls.

Les attentes d'affichage des tests existants du dépôt sont adaptées aux deux décimales. Le test documentaire existant vérifie désormais l'égalité complète des sections.

## Fichier par fichier

Les chemins Kotlin de production ci-dessous sont relatifs à `app/src/main/java/fr/ecole2recte/app/` ; les chemins de tests sont relatifs à `app/src/test/java/fr/ecole2recte/app/`.

| Fichier de production | Changement |
| --- | --- |
| `domain/AverageEngine.kt` | Nouveau moteur pur et projection de la générale. |
| `domain/AverageReconciliation.kt` | Nouveau verdict officiel/calculé/recalculé et recherche du retard. |
| `domain/AveragePresentation.kt` | Formatage, libellés, explications et tendance. |
| `domain/SubjectIdentity.kt` | Identité stable et rapprochement normalisé. |
| `domain/GradeMath.kt` | Code matière dans les notes du domaine. |
| `domain/GradeSimulation.kt` | Moyenne affichée, barème libre et messages associés. |
| `domain/GradeCurves.kt` | Filtre de période, point sans date et précision accessible. |
| `domain/Models.kt` | Origine recalculée, identités, verdicts et nombres bruts pour l'affichage. |
| `domain/PrivacyMode.kt` | Masquage des nouveaux nombres et verdicts. |
| `domain/AverageMethod.kt` | Texte embarqué identique au document. |
| `data/api/Parsers.kt` | Codes, sous-matières, groupes, dateCalcul et clé de déduplication. |
| `data/EcoleDirecteRepository.kt` | Appel du moteur et de l'arbitrage, construction fidèle des lignes, origines de tendance. |
| `settings/AppSettings.kt` | Réglage réactif et persistant des décimales, redessin des widgets. |
| `settings/AverageDecimals.kt` | Lecture du réglage partagé avec les widgets. |
| `ui/common/AverageFormatting.kt` | Lecture Compose réactive des décimales. |
| `ui/common/Motion.kt` | Conservation des décimales pendant l'animation des moyennes. |
| `ui/more/AppearanceCard.kt` | Choix 1/2 décimales dans Apparence. |
| `ui/home/HomeScreen.kt` | Formatage, origine et explication courte sur l'accueil. |
| `ui/grades/GradesView.kt` | Indentation, pastilles/liens, courbe par période et simulateur. |
| `ui/grades/GradeSimulatorUi.kt` | Avant affiché, barème, matière identifiée par clé et effet général. |
| `ui/grades/GradeCharts.kt` | Précision des nombres sur les courbes. |
| `sync/SyncStorage.kt` | Sérialisation rétrocompatible des informations de moyenne du widget. |
| `widget/WidgetLogic.kt` | Publication de l'origine, de l'officielle et de l'explication. |
| `widget/WidgetPrivacy.kt` | Masquage des nouvelles informations du widget. |
| `widget/WidgetUi.kt` | Application des décimales à la lecture du widget. |
| `widget/Widgets.kt` | Affichage compact de l'origine et du retard. |

| Fichier de test | Changement |
| --- | --- |
| `domain/AverageEngineTest.kt` | Nouveau, 4 tests. |
| `domain/AverageReconciliationTest.kt` | Nouveau, 9 tests. |
| `domain/AveragePresentationTest.kt` | Nouveau, 6 tests. |
| `data/SubjectIdentityTest.kt` | Nouveau, 3 tests. |
| `widget/WidgetAverageTest.kt` | Nouveau, 1 test. |
| `data/GradesAverageSourceTest.kt` | 4 tests supplémentaires et attentes à deux décimales. |
| `data/GradesPeriodsTest.kt` | Attentes de moyennes à deux décimales. |
| `data/EcoleDirecteRepositoryTest.kt` | Attentes de moyennes et tendance à deux décimales. |
| `session/GradeSimulationTest.kt` | Égalité complète du document et du texte embarqué. |

| Autre fichier | Changement |
| --- | --- |
| `app/src/test/resources/captures/grades_subjects.json` | Nouvelle fixture fictive : mère, spécialité, homonyme, groupe. |
| `docs/MOYENNES.md` | Documentation A1 à A9 et simulateur actualisée. |
| `RESULTAT-MOYENNE.md` | Présent rapport de réalisation et de validation. |

## Points à vérifier sur le téléphone

1. Passage de deux à une décimale dans Apparence, prise en compte immédiate dans Notes, accueil, courbes, simulateur et widget, puis persistance après redémarrage.
2. Badge « Recalculée · bulletin … », phrase datée et ouverture de la bonne note, y compris une note ancienne absente des six dernières.
3. Ordre mère/sous-matière, indentation et matières homonymes ; si le code mère manque, aucune parenté n'est inventée.
4. Simulation sur 10, 20 et 40 : même chiffre « Avant » que la matière, saisies invalides, objectif impossible et ligne de générale explicitement estimée quand nécessaire.
5. Courbe limitée à la période puis interrupteur annuel, dernier point égal à la moyenne calculée de la période, absence de flèche entre origines différentes.
6. Mode « montrer » sur écran et widget : ni chiffre officiel conservé, ni explication de note visible.
7. Lisibilité des libellés longs et des widgets aux petites tailles, avec les tailles de texte élevées et les thèmes sombre/noir.

La recherche à date égale utilise un ordre déterministe d'identifiants, documenté, et exige toujours une concordance de moyenne. Il n'existe pas d'heure de calcul dans la capture : cette déduction reste une estimation, comme le recalcul affiché.
