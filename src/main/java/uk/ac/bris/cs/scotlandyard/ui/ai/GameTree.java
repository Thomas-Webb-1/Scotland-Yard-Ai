package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.util.*;

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
            children = expandFullTurn(state);
        }
        return children;
    }

    private List<GameTree> expandFullTurn(Board.GameState initialState) {
        if (isMrXTurn()){
            List<GameTree> result = new ArrayList<>();
            for (Move move : initialState.getAvailableMoves()) {
                result.add(new GameTree(initialState.advance(move), move));
            }
            return result;
        } else {


            Deque<Board.GameState> queue = new ArrayDeque<>(initialState.getAvailableMoves().stream().map(initialState::advance).toList());
            List<GameTree> result = new ArrayList<>();
            Set<Set<Move>> moveSetSet = new HashSet<>();
            while (!queue.isEmpty()){
                var s = queue.pollFirst();
                if (!s.getWinner().isEmpty() || s.getAvailableMoves().stream().findFirst().orElseThrow().commencedBy().isMrX()){
                    result.add(new GameTree(s));
                } else {
                    for (var move : s.getAvailableMoves()){
                        queue.add(s.advance(move));
                    }
                }

            }
            return result;
        }
    }

    public boolean isMrXTurn() {
        return getState().getAvailableMoves().stream()
                .findFirst()
                .map(m -> m.commencedBy().isMrX())
                .orElse(false);
    }

    void free() {
        if (children != null) {
            children.clear();
        }
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
