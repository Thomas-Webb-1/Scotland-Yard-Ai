package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.BeforeClass;
import org.junit.Test;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.GameSetup;
import uk.ac.bris.cs.scotlandyard.model.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.Player;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestFastGameState {

    private static MoveStore moveStore;

    @BeforeClass
    public static void setUp() throws IOException {
        moveStore = new MoveStore(ScotlandYard.standardGraph());
    }

    private Board createBoard(int mrXLocation, ImmutableList<Boolean> moves,
                              ImmutableMap<ScotlandYard.Ticket, Integer> mrXTickets,
                              ImmutableMap<ScotlandYard.Ticket, Integer> detectiveTickets,
                              int... detectiveLocations) throws IOException {
        MyGameStateFactory factory = new MyGameStateFactory();
        GameSetup setup = new GameSetup(ScotlandYard.standardGraph(), moves);

        Player mrX = new Player(Piece.MrX.MRX, mrXTickets, mrXLocation);
        Piece.Detective[] pieces = {
                Piece.Detective.BLUE,
                Piece.Detective.RED,
                Piece.Detective.GREEN,
                Piece.Detective.YELLOW,
                Piece.Detective.WHITE
        };

        ImmutableList.Builder<Player> detectives = ImmutableList.builder();
        for (int i = 0; i < detectiveLocations.length; i++) {
            detectives.add(new Player(pieces[i], detectiveTickets, detectiveLocations[i]));
        }

        return factory.build(setup, mrX, detectives.build());
    }

    private Board createDefaultBoard(int mrXLocation, int turns, int... detectiveLocations) throws IOException {
        List<Boolean> moveList = Collections.nCopies(turns, false);
        return createBoard(mrXLocation, ImmutableList.copyOf(moveList),
                ScotlandYard.defaultMrXTickets(), ScotlandYard.defaultDetectiveTickets(),
                detectiveLocations);
    }

    //Tests for state creation from board
    @Test
    public void testStateFromBoardInitializesCorrectly() throws IOException {
        Board board = createDefaultBoard(35, 10, 26, 67, 94);
        FastGameState state = FastGameState.stateFromBoard(board, moveStore);

        assertThat(state.isMrXTurn()).isTrue();
        assertThat(state.getWinner()).isNull();
        assertThat(state.getMoves()).isGreaterThan(0);
        assertThat(StateUtils.getPlayerLocation(state.getMrX())).isEqualTo(34);
        assertThat(state.getDetectives()).hasSize(3);
        assertThat(StateUtils.getPlayerLocation(state.getDetectives()[0])).isEqualTo(25);
    }

    //Tests for win conditions
    @Test
    public void testDetectiveCatchesMrXOnCreation() {
        int mrX = StateUtils.buildMrX(34, 4, 3, 2, 1, 1);
        int detective = StateUtils.buildDetective(34, 11, 8, 4);
        FastGameState state = new FastGameState(mrX, new int[]{detective}, (byte) 5, moveStore, true, false);

        assertThat(state.getWinner()).isEqualTo(Team.DETECTIVES);
        assertThat(state.advanceOrNull(0)).isNull();
    }

    @Test
    public void testMrXWinsWhenZeroTurnsRemaining() {
        int mrX = StateUtils.buildMrX(34, 4, 3, 2, 1, 1);
        int detective = StateUtils.buildDetective(84, 11, 8, 4);
        FastGameState state = new FastGameState(mrX, new int[]{detective}, (byte) 0, moveStore, true, false);

        assertThat(state.getWinner()).isEqualTo(Team.MRX);
        assertThat(state.getMoves()).isEqualTo(0);
    }

    @Test
    public void testMrXWinsWhenDetectivesCannotMove() {
        int mrX = StateUtils.buildMrX(34, 4, 3, 2, 1, 1);
        // Detective with no tickets has 0 moves
        int detective = StateUtils.buildDetective(84, 0, 0, 0);
        FastGameState state = new FastGameState(mrX, new int[]{detective}, (byte) 5, moveStore, false, false);

        assertThat(state.getWinner()).isEqualTo(Team.MRX);
        assertThat(state.getMoves()).isEqualTo(0);
    }

    //Tests for state advancement
    @Test
    public void testAdvanceMrXSingleMove() throws IOException {
        Board board = createDefaultBoard(35, 5, 85, 120);
        FastGameState state = FastGameState.stateFromBoard(board, moveStore);

        // Even index represents single move when double move is available
        FastGameState next = state.advanceOrNull(0);
        assertThat(next).isNotNull();
        assertThat(next.isMrXTurn()).isFalse();
    }

    @Test
    public void testAdvanceThrowsOnOutOfBoundsIndex() throws IOException {
        Board board = createDefaultBoard(35, 5, 85, 120);
        FastGameState state = FastGameState.stateFromBoard(board, moveStore);

        assertThatThrownBy(() -> state.advanceOrNull(-1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> state.advanceOrNull(state.getMoves()))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    public void testAdvanceMrXIntoDetectiveReturnsNull() throws IOException {
        // Node 35 connects to node 48 by taxi. Place detective on 48.
        Board board = createDefaultBoard(35, 5, 48, 120);
        FastGameState state = FastGameState.stateFromBoard(board, moveStore);

        // Find move that moves MrX into node 48 (location 47 in 0-based)
        boolean hit = false;
        for (int i = 0; i < state.getMoves(); i++) {
            int targetMrX = moveStore.applyMrXMove(state.getMrX(), i / 2);
            if (StateUtils.getPlayerLocation(targetMrX) == 47) {
                assertThat(state.advanceOrNull(i)).isNull();
                hit = true;
                break;
            }
        }
        assertThat(hit).isTrue();
    }

    //Tests for double move mechanics
    @Test
    public void testDoubleMoveOptionsWhenAvailable() throws IOException {
        Board boardWithDouble = createDefaultBoard(35, 5, 85, 120);
        FastGameState stateWithDouble = FastGameState.stateFromBoard(boardWithDouble, moveStore);

        // Without double ticket, move count is halved
        int mrXNoDouble = StateUtils.buildMrX(34, 4, 3, 2, 1, 0);
        int expectedBaseMoves = moveStore.getMrXMoves(mrXNoDouble);

        assertThat(stateWithDouble.getMoves()).isEqualTo(expectedBaseMoves * 2);

        // Odd move index triggers double move continuation
        FastGameState continuation = stateWithDouble.advanceOrNull(1);
        assertThat(continuation).isNotNull();
        assertThat(continuation.isMrXTurn()).isTrue();
        assertThat(StateUtils.playerHasTicket(continuation.getMrX(), ScotlandYard.Ticket.DOUBLE, 2)).isFalse();
    }

    //Tests for detectives turn
    @Test
    public void testDetectivesTurnAdvancesBackToMrX() throws IOException {
        Board board = createDefaultBoard(35, 5, 85, 120);
        FastGameState mrXState = FastGameState.stateFromBoard(board, moveStore);

        // Single move from MrX -> detectives turn
        FastGameState detectiveState = mrXState.advanceOrNull(0);
        assertThat(detectiveState).isNotNull();
        assertThat(detectiveState.isMrXTurn()).isFalse();

        // Any detective move returns state back to MrX turn
        FastGameState backToMrX = detectiveState.advanceOrNull(0);
        assertThat(backToMrX).isNotNull();
        assertThat(backToMrX.isMrXTurn()).isTrue();
    }
}

