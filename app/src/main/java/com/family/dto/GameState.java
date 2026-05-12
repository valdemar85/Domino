package com.family.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime state of a domino round + cumulative scores across rounds.
 * Serialized into Firebase as a nested child of Game.
 */
public class GameState {
    private List<Tile> board = new ArrayList<>();
    /** Open value on the left end of the chain. -1 if board is empty. */
    private int leftEnd = -1;
    /** Open value on the right end of the chain. -1 if board is empty. */
    private int rightEnd = -1;
    /** playerId -> tiles in that player's hand. */
    private Map<String, List<Tile>> hands = new HashMap<>();
    /** Remaining tiles to draw from. Empty when 4 players (all dealt). */
    private List<Tile> bazaar = new ArrayList<>();
    /** Order of player IDs around the table; turn rotation goes through this list. */
    private List<String> playerOrder = new ArrayList<>();
    /** Index into playerOrder for whose turn it currently is. */
    private int currentTurnIndex = 0;
    /** playerId -> running total points across rounds. */
    private Map<String, Integer> scores = new HashMap<>();
    /** Reaching or exceeding this loses the game. */
    private int targetScore = 101;
    /** Consecutive passes; equals players.size() means fish. */
    private int passCount = 0;
    /** True when current round has ended (someone emptied a hand, or fish). */
    private boolean roundFinished = false;
    /** Whose tile fell — the round winner; on fish, player with fewest points. */
    private String roundWinnerId;
    /** True when round ended because no one could move. */
    private boolean fish = false;
    /** Game over (someone hit targetScore). */
    private boolean finished = false;
    /** Player who reached targetScore (= the loser of the whole game). */
    private String loserId;

    public GameState() {}

    public List<Tile> getBoard() { return board; }
    public void setBoard(List<Tile> board) { this.board = board; }

    public int getLeftEnd() { return leftEnd; }
    public void setLeftEnd(int leftEnd) { this.leftEnd = leftEnd; }

    public int getRightEnd() { return rightEnd; }
    public void setRightEnd(int rightEnd) { this.rightEnd = rightEnd; }

    public Map<String, List<Tile>> getHands() { return hands; }
    public void setHands(Map<String, List<Tile>> hands) { this.hands = hands; }

    public List<Tile> getBazaar() { return bazaar; }
    public void setBazaar(List<Tile> bazaar) { this.bazaar = bazaar; }

    public List<String> getPlayerOrder() { return playerOrder; }
    public void setPlayerOrder(List<String> playerOrder) { this.playerOrder = playerOrder; }

    public int getCurrentTurnIndex() { return currentTurnIndex; }
    public void setCurrentTurnIndex(int currentTurnIndex) { this.currentTurnIndex = currentTurnIndex; }

    public Map<String, Integer> getScores() { return scores; }
    public void setScores(Map<String, Integer> scores) { this.scores = scores; }

    public int getTargetScore() { return targetScore; }
    public void setTargetScore(int targetScore) { this.targetScore = targetScore; }

    public int getPassCount() { return passCount; }
    public void setPassCount(int passCount) { this.passCount = passCount; }

    public boolean isRoundFinished() { return roundFinished; }
    public void setRoundFinished(boolean roundFinished) { this.roundFinished = roundFinished; }

    public String getRoundWinnerId() { return roundWinnerId; }
    public void setRoundWinnerId(String roundWinnerId) { this.roundWinnerId = roundWinnerId; }

    public boolean isFish() { return fish; }
    public void setFish(boolean fish) { this.fish = fish; }

    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }

    public String getLoserId() { return loserId; }
    public void setLoserId(String loserId) { this.loserId = loserId; }
}
