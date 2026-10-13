// ADS I Class Project
// Pipelined RISC-V Core - Branch Target Buffer
//
// Chair of Electronic Design Automation, RPTU in Kaiserslautern
// File created on 05/12/2026 by Tobias Jauch (@tojauch)

/*
Branch Target Buffer (BTB): a hardware component that predicts the target address of conditional branch instructions to improve pipeline performance

Functionality (cf. slide 6-48 of the lecture slides):
    Stores target addresses and prediction information for conditional branch instructions
    On a branch instruction, checks if the instruction is in the BTB and retrieves the predicted target address and prediction state
    If the prediction is taken, the processor fetches the instruction from the predicted target address; if not taken, it continues sequentially
    Updates the BTB entry based on the actual outcome of the branch instruction (taken or not taken) and updates the prediction state accordingly

Inputs:
    PC: A 32-bit program counter representing the address of the branch instruction being fetched or executed.
    update: A 1-bit signal indicating whether the BTB should be updated with new information.
    updatePC: A 32-bit program counter associated with the branch instruction being updated.
    updateTarget: A 32-bit branch target address to be stored in the BTB.
    mispredicted: A 1-bit signal indicating whether the prediction turned out to be incorrect during execution (used to update the predictor).

Outputs:
    valid: A 1-bit signal indicating whether the BTB has a valid prediction for the provided program counter.
    target: A 32-bit signal representing the predicted branch target address when a valid prediction exists.
    predictTaken: A 1-bit signal indicating whether the branch is predicted to be taken or not.

*/

package core_tile

import chisel3._
import chisel3.util._

// 2-bit predictor FSM states (encoding from lecture slide 6-47).
// The MSB is the prediction: 1 = taken, 0 = not taken.
object BTBState {
  val strongNotTaken = "b00".U(2.W)
  val weakNotTaken = "b01".U(2.W)
  val strongTaken = "b10".U(2.W)
  val weakTaken = "b11".U(2.W)
}

// -----------------------------------------
// Branch Target Buffer
// -----------------------------------------

class BTB extends Module {
  val io = IO(new Bundle {
    val PC = Input(UInt(32.W))
    val update = Input(Bool())
    val updatePC = Input(UInt(32.W))
    val updateTarget = Input(UInt(32.W))
    val mispredicted = Input(Bool())

    val valid = Output(Bool())
    val target = Output(UInt(32.W))
    val predictTaken = Output(Bool())
  })

  val numSets = 8
  val numWays = 2

  // PC[1:0] is always zero (4-byte alignment), PC[4:2] selects one of the 8
  // sets, the remaining PC[31:5] bits form the tag (27 bits).
  def indexOf(pc: UInt): UInt = pc(4, 2)
  def tagOf(pc: UInt): UInt = pc(31, 5)

  // FSM transitions (slide 6-47): a correct prediction moves to the strong
  // state of the current prediction, a wrong prediction has to occur twice
  // (strong -> weak -> opposite) before the prediction flips.
  def nextState(state: UInt, wrong: Bool): UInt = {
    val onWrong = MuxLookup(
      state,
      state,
      Seq(
        BTBState.strongTaken -> BTBState.weakTaken,
        BTBState.weakTaken -> BTBState.strongNotTaken,
        BTBState.strongNotTaken -> BTBState.weakNotTaken,
        BTBState.weakNotTaken -> BTBState.strongTaken
      )
    )
    // Correct: keep the prediction (MSB) and saturate to the strong state.
    val onCorrect = Cat(state(1), 0.U(1.W))
    Mux(wrong, onWrong, onCorrect)
  }

  // Standard memory classes (Mem/SyncReadMem) only allow a single read per
  // port, but a set-associative lookup must compare all ways of a set at
  // once, so the storage is built from plain register vectors instead.
  val validBits = RegInit(
    VecInit(Seq.fill(numSets)(VecInit(Seq.fill(numWays)(false.B))))
  )
  val tags = Reg(Vec(numSets, Vec(numWays, UInt(27.W))))
  val targets = Reg(Vec(numSets, Vec(numWays, UInt(32.W))))
  val states = Reg(Vec(numSets, Vec(numWays, UInt(2.W))))
  // One LRU bit per set, holding the index of the least recently used way
  // (the eviction candidate when the set is full).
  val lru = RegInit(VecInit(Seq.fill(numSets)(0.U(1.W))))

  // --- Lookup (combinational) ---
  val idx = indexOf(io.PC)
  val tag = tagOf(io.PC)
  val hitWays = VecInit(
    (0 until numWays).map(w => validBits(idx)(w) && tags(idx)(w) === tag)
  )
  val hit = hitWays.asUInt.orR
  val hitWay = Mux(hitWays(0), 0.U(1.W), 1.U(1.W))

  io.valid := hit
  io.target := targets(idx)(hitWay)
  io.predictTaken := states(idx)(hitWay)(1)

  // A lookup hit counts as a use of the entry for the LRU policy.
  when(hit) {
    lru(idx) := ~hitWay
  }

  // --- Update (synchronous) ---
  // The update arrives two cycles after the lookup (branch resolution in EX),
  // so the entry is re-selected by tag match on updatePC: an existing entry
  // is updated in place, otherwise a free way is allocated, and only if the
  // set is full the LRU way is evicted.
  val uIdx = indexOf(io.updatePC)
  val uTag = tagOf(io.updatePC)
  val uHitWays = VecInit(
    (0 until numWays).map(w => validBits(uIdx)(w) && tags(uIdx)(w) === uTag)
  )
  val uHit = uHitWays.asUInt.orR
  val uHitWay = Mux(uHitWays(0), 0.U(1.W), 1.U(1.W))
  val hasFreeWay = !validBits(uIdx)(0) || !validBits(uIdx)(1)
  val freeWay = Mux(!validBits(uIdx)(0), 0.U(1.W), 1.U(1.W))
  val updateWay = Mux(uHit, uHitWay, Mux(hasFreeWay, freeWay, lru(uIdx)))

  when(io.update) {
    validBits(uIdx)(updateWay) := true.B
    tags(uIdx)(updateWay) := uTag
    targets(uIdx)(updateWay) := io.updateTarget
    when(uHit) {
      states(uIdx)(updateWay) := nextState(states(uIdx)(updateWay), io.mispredicted)
    }.otherwise {
      // New entry: without a BTB entry the implicit prediction was "not
      // taken", so a misprediction means the branch was actually taken.
      // Initialize to the matching weak state.
      states(uIdx)(updateWay) := Mux(
        io.mispredicted,
        BTBState.weakTaken,
        BTBState.weakNotTaken
      )
    }
    // The updated entry becomes the most recently used one. This write wins
    // over a simultaneous lookup hit on the same set.
    lru(uIdx) := ~updateWay
  }
}
