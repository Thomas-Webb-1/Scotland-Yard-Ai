package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Pair;
import jakarta.annotation.Nonnull;
import uk.ac.bris.cs.scotlandyard.model.Ai;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Nightingale implements Ai {

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
        // returns a random move, replace with your own implementation
        var moves = board.getAvailableMoves().asList();
        if (moves.stream().anyMatch(move -> move.commencedBy().isDetective())) {
            throw new RuntimeException("This ai only works with mrX");
        }
        ;

        var detectives = Piece.Detective.values();
        PathfindingGraph g = new PathfindingGraph(board.getSetup().graph);



        Move bestMove = moves.getFirst();
        Integer bestScore = 0;

        for (Move move : moves) {
            Integer score = Arrays.stream(detectives).mapToInt((d) -> g.getDistance(board.getDetectiveLocation(d).orElseThrow(), move.accept(new Move.Visitor<Integer>() {
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


}
