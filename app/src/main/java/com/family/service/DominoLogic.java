package com.family.service;

import com.family.dto.GameState;
import com.family.dto.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure domino rules. All methods are static and side-effecting only on the passed
 * GameState — no Firebase, no UI. This makes the game easy to test and replays
 * deterministic given a shuffled deck.
 *
 *  - 28 tiles total (0-0 through 6-6).
 *  - 7 tiles per player. Leftover goes to the bazaar (2 or 3 players only; 4 players
 *    deal out the whole set).
 *  - First move: highest double; if no doubles, highest-sum tile.
 *  - On your turn you must play if you can. Otherwise draw from bazaar; if bazaar
 *    is empty, pass.
 *  - Round ends when someone empties their hand OR all players pass in a row (fish).
 *  - Losers add the sum of pips remaining in their hand to their score.
 *  - First player to reach targetScore loses the whole game.
 */
public final class DominoLogic {
    private DominoLogic() {}

    /**
     * House-rule rank order for opening doubles: 1-1 is the weakest (must be played
     * first if anyone holds it), then 2-2, 3-3, …, 6-6, and 0-0 is considered the
     * strongest of all doubles. So the round-opening tile is found by walking this
     * sequence in order and picking the first double present in any hand.
     */
    private static final int[] DOUBLE_RANK_ORDER = {1, 2, 3, 4, 5, 6, 0};

    /**
     * The tile (a double) that MUST be the first move of the current round, if any
     * player holds one. Returns null only when no hand contains a double at all
     * (extremely rare — usually only with 2 players).
     */
    public static Tile findRequiredOpeningTile(GameState state) {
        if (state == null || state.getHands() == null) return null;
        for (int v : DOUBLE_RANK_ORDER) {
            Tile dbl = new Tile(v, v);
            for (java.util.List<Tile> hand : state.getHands().values()) {
                if (hand != null && hand.contains(dbl)) return dbl;
            }
        }
        return null;
    }

    public static List<Tile> allTiles() {
        List<Tile> tiles = new ArrayList<>();
        for (int a = 0; a <= 6; a++) {
            for (int b = a; b <= 6; b++) {
                tiles.add(new Tile(a, b));
            }
        }
        return tiles; // 28 tiles
    }

    /**
     * Initialize a fresh round on an existing or new state. Carries cumulative scores
     * forward. Resets board, hands, bazaar, pass counter, round flags.
     */
    public static GameState newRound(List<String> playerIds, GameState previous) {
        GameState state = previous == null ? new GameState() : previous;
        state.setBoard(new ArrayList<>());
        state.setLeftEnd(-1);
        state.setRightEnd(-1);
        state.setHands(new HashMap<>());
        state.setBazaar(new ArrayList<>());
        state.setPassCount(0);
        state.setRoundFinished(false);
        state.setFish(false);
        state.setRoundWinnerId(null);
        state.setPlayerOrder(new ArrayList<>(playerIds));
        if (state.getScores() == null) {
            state.setScores(new HashMap<>());
        }
        for (String pid : playerIds) {
            if (!state.getScores().containsKey(pid)) {
                state.getScores().put(pid, 0);
            }
        }

        List<Tile> tiles = allTiles();
        Collections.shuffle(tiles);

        for (String pid : playerIds) {
            List<Tile> hand = new ArrayList<>(tiles.subList(0, 7));
            tiles = new ArrayList<>(tiles.subList(7, tiles.size()));
            state.getHands().put(pid, hand);
        }
        state.setBazaar(tiles);

        String firstPlayer = findFirstPlayer(state, playerIds);
        state.setCurrentTurnIndex(Math.max(0, playerIds.indexOf(firstPlayer)));
        return state;
    }

    private static String findFirstPlayer(GameState state, List<String> playerIds) {
        // Walk doubles in house-rule rank order (1-1 first, then 2-2…6-6, then 0-0
        // last) and pick the holder of the first one found.
        for (int v : DOUBLE_RANK_ORDER) {
            Tile dbl = new Tile(v, v);
            for (String pid : playerIds) {
                if (state.getHands().get(pid).contains(dbl)) {
                    return pid;
                }
            }
        }
        // No doubles in any hand (extremely rare for 4 players): lowest-sum tile.
        String best = playerIds.get(0);
        int bestSum = Integer.MAX_VALUE;
        for (String pid : playerIds) {
            for (Tile t : state.getHands().get(pid)) {
                int s = t.points();
                if (s < bestSum) {
                    bestSum = s;
                    best = pid;
                }
            }
        }
        return best;
    }

    public static boolean canPlay(GameState state, Tile tile) {
        if (state.getBoard() == null || state.getBoard().isEmpty()) {
            // First move: only the forced opening tile is legal (if any double exists).
            Tile required = findRequiredOpeningTile(state);
            return required == null || required.equals(tile);
        }
        return tile.matches(state.getLeftEnd()) || tile.matches(state.getRightEnd());
    }

    public static boolean canPlayerMakeMove(GameState state, String playerId) {
        List<Tile> hand = state.getHands().get(playerId);
        if (hand == null || hand.isEmpty()) return false;
        if (state.getBoard() == null || state.getBoard().isEmpty()) {
            // First move: only the holder of the required opening tile can move.
            Tile required = findRequiredOpeningTile(state);
            return required == null || hand.contains(required);
        }
        for (Tile t : hand) {
            if (canPlay(state, t)) return true;
        }
        return false;
    }

    /**
     * Place a tile on the requested side (true = left end, false = right end).
     * If the tile only matches the opposite side, transparently places it there.
     * Returns true on success.
     */
    public static boolean playTile(GameState state, String playerId, Tile tile, boolean leftSide) {
        List<Tile> hand = state.getHands().get(playerId);
        if (hand == null) return false;
        Tile inHand = null;
        for (Tile t : hand) {
            if (t.equals(tile)) { inHand = t; break; }
        }
        if (inHand == null) return false;

        if (state.getBoard().isEmpty()) {
            // Enforce the opening-double rule at the logic layer too — defence in depth
            // against a tampered client pushing a non-required first move.
            Tile required = findRequiredOpeningTile(state);
            if (required != null && !required.equals(inHand)) {
                return false;
            }
            hand.remove(inHand);
            state.getBoard().add(inHand);
            state.setLeftEnd(inHand.getA());
            state.setRightEnd(inHand.getB());
        } else {
            int targetEnd = leftSide ? state.getLeftEnd() : state.getRightEnd();
            if (!inHand.matches(targetEnd)) {
                int otherEnd = leftSide ? state.getRightEnd() : state.getLeftEnd();
                if (inHand.matches(otherEnd)) {
                    leftSide = !leftSide;
                    targetEnd = otherEnd;
                } else {
                    return false;
                }
            }
            hand.remove(inHand);
            int newEnd = inHand.otherSide(targetEnd);
            // Orient the tile so the matching face sits NEXT TO the chain.
            //   Right side: tile rendered (targetEnd | newEnd) — left face touches the chain.
            //   Left  side: tile rendered (newEnd | targetEnd) — right face touches the chain.
            // Saving the tile orientation in board is what makes the visual chain coherent.
            Tile oriented = leftSide ? new Tile(newEnd, targetEnd) : new Tile(targetEnd, newEnd);
            if (leftSide) {
                state.getBoard().add(0, oriented);
                state.setLeftEnd(newEnd);
            } else {
                state.getBoard().add(oriented);
                state.setRightEnd(newEnd);
            }
        }

        state.setPassCount(0);

        if (hand.isEmpty()) {
            state.setRoundFinished(true);
            state.setRoundWinnerId(playerId);
            scoreRound(state);
        } else {
            advanceTurn(state);
        }
        return true;
    }

    /** Draw one tile from the bazaar into the player's hand. Returns null if empty. */
    public static Tile drawFromBazaar(GameState state, String playerId) {
        if (state.getBazaar() == null || state.getBazaar().isEmpty()) return null;
        Tile drawn = state.getBazaar().remove(0);
        List<Tile> hand = state.getHands().get(playerId);
        if (hand == null) {
            hand = new ArrayList<>();
            state.getHands().put(playerId, hand);
        }
        hand.add(drawn);
        return drawn;
    }

    /** Current player passes. Increments pass counter; if it reaches player count, fish. */
    public static void pass(GameState state) {
        state.setPassCount(state.getPassCount() + 1);
        advanceTurn(state);
        if (state.getPassCount() >= state.getPlayerOrder().size()) {
            state.setRoundFinished(true);
            state.setFish(true);
            String winner = null;
            int minPoints = Integer.MAX_VALUE;
            for (String pid : state.getPlayerOrder()) {
                int s = handSum(state.getHands().get(pid));
                if (s < minPoints) {
                    minPoints = s;
                    winner = pid;
                }
            }
            state.setRoundWinnerId(winner);
            scoreRound(state);
        }
    }

    public static int handSum(List<Tile> hand) {
        if (hand == null) return 0;
        int sum = 0;
        for (Tile t : hand) sum += t.points();
        return sum;
    }

    private static void scoreRound(GameState state) {
        Map<String, Integer> scores = state.getScores();
        for (String pid : state.getPlayerOrder()) {
            if (pid.equals(state.getRoundWinnerId())) continue;
            int handPoints = handSum(state.getHands().get(pid));
            Integer current = scores.get(pid);
            scores.put(pid, (current == null ? 0 : current) + handPoints);
        }
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            if (e.getValue() >= state.getTargetScore()) {
                state.setFinished(true);
                state.setLoserId(e.getKey());
                break;
            }
        }
    }

    private static void advanceTurn(GameState state) {
        int next = (state.getCurrentTurnIndex() + 1) % state.getPlayerOrder().size();
        state.setCurrentTurnIndex(next);
    }
}
