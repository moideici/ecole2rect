# Résultat — VOIE 5 : simulateur, projection annuelle, classe

Travail réalisé dans l'ordre S1, S2, S3. Changements laissés non commités dans le worktree. Aucune requête vers ÉcoleDirecte, aucune nouvelle dépendance. `ApiSession`, intervalles de synchronisation et garde-fous d'écriture inchangés.

## Fait

- **S1** : scénarios comprenant plusieurs devoirs hypothétiques avec note, barème et coefficient ; remplacement ou retrait de notes réelles comptées ; résultat par matière et effet cumulé sur la générale. La base est la moyenne affichée. Les calculs composent `GradeMath` et `AverageEngine.projectGeneral`, sans réécrire le moteur existant. Chaque résultat est une estimation.
- Scénarios nommés enregistrés localement, chiffrés avec le mécanisme Keystore existant, isolés par profil et période ; chargement, mise à jour, suppression et effacement à la suppression du compte. Les références devenues indisponibles sont signalées, sans modification silencieusement ignorée.
- Interface en cartes, saisies numériques avec virgule ou point. Une suppression de toutes les notes ne fabrique pas de zéro. Un remplacement incompatible avec la base officielle est refusé s'il produit une moyenne hors de 0 à 20.
- **S2** : annuelle des périodes disponibles pondérée si tous leurs coefficients sont connus et positifs ; sinon moyenne à plat explicitement indiquée. Périodes sans moyenne, période « Année » récapitulative et coefficients nuls exclus. Nombre de périodes retenues affiché.
- Meilleur cas, pire cas et cas probable pour 0 à 1 000 évaluations restantes par matière, avec coefficients 1 et barème 20. La projection modifie la période affichée et répercute son effet dans l'annuelle ; les autres périodes connues restent inchangées. Les hypothèses sont écrites dans l'interface et les résultats ne sont jamais présentés comme des prédictions.
- **S3** : lecture des champs `rang`, `effectif`, extrêmes de classe et `mediane` / `moyenneMediane`, transmission aux modèles d'écran. Valeurs absentes ou invalides masquées ; aucun rang estimé. Un effectif fourni seul peut être affiché.
- Comparaison horizontale minimum / classe / moi, complétée par médiane et maximum disponibles, dans la matière dépliée et sur la générale. Chaque barre porte sa valeur sur 20 ; une phrase complète décrit les données au lecteur d'écran.
- Les nouvelles statistiques, scénarios et projections sont masqués en mode « montrer à quelqu'un ».
- Adaptation des formulaires selon une fonction pure testée : empilement en compact, deux colonnes en moyen, trois en étendu ; empilement en paysage de faible hauteur ou avec grande police. Aucune dimension fixe explicite ni troncature ajoutée dans les nouveaux contenus. Les écrans utilisent les marges système du conteneur Notes existant.
- `docs/MOYENNES.md` et `AverageMethod.kt` mis à jour ensemble et contrôlés identiques.

## Fichiers modifiés ou ajoutés

Les chemins Kotlin ci-dessous sont relatifs à `app/src/main/java/fr/ecole2recte/app/`.

| Fichier | Changement |
| --- | --- |
| `domain/GradeScenario.kt` | Modèles de scénario, validation, composition des calculs matière/générale et choix pur de disposition. |
| `data/GradeScenarioStore.kt` | Sérialisation chiffrée, isolation profil/période et suppression des scénarios. |
| `AppContainer.kt` | Assemblage du stockage des scénarios. |
| `ui/AppRoot.kt` | Nettoyage des scénarios à la suppression d'un compte. |
| `ui/grades/GradeScenariosUi.kt` | Cartes d'édition, notes réelles, résultats et sauvegardes ; champs adaptatifs communs. |
| `ui/grades/GradeSimulatorUi.kt` | Raccordement des scénarios ; formulaire existant adaptatif ; estimation générale explicitement étiquetée ; matières avec moyenne officielle sans notes visibles admises. |
| `domain/AnnualProjection.kt` | Annuelle pondérée ou à plat et trois cas composés avec le simulateur. |
| `ui/grades/AnnualProjectionUi.kt` | Carte annuelle, choix du nombre d'évaluations et hypothèses explicites. |
| `domain/ClassPosition.kt` | Rang/effectif, données des barres et description accessible. |
| `ui/grades/ClassPositionUi.kt` | Histogramme horizontal avec valeurs et sémantique vocale. |
| `domain/Models.kt` | Coefficients de période et statistiques de classe optionnels. |
| `data/api/Json.kt` | Lecture défensive des statistiques de classe et des entiers positifs. |
| `data/api/Parsers.kt` | Lecture des coefficients de période et statistiques matière/générale. |
| `data/EcoleDirecteRepository.kt` | Transmission des nouveaux champs jusqu'aux modèles affichés, sans nouvel appel réseau. |
| `domain/PrivacyMode.kt` | Effacement des nouvelles statistiques dans les modèles masqués. |
| `ui/grades/GradesView.kt` | Insertion de l'annuelle et des comparaisons matière/générale. |
| `domain/AverageMethod.kt` | Texte embarqué des scénarios, de l'annuelle et de la position dans la classe. |

Autres fichiers :

- `docs/MOYENNES.md` : mêmes 12 sections que le texte embarqué.
- `app/src/test/java/fr/ecole2recte/app/domain/GradeScenarioTest.kt` : 9 tests nouveaux.
- `app/src/test/java/fr/ecole2recte/app/domain/AnnualProjectionTest.kt` : 6 tests nouveaux.
- `app/src/test/java/fr/ecole2recte/app/domain/ClassPositionTest.kt` : 4 tests nouveaux.
- `app/src/test/java/fr/ecole2recte/app/data/GradeScenarioStoreTest.kt` : 3 tests nouveaux.
- `app/src/test/java/fr/ecole2recte/app/data/GradesAnalysisFieldsTest.kt` : 3 tests nouveaux.
- `app/src/test/java/fr/ecole2recte/app/data/GradesAverageSourceTest.kt` : 1 test d'intégration supplémentaire, avec serveur local fictif.
- `RESULTAT-SIMULATEUR.md` : ce rapport.

## Tests ajoutés

26 tests écrits, **non exécutés dans ce bac à sable** :

- Scénarios : plusieurs barèmes, base affichée différente des seules notes, remplacements/retraits, dernière note retirée, plusieurs matières, générale officielle, exclusions/sous-matières, références périmées, saisies invalides et seuils de disposition.
- Stockage : relecture des modifications, isolation des profils et périodes, absence de nom en clair, remplacement sans doublon, suppression ciblée, nettoyage par compte et stockage corrompu.
- Annuelle : pondération complète, coefficients incomplets, périodes absentes, exclusion du récapitulatif annuel, coefficient nul, trois cas numériques, zéro évaluation, entrées invalides et grande police.
- Classe : rang/effectif cohérents ou absents, minimum zéro, médiane, valeurs invalides, phrase vocale française et confidentialité.
- Parseur et dépôt : coefficients connus/nuls/absents, nombres français, rang nul/non entier, statistiques générales et par matière, et conservation des nouveaux champs dans les modèles affichés.

Les tests de S1, puis de S2, puis de S3 ont été écrits avant leur implémentation initiale. Le cycle rouge/vert n'a pas pu être observé : le lanceur échoue avant même de démarrer les tests. La référence de 557 tests sans échec fournie par la mission n'a pas été revérifiée ici.

## Vérifications et sorties réelles

Commande demandée exécutée à la prise de référence et après l'écriture des tests de chaque lot :

```text
bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh
```

Résultat à chaque tentative : code de sortie **1** ; Bash termine avant Gradle. Extrait du message :

```text
bash.exe: *** fatal error - CreateFileMapping …, Win32 error 5. Terminating.
```

Commande finale également tentée avec le worktree explicite et les trois tâches :

```text
bash /c/Users/G/Dev-clean/Dev/projet/copilot-worktrees/ecole2rect/_run/test.sh /c/Users/G/.agent-hub/worktrees/ecole2recte-00a448/20261108-200455-voie5 testDebugUnitTest lintDebug assembleDebug
```

Même résultat : **code 1**, même erreur Bash. **Ni les tests unitaires, ni le lint, ni l'assemblage n'ont tourné. La compilation Kotlin/Compose reste à confirmer par le hub.** Aucun succès Gradle n'est revendiqué.

Contrôles statiques réellement exécutés :

- `git diff --check` : **code 0**, aucune sortie.
- Contrôle Python de l'identité des titres et paragraphes de `AverageMethod.kt` avec `docs/MOYENNES.md` : **code 0**, 12 sections identiques. Ce contrôle ne remplace pas le test JUnit existant.
- Contrôle des sept nouveaux fichiers de production : **code 0**, pas de caractère de remplacement ni de blanc final ; aucune dimension fixe explicite dans les trois nouveaux fichiers d'interface. Ce contrôle ne remplace pas un rendu sur appareil.

## Pas fait, limites et incertitudes

- Exécution Gradle et tests instrumentés : impossible ici, à relancer par le hub / sur appareil. Aucun APK construit ou installé.
- La projection reste conditionnelle aux données disponibles : elle ne suppose aucune moyenne pour une période sans données et ne prétend pas reconstituer une année complète manquante. Les coefficients des évaluations futures sont l'hypothèse affichée de 1.
- Une générale dont l'effet ne peut pas être calculé, notamment après retrait de toutes les notes d'une matière, est indiquée non estimable. Les sous-matières ne propagent pas un effet hypothétique à leur mère, conformément au moteur existant.
- Seuls les scénarios explicitement sauvegardés sont persistants : un brouillon non sauvegardé peut être perdu si son composable est détruit ou lors d'une rotation. Les sauvegardes sont rechargeables.
- Les variantes de champs ajoutées sont couvertes par des données fictives ; leur présence effective dépend de l'établissement. Aucune vérification sur compte réel n'a été faite.
- Pas de commit, push, merge, installation globale ni modification de fichiers d'environnement.

## À vérifier sur le téléphone

1. Ajouter deux devoirs avec barèmes 10 et 40, modifier une note réelle, la retirer puis annuler la modification ; comparer matière et générale avant/après, toujours étiquetées estimation.
2. Sauvegarder, fermer l'application, recharger et mettre à jour un scénario ; vérifier deux enfants et deux périodes, puis supprimer un scénario et un compte.
3. Vérifier l'annuelle pondérée / à plat, l'exclusion des périodes sans moyenne, les trois cas et zéro évaluation restante.
4. Vérifier les données fournies par l'établissement : rang, effectif, minimum, classe, médiane et maximum dans la générale et une matière dépliée ; rang 0 masqué.
5. Activer TalkBack : entendre le rang disponible et les valeurs de chaque barre. Activer « montrer à quelqu'un » : ne plus voir les scénarios, l'annuelle ni les statistiques.
6. Portrait et paysage sur téléphone, tablette et pliable ouvert/fermé ; police maximale du réglage « Taille du texte », clavier numérique, défilement complet, encoches et barres système. Contrôler également la perte annoncée d'un brouillon non sauvegardé lors d'une rotation.

Résumé : S1 à S3 implémentés, documentation synchronisée et 26 tests ajoutés ; validation Kotlin/Compose, tests, lint, APK et rendu réel restent à effectuer hors de ce bac à sable.
