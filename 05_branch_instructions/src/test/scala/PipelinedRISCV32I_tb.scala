// ADS I Class Project
// Pipelined RISC-V Core
//
// Chair of Electronic Design Automation, RPTU in Kaiserslautern
// File created on 01/15/2023 by Tobias Jauch (@tojauch)

package PipelinedRV32I_Tester

import chisel3._
import chiseltest._
import PipelinedRV32I._
import org.scalatest.flatspec.AnyFlatSpec

class PipelinedRISCV32ITest extends AnyFlatSpec with ChiselScalatestTester {

  "RV32I_BasicTester" should "work" in {
    test(new PipelinedRV32I("src/test/programs/BinaryFile_pipelined"))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.setTimeout(0)
        dut.clock.step(5)
        dut.io.result.expect(0.U) // ADDI x0, x0, 0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(4.U) // ADDI x1, x0, 4
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(5.U) // ADDI x2, x0, 5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(9.U) // ADD x3, x1, x2
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(2047.U) // ADDI x4, x0, 2047
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(16.U) // ADDI x5, x0, 16
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(2031.U) // SUB x6, x4, x5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(2022.U) // XOR x7, x6, x3
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(2047.U) // OR x8, x6, x5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // AND x9, x6, x5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(64704.U) // SLL x10, x7, x2
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(63.U) // SRL x11, x7, x2
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(63.U) // SRA x12, x7, x2
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // SLT x13, x4, x4
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // SLT x13, x4, x5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(1.U) // SLT x13, x5, x4
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // SLTU x13, x4, x4
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // SLTU x13, x4, x5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(1.U) // SLTU x13, x5, x4
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.exception.expect(true.B) // DIV x3, x2, x1 → NOT IMPLEMENTED, exception
        dut.clock.step(1)
      }
  }

  // ==========================================================================
  // Extended Test: Comprehensive RAW hazard and forwarding scenarios
  // ==========================================================================
  "RV32I_ExtendedTester" should "work" in {
    test(new PipelinedRV32I("src/test/programs/BinaryFile_pipelined_extended"))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        // --- Fill pipeline (5 cycles) ---
        dut.clock.step(5)

        // ================================================================
        // Section 1: I-type Immediate Instructions (negative/boundary)
        // ================================================================
        dut.io.result.expect("hFFFFFFFF".U) // ADDI x1, x0, -1
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect("hFFFFF800".U) // ADDI x2, x0, -2048
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(2047.U) // ADDI x3, x0, 2047
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect("hFFFFFF00".U) // XORI x4, x1, 0xFF
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect("h00000123".U) // ORI  x5, x0, 0x123
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect("h000000FF".U) // ANDI x6, x1, 0xFF
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(1.U) // SLTI  x7, x1, 0  → -1 < 0 signed = true
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // SLTIU x8, x1, 1  → 0xFFFFFFFF > 1 unsigned = false
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 2: Shift Immediate Edge Cases (shift by 0 and 31)
        // ================================================================
        dut.io.result.expect("hFFFFFFFF".U) // ADDI x9, x0, -1
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect("hFFFFFFFF".U) // SLLI x10, x9, 0  → no shift
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect("h80000000".U) // SLLI x11, x9, 31 → 0x80000000
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect("hFFFFFFFF".U) // SRLI x12, x9, 0  → no shift
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(1.U) // SRLI x13, x9, 31 → 0x00000001
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect("hFFFFFFFF".U) // SRAI x14, x9, 0  → no shift, sign preserved
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect("hFFFFFFFF".U) // SRAI x15, x9, 31 → all sign bits
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 3: x0 Hardwiring (write to x0 must be discarded)
        // ================================================================
        dut.io.result.expect(999.U) // ADDI x0, x0, 999 → ALU=999, but x0 stays 0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // ADD x24, x0, x0 → x0 still 0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 4: Self-Referencing Operation (same reg as src & dst)
        // ================================================================
        dut.io.result.expect(10.U) // ADDI x25, x0, 10
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(20.U) // ADD x25, x25, x25 → 10 + 10 = 20
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
      }
  }

  // ==========================================================================
  // Hazards Test: RAW, WAW, x0 guard, register bypass, double forwarding
  // ==========================================================================
  "RV32I_HazardsTester" should "work" in {
    test(new PipelinedRV32I("src/test/programs/BinaryFile_hazards"))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        // --- Fill pipeline (5 cycles) ---
        dut.clock.step(5)

        // ================================================================
        // Section 1: Distance-1 RAW hazard (forward from EX barrier / MEM)
        // ================================================================

        dut.io.result.expect(4.U) // ADDI x1, x0, 4 → x1=4
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        dut.io.result.expect(4.U) // ADD x1, x1, x2 → x1 fwd dist-1(MEM)=4, x2=0(regfile) → x1=4
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        dut.io.result.expect(5.U) // ADDI x2, x0, 5 → x2=5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // Tests double forwarding: both operands forwarded from different stages
        dut.io.result.expect(
          9.U
        ) // ADD x3, x1, x2 → x1 fwd dist-2(WB)=4, x2 fwd dist-1(MEM)=5 → x3=9
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 2: Distance-2 RAW hazard (forward from WB, i.e. MEM barrier)
        // ================================================================

        dut.io.result.expect(2047.U) // ADDI x4, x0, 2047 → x4=2047
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        dut.io.result.expect(16.U) // ADDI x5, x0, 16 → x5=16
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        dut.io.result.expect(
          2031.U
        ) // SUB x6, x4, x5 → x4 fwd dist-2(WB)=2047, x5 fwd dist-1(MEM)=16 → 2031
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 3: Chain forwarding (3 dependent instructions on x6)
        // ================================================================

        dut.io.result.expect(
          2022.U
        ) // XOR x7, x6, x3 → x6 fwd dist-1(MEM)=2031, x3 fwd dist-4(regfile)=9 → 2022
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        dut.io.result.expect(
          2047.U
        ) // OR x8, x6, x5 → x6 fwd dist-2(WB)=2031, x5 dist-3(regfile bypass)=16 → 2047
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        dut.io.result.expect(
          0.U
        ) // AND x9, x6, x5 → x6 dist-3(regfile bypass)=2031, x5 dist-4(regfile)=16 → 0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 4: Same-cycle read/write (WB writes, ID reads, distance-3)
        // ================================================================

        dut.io.result.expect(10.U) // ADDI x30, x0, 10 → x30=10
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        dut.io.result.expect(0.U) // NOP → result=0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        dut.io.result.expect(0.U) // NOP → result=0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        dut.io.result.expect(
          10.U
        ) // ADD x31, x0, x30 → x30 from same-cycle regfile bypass=10 → x31=10
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 5: I-type source hazard (distance-1)
        // ================================================================

        dut.io.result.expect(20.U) // ADDI x28, x0, 20 → x28=20
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        dut.io.result.expect(21.U) // ADDI x28, x28, 1 → x28 fwd dist-1(MEM)=20 + 1 = 21
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        dut.io.result.expect(22.U) // ADDI x28, x28, 1 → x28 fwd dist-1(MEM)=21 + 1 = 22
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 6: x0 forwarding guard (writing x0 must NOT forward)
        // ================================================================

        dut.io.result.expect(999.U) // ADDI x0, x0, 999 → ALU result=999, but x0 stays 0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        dut.io.result.expect(0.U) // ADD x29, x0, x0 → x0 NOT forwarded (guard: rd≠0), x29=0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
      }
  }

  // ==========================================================================
  // Comparator Semantics: signed vs. unsigned discrimination on one pair
  //   x1 = 5, x10 = -1 (0xFFFFFFFF). BLT/BGE (signed) disagree with
  //   BLTU/BGEU (unsigned) on the very same operands. BEQ/BNE cover the
  //   equality compares (sign-agnostic), each taken and not-taken.
  //   A branch writes no register, so its io.result is a don't-care (it is
  //   just the incidental ALU ADD of the operands); only exception and the
  //   taken/not-taken outcome are asserted.
  // ==========================================================================
  "RV32I_BranchCmpTester" should "work" in {
    test(new PipelinedRV32I("src/test/programs/branches/BinaryFile_cmp"))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        // --- Fill pipeline (5 cycles) ---
        dut.clock.step(5)

        // ================================================================
        // Section 1: Setup operands
        // ================================================================
        dut.io.result.expect(5.U) // ADDI x1, x0, 5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(BigInt("FFFFFFFF", 16).U) // ADDI x10, x0, -1
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 2: BLT (signed) taken, BLTU (unsigned) not taken on (-1, 5)
        // ================================================================
        // BLT x10, x1, +8 taken; signed -1 < 5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(4.U) // ADDI x4, x0, 4 (BLT target)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        // BLTU x10, x1, +8 NOT taken; unsigned 0xFFFFFFFF > 5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(55.U) // ADDI x5, x0, 55 (BLTU fell through)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 3: BGE (signed) taken, BGEU (unsigned) not taken on (5, -1)
        // ================================================================
        // BGE x1, x10, +8 taken; signed 5 >= -1
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(7.U) // ADDI x7, x0, 7 (BGE target)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        // BGEU x1, x10, +8 NOT taken; unsigned 5 < 0xFFFFFFFF
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(88.U) // ADDI x8, x0, 88 (BGEU fell through)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 4: BEQ / BNE equality (sign is irrelevant) on (5, 5) & (5, -1)
        // ================================================================
        // BEQ x1, x1, +8 taken; 5 == 5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(13.U) // ADDI x13, x0, 13 (BEQ target)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        // BEQ x1, x10, +8 NOT taken; 5 != -1
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(44.U) // ADDI x14, x0, 44 (BEQ fell through)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        // BNE x1, x10, +8 taken; 5 != -1
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(16.U) // ADDI x16, x0, 16 (BNE target)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        // BNE x1, x1, +8 NOT taken; 5 == 5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(66.U) // ADDI x17, x0, 66 (BNE fell through)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        dut.io.result.expect(9.U) // ADDI x9, x0, 9 (end marker)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
      }
  }

  // ==========================================================================
  // Backward Branch (loop): negative immB, repeated taken then final not-taken,
  //   and distance-1 forwarding of the loop counter into the branch decision.
  //   x1 counts 3 -> 0 while x2 accumulates to 3.
  // ==========================================================================
  "RV32I_BranchLoopTester" should "work" in {
    test(new PipelinedRV32I("src/test/programs/branches/BinaryFile_loop"))
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
        // Section 2: Iteration 1 (x1: 3->2, BNE taken)
        // ================================================================
        dut.io.result.expect(1.U) // ADDI x2, x2, 1 -> x2 = 1
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(2.U) // ADDI x1, x1, -1 -> x1 = 2
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        // BNE x1, x0, -8 taken; x1(fwd)=2 != 0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 3: Iteration 2 (x1: 2->1, BNE taken)
        // ================================================================
        dut.io.result.expect(2.U) // ADDI x2, x2, 1 -> x2 = 2
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(1.U) // ADDI x1, x1, -1 -> x1 = 1
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        // BNE x1, x0, -8 taken; x1(fwd)=1 != 0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 4: Iteration 3 (x1: 1->0, BNE NOT taken -> exit)
        // ================================================================
        dut.io.result.expect(3.U) // ADDI x2, x2, 1 -> x2 = 3
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // ADDI x1, x1, -1 -> x1 = 0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        // BNE x1, x0, -8 NOT taken; x1(fwd)=0
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(3.U) // ADDI x9, x2, 0 -> x9 = x2 = 3 (accumulator readback)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
      }
  }

  // ==========================================================================
  // Flush Correctness: a wrong-path register write must NOT retire.
  //   x8 seeded to 42; a taken BEQ skips a write of 111 to x8; reading x8
  //   afterwards must still yield 42.
  // ==========================================================================
  "RV32I_BranchFlushTester" should "work" in {
    test(new PipelinedRV32I("src/test/programs/branches/BinaryFile_flush"))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        // --- Fill pipeline (5 cycles) ---
        dut.clock.step(5)

        // ================================================================
        // Section 1: Seed x8 and branch operands
        // ================================================================
        dut.io.result.expect(42.U) // ADDI x8, x0, 42 (seed)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(5.U) // ADDI x1, x0, 5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(5.U) // ADDI x2, x0, 5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 2: Taken BEQ flushes the wrong-path write to x8
        // ================================================================
        // BEQ x1, x2, +12 taken (5 == 5)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // ADDI x8, x0, 111 (wrong-path, flushed)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // ADDI x3, x0, 99 (wrong-path, flushed)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 3: Target reads x8 -> must be 42, proving no wrong write
        // ================================================================
        dut.io.result.expect(42.U) // ADD x9, x8, x0 -> x8 still 42 (not 111)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
      }
  }

  // ==========================================================================
  // Jump / Link Semantics: JAL writes PC+4, JAL x0 discards the link, and a
  //   JALR return restores the saved address (call/return pattern).
  // ==========================================================================
  "RV32I_BranchJumpLinkTester" should "work" in {
    test(new PipelinedRV32I("src/test/programs/branches/BinaryFile_jump"))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        // --- Fill pipeline (5 cycles) ---
        dut.clock.step(5)

        // ================================================================
        // Section 1: JAL x1 saves link (PC+4) and jumps to the function
        // ================================================================
        dut.io.result.expect(7.U) // ADDI x5, x0, 7
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(8.U) // JAL x1, +16 -> link x1 = 8, jump to func
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 2: Function body returns via JALR x0, 0(x1)
        // ================================================================
        dut.io.result.expect(40.U) // ADDI x8, x0, 40 (func body)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(28.U) // JALR x0, 0(x1) -> link 28, return to x1 = 8
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)

        // ================================================================
        // Section 3: Back at the call site, JAL x0 jumps past the function
        // ================================================================
        dut.io.result.expect(20.U) // ADDI x6, x0, 20 (return landing)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(30.U) // ADDI x7, x0, 30
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(20.U) // JAL x0, +12 -> link 20 discarded (x0 stays 0)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // flushed wrong-path instruction
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(9.U) // ADDI x9, x0, 9 (end marker)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
      }
  }

  // ==========================================================================
  // Exceptions in Branch/Jump: illegal funct3 encodings raise the exception
  //   flag (decoded to NOP, no redirect, no persistent trap), and a misaligned
  //   jump target (target[1:0] != 0) raises the exception flag. The misaligned
  //   jump is the terminal instruction: we assert only that it raises the
  //   exception and never observe the (architecturally incorrect) redirect.
  // ==========================================================================
  "RV32I_BranchExceptionTester" should "work" in {
    test(new PipelinedRV32I("src/test/programs/branches/BinaryFile_xcpt"))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        // --- Fill pipeline (5 cycles) ---
        dut.clock.step(5)

        // ================================================================
        // Section 1: Illegal branch/JALR funct3 raise exception, no redirect.
        //   Each illegal encoding decodes to NOP; the following valid
        //   instruction reads exception=false, proving the trap does not persist.
        // ================================================================
        dut.io.result.expect(5.U) // ADDI x1, x0, 5
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(6.U) // ADDI x2, x0, 6
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // illegal branch funct3=010 -> NOP
        dut.io.exception.expect(true.B) // raises exception
        dut.clock.step(1)
        dut.io.result.expect(7.U) // ADDI x3, x0, 7
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // illegal branch funct3=011 -> NOP
        dut.io.exception.expect(true.B) // raises exception
        dut.clock.step(1)
        dut.io.result.expect(8.U) // ADDI x4, x0, 8
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        dut.io.result.expect(0.U) // illegal JALR funct3=001 -> NOP
        dut.io.exception.expect(true.B) // raises exception
        dut.clock.step(1)

        // ================================================================
        // Section 2: Misaligned JALR target raises exception (terminal).
        //   The misaligned JALR is the last executed instruction: we assert
        //   only that it raises the exception and do NOT step past it, so the
        //   test never depends on where the (incorrect) redirect lands.
        // ================================================================
        dut.io.result.expect(64.U) // ADDI x6, x0, 64 (aligned base)
        dut.io.exception.expect(false.B)
        dut.clock.step(1)
        // JALR x0, 2(x6) -> target 0x42 misaligned
        dut.io.exception.expect(true.B) // misaligned target exception
        dut.clock.step(1)
      }
  }

}
