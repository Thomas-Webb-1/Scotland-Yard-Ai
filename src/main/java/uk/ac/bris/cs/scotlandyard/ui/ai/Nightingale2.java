package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Pair;
import jakarta.annotation.Nonnull;
import uk.ac.bris.cs.scotlandyard.model.Ai;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Nightingale2 implements Ai {
    PathfindingGraph pathfindingGraph;
    private final int DEPTH = 2;//max depth for minimax algorythm
    private long nodesVisited = 0;//

    @Nonnull
    @Override
    public String name() {
        return "minimax";
    }

    @Nonnull
    @Override
    public Move pickMove(
            @Nonnull Board board,
            Pair<Long, TimeUnit> timeoutPair) {
        nodesVisited = 0;
        //build the pathfinding graph and store it for future invocations
        if (pathfindingGraph == null) {
            pathfindingGraph = new PathfindingGraph(board.getSetup().graph);
        }
        int mrXLocation = board.getAvailableMoves().stream().filter(m->m.commencedBy().isMrX()).findFirst().orElseThrow().source();

        GameTree root = GameTree.fromBoard(board, mrXLocation);


        int totalMoves = root.getChildren().size();
        var count = 0;

        List<Pair<Move,Integer>> moveScores = root.getChildren()
                .parallelStream()
                .map(child -> {
                    int score = minimax(child, DEPTH - 1, mrXLocation);
                    System.out.println( "one move processed");
                    return new Pair<Move,Integer>(child.getPrecedingMove(), score);

                }).toList();

        Move bestMove = null;
        Integer bestScore = Integer.MIN_VALUE;

        for (var move : moveScores){
            if (move.right() > bestScore){
                bestMove = move.left();
                bestScore = move.right();
            }
        }


        System.out.println("Total nodes visited: " + nodesVisited);



        if (bestMove == null) throw new RuntimeException("no move found");
        return bestMove;
    }



    private int updatedMrXLocation(GameTree node, int previousMrXLocation) {
        Move move = node.getPrecedingMove();
        if (move != null && move.commencedBy().isMrX()) {
            return move.accept(new Move.Visitor<Integer>() {
                public Integer visit(Move.SingleMove m) { return m.destination; }
                public Integer visit(Move.DoubleMove m) { return m.destination2; }
            });
        }
        return previousMrXLocation;
    }

    private int minimax(GameTree node, int depth, int mrXLocation) {
        nodesVisited++;
        mrXLocation = updatedMrXLocation(node, mrXLocation);

        //if either team win or the max depth is reached evaluate the node
        Team winner = node.getWinner();
        if (winner == Team.MRX) return 100000;
        if (winner == Team.DETECTIVES) return -100000;
        if (depth == 0) return evaluate(node, mrXLocation);

        //else return the min/max of the child nodes depending on turn
        boolean maximising = node.isMrXTurn();
        int best = maximising ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (GameTree child : node.getChildren()) {
            int score = minimax(child, depth - 1, mrXLocation);

            best = maximising
                    ? Math.max(best, score)
                    : Math.min(best, score);

            child.free();
        }

        return best;
    }

    private int evaluate(GameTree node, int mrXLocation) {
        //just using distance to the clocest detective
        Board.GameState state = node.getState();
        return state.getPlayers().stream()
                .filter(Piece::isDetective)
                .mapToInt(p -> pathfindingGraph.getDistance(mrXLocation,
                        state.getDetectiveLocation((Piece.Detective) p).orElseThrow()))
                .min()
                .orElse(0);
    }

}
