package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;

import jakarta.annotation.Nonnull;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.Detective;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AIUtils {
    // Prevents instantiation
    private AIUtils() { }

    public static Integer getMoveDestination(Move move) {
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
        for (Integer node : nodes) {
            if (!node.equals(source)) {
                dist.put(node, Integer.MAX_VALUE); // sets all distances to inf, except source.
            }
        }
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
                    if (dist[i][j] > dist[i][k] + dist[k][j]) {
                        dist[i][j] = dist[i][k] + dist[k][j];
                    }
                }
            }
        }
        return dist;
    }

    public static Integer getDistance(Board board, Integer source, Integer destination) {
        return dijkstra(board.getSetup().graph, source).get(destination);
    }

    public static Map<Piece.Detective, Integer> detectiveLocations(Board board) {
        return Arrays.stream(Piece.Detective.values())
                .collect(Collectors.toMap(
                        d -> d,
                        d -> board.getDetectiveLocation(d).orElseThrow()
                ));
    }

    @Nonnull
    public static Map<Piece.Detective, Integer> detectiveDistances(@Nonnull Board board, Move move) {
        return move.accept(new Move.Visitor<Map<Piece.Detective, Integer>>() {
            @Override
            public Map<Piece.Detective, Integer> visit(Move.SingleMove move) {
                return Arrays.stream(Piece.Detective.values())
                        .collect(Collectors.toMap(
                                d -> d,
                                d -> getDistance(board, move.destination, board.getDetectiveLocation(d).orElseThrow())
                        ));
            }

            @Override
            public Map<Piece.Detective, Integer> visit(Move.DoubleMove move) {
                return Arrays.stream(Piece.Detective.values())
                        .collect(Collectors.toMap(
                                d -> d,
                                d -> getDistance(board, move.destination2, board.getDetectiveLocation(d).orElseThrow())
                        ));
            }
        });
    }

    public static Integer totalDetectiveDistances(@Nonnull Board board, Move move) {
        return detectiveDistances(board, move).values().stream()
                .reduce(0, Integer::sum);
    }

    public static boolean isDetectiveOneAway(Board board, Move move) {
        // find the location of the move
        // can a detective catch mrX here? (is detective 1 away)
        // return true/false
        return board.getSetup().graph.adjacentNodes(getMoveDestination(move)).stream()
                .anyMatch(n -> detectiveLocations(board).containsValue(n));
    }
}

