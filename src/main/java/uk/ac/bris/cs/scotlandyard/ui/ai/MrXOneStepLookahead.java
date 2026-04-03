package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Nonnull;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Ai;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

import static uk.ac.bris.cs.scotlandyard.ui.ai.AIUtils.*;

public class MrXOneStepLookahead implements Ai {

	@Nonnull @Override public String name() { return "MrX One Step Lookahead"; }

	@Nonnull public Integer heuristic(Board board, Move move) {
		Integer res = 0;
		// if Mr X is further away from detectives, this is a positive.
		res += totalDetectiveDistances(board, move) * 10;

		// if Mr X can move around more, this is a positive.
		res += Math.toIntExact(board.getSetup().graph.adjacentNodes(getMoveDestination(move)).stream()
				.filter(n -> !detectiveLocations(board).containsValue(n))
				.count()) * 5;

		// if the next move allows the detectives to win, move away.
		if (isDetectiveOneAway(board, move)) res -= 100000;
		return res;
	}

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		var moves = board.getAvailableMoves().asList();
		return moves.stream()
				.max(Comparator.comparingInt(m -> heuristic(board, m)))
				.orElseThrow();
	}
}
