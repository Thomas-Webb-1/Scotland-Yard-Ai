package uk.ac.bris.cs.scotlandyard.ui.ai;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.GraphBuilder;
import com.google.common.collect.ImmutableSet;
import org.jspecify.annotations.NonNull;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

    @SuppressWarnings("UnstableApiUsage")
    public class PathfindingGraph {

        private final ImmutableValueGraph<Integer,ImmutableSet<ScotlandYard.Transport>> graph;
        private final int[][] distances;
        private final  Map<ScotlandYard.Transport,boolean[][]> adjacencyMatrices;
        private final  float[] centrality;

        PathfindingGraph(ImmutableValueGraph<Integer,ImmutableSet<ScotlandYard.Transport>> graph){
            Set<Integer> nodes = graph.nodes();
            int numNodes = graph.nodes().size();

            if (!nodes.stream().allMatch(n -> n >= 1 && n <= numNodes)) {
                throw new IllegalArgumentException("The graph must have nodes with values 1,2,3,..,n");
            }

            int[][] d = new int[numNodes][numNodes];
            Map<ScotlandYard.Transport,boolean[][]> adjacencyMatrices = new HashMap<>();
            float[] centrality = new float[numNodes];

            for (int[] row : d)
                Arrays.fill(row, numNodes);//prefill the matrix with a number larger than the longest possible path between two nodes


            for (var transport :ScotlandYard.Transport.values()){
                adjacencyMatrices.put(transport,new boolean[numNodes][numNodes]);
            };


            //calculate distances using Floyd-Warshall Algorithm and adjacency matrices;
            for (var edge : graph.edges()){
                int start = edge.nodeU();
                int end = edge.nodeV();

                //Ignore paths that can only be taken by mrX
                ImmutableSet<ScotlandYard.Transport> values = graph.edgeValue(edge).orElseThrow();
                if (!values.stream().allMatch(v->v.requiredTicket() == ScotlandYard.Ticket.SECRET)){
                    d[start-1][end-1] = 1;
                    d[end-1][start-1] = 1;
                }



                values.forEach((transport)->{
                    var matrix = adjacencyMatrices.get(transport);
                    matrix[start-1][end-1] = true;
                    matrix[end-1][start-1] = true;
                });
            }



            for (var k : nodes){
                for (var i : nodes){
                    for (var j : nodes){
                        d[i-1][j-1] = Math.min(d[i-1][j-1], d[i-1][k-1] + d[k-1][j-1]);
                    };
                };
            };

            for (var x : nodes){
                //TODO replace this with a more concrete calculation or PageRank
                centrality[x-1] = (float) 1 / Arrays.stream(d[x-1]).sum() / Arrays.stream(d[x-1]).count();
            }

            this.graph = graph;
            this.distances = d;
            this.adjacencyMatrices = adjacencyMatrices;
            this.centrality = centrality;
        }

        //returns the distance between two nodes
        public Integer getDistance(@NonNull Integer source, @NonNull Integer destination){
            return distances[source-1][destination-1];
        };


        public float getCentrality(@NonNull Integer node){
            return centrality[node-1];
        }

        //returns whether two nodes are adjacent
        public boolean isAdjacent(ScotlandYard.@NonNull Transport transport, int node1, int node2){
            return adjacencyMatrices.get(transport)[node1-1][node2-1];
        }

        public ImmutableValueGraph<Integer,ImmutableSet<ScotlandYard.Transport>> getGraph(){
            return graph;
        }
    }