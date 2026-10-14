package uk.ac.bris.cs.scotlandyard.ui.ai;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;


@RunWith(Suite.class)
    @Suite.SuiteClasses({
            TestPathfindingGraph.class,
            TestStateUtils.class,
            TestFastGameState.class,
    })
    public class AllTest { }
