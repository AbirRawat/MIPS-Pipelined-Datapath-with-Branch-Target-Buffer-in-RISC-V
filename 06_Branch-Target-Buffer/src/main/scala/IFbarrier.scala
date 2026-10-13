// ADS I Class Project
// Pipelined RISC-V Core - IF Barrier
//
// Chair of Electronic Design Automation, RPTU in Kaiserslautern
// File created on 01/09/2026 by Tobias Jauch (@tojauch)

/*
IF-Barrier: pipeline register between Fetch and Decode stages

Internal Registers:
    instrReg: holds instruction between pipeline stages, initialized to 0

Inputs:
    inInstr: fetched instruction from IF stage

Outputs:
    outInstr: instruction to ID stage

Functionality:
    Save all input signals to a register and output them in the following clock cycle
*/

package core_tile

import chisel3._

// -----------------------------------------
// IF-Barrier
// -----------------------------------------

class IFBarrier extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())
    val inPC = Input(UInt(32.W))
    val inInstr = Input(UInt(32.W))
    val inPredictedTaken = Input(Bool())
    val outPC = Output(UInt(32.W))
    val outInstr = Output(UInt(32.W))
    val outPredictedTaken = Output(Bool())
  })

  val pcReg = RegInit(0.U(32.W))
  val instrReg = RegInit(0.U(32.W))
  val predictedTakenReg = RegInit(false.B)

  when(io.flush) {
    instrReg := 0.U
    pcReg := 0.U
    predictedTakenReg := false.B
  }.otherwise {
    pcReg := io.inPC
    instrReg := io.inInstr
    predictedTakenReg := io.inPredictedTaken
  }

  io.outPC := pcReg
  io.outInstr := instrReg
  io.outPredictedTaken := predictedTakenReg
}