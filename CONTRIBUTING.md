# 🤝 Guide de Contribution — InkTone

Merci de votre intérêt pour contribuer à InkTone ! 🎉

Ce document définit les règles et conventions pour contribuer au projet.

---

## 📋 Table des matières

1. [Code de conduite](#code-de-conduite)
2. [Quand contribuer](#quand-contribuer)
3. [Workflow Git](#workflow-git)
4. [Conventions de code](#conventions-de-code)
5. [Structure du projet](#structure-du-projet)
6. [Tests](#tests)
7. [Pull Requests](#pull-requests)

---

## Code de conduite

Soyez respectueux, constructif et bienveillant. Pas de tolérance pour le harcèlement ou les commentaires déplacés.

---

## Quand contribuer

Les contributions sont **bienvenues à partir de la Phase 2** (pipeline audio stable). Avant cela, le projet est en phase de prototypage et l'architecture peut encore évoluer.

En attendant :
- ⭐ Star le repo
- 🐛 Signaler des bugs dans les [Issues](https://github.com/<user>/InkTone/issues)
- 💡 Proposer des idées dans les [Discussions](https://github.com/<user>/InkTone/discussions)

---

## Workflow Git

```
main         ← Stable, release tags uniquement
  └── develop ← Branche d'intégration
       └── feature/nom-de-la-feature
       └── fix/nom-du-bug
       └── docs/nom-du-doc
```

1. **Fork** le repo
2. Créer une branche depuis `develop` : `feature/xxx` ou `fix/xxx`
3. Commiter avec des messages en **français** (verbe à l'impératif : `Ajoute`, `Corrige`, `Met à jour`)
4. Pusher et ouvrir une **Pull Request** vers `develop`
5. Attendre la review CI + humaine

---

## Conventions de code

### Kotlin

- Suivre les [Coding Conventions Kotlin officielles](https://kotlinlang.org/docs/coding-conventions.html)
- Indentation : 4 espaces
- Noms en **camelCase** (variables, fonctions) et **PascalCase** (classes, interfaces)
- `data class` pour les modèles immuables, `sealed interface` pour les types scellés

### Architecture

- **Domain layer :** Kotlin pur, aucune dépendance Android
- **Data layer :** Implémentations des interfaces du domain
- **UI layer :** Compose + ViewModel (MVI)
- Injection de dépendances via **Hilt** (`@Inject`, `@Binds`, `@Provides`)

### Nommage

```
Classes        → PascalCase    (BookRepository, ReaderViewModel)
Fonctions      → camelCase     (parseEpub(), getCurrentPosition())
Constantes     → UPPER_SNAKE   (MAX_CACHE_SIZE)
Fichiers       → PascalCase    (BookEntity.kt, ReaderScreen.kt)
Resources      → snake_case    (ic_play_arrow.xml)
```

### Commits

```
Ajoute le bridge JNI pour Sherpa-ONNX
Corrige le buffer underrun sur MediaTek
Met à jour les dépendances Compose BOM 2024.06
```

---

## Structure du projet

Voir [`architecture.md`](./architecture.md) section 7 pour l'arborescence complète.

**Règles :**
- Un fichier par classe publique
- Les interfaces de repository sont dans `domain/repository/`
- Les implémentations sont dans `data/repository/`
- Les `@Composable` sont dans `ui/`
- Les services Android (`Service`, `BroadcastReceiver`) sont dans `service/`

---

## Tests

### Tests unitaires (obligatoires pour le domain layer)

```bash
./gradlew test
```

- Framework : **JUnit 5** + **MockK** + **Turbine** (Flow testing)
- Couverture cible : > 70% pour `domain/`
- Nommage : `ClasseTestéeTest.kt` dans `src/test/`

### Tests UI (optionnels, Phase 3+)

```bash
./gradlew connectedAndroidTest
```

- Framework : **Compose Testing** + **Espresso**

---

## Pull Requests

### Checklist avant de soumettre

- [ ] Le code compile (`./gradlew assembleDebug`)
- [ ] Les tests passent (`./gradlew test`)
- [ ] Pas de warnings de lint (`./gradlew lint`)
- [ ] Le code suit les conventions (nommage, architecture)
- [ ] Les nouveaux composants sont documentés (KDoc pour les API publiques)
- [ ] Le `CHANGELOG.md` est mis à jour (section `[Unreleased]`)

### Template de PR

```markdown
## Description
[Décrivez le changement en 1-2 phrases]

## Type de changement
- [ ] Nouvelle fonctionnalité
- [ ] Correction de bug
- [ ] Refactoring
- [ ] Documentation
- [ ] Tests

## Testé sur
- [ ] Émulateur Android 14 (ARM64)
- [ ] Device physique : [modèle]

## Checklist
- [ ] Tests ajoutés / mis à jour
- [ ] CHANGELOG.md mis à jour
- [ ] Pas de régression
```

---

## 📞 Contact

- **Issues** : [github.com/issa14/InkTone/issues](https://github.com/issa14/InkTone/issues)
- **Discussions** : [github.com/issa14/InkTone/discussions](https://github.com/issa14/InkTone/discussions)
