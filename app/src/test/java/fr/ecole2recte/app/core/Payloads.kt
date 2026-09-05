package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.protocol.Encoding

/**
 * Réponses ÉcoleDirecte enregistrées, réduites à ce qui compte et anonymisées.
 *
 * Elles servent de référence de format : si l'API change, ce sont ces échantillons qu'il
 * faudra remplacer, et les tests diront précisément ce que la nouvelle forme casse.
 */
object Payloads {

    val LOGIN_SUCCESS = """
        {
          "code": 200,
          "token": "jeton-de-session",
          "data": {
            "accounts": [
              {
                "id": 4242,
                "typeCompte": "E",
                "prenom": "Martin",
                "nom": "Leroy",
                "nomEtablissement": "Lycée Victor Hugo",
                "profile": { "classe": { "code": "TA", "libelle": "Terminale A" } }
              }
            ]
          }
        }
    """.trimIndent()

    val LOGIN_TWO_FACTOR = """{ "code": 250, "message": "Double authentification requise" }"""

    val LOGIN_INVALID = """{ "code": 505, "message": "Identifiant ou mot de passe invalide" }"""

    val SESSION_EXPIRED = """{ "code": 525, "message": "Token invalide" }"""

    val DOUBLE_AUTH_QUESTION = """
        {
          "code": 200,
          "data": {
            "question": "${Encoding.base64Encode("Quel est ton mois de naissance ?")}",
            "propositions": [
              "${Encoding.base64Encode("Mars")}",
              "${Encoding.base64Encode("Juin")}",
              "${Encoding.base64Encode("Septembre")}"
            ]
          }
        }
    """.trimIndent()

    val DOUBLE_AUTH_ANSWER = """
        { "code": 200, "data": { "cn": "identifiant-question", "cv": "valeur-reponse" } }
    """.trimIndent()

    val TIMETABLE = """
        {
          "code": 200,
          "data": [
            {
              "id": 11,
              "text": "PHILO",
              "matiere": "PHILOSOPHIE",
              "codeMatiere": "PHILO",
              "typeCours": "COURS",
              "start_date": "2026-09-07 10:15",
              "end_date": "2026-09-07 12:00",
              "salle": "B204",
              "prof": "Mme Laurent",
              "isAnnule": false
            },
            {
              "id": 12,
              "matiere": "MATHEMATIQUES",
              "start_date": "2026-09-07 13:30",
              "end_date": "2026-09-07 15:00",
              "salle": "C011",
              "prof": "M. Petit",
              "isAnnule": true
            },
            { "id": 13, "matiere": "SANS HEURE" }
          ]
        }
    """.trimIndent()

    val HOMEWORK_LIST = """
        {
          "code": 200,
          "data": {
            "2026-09-07": [
              {
                "idDevoir": 901,
                "matiere": "Philosophie",
                "codeMatiere": "PHILO",
                "aFaire": true,
                "donneLe": "2026-09-01",
                "effectue": false,
                "interrogation": false
              }
            ],
            "2026-09-08": [
              {
                "idDevoir": 902,
                "matiere": "Mathématiques",
                "aFaire": true,
                "donneLe": "2026-09-02",
                "effectue": true,
                "interrogation": true
              }
            ]
          }
        }
    """.trimIndent()

    val HOMEWORK_DAY = """
        {
          "code": 200,
          "data": {
            "date": "2026-09-07",
            "matieres": [
              {
                "id": 901,
                "matiere": "Philosophie",
                "codeMatiere": "PHILO",
                "nomProf": "Mme Laurent",
                "interrogation": false,
                "aFaire": {
                  "idDevoir": 901,
                  "donneLe": "2026-09-01",
                  "effectue": false,
                  "contenu": "${Encoding.base64Encode("<p>Dissertation&nbsp;: <b>la libert&eacute;</b></p><ul><li>Introduction</li></ul>")}",
                  "documents": [ { "id": 7, "libelle": "sujet.pdf", "type": "FICHIER" } ]
                }
              },
              {
                "id": 903,
                "matiere": "Histoire-Géo",
                "contenuDeSeance": { "contenu": "" }
              }
            ]
          }
        }
    """.trimIndent()

    val GRADES = """
        {
          "code": 200,
          "data": {
            "periodes": [
              {
                "idPeriode": "A001",
                "periode": "Trimestre 1",
                "dateDebut": "2026-09-01",
                "dateFin": "2026-12-15",
                "ensembleMatieres": {
                  "moyenneGenerale": "15,80",
                  "moyenneClasse": "12,50",
                  "disciplines": [
                    {
                      "discipline": "MATHEMATIQUES",
                      "codeMatiere": "MATHS",
                      "moyenne": "17,50",
                      "moyenneClasse": "13,20",
                      "coef": "5",
                      "professeurs": [ { "nom": "M. Petit" } ]
                    },
                    {
                      "discipline": "Pôle scientifique",
                      "groupeMatiere": true,
                      "moyenne": "16,00"
                    }
                  ]
                }
              }
            ],
            "notes": [
              {
                "id": 5001,
                "devoir": "DS n°1",
                "codePeriode": "A001",
                "libelleMatiere": "MATHEMATIQUES",
                "coef": "2",
                "noteSur": "20",
                "valeur": "17,5",
                "moyenneClasse": "13,2",
                "date": "2026-09-20",
                "nonSignificatif": false
              },
              {
                "id": 5002,
                "devoir": "Interro",
                "codePeriode": "A001",
                "libelleMatiere": "PHILOSOPHIE",
                "coef": "1",
                "noteSur": "10",
                "valeur": "7",
                "date": "2026-09-18",
                "nonSignificatif": true
              }
            ]
          }
        }
    """.trimIndent()

    val MESSAGES = """
        {
          "code": 200,
          "data": {
            "messages": {
              "received": [
                {
                  "id": 3001,
                  "read": false,
                  "subject": "Réunion parents-professeurs",
                  "content": "",
                  "date": "2026-09-04 09:42:00",
                  "from": { "civilite": "Mme", "prenom": "Claire", "nom": "Legrand" },
                  "files": []
                },
                {
                  "id": 3002,
                  "read": true,
                  "subject": "Correction du devoir maison",
                  "date": "2026-09-02 17:05:00",
                  "from": { "civilite": "M.", "nom": "Petit" }
                }
              ]
            }
          }
        }
    """.trimIndent()

    val MESSAGE_BODY = """
        {
          "code": 200,
          "data": {
            "id": 3001,
            "subject": "Réunion parents-professeurs",
            "date": "2026-09-04 09:42:00",
            "read": true,
            "from": { "civilite": "Mme", "prenom": "Claire", "nom": "Legrand" },
            "content": "${Encoding.base64Encode("<p>Bonjour,</p><p>La r&eacute;union aura lieu jeudi.</p>")}",
            "files": [ { "id": 12, "libelle": "convocation.pdf" } ]
          }
        }
    """.trimIndent()
}
