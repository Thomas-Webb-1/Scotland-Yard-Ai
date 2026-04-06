package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.Move;

public interface GameTreeStrategy    {
    abstract Move pick(GameTree tree);
}
