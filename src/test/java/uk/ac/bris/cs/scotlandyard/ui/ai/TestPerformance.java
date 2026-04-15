package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Pair;
import org.junit.Test;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class TestPerformance {

    @Test
    public void testFastGamestate() throws IOException {

       FastGameState state = FastGameState.stateFromBoard(getTestBoard());

        for (int i = 0; i < 100_000; i++) {
            state.getMoves();
        }

        long start = System.nanoTime();

        int iterations = 1_000_000;
        int explored = 0;

        for (int i = 0; i < iterations; i++) {
            FastGameState current = state;

            for (int depth = 0; depth < 8; depth++) {
                if (current == null || current.getWinner() != null) break;

                int moves = current.getMoves();
                if (moves == 0) break;

                int move = i % moves;
                current = current.advanceOrNull(move);

                explored++;
            }
        }

        long end = System.nanoTime();

        double seconds = (end - start) / 1_000_000_000.0;

        System.out.println("States explored " + explored + " in " + seconds +" seconds");

    }
    @Test
    public void testMiniMax() throws IOException {

        MiniMiniMax ai = new MiniMiniMax();


        long start = System.nanoTime();

        Move move = ai.pickMove(
                getTestBoard(),
                Pair.pair(30L, TimeUnit.SECONDS)
        );

        long end = System.nanoTime();

        double ms = (end - start) / 1_000_000.0;

        System.out.println("Move chosen: " + move);
        System.out.println("Time taken (ms): " + ms);

    }

    private Board getTestBoard() throws IOException {
        MyGameStateFactory factory = new MyGameStateFactory();

        GameSetup setup = new GameSetup(
                ScotlandYard.standardGraph(),
                ScotlandYard.STANDARD24MOVES
        );

        Player mrX = new Player(
                Piece.MrX.MRX,
                ScotlandYard.defaultMrXTickets(),
                ScotlandYard.generateMrXLocation(3453)
        );

        Player d1 = new Player(
                Piece.Detective.BLUE,
                ScotlandYard.defaultDetectiveTickets(),
                26
        );

        Player d2 = new Player(
                Piece.Detective.RED,
                ScotlandYard.defaultDetectiveTickets(),
                67
        );

        Player d3 = new Player(
                Piece.Detective.GREEN,
                ScotlandYard.defaultDetectiveTickets(),
                94
        );

        Player d4 = new Player(
                Piece.Detective.YELLOW,
                ScotlandYard.defaultDetectiveTickets(),
                127
        );

        Player d5 = new Player(
                Piece.Detective.WHITE,
                ScotlandYard.defaultDetectiveTickets(),
                9
        );

        return factory.build(
                setup,
                mrX,
                d1, d2, d3, d4, d5
        );
    }
}