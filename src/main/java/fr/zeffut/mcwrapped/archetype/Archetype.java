package fr.zeffut.mcwrapped.archetype;

import fr.zeffut.mcwrapped.ui.cards.WrappedContext;

import java.util.Comparator;
import java.util.Map;

/**
 * 15 archetypes the Wrapped picks from. Each one defines a {@link #score(WrappedContext)}
 * function returning a positive float; the highest score wins.
 */
public enum Archetype {

    MINEUR_COMPULSIF("The Compulsive Miner", "Stone whispers your name.") {
        @Override public float score(final WrappedContext ctx) {
            final long mined = ctx.delta().total("minecraft:mined");
            final long minutes = Math.max(1, ctx.playTimeTicks() / 20 / 60);
            return mined / (float) minutes / 25f;
        }
    },
    TUEUR_SILENCIEUX("The Silent Killer", "Many slain, never seen falling.") {
        @Override public float score(final WrappedContext ctx) {
            final long kills = ctx.mobKills();
            final long deaths = ctx.deaths();
            if (kills < 30) return 0;
            return kills / 50f / Math.max(1, deaths) * 5f;
        }
    },
    RISQUE_TOUT("The Daredevil", "Dying is just learning, right?") {
        @Override public float score(final WrappedContext ctx) {
            final long deaths = ctx.deaths();
            final long distance = totalDistanceCm(ctx);
            if (deaths < 8) return 0;
            return deaths * (distance / 100_000f) / 10f;
        }
    },
    ARCHITECTE("The Architect", "You build more than you break.") {
        @Override public float score(final WrappedContext ctx) {
            final long mined = Math.max(1, ctx.delta().total("minecraft:mined"));
            final long used = ctx.delta().total("minecraft:used");
            return used / (float) mined;
        }
    },
    AVENTURIER("The Adventurer", "Always one chunk further.") {
        @Override public float score(final WrappedContext ctx) {
            final long distance = totalDistanceCm(ctx);
            final long minutes = Math.max(1, ctx.playTimeTicks() / 20 / 60);
            return (distance / 1000f) / minutes / 5f;
        }
    },
    CASANIER("The Homebody", "Why leave when home is perfect?") {
        @Override public float score(final WrappedContext ctx) {
            final long minutes = ctx.playTimeTicks() / 20 / 60;
            final long distance = totalDistanceCm(ctx);
            if (minutes < 30) return 0;
            return minutes / Math.max(1, distance / 100_000f + 1) / 60f;
        }
    },
    PACIFISTE("The Pacifist", "Mobs sleep peacefully near you.") {
        @Override public float score(final WrappedContext ctx) {
            final long minutes = ctx.playTimeTicks() / 20 / 60;
            final long kills = ctx.mobKills();
            if (minutes < 30 || kills > 30) return 0;
            return (minutes / 30f) / Math.max(1, kills);
        }
    },
    PYROMANE("The Pyromaniac", "Some watch the world burn.") {
        @Override public float score(final WrappedContext ctx) {
            final Map<String, Long> used = ctx.delta().deltas().getOrDefault("minecraft:used", Map.of());
            final long tnt = used.getOrDefault("minecraft:tnt", 0L);
            final long lava = used.getOrDefault("minecraft:lava_bucket", 0L);
            final long flint = used.getOrDefault("minecraft:flint_and_steel", 0L);
            return (tnt + lava * 2 + flint) / 6f;
        }
    },
    ARTISAN("The Crafter", "Forge, smelt, repeat.") {
        @Override public float score(final WrappedContext ctx) {
            final long crafted = ctx.delta().total("minecraft:crafted");
            return crafted / 200f;
        }
    },
    PECHEUR("The Angler", "Patience pays in shimmering fish.") {
        @Override public float score(final WrappedContext ctx) {
            final long fish = customStat(ctx, "minecraft:fish_caught");
            return fish / 25f;
        }
    },
    VOYAGEUR("The Wanderer", "Pearls, elytras, no boundaries.") {
        @Override public float score(final WrappedContext ctx) {
            final long pearls = ctx.delta().deltas().getOrDefault("minecraft:used", Map.of()).getOrDefault("minecraft:ender_pearl", 0L);
            final long elytraCm = customStat(ctx, "minecraft:aviate_one_cm");
            return (pearls + elytraCm / 10_000f) / 5f;
        }
    },
    SPEEDRUNNER("The Speedrunner", "Compressed lifetimes per session.") {
        @Override public float score(final WrappedContext ctx) {
            final long minutes = ctx.playTimeTicks() / 20 / 60;
            final long crafted = ctx.delta().total("minecraft:crafted");
            if (minutes > 240 || crafted < 50) return 0;
            return crafted / Math.max(1, minutes) / 2f;
        }
    },
    NO_LIFE("The No-Life", "You earned it, friend.") {
        @Override public float score(final WrappedContext ctx) {
            final long minutes = ctx.playTimeTicks() / 20 / 60;
            return minutes / (100 * 60f);
        }
    },
    TRADER("The Trader", "Emeralds, the universal language.") {
        @Override public float score(final WrappedContext ctx) {
            final long talked = customStat(ctx, "minecraft:talked_to_villager");
            final long traded = customStat(ctx, "minecraft:traded_with_villager");
            return (talked + traded * 2) / 30f;
        }
    },
    INDECIS("The Wanderer of Many Hats", "A bit of everything, master of none.");

    private final String displayName;
    private final String tagline;

    Archetype(final String displayName, final String tagline) {
        this.displayName = displayName;
        this.tagline = tagline;
    }

    public String displayName() { return displayName; }
    public String tagline() { return tagline; }

    public float score(final WrappedContext ctx) { return 0f; }

    public static Archetype pick(final WrappedContext ctx) {
        Archetype best = INDECIS;
        float bestScore = 0.6f; // threshold below which we fall back to INDECIS
        for (final Archetype a : values()) {
            if (a == INDECIS) continue;
            final float s = a.score(ctx);
            if (s > bestScore) {
                bestScore = s;
                best = a;
            }
        }
        return best;
    }

    /** Top 3 by score for diagnostics — used by the test command. */
    public static java.util.List<java.util.Map.Entry<Archetype, Float>> topScores(final WrappedContext ctx, final int n) {
        return java.util.Arrays.stream(values())
                .filter(a -> a != INDECIS)
                .map(a -> Map.entry(a, a.score(ctx)))
                .sorted(Comparator.<Map.Entry<Archetype, Float>>comparingDouble(Map.Entry::getValue).reversed())
                .limit(n)
                .toList();
    }

    private static long customStat(final WrappedContext ctx, final String key) {
        return ctx.delta().deltas().getOrDefault("minecraft:custom", Map.of()).getOrDefault(key, 0L);
    }

    private static long totalDistanceCm(final WrappedContext ctx) {
        final Map<String, Long> custom = ctx.delta().deltas().getOrDefault("minecraft:custom", Map.of());
        return custom.getOrDefault("minecraft:walk_one_cm", 0L)
                + custom.getOrDefault("minecraft:sprint_one_cm", 0L)
                + custom.getOrDefault("minecraft:boat_one_cm", 0L)
                + custom.getOrDefault("minecraft:aviate_one_cm", 0L)
                + custom.getOrDefault("minecraft:horse_one_cm", 0L);
    }
}
