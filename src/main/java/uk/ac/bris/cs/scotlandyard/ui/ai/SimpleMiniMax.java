package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import io.atlassian.fugue.Pair;
import jakarta.annotation.Nonnull;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SimpleMiniMax implements Ai {

	@Nonnull @Override public String name() { return "Simple MiniMax"; }

	// we need to access the distances between Mr. X and the detectives repeatedly, so calculating the distances
	// between every node ahead of time and being able to call it in O(1) is important.
	public static Integer[][] floydWarshall(ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph) {
		Integer n = 199;
		Integer[][] dist = new Integer[n][n];
		Arrays.stream(dist).forEach(r -> Arrays.fill(r, 1000000));
		graph.edges().forEach(edge ->
				dist[edge.nodeU() - 1][edge.nodeV() - 1] = dist[edge.nodeV() - 1][edge.nodeU() - 1] = 1);
		graph.nodes().forEach(node -> dist[node - 1][node - 1] = 0);
		for (int k = 0; k < n; k++) {
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					dist[i][j] = Math.min(dist[i][j], dist[i][k] + dist[k][j]);
				}
			}
		}
		return dist;
	}

	public class MyBoard {
		private ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph;
		private Integer[][] graphDistances;
		private ImmutableMap<Piece.Detective, Integer> detectives;
		private Integer mrXLocation;
		private boolean mrXTurn;
		private ImmutableSet<Piece> winner;

		public MyBoard(
				ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph,
				Integer[][] graphDistances,
				ImmutableMap<Piece.Detective, Integer> detectives,
				Integer mrXLocation,
				boolean mrXTurn
		) {
			this.graph = graph;
			this.graphDistances = graphDistances;
			this.detectives = detectives;
			this.mrXLocation = mrXLocation;
			this.mrXTurn = mrXTurn;
			// don't calculate the amount of moves left
			this.winner = detectives.values().contains(mrXLocation) ? ImmutableSet.of(Piece.MrX.MRX) : ImmutableSet.of();
		}

		public MyBoard(Board board) {
			this.graph = board.getSetup().graph;
			this.detectives = board.getPlayers().stream()
					.filter(Piece::isDetective)
					.map(p -> (Piece.Detective) p)
					.collect(ImmutableMap.toImmutableMap(
							Function.identity(),
							d -> board.getDetectiveLocation(d).orElseThrow()
					));
			// this should only be called when it is Mr. X's turn.
			this.mrXLocation = board.getAvailableMoves().stream().findFirst().orElseThrow().source();
			this.graphDistances = floydWarshall(graph);
			this.mrXTurn = board.getAvailableMoves().stream().findFirst().orElseThrow().commencedBy().isMrX();
			this.winner = board.getWinner();
		}

		// this should only be called at the start when it is Mr. X's turn.
		public MyBoard(MyBoard board, Move move) {
			if (move.commencedBy().isDetective()) throw new IllegalArgumentException("Can only construct a new board with a Mr. X move.");
			this.graph = board.getGraph();
			this.graphDistances = board.graphDistances;
			this.detectives = board.getDetectives();
			this.mrXLocation = getMoveDestination(move);
			this.mrXTurn = false;
			this.winner = board.getWinner();
		}

		public ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> getGraph() {
			return graph;
		}

		public ImmutableMap<Piece.Detective, Integer> getDetectives() {
			return detectives;
		}

		public MyBoard boardFromNewDetectives(ImmutableMap<Piece.Detective, Integer> newDetectives) {
			return new MyBoard(
					graph,
					graphDistances,
					newDetectives,
					mrXLocation,
					true
			);
		}

		public Integer getMrXLocation() {
			return mrXLocation;
		}

		public MyBoard boardFromNewMrXLocation(Integer newLocation) {
			return new MyBoard(
					graph,
					graphDistances,
					detectives,
					newLocation,
					false
			);
		}

		public boolean isMrXTurn() {
			return mrXTurn;
		}

		public ImmutableSet<Piece> getWinner() {
			return winner;
		}

		public Integer getDistance(Integer source, Integer dest) {
			return graphDistances[source - 1][dest - 1];
		}

		// this function calculates all possible detective locations after every detective has made their move.
		// note that this doesn't verify if the detective has the right detectives.
		@Nonnull public Set<Map<Piece.Detective, Integer>> potentialDetectiveLocations() {
			// for each detective, find their set of locations they can move to.
			Map<Piece.Detective, Set<Integer>> potentialLocations = detectives.entrySet().stream()
					.collect(Collectors.toMap(
							Map.Entry::getKey,
							e -> graph.adjacentNodes(e.getValue())
					));

			// find the cartesian product between each detective's set of potential locations.
			Set<Map<Piece.Detective, Integer>> cartesianProduct = potentialLocations.entrySet().stream()
					.reduce(
							// the start of the reduction.
							Set.of(new HashMap<>()),
							// for each detective's set of potential locations, create a cartesian product of each set
							// one-by-one.
							(Set<Map<Piece.Detective, Integer>> combinations, Map.Entry<Piece.Detective, Set<Integer>> entry) ->
									combinations.stream()
											.flatMap(combo -> entry.getValue().stream()
													.map(location -> {
														Map<Piece.Detective, Integer> newCombo = new HashMap<>(combo);
														newCombo.put(entry.getKey(), location);
														return newCombo;
													}))
											.collect(Collectors.toSet()),
							// this is to combine two accumulations. this should never be called.
							(a, b) -> {
								Set<Map<Piece.Detective, Integer>> merged = new HashSet<>(a);
								merged.addAll(b);
								return merged;
							}
					);

			// filter out scenarios in which two detectives end up in the same location.
			return cartesianProduct.stream()
					.filter(combo -> combo.values().stream().distinct().count() == combo.size())
					.collect(Collectors.toSet());
		}

		public Set<Integer> potentialMrXLocations() {
			return graph.adjacentNodes(mrXLocation).stream().collect(Collectors.toSet());
		}
		public Integer heuristic() {
			Integer score = 0;
			// if a detective is on Mr. X, lose a lot of score.
			if (detectives.containsValue(mrXLocation)) score -= 100000;

			ImmutableMap<Piece.Detective, Integer> distancesFromDetectives = detectives.entrySet().stream()
					.collect(ImmutableMap.toImmutableMap(
							Map.Entry::getKey,
							e -> Objects.requireNonNull(getDistance(mrXLocation, e.getValue()))
					));

			// if a detective is nearby, reduce score.
			if (distancesFromDetectives.containsValue(1)) score -= 10000;
			if (distancesFromDetectives.containsValue(2)) score -= 1000;
			if (distancesFromDetectives.containsValue(3)) score -= 100;

			for (Integer d : distancesFromDetectives.values()) {
				// Mr. X gets more score the further away he is in total to all detectives.
				score += d * 10;
			}

			// if Mr. x has more freedom to move, increase score.
			score += graph.adjacentNodes(mrXLocation).size() * 10;
			return score;
		}
}

	@Nonnull public Integer minimaxAlphaBeta(
			MyBoard board,
			Integer depth,
			Integer alpha,
			Integer beta,
			boolean isMrXTurn
	) {
		if (depth == 0 || !board.getWinner().isEmpty()) {
			return board.heuristic();
		}
		Integer value;
		if (isMrXTurn) {
			value = Integer.MIN_VALUE;
			Set<Integer> potentialMrXLocations = board.potentialMrXLocations();
			for (Integer node : potentialMrXLocations) {
				value = Math.max(
						value,
						minimaxAlphaBeta(
								board.boardFromNewMrXLocation(node),
								depth - 1,
								alpha,
								beta,
								false
						));
				if (value >= beta) break;
				alpha = Math.max(alpha, value);
			}
		} else {
			value = Integer.MAX_VALUE;
			Set<Map<Piece.Detective, Integer>> potentialDetectiveLocations = board.potentialDetectiveLocations();
			for (Map<Piece.Detective, Integer> locations : potentialDetectiveLocations) {
				value = Math.min(
						value,
						minimaxAlphaBeta(
								board.boardFromNewDetectives(ImmutableMap.copyOf(locations)),
								depth - 1,
								alpha,
								beta,
								true
						));
				if (value <= alpha) break;
				beta = Math.min(beta, value);
			}
		}
		return value;
	}

	public Integer getMoveDestination(Move move) {
		return move.accept(new Move.Visitor<Integer>() {
			@Override
			public Integer visit(Move.SingleMove move) {
				return move.destination;
			}

			@Override
			public Integer visit(Move.DoubleMove move) {
				return move.destination2;
			}
		});
	}

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		var moves = board.getAvailableMoves().asList();
		if (moves.stream().anyMatch(m -> m.commencedBy().isDetective())) {
			throw new IllegalArgumentException("This AI is for Mr X. Only.");
		}
		MyBoard state = new MyBoard(board);
		//Map<Move, Integer> sortedMoves = moves.stream()
		//		.collect(Collectors.toMap(
		//				Function.identity(),
		//				m -> minimaxAlphaBeta(
		//						state.boardFromNewMrXLocation(getMoveDestination(m)),
		//						2,
		//						Integer.MIN_VALUE,
		//						Integer.MAX_VALUE,
		//						false
		//				)
		//		));
		//System.out.println("===================================================================================");
		//sortedMoves.entrySet().stream()
		//		.sorted(Comparator.comparingInt(Map.Entry::getValue))
		//		.forEach(System.out::println);
		//return sortedMoves.entrySet().stream()
		//		.max(Comparator.comparingInt(Map.Entry::getValue))
		//		.get().getKey();
		return moves.stream()
				.max(Comparator.comparingInt(m -> minimaxAlphaBeta(
						state.boardFromNewMrXLocation(getMoveDestination(m)),
						2,
						Integer.MIN_VALUE,
						Integer.MAX_VALUE,
						false
				)))
				.orElseThrow();
	}
}