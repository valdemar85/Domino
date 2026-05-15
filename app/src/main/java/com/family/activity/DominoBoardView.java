package com.family.activity;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import com.family.dto.Tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Domino board layout — real snake behaviour mimicking a physical table:
 *
 *   - The FIRST tile of the round is the anchor, placed in the centre of the board.
 *     The anchor never moves after that.
 *   - The chain grows in TWO independent branches outward from the anchor: one
 *     branch handles the rightEnd of the chain, the other handles the leftEnd.
 *   - Each branch keeps its own cursor and direction. A new tile attaches to its
 *     branch's current endpoint — existing tiles on that branch don't move.
 *   - When a branch runs out of space in its current direction it turns 90° at the
 *     last tile of that branch and continues. The OTHER branch is unaffected.
 *   - Doubles are placed perpendicular to the chain direction (the classic
 *     domino convention), centred on the chain's axis.
 *
 * Tiles in the input list are arranged in the order they sit on the table:
 * [..., leftmost, ..., anchor, ..., rightmost]. The anchor is identified by Tile
 * equality (orientation-independent), so even when index shifts because of a
 * prepend on the left branch, we still recognise it.
 *
 * The face size adapts to the view width — bigger view → bigger tiles, with sane
 * clamps so the artwork stays readable on phones and isn't huge on tablets.
 */
public class DominoBoardView extends FrameLayout {

    public interface TileRenderer {
        View create(Tile tile, boolean verticalOrientation, int faceSizePx);
    }

    private static final int DIR_RIGHT = 0;
    private static final int DIR_DOWN  = 1;
    private static final int DIR_LEFT  = 2;
    private static final int DIR_UP    = 3;

    /** Pre-computed pixel box for one tile after layout. */
    private static final class TilePos {
        float x, y, w, h;
        boolean verticalOrientation;
        /**
         * When the chain doubles back (e.g. right branch curls round and travels
         * LEFT or UP) the matching face of the new tile sits on the "wrong" end
         * relative to {@code Tile.a}/{@code Tile.b}. We swap the pips at render
         * time so the matching pip physically touches the previous tile.
         */
        boolean flipPips;
    }

    /** Per-branch growing state. */
    private static final class SideState {
        int direction;
        float endX, endY;     // attachment point for the next tile (point on the chain axis)
        TilePos lastTile;     // the most recently placed tile in this branch
        int turnsRemaining = 4; // safety counter so we don't infinitely spin on a tiny board
    }

    // ---- layout state (rebuilt per round / when view size changes) ----
    private Tile anchor;
    private final Map<Tile, TilePos> positionsByTile = new HashMap<>();
    private SideState rightSide;
    private SideState leftSide;
    private final List<Tile> previousTiles = new ArrayList<>();

    // ---- input state ----
    private final List<Tile> tiles = new ArrayList<>();
    private TileRenderer renderer;
    private int faceSizeDp = 30;
    private int pendingAnimateIndex = -1;
    private boolean pendingRebuild = false;

    public DominoBoardView(Context c) { super(c); init(); }
    public DominoBoardView(Context c, AttributeSet a) { super(c, a); init(); }
    public DominoBoardView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setClipChildren(false);
    }

    public void setTiles(List<Tile> newTiles, int animateIndex, TileRenderer r) {
        this.renderer = r;
        this.tiles.clear();
        if (newTiles != null) this.tiles.addAll(newTiles);
        this.pendingAnimateIndex = animateIndex;
        if (getWidth() > 0 && getHeight() > 0) {
            updateLayout();
            rebuildChildren();
        } else {
            pendingRebuild = true;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int oldFaceDp = faceSizeDp;
        adaptFaceSize();
        boolean faceChanged = oldFaceDp != faceSizeDp;

        if (pendingRebuild) {
            // First real measure since setTiles — lay out from scratch.
            resetLayoutState();
            updateLayout();
            rebuildChildren();
            pendingRebuild = false;
            return;
        }
        if (tiles.isEmpty()) return;

        if (faceChanged || oldw == 0 || oldh == 0) {
            // Tile face size has changed (e.g. screen rotation) — every pixel
            // coordinate is stale, full re-layout.
            resetLayoutState();
            updateLayout();
            rebuildChildren();
        }
        // Otherwise the canvas just resized without affecting our math (e.g. the
        // hand strip beneath us collapsed or expanded). Don't touch the layout —
        // any reset here would lose the chain the player is looking at.
    }

    /**
     * Pick a face size so the full 28-tile chain fits visibly without zoom.
     *
     * The snake wraps as a series of rows, so BOTH axes constrain the tile size:
     *   - Width: ~5 tiles per row (each tile = 2*face) → face ≈ width / 10.
     *   - Height: chain needs ~6 rows of width≈2*face on a wide canvas → height
     *     budget per row is roughly 2*face, so face ≈ height / 8.
     * Picking the tighter of the two prevents medium tablets (where height/8 is
     * the binding constraint) from rendering tiles so large that a long chain
     * spills off the bottom. The upper clamp is a readability ceiling.
     */
    private void adaptFaceSize() {
        float density = getResources().getDisplayMetrics().density;
        int viewWidthDp = (int) (getWidth() / density);
        int viewHeightDp = (int) (getHeight() / density);
        int candidate = Math.min(viewWidthDp / 10, viewHeightDp / 8);
        faceSizeDp = Math.min(56, Math.max(28, candidate));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int faceSizePx() {
        return dp(faceSizeDp);
    }

    private void resetLayoutState() {
        anchor = null;
        positionsByTile.clear();
        previousTiles.clear();
        rightSide = null;
        leftSide = null;
    }

    // ---- layout algorithm ----

    private void updateLayout() {
        if (tiles.isEmpty()) { resetLayoutState(); return; }
        if (anchor == null || !tiles.contains(anchor)) {
            // Fresh round (or board cleared) — re-initialise from a new anchor.
            resetLayoutState();
            initWithAnchor();
        }

        int newAnchorIdx = tiles.indexOf(anchor);
        int oldAnchorIdx = previousTiles.indexOf(anchor);
        int newLeftCount  = newAnchorIdx;
        int newRightCount = tiles.size() - 1 - newAnchorIdx;
        int oldLeftCount  = oldAnchorIdx < 0 ? 0 : oldAnchorIdx;
        int oldRightCount = oldAnchorIdx < 0 ? 0 : (previousTiles.size() - 1 - oldAnchorIdx);

        // Append new right-branch tiles (existing ones keep their positions).
        for (int i = oldRightCount + 1; i <= newRightCount; i++) {
            placeOnBranch(rightSide, tiles.get(newAnchorIdx + i), true);
        }
        // Append new left-branch tiles.
        for (int i = oldLeftCount + 1; i <= newLeftCount; i++) {
            placeOnBranch(leftSide, tiles.get(newAnchorIdx - i), false);
        }

        previousTiles.clear();
        previousTiles.addAll(tiles);
    }

    /**
     * Place the anchor in the centre of the board and prep the two branches.
     */
    private void initWithAnchor() {
        anchor = tiles.get(0);
        boolean isDouble = anchor.getA() == anchor.getB();
        int face = faceSizePx();
        int gap = dp(2);

        TilePos pos = new TilePos();
        pos.verticalOrientation = isDouble;       // double perpendicular to horizontal chain
        pos.w = pos.verticalOrientation ? face : 2f * face;
        pos.h = pos.verticalOrientation ? 2f * face : face;
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        pos.x = centerX - pos.w / 2f;
        pos.y = centerY - pos.h / 2f;
        positionsByTile.put(anchor, pos);

        rightSide = new SideState();
        rightSide.direction = DIR_RIGHT;
        rightSide.endX = pos.x + pos.w + gap;
        rightSide.endY = centerY;
        rightSide.lastTile = pos;

        leftSide = new SideState();
        leftSide.direction = DIR_LEFT;
        leftSide.endX = pos.x - gap;
        leftSide.endY = centerY;
        leftSide.lastTile = pos;
    }

    /**
     * Place {@code tile} on the end of {@code branch}. If it doesn't fit in the
     * current direction, the branch turns 90° (both branches turn CW so the right
     * one curls DOWN and the left one curls UP) and tries again. Up to 4 attempts
     * before giving up — at that point we place the tile anyway and rely on
     * pinch-zoom for escape.
     *
     * Crucially: only this branch's state changes. The other branch and all already
     * placed tiles stay exactly where they were.
     */
    private void placeOnBranch(SideState branch, Tile tile, boolean isRightBranch) {
        boolean isDouble = tile.getA() == tile.getB();
        int face = faceSizePx();
        int gap = dp(2);
        float padding = dp(8);
        // Both branches rotate clockwise — right RIGHT→DOWN, left LEFT→UP.
        final int turnSign = +1;

        for (int attempt = 0; attempt < 4; attempt++) {
            boolean horizontalChain = (branch.direction == DIR_RIGHT || branch.direction == DIR_LEFT);
            // Doubles always perpendicular to chain; non-doubles along chain.
            boolean verticalOrientation = horizontalChain ? isDouble : !isDouble;
            float boxW = verticalOrientation ? face : 2f * face;
            float boxH = verticalOrientation ? 2f * face : face;

            // Compute where the tile would sit if we placed it now, in current direction.
            // The chain has an "axis" line (perpendicular to direction) passing through
            // endY (or endX for vertical chain). The tile is centred on that axis,
            // attached at endX (or endY) — that's the connection point.
            TilePos pos = new TilePos();
            pos.verticalOrientation = verticalOrientation;
            pos.w = boxW;
            pos.h = boxH;
            // For the right branch, DominoLogic stores tiles as (matchingEnd, newEnd) so
            // the matching pip is at `a` (left/top in natural rendering). That's correct
            // while we're moving RIGHT or DOWN. When the chain doubles back (LEFT/UP)
            // the matching pip needs to be on the right/bottom — flip the rendering.
            // Mirror logic for the left branch.
            pos.flipPips = isRightBranch
                    ? (branch.direction == DIR_LEFT || branch.direction == DIR_UP)
                    : (branch.direction == DIR_RIGHT || branch.direction == DIR_DOWN);
            switch (branch.direction) {
                case DIR_RIGHT:
                    pos.x = branch.endX;
                    pos.y = branch.endY - boxH / 2f;
                    break;
                case DIR_LEFT:
                    pos.x = branch.endX - boxW;
                    pos.y = branch.endY - boxH / 2f;
                    break;
                case DIR_DOWN:
                    pos.x = branch.endX - boxW / 2f;
                    pos.y = branch.endY;
                    break;
                case DIR_UP:
                    pos.x = branch.endX - boxW / 2f;
                    pos.y = branch.endY - boxH;
                    break;
            }

            // A double placed perpendicular to the chain has both pips matching,
            // so the chain axis only has to land somewhere on its long matching
            // edge — not exactly at the centre. If the naturally centred position
            // overflows the canvas, slide the double along its long axis until it
            // fits. This is what real domino does at a corner: the chain "kinks"
            // through the double instead of forcing the double to rotate sideways.
            // Applied on every attempt — after a chain turn, the perpendicular
            // double commonly overflows by ~half a face and would otherwise
            // cascade through another 2-3 turns into a wrong-face placement.
            if (isDouble) {
                if (horizontalChain) {
                    // Vertical double, slide along y.
                    float chainY = branch.endY;
                    float lower = Math.max(padding, chainY - boxH);
                    float upper = Math.min(getHeight() - padding - boxH, chainY);
                    if (lower <= upper) {
                        pos.y = Math.max(lower, Math.min(upper, pos.y));
                    }
                } else {
                    // Horizontal double, slide along x.
                    float chainX = branch.endX;
                    float lower = Math.max(padding, chainX - boxW);
                    float upper = Math.min(getWidth() - padding - boxW, chainX);
                    if (lower <= upper) {
                        pos.x = Math.max(lower, Math.min(upper, pos.x));
                    }
                }
            }

            boolean inBounds = pos.x >= padding && pos.y >= padding
                    && pos.x + boxW <= getWidth()  - padding
                    && pos.y + boxH <= getHeight() - padding;
            // Doubles are wide perpendicular to the chain. If the chain bends and
            // there isn't enough room for the perpendicular double, the next-best
            // direction tried by the rotation can be one that points back into the
            // already-laid chain — without this overlap check the double would land
            // on top of an earlier tile. Reject any candidate that intersects an
            // existing piece and keep rotating.
            boolean fits = inBounds && !overlapsExisting(pos);

            if (fits || attempt == 3) {
                positionsByTile.put(tile, pos);
                branch.lastTile = pos;
                // Doubles connect via both long edges (both pips equal), so after
                // a shifted double the chain "kinks" through it — outgoing axis
                // follows the double's centre, not the incoming axis. For a
                // centred (un-shifted) double this is a no-op.
                if (isDouble) {
                    if (horizontalChain) {
                        branch.endY = pos.y + pos.h / 2f;
                    } else {
                        branch.endX = pos.x + pos.w / 2f;
                    }
                }
                // Advance the connection point along the current direction.
                switch (branch.direction) {
                    case DIR_RIGHT: branch.endX = pos.x + boxW + gap; break;
                    case DIR_LEFT:  branch.endX = pos.x - gap; break;
                    case DIR_DOWN:  branch.endY = pos.y + boxH + gap; break;
                    case DIR_UP:    branch.endY = pos.y - gap; break;
                }
                return;
            }

            // Doesn't fit — turn the branch 90° and re-attempt.
            // The new chain axis must pass through the EXPOSED FACE CENTRE of the
            // last tile (in the old direction), NOT its geometric centre. Otherwise
            // a non-double last tile (say 2:3 ending with 3 on the right) would
            // anchor the new perpendicular row halfway between its 2 and 3 — the
            // user's exact complaint. With the face-centre fix the new tile attaches
            // squarely under the matching half.
            int newDirection = ((branch.direction + turnSign) % 4 + 4) % 4;
            TilePos lt = branch.lastTile;
            if (lt != null) {
                float halfFace = face / 2f;
                float exposedCx;
                float exposedCy;
                switch (branch.direction) {  // OLD direction → which face of lt is exposed
                    case DIR_RIGHT:
                        exposedCx = lt.x + lt.w - halfFace;
                        exposedCy = lt.y + lt.h / 2f;
                        break;
                    case DIR_LEFT:
                        exposedCx = lt.x + halfFace;
                        exposedCy = lt.y + lt.h / 2f;
                        break;
                    case DIR_DOWN:
                        exposedCx = lt.x + lt.w / 2f;
                        exposedCy = lt.y + lt.h - halfFace;
                        break;
                    case DIR_UP:
                    default:
                        exposedCx = lt.x + lt.w / 2f;
                        exposedCy = lt.y + halfFace;
                        break;
                }
                switch (newDirection) {
                    case DIR_RIGHT:
                        branch.endX = lt.x + lt.w + gap;
                        branch.endY = exposedCy;
                        break;
                    case DIR_LEFT:
                        branch.endX = lt.x - gap;
                        branch.endY = exposedCy;
                        break;
                    case DIR_DOWN:
                        branch.endX = exposedCx;
                        branch.endY = lt.y + lt.h + gap;
                        break;
                    case DIR_UP:
                        branch.endX = exposedCx;
                        branch.endY = lt.y - gap;
                        break;
                }
            }
            branch.direction = newDirection;
        }
    }

    /** True if {@code pos} intersects any tile we already placed on the board. */
    private boolean overlapsExisting(TilePos pos) {
        for (TilePos other : positionsByTile.values()) {
            if (other == null || other == pos) continue;
            if (pos.x + pos.w <= other.x) continue;
            if (pos.x >= other.x + other.w) continue;
            if (pos.y + pos.h <= other.y) continue;
            if (pos.y >= other.y + other.h) continue;
            return true;
        }
        return false;
    }

    // ---- view rendering ----

    private void rebuildChildren() {
        removeAllViews();
        if (renderer == null) return;
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = tiles.get(i);
            TilePos pos = positionsByTile.get(t);
            if (pos == null) continue;
            Tile renderTile = pos.flipPips ? new Tile(t.getB(), t.getA()) : t;
            View v = renderer.create(renderTile, pos.verticalOrientation, faceSizePx());
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Math.round(pos.x);
            lp.topMargin  = Math.round(pos.y);
            v.setLayoutParams(lp);
            addView(v);

            if (i == pendingAnimateIndex) {
                v.setAlpha(0f);
                v.setScaleX(0.3f);
                v.setScaleY(0.3f);
                v.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(220)
                        .setInterpolator(new OvershootInterpolator(1.6f))
                        .start();
            }
        }
        pendingAnimateIndex = -1;
    }
}
