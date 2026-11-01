// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoreboardPreparationTest {
    private static ScoreboardObjective objective(Scoreboard board, String name) { return board.createObjective(name, ScoreboardCriterion.DUMMY); }
    private static List<String> owners(Collection<ScoreboardScore> scores) { return scores.stream().map(ScoreboardScore::getOwner).toList(); }
    private static void score(Scoreboard board, ScoreboardObjective objective, String owner, int value) { board.getScore(owner, objective).set(value); }

    @Test void sortedNativeSnapshotIsReusedButEveryReturnedCollectionStaysIndependentAndMutable() {
        Scoreboard board = new Scoreboard();
        ScoreboardObjective objective = objective(board, "snapshot");
        score(board, objective, "Alpha", 3); score(board, objective, "Bravo", 10); score(board, objective, "Charlie", 3);
        var previous = ScoreboardPreparation.begin(objective, () -> 1);
        try {
            assertNull(ScoreboardPreparation.scores(board, objective));
            var original = board.getScores(objective);
            ScoreboardPreparation.observedScores(board, objective, original);
            var first = ScoreboardPreparation.scores(board, objective);
            assertEquals(owners(original), owners(first));
            first.clear();
            var second = ScoreboardPreparation.scores(board, objective);
            assertEquals(owners(original), owners(second));
            assertNotSame(original, second);
            assertNotSame(first, second);
            second.remove(second.iterator().next());
            assertEquals(3, ScoreboardPreparation.scores(board, objective).size());
        } finally { ScoreboardPreparation.end(previous); }
        assertFalse(ScoreboardPreparation.active());
    }

    @Test void aScoreRevisionInsideTheSameRenderInvalidatesOrderAndVisibilityImmediately() {
        Scoreboard board = new Scoreboard();
        ScoreboardObjective objective = objective(board, "revision");
        score(board, objective, "Alpha", 1); score(board, objective, "Bravo", 2);
        AtomicInteger revision = new AtomicInteger();
        FakeHud hud = new FakeHud();
        var previous = ScoreboardPreparation.begin(objective, revision::get);
        try {
            var original = board.getScores(objective);
            ScoreboardPreparation.observedScores(board, objective, original);
            ScoreboardPreparation.observedVisibility(hud, original, false);
            assertEquals(false, ScoreboardPreparation.visibility(hud, original));
            score(board, objective, "Alpha", 20);
            revision.incrementAndGet();
            assertNull(ScoreboardPreparation.scores(board, objective));
            assertNull(ScoreboardPreparation.visibility(hud, original));
            var changed = board.getScores(objective);
            ScoreboardPreparation.observedScores(board, objective, changed);
            assertEquals(owners(changed), owners(ScoreboardPreparation.scores(board, objective)));
        } finally { ScoreboardPreparation.end(previous); }
    }

    @Test void pointVisibilityTracksActualValuesAndEveryRelevantLiveOption() {
        Scoreboard board = new Scoreboard();
        ScoreboardObjective objective = objective(board, "visibility");
        score(board, objective, "Alpha", 1); score(board, objective, "Bravo", 2);
        FakeHud hud = new FakeHud();
        var previous = ScoreboardPreparation.begin(objective, () -> 0);
        try {
            var scores = new ArrayList<>(board.getScores(objective));
            ScoreboardPreparation.observedVisibility(hud, scores, true);
            assertEquals(true, ScoreboardPreparation.visibility(hud, new ArrayList<>(scores)));
            hud.points = 0;
            assertNull(ScoreboardPreparation.visibility(hud, scores));
            hud.points = 1; hud.repeating = false;
            assertNull(ScoreboardPreparation.visibility(hud, scores));
            hud.repeating = true; hud.preview = true;
            assertNull(ScoreboardPreparation.visibility(hud, scores));
            hud.preview = false;
            scores.get(0).set(37);
            assertNull(ScoreboardPreparation.visibility(hud, scores));
            ScoreboardPreparation.observedVisibility(hud, scores, false);
            assertEquals(false, ScoreboardPreparation.visibility(hud, scores));
            scores.remove(0);
            assertNull(ScoreboardPreparation.visibility(hud, scores));
            assertNull(ScoreboardPreparation.visibility(new FakeHud(), scores));
        } finally { ScoreboardPreparation.end(previous); }
    }

    @Test void nestedObjectivesAndDisabledRendersCannotLeakSnapshotsAcrossScopes() {
        Scoreboard board = new Scoreboard();
        ScoreboardObjective first = objective(board, "first"), second = objective(board, "second");
        score(board, first, "Alpha", 1); score(board, second, "Bravo", 2);
        var outer = ScoreboardPreparation.begin(first, () -> 0);
        try {
            ScoreboardPreparation.observedScores(board, first, board.getScores(first));
            assertNull(ScoreboardPreparation.scores(board, second));
            var inner = ScoreboardPreparation.begin(second, () -> 0);
            try { assertNull(ScoreboardPreparation.scores(board, first)); }
            finally { ScoreboardPreparation.end(inner); }
            assertNotNull(ScoreboardPreparation.scores(board, first));
            var disabled = ScoreboardPreparation.begin(first, false);
            try { assertFalse(ScoreboardPreparation.active()); assertNull(ScoreboardPreparation.scores(board, first)); }
            finally { ScoreboardPreparation.end(disabled); }
            assertNotNull(ScoreboardPreparation.scores(board, first));
        } finally { ScoreboardPreparation.end(outer); }
    }

    @Test void customBoardsAndUnsupportedHudApisUseTheOriginalPreparation() {
        Scoreboard custom = new Scoreboard() { };
        var previous = ScoreboardPreparation.begin(objective(custom, "custom"), () -> 0);
        try { assertFalse(ScoreboardPreparation.active()); }
        finally { ScoreboardPreparation.end(previous); }
        Scoreboard board = new Scoreboard();
        ScoreboardObjective objective = objective(board, "unsupported");
        previous = ScoreboardPreparation.begin(objective, () -> 0);
        try {
            assertNull(ScoreboardPreparation.visibility(new Object(), List.of()));
            ScoreboardPreparation.observedVisibility(new Object(), List.of(), true);
            assertTrue(ScoreboardPreparation.active());
        } finally { ScoreboardPreparation.end(previous); }
    }

    private static final class FakeHud {
        int points = 1;
        boolean repeating = true, preview;
        public int getScoreboardPoints() { return points; }
        public boolean getHideRepeatingScores() { return repeating; }
        public boolean getPreviewing() { return preview; }
    }
}
