package uk.ac.bris.cs.scotlandyard.ui.ai;

import org.junit.Test;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestStateUtils {


    @Test
    public void testBuildDetectivePacksFieldsCorrectly() {
        int location = 42;
        int taxi = 11;
        int bus = 8;
        int underground = 4;

        int detective = StateUtils.buildDetective(location, taxi, bus, underground);

        assertThat(StateUtils.getPlayerLocation(detective)).isEqualTo(location);
        assertThat(StateUtils.playerHasTicket(detective, ScotlandYard.Ticket.TAXI, taxi)).isTrue();
        assertThat(StateUtils.playerHasTicket(detective, ScotlandYard.Ticket.BUS, bus)).isTrue();
        assertThat(StateUtils.playerHasTicket(detective, ScotlandYard.Ticket.UNDERGROUND, underground)).isTrue();

        // detective must not have secret or double tickets
        assertThat(StateUtils.playerHasTicket(detective, ScotlandYard.Ticket.SECRET)).isFalse();
        assertThat(StateUtils.playerHasTicket(detective, ScotlandYard.Ticket.DOUBLE)).isFalse();
    }

    @Test
    public void testDetectiveMovesBitPacking() {
        int detective = StateUtils.buildDetective(55, 10, 5, 2);

        // Initially moves are 0
        assertThat(StateUtils.getPlayerMoves(detective)).isEqualTo(0);

        // Set moves to a non-zero value (6 bits: 0 to 63)
        int updated = StateUtils.setPlayerMoves(detective, 17);
        assertThat(StateUtils.getPlayerMoves(updated)).isEqualTo(17);

        // Verify other fields remain untouched
        assertThat(StateUtils.getPlayerLocation(updated)).isEqualTo(55);
        assertThat(StateUtils.playerHasTicket(updated, ScotlandYard.Ticket.TAXI, 10)).isTrue();
        assertThat(StateUtils.playerHasTicket(updated, ScotlandYard.Ticket.BUS, 5)).isTrue();
        assertThat(StateUtils.playerHasTicket(updated, ScotlandYard.Ticket.UNDERGROUND, 2)).isTrue();

        // Test max moves (63) and reset back to 0
        int maxMoves = StateUtils.setPlayerMoves(updated, 63);
        assertThat(StateUtils.getPlayerMoves(maxMoves)).isEqualTo(63);

        int clearedMoves = StateUtils.setPlayerMoves(maxMoves, 0);
        assertThat(StateUtils.getPlayerMoves(clearedMoves)).isEqualTo(0);
        assertThat(StateUtils.getPlayerLocation(clearedMoves)).isEqualTo(55);
    }

    //MisterX bit packing tests
    @Test
    public void testBuildMrXPacksAllFieldsCorrectly() {
        int location = 105;
        int taxi = 4;
        int bus = 3;
        int underground = 3;
        int secret = 5;
        int doubleMove = 2;

        int mrX = StateUtils.buildMrX(location, taxi, bus, underground, secret, doubleMove);

        assertThat(StateUtils.getPlayerLocation(mrX)).isEqualTo(location);
        assertThat(StateUtils.playerHasTicket(mrX, ScotlandYard.Ticket.TAXI, taxi)).isTrue();
        assertThat(StateUtils.playerHasTicket(mrX, ScotlandYard.Ticket.BUS, bus)).isTrue();
        assertThat(StateUtils.playerHasTicket(mrX, ScotlandYard.Ticket.UNDERGROUND, underground)).isTrue();
        assertThat(StateUtils.playerHasTicket(mrX, ScotlandYard.Ticket.SECRET, secret)).isTrue();
        assertThat(StateUtils.playerHasTicket(mrX, ScotlandYard.Ticket.DOUBLE, doubleMove)).isTrue();
    }

    @Test
    public void testBuildMrXBoundaryValues() {
        // Max values:
        // location: 8 bits (255)
        // taxi, bus, underground: 6 bits each (63)
        // secret, doubleMove: 3 bits each (7)
        int maxMrX = StateUtils.buildMrX(255, 63, 63, 63, 7, 7);

        assertThat(StateUtils.getPlayerLocation(maxMrX)).isEqualTo(255);
        assertThat(StateUtils.playerHasTicket(maxMrX, ScotlandYard.Ticket.TAXI, 63)).isTrue();
        assertThat(StateUtils.playerHasTicket(maxMrX, ScotlandYard.Ticket.BUS, 63)).isTrue();
        assertThat(StateUtils.playerHasTicket(maxMrX, ScotlandYard.Ticket.UNDERGROUND, 63)).isTrue();
        assertThat(StateUtils.playerHasTicket(maxMrX, ScotlandYard.Ticket.SECRET, 7)).isTrue();
        assertThat(StateUtils.playerHasTicket(maxMrX, ScotlandYard.Ticket.DOUBLE, 7)).isTrue();

        // Zero values
        int zeroMrX = StateUtils.buildMrX(0, 0, 0, 0, 0, 0);

        assertThat(StateUtils.getPlayerLocation(zeroMrX)).isEqualTo(0);
        assertThat(StateUtils.playerHasTicket(zeroMrX, ScotlandYard.Ticket.TAXI)).isFalse();
        assertThat(StateUtils.playerHasTicket(zeroMrX, ScotlandYard.Ticket.BUS)).isFalse();
        assertThat(StateUtils.playerHasTicket(zeroMrX, ScotlandYard.Ticket.UNDERGROUND)).isFalse();
        assertThat(StateUtils.playerHasTicket(zeroMrX, ScotlandYard.Ticket.SECRET)).isFalse();
        assertThat(StateUtils.playerHasTicket(zeroMrX, ScotlandYard.Ticket.DOUBLE)).isFalse();
    }

    //Tests for location utils
    @Test
    public void testSetAndGetPlayerLocation() {
        int mrX = StateUtils.buildMrX(10, 4, 3, 2, 5, 2);
        assertThat(StateUtils.getPlayerLocation(mrX)).isEqualTo(10);

        int moved = StateUtils.setPlayerLocation(mrX, 85);
        assertThat(StateUtils.getPlayerLocation(moved)).isEqualTo(85);

        // Ensure ticket counts were not altered by moving
        assertThat(StateUtils.playerHasTicket(moved, ScotlandYard.Ticket.TAXI, 4)).isTrue();
        assertThat(StateUtils.playerHasTicket(moved, ScotlandYard.Ticket.BUS, 3)).isTrue();
        assertThat(StateUtils.playerHasTicket(moved, ScotlandYard.Ticket.UNDERGROUND, 2)).isTrue();
        assertThat(StateUtils.playerHasTicket(moved, ScotlandYard.Ticket.SECRET, 5)).isTrue();
        assertThat(StateUtils.playerHasTicket(moved, ScotlandYard.Ticket.DOUBLE, 2)).isTrue();

        // Location 0 and max 8-bit location 255
        int locZero = StateUtils.setPlayerLocation(moved, 0);
        assertThat(StateUtils.getPlayerLocation(locZero)).isEqualTo(0);

        int locMax = StateUtils.setPlayerLocation(moved, 255);
        assertThat(StateUtils.getPlayerLocation(locMax)).isEqualTo(255);
    }

    //tests for ticket utils

    @Test
    public void testPlayerHasTicketSingleArg() {
        int player = StateUtils.buildMrX(1, 1, 0, 2, 0, 1);

        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.TAXI)).isTrue();
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.BUS)).isFalse();
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.UNDERGROUND)).isTrue();
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.SECRET)).isFalse();
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.DOUBLE)).isTrue();
    }

    @Test
    public void testPlayerHasTicketThreshold() {
        int player = StateUtils.buildMrX(1, 5, 3, 2, 2, 1);

        // Taxi count = 5
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.TAXI, 0)).isTrue();
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.TAXI, 4)).isTrue();
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.TAXI, 5)).isTrue();
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.TAXI, 6)).isFalse();

        // Secret count = 2
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.SECRET, 2)).isTrue();
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.SECRET, 3)).isFalse();

        // Double count = 1
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.DOUBLE, 1)).isTrue();
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.DOUBLE, 2)).isFalse();
    }

    

    @Test
    public void testPlayerUseTicketTaxi() {
        int player = StateUtils.buildMrX(20, 5, 3, 2, 1, 1);
        int after = StateUtils.playerUseTicket(player, ScotlandYard.Ticket.TAXI);

        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.TAXI, 5)).isFalse();
        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.TAXI, 4)).isTrue();
        // Other tickets and location must remain intact
        assertThat(StateUtils.getPlayerLocation(after)).isEqualTo(20);
        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.BUS, 3)).isTrue();
        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.UNDERGROUND, 2)).isTrue();
        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.SECRET, 1)).isTrue();
        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.DOUBLE, 1)).isTrue();
    }

    @Test
    public void testPlayerUseTicketBus() {
        int player = StateUtils.buildMrX(20, 5, 3, 2, 1, 1);
        int after = StateUtils.playerUseTicket(player, ScotlandYard.Ticket.BUS);

        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.BUS, 3)).isFalse();
        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.BUS, 2)).isTrue();
        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.TAXI, 5)).isTrue();
    }

    @Test
    public void testPlayerUseTicketUnderground() {
        int player = StateUtils.buildMrX(20, 5, 3, 2, 1, 1);
        int after = StateUtils.playerUseTicket(player, ScotlandYard.Ticket.UNDERGROUND);

        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.UNDERGROUND, 2)).isFalse();
        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.UNDERGROUND, 1)).isTrue();
        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.BUS, 3)).isTrue();
    }

    @Test
    public void testPlayerUseTicketSecret() {
        int player = StateUtils.buildMrX(20, 5, 3, 2, 2, 1);
        int after = StateUtils.playerUseTicket(player, ScotlandYard.Ticket.SECRET);

        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.SECRET, 2)).isFalse();
        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.SECRET, 1)).isTrue();
    }

    @Test
    public void testPlayerUseTicketDouble() {
        int player = StateUtils.buildMrX(20, 5, 3, 2, 1, 2);
        int after = StateUtils.playerUseTicket(player, ScotlandYard.Ticket.DOUBLE);

        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.DOUBLE, 2)).isFalse();
        assertThat(StateUtils.playerHasTicket(after, ScotlandYard.Ticket.DOUBLE, 1)).isTrue();
    }

    @Test
    public void testPlayerUseTicketConsecutive() {
        int player = StateUtils.buildMrX(15, 2, 0, 0, 0, 0);

        player = StateUtils.playerUseTicket(player, ScotlandYard.Ticket.TAXI);
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.TAXI, 1)).isTrue();

        player = StateUtils.playerUseTicket(player, ScotlandYard.Ticket.TAXI);
        assertThat(StateUtils.playerHasTicket(player, ScotlandYard.Ticket.TAXI, 1)).isFalse();
    }

    //Test state flags

    @Test
    public void testInitialFlagsHaveNoWinner() {
        assertThat(StateUtils.getWinner((byte) 0)).isNull();
    }

    @Test
    public void testSetWinnerMrX() {
        byte flags = 0;
        byte mrXWon = StateUtils.setWinner(flags, Team.MRX);

        assertThat(StateUtils.getWinner(mrXWon)).isEqualTo(Team.MRX);
        assertThat((mrXWon & 0x04) != 0).isTrue();
    }

    @Test
    public void testSetWinnerDetectives() {
        byte flags = 0;
        byte detectivesWon = StateUtils.setWinner(flags, Team.DETECTIVES);

        assertThat(StateUtils.getWinner(detectivesWon)).isEqualTo(Team.DETECTIVES);
        assertThat((detectivesWon & 0x08) != 0).isTrue();
    }

    @Test
    public void testSetWinnerOverwrite() {
        byte flags = 0;
        // Set to MRX
        flags = StateUtils.setWinner(flags, Team.MRX);
        assertThat(StateUtils.getWinner(flags)).isEqualTo(Team.MRX);

        // Overwrite with DETECTIVES
        flags = StateUtils.setWinner(flags, Team.DETECTIVES);
        assertThat(StateUtils.getWinner(flags)).isEqualTo(Team.DETECTIVES);

        // Clear winner by passing null
        flags = StateUtils.setWinner(flags, null);
        assertThat(StateUtils.getWinner(flags)).isNull();
    }

    @Test
    public void testSetWinnerPreservesOtherFlags() {
        // bit 0: mrX turn (0x01)
        // bit 1: double move continuation (0x02)
        byte initialFlags = 0x03;

        byte withWinner = StateUtils.setWinner(initialFlags, Team.MRX);
        assertThat(StateUtils.getWinner(withWinner)).isEqualTo(Team.MRX);
        // Ensure bits 0 and 1 were not cleared
        assertThat(StateUtils.isMrXTurn(withWinner)).isTrue();
        assertThat((withWinner & 0x02) != 0).isTrue();
    }

 

    @Test
    public void testIsMrXTurn() {
        assertThat(StateUtils.isMrXTurn((byte) 0x00)).isFalse();
        assertThat(StateUtils.isMrXTurn((byte) 0x01)).isTrue();
        assertThat(StateUtils.isMrXTurn((byte) 0x04)).isFalse(); // MrX won bit set, but not turn bit
        assertThat(StateUtils.isMrXTurn((byte) 0x05)).isTrue();  // MrX won bit + turn bit set
        assertThat(StateUtils.isMrXTurn((byte) 0xFE)).isFalse();
        assertThat(StateUtils.isMrXTurn((byte) 0xFF)).isTrue();
    }

    //Test for determining which ticket was moved when reconstructing final state

    @Test
    public void testWhichTicketWasUsedTaxi() {
        int before = StateUtils.buildMrX(10, 4, 3, 2, 1, 1);
        int after = StateUtils.playerUseTicket(before, ScotlandYard.Ticket.TAXI);

        assertThat(StateUtils.whichTicketWasUsed(before, after)).isEqualTo(ScotlandYard.Ticket.TAXI);
    }

    @Test
    public void testWhichTicketWasUsedBus() {
        int before = StateUtils.buildMrX(10, 4, 3, 2, 1, 1);
        int after = StateUtils.playerUseTicket(before, ScotlandYard.Ticket.BUS);

        assertThat(StateUtils.whichTicketWasUsed(before, after)).isEqualTo(ScotlandYard.Ticket.BUS);
    }

    @Test
    public void testWhichTicketWasUsedUnderground() {
        int before = StateUtils.buildMrX(10, 4, 3, 2, 1, 1);
        int after = StateUtils.playerUseTicket(before, ScotlandYard.Ticket.UNDERGROUND);

        assertThat(StateUtils.whichTicketWasUsed(before, after)).isEqualTo(ScotlandYard.Ticket.UNDERGROUND);
    }

    @Test
    public void testWhichTicketWasUsedSecret() {
        int before = StateUtils.buildMrX(10, 4, 3, 2, 2, 1);
        int after = StateUtils.playerUseTicket(before, ScotlandYard.Ticket.SECRET);

        assertThat(StateUtils.whichTicketWasUsed(before, after)).isEqualTo(ScotlandYard.Ticket.SECRET);
    }

    @Test
    public void testWhichTicketWasUsedThrowsWhenNoTicketDecreased() {
        int before = StateUtils.buildMrX(10, 4, 3, 2, 1, 1);
        int after = before; // Identical state

        assertThatThrownBy(() -> StateUtils.whichTicketWasUsed(before, after))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no ticket count decreased");
    }

    @Test
    public void testWhichTicketWasUsedThrowsWhenOnlyLocationChanged() {
        int before = StateUtils.buildMrX(10, 4, 3, 2, 1, 1);
        int after = StateUtils.setPlayerLocation(before, 50);

        assertThatThrownBy(() -> StateUtils.whichTicketWasUsed(before, after))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no ticket count decreased");
    }


    @Test
    public void testMrXMoveToMoveSingleMove() {
        // FastGameState stores 0-based locations, ScotlandYard Move uses 1-based locations
        int source0 = 34; // 1-based: 35
        int dest0 = 44;   // 1-based: 45

        int before = StateUtils.buildMrX(source0, 4, 3, 2, 1, 1);
        int after = StateUtils.setPlayerLocation(
                StateUtils.playerUseTicket(before, ScotlandYard.Ticket.TAXI),
                dest0
        );

        Move move = StateUtils.mrXMoveToMove(before, after, -1);

        assertThat(move).isInstanceOf(Move.SingleMove.class);
        Move.SingleMove singleMove = (Move.SingleMove) move;

        assertThat(singleMove.commencedBy()).isEqualTo(Piece.MrX.MRX);
        assertThat(singleMove.source()).isEqualTo(35);
        assertThat(singleMove.ticket).isEqualTo(ScotlandYard.Ticket.TAXI);
        assertThat(singleMove.destination).isEqualTo(45);
    }

    @Test
    public void testMrXMoveToMoveDoubleMove() {
        int source0 = 10; // 1-based: 11
        int dest1_0 = 20; // 1-based: 21
        int dest2_0 = 30; // 1-based: 31

        int mrX0 = StateUtils.buildMrX(source0, 4, 3, 2, 1, 2);

        // First leg: Underground to 20
        int mrX1 = StateUtils.setPlayerLocation(
                StateUtils.playerUseTicket(mrX0, ScotlandYard.Ticket.UNDERGROUND),
                dest1_0
        );

        // Second leg: Bus to 30
        int mrX2 = StateUtils.setPlayerLocation(
                StateUtils.playerUseTicket(mrX1, ScotlandYard.Ticket.BUS),
                dest2_0
        );

        Move move = StateUtils.mrXMoveToMove(mrX0, mrX1, mrX2);

        assertThat(move).isInstanceOf(Move.DoubleMove.class);
        Move.DoubleMove doubleMove = (Move.DoubleMove) move;

        assertThat(doubleMove.commencedBy()).isEqualTo(Piece.MrX.MRX);
        assertThat(doubleMove.source()).isEqualTo(11);
        assertThat(doubleMove.ticket1).isEqualTo(ScotlandYard.Ticket.UNDERGROUND);
        assertThat(doubleMove.destination1).isEqualTo(21);
        assertThat(doubleMove.ticket2).isEqualTo(ScotlandYard.Ticket.BUS);
        assertThat(doubleMove.destination2).isEqualTo(31);
    }

    @Test
    public void testMrXMoveToMoveDoubleMoveWithSecret() {
        int mrX0 = StateUtils.buildMrX(5, 4, 3, 2, 2, 1);

        int mrX1 = StateUtils.setPlayerLocation(
                StateUtils.playerUseTicket(mrX0, ScotlandYard.Ticket.SECRET),
                12
        );

        int mrX2 = StateUtils.setPlayerLocation(
                StateUtils.playerUseTicket(mrX1, ScotlandYard.Ticket.TAXI),
                25
        );

        Move move = StateUtils.mrXMoveToMove(mrX0, mrX1, mrX2);

        assertThat(move).isInstanceOf(Move.DoubleMove.class);
        Move.DoubleMove doubleMove = (Move.DoubleMove) move;

        assertThat(doubleMove.ticket1).isEqualTo(ScotlandYard.Ticket.SECRET);
        assertThat(doubleMove.destination1).isEqualTo(13);
        assertThat(doubleMove.ticket2).isEqualTo(ScotlandYard.Ticket.TAXI);
        assertThat(doubleMove.destination2).isEqualTo(26);
    }
}

