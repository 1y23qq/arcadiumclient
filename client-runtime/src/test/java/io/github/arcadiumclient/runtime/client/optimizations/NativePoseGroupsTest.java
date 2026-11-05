// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NativePoseGroupsTest {
    @Test void separatePartsShareAJobButRepeatedPartsKeepTheirEarlierPose() {
        NativePoseGroups groups = new NativePoseGroups();
        Object model = new Object(), pass = new Object();
        Matrix4f matrix = new Matrix4f().translation(3, 2, 1).rotateY(.3f);
        NativePoseGroups.Entry first = groups.add(model, pass, matrix, 40);
        first.mark(0);
        assertSame(first, groups.find(model, pass, matrix, 1));
        first.mark(1);
        assertNull(groups.find(model, pass, matrix, 0));
        NativePoseGroups.Entry repeated = groups.add(model, pass, matrix, 41);
        repeated.mark(0);
        assertSame(repeated, groups.find(model, pass, matrix, 1));
        assertEquals(40, first.job);
        assertEquals(3, first.written);
        assertEquals(41, repeated.job);
        assertEquals(1, repeated.written);
    }

    @Test void modelPassAndEveryRootBitArePartOfTheKey() {
        NativePoseGroups groups = new NativePoseGroups();
        Object model = new Object(), pass = new Object();
        Matrix4f root = new Matrix4f();
        NativePoseGroups.Entry entry = groups.add(model, pass, root, 0);
        assertSame(entry, groups.find(model, pass, new Matrix4f(), 3));
        assertNull(groups.find(new Object(), pass, root, 3));
        assertNull(groups.find(model, new Object(), root, 3));
        for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++) {
            Matrix4f changed = new Matrix4f(root).set(column, row, Math.nextUp(root.get(column, row)));
            assertNull(groups.find(model, pass, changed, 3), "column " + column + " row " + row);
        }
        assertNull(groups.find(model, pass, new Matrix4f().m01(-0f), 3));
        root.translate(1, 2, 3);
        assertSame(entry, groups.find(model, pass, new Matrix4f(), 3));
        assertNull(groups.find(model, pass, root, 3));
    }

    @Test void captureResetReleasesProfilesAndCannotReuseOldJobs() {
        NativePoseGroups groups = new NativePoseGroups();
        Object model = new Object(), pass = new Object();
        NativePoseGroups.Entry old = groups.add(model, pass, new Matrix4f(), 7);
        old.mark(2);
        groups.clear();
        assertNull(old.profile);
        assertNull(old.pass);
        assertNull(groups.find(model, pass, new Matrix4f(), 2));
        NativePoseGroups.Entry reused = groups.add(model, pass, new Matrix4f(), 0);
        assertSame(old, reused);
        assertEquals(0, reused.job);
        assertEquals(0, reused.written);
    }

    @Test void budgetAndFailedAppendDoNotDamageExistingGroups() {
        NativePoseGroups groups = new NativePoseGroups();
        Object model = new Object(), pass = new Object();
        NativePoseGroups.Entry first = groups.add(model, pass, new Matrix4f(), 10);
        first.mark(0);
        NativePoseGroups.Entry failed = groups.add(model, pass, new Matrix4f().translation(1, 0, 0), 11);
        groups.removeLast(failed);
        assertNull(failed.profile);
        assertSame(first, groups.find(model, pass, new Matrix4f(), 1));
        assertNull(groups.find(model, pass, new Matrix4f().translation(1, 0, 0), 0));
        for (int i = 1; i < NativePoseGroups.LIMIT; i++) assertNotNull(groups.add(model, pass, new Matrix4f().translation(i, 0, 0), i));
        assertTrue(groups.full());
        assertNull(groups.add(model, pass, new Matrix4f(), 1000));
        assertSame(first, groups.find(model, pass, new Matrix4f(), 1));
        groups.clear();
        assertFalse(groups.full());
        assertNotNull(groups.add(model, pass, new Matrix4f(), 0));
    }
}
