// ADS I Class Project
// Pipelined RISC-V Core
//
// Chair of Electronic Design Automation, RPTU in Kaiserslautern
// File created on 01/15/2023 by Tobias Jauch (@tojauch)

/*
The goal of this task is to implement a 5-stage pipeline that features a subset of RV32I (all R-type and I-type instructions). 

    Instruction Memory:
        The CPU has an instruction memory (IMem) with 4096 words, each of 32 bits.
        The content of IMem is loaded from a binary file specified during the instantiation of the MultiCycleRV32Icore module.

    CPU Registers:
        The CPU has a program counter (PC) and a register file (regFile) with 32 registers, each holding a 32-bit value.
        Register x0 is hard-wired to zero.

    Microarchitectural Registers / Wires:
        Various signals are defined as either registers or wires depending on whether they need to be used in the same cycle or in a later cycle.

    Processor Stages:
        The FSM of the processor has five stages: fetch, decode, execute, memory, and writeback.
        All stages are active at the same time and process different instructions simultaneously.

        Fetch Stage:
            The instruction is fetched from the instruction memory based on the current value of the program counter (PC).

        Decode Stage:
            Instruction fields such as opcode, rd, funct3, and rs1 are extracted.
            For R-type instructions, additional fields like funct7 and rs2 are extracted.
            Control signals (isADD, isSUB, etc.) are set based on the opcode and funct3 values.
            Operands (operandA and operandB) are determined based on the instruction type.

        Execute Stage:
            Arithmetic and logic operations, including branch target calculation, are performed based on the control signals and operands.
            The result is stored in the aluResult register.

        Memory Stage:
            No memory operations are implemented in this basic CPU.

        Writeback Stage:
            The result of the operation (writeBackData) is written back to the destination register (rd) in the register file.

    Check Result:
        The final result (writeBackData) is output to the io.check_res signal.
        The exception signal is also passed to the wrapper module. It indicates whether an invalid instruction has been encountered.
        In the fetch stage, a default value of 0 is assigned to io.check_res.
*/

package core_tile

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import Assignment02.{ALU, ALUOp}
import uopc._


// useBTB switches between the dynamic BTB-based prediction scheme and the
// static not-taken scheme from the previous assignment (default, so the
// assignment 5 testbench keeps running unchanged against the static core).
class PipelinedRV32Icore(BinaryFile: String, useBTB: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val check_res = Output(UInt(32.W))
    val exception = Output(Bool())
    // Performance counters for branch prediction accuracy (Task 6.4)
    val branchCount = Output(UInt(32.W))
    val mispredictCount = Output(UInt(32.W))
  })

  // --- Instantiate pipeline stages ---
  val ifStage = Module(new IF(BinaryFile))
  val ifBarrier = Module(new IFBarrier)
  val idStage = Module(new ID)
  val idBarrier = Module(new IDBarrier)
  val exStage = Module(new EX)
  val exBarrier = Module(new EXBarrier)
  val memStage = Module(new MEM)
  val memBarrier = Module(new MEMBarrier)
  val wbStage = Module(new WB)
  val wbBarrier = Module(new WBBarrier)

  // --- Instantiate Register File ---
  val registerFile = Module(new regFile)

  // --- Instantiate Forwarding Unit ---
  val forwardingUnit = Module(new ForwardingUnit)

  // --- Branch prediction: BTB (dynamic) or static not-taken ---
  if (useBTB) {
    val btb = Module(new BTB)
    // Lookup for the PC currently being fetched
    btb.io.PC := ifStage.io.pcOut
    ifStage.io.predValid := btb.io.valid
    ifStage.io.predTarget := btb.io.target
    ifStage.io.predTaken := btb.io.predictTaken
    // Update from EX once the branch is resolved
    btb.io.update := exStage.io.btbUpdate
    btb.io.updatePC := exStage.io.btbUpdatePC
    btb.io.updateTarget := exStage.io.btbUpdateTarget
    btb.io.mispredicted := exStage.io.btbMispredicted
  } else {
    // Static not-taken scheme: never predict, EX redirects on taken branches
    ifStage.io.predValid := false.B
    ifStage.io.predTarget := 0.U
    ifStage.io.predTaken := false.B
  }

  // --- IF -> IF Barrier ---
  ifStage.io.redirectValid := exStage.io.redirect
  ifStage.io.redirectPC := exStage.io.redirectTarget
  ifBarrier.io.flush := exStage.io.redirect
  ifBarrier.io.inPC := ifStage.io.pcOut
  ifBarrier.io.inInstr := ifStage.io.instr
  ifBarrier.io.inPredictedTaken := ifStage.io.predictedTaken

  // --- IF Barrier -> ID ---
  idStage.io.pc := ifBarrier.io.outPC
  idStage.io.instr := ifBarrier.io.outInstr
  idStage.io.predictedTaken := ifBarrier.io.outPredictedTaken

  // --- ID <-> Register File (read ports) ---
  registerFile.io.req_1.addr := idStage.io.regFileReq_A.addr
  idStage.io.regFileResp_A.data := registerFile.io.resp_1.data

  registerFile.io.req_2.addr := idStage.io.regFileReq_B.addr
  idStage.io.regFileResp_B.data := registerFile.io.resp_2.data

  // --- ID -> ID Barrier ---
  idBarrier.io.flush := exStage.io.redirect
  idBarrier.io.inUOP := idStage.io.uop
  idBarrier.io.inRD := idStage.io.rd
  idBarrier.io.inRS1 := idStage.io.rs1Out
  idBarrier.io.inRS2 := idStage.io.rs2Out
  idBarrier.io.inOperandA := idStage.io.operandA
  idBarrier.io.inOperandB := idStage.io.operandB
  idBarrier.io.inPC := idStage.io.pcOut
  idBarrier.io.inImm := idStage.io.immOut
  idBarrier.io.inWrEn := idStage.io.wrEn
  idBarrier.io.inPredictedTaken := idStage.io.predictedTakenOut
  idBarrier.io.inXcptInvalid := idStage.io.XcptInvalid

  // --- ID Barrier -> EX (with forwarding muxes) ---
  exStage.io.uop := idBarrier.io.outUOP
  exStage.io.rd := idBarrier.io.outRD
  exStage.io.pc := idBarrier.io.outPC
  exStage.io.imm := idBarrier.io.outImm
  exStage.io.predictedTaken := idBarrier.io.outPredictedTaken
  exStage.io.XcptInvalid := idBarrier.io.outXcptInvalid

  // --- Forwarding Unit connections ---
  forwardingUnit.io.rs1_EX := idBarrier.io.outRS1
  forwardingUnit.io.rs2_EX := idBarrier.io.outRS2
  forwardingUnit.io.rd_MEM := exBarrier.io.outRD
  forwardingUnit.io.rd_WB := memBarrier.io.outRD
  forwardingUnit.io.wrEn_MEM := exBarrier.io.outWrEn
  forwardingUnit.io.wrEn_WB := memBarrier.io.outWrEn

  // --- Forwarding muxes (in core.scala per assignment specification) ---
  // forwardA/B: 00 = no forwarding, 10 = forward from MEM (EX barrier), 01 = forward from WB (MEM barrier)
  exStage.io.operandA := MuxLookup(
    forwardingUnit.io.forwardA,
    idBarrier.io.outOperandA,
    Seq(
      "b00".U -> idBarrier.io.outOperandA,
      "b10".U -> exBarrier.io.outAluResult,
      "b01".U -> memBarrier.io.outAluResult
    )
  )

  exStage.io.operandB := MuxLookup(
    forwardingUnit.io.forwardB,
    idBarrier.io.outOperandB,
    Seq(
      "b00".U -> idBarrier.io.outOperandB,
      "b10".U -> exBarrier.io.outAluResult,
      "b01".U -> memBarrier.io.outAluResult
    )
  )

  // --- EX -> EX Barrier ---
  exBarrier.io.inAluResult := exStage.io.aluResult
  exBarrier.io.inRD := exStage.io.rdOut
  // Write enable is computed in ID and carried through the ID barrier (same
  // instruction slot as the EX stage), so route it around the EX stage.
  exBarrier.io.inWrEn := idBarrier.io.outWrEn
  exBarrier.io.inXcptInvalid := exStage.io.exception

  // --- EX Barrier -> MEM Barrier (MEM stage is empty, pass through) ---
  memBarrier.io.inAluResult := exBarrier.io.outAluResult
  memBarrier.io.inRD := exBarrier.io.outRD
  memBarrier.io.inWrEn := exBarrier.io.outWrEn
  memBarrier.io.inException := exBarrier.io.outXcptInvalid

  // --- MEM Barrier -> WB ---
  wbStage.io.aluResult := memBarrier.io.outAluResult
  wbStage.io.rd := memBarrier.io.outRD
  wbStage.io.wrEn := memBarrier.io.outWrEn

  // --- WB <-> Register File (write port) ---
  registerFile.io.req_3.addr := wbStage.io.regFileReq.addr
  registerFile.io.req_3.data := wbStage.io.regFileReq.data
  registerFile.io.req_3.wr_en := wbStage.io.regFileReq.wr_en

  // --- WB -> WB Barrier ---
  wbBarrier.io.inCheckRes := wbStage.io.check_res
  wbBarrier.io.inXcptInvalid := memBarrier.io.outException

  // --- WB Barrier -> Core outputs ---
  io.check_res := wbBarrier.io.outCheckRes
  io.exception := wbBarrier.io.outXcptInvalid

  // --- Branch prediction accuracy counters (Task 6.4) ---
  // Count every resolved conditional branch in EX and every misprediction.
  // Accuracy = (branchCount - mispredictCount) / branchCount. With
  // useBTB = false the counters measure the static not-taken scheme, which
  // makes the two schemes directly comparable.
  val branchCounter = RegInit(0.U(32.W))
  val mispredictCounter = RegInit(0.U(32.W))
  when(exStage.io.btbUpdate) {
    branchCounter := branchCounter + 1.U
    when(exStage.io.btbMispredicted) {
      mispredictCounter := mispredictCounter + 1.U
    }
  }
  io.branchCount := branchCounter
  io.mispredictCount := mispredictCounter
}

