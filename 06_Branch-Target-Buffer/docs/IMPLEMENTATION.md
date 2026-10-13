# Assignment 06 — Implementation Overview

This document describes the Branch Target Buffer (BTB) implementation, how it
is integrated into the 5-stage pipeline, the test structure, and how to run
the tests. See [PREPARATION.md](PREPARATION.md) for the Task 6.1 answers and
the Task 6.4 performance evaluation results.

## BTB module (`src/main/scala/BTB.scala`)

A 2-way set-associative branch target buffer with 8 sets.

**Address split** (RV32I instructions are 4-byte aligned, so PC[1:0] = 00):

```
 31                    5   4   2   1  0
+------------------------+-------+-----+
|        tag (27 bit)    | index | 0 0 |
+------------------------+-------+-----+
                          PC[4:2] selects one of 8 sets
```

**Each entry** holds a valid bit, the 27-bit tag, the 32-bit branch target
address, and a 2-bit predictor state. The storage is built from plain
register vectors (`Reg(Vec(8, Vec(2, ...)))`) instead of `Mem`/`SyncReadMem`,
because a set-associative lookup must read and tag-compare *both* ways of a
set in the same cycle.

**Lookup** is combinational: the set selected by `PC[4:2]` is checked for a
valid entry with a matching tag. On a hit, `valid` is asserted with the
stored `target` and `predictTaken` (the MSB of the predictor state). On a
miss, `valid` is false and the pipeline falls back to the implicit
not-taken/sequential prediction.

**2-bit predictor FSM** (encoding from lecture slide 6-47):

| State          | Encoding | Predicts  | on correct       | on wrong         |
|----------------|----------|-----------|------------------|------------------|
| strongTaken    | `10`     | taken     | strongTaken      | weakTaken        |
| weakTaken      | `11`     | taken     | strongTaken      | strongNotTaken   |
| strongNotTaken | `00`     | not taken | strongNotTaken   | weakNotTaken     |
| weakNotTaken   | `01`     | not taken | strongNotTaken   | strongTaken      |

A prediction must be wrong twice before it flips (strong -> weak ->
opposite).

**Update** (synchronous, driven by EX two cycles after the lookup): the entry
is re-selected by tag-matching `updatePC`, so the correct entry is updated
even though lookup and update are cycles apart.

- *Existing entry*: target is rewritten and the FSM advances using
  `mispredicted` (correct/wrong).
- *New entry*: allocated into a free way, or — if the set is full — into the
  LRU way (eviction). The FSM is initialized from the observed outcome:
  `weakTaken` if the branch was taken (i.e. the implicit not-taken prediction
  was wrong), `weakNotTaken` otherwise.

**LRU replacement**: one bit per set pointing at the least recently used way.
Both a lookup hit and an update count as a "use" and make the touched way
most-recently-used; a simultaneous update wins over a lookup on the same set.

## Pipeline integration

```
        PC (IF) ------------------> +-----+
                                    | BTB |
  valid/target/predictTaken <------ +-----+
        (IF)                           ^
                                       | update / updatePC /
                                       | updateTarget / mispredicted
                                      (EX)
```

- **IF** (`IFstage.scala`): looks up the current fetch PC. On
  `valid && predictTaken` the *next* fetch comes from the predicted target
  (zero bubbles for a correct taken prediction); otherwise PC + 4. A redirect
  from EX overrides everything. IF also outputs `predictedTaken` for the
  fetched instruction.
- **IF/ID barriers + ID** (`IFbarrier.scala`, `IDstage.scala`,
  `IDbarrier.scala`): carry the `predictedTaken` bit alongside the
  instruction down to EX (cleared on flush).
- **EX** (`EXstage.scala`): resolves the branch condition and computes the
  real target. A conditional branch is *mispredicted* iff
  `branchTaken =/= predictedTaken`. On a misprediction EX redirects the fetch
  (to the real target if taken, to the fall-through PC if not) and flushes
  the two wrong-path instructions. For every resolved conditional branch EX
  drives the BTB update interface (`btbUpdate`, `btbUpdatePC`,
  `btbUpdateTarget`, `btbMispredicted`). Jumps (`jal`/`jalr`) never consult
  or update the BTB; they always redirect as taken.
- **core** (`core.scala`): instantiates and wires the BTB, and counts
  resolved branches / mispredictions into the `branchCount` /
  `mispredictCount` outputs (used for the Task 6.4 accuracy measurement).

**Switching prediction schemes (Task 6.3 modularity):** the core and the
`PipelinedRV32I` wrapper take a `useBTB: Boolean` parameter.

- `useBTB = false` (**default**): no BTB is instantiated, the prediction
  inputs of IF are tied off, and EX's mispredict logic reduces to "redirect
  on every taken branch" — exactly the static not-taken scheme from
  assignment 5. The unchanged assignment 5 testbench runs against this
  configuration.
- `useBTB = true`: dynamic BTB-based prediction. Used by the new BTB test
  suites and by Verilog generation (`MakeVerilog.scala`).

## Test structure

| Suite (file) | Level | Configuration | Programs |
|---|---|---|---|
| `PipelinedRISCV32ITest` (`PipelinedRISCV32I_tb.scala`) | pipeline | static (default) | `src/test/programs/`, `.../branches/` |
| `BTBTest` (`BTB_tb.scala`) | BTB module | — | none (direct I/O pokes) |
| `BTBPipelineTest` (`BTBPipeline_tb.scala`) | pipeline | `useBTB = true` | `src/test/programs/btb/` |

- **`PipelinedRISCV32ITest`** — the assignment 5 regression suite, carried
  over byte-identical (ALU, hazards/forwarding, branch semantics, flushes,
  jumps, exceptions). It must keep passing unchanged.

- **`BTBTest`** — unit tests on the BTB module, covering the four Task 6.5
  requirements directly:
  - `BTB_ResetTester` / `BTB_PredictionTester`: no valid prediction after
    reset; correct target/prediction for PCs with valid entries, no false
    hits for same-set or same-tag neighbors.
  - `BTB_UpdateTester`: updates rewrite an existing entry in place without
    disturbing its set neighbor.
  - `BTB_PredictorFSMTester`: walks the full FSM and shows the
    "wrong-twice-to-flip" hysteresis in both directions.
  - `BTB_LRUTester`: eviction picks the least recently used way; lookups and
    updates both refresh the LRU state.

- **`BTBPipelineTest`** — system tests of the integrated BTB, with dedicated
  hand-assembled programs (each `BinaryFile_*` has a commented `*_dump`):
  - `BTBPipeline_LoopTester` (`BinaryFile_loop3`): cycle-exact loop; the cold
    and exit executions mispredict (2 bubbles each), the predicted-taken
    iteration runs with zero bubbles.
  - `BTBPipeline_LRUTester` (`BinaryFile_lru`): three branches placed at
    0x0C/0x2C/0x4C collide in BTB set 3 and thrash the 2 ways via LRU while
    the program runs; asserts exact counter values (12 branches / 11
    mispredicts) and that flushed wrong-path writes never retire.
  - `BTBPipeline_AccuracyTester` (`BinaryFile_loop10`): Task 6.4 evaluation —
    runs the same 10-iteration loop with `useBTB = true` and `false`, reads
    the `branchCount`/`mispredictCount` counters and compares: BTB 2/10
    mispredicted (80% accuracy) vs. static 9/10 (10% accuracy).

## Running the tests

From the `06_Branch-Target-Buffer` directory:

```bash
# all test suites (sbt >= 2.0 runs incrementally; use testFull to force all)
sbt test
sbt testFull

# a single suite
sbt "testOnly PipelinedRV32I_Tester.BTBTest"
sbt "testOnly PipelinedRV32I_Tester.BTBPipelineTest"
sbt "testOnly PipelinedRV32I_Tester.PipelinedRISCV32ITest"
```

Expected: 16 tests, all passing. The accuracy tester prints the measured
accuracies:

```
BTB:    2/10 mispredicted, accuracy 80.0%
static: 9/10 mispredicted, accuracy 10.0%
```

Waveforms are written to `test_run_dir/<test name>/PipelinedRV32I.vcd`
(viewable with GTKWave or https://app.surfer-project.org/).

Verilog generation (BTB-enabled core, output in `generated-src/`):

```bash
sbt run
```
