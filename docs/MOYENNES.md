# Comment la moyenne est calculée

Ce document décrit le moteur AverageEngine, l'arbitrage AverageReconciliation et le simulateur. Le même texte est affiché dans l'application.

## Ce que l'application affiche

Une moyenne est dite « officielle » quand ÉcoleDirecte la fournit et qu'aucun retard n'est établi. Un écart inexpliqué laisse raison au bulletin.

Quand ÉcoleDirecte ne fournit pas la moyenne, l'application la calcule et l'écrit « calculée par l'app ».

Si des notes montrent que le bulletin est en retard, l'application affiche « Recalculée · bulletin … », conserve le chiffre officiel et indique les notes concernées. Dans Notes, toucher l'explication ouvre la note.

## D'où vient la moyenne générale

- 1. Officielle : le champ moyenneGenerale de la période, ou celui d'ensembleMatieres quand la période n'en a pas, sauf retard établi.

- 2. Sinon, moyenne des moyennes officielles de matière de la période, pondérée par le coefficient de matière : 1 quand il est inconnu ; un coefficient 0 exclut la matière. Les sous-matières sont exclues, car elles sont déjà incluses dans leur mère. Le marqueur groupeMatiere ne change pas cette pondération.

- 3. Sinon, moyenne des notes comptées de la période, pondérées par leur coefficient, sans les coefficients de matière. Ce repli s'applique aussi quand toutes les matières sont exclues.

- 4. Sinon, l'application affiche « – ».

Si une matière est en retard, la générale est recalculée à partir des valeurs actualisées des matières et de leurs coefficients, sans sous-matières ; à défaut de matières admissibles, à partir des notes.

## D'où vient la moyenne d'une matière

La moyenne officielle de la matière est conservée, sauf retard établi. À défaut de moyenne officielle, l'application calcule la moyenne pondérée des notes visibles de la période.

Les notes sont rapprochées par code matière, puis par nom sans accents, casse ni espaces superflus. Les codes et sous-codes distincts conservent les matières de même libellé. Une sous-matière est indentée sous sa mère quand leur code commun permet de la retrouver.

## Quand le bulletin est en retard

La date ensembleMatieres.dateCalcul est lue si elle existe. Une note comptée datée après ce jour prouve le retard. Une note du même jour exige une confirmation par le calcul ci-dessous.

Sans dateCalcul, l'application retire les notes les plus récentes successivement : si la moyenne officielle réapparaît à 0,055 point près, les notes retirées expliquent le retard. Une note sans date empêche cette déduction. À date égale, les identifiants départagent les notes pour cette recherche ; la concordance de moyenne reste nécessaire.

Si aucun retard n'est établi, même avec un écart entre le calcul et le bulletin, l'officielle reste affichée. Les absences, notes non significatives et coefficients nuls ne prouvent aucun retard.

## Comment l'application calcule

- Chaque note est ramenée sur 20 : valeur ÷ barème × 20 (un barème absent vaut 20).

- Chaque note est multipliée par son coefficient (1 quand il est absent) ; on additionne, puis on divise par la somme des coefficients.

- Ne comptent pas : les absences et valeurs non numériques (Abs, Disp, NE, NN, NR…), les notes non significatives et les notes de coefficient nul.

- Une note sans période connue n'entre dans le calcul d'aucune période. Aucun zéro n'est inventé et aucun arrondi n'est fait en cours de calcul.

- Plus > Apparence > Décimales des moyennes propose 1 ou 2 décimales, avec 2 par défaut comme le bulletin. Ce réglage concerne les moyennes, la classe et ses extrêmes, les courbes, le simulateur et le widget Notes ; les notes individuelles gardent leur présentation.

## Le choix de la période

Par défaut, la période affichée est celle qui contient la date du jour et qui a des données ; à défaut la période marquée « en cours », puis la dernière commencée qui a des données.

La période « Année » n'est pas proposée dans les puces : on choisit un trimestre ou un semestre.

Toucher une puce change la période affichée sans nouvelle demande à ÉcoleDirecte : le bulletin est déjà là.

## Pourquoi la moyenne calculée peut être différente

La moyenne calculée peut être plus basse ou plus haute. Le retard du bulletin peut expliquer un écart : il est signalé uniquement quand les dates ou une concordance de moyenne le confirment.

- L'établissement peut utiliser des coefficients, bonus, options ou règles d'exclusion que l'application ne connaît pas.

- Certaines notes ne sont pas visibles : notes masquées, devoirs non publiés, compétences sans note.

- Les arrondis du bulletin peuvent différer, notamment si les matières sont arrondies avant combinaison.

- Une période absente de la réponse n'a ni moyenne ni notes dans l'application.

## Tendance, courbes et comparaison à la classe

- La tendance compare la période affichée à la dernière période précédente avec une moyenne, à partir d'un écart de 0,05. Une flèche apparaît seulement entre deux officielles, ou entre deux calculées avec « ≈ ». Mélanger les origines ne donne aucune flèche.

- La courbe générale par période relie les moyennes officielles fournies par ÉcoleDirecte ; une période sans moyenne officielle n'a pas de point.

- La courbe d'une matière montre par défaut la période affichée : moyenne pondérée cumulée des notes, calculée par l'app. Le dernier point égale la moyenne calculée de cette période, qui peut différer de l'officielle. Les notes sans date sont regroupées en un point « Sans date ». L'interrupteur « Toute l'année » rétablit la vue annuelle.

- La moyenne de la classe est recopiée d'ÉcoleDirecte. L'écart d'une note à la classe est calculé dans le barème du devoir.

## Le simulateur

« Avant » est la moyenne affichée de la matière pour la période choisie, officielle ou calculée par l'app. W est la somme des coefficients des notes comptées de cette matière.

Avec une note x sur le barème choisi et un coefficient c, la projection sur 20 vaut (moyenne affichée × W + note sur 20 × c) ÷ (W + c). Le barème vaut 20 par défaut ; il doit être positif et la note doit être comprise entre 0 et ce barème.

« Quelle note pour atteindre X » calcule (X × (W + c) − moyenne affichée × W) ÷ c, puis convertit dans le barème choisi et arrondit au dixième supérieur. Au-delà du barème, l'objectif est hors d'atteinte en une évaluation ; un résultat nul ou négatif signifie qu'il est déjà acquis.

L'effet sur la moyenne générale remplace la matière par sa projection dans la pondération par coefficients de matière. Si la générale est officielle, ou si sa pondération ne peut pas être reproduite à 0,055 point près, l'effet est une estimation : générale + (matière projetée − matière affichée) × coefficient de matière ÷ somme des coefficients admissibles. Une sous-matière ou une matière exclue ne donne pas d'effet général.

Les scénarios combinent plusieurs devoirs hypothétiques, chacun avec sa note, son barème et son coefficient. Une note réelle comptée peut être remplacée ou retirée dans le scénario : ses points sur 20 et son coefficient sont soustraits à la base affichée × W, puis les éventuels nouveaux points et coefficients sont ajoutés. Les effets de plusieurs matières se cumulent sur la générale. Tous les résultats restent étiquetés « estimation ».

Retirer toutes les notes d'une matière ne crée pas un zéro : la matière et l'effet général deviennent non estimables. Si l'écart entre la moyenne affichée et les notes visibles rend le résultat incohérent (hors de 0 à 20), le scénario est refusé. Une note disparue ou une matière absente de la période invalide aussi le scénario : aucune modification n'est ignorée silencieusement.

Un scénario nommé sauvegarde uniquement ses modifications sur le téléphone, chiffrées, pour le profil et la période choisis. Il peut être chargé, modifié ou supprimé. Il ne modifie aucune note chez ÉcoleDirecte. Changer de profil ou de période isole le brouillon ; supprimer le compte efface ses scénarios. Le mode « montrer à quelqu'un » masque le simulateur.

## Moyenne annuelle et projection

L'annuelle calculée par l'app combine les moyennes des trimestres ou semestres disponibles, sans compter à nouveau la période « Année ». La période affichée utilise la moyenne affichée, officielle ou recalculée. Les périodes sans moyenne et celles de coefficient 0 sont exclues, jamais remplacées par zéro.

Quand tous les coefficients des périodes retenues sont connus et positifs, l'annuelle est pondérée par ces coefficients. Sinon toutes les périodes retenues ont le même poids et l'écran indique « Moyenne à plat : coefficients de période inconnus ou incomplets ». Le nombre de périodes utilisées est affiché : une annuelle partielle n'est pas un bulletin annuel complet.

La projection de fin d'année choisit un nombre entier de 0 à 1 000 évaluations restantes par matière de la période affichée. Chaque évaluation hypothétique est de coefficient 1 et sur 20. Meilleur cas : 20 à chacune ; pire cas : 0 ; cas probable : maintien de la moyenne actuelle de chaque matière. Zéro évaluation conserve les valeurs actuelles.

Les trois cas utilisent le moteur du simulateur pour la période et la générale, puis remplacent cette période dans l'annuelle. Les autres périodes connues restent inchangées ; les périodes sans moyenne ne font l'objet d'aucune hypothèse. Les résultats sont des estimations sur les périodes disponibles, jamais des prédictions des notes futures ni de l'annuelle officielle.

## Position dans la classe

Le rang et l'effectif sont affichés uniquement quand l'établissement les fournit comme entiers positifs. S'ils sont tous deux connus, le rang doit être inférieur ou égal à l'effectif. Un rang 0, une valeur absente ou invalide est masqué ; un effectif connu peut être affiché seul et aucun rang n'est estimé à partir d'une moyenne.

La comparaison de la matière et de la générale utilise un histogramme horizontal sur une échelle commune de 0 à 20 : minimum, moyenne de classe, moi, médiane et maximum quand ces valeurs sont fournies. Chaque barre porte son nom et sa valeur ; une donnée absente n'est pas inventée. La médiane vient du champ mediane ou moyenneMediane de l'établissement.

Une phrase accessible au lecteur d'écran décrit le rang disponible et toutes les valeurs présentes. La couleur ne porte jamais seule l'information. Le mode « montrer à quelqu'un » masque aussi le rang, l'effectif, les statistiques et la projection annuelle.

## En cas de doute

La moyenne du bulletin de l'établissement fait foi pour ses décisions. Le recalcul et le simulateur sont des estimations fondées sur les données visibles ; un écart inexpliqué ne remplace jamais une officielle.
