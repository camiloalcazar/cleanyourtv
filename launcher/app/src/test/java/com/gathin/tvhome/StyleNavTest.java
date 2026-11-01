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

    @Test public void metroTilesPackIntoColumnsWithoutOverlap() {
        int[][] spans = {{2, 2}, {2, 1}, {2, 1}, {1, 1}, {1, 1}, {2, 1}, {2, 1}, {1, 1}, {1, 1}, {2, 2}, {2, 2}};
        int[][] places = StyleNav.pack(spans, 4, 6);
        java.util.Set<String> used = new java.util.HashSet<>();
        for (int i = 0; i < spans.length; i++) {
            assertTrue(places[i][1] + spans[i][1] <= 4);
            assertTrue(places[i][0] % 6 + spans[i][0] <= 6);
            for (int a = 0; a < spans[i][0]; a++) {
                for (int b = 0; b < spans[i][1]; b++) {
                    assertTrue(used.add((places[i][0] + a) + "," + (places[i][1] + b)));
                }
            }
        }
        assertEquals(0, places[0][0]);
        assertEquals(0, places[0][1]);
        assertEquals(2, places[1][1]);
    }

    @Test public void lastUsedReadsInMinutesHoursAndDays() {
        long now = 1_000_000_000_000L;
        assertEquals(null, AppUsage.ago(now, 0));
        assertEquals("1 min", AppUsage.ago(now, now - 5_000));
        assertEquals("2 min", AppUsage.ago(now, now - 120_000));
        assertEquals("3 h", AppUsage.ago(now, now - 3 * 3_600_000L));
        assertEquals("1 d", AppUsage.ago(now, now - 25 * 3_600_000L));
        assertEquals("1 sem", AppUsage.ago(now, now - 8 * 86_400_000L));
        assertEquals("1 mes", AppUsage.ago(now, now - 31 * 86_400_000L));
        assertTrue(AppUsage.recent(now, now - 3_600_000L));
        assertTrue(!AppUsage.recent(now, now - 25 * 3_600_000L));
        assertTrue(!AppUsage.recent(now, 0));
    }

    @Test public void theDeckPutsFavoritesFirstThenLatestUsedThenByName() {
        assertTrue(AppUsage.compareForDeck(true, 0, "Z", false, 999, "A") < 0);
        assertTrue(AppUsage.compareForDeck(false, 5, "Z", false, 9, "A") > 0);
        assertTrue(AppUsage.compareForDeck(false, 9, "Z", false, 0, "A") < 0);
        assertTrue(AppUsage.compareForDeck(false, 0, "alpha", false, 0, "Beta") < 0);
        assertTrue(AppUsage.compareForDeck(true, 9, "Z", true, 5, "A") < 0);
    }

    @Test public void anOpeningIsAnAppComingAfterAnother() {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        String last = AppOpenCounts.tally(java.util.Arrays.asList("a", "a", "home", "a", "b", "b"), null, counts);
        assertEquals("b", last);
        assertEquals(Integer.valueOf(2), counts.get("a"));
        assertEquals(Integer.valueOf(1), counts.get("b"));
        assertEquals(Integer.valueOf(1), counts.get("home"));
        counts.clear();
        assertEquals("c", AppOpenCounts.tally(java.util.Arrays.asList("b", "c"), "b", counts));
        assertEquals(null, counts.get("b"));
    }

    @Test public void aRowSlidesOnlyPastItsFirstTiles() {
        assertEquals(0, StyleNav.rowScroll(0, 3));
        assertEquals(0, StyleNav.rowScroll(3, 3));
        assertEquals(2, StyleNav.rowScroll(5, 3));
    }

    @Test public void cardsFlowToANewRowWhereTheyDoNotFit() {
        int[][] places = StyleNav.flow(new int[][]{{1}, {2}, {1}, {1}, {2}, {2}}, 4);
        assertEquals(1, places[1][0]);
        assertEquals(3, places[2][0]);
        assertEquals(0, places[3][0]);
        assertEquals(1, places[3][1]);
        assertEquals(1, places[4][0]);
        assertEquals(0, places[5][0]);
        assertEquals(2, places[5][1]);
    }

    @Test public void aTeamIsFoundWhateverItsAccentsOrCapitals() {
        assertTrue(SalaData.teamMatches("colombia", "Colombia"));
        assertTrue(SalaData.teamMatches("Atletico Nacional", "Atl\u00e9tico Nacional"));
        assertTrue(SalaData.teamMatches("nacional", "Atl\u00e9tico Nacional"));
        assertTrue(!SalaData.teamMatches("Colombia", "Brazil"));
        assertTrue(!SalaData.teamMatches("", "Brazil"));
    }

    @Test public void weatherCodesGetASymbol() {
        assertEquals("\u2600", SalaData.weatherSymbol(0));
        assertEquals("\u2601", SalaData.weatherSymbol(3));
        assertEquals("\u2602", SalaData.weatherSymbol(61));
        assertEquals("\u2744", SalaData.weatherSymbol(73));
        assertEquals("\u26A1", SalaData.weatherSymbol(95));
        assertEquals("3 h 12 min", SalaData.duration(192 * 60000L));
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
