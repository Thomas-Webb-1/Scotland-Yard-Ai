package uk.ac.bris.cs.scotlandyard.ui.ai;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.GraphBuilder;
import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard;

import java.util.Arrays;

public class PathfindingGraph {

    private ImmutableValueGraph<Integer,ImmutableSet<ScotlandYard.Transport>> graph;
    private int[][] distances;

    PathfindingGraph(ImmutableValueGraph<Integer,ImmutableSet<ScotlandYard.Transport>> graph){
        this.graph = graph;

        int[][] d = new int[200][200];

        for (int[] row : d)
            Arrays.fill(row, 999);

        //calculate distances using Floyd-Warshall Algorithm;

        graph.edges().forEach((edge)->{
            var value = graph.edgeValue(edge.nodeU(),edge.nodeV());
            d[edge.nodeU()][edge.nodeV()] = 1;
            d[edge.nodeV()][edge.nodeU()] = 1;

        });

        graph.nodes().forEach((k)->{
            graph.nodes().forEach((i)->{
                graph.nodes().forEach((j)->{
                    if (d[i][j] > d[i][k] + d[k][j]){
                        d[i][j] = d[i][k] + d[k][j];
                    }

                });

            });

        });

        this.distances = d;

    }

    public Integer getDistance(Integer source, Integer dest){
        return distances[source][dest];

    };
}
