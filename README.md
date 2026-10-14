# Scotland Yard AI Agent

An adversarial game-playing AI developed to play as **Mr. X** in the board game *Scotland Yard* ([official rules PDF](https://desktopgames.com.ua/games/199/scotlandyard_rules_en.pdf)), implemented in Java.

This project was developed in collaboration with [Yuma Bannerman](https://github.com/yyuuma) as part of the 1st-year Computer Science curriculum at the **University of Bristol**.

---

## Architecture Overview

The agent operates in a real-time, adversarial environment where Mr. X must evade up to 5 detectives across the London transit network (taxi, bus, underground, and river routes).

- **Iterative Deepening Minimax with $\alpha$-$\beta$ Pruning:**
  Game trees are explored using iterative deepening bounded by a dynamic time deadline per turn (`deadline = System.currentTimeMillis() + ...`). This guarantees the agent always returns the highest-quality move found even under strict timeout constraints.

- **Graph Precomputation (`PathfindingGraph`):**
  Uses the Floyd-Warshall algorithm at startup to precompute all-pairs shortest paths across the entire board graph. This allows the heuristic evaluation function to query exact transit distances between Mr. X and all detectives in $O(1)$ time without runtime pathfinding overhead.

- **Heuristic Evaluation:**
  Evaluates positions based on:
  - Safety margins to the nearest detectives (weighted penalisation for distances $\le 3$).
  - Node mobility and escape route availability (degree and transit diversity).
  - Ticket conservation (preserving double-move and secret tickets for critical escapes).

---

## Performance Optimisations

Because Minimax tree search depth is strictly throughput-bound, significant effort went into minimising memory allocations and cache misses:

1. **Bit-Packed Game State (`FastGameState` & `StateUtils`)**
   Standard object-oriented representations incur substantial heap allocations and GC pressure across millions of explored states. Instead, player states are packed into primitive 32-bit `int` values:
   - `bits 0–7`: Board location (0–255)
   - `bits 8–13`: Taxi tickets (6 bits)
   - `bits 14–19`: Bus tickets (6 bits)
   - `bits 20–25`: Underground tickets (6 bits)
   - `bits 26–28`: Secret tickets (3 bits)
   - `bits 29–31`: Double-move tickets (3 bits)

2. **Bitwise State Transitions**
   Ticket consumption and validation are computed directly through bitwise shifts and arithmetic (e.g. `player - (1 << 8)` to consume a taxi ticket), completely bypassing object creation during tree traversal.

3. **Move & Transition Caching (`MoveStore`)**
   Valid transit edges, detective combinations, and cartesian product move spaces are precomputed into lookup arrays at initialization rather than recalculated at each branch.

---

## Project Structure

```text
src/
├── main/java/uk/ac/bris/cs/scotlandyard/ui/ai/
│   ├── MiniMax.java           # Core search loop with iterative deepening & alpha-beta pruning
│   ├── FastGameState.java     # Lightweight, GC-friendly state container
│   ├── StateUtils.java        # Bit manipulation utilities for player states & tickets
│   ├── MoveStore.java         # Precomputed transition lookup tables
│   └── PathfindingGraph.java  # Graph metrics, distance matrices & Floyd-Warshall precomputation
└── test/java/                 # Comprehensive unit & performance benchmarks
```

---

## Attribution & Academic Context

- **Coursework Context:** University of Bristol, School of Computer Science.
- **Game Framework:** The game rules, GUI, test harness and provided tests are proprietary to the University of Bristol.
- **AI Implementation:** All search algorithms, bit-packed state representations, pathfinding utilities, heuristics, and custom unit tests were co-authored by **Thomas Webb** and **Yuma Bannerman**.
