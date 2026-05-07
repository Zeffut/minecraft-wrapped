# Minecraft Wrapped — Project Brief pour Claude Code

> **Pour Claude Code** : ce document contient TOUT ce dont tu as besoin pour développer ce projet de A à Z. Lis-le entièrement avant de commencer. Les sections sont ordonnées : contexte → décisions → spec → roadmap → conventions → opérationnel. Si tu as besoin de clarifier quelque chose, demande à l'utilisateur (Zeffut), mais essaie d'abord de trouver la réponse dans ce doc.

---

## 0. TL;DR

**Le projet** : un mod Fabric client-side qui génère, automatiquement le 1er de chaque mois, une expérience animée façon **Spotify Wrapped** récapitulant le mois Minecraft du joueur (stats, archétype, fun facts), avec export image partageable.

**Le nom** : **Minecraft Wrapped**.

**L'auteur** : Zeffut. Étudiant BUT Informatique, dev Java, gère un serveur SMP/Lifesteal.

**L'objectif business** : faire du chiffre sur Modrinth via le format viral "Wrapped".

**La stack** : Fabric 1.21.4+, Java 21, Gradle, Fabric Loom, vanilla Screen API + custom rendering.

**Le scope MVP** : 6 semaines de dev solo, 8 cards animées, auto-trigger mensuel, export PNG.

**Mon rôle de dev (Claude Code)** : implémenter ça proprement, semaine par semaine, en respectant les choix techniques décidés ci-dessous. Ne pas overengineer. Pas de plugin serveur. Pas de plateforme web. Pas de monétisation.

---

## 1. Contexte de l'utilisateur

Zeffut a déjà :
- De l'expérience en dev Java
- Un projet en cours : **MultiView Flashback Addon** (`fr.zeffut.multiview`) — un autre mod Fabric qu'il code avec Claude Code
- Une instance n8n self-hosted à `n8n.zeffut.fr` (utile pour le sharing v1.1, pas pour le MVP)
- Un serveur SMP sur lequel il peut tester en conditions réelles
- Une bonne intuition sur ce qu'il veut, mais besoin que tu sois pragmatique sur le scope

**Style de communication** : casual, en français, direct. Pas besoin de tonnes de préambule dans tes réponses.

**Ce qu'il a déjà rejeté pour ce projet** :
- Plugin serveur (il veut TOUT côté client)
- Plateforme web (KISS)
- Monétisation forcée

---

## 2. Décisions déjà prises (NE PAS rediscuter)

| Décision | Choix | Raison |
|----------|-------|--------|
| Loader | Fabric only | Focus MVP, pas de fragmentation |
| Version Minecraft | 1.21.4+ | Là où sont les joueurs en 2026 |
| Java | 21 | Requis par MC 1.21+ |
| Côté | Client only | Pas de plugin serveur |
| Période du recap | Mois précédent (1er → dernier jour) | Concept clair et viral |
| Trigger | Automatique le 1er du mois à la connexion | Le killer feature |
| Format export | PNG 1080x1920 (story Insta) | Partage social optimal |
| License | MIT | Open-source, simple |
| Mod ID | `fr.zeffut.mcwrapped` | Convention de Zeffut |
| Storage stats | `~/.minecraft/wrapped/<server-or-singleplayer>/<YYYY-MM>.json` | Snapshots mensuels |

---

## 3. Décisions à prendre AVANT le code

Voici 4 questions ouvertes. **Ton boulot : proposer une recommandation argumentée pour chacune en premier message à l'utilisateur, et attendre sa validation avant de coder.**

### Q1 : owo-lib ou vanilla Screen API ?
- **owo-lib** : facilite les animations et UI complexes, mais ajoute une dépendance que les utilisateurs devront installer
- **Vanilla Screen API** : zéro dépendance hard, plus de boulot pour les anims
- **Mon avis pré-recommandé** : vanilla Screen API. Une dépendance = friction d'installation. Et les animations qu'on veut sont faisables sans owo (tweening custom de ~50 lignes).

### Q2 : Font custom ou Minecraftia vanilla ?
- **Custom (Minecraftia, MinecraftTen...)** : plus stylé, charge depuis les resources du mod
- **Vanilla** : zéro friction, mais visuellement plus pauvre
- **Mon avis pré-recommandé** : vanilla pour MVP. On peut ajouter custom en v1.1.

### Q3 : Sons 100% vanilla ou custom ?
- **Vanilla MC** : block place, level up, enderpearl, ghast laugh, etc. — déjà chargés
- **Custom** : drumroll, swoosh, etc. — ajoute du poids au mod
- **Mon avis pré-recommandé** : vanilla MC only. C'est cohérent avec l'esthétique Minecraft et zéro friction.

### Q4 : Stats serveur dès MVP ou client-only ?
- **Client-only** : trivial à implémenter, marche en singleplayer parfaitement
- **Avec stats serveur** : nécessite de capturer le packet `StatisticsS2CPacket` et forcer la sync au lancement
- **Mon avis pré-recommandé** : commencer client-only en S1, ajouter le support serveur en S5 si reste du temps. Beaucoup d'utilisateurs jouent multiplayer, c'est important mais pas critique pour shipper.

---

## 4. Spec produit complète

### 4.1 User flow MVP

**Le moment magique (auto-trigger)** :
```
1. Joueur lance Minecraft normalement, c'est le 1er du mois
2. Après l'écran de chargement habituel, juste avant le menu principal :
   → écran "Your November Wrapped is ready" avec bouton START
3. Joueur clique START → l'expérience animée démarre :
   - Card par card, fade-in, build-up de chiffres animés
   - Sound design : sons vanilla MC
   - Background animé en blocs Minecraft pixelisés qui défilent
4. Après les 8 cards (~45 sec d'animation), écran final :
   - "Share your Wrapped" → exporte l'image récap (1080x1920)
   - "Save" → enregistre dans `screenshots/wrapped/`
   - "Skip to game" → ferme l'expérience
5. Ne se redéclenche pas avant le mois suivant
```

**À tout moment** :
- `/wrapped` → relance la dernière expérience
- `/wrapped history` → voir tes anciens recaps
- Bouton "Wrapped" dans le menu pause si déjà eu son recap du mois en cours

**Edge cases à gérer** :
- Joueur saute des mois → pas de wrapped pour ces mois, on attend le prochain mois où il joue
- Joueur joue très peu (< 1h dans le mois) → wrapped allégé, "Tu as à peine joué ce mois-ci 😅"
- Premier mois jamais (nouvelle install) → pas de wrapped, on attend le mois suivant pour avoir des données complètes

### 4.2 Les 8 cards animées

Chaque card a sa propre animation et son propre son.

| # | Card | Contenu | Animation | Son vanilla MC |
|---|------|---------|-----------|----------------|
| 1 | Intro | "Your **November** in Minecraft" | Texte typewriter + blocks falling background | enderpearl impact |
| 2 | Time spent | "**47h 23m** ce mois" + comparaison fun | Compteur qui monte + ticking | clock + level up |
| 3 | Top blocks | Top 3 blocs minés avec icônes | Pop-in séquentiel | pickaxe hits |
| 4 | Top mob killed | Mob render 3D qui tourne | Rotation + reveal | mob's death sound |
| 5 | Death recap | "Tu es mort 23 fois 💀" + cause #1 + plot twist | Death overlay rouge | death + ghast laugh |
| 6 | Distance | "127 km" + breakdown (walk/boat/elytra) | Compteur + progress bars | footsteps loop |
| 7 | Archetype | Reveal animé + flip card | Drumroll + dramatic flip | achievement unlocked |
| 8 | Final | Récap visuel + share button | Static avec watermark | ambient music end |

### 4.3 Les 15 archétypes

Algorithme : on calcule un score par archétype basé sur les stats du mois, on prend le score max.

1. **Le Mineur Compulsif** — ratio blocs minés / playtime élevé
2. **Le Tueur Silencieux** — beaucoup de mob_kills, peu de deaths
3. **Le Risque-Tout** — beaucoup de deaths, beaucoup de distance parcourue
4. **L'Architecte** — beaucoup de blocs posés vs minés
5. **L'Aventurier** — distance parcourue énorme, peu de temps stationnaire
6. **Le Casanier** — playtime élevé mais distance faible
7. **Le Pacifiste** — 0 ou très peu de mob_kills
8. **Le Pyromane** — beaucoup de TNT triggered ou lava use
9. **L'Artisan** — beaucoup de crafts + smelts + brewing
10. **Le Pêcheur** — fish_caught dominant
11. **Le Voyageur** — usage enderpearl/elytra élevé
12. **Le Speedrunner** — peu de playtime mais events significatifs (kill ender dragon, etc.)
13. **Le No-Life Assumé** — playtime > 100h dans le mois
14. **Le Trader** — beaucoup de villager interactions
15. **L'Indécis** — fallback si aucun archétype ne se démarque clairement

**Implémentation** : créer une enum `Archetype` avec une méthode `score(StatsSnapshot)` retournant un float. Pick the max.

### 4.4 Stats vanilla disponibles

Source : `stats/<uuid>.json` dans le world folder (singleplayer) ou via `StatisticsS2CPacket` (multiplayer).

```
minecraft:mined.<block>           → blocs minés par type
minecraft:used.<item>             → items utilisés
minecraft:crafted.<item>          → items craftés
minecraft:broken.<item>           → outils cassés
minecraft:picked_up.<item>        → items ramassés
minecraft:dropped.<item>          → items droppés
minecraft:killed.<entity>         → mobs tués par type
minecraft:killed_by.<entity>      → tués par mob
minecraft:custom.<stat>           → ~50 stats custom
```

Les stats `custom` les plus utiles pour nos cards :
- `play_time` (ticks → /20 pour secondes → /60 pour minutes)
- `walk_one_cm`, `sprint_one_cm`, `boat_one_cm`, `aviate_one_cm` (elytra)
- `damage_dealt`, `damage_taken`, `mob_kills`, `player_kills`
- `deaths`, `time_since_death`, `time_since_rest`
- `jump`, `drop`, `eat_cake_slice`, `play_record`, `clean_armor`
- `fish_caught`, `enchant_item`, `bell_ring`
- `interact_with_villager`, `traded_with_villager`

### 4.5 Stratégie de monthly delta

À chaque connexion :
1. Lire les stats actuelles
2. Comparer avec le snapshot du dernier accès stocké dans `~/.minecraft/wrapped/<context>/`
3. Si on est passé sur un nouveau mois → finalize le snapshot du mois précédent
4. Stocker un nouveau snapshot pour le mois courant

Structure d'un snapshot JSON :
```json
{
  "month": "2026-04",
  "context": "singleplayer-WorldName" ou "server-hash",
  "captured_at": "2026-04-30T23:58:42Z",
  "stats_raw": { /* tout le dump JSON des stats vanilla */ }
}
```

Et un fichier `wrapped_<YYYY-MM>.json` finalisé par mois :
```json
{
  "month": "2026-04",
  "context": "singleplayer-WorldName",
  "deltas": {
    "play_time_ticks": 8472000,
    "blocks_mined": { "minecraft:stone": 12384, ... },
    "mobs_killed": { "minecraft:zombie": 247, ... },
    "deaths": 23,
    "killed_by": { "minecraft:fall": 8, "minecraft:chicken": 1, ... },
    /* etc. */
  },
  "archetype": "Le Mineur Compulsif",
  "consumed": false  // true quand l'utilisateur a vu son wrapped
}
```

### 4.6 Auto-trigger logic

Au `ClientTickEvents.END_CLIENT_TICK` (ou un autre event approprié) :
1. Si on est sur le main menu (pas en jeu)
2. Si on est entre le 1er et le 7 du mois (fenêtre de grâce de 7 jours)
3. Si un fichier `wrapped_<YYYY-MM-1>.json` existe avec `consumed: false`
4. → afficher l'écran "Your X Wrapped is ready"

Ne JAMAIS interrompre le joueur en cours de partie.

---

## 5. Architecture du projet

### 5.1 Structure des fichiers

```
mcwrapped/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── src/main/
│   ├── java/fr/zeffut/mcwrapped/
│   │   ├── McWrappedClient.java                    # Entry point ClientModInitializer
│   │   ├── stats/
│   │   │   ├── StatsSnapshot.java                  # POJO d'un snapshot
│   │   │   ├── StatsReader.java                    # Lit stats vanilla
│   │   │   ├── SnapshotManager.java                # Gère les snapshots mensuels
│   │   │   ├── MonthlyDelta.java                   # Calcule les deltas mois sur mois
│   │   │   └── StatsContext.java                   # Identifie le contexte (singleplayer/server)
│   │   ├── archetype/
│   │   │   ├── Archetype.java                      # Enum des 15 archétypes
│   │   │   └── ArchetypeCalculator.java            # Algo de scoring
│   │   ├── ui/
│   │   │   ├── WrappedScreen.java                  # Screen qui orchestre l'expérience
│   │   │   ├── cards/
│   │   │   │   ├── Card.java                       # Interface abstraite
│   │   │   │   ├── IntroCard.java
│   │   │   │   ├── TimeSpentCard.java
│   │   │   │   ├── TopBlocksCard.java
│   │   │   │   ├── TopMobCard.java
│   │   │   │   ├── DeathRecapCard.java
│   │   │   │   ├── DistanceCard.java
│   │   │   │   ├── ArchetypeCard.java
│   │   │   │   └── FinalCard.java
│   │   │   ├── animation/
│   │   │   │   ├── Easing.java                     # Fonctions d'easing
│   │   │   │   ├── Tween.java                      # Système de tweening
│   │   │   │   └── ParticleEffects.java
│   │   │   └── prompt/
│   │   │       └── WrappedReadyScreen.java         # "Your X Wrapped is ready"
│   │   ├── export/
│   │   │   ├── ImageExporter.java                  # Génère le PNG final
│   │   │   └── ClipboardHelper.java
│   │   ├── trigger/
│   │   │   └── MonthlyTrigger.java                 # Logic de l'auto-trigger
│   │   └── command/
│   │       └── WrappedCommand.java                 # /wrapped et /wrapped history
│   └── resources/
│       ├── fabric.mod.json
│       ├── assets/mcwrapped/
│       │   ├── lang/en_us.json
│       │   ├── lang/fr_fr.json
│       │   └── textures/                           # Si on a des textures custom
│       └── mcwrapped.mixins.json                   # Si besoin de mixins
└── README.md
```

### 5.2 Dépendances

```gradle
// build.gradle
dependencies {
    minecraft "com.mojang:minecraft:1.21.4"
    mappings "net.fabricmc:yarn:1.21.4+build.X:v2"
    modImplementation "net.fabricmc:fabric-loader:0.16.X"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.114.X+1.21.4"

    // Pas de dépendances autres en MVP. Si owo-lib finalement choisi :
    // modImplementation "io.wispforest:owo-lib:0.12.X"
}
```

Java AWT (`java.awt.image.BufferedImage`, `Graphics2D`, `ImageIO`) est disponible nativement, pas besoin de dépendance pour l'export PNG.

### 5.3 Flux global

```
Game Launch
    ↓
ClientModInitializer.onInitializeClient()
    ↓
Register events:
  - CLIENT_STARTED → init SnapshotManager (lit les snapshots existants)
  - END_CLIENT_TICK → MonthlyTrigger checks if wrapped should display
  - JOIN_SERVER / DISCONNECT → capture snapshot
  - SCREEN_OPENED (StatisticsScreen) → opportunité de capturer stats serveur
    ↓
Monthly trigger fires:
  - Display WrappedReadyScreen
  - User clicks START
  - Display WrappedScreen which orchestrates Card1 → Card2 → ... → FinalCard
  - User clicks Share/Save → ImageExporter.export()
  - Mark wrapped as consumed
```

---

## 6. Roadmap par milestones

**IMPORTANT** : Tu dois travailler **semaine par semaine** et demander une validation à Zeffut entre chaque milestone. Ne pas tout coder d'un coup.

### Milestone S1 (semaine 1) — Foundation
**Objectif** : pouvoir lancer le mod et voir dans la console les stats du mois précédent.

Livrables :
- [ ] Setup projet Fabric avec Gradle, Java 21, MC 1.21.4
- [ ] `fabric.mod.json` correct
- [ ] `StatsReader` qui lit `stats/<uuid>.json` en singleplayer
- [ ] `SnapshotManager` qui crée et lit les snapshots dans `~/.minecraft/wrapped/`
- [ ] `MonthlyDelta` qui calcule les deltas
- [ ] `StatsContext` qui identifie singleplayer vs server (basé sur le hash de l'IP serveur ou nom du world)
- [ ] Au lancement, log les deltas du mois précédent dans la console (test : modifier la date système ou créer faux snapshots pour tester)
- [ ] **Aucune UI**, juste de la logique

**Validation S1** : "Je lance MC, je vois dans la console mes stats du mois précédent."

### Milestone S2 (semaine 2) — 1 card animée complète
**Objectif** : valider le pipeline d'animation avec une seule card de qualité.

Livrables :
- [ ] `WrappedScreen` qui peut afficher une seule card
- [ ] `Easing` (cubic, quad, bounce) implémenté
- [ ] `Tween` system pour animer des valeurs dans le temps
- [ ] `IntroCard` complète avec :
  - Animation typewriter du texte
  - Background blocks falling (utiliser les particles vanilla)
  - Son enderpearl impact
- [ ] Commande `/wrapped test intro` pour la déclencher manuellement
- [ ] Pas encore de transitions vers d'autres cards

**Validation S2** : "Je tape /wrapped test intro, je vois une animation belle et fluide. Si c'est moche, on s'arrête là."

### Milestone S3 (semaine 3) — 4 autres cards
**Objectif** : avoir le squelette complet avec 5 cards.

Livrables :
- [ ] `TimeSpentCard` (compteur qui monte)
- [ ] `TopBlocksCard` (pop-in séquentiel)
- [ ] `TopMobCard` (rotation 3D du mob)
- [ ] `DeathRecapCard` (overlay rouge + plot twist)
- [ ] Système de transitions entre cards (fade, slide)
- [ ] Skip avec ESC

**Validation S3** : "Je lance /wrapped test, je vois les 5 cards s'enchaîner."

### Milestone S4 (semaine 4) — 3 dernières cards + archétypes
**Objectif** : expérience complète end-to-end.

Livrables :
- [ ] `DistanceCard`
- [ ] `ArchetypeCard` (drumroll + reveal)
- [ ] `FinalCard`
- [ ] Système d'archétypes complet : enum + algorithme de scoring + textes descriptifs
- [ ] Tests unitaires sur le calcul d'archétype (au moins 3-4 cas)

**Validation S4** : "Je vois les 8 cards complètes. L'archétype est cohérent avec mes stats."

### Milestone S5 (semaine 5) — Export image + auto-trigger
**Objectif** : MVP shippable.

Livrables :
- [ ] `ImageExporter` qui génère un PNG 1080x1920
- [ ] Sauvegarde dans `screenshots/wrapped/<YYYY-MM>.png`
- [ ] Copie au clipboard
- [ ] `WrappedReadyScreen` (le prompt "Your X Wrapped is ready")
- [ ] `MonthlyTrigger` avec la fenêtre de grâce 1-7
- [ ] Mark `consumed: true` après visionnage
- [ ] Commande `/wrapped` (relance) et `/wrapped history`
- [ ] **(Si temps)** support stats serveur via packet capture

**Validation S5** : "Je peux théoriquement utiliser le mod en conditions réelles."

### Milestone S6 (semaine 6) — Polish + release
**Objectif** : version 1.0.0 publique.

Livrables :
- [ ] Bug fixes des semaines précédentes
- [ ] README.md propre avec :
  - GIFs animés des cards
  - Installation instructions
  - Liste des features
  - FAQ
- [ ] Description Modrinth
- [ ] Versioning : tag `v1.0.0`
- [ ] Build du jar release
- [ ] Vidéo TikTok teaser de 30s (optionnel mais recommandé)

**Validation S6** : "Le mod est sur Modrinth, je peux le partager."

---

## 7. Conventions de code

### 7.1 Style général
- **Java 21** : utiliser les features modernes (records, switch expressions, var, text blocks)
- **Naming** : classique Java (PascalCase pour classes, camelCase pour méthodes/champs, UPPER_SNAKE pour constantes)
- **Pas de stars imports** : `import java.util.List;` pas `import java.util.*;`
- **Final partout où c'est possible** : champs, paramètres, variables locales

### 7.2 Logging
- Utiliser SLF4J (déjà fourni par Fabric)
- Logger à `info` pour les events importants (chargement, monthly trigger fire, export image)
- Logger à `debug` pour le détail
- Logger à `warn` pour les edge cases
- Logger à `error` SEULEMENT pour les vraies erreurs

```java
public final class McWrappedClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("mcwrapped");
}
```

### 7.3 Tests
- Pas de testing framework lourd. Utiliser JUnit 5 si vraiment nécessaire pour la logique pure (calcul d'archétype, parsing de stats).
- Pas de tests pour le rendering UI (chiant et peu utile).
- Tests in-game prioritaires.

### 7.4 Configuration
- **Pas de config file en MVP**. Pas de menu de config. Tout est sensible au défaut.
- Si vraiment besoin de config (v1.1+) : un simple JSON dans `~/.minecraft/config/mcwrapped.json`.

### 7.5 Internationalization
- Tous les strings UI dans `assets/mcwrapped/lang/en_us.json` et `fr_fr.json`
- Utiliser `Text.translatable("mcwrapped.card.intro.title")` partout
- Pas de hardcoded strings dans le code

### 7.6 Commits
- Format : `<type>: <description>` (feat, fix, refactor, docs, chore)
- Exemples :
  - `feat: add IntroCard with typewriter animation`
  - `fix: snapshot loading fails on first launch`
  - `refactor: extract easing functions to dedicated class`

---

## 8. Choses à NE PAS faire

- ❌ **Pas de plugin serveur**. Tout est client.
- ❌ **Pas de plateforme web**. Le sharing est juste un export image local + clipboard. Lien web = v1.1+.
- ❌ **Pas de monétisation, pas d'analytics, pas de tracking**. Privacy first.
- ❌ **Pas de network calls** dans le MVP. Le mod marche 100% offline.
- ❌ **Pas d'overengineering**. Pas de DI framework, pas d'abstractions inutiles. Le code doit rester simple et lisible.
- ❌ **Pas de mixins** sauf si vraiment nécessaire (probablement pas pour ce projet).
- ❌ **Pas de support Forge/NeoForge** pour le MVP.
- ❌ **Pas de support multi-version** (1.21.4 only au début).
- ❌ **Pas d'animations 3D custom complexes** (juste rendering 2D + utilisation des particles MC).

---

## 9. Points critiques à valider avec l'utilisateur

Au début du projet, **avant de coder**, demander à l'utilisateur :

1. Validation des 4 questions ouvertes (cf. section 3) avec tes recommandations
2. Confirmation de la version MC cible (1.21.4 ? 1.21.10 ? 1.21.11 ?)
3. Le projet sera dans un repo Git ? Si oui, demander où.
4. Y a-t-il un design/maquette des cards ou tu peux improviser ?

Pendant le dev, **demander validation à chaque fin de milestone** avant de passer à la suivante.

**À la fin de S2 spécifiquement** : montrer un GIF/vidéo de la card animée à Zeffut. Si le rendu est moche, ne pas continuer la S3 — d'abord améliorer le design.

---

## 10. Risques connus et mitigations

| Risque | Impact | Mitigation |
|--------|--------|------------|
| Animation finale moche | 🔴 Critique | Investir 50% du temps sur S2-S4. Si vraiment pas bon, demander à Zeffut de trouver un graphiste avant la release. |
| Stats serveur impossibles à capturer fiablement | 🟡 Moyen | Fallback : MVP en client-only. Documenter clairement la limitation. |
| Compatibility avec mods de stats existants (Stats+, etc.) | 🟢 Faible | On lit les stats vanilla, ils n'écrivent pas dessus. Pas de conflit attendu. |
| Snapshot manquant (joueur change de PC) | 🟢 Faible | Documenter dans le README que les wrapped sont locaux. |
| 1er mois après install : pas de data | 🟢 Faible | Afficher un message "Reviens le mois prochain pour ton premier Wrapped !" |
| Performance de l'image PNG generation | 🟢 Faible | Faire ça en async, montrer un loading. |

---

## 11. Ressources

### Documentation Fabric
- Fabric Wiki : https://fabricmc.net/wiki/start
- Fabric API : https://github.com/FabricMC/fabric
- Yarn mappings : https://maven.fabricmc.net/

### Documentation Minecraft
- Stats format : https://minecraft.wiki/w/Statistics
- Particle types : https://minecraft.wiki/w/Particles
- Sound events : https://minecraft.wiki/w/Sounds.json

### Inspirations design
- Spotify Wrapped : https://newsroom.spotify.com/wrapped/
- Stats.fm : https://stats.fm
- Apple Music Replay

### Mods de référence (pour la qualité d'animation)
- Immersive UI (16M dl) : https://www.curseforge.com/minecraft/mc-mods/immersive-ui
- Flow : https://modrinth.com/mod/flow
- Slight GUI Modifications : https://www.curseforge.com/minecraft/mc-mods/slight-gui-modifications

### Format export image
- `BufferedImage` doc : https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/image/BufferedImage.html
- `ImageIO` doc : https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/imageio/ImageIO.html

### Repo template
- Fabric example mod : https://github.com/FabricMC/fabric-example-mod

---

## 12. Premier message attendu de Claude Code

Quand l'utilisateur te file ce document, tu réponds :

1. **Confirmer la lecture** : "J'ai lu la spec complète."
2. **Recommandations sur les 4 questions ouvertes** avec justification courte de chaque
3. **Confirmation des prérequis** : MC version, repo Git, design/maquette
4. **Ton plan d'action S1** détaillé en bullet points
5. **Ne PAS commencer à coder avant la validation de Zeffut**

Format type :
```
J'ai lu la spec complète de Minecraft Wrapped. Voici mes recommandations sur les questions ouvertes :

Q1 (owo-lib vs vanilla) : vanilla, parce que [raison]
Q2 (font) : vanilla, parce que [raison]
Q3 (sons) : vanilla MC, parce que [raison]
Q4 (stats serveur) : client-only en S1, ajout en S5 si temps, parce que [raison]

Avant de commencer, j'ai besoin de confirmer :
- Version MC cible : 1.21.4 ou plus récente ?
- Y a-t-il un repo Git ?
- As-tu des références de design pour les cards ?

Plan S1 (Foundation) :
- [...]
- [...]

Je commence dès que tu valides.
```

---

**Bon dev !** 🚀

— *Document préparé par Zeffut + Claude (claude.ai), mai 2026*