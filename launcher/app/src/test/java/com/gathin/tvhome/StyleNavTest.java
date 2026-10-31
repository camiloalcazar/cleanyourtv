package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StyleNavTest {
    private static int wii(int index, int count, int dx, int dy) {
        return StyleNav.move(index, count, 4, 12, dx, dy);
    }

    private static int roku(int index, int count, int dx, int dy) {
        return StyleNav.move(index, count, 3, 0, dx, dy);
    }

    @Test public void oneStepInsideAPage() {
        assertEquals(1, wii(0, 12, 1, 0));
        assertEquals(4, wii(0, 12, 0, 1));
        assertEquals(0, wii(4, 12, 0, -1));
        assertEquals(4, wii(5, 12, -1, 0));
    }

    @Test public void rightPastTheEdgeTurnsThePageOnTheSameRow() {
        assertEquals(12, wii(3, 20, 1, 0));
        assertEquals(16, wii(7, 20, 1, 0));
    }

    @Test public void aShortNextPageTakesItsLastTile() {
        assertEquals(13, wii(11, 14, 1, 0));
    }

    @Test public void leftPastTheEdgeTurnsBackToTheLastColumn() {
        assertEquals(3, wii(12, 20, -1, 0));
        assertEquals(7, wii(16, 20, -1, 0));
    }

    @Test public void theFirstAndLastTilesStop() {
        assertEquals(StyleNav.OUT, wii(0, 20, -1, 0));
        assertEquals(StyleNav.OUT, wii(19, 20, 1, 0));
    }

    @Test public void lastRowOfAPageLeavesDownward() {
        assertEquals(StyleNav.OUT, wii(8, 12, 0, 1));
        assertEquals(StyleNav.OUT, wii(11, 20, 0, 1));
    }

    @Test public void downNeverCrossesIntoTheNextPage() {
        for (int i = 8; i < 12; i++) assertEquals(StyleNav.OUT, wii(i, 24, 0, 1));
    }

    @Test public void upStopsAtTheTopOfThePage() {
        assertEquals(StyleNav.OUT, wii(13, 20, 0, -1));
    }

    @Test public void downToAShortRowTakesItsLastTile() {
        assertEquals(4, wii(3, 5, 0, 1));
        assertEquals(StyleNav.OUT, wii(4, 5, 0, 1));
    }

    @Test public void aLongListHasNoPages() {
        assertEquals(StyleNav.OUT, roku(2, 15, 1, 0));
        assertEquals(StyleNav.OUT, roku(3, 15, -1, 0));
        assertEquals(12, roku(9, 15, 0, 1));
        assertEquals(StyleNav.OUT, roku(12, 15, 0, 1));
        assertEquals(StyleNav.OUT, roku(1, 15, 0, -1));
    }

    @Test public void nothingToMoveTo() {
        assertEquals(StyleNav.OUT, wii(0, 0, 1, 0));
        assertEquals(StyleNav.OUT, roku(0, 0, 0, 1));
    }

    private static final int[][] MODERN = {
            {234, 234, 256, 383},   // the tall card
            {514, 234, 316, 178}, {854, 234, 316, 178},
            {514, 436, 316, 178}, {854, 436, 316, 178},
            {234, 725, 216, 162}, {474, 725, 216, 162}, {714, 725, 216, 162}, {954, 725, 216, 162},
            {234, 911, 216, 162}};

    @Test public void aMixedLayoutMovesToTheTileThatLinesUp() {
        assertEquals(1, StyleNav.nearest(MODERN, 0, 1, 0));
        assertEquals(2, StyleNav.nearest(MODERN, 1, 1, 0));
        assertEquals(0, StyleNav.nearest(MODERN, 1, -1, 0));
        assertEquals(0, StyleNav.nearest(MODERN, 3, -1, 0));
        assertEquals(3, StyleNav.nearest(MODERN, 1, 0, 1));
        assertEquals(5, StyleNav.nearest(MODERN, 0, 0, 1));
        assertEquals(0, StyleNav.nearest(MODERN, 5, 0, -1));
        assertEquals(9, StyleNav.nearest(MODERN, 5, 0, 1));
    }

    @Test public void aMixedLayoutStopsAtItsEdges() {
        assertEquals(StyleNav.OUT, StyleNav.nearest(MODERN, 0, -1, 0));
        assertEquals(StyleNav.OUT, StyleNav.nearest(MODERN, 2, 1, 0));
        assertEquals(StyleNav.OUT, StyleNav.nearest(MODERN, 1, 0, -1));
        assertEquals(StyleNav.OUT, StyleNav.nearest(MODERN, 9, 0, 1));
    }

    @Test public void anUnknownStyleIsTheDefaultLook() {
        assertEquals(LauncherStyle.WII, LauncherStyle.normalize("wii"));
        assertEquals(LauncherStyle.ROKU, LauncherStyle.normalize("roku"));
        assertEquals(LauncherStyle.ROKU_NEW, LauncherStyle.normalize("roku_new"));
        assertEquals(LauncherStyle.APPLE, LauncherStyle.normalize("apple"));
        assertEquals(LauncherStyle.APPLE, LauncherStyle.normalize("bogus"));
        assertEquals(LauncherStyle.APPLE, LauncherStyle.normalize(null));
    }

    @Test public void tonesFadeOutAndStayInRange() {
        short[] pcm = StyleSounds.tone(45, 1500, 1500, 60);
        assertEquals(44100 * 45 / 1000, pcm.length);
        assertTrue(Math.abs(pcm[0]) < 2000);
        assertEquals(0, pcm[pcm.length - 1]);
        assertEquals(pcm.length * 2, StyleSounds.join(pcm, pcm).length);
    }
}
