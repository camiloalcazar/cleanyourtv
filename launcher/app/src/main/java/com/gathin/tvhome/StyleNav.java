package com.gathin.tvhome;

/** Where a D-pad press goes in a styled home's grid: one step at a time, whatever the remote repeats. */
final class StyleNav {
    private StyleNav() {}

    /** The press leaves the grid on that side. */
    static final int OUT = -1;

    /**
     * @param pageSize tiles per page (right and left past the page's edge turn it), or 0 for one long list
     * @return the tile's index, or {@link #OUT}
     */
    static int move(int index, int count, int columns, int pageSize, int dx, int dy) {
        if (count <= 0 || index < 0 || index >= count || columns <= 0) return OUT;
        int size = pageSize > 0 ? pageSize : count;
        int start = index / size * size;
        int end = Math.min(start + size, count);
        int slot = index - start;
        int column = slot % columns;
        int row = slot / columns;
        if (dx > 0) {
            if (column < columns - 1 && index + 1 < end) return index + 1;
            int next = start + size;
            return next < count ? Math.min(next + row * columns, count - 1) : OUT;
        }
        if (dx < 0) {
            if (column > 0) return index - 1;
            return start > 0 ? start - size + row * columns + columns - 1 : OUT;
        }
        if (dy > 0) {
            if (index + columns < end) return index + columns;
            return start + (row + 1) * columns < end ? end - 1 : OUT;
        }
        if (dy < 0) return index - columns >= start ? index - columns : OUT;
        return index;
    }

    /**
     * The same for tiles of different sizes, each {x, y, width, height}: the closest tile on that side, one that lines
     * up with this one before any that does not. On a tie the earlier tile wins, so a press is always the same move.
     */
    static int nearest(int[][] rects, int index, int dx, int dy) {
        if (index < 0 || index >= rects.length) return OUT;
        int[] from = rects[index];
        int best = OUT;
        long bestScore = Long.MAX_VALUE;
        for (int i = 0; i < rects.length; i++) {
            if (i == index) continue;
            int[] to = rects[i];
            int gap;
            int overlap;
            int offset;
            if (dx != 0) {
                gap = dx > 0 ? to[0] - (from[0] + from[2]) : from[0] - (to[0] + to[2]);
                overlap = Math.min(from[1] + from[3], to[1] + to[3]) - Math.max(from[1], to[1]);
                offset = Math.abs(from[1] - to[1]);
            } else {
                gap = dy > 0 ? to[1] - (from[1] + from[3]) : from[1] - (to[1] + to[3]);
                overlap = Math.min(from[0] + from[2], to[0] + to[2]) - Math.max(from[0], to[0]);
                offset = Math.abs(from[0] - to[0]);
            }
            if (gap < 0 || overlap <= 0) continue;
            long score = gap * 4L + offset;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }
}
