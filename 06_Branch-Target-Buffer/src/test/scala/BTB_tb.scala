// ADS I Class Project
// Pipelined RISC-V Core - Branch Target Buffer Testbench
//
// Chair of Electronic Design Automation, RPTU in Kaiserslautern

/*
Unit tests for the 2-way set-associative BTB (8 sets), covering the Task 6.5
requirements:
    - correct predictions for program counters with valid entries
    - proper handling of updates
    - LRU-based eviction when a set is full
    - 2-bit predictor FSM state transitions ("wrong twice before the
      prediction changes")

The BTB indexes with PC[4:2] and tags with PC[31:5], so two PCs share a set
iff bits [4:2] are equal, and a lookup only hits for the exact branch PC.
*/

package PipelinedRV32I_Tester

import chisel3._
import chiseltest._
import core_tile._
import org.scalatest.flatspec.AnyFlatSpec

class BTBTest extends AnyFlatSpec with ChiselScalatestTester {

  // Applies one update (as EX would after resolving a branch) and returns to
  // idle. NEUTRAL_PC keeps the lookup port away from any stored entry so the
  // lookup side does not disturb the LRU state during the update.
  val NEUTRAL_PC = "hFFFFFFC".U

  def applyUpdate(dut: BTB, pc: UInt, target: UInt, mispredicted: Boolean): Unit = {
    dut.io.PC.poke(NEUTRAL_PC)
    dut.io.update.poke(true.B)
    dut.io.updatePC.poke(pc)
    dut.io.updateTarget.poke(target)
    dut.io.mispredicted.poke(mispredicted.B)
    dut.clock.step(1)
    dut.io.update.poke(false.B)
  }

  // Reads an entry (combinational lookup) without advancing the clock, so
  // the check itself does not touch the LRU state.
  def expectEntry(dut: BTB, pc: UInt, target: UInt, predictTaken: Boolean): Unit = {
    dut.io.PC.poke(pc)
    dut.io.valid.expect(true.B)
    dut.io.target.expect(target)
    dut.io.predictTaken.expect(predictTaken.B)
  }

  def expectMiss(dut: BTB, pc: UInt): Unit = {
    dut.io.PC.poke(pc)
    dut.io.valid.expect(false.B)
  }

  // Performs a lookup for one full cycle, marking the entry as most recently
  // used (as a fetch of that PC would).
  def touch(dut: BTB, pc: UInt): Unit = {
    dut.io.PC.poke(pc)
    dut.clock.step(1)
    dut.io.PC.poke(NEUTRAL_PC)
  }

  // ==========================================================================
  // Reset: no PC has a valid prediction before any update
  // ==========================================================================
  "BTB_ResetTester" should "work" in {
    test(new BTB) { dut =>
      expectMiss(dut, 0x00.U)
      expectMiss(dut, 0x40.U)
      expectMiss(dut, "h7FFFFFC".U)
    }
  }

  // ==========================================================================
  // Valid entries: an inserted branch is predicted with the stored target,
  // and neither a set neighbor nor a tag neighbor produces a false hit
  // ==========================================================================
  "BTB_PredictionTester" should "work" in {
    test(new BTB) { dut =>
      // Taken branch at 0x40 (set 0, tag 2) with target 0x100
      applyUpdate(dut, 0x40.U, 0x100.U, mispredicted = true)

      // New taken entry starts in weakTaken -> predict taken with target
      expectEntry(dut, 0x40.U, 0x100.U, predictTaken = true)
      // Same tag bits, different set -> miss
      expectMiss(dut, 0x44.U)
      // Same set (0), different tag -> miss
      expectMiss(dut, 0x20.U)

      // A not-taken branch gets an entry too, initialized to weakNotTaken
      applyUpdate(dut, 0x24.U, 0x200.U, mispredicted = false)
      expectEntry(dut, 0x24.U, 0x200.U, predictTaken = false)
    }
  }

  // ==========================================================================
  // Updates: an update to an existing entry rewrites the stored target and
  // leaves other entries in the same set untouched
  // ==========================================================================
  "BTB_UpdateTester" should "work" in {
    test(new BTB) { dut =>
      // Two entries in set 1 (PC bits [4:2] = 001)
      applyUpdate(dut, 0x04.U, 0x100.U, mispredicted = true)
      applyUpdate(dut, 0x24.U, 0x200.U, mispredicted = true)

      // Update the first entry with a new target (correct prediction)
      applyUpdate(dut, 0x04.U, 0x300.U, mispredicted = false)

      // Entry updated in place, neighbor unchanged, no eviction happened
      expectEntry(dut, 0x04.U, 0x300.U, predictTaken = true)
      expectEntry(dut, 0x24.U, 0x200.U, predictTaken = true)
    }
  }

  // ==========================================================================
  // 2-bit predictor FSM: a prediction must be wrong twice before it flips
  //   weakTaken -(correct)-> strongTaken -(wrong)-> weakTaken
  //   -(wrong)-> strongNotTaken -(wrong)-> weakNotTaken -(wrong)-> strongTaken
  // ==========================================================================
  "BTB_PredictorFSMTester" should "work" in {
    test(new BTB) { dut =>
      val pc = 0x40.U
      // Allocation after a taken branch -> weakTaken (predict taken)
      applyUpdate(dut, pc, 0x100.U, mispredicted = true)
      expectEntry(dut, pc, 0x100.U, predictTaken = true)

      // Correct prediction -> strongTaken (predict taken)
      applyUpdate(dut, pc, 0x100.U, mispredicted = false)
      expectEntry(dut, pc, 0x100.U, predictTaken = true)

      // One wrong prediction: strongTaken -> weakTaken, still predict taken
      applyUpdate(dut, pc, 0x100.U, mispredicted = true)
      expectEntry(dut, pc, 0x100.U, predictTaken = true)

      // Second wrong prediction: weakTaken -> strongNotTaken, prediction flips
      applyUpdate(dut, pc, 0x100.U, mispredicted = true)
      expectEntry(dut, pc, 0x100.U, predictTaken = false)

      // Wrong once as not-taken: strongNotTaken -> weakNotTaken, still not taken
      applyUpdate(dut, pc, 0x100.U, mispredicted = true)
      expectEntry(dut, pc, 0x100.U, predictTaken = false)

      // Wrong again: weakNotTaken -> strongTaken, prediction flips back
      applyUpdate(dut, pc, 0x100.U, mispredicted = true)
      expectEntry(dut, pc, 0x100.U, predictTaken = true)

      // Correct predictions keep it saturated at strongTaken
      applyUpdate(dut, pc, 0x100.U, mispredicted = false)
      applyUpdate(dut, pc, 0x100.U, mispredicted = false)
      expectEntry(dut, pc, 0x100.U, predictTaken = true)
    }
  }

  // ==========================================================================
  // LRU eviction: with a full set, the entry that has not been used for the
  // longest time is evicted, and both lookups and updates count as a use
  // ==========================================================================
  "BTB_LRUTester" should "work" in {
    test(new BTB) { dut =>
      // Three branches mapping to set 3 (PC bits [4:2] = 011)
      val pcA = 0x0C.U // tag 0
      val pcB = 0x2C.U // tag 1
      val pcC = 0x4C.U // tag 2
      val pcD = 0x6C.U // tag 3

      // Fill both ways of set 3: A then B (B is most recently used)
      applyUpdate(dut, pcA, 0x100.U, mispredicted = true)
      applyUpdate(dut, pcB, 0x200.U, mispredicted = true)

      // A lookup of A makes B the least recently used entry
      touch(dut, pcA)

      // Inserting C into the full set must evict B, not A
      applyUpdate(dut, pcC, 0x300.U, mispredicted = true)
      expectEntry(dut, pcA, 0x100.U, predictTaken = true)
      expectEntry(dut, pcC, 0x300.U, predictTaken = true)
      expectMiss(dut, pcB)

      // C was inserted last, so A is now LRU: inserting D evicts A
      applyUpdate(dut, pcD, 0x400.U, mispredicted = true)
      expectEntry(dut, pcC, 0x300.U, predictTaken = true)
      expectEntry(dut, pcD, 0x400.U, predictTaken = true)
      expectMiss(dut, pcA)
    }
  }
}
