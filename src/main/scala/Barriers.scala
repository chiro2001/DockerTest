package FiveStage
import chisel3._

class IFBarrierContents extends Bundle {
  val PC = UInt(32.W)
  val instruction = UInt(32.W)
}

class IDBarrierContents extends Bundle {
  val PC = UInt(32.W)
  val op1 = UInt(32.W)
  val op2 = UInt(32.W)
  val aluOp = UInt(4.W)
  val regData = UInt(32.W)
  val writeAddress = UInt(12.W)
  val writeEnable = Bool()
  val readEnable = Bool()
  val writeReg = Bool()
  val lswByte = UInt(3.W)
}

class EXBarrierContents extends Bundle {
  val PC = UInt(32.W)
  val regData = UInt(32.W)
  val writeAddress = UInt(12.W)
  val writeData = UInt(32.W)
  val writeEnable = Bool()
  val readEnable = Bool()
  val writeReg = Bool()
  val lswByte = UInt(3.W)
}

class MEMBarrierContents extends Bundle {
  val PC = UInt(32.W)
  val writeData = UInt(32.W)
  val readDelaySignal = Bool()
  val writeReg = Bool()
  val writeAddress = UInt(5.W)
}

class WriteBackBarrierContents extends Bundle {
  val PC = UInt(32.W)
  val writeData = UInt(32.W)
  val writeEnable = Bool()
  val writeAddress = UInt(5.W)
}

class IDBarrier   extends BaseBarrier(new IDBarrierContents)  {}
class EXBarrier   extends BaseBarrier(new EXBarrierContents)  {}

class BaseBarrier(
      getBarrierContents: => Bundle
    ) extends Module {
  val io = IO(new Bundle {
    val in = Input(getBarrierContents)
    val out = Output(getBarrierContents)
  })
  val regs = Reg(getBarrierContents)

  regs   := io.in
  io.out := regs
}

class MEMBarrier extends Module {
  val io = IO(new Bundle {
    val in = Input(new MEMBarrierContents)
    val out = Output(new MEMBarrierContents)
    val lastMemValue = Input(UInt(32.W))
  })
  val regs = Reg(new MEMBarrierContents)
  val waitNext = RegInit(false.B)
  when (io.in.readDelaySignal === true.B) {
    waitNext := true.B
  }

  regs   := io.in
  io.out := regs

  when(waitNext && !io.in.readDelaySignal) {
    io.out.writeData := io.lastMemValue
    waitNext := false.B
  } .otherwise {
    io.out.writeData := regs.writeData
  }
}
