# projet_mescontact_v4 — Contacts persistants SQLite + Préférences (TP Android V4)

Évolution de la V3 : les contacts sont stockés dans une **base SQLite `contacts.db`**
(API native `SQLiteOpenHelper` / `SQLiteDatabase` / `ContentValues` / `Cursor`, sans Room),
avec **CRUD complet**, et un **écran Paramètres** (AndroidX Preference) qui personnalise la liste.
Kotlin • XML • Fragments • RecyclerView • Coroutines • Google Maps.

## Ce qui change par rapport à la V3
| V3 | V4 |
|---|---|
| Liste en mémoire, perdue à la fermeture | Table `contacts` dans `contacts.db`, données conservées après fermeture/redémarrage |
| Bouton + dans la barre | **FAB « + »** → détail en mode création (id = 0) |
| — | Bouton **Supprimer** + confirmation `AlertDialog` |
| Une toolbar par fragment | **Barre d'application unique** dans `MainActivity`, icône ⚙ **Paramètres** |
| — | `ParametresFragment` : taille de police, couleur du texte, appel depuis la liste (+ bonus : ordre de tri) |
| — | Bouton **Appeler** sur chaque ligne (si activé), `ACTION_DIAL` |
| — | Bonus : colonne **favori** ajoutée par **migration v1 → v2** (`onUpgrade`), favoris en tête |

## Schéma de la base
```sql
CREATE TABLE contacts (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    nom TEXT NOT NULL,
    prenom TEXT NOT NULL,
    telephone TEXT NOT NULL,      -- TEXT : « 0… », « +216 », espaces
    email TEXT,
    photo_uri TEXT,               -- chemin d'une copie JPEG dans filesDir
    latitude REAL,                -- NULL = pas de position
    longitude REAL
);
-- version 2 (bonus) :
ALTER TABLE contacts ADD COLUMN favori INTEGER NOT NULL DEFAULT 0;
```
`DATABASE_VERSION = 2`. Une base neuve exécute la création v1 puis la migration (même schéma final) ;
une base v1 existante reçoit seulement `ALTER TABLE` dans `onUpgrade` → **aucun contact perdu**.

## Architecture (`data/` = accès aux données, `ui/` = écrans)
| Classe | Rôle |
|---|---|
| `MainActivity` | Barre d'application + `FragmentContainerView`, navigation `replace()`/`addToBackStack()`, menu Paramètres |
| `ui/ListeContactsFragment` | Liste, recherche, FAB +, bouton Appeler par ligne ; relit préférences et base dans `onResume()` |
| `ui/DetailContactFragment` | Création / lecture / modification / suppression ; Appeler, E-mail, Galerie, Caméra, Carte |
| `ui/CarteContactFragment` | Google Maps + marqueur (inchangé) |
| `ui/ParametresFragment` | `PreferenceFragmentCompat` + `res/xml/preferences.xml` + `res/values/arrays.xml` |
| `data/ContactContract` | Nom de la table et des colonnes en constantes |
| `data/ContactDbHelper` | `SQLiteOpenHelper` **singleton** (`applicationContext`), `onCreate` (+ 6 contacts de démo dont **2 sans GPS** et « O'Neil »), `onUpgrade` |
| `data/ContactDao` | **Seule classe avec du SQL** : `insert`, `update`, `delete`, `getAll`, `getById`, `search`, `count` |
| `data/ContactViewModel` | Partagé ; lance le DAO dans `viewModelScope` + `withContext(Dispatchers.IO)`, recharge la liste après chaque écriture |
| `data/PreferencesManager` | Lecture centralisée des préférences → `DisplayPrefs(tailleSp: Float, couleur: Int, appelDepuisListe: Boolean)` |
| `data/SortOrder`, `data/SearchQuery` | Clauses ORDER BY autorisées ; préparation des arguments LIKE |

### Stratégie de threads
- Toutes les méthodes du DAO sont `@WorkerThread` et ne sont appelées que dans `withContext(Dispatchers.IO)`.
- Liste : `viewModel.refresh()` (lancé dans `viewModelScope`, l'appel précédent est annulé à chaque frappe).
- Détail : `viewLifecycleOwner.lifecycleScope.launch { viewModel.loadContact(id) }` ; boutons désactivés + barre de progression pendant le chargement / l'écriture.
- Écritures (`save`, `delete`) dans `viewModelScope` : elles se terminent même si l'écran tourne.
- L'import de photo (décodage + compression JPEG) est aussi fait sur `Dispatchers.IO`.

### Choix du filtrage : **en SQL**
`ContactDao.search(texte)` utilise `LIKE ?` sur nom, prénom, « prénom nom », « nom prénom » et téléphone,
plus une comparaison sur les seuls chiffres du téléphone (« 20123 » trouve « +216 20 123 456 »).
- La liste affichée vient **toujours** de la base (pas de copie en mémoire à synchroniser après une écriture).
- Le tri `COLLATE NOCASE` est appliqué par la même requête.
- La saisie part dans `selectionArgs` : « O'Neil » ou « L'Ariana » ne cassent rien ; `%` et `_` sont échappés (`ESCAPE '\'`).
- Limite connue : `LIKE` de SQLite n'ignore la casse que pour les lettres ASCII (« é » ≠ « É »), ce qui reste
  acceptable pour des noms saisis en alphabet latin. Avec quelques milliers de contacts, le filtrage en mémoire
  aurait aussi été possible, mais il aurait fallu tenir une copie à jour en parallèle de la base.

### Photos
La galerie renvoie une URI temporaire qui peut devenir illisible après redémarrage. L'image est donc **copiée**
(réduite, orientation EXIF corrigée) dans `filesDir/photos/`, et c'est ce **chemin** qui est enregistré dans `photo_uri`.
L'ancienne photo est supprimée du disque quand elle est remplacée et enregistrée, ou quand le contact est supprimé ;
les photos d'une édition annulée sont supprimées.

### Préférences
| Clé | Composant | Valeurs | Défaut | Effet |
|---|---|---|---|---|
| `taille_police` | ListPreference | 14 / 16 / 20 sp | 16 | nom, prénom, téléphone (liste et détail) |
| `couleur_preferee` | ListPreference | Noir `#000000`, Bleu `#1565C0`, Vert `#2E7D32`, Rouge `#C62828`, Violet `#6A1B9A` | Noir | nom et prénom (liste et détail) |
| `appel_depuis_liste` | SwitchPreferenceCompat | oui / non | non | bouton Appeler par ligne (`GONE` si désactivé) |
| `ordre_tri` (bonus) | ListPreference | favoris d'abord / nom / prénom | favoris d'abord | clause ORDER BY |

- Stockées dans les **SharedPreferences** par défaut (fichier XML clé/valeur), pas dans SQLite.
- `useSimpleSummaryProvider="true"` affiche la valeur choisie ; `PreferenceManager.setDefaultValues()` au premier lancement.
- **Application immédiate** : au retour, `ListeContactsFragment.onResume()` relit `PreferencesManager.display()`,
  le passe à `adapter.setDisplayPrefs()` (qui redessine les lignes si quelque chose a changé) puis relit la base
  avec le nouvel ordre de tri.
- Les 5 couleurs sont foncées : contraste ≥ 4,5:1 sur le fond blanc des cartes. L'application utilise un thème clair uniquement.
- Bouton Appeler d'une ligne : `ACTION_DIAL` (aucune permission). C'est un bouton distinct qui consomme son clic,
  donc la fiche ne s'ouvre pas. Numéro vide et absence d'application téléphone sont gérés (Toast).

## Clé Google Maps (jamais publiée)
Comme en V3 : ajouter `MAPS_API_KEY=...` dans **`local.properties`** (ignoré par Git), voir `local.properties.example`.
Restreindre la clé au package `com.example.projet_mescontact_v4` + SHA-1. Sans clé, la carte affiche un message.

## Lancer
1. Ouvrir `projet_mescontact_v4` dans Android Studio, synchroniser Gradle, ajouter la clé Maps.
2. Lancer sur un émulateur « Google Play ».
3. **App Inspection → Database Inspector** → `contacts.db` → table `contacts` pour vérifier le contenu.
4. Tests unitaires (`app/src/test`) : échappement des recherches, conversion des préférences, validation GPS.

> Si `contacts.db` existe déjà d'une ancienne installation de développement avec un autre schéma :
> désinstaller l'application (ou incrémenter `DATABASE_VERSION` et compléter `onUpgrade`).

## Plan de tests
| # | Test | Attendu |
|---|---|---|
| 1 | 1er lancement puis 2e lancement | 6 contacts de démo, **pas** insérés une 2e fois |
| 2 | +, créer « Zarrouk Ines » → retour | Apparaît à sa place dans l'ordre alphabétique |
| 3 | Modifier un contact, fermer l'appli (glisser hors des récentes), relancer | Modification conservée |
| 4 | Supprimer → Annuler dans la boîte / Supprimer | Rien ne change / contact absent, même après relance |
| 5 | Nom « L'Ariana », filtre « o'n » | Enregistré et trouvé, aucune erreur SQL |
| 6 | Enregistrer sans nom, prénom ou téléphone | Erreur sous le champ, rien n'est écrit (vérifier dans le Database Inspector) |
| 7 | Changer la photo, redémarrer l'appareil | Photo toujours affichée |
| 8 | Latitude « abc », « 95 », un seul champ rempli | Erreur, rien d'invalide en base ; la carte affiche un message |
| 9 | Chaque taille et chaque couleur | Appliquées dans la liste et le détail dès le retour |
| 10 | Activer / désactiver l'appel depuis la liste | Bouton visible / masqué ; ouvre le composeur avec le bon numéro sans ouvrir la fiche |
| 11 | Relancer l'appli | Les préférences sont conservées |
| 12 | Rotation sur la liste, le détail en cours de saisie, les Paramètres ; Retour depuis chaque écran | Pas de doublon, saisie conservée |
| 13 | Bonus : cocher « favori » | Le contact passe en tête (tri « favoris d'abord ») |

## Questions de compréhension
1. **onCreate / onUpgrade.** `onCreate` est appelé au premier accès à la base (`getWritableDatabase()` /
   `getReadableDatabase()`) **seulement si le fichier `contacts.db` n'existe pas**. `onUpgrade` est appelé quand
   `DATABASE_VERSION` est supérieure à la version enregistrée dans le fichier. Si l'on modifie `onCreate` sans changer la
   version, **rien ne se passe** sur un appareil où la base existe déjà : l'ancien schéma reste, ce qui peut provoquer une
   erreur « no such column ». Il faut désinstaller l'application ou incrémenter la version et écrire `onUpgrade`.
2. **Arguments `?`.** La valeur est transmise séparément du texte SQL : elle ne peut jamais modifier la requête
   (injection SQL) et n'a pas besoin d'être échappée. Exemple : `"... WHERE nom = '" + saisie + "'"` avec la saisie
   `O'Neil` donne `WHERE nom = 'O'Neil'` → erreur de syntaxe ; avec `' OR '1'='1`, la requête renvoie toute la table.
3. **Valeurs de retour.** `insert()` renvoie le nouvel `_id` (ou **-1** en cas d'échec) ; `update()` et `delete()` renvoient
   le **nombre de lignes** touchées. Dans l'appli : -1 → « Échec de l'enregistrement » ; 0 → « Contact introuvable » ;
   1 → Toast de succès et retour à la liste (classe `DbResult`).
4. **Thread principal.** Les accès disque peuvent être lents et bloqueraient l'interface (saccades, erreur ANR après
   environ 5 s). Toutes les requêtes sont faites dans `withContext(Dispatchers.IO)`, lancées depuis `viewModelScope`
   ou `lifecycleScope` ; le résultat est publié sur le thread principal (LiveData ou callback).
5. **Helper unique + applicationContext.** Une seule instance = une seule connexion, ce qui évite les conflits
   d'écriture (« database is locked ») et l'ouverture répétée du fichier. `applicationContext` vit aussi longtemps que
   l'application : garder un `Context` d'Activity ou de Fragment dans un singleton provoquerait une fuite mémoire
   après chaque rotation.
6. **SharedPreferences ou SQLite.** SharedPreferences : quelques réglages simples clé/valeur, lus en entier, sans
   requête → **préférences**. SQLite : données structurées, nombreuses et répétées (une ligne par contact), à rechercher,
   trier, modifier une par une, avec contraintes → **contacts**.
7. **Application immédiate.** `ListeContactsFragment.onResume()` (appelé au retour des Paramètres) relit
   `PreferencesManager.display()`, appelle `adapter.setDisplayPrefs()` puis `notifyItemRangeChanged`, et relit la base
   avec l'ordre de tri choisi.
8. **ACTION_DIAL / ACTION_CALL.** `ACTION_DIAL` ouvre le composeur avec le numéro prérempli ; l'utilisateur confirme
   l'appel → **aucune permission**. `ACTION_CALL` lance l'appel directement et exige la permission dangereuse
   `CALL_PHONE`, demandée à l'exécution.
9. **Désinstallation / effacement des données.** Les deux suppriment le dossier privé de l'application : la base
   (`databases/contacts.db`), les préférences (`shared_prefs/`) et les photos (`files/`). Au lancement suivant, `onCreate`
   recrée la base avec les contacts de démo et les préférences reprennent leurs valeurs par défaut. Exception :
   avec `allowBackup="true"`, une sauvegarde Google peut restaurer les données lors d'une réinstallation.

## Auteur
Triki Mohamed Amine – L3MDW2
