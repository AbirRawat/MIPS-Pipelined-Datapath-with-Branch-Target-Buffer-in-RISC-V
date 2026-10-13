// ADS I Class Project
// Pipelined RISC-V Core - BTB-enabled Pipeline Testbench
//
// Chair of Electronic Design Automation, RPTU in Kaiserslautern

/*
System-level tests for the pipeline with the BTB-based dynamic prediction
scheme enabled (useBTB = true). The module-level BTB behavior (predictions,
updates, LRU, FSM) is covered by BTB_tb.scala; these tests verify the
integration into the core and the Task 6.4 performance evaluation:

    - BTBPipeline_LoopTester: cycle-exact loop showing that a correctly
      predicted taken branch redirects the fetch with zero bubble cycles,
      while cold and exit executions roll back correctly.
    - BTBPipeline_LRUTester: three same-set branches thrash the 2-way set
      through the LRU policy inside a running program; wrong-path
      instructions must never retire.
    - BTBPipeline_AccuracyTester: measures prediction accuracy with the
      branchCount/mispredictCount counters and compares the BTB against the
      static not-taken scheme on the same program.

All programs used here live in src/test/programs/btb/ and are independent of
the assignment 5 test programs.
*/

package PipelinedRV32I_Tester

import chisel3._
import chiseltest._
import PipelinedRV32I._
import org.scalatest.flatspec.AnyFlatSpec

class BTBPipelineTest extends AnyFlatSpec with ChiselScalatestTester {

  // ==========================================================================
  // Loop with dynamic prediction (program: btb/BinaryFile_loop3):
  //   x1 counts 3 -> 0 while x2 accumulates to 3.
  //   Iteration 1: BTB miss -> implicit not-taken prediction, branch taken ->
  //     misprediction with 2 flush bubbles; entry allocated as weakTaken.
  //   Iteration 2: BTB hit, predicted taken -> the loop body follows the
  //     branch with ZERO bubbles (this is the BTB win).
  //   Iteration 3: predicted taken (strongTaken) but the loop exits ->
  //     misprediction, 2 flush bubbles, resume at the fall-through path.
  // ==========================================================================
  "BTBPipeline_LoopTester" should "work" in {
    test(new PipelinedRV32I("src/test/programs/btb/BinaryFile_loop3", useBTB = true))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        // --- Fill pipeline (5 cycles) ---
        dut.clock.step(5)

        // ================================================================
        // Section 1: Setup
        // ================================================================
        dut.io.result.expect(0.U) // ADDI x2, x0, 0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(3.U) // ADDI x1, x0, 3
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 2: Iteration 1 (BTB miss -> mispredict, 2 bubbles)
        // ================================================================
        dut.io.result.expect(1.U) // ADDI x2, x2, 1 -> x2 = 1
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(2.U) // ADDI x1, x1, -1 -> x1 = 2
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        // BNE x1, x0, -8 taken; no BTB entry yet -> mispredicted
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 3: Iteration 2 (BTB hit, predicted taken -> NO bubbles)
        // ================================================================
        dut.io.result.expect(2.U) // ADDI x2, x2, 1 -> x2 = 2
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(1.U) // ADDI x1, x1, -1 -> x1 = 1
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        // BNE x1, x0, -8 taken; predicted taken by the BTB -> no flush
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 4: Iteration 3 follows immediately (no flush bubbles!),
        //   loop exits -> predicted taken but NOT taken -> 2 bubbles
        // ================================================================
        dut.io.result.expect(3.U) // ADDI x2, x2, 1 -> x2 = 3 (right after BNE)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // ADDI x1, x1, -1 -> x1 = 0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        // BNE x1, x0, -8 NOT taken; predicted taken -> mispredicted
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(3.U) // ADDI x9, x2, 0 -> x9 = 3 (accumulator)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
      }
  }

  // ==========================================================================
  // LRU thrashing in a running program (program: btb/BinaryFile_lru):
  //   Branches A/B/C at 0x0C/0x2C/0x4C all map to BTB set 3 but carry
  //   different tags, so three entries compete for two ways and the LRU
  //   policy evicts on every allocation. Executed 3 times by an outer loop
  //   (back-edge in set 7):
  //     - A/B/C are always evicted before their next execution -> all
  //       9 executions miss and mispredict (predicted not-taken, taken)
  //     - the conflict-free back-edge behaves like a normal loop branch:
  //       cold miss + exit misprediction -> 2 of 3 wrong
  //   => branchCount = 12, mispredictCount = 11. The wrong-path
  //   "ADDI x6, x6, 100" after each taken branch must never retire, so any
  //   retired result >= 100 is a rollback failure; x9 must read back 9.
  // ==========================================================================
  "BTBPipeline_LRUTester" should "work" in {
    test(new PipelinedRV32I("src/test/programs/btb/BinaryFile_lru", useBTB = true))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        var sawFinal = false
        for (_ <- 0 until 200) {
          val retired = dut.io.result.peek().litValue
          assert(retired < 100, "wrong-path instruction retired (flush failed)")
          if (retired == 9) { sawFinal = true }
          dut.io.exception.expect(false.B)
          dut.clock.step(1)
        }
        assert(sawFinal, "program must retire x9 = 9")
        dut.io.branchCount.expect(12.U)
        dut.io.mispredictCount.expect(11.U)
      }
  }

  // ==========================================================================
  // Prediction Accuracy (Task 6.4, program: btb/BinaryFile_loop10):
  //   a 10-iteration counting loop, BNE executes 10 times (9 taken + 1
  //   fall-through on exit), measured once with the BTB and once with the
  //   static not-taken scheme:
  //     BTB:    mispredicts only the first (allocation) and last (exit)
  //             execution -> 2/10 wrong, 80% accuracy
  //     static: mispredicts every taken execution -> 9/10 wrong, 10% accuracy
  // ==========================================================================
  "BTBPipeline_AccuracyTester" should "show improved prediction accuracy" in {
    def measure(useBTB: Boolean): (BigInt, BigInt) = {
      var branches: BigInt = 0
      var mispredicts: BigInt = 0
      var sawResult = false
      test(new PipelinedRV32I("src/test/programs/btb/BinaryFile_loop10", useBTB)) { dut =>
        dut.clock.setTimeout(0)
        // 10 iterations x 3 instructions + flushes fit comfortably in 150
        // cycles; scan the retire stream for the final x9 = 10 readback.
        for (_ <- 0 until 150) {
          if (dut.io.result.peek().litValue == 10) { sawResult = true }
          dut.io.exception.expect(false.B)
          dut.clock.step(1)
        }
        branches = dut.io.branchCount.peek().litValue
        mispredicts = dut.io.mispredictCount.peek().litValue
      }
      assert(sawResult, "program must retire x9 = 10 (loop executed correctly)")
      (branches, mispredicts)
    }

    val (btbBranches, btbWrong) = measure(useBTB = true)
    val (statBranches, statWrong) = measure(useBTB = false)

    def accuracy(total: BigInt, wrong: BigInt): Double =
      100.0 * (total - wrong).toDouble / total.toDouble

    println(f"BTB:    $btbWrong/$btbBranches mispredicted, accuracy ${accuracy(btbBranches, btbWrong)}%.1f%%")
    println(f"static: $statWrong/$statBranches mispredicted, accuracy ${accuracy(statBranches, statWrong)}%.1f%%")

    // Both runs execute the same 10 conditional branches
    assert(btbBranches == 10 && statBranches == 10)
    // BTB: only the first (cold) and last (loop exit) BNE mispredict
    assert(btbWrong == 2)
    // Static not-taken: all 9 taken executions mispredict
    assert(statWrong == 9)
  }

}
