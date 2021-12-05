package FiveStage
import chisel3._
import chisel3.util._
import ALUOps._

class WriteBack extends Module {
  val io = IO(new Bundle {
    val in = Input(new MEMBarrierContents)
    val out = Output(new WriteBackBarrierContents)
  })
  io.out.writeData    := io.in.writeData
  io.out.writeEnable  := io.in.writeReg
  io.out.writeAddress := io.in.writeAddress

  io.out.PC   := io.in.PC
}