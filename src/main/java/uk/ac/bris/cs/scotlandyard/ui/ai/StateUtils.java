package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard;

import java.util.List;

public class StateUtils {
    //Utility functions for FastGamestate creation and mutation
    public static boolean playerHasTicket(int player, ScotlandYard.Ticket ticket, int n) {
        switch (ticket) {
            case TAXI -> {
                return ((player >> 8) & 0x3F) >= n;
            }
            case BUS -> {
                return ((player >> 14) & 0x3F) >= n;
            }
            case UNDERGROUND -> {
                return ((player >> 20) & 0x3F) >= n;
            }
            case SECRET -> {
                return ((player >> 26) & 0x07) >= n;
            }
            case DOUBLE -> {
                return ((player >> 29) & 0x07) >= n;
            }
            default -> {
                return false;
            }
        }
    }

    public static boolean playerHasTicket(int player, ScotlandYard.Ticket ticket) {
        return playerHasTicket(player, ticket, 1);
    }

    public static int playerUseTicket(int player, ScotlandYard.Ticket ticket) {
        switch (ticket) {
            case TAXI -> {
                return player - (1 << 8);
            }
            case BUS -> {
                return player - (1 << 14);
            }
            case UNDERGROUND -> {
                return player - (1 << 20);
            }
            case SECRET -> {
                return player - (1 << 26);
            }
            case DOUBLE -> {
                return player - (1 << 29);
            }
            default -> {
                return player;
            }
        }
    }

    public static int setPlayerLocation(int player, int location) {
        return (player & ~0xFF) | (location & 0xFF);
    }

    public static int getPlayerLocation(int player) {
        return player & 0xFF;
    }

    public static int setPlayerMoves(int player, int moves) {
        return (player & ~(0x3F << 26)) | ((moves & 0x3F) << 26);
    }

    public static int getPlayerMoves(int player) {
        return (player >> 26) & 0x3F;
    }

    public static int buildDetective(int location, int taxi, int bus, int underground) {
        //detectives use 8 bits for location, 6 for each type of ticket and 8 for number of moves
        return (location & 0xFF)
                | ((taxi & 0x3F) << 8)
                | ((bus & 0x3F) << 14)
                | ((underground & 0x3F) << 20);
    }

    public static int buildMrX(int location, int taxi, int bus, int underground, int secret, int doubleMove) {
        //mrX uses 8 bits for location, 6 for taxi bus and underground tickets, and 4 for both secret and double tickets
        return (location & 0xFF)
                | ((taxi & 0x3F) << 8)
                | ((bus & 0x3F) << 14)
                | ((underground & 0x3F) << 20)
                | ((secret & 0x07) << 26)
                | ((doubleMove & 0x07) << 29);
    }


    public static byte setWinner(byte flags, Team t) {
        //clear winner flags
        flags &= ~0x0C;

        //set flag for given team
        if (t == Team.MRX) {
            return flags |= 0x04;
        } else if (t == Team.DETECTIVES) {
            return flags |= 0x08;
        } else return flags;
    }

    public static Team getWinner(byte flags) {
        if ((flags & 0x04) != 0) return Team.MRX;
        if ((flags & 0x08) != 0) return Team.DETECTIVES;
        return null;
    }

    public static boolean isMrXTurn(byte flags) {
        return (flags & 0x01) != 0;
    }


    static Move mrXMoveToMove(int mrXBefore, int mrXAfter1, int mrXAfter2) {
        //convert 3 mrX's from FastGameState to a move from the scotland yard model;
        int source = StateUtils.getPlayerLocation(mrXBefore);
        int dest1 = StateUtils.getPlayerLocation(mrXAfter1);

        ScotlandYard.Ticket ticket1 = whichTicketWasUsed(mrXBefore, mrXAfter1);

        //if no 3rd mrx is provided Return a single move
        if (mrXAfter2 == -1) {
            return new Move.SingleMove(Piece.MrX.MRX, source + 1, ticket1, dest1 + 1);
        }

        int dest2 = StateUtils.getPlayerLocation(mrXAfter2);
        ScotlandYard.Ticket ticket2 = whichTicketWasUsed(mrXAfter1, mrXAfter2);

        return new Move.DoubleMove(Piece.MrX.MRX, source + 1, ticket1, dest1 + 1, ticket2, dest2 + 1);
    }

    static ScotlandYard.Ticket whichTicketWasUsed(int before, int after) {
        int taxiBefore = (before >> 8) & 0x3F;
        int busBefore = (before >> 14) & 0x3F;
        int undergroundBefore = (before >> 20) & 0x3F;
        int secretBefore = (before >> 26) & 0x07;

        int taxiAfter = (after >> 8) & 0x3F;
        int busAfter = (after >> 14) & 0x3F;
        int undergroundAfter = (after >> 20) & 0x3F;
        int secretAfter = (after >> 26) & 0x07;

        if (taxiAfter < taxiBefore) return ScotlandYard.Ticket.TAXI;
        if (busAfter < busBefore) return ScotlandYard.Ticket.BUS;
        if (undergroundAfter < undergroundBefore) return ScotlandYard.Ticket.UNDERGROUND;
        if (secretAfter < secretBefore) return ScotlandYard.Ticket.SECRET;

        throw new IllegalArgumentException("no ticket count decreased between states");
    }

    static FastGameState stateFromBoard(Board board, MoveStore moveStore) {
        //utility function to convert a board to a gamestate with a precomputed move store

        var mrXTickets = board.getPlayerTickets(Piece.MrX.MRX).orElseThrow();
        int mrXLocation = board.getAvailableMoves().stream().findFirst().orElseThrow().source() - 1;
        int mrX = StateUtils.buildMrX(mrXLocation, mrXTickets.getCount(ScotlandYard.Ticket.TAXI), mrXTickets.getCount(ScotlandYard.Ticket.BUS), mrXTickets.getCount(ScotlandYard.Ticket.UNDERGROUND),
                mrXTickets.getCount(ScotlandYard.Ticket.SECRET),
                mrXTickets.getCount(ScotlandYard.Ticket.DOUBLE));

        List<Piece> boardDetectives = board.getPlayers().stream().filter(Piece::isDetective).toList();
        int[] detectives = new int[boardDetectives.size()];
        for (int i = 0; i < boardDetectives.size(); i++) {
            Piece d = boardDetectives.get(i);
            var tickets = board.getPlayerTickets(d).orElseThrow();
            int location = board.getDetectiveLocation((Piece.Detective) d).orElseThrow() - 1;
            detectives[i] = StateUtils.buildDetective(location,
                    tickets.getCount(ScotlandYard.Ticket.TAXI),
                    tickets.getCount(ScotlandYard.Ticket.BUS),
                    tickets.getCount(ScotlandYard.Ticket.UNDERGROUND));
        }

        int remainingMoves = board.getSetup().moves.size() - board.getMrXTravelLog().size();
        return new FastGameState(mrX, detectives, (byte) remainingMoves, moveStore, true, false);
    }


}
