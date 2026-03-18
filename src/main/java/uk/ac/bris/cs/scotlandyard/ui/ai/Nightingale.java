package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Pair;
import jakarta.annotation.Nonnull;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Nightingale implements Ai {
    PathfindingGraph pathfindingGraph;

    @Nonnull
    @Override
    public String name() {
        return "Nightingale";
    }

    @Nonnull
    @Override
    public Move pickMove(
            @Nonnull Board board,
            Pair<Long, TimeUnit> timeoutPair) {
        bootStrap(board);

        if (board.getMrXTravelLog().isEmpty()) bootStrap(board);
        var moves = board.getAvailableMoves().asList();
        var detectives = Piece.Detective.values();


        Move bestMove = moves.getFirst();
        int bestScore = 0;

        for (Move move : moves) {
            int score = Arrays.stream(detectives).mapToInt((d) -> pathfindingGraph.getDistance(board.getDetectiveLocation(d).orElseThrow(), move.accept(new Move.Visitor<Integer>() {
				@Override
				public Integer visit(Move.SingleMove move) {
					return move.destination;
				}

				@Override
				public Integer visit(Move.DoubleMove move) {
					return move.destination2;
				}
			}))).sum();
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }

        }


        return bestMove;
    }


    private void bootStrap(Board board){
        this.pathfindingGraph = new PathfindingGraph(board.getSetup().graph);
        if (board.getAvailableMoves().stream().anyMatch(move -> move.commencedBy().isDetective())) {
            throw new RuntimeException("This ai only works with mrX");
        }
    }
}
