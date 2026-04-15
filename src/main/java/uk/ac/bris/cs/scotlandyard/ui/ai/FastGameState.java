package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard;

import java.util.List;

public class FastGameState {
    private final int mrX;
    private final int[] detectives;
    private int moves;
    private final byte turns;
    private byte flags;
    private final MoveStore store;

    private FastGameState(int mrX, int[] detectives, byte turns, MoveStore store, boolean mrXTurn,boolean doubleMoveContinuation) {
        this.mrX = mrX;
        this.detectives = detectives;
        this.turns = turns;
        this.store = store;
        this.flags = 0;

        for (int d : detectives){
            if (StateUtils.getPlayerLocation(d) == StateUtils.getPlayerLocation(mrX)){
                setWinner(Team.DETECTIVES);
                return;
            }
        }

        if (mrXTurn) {
            if (turns == 0) {
                setWinner(Team.MRX);
                this.moves = 0;
                return;
            }
            flags |= 0x01;// mrX turn flag
            if (doubleMoveContinuation){
                flags |= 0x02;//state is a continuation of a double moves
            }
            this.moves = store.getMrXMoves(mrX) * (!doubleMoveContinuation && StateUtils.playerHasTicket(mrX, ScotlandYard.Ticket.DOUBLE) ? 2 : 1);
        } else {
            int crossProduct = 1;
            boolean anyCanMove = false;
            for (int i = 0; i < detectives.length; i++) {
                int d = detectives[i];
                int m = store.getDetectiveMoves(d);
                if (m > 0) {
                    anyCanMove = true;
                    d = StateUtils.setPlayerMoves(d, m);
                    detectives[i] = d;
                    crossProduct *= m;
                }else {
                    detectives[i] = StateUtils.setPlayerMoves(d, 0);
                }
            }
            if (!anyCanMove) {
                setWinner(Team.MRX);
                this.moves = 0;
                return;
            }
            this.moves = crossProduct;
        }
    }

    public FastGameState advanceOrNull(int move) {
        if (getWinner() != null) return null;
        if (move >= moves || move < 0) throw new IndexOutOfBoundsException();

        if (isMrXTurn()) {//MrX turn
            boolean isDoubleAvailable = (flags & 0x02) == 0 && StateUtils.playerHasTicket(mrX, ScotlandYard.Ticket.DOUBLE);
            int newMrX = store.applyMrXMove(mrX, isDoubleAvailable ? move/2 : move);
            for (int d : detectives){
                if (StateUtils.getPlayerLocation(d) == StateUtils.getPlayerLocation(newMrX)) return null;
            }
            if (isDoubleAvailable && (move % 2) == 1){
                return new FastGameState(
                         StateUtils.playerUseTicket(newMrX, ScotlandYard.Ticket.DOUBLE),
                        detectives.clone(),
                        (byte) (turns - 1),
                        store,
                        true,
                        true
                );
            } else {
                return new FastGameState(
                        newMrX,
                        detectives.clone(),
                        (byte) (turns - 1),
                        store,
                        false,
                        false
                );

            }

        } else {//Detectives turn
            int[] newDetectives = detectives.clone();
            int newMrX = mrX;
            int remaining = move;

            for (int i = 0; i < newDetectives.length; i++) {
                int detectiveMoves = StateUtils.getPlayerMoves(detectives[i]);
                if (detectiveMoves == 0) continue;

                int applied = store.applyDetectiveMove(newDetectives[i], remaining % detectiveMoves);
                ScotlandYard.Ticket ticketUsed = StateUtils.whichTicketWasUsed(newDetectives[i],applied);
                newMrX = StateUtils.playerUseTicket(newMrX,ticketUsed);

                int dest = StateUtils.getPlayerLocation(applied);
                for (int j = 0; j < i; j++) {
                    if (StateUtils.getPlayerLocation(newDetectives[j]) == dest) return null;
                }
                newDetectives[i] = applied;
                remaining /= detectiveMoves;
            }

            return new FastGameState(
                    newMrX,
                    newDetectives,
                    turns,
                    store,
                    true,
                    false
            );
        }
    }
    public boolean isMrXTurn() {
        return StateUtils.isMrXTurn(flags);
    }

    public int getMoves() {
        return moves;
    }

    public int getMrX() {
        return mrX;
    }

    public int[] getDetectives(){
        return detectives;
    }

    private void setWinner(Team t) {
        flags = StateUtils.setWinner(flags, t);
    }

    public Team getWinner() {
        return StateUtils.getWinner(flags);
    }

    public static FastGameState stateFromBoard(Board board){
        return FastGameState.stateFromBoard(board, new MoveStore(board.getSetup().graph));
    }

    public static FastGameState stateFromBoard(Board board, MoveStore moveStore) {
        //utility function to convert a board to a FastGameState with a precomputed move store

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
