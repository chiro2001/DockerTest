package FiveStage
import chisel3._
import chisel3.util._
import ALUOps._

class Execute extends Module {
  val io = IO(new Bundle {
    val in = Input(new IDBarrierContents)
    val out = Output(new EXBarrierContents)
  })
  val a = io.in.op1
  val b = io.in.op2
  val ALUopMap = Array(
    ADD   -> (a + b),
    SUB   -> (a - b),
    AND   -> (a & b),
    OR    -> (a | b),
    XOR   -> (a ^ b),
    SLL   -> (a << b(4, 0)),
    SRL   -> (a >> b(4, 0)),
    SRA   -> (a.asSInt >> b(4, 0)).asUInt,
    SLT   -> (a.asSInt < b.asSInt),
    SLTU  -> (a < b),
    COPY_A-> a,
    COPY_B-> b,
    DC    -> 0.U
  )
  val aluResult = MuxLookup(io.in.aluOp, 0.U(32.W), ALUopMap)
  io.out.writeData    := aluResult
  io.out.writeAddress := io.in.writeAddress
  io.out.writeEnable  := io.in.writeEnable
  io.out.readEnable   := io.in.readEnable
  io.out.writeReg     := io.in.writeReg && io.in.writeAddress =/= 0.U
  io.out.regData      := io.in.regData
  io.out.PC           := io.in.PC
  io.out.lswByte      := io.in.lswByte
}