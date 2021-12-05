package FiveStage

import chisel3._
import chisel3.core.Input
import chisel3.experimental._


class CPU extends MultiIOModule {

  val testHarness = IO(
    new Bundle {
      val setupSignals = Input(new SetupSignals)
      val testReadouts = Output(new TestReadouts)
      val regUpdates   = Output(new RegisterUpdates)
      val memUpdates   = Output(new MemUpdates)
      val currentPC    = Output(UInt(32.W))
    }
  )

  /**
    You need to create the classes for these yourself
    */

  // val IFBarrier        = Module(new IFBarrier).io
  val IDBarrier        = Module(new IDBarrier).io
  val EXBarrier        = Module(new EXBarrier).io
  val MEMBarrier       = Module(new MEMBarrier).io
  // val WriteBackBarrier = Module(new WriteBackBarrier).io

  val IF  = Module(new InstructionFetch)
  val ID  = Module(new InstructionDecode)
  val EX  = Module(new Execute)
  val MEM = Module(new MemoryFetch)
  val WB  = Module(new WriteBack)


  /**
    * Setup. You should not change this code
    */
  IF.testHarness.IMEMsetup     := testHarness.setupSignals.IMEMsignals
  ID.testHarness.registerSetup := testHarness.setupSignals.registerSignals
  MEM.testHarness.DMEMsetup    := testHarness.setupSignals.DMEMsignals

  testHarness.testReadouts.registerRead := ID.testHarness.registerPeek
  testHarness.testReadouts.DMEMread     := MEM.testHarness.DMEMpeek

  /**
    spying stuff
    */
  testHarness.regUpdates := ID.testHarness.testUpdates
  testHarness.memUpdates := MEM.testHarness.testUpdates
  testHarness.currentPC  := IF.testHarness.PC

  ID.io.in              <> IF.io.out

  IF.io.stall           := ID.io.stall
  IF.io.jump            := ID.io.toJump
  IF.io.nextPC          := ID.io.nextPC
  IF.io.stopped         := ID.io.stopped

  IDBarrier.in          <> ID.io.out
  EX.io.in              <> IDBarrier.out

  EXBarrier.in          <> EX.io.out
  MEM.io.in             <> EXBarrier.out

  MEMBarrier.lastMemValue := MEM.io.lastMemValue

  MEMBarrier.in         <> MEM.io.out
  WB.io.in              <> MEMBarrier.out

  ID.io.writeBack       <> WB.io.out

  EX.io.out             <> ID.io.ex
  MEM.io.out            <> ID.io.mem
}
