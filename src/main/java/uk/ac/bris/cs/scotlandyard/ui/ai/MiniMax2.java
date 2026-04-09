package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import jakarta.annotation.Nonnull;
import org.jspecify.annotations.NonNull;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.ac.bris.cs.scotlandyard.ui.ai.AIUtils.detectiveMoveCartesianProduct;

public class MiniMax implements Ai {

	@Nonnull @Override public String name() { return "MiniMax"; }
	private final class MyBoard implements Board {
		private final GameSetup setup;
		private final ImmutableMap<Piece.Detective, Integer> detectiveLocations;
		private final Integer mrXLocation;
		private final ImmutableList<LogEntry> mrXTravelLog;
		private final ImmutableMap<Piece, ImmutableMap<ScotlandYard.Ticket, Integer>> tickets;
		private final ImmutableSet<Piece> winner;
		private final boolean isMrXTurn;
		private final ImmutableSet<ImmutableSet<Move>> moveSet;

		MyBoard(
				GameSetup setup,
				ImmutableMap<Piece.Detective, Integer> detectiveLocations,
				Integer mrXLocation, // assumes Mr. X goes first. if he doesn't, we don't know his position.
				ImmutableList<LogEntry> mrXTravelLog,
				ImmutableMap<Piece, ImmutableMap<ScotlandYard.Ticket, Integer>> tickets,
				ImmutableSet<Piece> winner,
				boolean isMrXTurn
		) {
			this.setup = setup;
			this.detectiveLocations = detectiveLocations;
			this.mrXLocation = mrXLocation;
			this.mrXTravelLog = mrXTravelLog;
			this.tickets = tickets;
			this.winner = winner;
			this.isMrXTurn = isMrXTurn;
			if (isMrXTurn) {
				ImmutableSet<Move> moves =
						Stream.concat(
										makeSingleMoves(Piece.MrX.MRX, mrXLocation).stream(),
										makeDoubleMoves(Piece.MrX.MRX, mrXLocation).stream()
								)
								.collect(ImmutableSet.toImmutableSet());
				ImmutableSet<ImmutableSet<Move>> moveSet = ImmutableSet.<ImmutableSet<Move>>builder()
						.add(moves)
						.build();
				return moveSet;
			}
			else {
				this.moves = potentialDetectiveMoves();
			}
		}

		@Override
		public @NonNull GameSetup getSetup() {
			return setup;
		}

		public @Nonnull Integer getMrXLocation() {
			return mrXLocation;
		}

		@Override
		public @NonNull ImmutableSet<Piece> getPlayers() {
			return tickets.keySet();
		}

		@Override
		public @NonNull Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
			return Optional.ofNullable(detectiveLocations.get(detective));
		}

		@Override
		public @NonNull Optional<TicketBoard> getPlayerTickets(Piece piece) {
			return Optional.ofNullable(tickets.get(piece))
					.map(tickets -> ticket -> tickets.getOrDefault(ticket, 0));
		}

		@Override
		public @NonNull ImmutableList<LogEntry> getMrXTravelLog() {
			return mrXTravelLog;
		}

		@Override
		public @NonNull ImmutableSet<Piece> getWinner() {
			return winner;
		}


		public ImmutableSet<Move.SingleMove> makeSingleMoves(Piece piece, Integer source) {
			return setup.graph.adjacentNodes(source).stream()
					.filter(n -> !detectiveLocations.containsValue(n))
					.flatMap(n -> Stream.concat(
									// remove potential move if piece doesn't have enough tickets.
									Objects.requireNonNull(setup
													.graph
													.edgeValueOrDefault(source, n, ImmutableSet.of()))
											.stream()
											.map(ScotlandYard.Transport::requiredTicket)
											.filter(t -> getPlayerTickets(piece).orElse(_ -> 0)
													.getCount(t) > 0),
									// if piece has secret ticket, they are able to move to any adjacent node.
									getPlayerTickets(piece).orElse(_ -> 0).getCount(ScotlandYard.Ticket.SECRET) > 0
											? Stream.of(ScotlandYard.Ticket.SECRET)
											: Stream.of())
							.map(t -> new Move.SingleMove(piece, source, t, n)))
					.collect(ImmutableSet.toImmutableSet());
		}

		public ImmutableSet<Move.DoubleMove> makeDoubleMoves(Piece piece, Integer source) {
			// return empty set if piece doesn't have double ticket, or if the game ends in less than 2 moves.
			if (getPlayerTickets(piece).orElse(_ -> 0).getCount(ScotlandYard.Ticket.DOUBLE) <= 0
					|| getSetup().moves.size() < getMrXTravelLog().size() + 2) return ImmutableSet.of();
			// for each potential move, find it's potential move.
			return makeSingleMoves(piece, source).stream()
					.flatMap(first -> makeSingleMoves(piece, getMoveDestination(first)).stream()
							// remove potential move if player doesn't have enough tickets.
							.filter(second -> first.ticket != second.ticket
									|| getPlayerTickets(piece).orElse(_ -> 0).getCount(second.ticket) >= 2)
							.map(second -> new Move.DoubleMove(
									piece,
									source,
									first.ticket,
									first.destination,
									second.ticket,
									second.destination)))
					.collect(ImmutableSet.toImmutableSet());
		}

		public @Nonnull ImmutableSet<Move> getAvailableMoves() {
			return getPlayers().stream()
					.flatMap(p -> Stream.concat( p.isDetective()
											? makeSingleMoves(p, getDetectiveLocation((Piece.Detective) p).orElseThrow()).stream()
											: makeSingleMoves(p, mrXLocation).stream(),
									makeDoubleMoves(p, mrXLocation).stream()
							)
					)
					.collect(ImmutableSet.toImmutableSet());
		}
	}

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

	public ImmutableMap<Piece.Detective, Integer> getAllDetectiveLocations(Board board) {
		return board.getPlayers().stream()
				.filter(Piece::isDetective)
				.map(p -> (Piece.Detective) p)
				.collect(ImmutableMap.toImmutableMap(
						Function.identity(),
						d -> board.getDetectiveLocation(d).orElseThrow()
				));
	}

	public static ImmutableSet<ImmutableMap<Piece.Detective, Integer>> potentialDetectiveLocations(@Nonnull Board board) {
		List<List<Move>> combinations = board.getPlayers().stream()
				.filter(Piece::isDetective)
				.map(p -> (Piece.Detective) p)
				.map(d -> getPieceAvailableMoves(board, d, board.getDetectiveLocation(d).get()))
				.reduce(
						List.of(List.of()),
						(c, l) -> c.stream()
								.flatMap(combo -> l.stream()
										.map(item -> {
											List<Move> newList = new ArrayList<>(combo);
											newList.add(item);
											return newList;
										}))
								.collect(Collectors.toList()),
						(a, b) -> {
							List<List<Move>> res = new ArrayList<>(a);
							res.addAll(b);
							return res;
						}
				);
		// filter out scenarios where 2 detectives end up on the same location.
		List<List<Move>> moveSets = combinations.stream()
				.filter(ml ->
						ml.stream().count() == ml.stream().map(AIUtils::moveDestination).distinct().count())
				.collect(Collectors.toList());

		return moveSets.stream()
				.map(ms -> {
					List<Move> newList = ms;
					return newList.stream()
							.collect(ImmutableMap.toImmutableMap(
									m -> (Piece.Detective) m.commencedBy(),
									m -> moveDestination(m)
							));
				})
				.collect(ImmutableSet.toImmutableSet());
	}


	public ImmutableMap<Piece, ImmutableMap<ScotlandYard.Ticket, Integer>> getAllPlayerTickets(Board board) {
		return board.getPlayers().stream()
				.collect(ImmutableMap.toImmutableMap(
						Function.identity(),
						p -> Arrays.stream(ScotlandYard.Ticket.values())
								.collect(ImmutableMap.toImmutableMap(
										Function.identity(),
										t -> board.getPlayerTickets(p).orElse(_ -> 0).getCount(t)
								))
				));
	}

	@Nonnull public Integer heuristic(Board board, Integer mrXLocation, boolean mrXTurn) {
		if (mrXTurn) {

		}
	}

	@Nonnull public Integer minimax(Board board, Integer depth, boolean mrXTurn) {
		Integer value;
		if (depth == 0 || !board.getWinner().isEmpty()) {
			return heuristic(Board);
		}
		if (mrXTurn) {
			value = Integer.MIN_VALUE;
			return board.getAvailableMoves().stream()
					.map(m -> {
								Board child = new MyBoard(
										board.getSetup(),
										getAllDetectiveLocations(board),
										getMoveDestination(m),
										board.getMrXTravelLog(), // TODO: update in the future
										getAllPlayerTickets(board),
										ImmutableSet.of(), // TODO: Set a winner
										false
								);
								return Math.max(value, minimax(child, depth - 1, false));
							}
					)
					.max(Integer::compare).get();
		}
		else {
			value = Integer.MAX_VALUE;
			return get
			return board.getAvailableMoves().stream()
					.map(m -> {
								Board child = new MyBoard(
										board.getSetup(),
										getAllDetectiveLocations(board), // TODO: use the set function
										board.getMrXTravelLog().stream().flatMap(log -> log.location().stream()).reduce((a, b) -> b).orElse(100),
										board.getMrXTravelLog(),
										getAllPlayerTickets(board),
										ImmutableSet.of(),
										true
								)
								return Math.min(value, minimax(child, depth - 1, true));
							}
					)
					.min(Integer::compare).get()
		}
	}

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		var moves = board.getAvailableMoves().asList();
		return moves.get(new Random().nextInt(moves.size()));
	}
}