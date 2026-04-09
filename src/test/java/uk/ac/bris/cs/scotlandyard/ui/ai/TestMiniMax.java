package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.io.IOException;

public class TestMiniMax {

    @Test
    public void TestMiniMax() throws IOException {
        var factory = new MyGameStateFactory();

        Player mrX = new Player(
                Piece.MrX.MRX,
                ScotlandYard.defaultMrXTickets(),
                45
        );

        ImmutableList<Player> detectives = ImmutableList.of(
                //new Player(Piece.Detective.RED,   ScotlandYard.defaultDetectiveTickets(), 13),
                //new Player(Piece.Detective.BLUE,  ScotlandYard.defaultDetectiveTickets(), 26),
                new Player(Piece.Detective.GREEN,  ScotlandYard.defaultDetectiveTickets(), 86),
                new Player(Piece.Detective.YELLOW,  ScotlandYard.defaultDetectiveTickets(), 124)
        );

        Board.GameState state = factory.build(
                new GameSetup(
                        ScotlandYard.standardGraph(),
                        ScotlandYard.STANDARD24MOVES
                ),
                mrX,
                detectives
        );

        GameTree tree = new GameTree(state);

        var ai = new Nightingale2();
        System.out.println(ai.pickMove(tree.getState(),null));
    }
}
