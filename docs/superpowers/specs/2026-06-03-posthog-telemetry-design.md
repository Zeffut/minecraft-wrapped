# Design — Système de télémétrie PostHog pour Minecraft Wrapped

**Date :** 2026-06-03
**Auteur :** Zeffut + Claude Code
**Statut :** Validé en brainstorming, en attente de revue spec

---

## 1. Contexte et objectif

Minecraft Wrapped est un mod Fabric client-side (v1.1.0, MC 1.21.11) déjà publié.
L'objectif de cette feature : ajouter un système de télémétrie **ultra complet** via PostHog
pour comprendre l'usage réel du mod et l'améliorer (quelles cards font quitter, taux de
complétion, features utilisées, erreurs en prod, etc.).

> ⚠️ **Renversement de décision projet** : le `CLAUDE.md` section 8 interdit explicitement
> « analytics / tracking » et « network calls ». Cette feature inverse ce choix sur demande
> explicite de Zeffut. Le `CLAUDE.md` devra être mis à jour pour refléter cette nouvelle
> orientation (voir §10).

### Décisions de cadrage (brainstorming)

| Décision | Choix retenu |
|----------|--------------|
| Modèle de consentement | **Opt-out** : activé par défaut, désactivable |
| Catégories d'events | **Les 4** : lifecycle, engagement, gameplay agrégé, erreurs/perfs |
| Identité | **UUID Minecraft brut** comme `distinct_id` (choix de Zeffut ; IP anonymisée) |
| Intégration SDK | **SDK officiel** `com.posthog:posthog-server` |
| Bundling | **Shadow + relocation** |
| Projet PostHog | **Projet partagé « Default project »** (id `192659`) — le plan PostHog ne permet pas de créer un projet dédié. Isolation par **tag d'application**. |

### Isolation dans le projet partagé

Le projet `Default project` héberge déjà une autre app (Esiee-Salles). Pour séparer
proprement les données du mod :

1. **Super-property `app = "minecraft-wrapped"`** sur **chaque** event → tout filtrage
   d'insight/dashboard se fait sur `app = minecraft-wrapped`.
2. **Préfixe `mcw_` sur tous les noms d'events** (`mcw_mod_loaded`, `mcw_card_viewed`, …)
   → aucune collision de nom avec les events de l'autre app dans la liste d'events.

---

## 2. Prérequis externes

Valeurs connues (projet partagé `Default project`) :

- **Project API key** : `phc_zdMj4p5wo8EvfVApjb2EbfUHJ76zgYGM5wAGz5YJC359`
- **Host d'ingestion** : `https://eu.i.posthog.com` — **À CONFIRMER** (EU vs US) dans le
  snippet d'install des settings PostHog. Constante d'une ligne, triviale à corriger.
- À activer côté projet : **`anonymize_ips`**.

La clé et le host sont stockés comme constantes dans le code (clé write-only publique,
safe à embarquer dans un client open-source MIT).

---

## 3. Architecture

Nouveau package isolé : `fr.zeffut.mcwrapped.telemetry`.

| Classe | Responsabilité | Dépend de |
|--------|----------------|-----------|
| `Telemetry` | Façade statique unique. `Telemetry.capture(event, props)`, `init()`, `shutdown()`. No-op total si désactivé ou non-initialisé. **Ne lance jamais d'exception.** | `TelemetryClient`, `McWrappedConfig` |
| `TelemetryClient` | Wrappe le SDK `posthog-server` (init/flush/close). Encapsule la clé et le host. | SDK PostHog |
| `EventContext` | Construit les super-properties communes attachées à chaque event (versions, OS, langue). | Fabric loader API |
| `Events` | Constantes des noms d'events + helpers de construction de props. Zéro string magique ailleurs. | — |
| `StatsBucketer` | Logique pure de bucketisation des stats gameplay (playtime, deaths, distance). Testable. | — |
| `TelemetryConfig` (champs ajoutés à `McWrappedConfig`) | `telemetryEnabled: boolean = true`. | ConfigManager existant |

### Principe directeur

**Aucune classe métier ne connaît PostHog.** Tout passe par `Telemetry.capture(...)`.
La télémétrie est strictement *fire-and-forget* :

- Si opt-out actif → no-op.
- Si init échouée → no-op.
- Si le réseau tombe → le SDK queue/drop en silence, jamais d'exception remontée.
- **Contrainte dure : la télémétrie ne doit jamais casser le mod ni freezer le client.**
  Tout appel `capture` est wrappé dans un `try/catch` qui log en `debug` et avale l'erreur.

---

## 4. Identité et vie privée

- `distinct_id` = **UUID Minecraft brut** du joueur (choix explicite de Zeffut).
- `anonymize_ips` activé côté projet PostHog → pas de stockage d'IP.
- **Jamais envoyé** : pseudo, nom de monde, IP de serveur, chemins de fichiers,
  stacktraces contenant des données perso.
- `context_type` se limite à `singleplayer` / `server` (jamais l'identité du serveur).
- Notice de transparence obligatoire dans le README + description Modrinth, même en opt-out.

---

## 5. Consentement (opt-out)

- `telemetryEnabled = true` par défaut dans `McWrappedConfig`.
- Toggle exposé dans l'écran de config ModMenu existant.
- Commande `/wrapped telemetry on|off` (+ `status`).
- Event `telemetry_opt_out` capturé **avant** la coupure, pour mesurer le taux de refus.
- À la réactivation : event `telemetry_opt_in`.

---

## 6. Catalogue d'events

> **Nommage** : tous les events sont préfixés `mcw_` (ex. `mcw_mod_loaded`). Les noms ci-dessous
> sont donnés sans préfixe pour la lisibilité ; le code les préfixe systématiquement via `Events`.

### Super-properties (sur chaque event, via `EventContext`)
`app` (= `"minecraft-wrapped"`, tag d'isolation), `mod_version`, `mc_version`,
`fabric_loader_version`, `fabric_api_version`, `os_name`, `os_arch`, `java_version`,
`language` (locale MC).

### 6.1 Lifecycle & usage
| Event | Properties |
|-------|-----------|
| `mod_loaded` | super-props + `history_count` (nb de wrapped archivés) |
| `wrapped_triggered` | `source` (`auto` / `command` / `pause_button`) |
| `wrapped_started` | `month`, `card_count` (selon config) |
| `card_viewed` | `card_id`, `index`, `duration_ms` |
| `wrapped_completed` | `total_duration_ms`, `cards_viewed`, `completion_pct` |
| `wrapped_skipped` | `card_id`, `index` (où le joueur a quitté) |
| `wrapped_ready_shown` | `month` |
| `wrapped_ready_dismissed` | `month` |

### 6.2 Engagement
| Event | Properties |
|-------|-----------|
| `image_exported` | `aspect_ratio`, `success` |
| `image_saved` | `success` |
| `clipboard_copied` | `success` |
| `command_used` | `command` (`wrapped` / `history` / `telemetry` / `test`) |
| `history_opened` | `entry_count` |
| `pause_button_clicked` | — |
| `config_opened` | — |
| `config_changed` | `setting`, `old_value`, `new_value` |

### 6.3 Gameplay agrégé (bucketisé)
| Event | Properties |
|-------|-----------|
| `wrapped_generated` | `archetype`, `playtime_bucket`, `deaths_bucket`, `distance_bucket`, `context_type`, `cards_shown` |

**Buckets** (`StatsBucketer`) :
- `playtime_bucket` : `<1h`, `1-10h`, `10-50h`, `50-100h`, `100h+`
- `deaths_bucket` : `0`, `1-5`, `6-20`, `21-50`, `50+`
- `distance_bucket` : `<10km`, `10-50km`, `50-100km`, `100-500km`, `500km+`

### 6.4 Erreurs & perfs
| Event | Properties |
|-------|-----------|
| `error_caught` | `exception_class`, `message` (tronqué, sans données perso), `location` |
| `json_parse_failed` | `file_type`, `reason` |
| `export_failed` | `stage`, `reason` |
| `wrapped_generation_time` | `duration_ms` |
| `animation_fps` | `avg_fps`, `min_fps` |

---

## 7. Intégration SDK & bundling

- Dépendance : `com.posthog:posthog-server:2.+` (beta, support capture + person properties).
- **Bundling : Shadow + relocation.**
  - Plugin `com.gradleup.shadow` (ou `johnrengelman.shadow`).
  - Relocation : `com.posthog` → `fr.zeffut.mcwrapped.shadow.posthog`, idem deps transitives
    (gson / okhttp / kotlin-runtime selon ce que tire le SDK — **à vérifier au build**).
  - Raison : Fabric charge tous les mods sur le même classloader ; sans relocation, conflit
    de version possible avec un autre mod embarquant les mêmes libs.
  - Intégration avec Loom : `remapJar` doit consommer la sortie shadow (configurer
    `remapJar { inputFile = shadowJar.archiveFile }` ou équivalent selon version Loom 1.13).
- **Init** : lazy au `ServerLifecycleEvents` / `ClientLifecycleEvents.CLIENT_STARTED`,
  dans un thread dédié pour ne pas bloquer le boot.
- **Shutdown** : `flush()` + `close()` sur `ClientLifecycleEvents.CLIENT_STOPPING`.

> Point de vérification au build : lister les dépendances transitives réelles de
> `posthog-server:2.+` et toutes les relocaliser. Si le SDK tire trop de dépendances lourdes
> (ex. kotlin stdlib + okhttp), réévaluer le compromis taille de jar.

---

## 8. Gestion des erreurs

- Tout `Telemetry.capture` est non-bloquant et avale ses erreurs (log `debug`).
- Échec d'init télémétrie → le mod fonctionne normalement, télémétrie désactivée pour la session.
- Pas de retry agressif : on s'appuie sur la queue interne du SDK.

---

## 9. Tests

JUnit 5 sur la logique pure uniquement :
- `StatsBucketer` : chaque borne de bucket (au moins 2 cas par dimension).
- `EventContext` : construction des super-properties.
- `Telemetry` no-op : `capture` ne fait rien et ne lève rien quand `telemetryEnabled=false`
  ou quand le client n'est pas initialisé (client mocké).

Pas de test sur l'envoi réseau réel. Vérification finale in-game via PostHog Live Events.

---

## 10. Mise à jour de la documentation projet

- Mettre à jour `CLAUDE.md` §8 : retirer/qualifier l'interdiction de tracking et de network
  calls (désormais : télémétrie opt-out anonyme autorisée).
- Ajouter une section « Télémétrie & vie privée » au `README.md` (ce qui est collecté,
  comment opt-out).
- Mentionner la collecte dans la description Modrinth.

---

## 11. Hors scope (YAGNI)

- Pas de feature flags PostHog (non supportés par le SDK Java de toute façon).
- Pas de session replay, pas de surveys.
- Pas d'écran de consentement bloquant (on a choisi opt-out, pas opt-in).
- Pas de tracking serveur-side (le mod reste client-only).
