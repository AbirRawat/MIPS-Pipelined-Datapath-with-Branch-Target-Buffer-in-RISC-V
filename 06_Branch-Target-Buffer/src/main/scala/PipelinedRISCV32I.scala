// ADS I Class Project
// Pipelined RISC-V Core
//
// Chair of Electronic Design Automation, RPTU in Kaiserslautern
// File created on 05/10/2023 by Tobias Jauch (@tojauch)

/*
This file contains the top-level module for the Pipelined RISC-V 32I core and acts as the interface between the core and external testbenches.
*/

package PipelinedRV32I

import chisel3._
import chisel3.util._

import core_tile._

// useBTB selects between the dynamic BTB prediction scheme and the static
// not-taken scheme from assignment 5 (default, so the existing assignment 5
// testbench keeps running unchanged against the static core).
class PipelinedRV32I (BinaryFile: String, useBTB: Boolean = false) extends Module {

val io = IO(new Bundle {
  val result    = Output(UInt(32.W))
  val exception = Output(Bool())
  // Branch prediction accuracy counters (Task 6.4)
  val branchCount    = Output(UInt(32.W))
  val mispredictCount = Output(UInt(32.W))
 })

  val core = Module(new PipelinedRV32Icore(BinaryFile, useBTB))

  io.result    := core.io.check_res
  io.exception := core.io.exception
  io.branchCount     := core.io.branchCount
  io.mispredictCount := core.io.mispredictCount

}
