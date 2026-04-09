package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.graph.ImmutableValueGraph;

import com.sun.scenario.effect.Identity;
import jakarta.annotation.Nonnull;
import org.jspecify.annotations.NonNull;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AIUtils {
    // Prevents instantiation
    private AIUtils() { }

    public static Integer moveDestination(Move move) {
        return move.accept(new Move.Visitor<Integer>(){

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

    // take in a node, return a hash map of all separate nodes and their distances.
    // this is probably better, as we have to call this every time the AI has to pick a move.
    // -> if there's a way to store a hashmap of floyd-warshall without recalculation every move, that would be better.
    // implementation modified from wikipedia.
    public static Map<Integer, Integer> dijkstra(
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

    public static ImmutableMap<ScotlandYard.Ticket, Integer> getTickets(Board board, Piece piece) {
        return Arrays.stream(ScotlandYard.Ticket.values())
                .collect(ImmutableMap.toImmutableMap(
                        Function.identity(),
                        t -> board.getPlayerTickets(piece).get().getCount(t)
                ));
    }

    public static ImmutableMap<Piece, ImmutableMap<ScotlandYard.Ticket, Integer>> getAllTickets(Board board) {
        return board.getPlayers().stream()
                .collect(ImmutableMap.toImmutableMap(
                        Function.identity(),
                        p -> getTickets(board, p)
                ));
    }

    // take in a graph, return a 2d array with the distances between every node.
    // implementation modified from wikipedia.
    public static Integer[][] floydWarshall(ImmutableValueGraph<Integer, Set<ScotlandYard.Transport>> graph) {
        int n = graph.nodes().size();
        Integer[][] dist = new Integer[n][n];
        Arrays.stream(dist).forEach(r -> Arrays.fill(r, Integer.MAX_VALUE));
        graph.edges().forEach(edge ->
                dist[edge.nodeU()][edge.nodeV()] = dist[edge.nodeV()][edge.nodeU()] = 1);
        graph.nodes().forEach(node -> dist[node][node] = 0);
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    dist[i][j] = Math.min(dist[i][j], dist[i][k] + dist[k][j]);
                }
            }
        }
        return dist;
    }

    public static Integer getDistance(Board board, Integer source, Integer destination) {
        return dijkstra(board.getSetup().graph, source).get(destination);
    }

    public static ImmutableMap<Piece.Detective, Integer> detectiveLocations(Board board) {
        return Arrays.stream(Piece.Detective.values())
                .filter(d -> board.getPlayers().contains(d))
                .collect(ImmutableMap.toImmutableMap(
                        Function.identity(),
                        d -> board.getDetectiveLocation(d).orElseThrow()
                ));
    }

    @Nonnull
    public static ImmutableMap<Piece.Detective, Integer> detectiveDistances(@Nonnull Board board, Move move) {
        return move.accept(new Move.Visitor<ImmutableMap<Piece.Detective, Integer>>() {
            @Override
            public ImmutableMap<Piece.Detective, Integer> visit(Move.SingleMove move) {
                return Arrays.stream(Piece.Detective.values())
                        .filter(d -> board.getPlayers().contains(d))
                        .collect(ImmutableMap.toImmutableMap(
                                Function.identity(),
                                d -> getDistance(board, move.destination, board.getDetectiveLocation(d).orElseThrow())
                        ));
            }

            @Override
            public ImmutableMap<Piece.Detective, Integer> visit(Move.DoubleMove move) {
                return Arrays.stream(Piece.Detective.values())
                        .filter(d -> board.getPlayers().contains(d))
                        .collect(ImmutableMap.toImmutableMap(
                                Function.identity(),
                                d -> getDistance(board, move.destination2, board.getDetectiveLocation(d).orElseThrow())
                        ));
            }
        });
    }

    public static Integer totalDetectiveDistances(@Nonnull Board board, Move move) {
        return detectiveDistances(board, move).values().stream()
                .reduce(0, Integer::sum);
    }

    public static boolean isDetectiveHere(Board board, Integer location) {
        return detectiveLocations(board).containsValue(location);
    }

    public static boolean isDetectiveOneAway(Board board, Move move) {
        // find the location of the move
        // can a detective catch mrX here? (is detective 1 away)
        // return true/false
        return board.getSetup().graph.adjacentNodes(moveDestination(move)).stream()
                .anyMatch(n -> detectiveLocations(board).containsValue(n));
    }

    public static Board generateBoardFromMoves(Board board, ImmutableSet<Move> moves) {
        return new ImmutableBoard(
                board.getSetup(),
                detectiveLocations(board),
                getAllTickets(board), // TODO: need to update this
                board.getMrXTravelLog(), // TODO: need to implement this
                board.getWinner(),
                board.getAvailableMoves()
        );
    }

    public static ImmutableSet<Move.SingleMove> makeSingleMoves(@Nonnull Board board, Piece piece, Integer source) {
        Set<Integer> allDetectiveLocations = new HashSet<>(detectiveLocations(board).values());
        return board.getSetup().graph.adjacentNodes(source).stream()
                .filter(n -> !allDetectiveLocations.contains(n))
                .flatMap(n -> Stream.concat(
                                // remove potential move if piece doesn't have enough tickets.
                                Objects.requireNonNull(board.getSetup()
                                                .graph
                                                .edgeValueOrDefault(source, n, ImmutableSet.of()))
                                        .stream()
                                        .map(ScotlandYard.Transport::requiredTicket)
                                        .filter(t -> board.getPlayerTickets(piece).orElse(_ -> 0)
                                                .getCount(t) > 0),
                                // if piece has secret ticket, they are able to move to any adjacent node.
                                board.getPlayerTickets(piece).orElse(_ -> 0).getCount(ScotlandYard.Ticket.SECRET) > 0
                                        ? Stream.of(ScotlandYard.Ticket.SECRET)
                                        : Stream.of())
                        .map(t -> new Move.SingleMove(piece, source, t, n)))
                .collect(ImmutableSet.toImmutableSet());
    }

    public static ImmutableSet<Move.DoubleMove> makeDoubleMoves(@Nonnull Board board, Piece piece, Integer source) {
        // return empty set if piece doesn't have double ticket, or if the game ends in less than 2 moves.
        if (board.getPlayerTickets(piece).orElse(_ -> 0).getCount(ScotlandYard.Ticket.DOUBLE) <= 0
                || board.getSetup().moves.size() < board.getMrXTravelLog().size() + 2) return ImmutableSet.of();
        // for each potential move, find it's potential move.
        return makeSingleMoves(board, piece, source).stream()
                .flatMap(first -> makeSingleMoves(board, piece, moveDestination(first)).stream()
                        // remove potential move if player doesn't have enough tickets.
                        .filter(second -> first.ticket != second.ticket
                                || board.getPlayerTickets(piece).orElse(_ -> 0).getCount(second.ticket) >= 2)
                        .map(second -> new Move.DoubleMove(
                                piece,
                                source,
                                first.ticket,
                                first.destination,
                                second.ticket,
                                second.destination)))
                .collect(ImmutableSet.toImmutableSet());
    }

    public static ImmutableSet<Move> getPieceAvailableMoves(@Nonnull Board board, Piece piece, Integer source) {
        return Stream.concat(
                        makeSingleMoves(board, piece, source).stream(),
                        makeDoubleMoves(board, piece, source).stream())
                .collect(ImmutableSet.toImmutableSet());
    }

    public static ImmutableSet<ImmutableSet<Move>> detectiveMoveCartesianProduct(@Nonnull Board board) {
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
        return combinations.stream()
                .filter(ml ->
                        ml.stream().count() == ml.stream().map(AIUtils::moveDestination).distinct().count())
                .map(ml -> ImmutableSet.copyOf(ml))
                .collect(ImmutableSet.toImmutableSet());
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

    public static ImmutableMap<Piece, ImmutableMap<ScotlandYard.Ticket, Integer>> getTicketsFromBoard(Board board) {
        return board.getPlayers().stream()
                .collect(ImmutableMap.toImmutableMap(
                        p -> p,
                        p -> Arrays.stream(ScotlandYard.Ticket.values())
                                .collect(ImmutableMap.toImmutableMap(
                                        t -> t,
                                        t -> board.getPlayerTickets(p).orElse(_ -> 0).getCount(t)
                                ))
                ));
    }
}
