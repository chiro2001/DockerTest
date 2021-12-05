package FiveStage
import chisel3._
import chisel3.experimental.MultiIOModule

class RegistersInContents extends Bundle {
  val readAddress1 = UInt(5.W)
  val readAddress2 = UInt(5.W)
  val writeEnable  = Bool()
  val writeAddress = UInt(5.W)
  val writeData    = UInt(32.W)
}

class RegistersOutContents extends Bundle {
  val readData1    = UInt(32.W)
  val readData2    = UInt(32.W)
}

/**
  * This module is already done. Have one on me
  *  
  * When a write and read conflicts this might result in stale data.
  * This caveat must be handled using a bypass.
 */
class Registers() extends MultiIOModule {


  // Don't touch the test harness
  val testHarness = IO(
    new Bundle {
      val setup        = Input(new RegisterSetupSignals)
      val testUpdates  = Output(new RegisterUpdates)
    }
  )


  // You shouldn't really touch these either
  val io = IO(new Bundle {
    val in = Input(new RegistersInContents)
    val out = Output(new RegistersOutContents)
  })


  /**
    * Mem creates asynchronous read, synchronous write.
    * In other words, reading is combinatory.
    */
  val registerFile = Mem(32, UInt(32.W))

  val readAddress1 = Wire(UInt(5.W))
  val readAddress2 = Wire(UInt(5.W))
  val writeAddress = Wire(UInt(5.W))
  val writeData    = Wire(UInt(32.W))
  val writeEnable  = Wire(Bool())

  when(testHarness.setup.setup){
    readAddress1 := testHarness.setup.readAddress
    readAddress2 := io.in.readAddress2
    writeData    := testHarness.setup.writeData
    writeEnable  := testHarness.setup.writeEnable
    writeAddress := testHarness.setup.readAddress
  }.otherwise{
    readAddress1 := io.in.readAddress1
    readAddress2 := io.in.readAddress2
    writeData    := io.in.writeData
    writeEnable  := io.in.writeEnable
    writeAddress := io.in.writeAddress
  }


  testHarness.testUpdates.writeData := writeData
  testHarness.testUpdates.writeEnable := writeEnable
  testHarness.testUpdates.writeAddress := writeAddress


  when(writeEnable){
    when(writeAddress =/= 0.U){
      registerFile(writeAddress) := writeData
    }
  }


  io.out.readData1 := 0.U
  io.out.readData2 := 0.U
  when(readAddress1 =/= 0.U){ io.out.readData1 := registerFile(readAddress1) }
  when(readAddress2 =/= 0.U){ io.out.readData2 := registerFile(readAddress2) }
}
