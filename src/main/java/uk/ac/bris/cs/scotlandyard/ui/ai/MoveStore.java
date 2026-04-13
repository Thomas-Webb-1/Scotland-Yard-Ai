package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class MoveStore {

    short[] moveData;
    int[] layout;

    private int getBlockSize(int layout){
        return layout >>> 16;
    }
    private int getBlockOffset(int layout){
        return layout & 0xFFFF;
    }
    private int createLayoutBlock(int size, int offset){
        return (size << 16 | offset);
    }

    MoveStore(ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph) {
        //precompute possible moves and store them for fast lookups

        if (graph.nodes().size() != 199){
            throw new IllegalArgumentException(
                    "This class is only designed to work with the standard graph"
            );
        }

        layout = new int[199*4];
        int globalOffset = 0;
        moveData = new short[2000];

        for (int a = 0; a < 199; a++) {
            for (int key = 0; key < 4; key++) {
                int size = 0;
                for (int b = 0; b < 199; b++) {
                    var edges = graph.edgeValue(a + 1, b +1);
                    if (edges.isPresent()) {
                        var tickets = edges.orElseThrow().stream().map(e -> e.requiredTicket().ordinal()).toList();

                        if (key == 3) {
                            if (!tickets.isEmpty()) {
                                moveData[globalOffset + size] = (short) (b);
                                size++;
                            }
                        } else if (tickets.contains(key)) {
                            moveData[globalOffset + size] = (short) (b);
                            size++;
                        }
                    }
                }
                layout[a * 4 + key] = createLayoutBlock(size, globalOffset);
                globalOffset += size;
            }
        }

    }





    int getDetectiveMoves(int detective){
        int location = StateUtils.getPlayerLocation(detective);
        int result = StateUtils.playerHasTicket(detective, ScotlandYard.Ticket.TAXI) ? getBlockSize(layout[location*4]) : 0;
        result += StateUtils.playerHasTicket(detective, ScotlandYard.Ticket.BUS) ? getBlockSize(layout[location*4 + 1]) : 0;
        result += StateUtils.playerHasTicket(detective, ScotlandYard.Ticket.UNDERGROUND) ? getBlockSize(layout[location*4 + 2]) : 0;

        return result;
    }

    int getMrXMoves(int mrX) {
        int location = StateUtils.getPlayerLocation(mrX);
        int result = StateUtils.playerHasTicket(mrX, ScotlandYard.Ticket.TAXI) ? getBlockSize(layout[location*4]) : 0;
        result += StateUtils.playerHasTicket(mrX, ScotlandYard.Ticket.BUS) ? getBlockSize(layout[location*4 + 1]) : 0;
        result += StateUtils.playerHasTicket(mrX, ScotlandYard.Ticket.UNDERGROUND) ? getBlockSize(layout[location*4 + 2]) : 0;
        result += StateUtils.playerHasTicket(mrX, ScotlandYard.Ticket.SECRET) ? getBlockSize(layout[location*4 + 3]) : 0;
        return result;
    }

    int applyDetectiveMove(int detective, int move) {
        int location = StateUtils.getPlayerLocation(detective);

        ScotlandYard.Ticket[] ticketTypes = {
                ScotlandYard.Ticket.TAXI,
                ScotlandYard.Ticket.BUS,
                ScotlandYard.Ticket.UNDERGROUND
        };

        for (int key = 0; key < 3; key++) {
            ScotlandYard.Ticket ticket = ticketTypes[key];
            if (!StateUtils.playerHasTicket(detective, ticket)) continue;

            int block = layout[location * 4 + key];
            int size   = getBlockSize(block);
            int offset = getBlockOffset(block);

            if (move < size) {
                int destination = moveData[offset + move];
                int updated = StateUtils.setPlayerLocation(detective, destination);
                updated = StateUtils.playerUseTicket(updated, ticket);
                return updated;
            }
            move -= size;
        }

        System.out.println(move + " out of range of");

        throw new IllegalArgumentException("Move index out of range for detective");
    }


    int applyMrXMove(int mrX, int move) {


        int location = StateUtils.getPlayerLocation(mrX);
        ScotlandYard.Ticket[] ticketTypes = {
                ScotlandYard.Ticket.TAXI,
                ScotlandYard.Ticket.BUS,
                ScotlandYard.Ticket.UNDERGROUND,
                ScotlandYard.Ticket.SECRET
        };

        for (int key = 0; key < 4; key++) {
            ScotlandYard.Ticket ticket = ticketTypes[key];
            if (!StateUtils.playerHasTicket(mrX, ticket)) continue;

            int block = layout[location * 4 + key];
            int size   = getBlockSize(block);
            int offset = getBlockOffset(block);

            if (move < size) {
                int destination = moveData[offset + move];
                int updated = StateUtils.setPlayerLocation(mrX, destination);
                updated = StateUtils.playerUseTicket(updated, ticket);
                return updated;
            }
            move -= size;
        }

        throw new IllegalArgumentException("move index out of range for mrX");
    }



}