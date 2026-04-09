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

import static uk.ac.bris.cs.scotlandyard.ui.ai.AIUtils.detectiveMoveCartesianProduct;

public class SimpleMiniMax implements Ai {

	@Nonnull @Override public String name() { return "Simple MiniMax"; }

	public static Integer[][] floydWarshall(ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph) {
		Integer n = 199;
		Integer[][] dist = new Integer[n][n];
		Arrays.stream(dist).forEach(r -> Arrays.fill(r, Integer.MAX_VALUE));
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

	// there are values from 1-199, so the index goes from 0-198.
	// this function makes it easier to find values from the board location instead of having to do mental
	// of-by-one error checking.
	public static Integer getDistanceFromFloydWarshall(Integer[][] floydWarshall, Integer source, Integer dest) {
		return floydWarshall[source - 1][dest - 1];
	}

	public Map<Integer, Integer> dijkstra(
			ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph,
			Integer source) {
		Set<Integer> nodes = graph.nodes();
		// all the nodes in the game.
		Map<Integer, Integer> dist = new HashMap<>();
		// a hash map with destination node (key) and distance (value).
		PriorityQueue<Integer> q = new PriorityQueue<>(Comparator.comparing(dist::get));
		// a priority queue of nodes, with priority being the distance it has from the source.
		dist.put(source, 0);
		q.add(source);
		nodes.stream().filter(n -> !n.equals(source)).forEach(n -> dist.put(n, Integer.MAX_VALUE));
		//for (Integer node : nodes) {
		//    if (!node.equals(source)) {
		//        dist.put(node, Integer.MAX_VALUE); // sets all distances to inf, except source.
		//    }
		//}
		Integer u;
		Integer alt;
		while (!q.isEmpty()) {
			u = q.poll();
			for (Integer adjNode : graph.adjacentNodes(u)) {
				alt = dist.get(u) + 1;
				if (alt < dist.get(adjNode)) {
					dist.put(adjNode, alt);
					q.offer(adjNode);
				}
			}
		}
		return dist;
	}

	@Nonnull public Map<Piece.Detective, Integer> getDetectiveLocations(Board board) {
		return board.getPlayers().stream()
				.filter(Piece::isDetective)
				.map(p -> (Piece.Detective) p)
				.collect(Collectors.toMap(
						Function.identity(),
						d -> board.getDetectiveLocation(d).orElseThrow()
				));
	}

	@Nonnull public Set<Map<Piece.Detective, Integer>> potentialDetectiveLocations(ImmutableValueGraph graph, Map<Piece.Detective, Integer> detectiveLocations) {
		Map<Piece.Detective, Set<Integer>> potentialLocations = detectiveLocations.entrySet().stream()
				.collect(Collectors.toMap(
						e -> e.getKey(),
						e -> graph.adjacentNodes(e.getValue())
				));
		Set<Map<Piece.Detective, Integer>> cartesianProduct = potentialLocations.entrySet().stream()
				.reduce(
						Set.of(new HashMap<>()),
						(Set<Map<Piece.Detective, Integer>> combinations, Map.Entry<Piece.Detective, Set<Integer>> entry) ->
                            combinations.stream()
                                    .flatMap(combo -> entry.getValue().stream()
                                            .map(location -> {
                                                Map<Piece.Detective, Integer> newCombo = new HashMap<>(combo);
                                                newCombo.put(entry.getKey(), location);
                                                return newCombo;
                                            }))
                                    .collect(Collectors.toSet()),
                    (a, b) -> {
                        Set<Map<Piece.Detective, Integer>> merged = new HashSet<>(a);
                        merged.addAll(b);
                        return merged;
					}
				);

		return cartesianProduct.stream()
				.filter(combo -> combo.values().stream().distinct().count() == combo.values().stream().count())
				.collect(Collectors.toSet());
	}

	@Nonnull public Integer heuristic2(ImmutableValueGraph graph, Integer[][] distances, Integer mrXLocation, Map<Piece.Detective, Integer> detectiveLocations) {
		Integer score = 0;

		if (detectiveLocations.values().stream()
				.anyMatch(mrXLocation::equals)) score -= 1000000;

		Set<Integer> possibleMrXLocations = graph.adjacentNodes(mrXLocation);

		for (Integer location : detectiveLocations.values()) {
			score += getDistanceFromFloydWarshall(distances, mrXLocation, location) * 10;
		}

		score += possibleMrXLocations.size() * 5;
		return score;
	}

	//@Nonnull public Integer heuristic(Board board) {
	//	Integer score = 0;
	//	// this will only be accurate if it is Mr X's go, which it should always be.
	//	Integer mrXLocation = board.getAvailableMoves().stream()
	//			.findFirst()
	//			.get()
	//			.source();
	//	Map<Piece.Detective, Integer> detectiveLocations = getDetectiveLocations(board);

	//	Map<Integer, Integer> distances = dijkstra(board.getSetup().graph, mrXLocation);

	//	if (board.getSetup().graph.adjacentNodes(mrXLocation).stream()
	//			.anyMatch(detectiveLocations::containsValue)) score -= 1000000;

	//	// prefer moves where Mr. X is further away from the detectives.
	//	for (Integer location : detectiveLocations.values()) {
	//		score += distances.get(location) * 10;
	//	}

	//	// prefer moves which give Mr. X more freedom to move around.
	//	score += board.getAvailableMoves().size() * 10;
	//	return score;
	//}

	//@Nonnull public Integer minimax2(ImmutableValueGraph graph, Integer mrXLocation, Map<Piece.Detective, Integer> detectiveLocations, Integer depth, boolean isMrXTurn) {
	//	System.out.println("Is it Mr X's turn? " + isMrXTurn + "! Checking minimax... MrXLocation: " + mrXLocation + ". detectiveLocations: " + detectiveLocations);
	//	if (depth == 0 || detectiveLocations.values().contains(mrXLocation)) {
	//		return heuristic2(graph, mrXLocation, detectiveLocations);
	//	}
	//	if (isMrXTurn) {
	//		Set<Integer> potentialMrXLocations = graph.adjacentNodes(mrXLocation);
	//		return potentialMrXLocations.stream()
	//				.mapToInt(n -> minimax2(graph, n, detectiveLocations, depth - 1, false))
	//				.max()
	//				.orElse(Integer.MIN_VALUE);
	//	} else {
	//		return potentialDetectiveLocations(graph, detectiveLocations).stream()
	//				.mapToInt(m -> minimax2(graph, mrXLocation, m, depth - 1, true))
	//				.min()
	//				.orElse(Integer.MAX_VALUE);
	//	}
	//}

	@Nonnull public Integer minimaxAlphaBeta(
			ImmutableValueGraph graph,
			Integer[][] distances,
			Integer mrXLocation,
			Map<Piece.Detective, Integer> detectiveLocations,
			Integer depth,
			Integer alpha,
			Integer beta,
			boolean isMrXTurn) {
		if (depth == 0 || detectiveLocations.values().contains(mrXLocation)) {
			return heuristic2(graph, distances, mrXLocation, detectiveLocations);
		}
        Integer value;
        if (isMrXTurn) {
            value = Integer.MIN_VALUE;
			Set<Integer> potentialMrXLocations = graph.adjacentNodes(mrXLocation);
			for (Integer node : potentialMrXLocations) {
				value = Math.max(
						value,
						minimaxAlphaBeta(
								graph,
								distances,
								node,
								detectiveLocations,
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
			Set<Map<Piece.Detective, Integer>> potentialDetectiveLocations = potentialDetectiveLocations(graph, detectiveLocations);
			for (Map<Piece.Detective, Integer> locations : potentialDetectiveLocations) {
				value = Math.min(
						value,
						minimaxAlphaBeta(
								graph,
								distances,
								mrXLocation,
								locations,
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
	//@Nonnull public minimax(Board board, Integer depth, boolean mrXTurn) {
	//	if (depth == 0 || !board.getWinner().isEmpty()) {
	//		return heuristic(board);
	//	}
	//	Integer value;
	//	if (mrXTurn) {
	//		value = Integer.MIN_VALUE;
	//		return board.getAvailableMoves().stream()
	//				.map(m -> )
	//				.map(b -> Math.max(value, minimax(b, depth - 1, false)))
	//				.max(Integer::compare).get();
	//	}
	//	else {
	//		value = Integer.MAX_VALUE;
	//		System.out.println(moveSetsFromBoard(board));
	//		return moveSetsFromBoard(board).stream()
	//				.map(ms -> boardFromMoveSet(board, ms, false))
	//				.map(b -> Math.min(value, minimax(b, depth - 1, true)))
	//				.min(Integer::compare).get();
	//	}
	//	}
	//}

	public Integer getMoveDestination(Move move){
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
		Integer[][] distances = floydWarshall(board.getSetup().graph);
		Integer mrXLocation = moves.stream().findFirst().orElseThrow().source();
//		List<Move> sortedMoves = moves.stream()
//				.sorted(Comparator.comparingInt(m ->
//								minimaxAlphaBeta(
//										board.getSetup().graph,
//										distances,
//										mrXLocation,
//										getDetectiveLocations(board),
//										2,
//										Integer.MIN_VALUE,
//										Integer.MAX_VALUE,
//										false
//								))
//						.reversed()
//				)
//				.toList();
		Map<Move, Integer> sortedMoves = moves.stream()
				.collect(Collectors.toMap(
								Function.identity(),
								m -> minimaxAlphaBeta(
										board.getSetup().graph,
										distances,
										getMoveDestination(m),
										getDetectiveLocations(board),
										2,
										Integer.MIN_VALUE,
										Integer.MAX_VALUE,
										false
								)
				));
		System.out.println("===================================================================================");
		sortedMoves.entrySet().stream()
				.sorted(Comparator.comparingInt(m -> m.getValue()))
				.forEach(e -> System.out.println(e));
		return sortedMoves.entrySet().stream()
				.max(Comparator.comparingInt(m -> m.getValue()))
				.get().getKey();
		//return moves.get(new Random().nextInt(moves.size()));
	}
}