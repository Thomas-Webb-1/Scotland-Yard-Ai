package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Pair;
import org.jspecify.annotations.NonNull;
import uk.ac.bris.cs.scotlandyard.model.Ai;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MiniMax implements Ai {
    private final int MAX_DEPTH = 10;
    MoveStore moveStore;
    PathfindingGraph pathGraph;
    private long statesVisited;
    private long deadline;


    @Override
    public @NonNull String name() {
        return "MiniMax";
    }

    @Override
    public @NonNull Move pickMove(@NonNull Board board, Pair<Long, TimeUnit> timeoutPair) {
        //Reset counter for info
        statesVisited = 0;
        //Calculate the time at which we must halt computation
        deadline = System.currentTimeMillis() + (timeoutPair.left() * 1000) - 3000;
        long startTime = deadline - (timeoutPair.left() * 1000) + 3000;


        //precompute lookup tables and store them for future invocations;
        if (moveStore == null) this.moveStore = new MoveStore(board.getSetup().graph);
        if (pathGraph == null) this.pathGraph = new PathfindingGraph(board.getSetup().graph);

        FastGameState initialState = FastGameState.stateFromBoard(board, this.moveStore);
        return searchAtIncreasingDepth(initialState);
    }

    private Move searchAtIncreasingDepth(FastGameState initialState) {
        Map<Move, FastGameState> movesMap = new HashMap<>();
        int initialMrx = initialState.getMrX();

        // Generate moves once
        for (int i = 0; i < initialState.getMoves(); i++) {
            FastGameState state1 = initialState.advanceOrNull(i);
            if (state1 == null) continue;

            int state1Mrx = state1.getMrX();

            if (!state1.isMrXTurn()) {
                Move move = new Move.SingleMove(
                        Piece.MrX.MRX,
                        StateUtils.getPlayerLocation(initialMrx + 1),
                        StateUtils.whichTicketWasUsed(initialMrx, state1Mrx),
                        StateUtils.getPlayerLocation(state1Mrx + 1)
                );
                movesMap.put(move, state1);
            } else {
                for (int j = 0; j < state1.getMoves(); j++) {
                    FastGameState state2 = state1.advanceOrNull(j);
                    if (state2 == null) continue;

                    int state2Mrx = state2.getMrX();

                    Move move = new Move.DoubleMove(
                            Piece.MrX.MRX,
                            StateUtils.getPlayerLocation(initialMrx + 1),
                            StateUtils.whichTicketWasUsed(initialMrx, state1Mrx),
                            StateUtils.getPlayerLocation(state1Mrx + 1),
                            StateUtils.whichTicketWasUsed(state1Mrx, state2Mrx),
                            StateUtils.getPlayerLocation(state2Mrx + 1)
                    );
                    movesMap.put(move, state2);
                }
            }
        }

        //get a list of moves so we can store previous iterations results to process "better" moves first;
        List<Move> moves = new ArrayList<>(movesMap.keySet());
        Move absoluteBestMove = moves.getFirst();

        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            if (System.currentTimeMillis() >= deadline) break;

            Map<Move, Integer> scores = new HashMap<>();
            int currentAlpha = Integer.MIN_VALUE + 1;
            boolean timeoutOccurred = false;

            for (Move move : moves) {
                if (System.currentTimeMillis() >= deadline) {
                    timeoutOccurred = true;
                    break;
                }

                int score = minimax(
                        movesMap.get(move),
                        depth - 1,
                        false,
                        currentAlpha,
                        Integer.MAX_VALUE
                );

                if (score == Integer.MIN_VALUE) {
                    timeoutOccurred = true;
                    break;
                }

                scores.put(move, score);

                if (score > currentAlpha) {
                    currentAlpha = score;
                }
            }

            if (timeoutOccurred && scores.isEmpty()) break;

            //sort moves so for the next depth moves that did well this depth will be processed first;
            moves.sort((a, b) -> Integer.compare(
                    scores.getOrDefault(b, Integer.MIN_VALUE),
                    scores.getOrDefault(a, Integer.MIN_VALUE)
            ));

            absoluteBestMove = moves.getFirst();
            System.out.println("Depth " + depth + " completed. Best move Found: " + absoluteBestMove);
            System.out.println(statesVisited);
        }

        System.out.println(statesVisited);
        return absoluteBestMove;
    }

    private int minimax(FastGameState state, int depth, boolean maximizingPlayer, int alpha, int beta) {
        statesVisited++;
        if (System.currentTimeMillis() >= deadline) return Integer.MIN_VALUE;//exit early
        if (state.getWinner() != null || depth == 0) return evaluate(state);

        if (maximizingPlayer) { // mrX move
            int maxEval = Integer.MIN_VALUE;
            for (int i = 0; i < state.getMoves(); i++) {
                FastGameState next = state.advanceOrNull(i);
                if (next == null) continue;

                int eval;
                if (next.isMrXTurn()) {
                    //if move is a double move call minimax at the same depth
                    eval = minimax(next, depth, true, alpha, beta);
                } else {
                    eval = minimax(next, depth - 1, false, alpha, beta);
                }
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return maxEval;
        } else { //Detectives move
            int minEval = Integer.MAX_VALUE;
            for (int i = 0; i < state.getMoves(); i++) {
                FastGameState next = state.advanceOrNull(i);
                if (next == null) continue;
                int eval = minimax(next, depth - 1, true, alpha, beta);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            return minEval;
        }
    }

    private int evaluate(FastGameState state) {
        if (state.getWinner() != null) {
            return state.getWinner() == Team.MRX ? 100000 : -100000;
        }

        int mrXLoc = StateUtils.getPlayerLocation(state.getMrX() + 1);
        int score = 0;
        int minDist = Integer.MAX_VALUE;
        int totalDist = 0;

        for (int d : state.getDetectives()) {
            int dLoc = StateUtils.getPlayerLocation(d + 1);
            int dist = pathGraph.getDistance(mrXLoc, dLoc);
            minDist = Math.min(minDist, dist);
            totalDist += dist;
        }

        score += totalDist * 10;
        score += minDist * 50;
        score += (int) (pathGraph.getCentrality(mrXLoc) * 1000);

        score += state.getMoves() * 20;

        if (minDist <= 3) score -= 100;
        if (minDist <= 2) score -= 500;
        if (minDist == 1) score -= 2000;

        return score;
    }
}