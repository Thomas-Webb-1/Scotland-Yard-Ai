package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import jakarta.annotation.Nonnull;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

public class OneStepLookahead implements Ai {

	@Nonnull @Override public String name() { return "One Step Lookahead"; }

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
		// sets the distance to all nodes to infinity, except the source
		nodes.stream().filter(n -> !n.equals(source)).forEach(n -> dist.put(n, Integer.MAX_VALUE));
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
						   }
		);
	}

	public class MyBoard {
		private ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph;
		private ImmutableMap<Piece.Detective, Integer> detectives;
		private Integer mrXLocation;
		private ImmutableMap<Integer, Integer> distancesFromMrX;
		private boolean mrXTurn;
		private ImmutableSet<Piece> winner;

		public MyBoard(
				ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph,
				ImmutableMap<Piece.Detective, Integer> detectives,
				Integer mrXLocation,
				boolean mrXTurn
		) {
			this.graph = graph;
			this.detectives = detectives;
			this.mrXLocation = mrXLocation;
			this.distancesFromMrX = ImmutableMap.copyOf(dijkstra(graph, mrXLocation));
			this.mrXTurn = mrXTurn;
			// don't calculate the amount of moves left
			this.winner = detectives.values().contains(mrXLocation) ? getDetectivesSet() : ImmutableSet.of();
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
			this.distancesFromMrX = ImmutableMap.copyOf(dijkstra(graph, mrXLocation));
			this.mrXTurn = board.getAvailableMoves().stream().findFirst().orElseThrow().commencedBy().isMrX();
			this.winner = board.getWinner();
		}

		public MyBoard(Board board, Move move) {
			this.graph = board.getSetup().graph;
			this.detectives = board.getPlayers().stream()
					.filter(Piece::isDetective)
					.map(p -> (Piece.Detective) p)
					.collect(ImmutableMap.toImmutableMap(
							Function.identity(),
							d -> board.getDetectiveLocation(d).orElseThrow()
					));
			// this should only be called when it is Mr. X's turn.
			this.mrXLocation = getMoveDestination(move);
			this.distancesFromMrX = ImmutableMap.copyOf(dijkstra(graph, mrXLocation));
			this.mrXTurn = board.getAvailableMoves().stream().findFirst().orElseThrow().commencedBy().isMrX();
			this.winner = board.getWinner();
		}

		public ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> getGraph() {
			return graph;
		}

		public ImmutableMap<Piece.Detective, Integer> getDetectives() {
			return detectives;
		}

		public ImmutableSet<Piece> getDetectivesSet() {
			return detectives.keySet().stream().map(d -> (Piece) d).collect(ImmutableSet.toImmutableSet());
		}

		public Integer getMrXLocation() {
			return mrXLocation;
		}

		public boolean isMrXTurn() {
			return mrXTurn;
		}

		public ImmutableSet<Piece> getWinner() {
			return winner;
		}

		public Integer heuristic() {
			Integer score = 0;
			// if a detective is on Mr. X, lose a lot of score.
			if (detectives.containsValue(mrXLocation)) score -= 100000;

			ImmutableMap<Piece.Detective, Integer> distancesFromDetectives = detectives.entrySet().stream()
					.collect(ImmutableMap.toImmutableMap(
                            Map.Entry::getKey,
							e -> Objects.requireNonNull(distancesFromMrX.get(e.getValue()))
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
	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		var moves = board.getAvailableMoves().asList();
		return moves.stream()
				.max(Comparator.comparingInt(m -> new MyBoard(board, m).heuristic()))
				.get();
	}
}
