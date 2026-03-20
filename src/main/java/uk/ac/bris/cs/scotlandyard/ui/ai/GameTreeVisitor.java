package uk.ac.bris.cs.scotlandyard.ui.ai;

interface GameTreeVisitor<T> {
    T visit(GameTree node);
}