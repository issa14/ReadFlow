# 📋 ReadFlow — Résumé Exécutif & Plan Correction

**Date** : 2026-07-18  
**Score Audit** : 6.2/10  
**Status** : ⚠️ **NON prête pour Play Store**

---

## 🚨 Situation Critique

| Problème | Sévérité | Impact | ETA Fix |
|---|---|---|---|
| **Race condition AudioTrack (SIGSEGV)** | 🔴 BLOQUANT | Crash aléatoire au stop | 1-2j |
| **Deadlock synthèse ONNX (ANR)** | 🔴 BLOQUANT | Lecture figée 5-10s | 1j |
| **ShortArray allocation (GC)** | 🔴 BLOQUANT | Stuttering toutes les phrases | 2-3j |
| **0% test coverage** | 🔴 BLOQUANT | Zéro fiabilité regression | 3-5j |
| **ProGuard rules incomplete** | 🟠 WARNING | Crash release build | 1j |
| **Baseline Profile minimal** | 🟠 WARNING | Cold start 400ms vs 300ms | 1-2j |

---

## 📊 État Actuel

```
Métrique                    | Actuel  | Cible   | Gap
────────────────────────────────────────────────
Fichiers Kotlin             | 106     | 120     | ✅ OK
Tests                       | 6       | 20+     | 🔴 -70%
Couverture                  | ~12%    | ≥70%    | 🔴 -58%
Bugs critiques identifiés   | 3       | 0       | 🔴 -3
Architecture (Clean)        | ✅ OK   | ✅ OK   | ✅ OK
CI/CD                       | ✅ OK   | ✅ OK   | ✅ OK
```

---

## 🎯 Priorités d'Correction

### Phase 1 : Déblocage (2-4 jours)

```
Jour 1 : Race condition AudioTrack
  - Ajouter writeLock avant vérification état
  - Test de regression avec rapid stop/play
  - Mesure : zéro crash sur 1000 iterations

Jour 1-2 : Timeout synthèse
  - withTimeoutOrNull(2000) sur ONNX/Edge synthesis
  - Error handling + skip avec feedback utilisateur
  - Test avec mock synthèse lente

Jour 2-3 : ObjectPool<ShortArray>
  - Pool capacity = 3 buffers
  - Réutilisation par chunks
  - Benchmark memory allocation (avant/après)
```

### Phase 2 : Tests & QA (3-5 jours)

```
Jour 3-4 : Ajouter 30+ tests
  - GaplessAudioPlayerTest (race conditions)
  - ReaderViewModelTest (error paths, SavedStateHandle)
  - PlaybackOrchestratorTest (timeout, consecutive errors)
  - TtsRepositoryImplTest (ONNX fallback)

Jour 4-5 : Vérifier couverture
  - Cible : 70% domain/, 50% service/
  - ./gradlew test jacocoTestReport
```

### Phase 3 : Release-Ready (1-2 jours)

```
Jour 5-6 : ProGuard + Baseline Profile
  - Rules complètes pour Readium, Media3
  - Baseline Profile couverture > 90%
  - Release build sans erreur
```

---

## 💡 Recommandations Clés

### ✅ À Faire (bloquant)

```
[ ] C1 : Race condition AudioTrack (écrit writeLock)
    └─ Validation: 100+ rapid stop/play sans crash

[ ] C2 : Timeout synthèse (2s per phrase)
    └─ Validation: ONNX crash no longer ANR

[ ] C3 : ObjectPool<ShortArray>
    └─ Validation: GC pressure divisée par 10

[ ] Tests : +30 cas critiques
    └─ Validation: 70% coverage domain/, 50% service/

[ ] ProGuard : Règles complètes
    └─ Validation: Release build successful

[ ] Baseline Profile : 90%+ hotspots
    └─ Validation: Cold start mesuré < 300ms
```

### 🟠 À Améliorer (après release)

```
- Recomposition isolation (ReaderScreen)
- Audit complet SharedPreferences → DataStore
- Double-check Memory calculation AudioCacheManager
- ViewModel refactoring (logique métier → UseCase)
- Process Death + Backup/Restore complet
```

### ✅ Déjà Bien

```
✓ Architecture Clean 4 couches
✓ MVI Pattern appliqué correctement
✓ Room + FTS5 implémenté
✓ Hilt DI structuré
✓ CI/CD GitHub Actions
✓ Baseline Profile existant
✓ CONTRIBUTING guide présent
```

---

## 📈 Métriques Post-Correction

| Métrique | Avant | Après | Gain |
|---|---|---|---|
| Cold start | ~400ms | <300ms | -25% |
| GC pause max | ~200ms | <50ms | -75% |
| Allocations/heure | ~720 | ~70 | -90% |
| Test coverage | ~12% | ~70% | +58% |
| Crash rate | TBD | <0.1% | — |

---

## 📅 Timeline Récapitulatif

```
Jour 1  (2026-07-19)
  ├─ C1: Race condition + test
  └─ C2: Timeout synthèse

Jour 2  (2026-07-20)
  ├─ C3: ObjectPool<ShortArray>
  └─ W1: Memory sizeOf()

Jour 3-4 (2026-07-21-22)
  ├─ Tests: +30 cases
  └─ Coverage report

Jour 5  (2026-07-23)
  ├─ ProGuard complete
  └─ Baseline Profile

Jour 6  (2026-07-24)
  ├─ Final validation
  ├─ Release build test
  └─ ✅ READY FOR STORE
```

---

## 🔧 Prompts à Utiliser

1. **Pour Copilot Chat** : Utiliser `copilot-prompt-corrections.md`
2. **Pour Context** : Fournir `AUDIT_QUALITE_READFLOW.md` complet
3. **Pour Tracking** : Mettre à jour `CHANGELOG.md` par commit

---

## ✅ Checklist Pré-Store

```
Avant de soumettre à Play Store :

CORRECTIFS CRITIQUES
[ ] C1 race condition fix + test
[ ] C2 timeout synthèse + error handling
[ ] C3 ObjectPool ShortArray + benchmark
[ ] C4 30+ tests (70% coverage)

CONFIGURATION RELEASE
[ ] ProGuard rules complet (zéro obfuscation issue)
[ ] Baseline Profile validé > 90%
[ ] Signing config en place (keystore.properties)
[ ] Version code incrémenté (0.1.0 → 0.1.1)

VALIDATION RUNTIME
[ ] 1h lecture continue sur Snapdragon 680
[ ] Aucun crash, aucun ANR
[ ] Memory profiler : pas de leak
[ ] Cold start < 300ms mesuré
[ ] GC pause max < 50ms

DOCUMENTAIRE
[ ] CHANGELOG.md à jour
[ ] Privacy policy présente
[ ] Third-party licenses listées (Sherpa, Readium, ONNX)
[ ] Screenshots et descriptions App Store prêts
```

---

## 📞 Questions Clés

**Q: Peut-on lancer une beta pendant les corrections ?**  
❌ Non. Les 3 bugs critiques causent des crashes/ANR qui ruineraient la réputation de l'app.

**Q: Quel est le MVP minimum viable ?**  
✅ Tous les 3 🔴 CRITIQUES fixes + au moins 40% coverage tests. Les 🟠 WARNING peuvent attendre un patch.

**Q: TimeFrame réaliste pour Play Store ?**  
📅 **2026-07-24** si les corrections avancent à rythme normal (équipe de 1-2 devs). Soit **~1 semaine** à partir d'aujourd'hui.

**Q: DevOps : comment tester release build ?**  
```bash
# Debug build
./gradlew installDebug && adb shell am start -n com.readflow.debug/.MainActivity

# Release build (signé)
./gradlew assembleRelease -Dorg.gradle.unsafe.isolation=false
# (demande les credentials keystore.properties)
adb install app/build/outputs/apk/release/app-release.apk
```

---

**Conclusion** : ReadFlow est architecturalement solide mais **non-déployable à l'état actuel**. Les 3 bugs critiques doivent être fermés avant qualquer release publique. Avec une équipe de 1-2 développeurs à temps complet, **ETA realiste : fin 2026-07-24**.

