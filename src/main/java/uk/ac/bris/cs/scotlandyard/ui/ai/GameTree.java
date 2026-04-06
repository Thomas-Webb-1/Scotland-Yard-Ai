package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.util.ArrayList;
import java.util.List;

import static uk.ac.bris.cs.scotlandyard.ui.ai.Team.*;

public class GameTree {
    private final Board.GameState state;
    private List<GameTree> children;
    private Move move;

    GameTree(Board.GameState state) {
        this.state = state;
        this.children = new ArrayList<>();
    }

    GameTree(Board.GameState state, Move move) {
        this.state = state;
        this.move = move;
        this.children = new ArrayList<>();
    }

    static GameTree fromBoard(Board board, Integer mrXLocation) {

        //extract pieces from the gamestate then enrich to players
        ImmutableList<Player> players = board.getPlayers().stream().map((p) -> {

            Integer location = p.isMrX() ? mrXLocation : board.getDetectiveLocation((Piece.Detective) p).orElseThrow();
            ImmutableMap.Builder<ScotlandYard.Ticket, Integer> builder2 = ImmutableMap.builder();

            var ticketBoard = board.getPlayerTickets(p).orElseThrow();

            for (ScotlandYard.Ticket ticket : ScotlandYard.Ticket.values()) {
                builder2.put(ticket, ticketBoard.getCount(ticket));
            }

            ImmutableMap<ScotlandYard.Ticket, Integer> tickets = builder2.build();

            return new Player(p, tickets, location);

        }).collect(ImmutableList.toImmutableList());

        //create a new setup with any played moves removed
        GameSetup oldSetup = board.getSetup();
        GameSetup newSetup = new GameSetup(
                oldSetup.graph,
                ImmutableList.copyOf(
                        oldSetup.moves.subList(
                                board.getMrXTravelLog().size(),
                                oldSetup.moves.size()
                        )
                )
        );

        //create a new gamestate using game state factory
        var factory = new MyGameStateFactory();
        return new GameTree(factory.build(
                newSetup,
                players.stream().filter(Player::isMrX).findFirst().orElseThrow(),
                players.stream().filter(Player::isDetective).collect(ImmutableList.toImmutableList())
        ));


    }

    Board.GameState getState() {
        return state;
    };

    Move getPrecedingMove() {
        return move;
    };

    List<GameTree> getChildren() {
        //Lazy load child nodes
        if (children.isEmpty() && state.getWinner().isEmpty()) {
            children = state.getAvailableMoves().stream().map(
                    move -> new GameTree(state.advance(move), move)
            ).toList();
        }
        return children;
    }

    void free() {
        this.children = null;
    }

    Team getWinner(){
        if (this.state.getWinner().isEmpty()) return null;
        if (this.state.getWinner().stream().findFirst().orElseThrow().isMrX()) return MRX;
        else return DETECTIVES;
    }

    <T> T accept(GameTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
