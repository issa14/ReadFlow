# Prompt GitHub Copilot — Corrections pré-release ReadFlow

> À coller dans Copilot Chat (mode Edit/Agent). Traiter les sections dans l'ordre : 🔴 avant 🟠.
> Avant d'exécuter : vérifier que les noms de classes ci-dessous correspondent au code réel du repo
> (notamment `OnnxInferenceService` — confirmer si le moteur TTS en prod est bien Sherpa-ONNX ou Piper,
> le nom de la classe/wrapper doit être ajusté en conséquence).

---

## Contexte projet (à donner à Copilot en tête de conversation)

```
Projet : lecteur EPUB Android, Kotlin + Jetpack Compose (Material 3), Clean Architecture 4 couches + MVI, Hilt, Room.
TTS local offline (Piper + Edge-TTS), budget audio ~30 Mo / 10 min. Cible hardware milieu de gamme (Snapdragon 680).
Objectif de cette session : corriger 3 bugs critiques bloquants pour la release, puis 6 bugs prioritaires.
Contrainte : ne pas casser les tests existants, ne pas introduire de régression sur le pipeline de lecture audio.
Chaque correction doit être committée séparément avec un message en français à l'impératif
(convention du projet, voir CONTRIBUTING.md), et accompagnée des tests unitaires correspondants.

NOMS DE CLASSES RÉELLES (du repo audité) :
- Orchestrator: PlaybackOrchestrator (service/audio/)
- Audio Player: GaplessAudioPlayer (service/audio/)
- TTS Services: OnnxInferenceService (service/onnx/), EdgeTtsClient (service/edge/)
- Repository: TtsRepositoryImpl (data/repository/)
- ViewModel: ReaderViewModel (ui/screen/reader/), LibraryViewModel (ui/screen/library/)
- Cache: AudioCacheManager (service/audio/)
- Models: SynthesisResult (domain/model/)
```

---

## 🔴 CRITIQUE 1 — Race Condition AudioTrack (use-after-free SIGSEGV)

**Symptôme :** Crash aléatoire lors du stop de la lecture audio (SIGSEGV natif ou `IllegalStateException`).

**Localisation** : `GaplessAudioPlayer.kt:178-196` + `writeBlocking:236-265`

**Description technique** :
- `writeBlocking()` exécute une boucle while qui appelle `AudioTrack.write()` par chunks
- `stop()` appelle `job?.cancel()` (non-préemptif), puis libère `track` et le met à `null`
- Entre le cancel et la prochaine itération du while, `track` peut être libéré
- L'appel suivant `t.write()` s'exécute sur un objet libéré → **use-after-free natif**

**Demande à Copilot :**
1. **Dans `GaplessAudioPlayer.writeBlocking()`** (ligne 248-262) : 
   - Acquérir `writeLock` **AVANT** la vérification de l'état (`_state.value == State.Playing`)
   - Ajouter un check supplémentaire `if (track == null) break` dans la boucle
2. **Dans `stop()`** (ligne 178-196) :
   - Ajouter un flag atomique `willStop` coché dans la boucle writeBlocking avant chaque écriture
   - Ou : implémenter une proper graceful shutdown avec un `CancellationToken` explicite
3. **Ajouter un test** : `GaplessAudioPlayerTest.kt` pour tester le race condition en stressant stop() rapidement

**Critère d'acceptation :** Zero use-after-free crashes, test de regression passe avec 100+ rapid stop/play sequences

---

## 🔴 CRITIQUE 2 — Allocation mémoire excessive (ShortArray par phrase)

**Symptôme :** GC pressure massive, stuttering à chaque phrase, risque OOM sur long reads.

**Localisation** : `GaplessAudioPlayer.kt:236-265` ligne 239

**Description technique** :
```kotlin
private fun writeBlocking(floatSamples: FloatArray) {
    val n = floatSamples.size
    val shortSamples = ShortArray(n)  // ← ALLOCATION À CHAQUE APPEL
    // ... conversion et écriture ...
}
```
- Chaque phrase = 1 allocation `ShortArray` de 330k-660k shorts (660 KB - 1.3 MB)
- 1 allocation par ~5 secondes de lecture = ~720 allocations/heure
- Déclenche des GC pleins toutes les ~5-10 phrases (pauses de 50-200ms visibles)

**Demande à Copilot :**
1. **Implémenter un ObjectPool<ShortArray>** dans GaplessAudioPlayer :
   - Pool capacity = 3 (2 en vol, 1 en attente)
   - Réutilisation par chunks : reuse + reset entre chaque writeBlocking
2. **Alternative** : Convertir FloatArray → ShortArray et écrire par chunks sans allouer de ShortArray intermédiaire
3. **Ajouter un profiler memory test** : mesurer les allocations avant/après
4. **Benchmark** : comparer GC time sur 1h de lecture avant/après

**Critère d'acceptation :** Allocations divisées par 10x, zéro GC pause > 50ms sur read de 1h continue

---

## 🔴 CRITIQUE 3 — Deadlock + ANR dans PlaybackOrchestrator.launchSynthesisPipeline()

**Symptôme :** Lecture figée ou extrêmement ralentie sans ANR explicite. Après 5s, l'app peut devenir non-réactive.

**Localisation** : `PlaybackOrchestrator.kt:448-451` + `consumeAndPlay:486-530`

**Description technique** :
- `synthesize()` peut bloquer 500ms-1s par phrase (inférence ONNX lente)
- Aucun timeout n'interrompt l'attente si le modèle crash/hang
- Si synthèse > 2s, le système le perçoit comme un ANR potentiel
- Le pool de threads restreint peut saturer si plusieurs synthèses sont en vol

**Demande à Copilot :**
1. **Dans `SynthesizeUseCase` ou `TtsRepositoryImpl`** :
   - Ajouter un timeout `withTimeoutOrNull(2000)` autour de chaque `synthesize()` 
   - En cas de timeout, logger et retourner `SynthesisResult.Error()`
   - Le PlaybackOrchestrator skippera la phrase en silence court (50ms) 

2. **Dans `PlaybackOrchestrator.consumeAndPlay()`** (ligne 486-530) :
   - Ajouter un compteur `consecutiveErrors` 
   - Après 3 erreurs successives, envoyer une notification à l'UI et pause la lecture (pas un crash silencieux)

3. **Ajouter des tests** : 
   - `SynthesizeUseCaseTest` avec timeout mock
   - PlaybackOrchestratorTest avec synthèse lente

**Critère d'acceptation :** Aucune synthèse ne bloque > 2s, erreurs gracefully skipped avec feedback utilisateur

---

## 🟠 HAUTE 1 — AudioCacheManager sous-estime la taille mémoire réelle

**Localisation** : `service/audio/AudioCacheManager.kt:42-46`

**Demande à Copilot :**
1. Revoir la fonction `sizeOf()` qui actuellement calcule :
   - `samples.size * 4 + 24` (FloatArray + overhead)
   - `text.length * 2 + 38` (String UTF-16)
   - `32 + 24` (SynthesisResult + Entry)
   
2. **Ajouter les overheads manquants** :
   - LinkedHashMap.Node wrapper (~48 bytes)
   - Métadonnées String (engine ID, timestamps, etc.)
   - Alignement/fragmentation heap (~15-20%)

3. **Réduire MAX_SIZE_BYTES** de 20 Mo à 15 Mo avec la nouvelle formule
4. **Test** : Écrire un test qui alloue 20 phrases et vérifie que la mémoire réelle reste < 15 Mo mesuré avec `android.app.ActivityManager.MemoryInfo`

## 🟠 HAUTE 2 — SILENCE_BUFFER réutilisé mais ShortArray allocé par phrase (régression)

**Localisation** : `service/audio/GaplessAudioPlayer.kt:35-36` vs `writeBlocking:239`

**Note** : Le SILENCE_BUFFER en ligne 36 est correctement une allocation unique. Le vrai problème est la `ShortArray(n)` de la ligne 239 — déjà traité comme CRITIQUE 2.

## 🟠 HAUTE 3 — Double système de préférences (SharedPreferences possible remnants + DataStore)

**Localisation** : `data/settings/SettingsRepository.kt` utilise DataStore, mais à vérifier globalement

**Demande à Copilot :**
1. Faire un grep global : `grep -r "SharedPreferences" app/src/main/`
2. Si des usages restent, migrer entièrement vers `DataStore` (Preferences) avec `SharedPreferencesMigration`
3. Si zéro usage `SharedPreferences` → SKIP, considérer comme déjà fait (✅)
4. Test : Vérifier que SettingsRepository expose tout via `Flow<*>` et non via calls synchrones

## 🟠 HAUTE 4 — ReaderViewModel : logique métier directement dans les intents

**Localisation** : `ui/screen/reader/ReaderViewModel.kt` (347 L)

**Demande à Copilot :**
1. Auditer la méthode `handleIntent()` et identifier la logique métier qui devrait être dans le domain layer (PlaybackOrchestrator, ChunkTextUseCase, etc.)
2. Extraire les calculs/transformations vers UseCases, garder le ViewModel pour router et mapper l'état
3. Cible : ViewModel < 200 L (après extraction), UseCase contient la logique complexe
4. Test : Ajouter des tests sur les UseCase directs, indépendants du ViewModel

## 🟠 HAUTE 5 — Baseline Profile insuffisant

**Localisation** : `app/src/main/baseline-prof.txt` (présent mais court, ~4 L)

**Demande à Copilot :**
1. Étendre le profil pour couvrir tous les hotspots : ReaderContent, AudioPlayback paths, Theme composition
2. Utiliser `androidx.benchmark.macrobenchmark` pour générer un vrai profil de démarrage
3. Mesurer avant/après avec `adb shell cmd startop record-trace` sur la séquence cold start
4. Cible : réduire le cold start de ~400ms (actuel, voir COLD_START_AUDIT.md) à < 300ms

## 🟠 HAUTE 6 — Tests unitaires insuffisants (~12% couverture globale, objectif 70%)

**Localisation** : `app/src/test/` (6 fichiers, 849 L seulement)

**Demande à Copilot :**
1. Ajouter 30+ test cases critiques :
   - `ReaderViewModelTest` : expand pour couvrir error paths, rapid intents, process death + `SavedStateHandle`
   - `GaplessAudioPlayerTest` : NEW — race conditions, stop/play stress, allocation profiling
   - `PlaybackOrchestratorTest` : expand pour synthesize timeout, consecutive errors, buffer underrun
   - `TtsRepositoryImplTest` : NEW — mock ONNX, test fallback EdgeTTS
2. Mesurer couverture avec `./gradlew test jacocoTestReport`
3. Cible : ≥70% sur domain/usecase, ≥50% sur service/audio

---

## Instructions finales pour Copilot

### Ordre de traitement (STRICT)

```
🔴 C1 : Race condition AudioTrack (1-2 jours)
   ↓ commit: "Corrige la race condition use-after-free dans GaplessAudioPlayer.stop()"
   
🔴 C2 : Deadlock synthesize (1 jour)
   ↓ commit: "Ajoute un timeout à SynthesizeUseCase pour éviter les blocages ONNX"
   
🔴 C3 : ShortArray allocation pool (2-3 jours)
   ↓ commit: "Implémente un ObjectPool<ShortArray> pour éviter les allocations par phrase"
   
🟠 W1 : Memory sizeOf() (1 jour)
   ↓ commit: "Corrige le calcul de mémoire dans AudioCacheManager"
   
🟠 W2 : Tests unitaires (3-5 jours)
   ↓ commits: "Ajoute tests GaplessAudioPlayerTest", "Étend ReaderViewModelTest", etc.
   
🟠 W3 : ProGuard rules (1 jour)
   ↓ commit: "Complète les règles ProGuard pour Readium et Media3"
   
🟠 W4 : Baseline Profile (1-2 jours)
   ↓ commit: "Génère et valide le Baseline Profile complet"
```

### Après chaque correction

```bash
./gradlew clean test         # Tous les tests doivent passer
./gradlew lint              # Zéro avertissement critique
./gradlew assembleDebug     # Build sans erreur
git commit -m "Français à l'impératif"
```

### Mise à jour CHANGELOG

Ajouter dans `[Unreleased]` après chaque commit :
```markdown
### Fixed
- Corrige la race condition use-after-free dans GaplessAudioPlayer.stop()
  - Acquiert writeLock avant l'écriture AudioTrack
  - Ajoute un check track == null dans la boucle
  - Tests: GaplessAudioPlayerTest (100+ rapid stop/play)

### Added
- ObjectPool<ShortArray> pour éviter les allocations par phrase
  - Divise par 10x le GC pressure
  - Tests: benchmark memory allocation
```

### Validation finale avant release

```bash
# Mesurer froid start
adb shell pm clear com.readflow.debug
adb logcat | grep "START\|CREATION"  # Vérifier < 400ms

# Mesurer allocations
adb shell dumpsys meminfo com.readflow.debug | grep "NATIVE HEAP"

# Tester 1h de lecture continue
(À faire manuellement sur device physique Snapdragon 680)

# Vérifier zéro ProGuard/R8 crashes
./gradlew assembleRelease  # Doit réussir
```
