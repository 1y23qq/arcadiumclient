// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;

public final class ScoreboardPreparation {
    public static String status() {
        return io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig.getRetainedScoreboardPreparation()
                ? "Scoreboard: " + snapshots + " prepared / " + snapshotHits + " shared row lookups"
                : "Scoreboard preparation: Off";
    }
    private static final int MAX_SCORES = 8192;
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final Map<Class<?>, HudAccess> HUDS = new HashMap<>();
    private static final HudAccess UNSUPPORTED = new HudAccess(null, null, null);
    public static long snapshots, snapshotHits, visibilityComputations, visibilityHits, invalidations;

    public static final class Scope {
        private final Scoreboard board;
        private final ScoreboardObjective objective;
        private final IntSupplier revision;
        private int observedRevision;
        private ArrayList<ScoreboardScore> scores;
        private Object hud;
        private int points;
        private boolean repeating, preview, decision;
        private int[] values;

        private Scope(ScoreboardObjective objective, IntSupplier revision) {
            this.objective = objective;
            board = objective.getScoreboard();
            this.revision = revision;
            observedRevision = revision.getAsInt();
        }

        private void refresh() {
            int next = revision.getAsInt();
            if (next != observedRevision) {
                observedRevision = next;
                scores = null;
                hud = null;
                values = null;
                invalidations++;
            }
        }
    }

    private record HudAccess(MethodHandle points, MethodHandle repeating, MethodHandle preview) { }

    private static final class Revision {
        static final VarHandle FIELD = find();
        static final IntSupplier VALUE = () -> (int) FIELD.get();
        private static VarHandle find() {
            try {
                Class<?> type = Class.forName("org.polyfrost.vanillahud.hud.HudInternals");
                return MethodHandles.publicLookup().unreflectVarHandle(type.getField("scoreboardRevision"));
            } catch (ReflectiveOperationException unavailable) { return null; }
        }
    }

    private ScoreboardPreparation() { }

    public static Scope begin(ScoreboardObjective objective, boolean enabled) {
        return begin(objective, enabled && Revision.FIELD != null ? Revision.VALUE : null);
    }

    static Scope begin(ScoreboardObjective objective, IntSupplier revision) {
        Scope previous = CURRENT.get();
        if (objective == null || revision == null || objective.getScoreboard().getClass() != Scoreboard.class) CURRENT.remove();
        else CURRENT.set(new Scope(objective, revision));
        return previous;
    }

    public static void end(Scope previous) {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }

    public static boolean active() { return CURRENT.get() != null; }

    public static Collection<ScoreboardScore> scores(Scoreboard board, ScoreboardObjective objective) {
        Scope scope = CURRENT.get();
        if (scope == null || scope.board != board || scope.objective != objective) return null;
        scope.refresh();
        if (scope.scores == null) return null;
        snapshotHits++;
        return new ArrayList<>(scope.scores);
    }

    public static void observedScores(Scoreboard board, ScoreboardObjective objective, Collection<ScoreboardScore> scores) {
        Scope scope = CURRENT.get();
        if (scope == null || scope.board != board || scope.objective != objective || scores == null || scores.size() > MAX_SCORES) return;
        scope.refresh();
        if (scope.scores == null) {
            scope.scores = new ArrayList<>(scores);
            snapshots++;
        }
    }

    public static Boolean visibility(Object hud, Collection<ScoreboardScore> scores) {
        Scope scope = CURRENT.get();
        if (scope == null || scores.size() > MAX_SCORES) return null;
        scope.refresh();
        HudAccess access = access(hud);
        if (access == UNSUPPORTED) return null;
        try {
            return visibility(scope, hud, scores, (int) access.points.invokeExact(hud),
                    (boolean) access.repeating.invokeExact(hud), (boolean) access.preview.invokeExact(hud));
        } catch (Throwable incompatible) { return unsupported(hud, incompatible); }
    }

    static Boolean visibility(Scope scope, Object hud, Collection<ScoreboardScore> scores, int points, boolean repeating, boolean preview) {
        if (!matches(scope, hud, scores, points, repeating, preview)) return null;
        visibilityHits++;
        return scope.decision;
    }

    private static boolean matches(Scope scope, Object hud, Collection<ScoreboardScore> scores, int points, boolean repeating, boolean preview) {
        if (scope.hud != hud || scope.points != points || scope.repeating != repeating || scope.preview != preview
                || scope.values == null || scope.values.length != scores.size()) return false;
        int index = 0;
        for (ScoreboardScore score : scores) if (scope.values[index++] != score.get()) return false;
        return true;
    }

    public static void observedVisibility(Object hud, Collection<ScoreboardScore> scores, boolean decision) {
        Scope scope = CURRENT.get();
        if (scope == null || scores.size() > MAX_SCORES) return;
        scope.refresh();
        HudAccess access = access(hud);
        if (access == UNSUPPORTED) return;
        try {
            observedVisibility(scope, hud, scores, (int) access.points.invokeExact(hud), (boolean) access.repeating.invokeExact(hud),
                    (boolean) access.preview.invokeExact(hud), decision);
        } catch (Throwable incompatible) { unsupported(hud, incompatible); }
    }

    static void observedVisibility(Scope scope, Object hud, Collection<ScoreboardScore> scores, int points, boolean repeating, boolean preview, boolean decision) {
        if (scope.decision == decision && matches(scope, hud, scores, points, repeating, preview)) return;
        scope.hud = hud;
        scope.points = points;
        scope.repeating = repeating;
        scope.preview = preview;
        scope.decision = decision;
        if (scope.values == null || scope.values.length != scores.size()) scope.values = new int[scores.size()];
        int index = 0;
        for (ScoreboardScore score : scores) scope.values[index++] = score.get();
        visibilityComputations++;
    }

    private static Boolean unsupported(Object hud, Throwable error) {
        if (error instanceof VirtualMachineError fatal) throw fatal;
        HUDS.put(hud.getClass(), UNSUPPORTED);
        return null;
    }

    private static HudAccess access(Object hud) {
        return HUDS.computeIfAbsent(hud.getClass(), type -> {
            try { return new HudAccess(getter(type, "getScoreboardPoints", int.class), getter(type, "getHideRepeatingScores", boolean.class), getter(type, "getPreviewing", boolean.class)); }
            catch (ReflectiveOperationException incompatible) { return UNSUPPORTED; }
        });
    }

    private static MethodHandle getter(Class<?> owner, String name, Class<?> result) throws ReflectiveOperationException {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                Method getter = type.getDeclaredMethod(name);
                getter.setAccessible(true);
                return MethodHandles.lookup().unreflect(getter).asType(MethodType.methodType(result, Object.class));
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
}
