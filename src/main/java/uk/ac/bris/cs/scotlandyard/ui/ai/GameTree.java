package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

import java.util.ArrayList;
import java.util.List;

class GameTree {
    private final Board.GameState state;
    private List<GameTree> children;

    GameTree(Board.GameState state) {
        this.state = state;
        this.children = new ArrayList<>();
    }
    Board.GameState getState(){return state;};

    List<GameTree> getChildren(){
        //Lazy load child nodes
        if (children.isEmpty() && state.getWinner().isEmpty()){
            children = state.getAvailableMoves().stream().map(
                    move->new GameTree(state.advance(move))
            ).toList();
        }
        return children;
    }
    <T> T accept(GameTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
