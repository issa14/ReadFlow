# Rapport de Prototype — Sherpa-ONNX VITS Piper pour InkTone

> **Date :** 2026-07-08  
> **Phase :** 0 & 1 — Préparation, Prototype & Validation ONNX  
> **Auteur :** Équipe InkTone  

---

## 1. Résumé Exécutif

Le prototype de synthèse vocale neuronale locale a été réalisé avec succès sur un appareil Android **Snapdragon 680** (milieu de gamme). Le moteur **Sherpa-ONNX** avec le modèle **VITS Piper `fr_FR-upmc-medium`** produit une voix française de qualité satisfaisante avec un **Real-Time Factor (RTF) de ~0.8**, bien inférieur au seuil critique de 1.0.

**Décision : GO Sherpa-ONNX**. Aucun fallback Piper nécessaire.

---

## 2. Configuration Testée

| Élément | Détail |
|---|---|
| **Moteur TTS** | Sherpa-ONNX v1.13.4 (AAR Android, 47 Mo) |
| **Modèle** | `vits-piper-fr_FR-upmc-medium` (77 Mo) |
| **Voix** | Jessica (♀, sid=0) et Pierre (♂, sid=1) |
| **Fréquence** | 22 050 Hz mono |
| **Phonémisation** | eSpeak-NG intégré (voix `fr`) |
| **Runtime ONNX** | Inclus dans l'AAR (ONNX Runtime 1.19.2) |
| **Appareil test** | Vivo V2206 — Snapdragon 680 (SM6225), 8 cœurs |
| **Android** | API 34 (Android 14) |

---

## 3. Résultats des Tests

### 3.1 Performance (RTF)

| Longueur texte | RTF | Statut |
|---|---|---|
| Très courte ("Bonjour.") | ~0.60 | ✅ |
| Courte (1 phrase) | ~0.75 | ✅ |
| Moyenne (2-3 phrases) | ~0.80 | ✅ |
| Longue (4-5 phrases) | ~0.78 | ✅ |
| Très longue (paragraphe) | ~0.82 | ✅ |

**RTF moyen : 0.75–0.83** — la synthèse est systématiquement plus rapide que le temps réel.

> ⚠️ Tests restants : MediaTek et Tensor (autres appareils requis).

### 3.2 Phonémisation Française

10 phrases tests couvrant les spécificités du français :

| Phénomène | Résultat |
|---|---|
| Liaisons simples (les‿amis) | ✅ Correct |
| Lettres muettes (parlent, souvent) | ✅ Correct |
| Nasales (un, bon, vin, blanc) | ✅ Correct |
| Élisions (m'appelle, c'est) | ✅ Correct |
| Liaisons complexes (prends-en‿un) | ⚠️ Non réalisée |
| Nasale contextuelle (maintenant) | ⚠️ Légère distorsion |

**Score : 7/10** — Qualité suffisante pour une V1. Les 3 cas problématiques pourront être corrigés via des règles custom dans le `PhonemizationPipeline` (Phase 2).

### 3.3 Timestamps & Surlignage

Le test de bout en bout (paragraphe → découpage en phrases → synthèse → alignement temporel) est fonctionnel. L'alignement phrase par phrase via le cumul des échantillons audio est fiable et permettra le surlignage synchronisé dans l'interface de lecture.

> ℹ️ Les timestamps natifs mot-à-mot ne sont pas fournis par le modèle VITS Piper. L'approche retenue est le timing par phrase, avec distribution proportionnelle pour le surlignage mot (à implémenter en Phase 3).

---

## 4. Intégration Technique

### 4.1 Dépendances

L'AAR `sherpa-onnx-1.13.4.aar` (47 Mo) est stocké localement dans `app/libs/`. Il inclut ONNX Runtime, les libs JNI et l'API Java/Kotlin.

### 4.2 Modèle

Le modèle (77 Mo) est dans les assets Android. Au premier lancement, `espeak-ng-data/` est copié vers le stockage interne. Le modèle ONNX est lu via `AssetManager`.

### 4.3 Taille APK

| Composant | Taille |
|---|---|
| Code + ressources | ~15 Mo |
| Libs natives (ONNX + Sherpa) | ~31 Mo |
| Modèle TTS (assets) | ~77 Mo |
| **Total** | **~132 Mo** |

---

## 5. Points d'Attention

| # | Risque | Impact | Mitigation |
|---|---|---|---|
| R1 | Timestamps mot-à-mot absents | Surlignage moins précis | Distribution proportionnelle (Phase 3) |
| R2 | Phonémisation imparfaite | Prononciation robotique | Règles custom (Phase 2) |
| R3 | APK volumineux | Téléchargement lent | Compression int8, téléchargement différé |
| R4 | RTF chipsets bas de gamme inconnu | Risque > 1.0 | Tests supplémentaires |

---

## 6. Conclusion

- ✅ Sherpa-ONNX fonctionne sur Android
- ✅ RTF < 1.0 sur Snapdragon 680
- ✅ Qualité audio satisfaisante pour une V1
- ✅ Alignement phrase par phrase fonctionnel
- ✅ Architecture compatible avec le pipeline Phase 2

**Recommandation :** Poursuivre avec Sherpa-ONNX comme moteur unique. Allouer les efforts de la Phase 2 au parsing EPUB et au pipeline audio.
