#!/usr/bin/env python3
"""
Color Zen - level generator.

Writes `app/src/main/assets/levels.json`: 120 levels, each with 3 independently
verified arrangements ("variants") that the in-game Shuffle button cycles
through, plus a `par` move count that drives the 1-3 star rating.

Why generated + solver-verified instead of hand authored?

  * Every shipped level is *proved* solvable before it reaches a device.
  * `par` is a near-optimal solution length, so star ratings are fair.
  * Levels are only accepted when the greedy solver resolves them in a few
    thousand nodes - the identical algorithm runs on-device for the Hint
    button, so a Hint always produces a real move (never a dead guess).

Search algorithm (greedy DFS over a canonical visited set) is mirrored exactly
in Kotlin at app/src/main/java/com/colorzen/puzzle/game/Solver.kt.
If you change one, change the other.

Level shape rules discovered while building this:
  With capacity 4 and only ONE spare bottle the reachable state space collapses
  (~60 states for 8 colours) and most random layouts are unsolvable - measured
  0/25 solvable at 6+ colours. Two spare bottles make every layout solvable
  (25/25) while keeping the puzzles deep, so paid tiers use two spares.
  Free tiers (1-30) keep the exact shapes from the product spec and are
  solvable by construction because we regenerate until the solver succeeds.

Usage:
    python3 tools/generate_levels.py            # regenerate assets/levels.json
    python3 tools/generate_levels.py --check    # verify the committed file
    python3 tools/generate_levels.py --report   # print difficulty table only
"""

from __future__ import annotations

import argparse
import json
import os
import random
import sys
from typing import Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

CAPACITY = 4        # liquid segments per bottle
VARIANTS = 3        # arrangements per level (index 0 is the starting layout)
TOTAL_LEVELS = 120
PAR_RESTARTS = 14   # randomised DFS restarts; shortest result is `par`

# Node budget the on-device hint solver is allowed. Generation accepts a level
# only when the solver succeeds well inside this budget, which is what makes
# Hint reliable on a phone.
NODE_BUDGET = 40_000

# Free starter levels keep the shapes from the product spec exactly.
# Paid levels use "7+ bottles, 6+ colours" with two spare bottles.
#
# (from_level, to_level, bottles, colours, tier, min_moves)
TIERS: List[Tuple[int, int, int, int, str, int]] = [
    (1, 10, 4, 3, "beginner", 5),    # 1 spare bottle
    (11, 20, 5, 4, "easy", 8),       # 1 spare bottle
    (21, 30, 6, 5, "medium", 12),    # 1 spare bottle
    (31, 45, 8, 6, "hard", 16),      # 2 spare bottles
    (46, 60, 9, 7, "hard", 20),      # 2 spare bottles
    (61, 75, 10, 8, "expert", 24),   # 2 spare bottles
    (76, 90, 11, 9, "expert", 28),   # 2 spare bottles
    (91, 105, 12, 10, "master", 32),  # 2 spare bottles
    (106, 120, 13, 11, "master", 36), # 2 spare bottles
]

State = Tuple[Tuple[int, ...], ...]
Move = Tuple[int, int, int]


def spec_for(level: int) -> Tuple[int, int, str, int]:
    for start, end, bottles, colours, tier, min_moves in TIERS:
        if start <= level <= end:
            return bottles, colours, tier, min_moves
    raise ValueError(f"level {level} outside 1..{TOTAL_LEVELS}")


# ---------------------------------------------------------------------------
# Rules
#
# A bottle is a tuple of colour ids, bottom -> top; a short tuple means the
# bottle has free space at the top.
#
#   * A pour moves the whole top run of one colour from A to B.
#   * B must be empty, or have the same colour on top.
#   * If B cannot hold the whole run, as many segments as fit are poured.
#   * Solved = every bottle is empty, or holds CAPACITY segments of one colour.
# ---------------------------------------------------------------------------


def top_run(bottle: Tuple[int, ...]) -> Tuple[int, int]:
    if not bottle:
        return (0, 0)
    colour = bottle[-1]
    count = 0
    for c in reversed(bottle):
        if c != colour:
            break
        count += 1
    return colour, count


def is_solved(state: State) -> bool:
    for bottle in state:
        if not bottle:
            continue
        if len(bottle) != CAPACITY or len(set(bottle)) != 1:
            return False
    return True


def apply_move(state: State, i: int, j: int, count: int) -> State:
    src, dst = state[i], state[j]
    count = min(count, CAPACITY - len(dst), len(src))
    if count <= 0:
        return state
    lst = list(state)
    lst[i] = src[: len(src) - count]
    lst[j] = dst + src[len(src) - count:]
    return tuple(lst)


def legal_moves(state: State, rng: Optional[random.Random] = None) -> List[Move]:
    """All legal pours, most promising first (ties broken randomly if rng)."""
    moves: List[Tuple[Move, Tuple[int, int, int], float]] = []
    n = len(state)
    for i in range(n):
        src = state[i]
        if not src:
            continue
        colour, run = top_run(src)
        src_is_pure = len(set(src)) == 1
        for j in range(n):
            if i == j:
                continue
            dst = state[j]
            free = CAPACITY - len(dst)
            if free <= 0:
                continue
            if dst:
                if dst[-1] != colour:
                    continue
            elif src_is_pure:
                # Moving a clean bottle into an empty one changes nothing.
                continue
            count = min(run, free)
            completes = 1 if len(dst) + count == CAPACITY and (not dst or dst[0] == colour) else 0
            empties = 1 if count == len(src) else 0
            onto_match = 1 if dst else 0
            key = (completes, empties + onto_match, count)
            jitter = rng.random() if rng is not None else 0.0
            moves.append(((i, j, count), key, jitter))

    moves.sort(key=lambda item: (item[1], item[2]), reverse=True)
    return [m for m, _, _ in moves]


def canonical(state: State) -> Tuple[Tuple[int, ...], ...]:
    """Bottles are interchangeable, so collapse permutations of the same layout."""
    return tuple(sorted(state))


def dfs_solve(state: State, budget: int = NODE_BUDGET,
              rng: Optional[random.Random] = None) -> Tuple[Optional[List[Move]], int]:
    """Greedy depth-first search. Returns (move list or None, nodes expanded)."""
    if is_solved(state):
        return [], 0

    seen = {canonical(state)}
    nodes = 0
    stack: List[Tuple[State, List[Move]]] = [(state, [])]

    while stack:
        current, path = stack.pop()
        children: List[Tuple[State, List[Move]]] = []
        for move in legal_moves(current, rng):
            nodes += 1
            if nodes > budget:
                return None, nodes
            i, j, count = move
            nxt = apply_move(current, i, j, count)
            key = canonical(nxt)
            if key in seen:
                continue
            seen.add(key)
            children.append((nxt, path + [move]))
        # Push worst-first so the best-scored child is popped next.
        for child in reversed(children):
            if is_solved(child[0]):
                return child[1], nodes
            stack.append(child)
    return None, nodes


def near_optimal_par(state: State) -> Tuple[int, int]:
    """Shortest solution over randomised restarts; also returns worst-case nodes."""
    best_len = 1 << 30
    worst_nodes = 0
    solution = dfs_solve(state)[0]
    if solution is None:
        return -1, 0
    best_len = len(solution)
    for attempt in range(PAR_RESTARTS):
        rng = random.Random(0x5EED + attempt * 7919)
        sol, nodes = dfs_solve(state, rng=rng)
        worst_nodes = max(worst_nodes, nodes)
        if sol is not None and len(sol) < best_len:
            best_len = len(sol)
    return best_len, worst_nodes


# ---------------------------------------------------------------------------
# Generation
# ---------------------------------------------------------------------------


def random_arrangement(bottles: int, colours: int, rng: random.Random) -> State:
    pool: List[int] = []
    for colour in range(1, colours + 1):
        pool.extend([colour] * CAPACITY)
    rng.shuffle(pool)
    state: List[Tuple[int, ...]] = []
    for b in range(bottles):
        if b < colours:
            state.append(tuple(pool[b * CAPACITY:(b + 1) * CAPACITY]))
        else:
            state.append(tuple())
    return tuple(state)


def has_pre_solved_bottle(state: State) -> bool:
    return any(len(b) == CAPACITY and len(set(b)) == 1 for b in state)


def flatten(state: State) -> List[int]:
    """Bottle-major, exactly CAPACITY ints per bottle, 0 = empty slot."""
    flat: List[int] = []
    for bottle in state:
        flat.extend(list(bottle))
        flat.extend([0] * (CAPACITY - len(bottle)))
    return flat


def parse_flat(flat: List[int], bottles: int) -> State:
    state: List[Tuple[int, ...]] = []
    for b in range(bottles):
        chunk = flat[b * CAPACITY:(b + 1) * CAPACITY]
        state.append(tuple(c for c in chunk if c != 0))
    return tuple(state)


def make_variant(level: int, bottles: int, colours: int, min_moves: int,
                 rng: random.Random, reject: set) -> Optional[dict]:
    for _ in range(2000):
        state = random_arrangement(bottles, colours, rng)
        if is_solved(state) or has_pre_solved_bottle(state):
            continue
        solution, _ = dfs_solve(state)
        if solution is None or len(solution) < min_moves:
            continue
        flat = flatten(state)
        key = tuple(flat)
        if key in reject:
            continue
        reject.add(key)
        par, _ = near_optimal_par(state)
        if par < min_moves:
            continue
        return {"flat": flat, "par": par, "greedy": len(solution)}
    return None


def generate_level(level: int, seed: int) -> Optional[dict]:
    bottles, colours, tier, min_moves = spec_for(level)
    rng = random.Random(seed)
    reject: set = set()
    variants: List[List[int]] = []
    pars: List[int] = []
    greedy: List[int] = []
    while len(variants) < VARIANTS:
        made = make_variant(level, bottles, colours, min_moves, rng, reject)
        if made is None:
            return None
        variants.append(made["flat"])
        pars.append(made["par"])
        greedy.append(made["greedy"])
    return {
        "n": level,
        "t": tier,
        "colors": colours,
        "bottles": bottles,
        "spares": bottles - colours,
        "par": pars[0],
        "variants": variants,
        "_diagnostics": {"pars": pars, "greedy": greedy},
    }


def generate_all(seed_base: int = 20260916) -> dict:
    levels: List[dict] = []
    for level in range(1, TOTAL_LEVELS + 1):
        data = None
        for offset in (0, 10_000, 20_000, 40_000, 80_000, 160_000):
            data = generate_level(level, seed_base + level * 7919 + offset)
            if data is not None:
                break
        if data is None:
            raise RuntimeError(f"could not generate level {level}")
        levels.append(data)
        if level % 10 == 0 or level == TOTAL_LEVELS:
            print(f"  level {level:3d}/{TOTAL_LEVELS}  par {data['par']:3d}  "
                  f"bottles {data['bottles']:2d}  colours {data['colors']:2d}", flush=True)
    return {"version": 1, "capacity": CAPACITY, "levels": levels}


# ---------------------------------------------------------------------------
# Verification
# ---------------------------------------------------------------------------


def check(document: dict) -> int:
    problems = 0
    levels = document["levels"]
    if len(levels) != TOTAL_LEVELS:
        print(f"expected {TOTAL_LEVELS} levels, found {len(levels)}")
        problems += 1
    print(f"checking {len(levels)} levels, capacity {document['capacity']}, "
          f"{VARIANTS} variants each")
    worst = 0
    for entry in levels:
        bottles, colours = entry["bottles"], entry["colors"]
        expected_len = bottles * CAPACITY
        for vi, flat in enumerate(entry["variants"]):
            label = f"L{entry['n']:3d} v{vi}"
            if len(flat) != expected_len:
                print(f"  {label}: wrong length {len(flat)} != {expected_len}")
                problems += 1
                continue
            state = parse_flat(flat, bottles)
            counts: Dict[int, int] = {}
            for bottle in state:
                for c in bottle:
                    counts[c] = counts.get(c, 0) + 1
            if len(counts) != colours or any(v != CAPACITY for v in counts.values()):
                print(f"  {label}: bad colour multiset {counts}")
                problems += 1
                continue
            if has_pre_solved_bottle(state):
                print(f"  {label}: starts with an already-finished bottle")
                problems += 1
            solution, nodes = dfs_solve(state)
            worst = max(worst, nodes)
            if solution is None:
                print(f"  {label}: NOT SOLVABLE within {NODE_BUDGET} nodes")
                problems += 1
            elif vi == 0 and len(solution) > entry["par"] * 2:
                print(f"  {label}: greedy {len(solution)} vs par {entry['par']} - suspicious")
    print(f"worst-case solver nodes across all levels/variants: {worst} (budget {NODE_BUDGET})")
    if problems == 0:
        print("OK - every level and every variant is solvable")
    else:
        print(f"{problems} problem(s) found")
    return problems


def report(document: dict) -> None:
    rows: Dict[str, List[dict]] = {}
    for entry in document["levels"]:
        rows.setdefault(entry["t"], []).append(entry)
    print(f"{'tier':9s} {'levels':>12s} {'bottles':>9s} {'colours':>9s} {'spares':>7s} {'par':>9s}")
    for tier, entries in rows.items():
        lo, hi = entries[0]["n"], entries[-1]["n"]
        pars = [e["par"] for e in entries]
        bottles = sorted({e["bottles"] for e in entries})
        colours = sorted({e["colors"] for e in entries})
        spares = sorted({e["spares"] for e in entries})
        span = lambda v: str(v[0]) if len(v) == 1 else f"{v[0]}-{v[-1]}"
        print(f"{tier:9s} {lo:5d}-{hi:<6d} {span(bottles):>9s} {span(colours):>9s} "
              f"{span(spares):>7s} {min(pars):4d}-{max(pars):<4d}")



def main() -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    out_path = os.path.normpath(os.path.join(here, "..", "app", "src", "main", "assets", "levels.json"))

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify the existing levels.json")
    parser.add_argument("--report", action="store_true", help="print the difficulty table")
    parser.add_argument("--seed", type=int, default=20260916)
    args = parser.parse_args()

    if args.check or args.report:
        with open(out_path, encoding="utf-8") as fh:
            document = json.load(fh)
        if args.report:
            report(document)
            return 0
        return 1 if check(document) else 0

    print("generating Color Zen levels ...")
    document = generate_all(args.seed)
    report(document)

    diagnostics = {str(e["n"]): e.pop("_diagnostics") for e in document["levels"]}
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(document, fh, separators=(",", ":"))
    print(f"wrote {out_path} ({os.path.getsize(out_path)/1024:.1f} KB)")

    diag_path = os.path.normpath(os.path.join(here, "..", "docs", "levels-diagnostics.json"))
    os.makedirs(os.path.dirname(diag_path), exist_ok=True)
    with open(diag_path, "w", encoding="utf-8") as fh:
        json.dump({"seed": args.seed, "budget": NODE_BUDGET, "levels": diagnostics}, fh, indent=1)
    print(f"wrote {diag_path}")

    print("\nre-verifying the generated file ...")
    with open(out_path, encoding="utf-8") as fh:
        return 1 if check(json.load(fh)) else 0


if __name__ == "__main__":
    sys.exit(main())
