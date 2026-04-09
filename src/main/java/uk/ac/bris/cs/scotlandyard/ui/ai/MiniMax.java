package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import io.atlassian.fugue.Pair;
import jakarta.annotation.Nonnull;
import uk.ac.bris.cs.scotlandyard.model.Ai;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

import static uk.ac.bris.cs.scotlandyard.ui.ai.AIUtils.*;

public class RandomMoves implements Ai {

	@Nonnull @Override public String name() { return "Random Moves (Testing)"; }

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		var moves = board.getAvailableMoves().asList();
		System.out.println("-----------------------------------------------------------");
		System.out.println(detectiveMoveCartesianProduct(board));
		return moves.get(new Random().nextInt(moves.size()));
	}
}