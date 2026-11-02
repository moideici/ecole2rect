# ecole2rect — maquette web

Maquette statique de l'accueil d'ecole2recte, un client ÉcoleDirecte : prochain cours,
journée, dernières notes, devoirs, messages, et les deux écrans de connexion.

**C'est une maquette.** Toutes les données sont écrites en dur, aucune requête ne part vers
ÉcoleDirecte, et les formulaires n'envoient ni n'enregistrent rien.

## Ouvrir

Aucune dépendance ni compilation : ouvrir `index.html` dans un navigateur, ou servir le
dossier.

```sh
python -m http.server 8080
```

## Fichiers

| Fichier | Rôle |
|---|---|
| `index.html` | la page et ses deux modales (connexion, question de sécurité) |
| `styles.css` | la charte : grille de cartes, barre latérale, version téléphone sous 720 px |
| `app.js` | la navigation par vue (suit l'adresse `#accueil`, `#notes`…), la personnalisation de l'accueil, les modales |

## Ce qui fonctionne

- **Navigation** : chaque vue a son adresse ; les liens des cartes, le rechargement et le
  bouton retour affichent la bonne vue.
- **Personnaliser** : les cartes se réordonnent au glisser-déposer, ou au clavier avec les
  flèches une fois la carte sélectionnée. « Terminé » garde l'ordre dans le navigateur
  (`localStorage`).
- **Modales** : ouvertes depuis « Changer de profil » ou « Réglages », fermées par ×,
  Échap ou un clic à côté. La question de sécurité est présentée comme ÉcoleDirecte la
  pose : un choix parmi des propositions.

Les boutons Rechercher, Notifications, « ••• » et « Ajouter un widget » sont décoratifs.
