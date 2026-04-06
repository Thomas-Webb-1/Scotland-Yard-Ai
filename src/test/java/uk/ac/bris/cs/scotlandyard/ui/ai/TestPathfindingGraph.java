package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import org.junit.Test;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

public class TestPathfindingGraph {
    @Test(expected = IllegalArgumentException.class)
    public void TestInvalidGraphThrows() {
        MutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> invalidGraph =
                ValueGraphBuilder.undirected().build();

        invalidGraph.addNode(1);
        invalidGraph.addNode(2);
        invalidGraph.addNode(67);

        invalidGraph.putEdgeValue(
                1,
                2,
                ImmutableSet.of(ScotlandYard.Transport.TAXI)
        );
        new PathfindingGraph(ImmutableValueGraph.copyOf(invalidGraph));
    }



    @Test
    public void TestDistances () throws IOException {
        PathfindingGraph graph = new PathfindingGraph(ScotlandYard.standardGraph());
        assertThat(graph.getDistance(1,47)).isEqualTo(2);
        assertThat(graph.getDistance(1,185)).isEqualTo(5);
        assertThat(graph.getDistance(194,157)).isEqualTo(5);
        assertThat(graph.getDistance(13,3)).isEqualTo(2);

        for (var node : ScotlandYard.standardGraph().nodes()){
            //TODO it may make more sense for this to be 0
            assertThat(graph.getDistance(node,node)).isEqualTo(2);
        }

        for (var source : ScotlandYard.standardGraph().nodes()){
            for (var destination : ScotlandYard.standardGraph().nodes()){
                assertThat(graph.getDistance(source,destination)).isEqualTo(graph.getDistance(destination,source));

            }
        }



    }
    @Test
    public void TestAdjacency() throws IOException {
        PathfindingGraph graph = new PathfindingGraph(ScotlandYard.standardGraph());
        assertThat(graph.isAdjacent(ScotlandYard.Transport.TAXI,1,9)).isTrue();
        assertThat(graph.isAdjacent(ScotlandYard.Transport.UNDERGROUND,1,46)).isTrue();
        assertThat(graph.isAdjacent(ScotlandYard.Transport.UNDERGROUND,1,9)).isFalse();
        assertThat(graph.isAdjacent(ScotlandYard.Transport.FERRY,194,157)).isTrue();

    }

    @Test
    public  void TestCentrality() throws  IOException{
        PathfindingGraph graph = new PathfindingGraph(ScotlandYard.standardGraph());
        assertThat(graph.getCentrality(67)).isGreaterThan(graph.getCentrality(120));
        assertThat(graph.getCentrality(67)).isEqualTo(graph.getCentrality(67));
        assertThat(graph.getCentrality(163)).isGreaterThan(graph.getCentrality(149));

    }
}
