package FiveStage
import chisel3._
import chisel3.util._
import chisel3.experimental.MultiIOModule


class MemoryFetch() extends MultiIOModule {


  // Don't touch the test harness
  val testHarness = IO(
    new Bundle {
      val DMEMsetup      = Input(new DMEMsetupSignals)
      val DMEMpeek       = Output(UInt(32.W))

      val testUpdates    = Output(new MemUpdates)
    })

  val io = IO(new Bundle {
    val in = Input(new EXBarrierContents)
    val out = Output(new MEMBarrierContents)
    val lastMemValue = Output(UInt(32.W))
  })


  val DMEM = Module(new DMEM)


  /**
    * Setup. You should not change this code
    */
  DMEM.testHarness.setup  := testHarness.DMEMsetup
  testHarness.DMEMpeek    := DMEM.io.dataOut
  testHarness.testUpdates := DMEM.testHarness.testUpdates

  import LswByteType._
  val readWriteByteMaskMap = Array(
    //LSW4 -> 0xFFFFFFFF.U(32.W), // Error: UInt literal -1 is negative...?
    LSW4 -> -1.S(32.W).asTypeOf(UInt(32.W)),
    LSW2 -> 0x0000FFFF.U(32.W),
    LSW1 -> 0x000000FF.U(32.W),
    LSWD -> 0x00000000.U(32.W),
  )

  val readWriteByteMask = MuxLookup(io.in.lswByte, -1.S(32.W).asTypeOf(UInt(32.W)), readWriteByteMaskMap)
  val readWriteByteMaskReg = RegNext(readWriteByteMask)

  val i = RegInit(0.U(32.W))
  val memDelayReg = RegInit(false.B)

  val launched = io.in.PC.asTypeOf(SInt(32.W)) >= 0.S

  // 默认为不读不写状态
  DMEM.io.dataIn      := 0.U
  DMEM.io.dataAddress := io.in.writeData
  DMEM.io.writeEnable := false.B

  io.out.writeData := 0x5A.U
  io.out.writeReg := io.in.writeReg
  io.out.writeAddress := io.in.writeAddress

  io.lastMemValue := 0.U

  io.out.readDelaySignal := true.B

  when (!io.in.readEnable) {
    io.out.readDelaySignal := false.B
    io.out.writeData := io.in.writeData
    DMEM.io.writeEnable := Mux(io.in.writeReg, 0.U, io.in.writeEnable)

    when (io.in.writeEnable) {
      i := i + 1.U
      DMEM.io.dataIn      := io.in.regData
      DMEM.io.dataAddress := io.in.writeData & readWriteByteMask

      when (launched && !Constants.mute) {
        // printf("[%d] ls M(0x%x)=0x__ <- 0x%x\n", i, io.in.writeData, io.in.regData)
      }
    } .otherwise {
      DMEM.io.dataIn      := io.in.writeData
      DMEM.io.dataAddress := io.in.writeAddress
      
      when (launched && !Constants.mute) {
        when (io.in.writeReg) {
          // printf("[%d] xx reg(0x%x) := 0x%x\n", i, io.in.writeAddress, io.in.writeData)
        } .otherwise {
          // printf("[%d] xx 0x%x -> 0x%x\n", i, io.in.writeData, io.in.writeAddress)
        }
      }

    }
  } .otherwise {
    i := i + 1.U
    memDelayReg := true.B
    when (launched) {
      // printf("[%d] lw M(0x%x)=0x__ -> 0x%x\n", i, io.in.writeData, io.in.writeAddress)
    }
  }

  when(memDelayReg && !io.in.readEnable) {
    io.lastMemValue := DMEM.io.dataOut & readWriteByteMaskReg
    memDelayReg := false.B
    io.out.readDelaySignal := false.B
  }

  io.out.PC     := io.in.PC
}

