package FiveStage
import chisel3._
import chisel3.util.BitPat
import chisel3.util.ListLookup


/**
  * This module is mostly done, but you will have to fill in the blanks in opcodeMap.
  * You may want to add more signals to be decoded in this module depending on your
  * design if you so desire.
  *
  * In the "classic" 5 stage decoder signals such as op1select and immType
  * are not included, however I have added them to my design, and similarily you might
  * find it useful to add more
 */
class Decoder() extends Module {

  val io = IO(new Bundle {
                val instruction    = Input(new Instruction)

                val controlSignals = Output(new ControlSignals)
                val branchType     = Output(UInt(3.W))
                val op1Select      = Output(UInt(3.W))
                val op2Select      = Output(UInt(3.W))
                val immType        = Output(UInt(3.W))
                val immExt         = Output(UInt(3.W))
                val lswByte        = Output(UInt(3.W))
                val ALUop          = Output(UInt(4.W))
              })

  import lookup._
  import Op1Select._
  import Op2Select._
  import branchType._
  import ImmFormat._

  val N = 0.asUInt(1.W)
  val Y = 1.asUInt(1.W)

  import ImmExtension._
  import LswByteType._

  /**
    * In scala we sometimes (ab)use the `->` operator to create tuples.
    * The reason for this is that it serves as convenient sugar to make maps.
    *
    * This doesn't matter to you, just fill in the blanks in the style currently
    * used, I just want to demystify some of the scala magic.
    *
    * `a -> b` == `(a, b)` == `Tuple2(a, b)`
    */
  val opcodeMap: Array[(BitPat, List[UInt])] = Array(
    // 指令     写寄存器   读内存   写内存   含分支判断 含跳转
    // signal   regWrite, memRead, memWrite, branch,    jump,
    //                            分支类型    ex.op1源   ex.op2源 立即数类型 立即数扩展类型
    //                           branchType, Op1Select, Op2Select, ImmType, ImmExtension
    //                                                       内存操作字节数 计算类型
    //                                                       LswByte, ALUOp
    // x[rd] = sext(M[x[rs1] + sext(offset)][31:0])
    LW    -> List(Y, Y, N, N, N, BTDC, rs1,    imm, ITYPE, SEXT, LSW4, ALUOps.ADD),
    // x[rd] = sext(M[x[rs1] + sext(offset)][7:0])
    LB    -> List(Y, Y, N, N, N, BTDC, rs1,    imm, ITYPE, SEXT, LSW1, ALUOps.ADD),
    // x[rd] = sext(M[x[rs1] + sext(offset)][15:0])
    LH    -> List(Y, Y, N, N, N, BTDC, rs1,    imm, ITYPE, SEXT, LSW2, ALUOps.ADD),
    // x[rd] = M[x[rs1] + sext(offset)][7:0]
    LBU   -> List(Y, Y, N, N, N, BTDC, rs1,    imm, ITYPE, UEXT, LSW1, ALUOps.ADD),
    // x[rd] = M[x[rs1] + sext(offset)][15:0]
    LHU   -> List(Y, Y, N, N, N, BTDC, rs1,    imm, ITYPE, UEXT, LSW4, ALUOps.ADD),
    // M[x[rs1] + sext(offset)] = x[rs2][7: 0]
    SB    -> List(N, N, Y, N, N, BTDC, rs1,    imm, STYPE, SEXT, LSW1, ALUOps.ADD),
    // M[x[rs1] + sext(offset)] = x[rs2][15: 0]
    SH    -> List(N, N, Y, N, N, BTDC, rs1,    imm, STYPE, SEXT, LSW2, ALUOps.ADD),
    // M[x[rs1] + sext(offset)] = x[rs2][31: 0]
    SW    -> List(N, N, Y, N, N, BTDC, rs1,    imm, STYPE, SEXT, LSW4, ALUOps.ADD),
    ADD   -> List(Y, N, N, N, N, BTDC, rs1,    rs2, IMFDC, NEXT, LSWD, ALUOps.ADD),
    SUB   -> List(Y, N, N, N, N, BTDC, rs1,    rs2, IMFDC, NEXT, LSWD, ALUOps.SUB),
    AND   -> List(Y, N, N, N, N, BTDC, rs1,    rs2, IMFDC, NEXT, LSWD, ALUOps.AND),
    OR    -> List(Y, N, N, N, N, BTDC, rs1,    rs2, IMFDC, NEXT, LSWD, ALUOps.OR),
    XOR   -> List(Y, N, N, N, N, BTDC, rs1,    rs2, IMFDC, NEXT, LSWD, ALUOps.XOR),
    SLT   -> List(Y, N, N, N, N, BTDC, rs1,    rs2, IMFDC, NEXT, LSWD, ALUOps.SLT),
    SLTU  -> List(Y, N, N, N, N, BTDC, rs1,    rs2, IMFDC, NEXT, LSWD, ALUOps.SLTU),
    SRA   -> List(Y, N, N, N, N, BTDC, rs1,    rs2, IMFDC, REXT, LSWD, ALUOps.SRA),
    SRL   -> List(Y, N, N, N, N, BTDC, rs1,    rs2, IMFDC, NEXT, LSWD, ALUOps.SRL),
    SLL   -> List(Y, N, N, N, N, BTDC, rs1,    rs2, IMFDC, NEXT, LSWD, ALUOps.SLL),
    ADDI  -> List(Y, N, N, N, N, BTDC, rs1,    imm, ITYPE, SEXT, LSWD, ALUOps.ADD),
    ANDI  -> List(Y, N, N, N, N, BTDC, rs1,    imm, ITYPE, SEXT, LSWD, ALUOps.AND),
    ORI   -> List(Y, N, N, N, N, BTDC, rs1,    imm, ITYPE, SEXT, LSWD, ALUOps.OR),
    XORI  -> List(Y, N, N, N, N, BTDC, rs1,    imm, ITYPE, SEXT, LSWD, ALUOps.XOR),
    SLTIU -> List(Y, N, N, N, N, BTDC, rs1,    imm, ITYPE, SEXT, LSWD, ALUOps.SLTU),
    SLTI  -> List(Y, N, N, N, N, BTDC, rs1,    imm, ITYPE, SEXT, LSWD, ALUOps.SLT),
    SRAI  -> List(Y, N, N, N, N, BTDC, rs1,    imm, ITYPE, REXT, LSWD, ALUOps.SRA),
    SRLI  -> List(Y, N, N, N, N, BTDC, rs1,    imm, ITYPE, NEXT, LSWD, ALUOps.SRL),
    SLLI  -> List(Y, N, N, N, N, BTDC, rs1,    imm, ITYPE, NEXT, LSWD, ALUOps.SLL),
    LUI   -> List(Y, N, N, N, N, BTDC, OP1_N,  imm, UTYPE, SEXT, LSWD, ALUOps.COPY_B),
    AUIPC -> List(Y, N, N, N, N, BTDC, PC,     imm, UTYPE, SEXT, LSWD, ALUOps.ADD),
    JAL   -> List(Y, N, N, N, Y, BTDC, PC,     imm, JTYPE, SEXT, LSWD, ALUOps.DC),
    JALR  -> List(Y, N, N, N, Y, BTDC, rs1,    imm, ITYPE, SEXT, LSWD, ALUOps.DC),
    BLT   -> List(N, N, N, Y, Y, lt,   rs1,    rs2, BTYPE, SEXT, LSWD, ALUOps.DC),
    BLTU  -> List(N, N, N, Y, Y, ltu,  rs1,    rs2, BTYPE, SEXT, LSWD, ALUOps.DC),
    BEQ   -> List(N, N, N, Y, Y, beq,  rs1,    rs2, BTYPE, SEXT, LSWD, ALUOps.DC),
    BNE   -> List(N, N, N, Y, Y, neq,  rs1,    rs2, BTYPE, SEXT, LSWD, ALUOps.DC),
    BGE   -> List(N, N, N, Y, Y, gte,  rs1,    rs2, BTYPE, SEXT, LSWD, ALUOps.DC),
    BGEU  -> List(N, N, N, Y, Y, gteu, rs1,    rs2, BTYPE, SEXT, LSWD, ALUOps.DC),
)

  val NOP =  List(N, N, N, N, N, BTDC, rs1, rs2, SEXT, LSWD, IMFDC, ALUOps.DC)

  val decodedControlSignals = ListLookup(
    io.instruction.asUInt(),
    NOP,
    opcodeMap)

  io.controlSignals.regWrite   := decodedControlSignals(0)
  io.controlSignals.memRead    := decodedControlSignals(1)
  io.controlSignals.memWrite   := decodedControlSignals(2)
  io.controlSignals.branch     := decodedControlSignals(3)
  io.controlSignals.jump       := decodedControlSignals(4)

  io.branchType := decodedControlSignals(5)
  io.op1Select  := decodedControlSignals(6)
  io.op2Select  := decodedControlSignals(7)
  io.immType    := decodedControlSignals(8)
  io.immExt     := decodedControlSignals(9)
  io.lswByte    := decodedControlSignals(10)
  io.ALUop      := decodedControlSignals(11)
}
