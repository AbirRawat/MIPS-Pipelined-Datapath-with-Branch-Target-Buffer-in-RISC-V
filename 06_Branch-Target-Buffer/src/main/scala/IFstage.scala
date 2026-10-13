// ADS I Class Project
// Pipelined RISC-V Core - IF Stage
//
// Chair of Electronic Design Automation, RPTU in Kaiserslautern
// File created on 01/09/2026 by Tobias Jauch (@tojauch)

/*
The Instruction Fetch (IF) stage is the first stage of the pipeline and handles instruction retrieval from memory.

Memory:
    IMem: instruction memory with 4096 32-bit unsigned integer entires, loaded from a binary file at compile time

Internal Registers:
    PC: 32-bit unsigned integer register, initialized to 0 holding the current program counter address

Internal Signals:
    none

Functionality:
    Fetch the instruction at the current PC (word-aligned addressing)
    Increment the PC (word-aligned) each clock cycle to fetch the next sequential instruction
    Handle flushes due to mispredicted branches

Parameters:
    BinaryFile: String - path to the binary file to load into instruction memory

Inputs:
    none

Outputs:
    instr: send the fetched instruction to IF Barrier
*/

package core_tile

import chisel3._
import chisel3.util.experimental.loadMemoryFromFile

// -----------------------------------------
// Fetch Stage
// -----------------------------------------

class IF(BinaryFile: String) extends Module {
  val io = IO(new Bundle {
    val redirectValid = Input(Bool())
    val redirectPC = Input(UInt(32.W))
    // Prediction for the currently fetched PC (from the BTB; tied off when
    // the static not-taken scheme is used)
    val predValid = Input(Bool())
    val predTarget = Input(UInt(32.W))
    val predTaken = Input(Bool())
    val pcOut = Output(UInt(32.W))
    val instr = Output(UInt(32.W))
    // Tells the later stages whether this instruction was fetched under a
    // taken prediction, so EX can detect mispredictions
    val predictedTaken = Output(Bool())
  })

  val IMem = Mem(4096, UInt(32.W))
  loadMemoryFromFile(IMem, BinaryFile)

  val PC = RegInit(0.U(32.W))

  val takenPrediction = io.predValid && io.predTaken

  io.pcOut := PC
  io.instr := IMem(PC >> 2.U)
  io.predictedTaken := takenPrediction

  // A redirect from EX (misprediction or jump) overrides everything. If the
  // BTB predicts the fetched branch as taken, fetch from its target next,
  // otherwise fall through to PC + 4.
  PC := Mux(
    io.redirectValid,
    io.redirectPC,
    Mux(takenPrediction, io.predTarget, PC + 4.U)
  )
}
