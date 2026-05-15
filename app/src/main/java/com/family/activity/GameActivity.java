package com.family.activity;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.family.app.R;
import com.family.dto.Game;
import com.family.dto.GameState;
import com.family.dto.Player;
import com.family.dto.Tile;
import com.family.service.DominoLogic;
import com.family.service.SoundManager;
import com.family.utils.UserUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The actual playable domino screen.
 *
 * Concurrency model:
 *   Every state mutation runs through {@link #applyMove(GameTransform)} which uses
 *   Firebase Realtime Database's {@code runTransaction}. The handler re-reads the
 *   current Game on the server, applies the transform, and either commits or aborts.
 *   This gives us atomic read-modify-write, so a stale local snapshot cannot
 *   overwrite a newer move by another player. The same machinery covers leave/end
 *   actions.
 *
 * Authorization:
 *   The transform itself re-checks "is this still my turn?" against the server-fresh
 *   state. If the local user lost their turn between tapping and the transaction
 *   firing, the move is aborted. This is the strongest verification we can do
 *   without a real server (Firebase Database Rules would be the next layer).
 */
public class GameActivity extends AppCompatActivity {
    public static final String EXTRA_GAME_ID = "game_id";

    private String gameId;
    private String userId;
    private Game game;
    private ValueEventListener gameListener;

    private TextView statusText;
    private TextView scoresText;
    private LinearLayout playersInfo;
    private DominoBoardView board;
    private LinearLayout myHand;
    private TextView handHint;
    private TextView bazaarInfo;
    private MaterialButton drawButton;
    private MaterialButton nextRoundButton;
    private MaterialButton leaveButton;

    /** Cached tile-face drawables keyed by value (0..6). Loaded lazily from assets. */
    private final Map<Integer, Drawable> faceCache = new HashMap<>();
    /** Once the game-over dialog has been shown we don't pop it again on every tick. */
    private boolean finishedDialogShown = false;
    /** Current board zoom factor, preserved across config changes. */
    private float boardScale = 1f;
    /** Pan offset applied via setTranslationX/Y while zoomed in. */
    private float boardTransX = 0f;
    private float boardTransY = 0f;

    private SoundManager soundManager;

    // Snapshots from the previous render() tick. We use these to detect "what just
    // happened" — a new tile played left/right, or a round just finished — and trigger
    // the corresponding sound + entry animation. Without this diff, we'd play the
    // sound on every listener tick (including pure refreshes from other clients).
    private int previousBoardSize = 0;
    private int previousLeftEnd = -1;
    private boolean previousRoundFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.game_activity);

        gameId = getIntent().getStringExtra(EXTRA_GAME_ID);
        userId = UserUtils.resolveUserId(getApplicationContext());

        statusText = findViewById(R.id.status_text);
        scoresText = findViewById(R.id.scores_text);
        playersInfo = findViewById(R.id.players_info);
        board = findViewById(R.id.board);
        myHand = findViewById(R.id.my_hand);
        handHint = findViewById(R.id.hand_hint);
        bazaarInfo = findViewById(R.id.bazaar_info);
        drawButton = findViewById(R.id.draw_button);
        nextRoundButton = findViewById(R.id.next_round_button);
        leaveButton = findViewById(R.id.leave_button);

        drawButton.setOnClickListener(v -> onDrawOrPass());
        nextRoundButton.setOnClickListener(v -> onNextRound());
        leaveButton.setOnClickListener(v -> handleLeave());

        setupBoardZoom();
        soundManager = new SoundManager(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundManager != null) {
            soundManager.release();
            soundManager = null;
        }
    }

    /**
     * Pinch to zoom, double-tap to reset, drag with one finger to pan when zoomed.
     * Replaces the previous HorizontalScrollView-based setup — the snake layout now
     * fills the whole board, so panning is done via View.setTranslationX/Y instead
     * of a scroll container.
     */
    private void setupBoardZoom() {
        ScaleGestureDetector scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector d) {
                        board.setPivotX(d.getFocusX());
                        board.setPivotY(d.getFocusY());
                        return true;
                    }
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        boardScale *= d.getScaleFactor();
                        boardScale = Math.max(0.5f, Math.min(boardScale, 3.0f));
                        board.setScaleX(boardScale);
                        board.setScaleY(boardScale);
                        return true;
                    }
                });
        GestureDetector gestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        // Reset both zoom and pan on double tap.
                        boardScale = 1f;
                        boardTransX = 0f;
                        boardTransY = 0f;
                        board.setScaleX(1f);
                        board.setScaleY(1f);
                        board.setTranslationX(0f);
                        board.setTranslationY(0f);
                        return true;
                    }
                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                        // Only pan when zoomed in — otherwise drags don't make sense
                        // (chain fits, no need to scroll).
                        if (boardScale <= 1.02f) return false;
                        boardTransX -= dx;
                        boardTransY -= dy;
                        board.setTranslationX(boardTransX);
                        board.setTranslationY(boardTransY);
                        return true;
                    }
                });
        board.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            return true; // we own touches on the board area
        });
    }

    @Override
    public void onBackPressed() {
        // Back button mirrors the "Выйти/Закончить" button — never leaves silently,
        // because doing so would loop: MainActivity sees my still-started game and
        // re-launches this activity. A user wanting to actually exit must either
        // leave the team (guest) or end the game (boss).
        handleLeave();
    }

    @Override
    protected void onResume() {
        super.onResume();
        finishedDialogShown = false;
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("games").child(gameId);
        gameListener = ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Game g = snapshot.getValue(Game.class);
                if (g == null) {
                    // Game removed (boss ended the game) — back to lobby.
                    finish();
                    return;
                }
                game = g;
                render();
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameListener != null) {
            FirebaseDatabase.getInstance().getReference("games").child(gameId)
                    .removeEventListener(gameListener);
            gameListener = null;
        }
    }

    // --- Player actions ---------------------------------------------------

    private void onDrawOrPass() {
        if (game == null || game.getState() == null) return;
        GameState s = game.getState();
        // Sanity-check locally first to avoid hammering Firebase on bad taps.
        if (s.isRoundFinished()) return;
        if (DominoLogic.canPlayerMakeMove(s, userId)) return; // must play instead

        boolean bazaarLeft = s.getBazaar() != null && !s.getBazaar().isEmpty();
        applyMove(g -> {
            GameState gs = g.getState();
            if (gs == null || gs.isRoundFinished()) return false;
            String current = gs.getPlayerOrder().get(gs.getCurrentTurnIndex());
            if (!userId.equals(current)) return false;
            if (DominoLogic.canPlayerMakeMove(gs, userId)) return false;
            if (gs.getBazaar() != null && !gs.getBazaar().isEmpty()) {
                DominoLogic.drawFromBazaar(gs, userId);
            } else {
                DominoLogic.pass(gs);
            }
            return true;
        });
    }

    private void onTileTap(Tile tile) {
        if (!isMyTurnAndPlayable()) return;
        GameState s = game.getState();

        if (s.getBoard().isEmpty()) {
            applyTilePlay(tile, false);
            return;
        }
        boolean matchesLeft = tile.matches(s.getLeftEnd());
        boolean matchesRight = tile.matches(s.getRightEnd());
        if (!matchesLeft && !matchesRight) return;

        // Double-double combo (house rule): if the tapped tile is a double matching
        // one end only AND the hand also holds another double matching the OTHER end,
        // both doubles must play together in a single turn. Handled before the
        // bridge/auto-balance branches because those only run when matchesLeft
        // && matchesRight, which doesn't apply here.
        if (tile.getA() == tile.getB() && matchesLeft != matchesRight) {
            int otherEnd = matchesLeft ? s.getRightEnd() : s.getLeftEnd();
            Tile otherDouble = findOtherDouble(s.getHands().get(userId), otherEnd, tile);
            if (otherDouble != null) {
                applyDoubleDoublePlay(tile, matchesLeft, otherDouble, !matchesLeft);
                return;
            }
        }

        if (matchesLeft && matchesRight) {
            if (s.getLeftEnd() == s.getRightEnd()) {
                // Both open ends carry the same pip value, so the player's choice is
                // visually ambiguous — auto-place on the shorter branch (counted from
                // the centre anchor) to keep the snake balanced.
                int leftCount = s.getAnchorIndex();
                int rightCount = s.getBoard().size() - 1 - s.getAnchorIndex();
                applyTilePlay(tile, leftCount < rightCount);
                return;
            }
            // Ends differ — let the player pick. The dialog only fires when the tile is
            // the exact bridge (leftEnd|rightEnd), so each placement unifies the chain
            // on the OPPOSITE end's value: placing on the left exposes the tile's other
            // face (= rightEnd), making both ends equal to rightEnd, and symmetrically
            // for the right placement.
            showPickSideDialog(tile, s);
            return;
        }
        applyTilePlay(tile, matchesLeft);
    }

    /**
     * Compact pick-side dialog: a horizontal pair of buttons rather than a full
     * AlertDialog. Two reasons:
     *  - Without title/message, MaterialAlertDialog still reserves the content
     *    area, leaving a big empty band on the left of the buttons. wrap_content
     *    on a custom view collapses the dialog to exactly the buttons' width.
     *  - The dialog is anchored to the bottom edge of the board card, so it sits
     *    just above the hand strip — close to where the player just tapped, not
     *    floating in the centre of the screen.
     */
    private void showPickSideDialog(Tile tile, GameState s) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(dp(8), dp(8), dp(8), dp(8));

        MaterialButton btnPositive = new MaterialButton(this);
        btnPositive.setText(sideName(s.getRightEnd()));
        LinearLayout.LayoutParams posLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        posLp.setMarginEnd(dp(8));
        btnPositive.setLayoutParams(posLp);
        content.addView(btnPositive);

        MaterialButton btnNegative = new MaterialButton(this);
        btnNegative.setText(sideName(s.getLeftEnd()));
        content.addView(btnNegative);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .create();

        btnPositive.setOnClickListener(v -> { dialog.dismiss(); applyTilePlay(tile, true); });
        btnNegative.setOnClickListener(v -> { dialog.dismiss(); applyTilePlay(tile, false); });

        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w == null) return;
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
            // Position the dialog's bottom edge a hair above the board's bottom edge.
            // board has already been laid out by the time the user could tap a tile.
            int[] loc = new int[2];
            board.getLocationOnScreen(loc);
            int boardBottom = loc[1] + board.getHeight();
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            lp.y = Math.max(dp(8), (screenHeight - boardBottom) + dp(8));
            // Dim the rest of the screen less aggressively — the dialog is small and
            // sits near the action; a heavy scrim would obscure the board context.
            lp.dimAmount = 0.2f;
            w.setAttributes(lp);
        });
        dialog.show();
    }

    private static String sideName(int value) {
        switch (value) {
            case 0: return "По черепахе";
            case 1: return "По широкой";
            case 2: return "По крабу";
            case 3: return "По зеку";
            case 4: return "По дельфину";
            case 5: return "По золотой";
            case 6: return "По коню";
            default: return "К " + value;
        }
    }

    private void applyTilePlay(Tile tile, boolean leftSide) {
        applyMove(g -> {
            GameState gs = g.getState();
            if (gs == null || gs.isRoundFinished()) return false;
            String current = gs.getPlayerOrder().get(gs.getCurrentTurnIndex());
            if (!userId.equals(current)) return false;
            return DominoLogic.playTile(gs, userId, tile, leftSide);
        });
    }

    /**
     * Place two doubles in a single atomic turn: one on each end of the chain.
     * playTile() advances the turn after each play, so we rewind it between the
     * two plays — the combo counts as one move.
     */
    private void applyDoubleDoublePlay(Tile first, boolean firstLeftSide,
                                       Tile second, boolean secondLeftSide) {
        applyMove(g -> {
            GameState gs = g.getState();
            if (gs == null || gs.isRoundFinished()) return false;
            String current = gs.getPlayerOrder().get(gs.getCurrentTurnIndex());
            if (!userId.equals(current)) return false;
            int turnBefore = gs.getCurrentTurnIndex();
            if (!DominoLogic.playTile(gs, userId, first, firstLeftSide)) return false;
            // First play already won the round — don't play the second, just commit.
            if (gs.isRoundFinished()) return true;
            gs.setCurrentTurnIndex(turnBefore);
            return DominoLogic.playTile(gs, userId, second, secondLeftSide);
        });
    }

    /** Find a double in {@code hand} (other than {@code exclude}) that matches {@code targetEnd}. */
    private static Tile findOtherDouble(List<Tile> hand, int targetEnd, Tile exclude) {
        if (hand == null) return null;
        for (Tile t : hand) {
            if (t.equals(exclude)) continue;
            if (t.getA() != t.getB()) continue;
            if (t.matches(targetEnd)) return t;
        }
        return null;
    }

    private void onNextRound() {
        if (game == null || game.getState() == null) return;
        if (!isMyselfBoss()) return;
        applyMove(g -> {
            if (g.getState() == null || g.getState().isFinished()) return false;
            if (!g.getState().isRoundFinished()) return false;
            if (g.getBossId() == null || !g.getBossId().equals(userId)) return false;
            List<String> ids = new ArrayList<>();
            for (Player p : g.getPlayers()) ids.add(p.getId());
            g.setState(DominoLogic.newRound(ids, g.getState()));
            return true;
        });
    }

    private void handleLeave() {
        if (game == null) {
            super.onBackPressed();
            return;
        }
        if (isMyselfBoss()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Закончить игру?")
                    .setMessage("Все участники вернутся в лобби, команда будет распущена.")
                    .setPositiveButton("Закончить", (d, w) -> endGameAsBoss())
                    .setNegativeButton("Отмена", null)
                    .show();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Выйти из игры?")
                    .setMessage("Вы покинете команду. Игра продолжится для остальных.")
                    .setPositiveButton("Выйти", (d, w) -> leaveAsGuest())
                    .setNegativeButton("Отмена", null)
                    .show();
        }
    }

    private void endGameAsBoss() {
        // Boss tears down the whole game. Other players' listeners fire with null
        // and bounce back to the lobby. Doing this through a transaction guarantees
        // the removal happens atomically with whatever in-flight moves exist.
        FirebaseDatabase.getInstance().getReference("games").child(gameId)
                .removeValue((error, ref) -> finish());
    }

    private void leaveAsGuest() {
        applyMove(g -> {
            // Remove me from the lobby players list...
            if (g.getPlayers() != null) {
                for (int i = g.getPlayers().size() - 1; i >= 0; i--) {
                    if (userId.equals(g.getPlayers().get(i).getId())) {
                        g.getPlayers().remove(i);
                    }
                }
            }
            // ...and from the runtime state.
            GameState s = g.getState();
            if (s != null) {
                if (s.getHands() != null) s.getHands().remove(userId);
                if (s.getScores() != null) s.getScores().remove(userId);
                if (s.getPlayerOrder() != null) {
                    s.getPlayerOrder().remove(userId);
                    if (!s.getPlayerOrder().isEmpty()
                            && s.getCurrentTurnIndex() >= s.getPlayerOrder().size()) {
                        s.setCurrentTurnIndex(0);
                    }
                }
            }
            return true;
        });
        finish();
    }

    private boolean isMyTurnAndPlayable() {
        if (game == null || game.getState() == null) return false;
        GameState s = game.getState();
        if (s.isRoundFinished()) return false;
        List<String> order = s.getPlayerOrder();
        if (order == null || order.isEmpty()) return false;
        String current = order.get(s.getCurrentTurnIndex());
        return userId.equals(current);
    }

    private boolean isMyselfBoss() {
        return game != null && game.getBossId() != null && game.getBossId().equals(userId);
    }

    /** Functional interface for a state transform applied inside a Firebase transaction. */
    private interface GameTransform {
        /** Mutate the game. Return false to abort (no Firebase write). */
        boolean apply(Game game);
    }

    /**
     * Run a state transform atomically: server re-read, transform, commit-or-abort.
     * Firebase will retry our transform if the server data changed under us. If the
     * transform returns false (invalid move / not my turn anymore), the transaction
     * aborts and nothing is written.
     */
    private void applyMove(GameTransform transform) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("games").child(gameId);
        ref.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                Game current = currentData.getValue(Game.class);
                if (current == null) {
                    return Transaction.success(currentData);
                }
                if (!transform.apply(current)) {
                    return Transaction.abort();
                }
                currentData.setValue(current);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                // No-op: the value listener will deliver the new state to render().
            }
        });
    }

    // --- Rendering --------------------------------------------------------

    private void render() {
        if (game == null || game.getState() == null) return;
        GameState s = game.getState();

        // Detect transitions since the previous tick so we play sounds/animations
        // only for actual events, not on every listener fire-through.
        int newBoardSize = s.getBoard() == null ? 0 : s.getBoard().size();
        boolean tileAdded = newBoardSize > previousBoardSize;
        // If the left end value changed, the new tile went on the LEFT side.
        // Otherwise it went on the right (or this is the first ever tile).
        boolean addedOnLeft = tileAdded && s.getLeftEnd() != previousLeftEnd && previousBoardSize > 0;
        boolean addedOnRight = tileAdded && !addedOnLeft;
        boolean roundJustEnded = s.isRoundFinished() && !previousRoundFinished;

        if (tileAdded && soundManager != null) {
            soundManager.playTile();
        }
        if (roundJustEnded && soundManager != null) {
            soundManager.playRoundWin();
        }

        previousBoardSize = newBoardSize;
        previousLeftEnd = s.getLeftEnd();
        previousRoundFinished = s.isRoundFinished();

        renderStatus(s);
        renderScores(s);
        renderPlayersInfo(s);
        renderBoard(s, addedOnLeft, addedOnRight);
        renderMyHand(s);
        renderControls(s);
        renderHandHint(s);

        if (s.isFinished()) {
            showFinishedDialogOnce(s);
        }
    }

    private void showFinishedDialogOnce(GameState s) {
        if (finishedDialogShown) return;
        finishedDialogShown = true;
        String loserName = playerName(s.getLoserId());
        Integer loserScore = s.getScores() == null ? null : s.getScores().get(s.getLoserId());
        new MaterialAlertDialogBuilder(this)
                .setTitle("Игра окончена")
                .setMessage(loserName + " проиграл с " + (loserScore == null ? "?" : loserScore) + " очками.")
                .setPositiveButton("OK", (d, w) -> {})
                .setCancelable(false)
                .show();
    }

    private void renderStatus(GameState s) {
        if (s.isFinished()) {
            statusText.setText("Игра окончена. Проиграл: " + playerName(s.getLoserId()));
        } else if (s.isRoundFinished()) {
            String prefix = s.isFish() ? "Рыба! " : "";
            statusText.setText(prefix + "Раунд окончен. Победил: " + playerName(s.getRoundWinnerId()));
        } else {
            String current = s.getPlayerOrder().get(s.getCurrentTurnIndex());
            statusText.setText(userId.equals(current) ? "Ваш ход" : "Ход: " + playerName(current));
        }
    }

    private void renderScores(GameState s) {
        StringBuilder sb = new StringBuilder("Счёт до ").append(s.getTargetScore()).append(": ");
        boolean first = true;
        for (String pid : s.getPlayerOrder()) {
            if (!first) sb.append(" · ");
            first = false;
            Integer score = s.getScores() == null ? null : s.getScores().get(pid);
            sb.append(playerName(pid)).append(' ').append(score == null ? 0 : score);
        }
        scoresText.setText(sb.toString());
    }

    private void renderPlayersInfo(GameState s) {
        playersInfo.removeAllViews();
        String currentPid = s.getPlayerOrder().get(s.getCurrentTurnIndex());
        for (String pid : s.getPlayerOrder()) {
            if (pid.equals(userId)) continue;
            int handSize = s.getHands().get(pid) != null ? s.getHands().get(pid).size() : 0;
            TextView tv = new TextView(this);
            tv.setText(playerName(pid) + ": " + handSize + " 🂠");
            tv.setPadding(dp(12), dp(6), dp(12), dp(6));
            tv.setTextSize(13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            tv.setLayoutParams(lp);
            // Highlight whose turn it is.
            if (pid.equals(currentPid) && !s.isRoundFinished()) {
                tv.setBackgroundResource(R.color.domino_primary_light);
                tv.setTextColor(ContextCompat.getColor(this, R.color.domino_primary_dark));
            } else {
                tv.setBackgroundResource(R.color.domino_surface_variant);
                tv.setTextColor(ContextCompat.getColor(this, R.color.domino_on_surface));
            }
            playersInfo.addView(tv);
        }
    }

    private void renderBoard(GameState s, boolean animateLeft, boolean animateRight) {
        java.util.List<Tile> chain = s.getBoard() == null
                ? new java.util.ArrayList<>()
                : s.getBoard();
        // Mark which tile gets the entry animation (newly added, on the side it appeared on).
        int animateIndex = -1;
        int size = chain.size();
        if (animateLeft && size > 0) animateIndex = 0;
        else if (animateRight && size > 0) animateIndex = size - 1;

        // Delegate snake layout to DominoBoardView. We supply the per-tile renderer
        // because GameActivity owns the visual style (assets, badges, borders).
        board.setTiles(chain, animateIndex, (tile, verticalOrientation, faceSizePx) ->
                createBoardTileView(tile, verticalOrientation, faceSizePx));
    }

    /**
     * Render one board tile at the orientation chosen by DominoBoardView's snake
     * algorithm. Unlike hand tiles (always vertical, see renderMyHand), board tiles
     * follow the chain — horizontal/vertical depending on segment direction and
     * whether the tile is a double.
     */
    private View createBoardTileView(Tile tile, boolean verticalOrientation, int faceSizePx) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(verticalOrientation ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ContextCompat.getColor(this, R.color.domino_surface));
        bg.setStroke(dp(1), ContextCompat.getColor(this, R.color.domino_outline));
        bg.setCornerRadius(dp(4));
        container.setBackground(bg);

        int innerPadding = dp(1);
        container.setPadding(innerPadding, innerPadding, innerPadding, innerPadding);

        container.addView(createFacePx(tile.getA(), faceSizePx));
        View strip = new View(this);
        LinearLayout.LayoutParams stripLp = verticalOrientation
                ? new LinearLayout.LayoutParams(faceSizePx, dp(1))
                : new LinearLayout.LayoutParams(dp(1), faceSizePx);
        strip.setLayoutParams(stripLp);
        strip.setBackgroundResource(R.color.domino_on_surface_muted);
        container.addView(strip);
        container.addView(createFacePx(tile.getB(), faceSizePx));
        return container;
    }

    /** Same shape as createFace but accepts size in pixels (used by the snake board). */
    private android.widget.FrameLayout createFacePx(int value, int sizePx) {
        android.widget.FrameLayout fl = new android.widget.FrameLayout(this);
        fl.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));

        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        Drawable d = getFaceDrawable(value);
        if (d != null) iv.setImageDrawable(d);
        fl.addView(iv);

        TextView badge = new TextView(this);
        android.widget.FrameLayout.LayoutParams nlp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        nlp.gravity = Gravity.TOP | Gravity.START;
        nlp.setMargins(dp(1), 0, 0, 0);
        badge.setLayoutParams(nlp);
        badge.setText(String.valueOf(value));
        badge.setTextSize(9);
        badge.setTextColor(ContextCompat.getColor(this, R.color.domino_primary_dark));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0xCCFFFFFF);
        badgeBg.setCornerRadius(dp(3));
        badge.setBackground(badgeBg);
        badge.setPadding(dp(2), 0, dp(2), 0);
        fl.addView(badge);

        return fl;
    }

    private void renderMyHand(GameState s) {
        myHand.removeAllViews();
        List<Tile> hand = s.getHands().get(userId);
        if (hand == null) return;
        boolean myTurn = isMyTurnAndPlayable();
        for (Tile t : hand) {
            boolean playable = myTurn && DominoLogic.canPlay(s, t);
            View tile = createTileView(t, true, playable);
            if (playable) {
                tile.setOnClickListener(v -> onTileTap(t));
            }
            myHand.addView(tile);
        }
    }

    private void renderControls(GameState s) {
        boolean myTurn = isMyTurnAndPlayable();
        boolean canMove = myTurn && DominoLogic.canPlayerMakeMove(s, userId);
        boolean bazaarLeft = s.getBazaar() != null && !s.getBazaar().isEmpty();

        bazaarInfo.setText(bazaarLeft ? "Базар: " + s.getBazaar().size() : "Базар пуст");

        // Single button: take from bazaar if there's anything to take; otherwise pass.
        // The player cannot freely pass when there's a tile to draw — that's against
        // the rules and would lock the game into a premature fish.
        drawButton.setEnabled(myTurn && !canMove);
        drawButton.setText(bazaarLeft ? "Взять" : "Пропустить");

        nextRoundButton.setVisibility(
                isMyselfBoss() && s.isRoundFinished() && !s.isFinished() ? View.VISIBLE : View.GONE);
        leaveButton.setText(isMyselfBoss() ? "Закончить игру" : "Выйти");
    }

    /**
     * Overlay a semi-transparent banner on the hand strip so the player sees the
     * critical state right where their attention is (on their tiles), instead of
     * having to glance at the top status row. Three messages worth surfacing:
     *
     *   - It's your turn but you have no legal moves: prompt to draw or pass.
     *   - The round just ended: who won (green for you, blue for someone else).
     *   - The whole game ended: winner/loser (green if you didn't lose, red if
     *     you're the one who hit the target score).
     *
     * Anything else (normal play with legal moves, or somebody else's turn) → hide.
     */
    private void renderHandHint(GameState s) {
        if (handHint == null) return;
        String text = null;
        int bgColor = ContextCompat.getColor(this, R.color.domino_primary);

        if (s.isFinished()) {
            if (userId.equals(s.getLoserId())) {
                text = "Вы проиграли!";
                bgColor = ContextCompat.getColor(this, R.color.domino_error);
            } else {
                text = "Игра окончена. Проиграл: " + playerName(s.getLoserId());
                bgColor = ContextCompat.getColor(this, R.color.domino_success);
            }
        } else if (s.isRoundFinished()) {
            String prefix = s.isFish() ? "Рыба! " : "";
            if (userId.equals(s.getRoundWinnerId())) {
                text = prefix + "Вы выиграли раунд!";
                bgColor = ContextCompat.getColor(this, R.color.domino_success);
            } else {
                text = prefix + "Раунд выиграл: " + playerName(s.getRoundWinnerId());
            }
        } else if (isMyTurnAndPlayable() && !DominoLogic.canPlayerMakeMove(s, userId)) {
            boolean bazaarLeft = s.getBazaar() != null && !s.getBazaar().isEmpty();
            text = bazaarLeft ? "Нет хода — возьмите из базара" : "Нет хода — пропустите ход";
        }

        if (text == null) {
            handHint.setVisibility(View.GONE);
            return;
        }
        handHint.setText(text);
        GradientDrawable bg = new GradientDrawable();
        // 90% alpha — tiles underneath stay visible through the banner.
        bg.setColor((0xE6 << 24) | (bgColor & 0x00FFFFFF));
        bg.setCornerRadius(dp(20));
        handHint.setBackground(bg);
        handHint.setVisibility(View.VISIBLE);
    }

    // --- Tile rendering ---------------------------------------------------

    /**
     * Build a tile view: two faces from assets/images/domino_N.png with the value
     * painted as a small numeric badge on top.
     *
     * Orientation:
     *  - In the player's hand: always vertical (a face on top, b face below), so a
     *    long hand fits a narrow phone screen by stacking thin column-tiles.
     *  - On the board: horizontal for normal tiles, vertical for doubles — doubles
     *    sit perpendicular to the chain by convention.
     *
     * Borders:
     *  - inHand + playable: thick primary-blue stroke = "you can play this".
     *  - inHand non-playable: thin muted stroke.
     *  - on the board: thin outline.
     * The strokes ensure tiles don't blend into the card or background on any device.
     */
    private View createTileView(Tile tile, boolean inHand, boolean playable) {
        boolean isDouble = tile.getA() == tile.getB();
        boolean vertical = inHand || isDouble;
        // Hand tiles scale up on tablets — the default 40dp leaves a lot of empty
        // strip on sw≥600dp devices.
        int faceDp = inHand ? handFaceDp() : 32;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        // Highlight the playable hand tiles with a tinted background so they pop
        // against the row of dimmed unplayable tiles. On the board (inHand=false)
        // and for non-playable hand tiles we keep the plain surface colour.
        int bgColor = (inHand && playable)
                ? ContextCompat.getColor(this, R.color.domino_surface_variant)
                : ContextCompat.getColor(this, R.color.domino_surface);
        bg.setColor(bgColor);
        int strokeColor = playable
                ? ContextCompat.getColor(this, R.color.domino_primary)
                : inHand
                    ? ContextCompat.getColor(this, R.color.domino_on_surface_muted)
                    : ContextCompat.getColor(this, R.color.domino_outline);
        // Playable tiles get a much thicker border so they pop out of the row at a
        // glance: 4dp vs 1dp for the rest.
        bg.setStroke(dp(playable ? 4 : 1), strokeColor);
        bg.setCornerRadius(dp(4));
        container.setBackground(bg);
        // Dim non-playable hand tiles so the eye lands on the legal moves. Full
        // alpha for board tiles regardless — they're not interactive.
        if (inHand) {
            container.setAlpha(playable ? 1f : 0.45f);
        }

        int innerPadding = dp(2);
        container.setPadding(innerPadding, innerPadding, innerPadding, innerPadding);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(3), dp(3), dp(3), dp(3));
        container.setLayoutParams(clp);

        container.addView(createFace(tile.getA(), faceDp));
        // Separator line between the two halves of the tile.
        View strip = new View(this);
        LinearLayout.LayoutParams stripLp = vertical
                ? new LinearLayout.LayoutParams(dp(faceDp), dp(1))
                : new LinearLayout.LayoutParams(dp(1), dp(faceDp));
        strip.setLayoutParams(stripLp);
        strip.setBackgroundResource(R.color.domino_on_surface_muted);
        container.addView(strip);
        container.addView(createFace(tile.getB(), faceDp));
        return container;
    }

    /**
     * One face = picture (loaded from assets) with the numeric value as a small badge
     * overlaid in the top-left. The number is the primary cue: even if the artwork
     * is hard to read at small sizes, the digit makes the value unambiguous.
     */
    private FrameLayout createFace(int value, int faceDp) {
        FrameLayout fl = new FrameLayout(this);
        fl.setLayoutParams(new LinearLayout.LayoutParams(dp(faceDp), dp(faceDp)));

        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable d = getFaceDrawable(value);
        if (d != null) iv.setImageDrawable(d);
        fl.addView(iv);

        TextView numberBadge = new TextView(this);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        nlp.gravity = Gravity.TOP | Gravity.START;
        nlp.setMargins(dp(2), dp(1), 0, 0);
        numberBadge.setLayoutParams(nlp);
        numberBadge.setText(String.valueOf(value));
        numberBadge.setTextSize(11);
        numberBadge.setTextColor(ContextCompat.getColor(this, R.color.domino_primary_dark));
        // Semi-transparent badge background keeps the digit legible over any artwork.
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0xCCFFFFFF);
        badgeBg.setCornerRadius(dp(4));
        numberBadge.setBackground(badgeBg);
        numberBadge.setPadding(dp(3), 0, dp(3), 0);
        fl.addView(numberBadge);

        return fl;
    }

    private Drawable getFaceDrawable(int value) {
        Drawable d = faceCache.get(value);
        if (d != null) return d;
        try (InputStream is = getAssets().open("images/domino_" + value + ".png")) {
            d = Drawable.createFromStream(is, "domino_" + value);
            faceCache.put(value, d);
            return d;
        } catch (IOException e) {
            return null;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int handFaceDp() {
        // Three tiers: phone (40), mid-size tablet (48), large tablet (56). The
        // jump from 40→56 on every sw≥600 device made medium tablets feel too
        // chunky relative to the board.
        int sw = getResources().getConfiguration().smallestScreenWidthDp;
        if (sw >= 720) return 56;
        if (sw >= 600) return 48;
        return 40;
    }

    private String playerName(String playerId) {
        if (playerId == null || game == null) return "—";
        for (Player p : game.getPlayers()) {
            if (playerId.equals(p.getId())) return p.getName();
        }
        return "—";
    }
}
