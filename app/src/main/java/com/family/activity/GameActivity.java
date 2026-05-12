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
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
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
    private LinearLayout board;
    private LinearLayout myHand;
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
     * Pinch-to-zoom + double-tap-to-reset on the board. Long chains don't fit on a
     * phone screen at full size, so the user can spread two fingers to magnify a
     * detail or pinch to see the overall flow. Native HorizontalScrollView still
     * handles horizontal panning between gestures.
     *
     * Note: real chain wrapping with corner turns (a.k.a. "snake" layout) is a much
     * bigger change — it requires a custom ViewGroup that knows when to rotate the
     * next tile 90° and break the line. For an MVP, pinch-zoom solves the practical
     * problem of "I can't see the table".
     */
    private void setupBoardZoom() {
        HorizontalScrollView boardScroll = findViewById(R.id.board_scroll);
        ScaleGestureDetector scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector d) {
                        // Pivot on the gesture's focal point so zoom feels natural.
                        board.setPivotX(d.getFocusX());
                        board.setPivotY(d.getFocusY());
                        return true;
                    }
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        boardScale *= d.getScaleFactor();
                        boardScale = Math.max(0.5f, Math.min(boardScale, 2.5f));
                        board.setScaleX(boardScale);
                        board.setScaleY(boardScale);
                        return true;
                    }
                });
        GestureDetector tapDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        boardScale = 1f;
                        board.setScaleX(1f);
                        board.setScaleY(1f);
                        return true;
                    }
                });
        boardScroll.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            tapDetector.onTouchEvent(event);
            // Consume the event only while a pinch is active — otherwise let the
            // ScrollView pan as normal.
            return scaleDetector.isInProgress();
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

        if (matchesLeft && matchesRight && s.getLeftEnd() != s.getRightEnd()) {
            // Phrase the choice in terms of the matching value, not "left/right".
            // Two reasons:
            //   1. Android's positive button usually renders on the right of the dialog,
            //      so a button labelled "Слева" appears on the right side of the screen —
            //      confusing. A label like "К 5" is unambiguous regardless of position.
            //   2. When the future snake layout wraps the chain around corners, "left"
            //      and "right" no longer correspond to any visible direction — the
            //      chain's ends can sit anywhere on screen. Naming the value future-proofs
            //      this UI.
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Куда приложить?")
                    .setMessage("Костяшка подходит к обоим концам цепочки.")
                    .setPositiveButton("К " + s.getLeftEnd(),
                            (d, w) -> applyTilePlay(tile, true))
                    .setNegativeButton("К " + s.getRightEnd(),
                            (d, w) -> applyTilePlay(tile, false))
                    .show();
            return;
        }
        applyTilePlay(tile, matchesLeft);
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
        board.removeAllViews();
        if (s.getBoard() == null) return;
        int size = s.getBoard().size();
        for (int i = 0; i < size; i++) {
            View tileView = createTileView(s.getBoard().get(i), false, false);
            boolean shouldAnimate = (i == 0 && animateLeft)
                    || (i == size - 1 && animateRight);
            if (shouldAnimate) {
                animateTileEntry(tileView);
            }
            board.addView(tileView);
        }
        board.post(() -> {
            View parent = (View) board.getParent();
            if (parent instanceof android.widget.HorizontalScrollView) {
                // Scroll to whichever end the new tile landed on so the player can see it.
                ((android.widget.HorizontalScrollView) parent)
                        .fullScroll(animateLeft ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
            }
        });
    }

    /**
     * Tile entry animation: start at 30% size + invisible, snap to 100% with a slight
     * overshoot. Quick (200ms) so it doesn't slow down the game pace, but enough to
     * draw the eye to where the new tile landed.
     */
    private void animateTileEntry(View tileView) {
        tileView.setAlpha(0f);
        tileView.setScaleX(0.3f);
        tileView.setScaleY(0.3f);
        tileView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f))
                .start();
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
        int faceDp = inHand ? 40 : 32;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ContextCompat.getColor(this, R.color.domino_surface));
        int strokeColor = playable
                ? ContextCompat.getColor(this, R.color.domino_primary)
                : inHand
                    ? ContextCompat.getColor(this, R.color.domino_on_surface_muted)
                    : ContextCompat.getColor(this, R.color.domino_outline);
        // Playable tiles get a noticeably thicker border so they pop out of the row
        // at a glance — 3dp vs 1dp for the rest. 4dp felt too "fat" against the small
        // face artwork; 3dp is the sweet spot.
        bg.setStroke(dp(playable ? 3 : 1), strokeColor);
        bg.setCornerRadius(dp(4));
        container.setBackground(bg);

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

    private String playerName(String playerId) {
        if (playerId == null || game == null) return "—";
        for (Player p : game.getPlayers()) {
            if (playerId.equals(p.getId())) return p.getName();
        }
        return "—";
    }
}
