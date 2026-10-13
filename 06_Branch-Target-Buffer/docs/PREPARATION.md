# Task 6.1 Preparation

## Questions

**1. How does a higher associativity affect the performance of a cache?**

Higher associativity reduces conflict misses: a memory block can be placed in
any of the n ways of its set, so blocks that map to the same index no longer
necessarily evict each other. For a BTB this means two hot branches whose PCs
share the same index bits can both keep their entries. The costs are hardware
and latency: n tag comparators are needed per lookup, the way-select mux gets
wider and the replacement policy (e.g. LRU) needs extra state, which can
lengthen the critical path. Beyond a certain degree of associativity the
hit-rate gain becomes marginal while the cost keeps growing.

**2. What's the best initial state for the 2-bit predictor FSM? What effects
would different initial states have in regular program patterns (e.g., loops)?**

A *weak* state is the best initialization because a single misprediction can
then correct the prediction direction. Since branches in typical programs
(especially loop back-edges) are taken far more often than not, *weakTaken*
is the best static choice. In this design a new entry is initialized based on
the actual outcome that caused the allocation: *weakTaken* if the branch was
taken, *weakNotTaken* if it fell through — so the first observed behavior
seeds the predictor.

Effects in a loop (N iterations, back-edge taken N-1 times, not taken once):

- **strongNotTaken**: the back-edge mispredicts twice before the prediction
  flips to taken — the worst case for loops.
- **weakNotTaken**: one misprediction before flipping.
- **weakTaken**: predicts the back-edge correctly from the start; only the
  cold (allocation) execution and the final exit iteration mispredict.
- **strongTaken**: same loop behavior as weakTaken, but if the branch is
  actually biased not-taken it takes two mispredictions to recover.

## Implementation details

**1. Structure of registers with 8 sets and 2 ways.** `Mem`/`SyncReadMem`
only supply one read per port, but a set-associative lookup must read and
compare *both* ways of a set in the same cycle (and an update must read all
valid bits and the LRU state). The BTB therefore uses plain register vectors
(`Reg(Vec(8, Vec(2, ...)))`) for valid, tag, target and predictor state,
which allow arbitrary parallel access.

**2. How can the number of index bits be determined?** index bits =
log2(number of sets) = log2(8) = 3. Because RV32I instructions are 4-byte
aligned, PC[1:0] is always zero, so the index is taken from PC[4:2].

**3. How many tag bits does each entry need (8 sets, 2 ways)?** The remaining
PC bits above the index: 32 − 2 (alignment) − 3 (index) = **27 bits**
(PC[31:5]). The way count does not influence the tag width.

# Task 6.4 Performance Evaluation

Measured with the hardware counters `branchCount` / `mispredictCount`
exposed by the core (see `BTBPipeline_AccuracyTester`), running the
10-iteration counting loop `src/test/programs/btb/BinaryFile_loop10`
(BNE executes 10 times: 9 taken, 1 fall-through on exit):

| Scheme                     | Mispredictions | Accuracy |
|----------------------------|----------------|----------|
| BTB + 2-bit predictor      | 2 / 10         | 80%      |
| Static not-taken (task 5)  | 9 / 10         | 10%      |

The BTB only mispredicts the cold first execution (allocation) and the final
exit iteration; every predicted-taken hit redirects the fetch in the IF stage
with **zero** bubble cycles, whereas the static scheme pays a 2-cycle flush
for every taken branch (see `BTBPipeline_LoopTester` vs. the assignment 5
`RV32I_BranchLoopTester` for the cycle-exact comparison).

Note: when branches thrash the BTB the benefit disappears —
`BTBPipeline_LRUTester` over-commits one set with three hot branches and
degrades to 11/12 mispredictions, which is the motivation for higher
associativity (question 1 above).
