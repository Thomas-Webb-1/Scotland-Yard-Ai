    package uk.ac.bris.cs.scotlandyard.ui.ai;

    import io.atlassian.fugue.Pair;
    import org.jspecify.annotations.NonNull;
    import uk.ac.bris.cs.scotlandyard.model.*;

    import java.util.ArrayList;
    import java.util.List;
    import java.util.concurrent.TimeUnit;

    public class MiniMiniMax implements Ai {
        MoveStore moveStore;
        PathfindingGraph pathGraph;
        private final int MAX_DEPTH = 5;
        private long statesVisited;
        private long deadline;

        @Override
        public @NonNull String name() {
            return "MiniMax 2";
        }

        @Override
        public @NonNull Move pickMove(@NonNull Board board, Pair<Long, TimeUnit> timeoutPair) {
            //Reset counter for info
            statesVisited = 0;
            //Calculate the time at which we must halt computation
            deadline = System.currentTimeMillis() + (timeoutPair.left()* 1000) - 3000;
            long startTime = deadline - (timeoutPair.left() * 1000) + 3000;


            //precompute lookup tables and store them for future invocations;
            if (moveStore == null) this.moveStore = new MoveStore(board.getSetup().graph);
            if (pathGraph == null) this.pathGraph = new PathfindingGraph(board.getSetup().graph);

            //Convert the board to a gamestate
            FastGameState initialState = StateUtils.stateFromBoard(board,this.moveStore);


            //Default to the first available move
            Move bestMove = board.getAvailableMoves().stream().findFirst().orElseThrow();
            int bestDepthReached = 0;

            for (int depth = 1; depth <= MAX_DEPTH; depth++) {
                if (System.currentTimeMillis() >= deadline) break;

                Move bestAtDepth = searchAtDepth(initialState, depth);
                if (bestAtDepth != null) {  // null means time ran out mid-search
                    System.out.println("Best Move found At search depth " + depth + " in " + (System.currentTimeMillis() - startTime) +"ms : " + bestAtDepth);
                    System.out.println(statesVisited);
                    bestMove = bestAtDepth;
                    bestDepthReached = depth;
                } else {
                    break;
                }
            }

            System.out.println("Selected move:" + bestMove);
            System.out.println(statesVisited);

            return bestMove;
        }

        private Move searchAtDepth(FastGameState initialState, int depth) {
            //sort initial moves based on heuristic to potently improve alpha beta
            List<Integer> moves = new ArrayList<>();
            for (int i = 0; i < initialState.getMoves(); i++) {
                moves.add(i);
            }

            moves.sort((a, b) -> {
                FastGameState stateA = initialState.advanceOrNull(a);
                FastGameState stateB = initialState.advanceOrNull(b);

                int scoreA = (stateA != null) ? evaluate(stateA) : Integer.MIN_VALUE;
                int scoreB = (stateB != null) ? evaluate(stateB) : Integer.MIN_VALUE;

                return Integer.compare(scoreB, scoreA);
            });

            int bestScore = Integer.MIN_VALUE;
            Move bestMove = null;
            int alpha = Integer.MIN_VALUE;
            int count = 0;

            for (int i : moves) {
                if (System.currentTimeMillis() >= deadline) return null;

                FastGameState singleMove = initialState.advanceOrNull(i);
                if (singleMove == null) continue;

                int score;
                if (singleMove.isMrXTurn()) {
                    for (int a = 0; a < singleMove.getMoves(); a++) {
                        if (System.currentTimeMillis() >= deadline) return null;
                        FastGameState doubleMove = singleMove.advanceOrNull(a);
                        if (doubleMove == null) continue;
                        score = minimax(doubleMove, depth - 1, false, alpha, Integer.MAX_VALUE);
                        if (score == Integer.MIN_VALUE) return null;
                        if (score > bestScore) {
                            bestScore = score;
                            bestMove = StateUtils.mrXMoveToMove(initialState.getMrX(), singleMove.getMrX(), doubleMove.getMrX());
                        }
                        alpha = Math.max(alpha, bestScore);
                    }
                } else {
                    score = minimax(singleMove, depth - 1, false, alpha, Integer.MAX_VALUE);
                    if (score == Integer.MIN_VALUE) return null;
                    if (score > bestScore) {
                        bestScore = score;
                        bestMove = StateUtils.mrXMoveToMove(initialState.getMrX(), singleMove.getMrX(), -1);
                    }
                    alpha = Math.max(alpha, bestScore);
                }
                count++;
                if (depth >= 4) System.out.println(count + "/"+moves.size());
            }
            return bestMove;
        }

        private int minimax(FastGameState state, int depth, boolean maximizingPlayer, int alpha, int beta) {
            statesVisited++;
            if (System.currentTimeMillis() >= deadline) return Integer.MIN_VALUE;//exit early if time runs out

            if (state.getWinner() != null) return evaluate(state);
            if (depth == 0) return evaluate(state);

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
            //if it's a terminal state return a large value
            if (state.getWinner() != null) {
                return state.getWinner() == Team.MRX ? 100000 : -100000;
            }
            //else calculate a heuristic based on distances to detectives and number of escape routes;

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
            score += (int)(pathGraph.getCentrality(mrXLoc) * 1000);

            if (minDist <= 3) score -= 100;
            if (minDist <= 2) score -= 500;
            if (minDist == 1) score -= 2000;

            return score;
        }
    }