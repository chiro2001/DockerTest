package FiveStage
import chisel3._
import chisel3.util._
import chisel3.experimental.MultiIOModule


class InstructionDecode extends MultiIOModule {

  // Don't touch the test harness
  val testHarness = IO(
    new Bundle {
      val registerSetup = Input(new RegisterSetupSignals)
      val registerPeek  = Output(UInt(32.W))

      val testUpdates   = Output(new RegisterUpdates)
    })


  val io = IO(
    new Bundle {
      val in = Input(new IFBarrierContents)
      val out = Output(new IDBarrierContents)
      val writeBack = Input(new WriteBackBarrierContents)
      val ex = Input(new EXBarrierContents)
      val mem = Input(new MEMBarrierContents)
      val stall = Output(Bool())
      val regSourceOp1 = Output(UInt(8.W))
      val regSourceOp2 = Output(UInt(8.W))
      val regDataSource = Output(UInt(8.W))
      val nextPC = Output(UInt(32.W))
      val toJump = Output(Bool())
      val op1SetValue = Output(UInt(32.W))
      val op2SetValue = Output(UInt(32.W))
      val stopped = Output(Bool())
      val jumpNeedWriteRegister = Output(Bool())
    }
  )

  val registers = Module(new Registers)
  val decoder   = Module(new Decoder).io


  /**
    * Setup. You should not change this code
    */
  registers.testHarness.setup := testHarness.registerSetup
  testHarness.registerPeek    := registers.io.out.readData1
  testHarness.testUpdates     := registers.testHarness.testUpdates


  val launched = io.in.PC.asTypeOf(SInt(32.W)) >= 0.S
  
  val stalled = RegInit(false.B)
  val stalled2 = RegInit(false.B)
  val instructionLast = RegInit(0.U(32.W))

  val instructionNow = Mux(stalled, instructionLast, io.in.instruction)
  val stopped = RegInit(false.B)
  io.stopped := stopped
  val stopping = (instructionNow === 0x13.U || instructionNow === 0x00.U) &&
    launched && io.in.PC =/= 0.U && !stalled

  when (stopping && !Constants.mute) {
    printf("    %%%%%%%% STOPPING CPU %%%%%%%%\n")
    stopped := true.B
  }
  
  val regSourceOp1 = io.regSourceOp1
  val regSourceOp2 = io.regSourceOp2
  val regDataSource = io.regDataSource

  class LastInstructionContents {
    val regSourceOp1 = RegInit(0.U(8.W))
    val regSourceOp2 = RegInit(0.U(8.W))
    val writeAddress = RegInit(0.U(5.W))
    val memRead      = RegInit(false.B)
  }

  // 记录上两条指令信息
  val lastInstruction = new LastInstructionContents
  val lastInstruction2 = new LastInstructionContents

  val writeAddress = Mux(decoder.controlSignals.regWrite && launched, decoder.instruction.registerRd, 0.U)

  lastInstruction.regSourceOp1 := regSourceOp1
  lastInstruction.regSourceOp2 := regSourceOp2
  lastInstruction.writeAddress := writeAddress
  lastInstruction.memRead      := decoder.controlSignals.memRead

  // 自建class没有 <>
  lastInstruction2.regSourceOp1 := lastInstruction.regSourceOp1
  lastInstruction2.regSourceOp2 := lastInstruction.regSourceOp2
  lastInstruction2.writeAddress := lastInstruction.writeAddress
  lastInstruction2.memRead := lastInstruction.memRead

  decoder.instruction := instructionNow.asTypeOf(new Instruction)
  when (instructionLast =/= 0.U && stalled2 === 0.U) {
    instructionLast := 0.U
  }

  import Op1Select._
  import Op2Select._
  import ImmFormat._
  import ImmExtension._
  import LswByteType._

  // 取立即数类型
  val immMap = Array(
    ITYPE -> decoder.instruction.immediateIType,
    STYPE -> decoder.instruction.immediateSType,
    BTYPE -> decoder.instruction.immediateBType,
    UTYPE -> decoder.instruction.immediateUType,
    JTYPE -> decoder.instruction.immediateJType,
    IMFDC -> 0.S(12.W)
  )

  // 标明到Ex的运算的数据来源
  object OpSourceType {
    val imm             = 0.U(8.W)
    val reg             = 1.U(8.W)
    val ex              = 2.U(8.W)
    val mem             = 3.U(8.W)
    val writeBack       = 4.U(8.W)
    val n               = 0xFC.U(8.W)
    val pc              = 0xFD.U(8.W)
    val enabled         = 0xFE.U(8.W)
    val disabled        = 0xFF.U(8.W)
  }

  val immediateValue = MuxLookup(decoder.immType, 0.S(12.W), immMap)

  // 立即数符号扩展
  def sext(value: SInt, width: Int = 32) = value.asTypeOf(SInt(width.W)).asUInt
  def uext(value: SInt, width: Int = 32) = value.asUInt.asTypeOf(UInt(width.W))
  def noExt(value: SInt) = value.asUInt
  def rs1Ext(value: SInt, width: Int = 32) = Mux(
    registers.io.out.readData1.asSInt() > 0.S,
    uext(value),
    // TODO: -value
    sext(value)
  ).asUInt

  val extensionMap = Array(
    ImmExtension.NEXT -> noExt(immediateValue),
    SEXT              -> sext(immediateValue),
    UEXT              -> uext(immediateValue),
    REXT              -> rs1Ext(immediateValue)
  )

  val immRealValue = MuxLookup(decoder.immExt, -1.S(32.W).asUInt, extensionMap)

  io.out.aluOp := Mux(io.stall, 0.U, decoder.ALUop)

  // 需要读取的寄存器的地址
  val regReadAddressReal1 = decoder.instruction.registerRs1
  val regReadAddressReal2 = decoder.instruction.registerRs2
  val regReadAddress1 = Mux(decoder.op1Select === rs1, decoder.instruction.registerRs1,
    Mux(decoder.op1Select === Op1Select.PC, OpSourceType.pc, 0.U))
  val regReadAddress2 = Mux(decoder.immType === ImmFormat.STYPE,
                      decoder.instruction.registerRs2,
                      Mux(decoder.op2Select === rs2, decoder.instruction.registerRs2, 0.U))

  def stallFor1() = {
    stalled := true.B
    instructionLast := io.in.instruction
    io.stall := true.B
  }

  def stallFor2() = {
    stalled := true.B
    stalled2 := true.B
    instructionLast := io.in.instruction
    io.stall := true.B
  }

  io.stall := false.B
  when (stalled2) {
    io.stall := true.B
    stalled2 := false.B
  } .otherwise {
    when ((launched &&
      ((regReadAddress1 === lastInstruction2.writeAddress &&
        regReadAddress1 =/= 0.U &&
        regSourceOp1 === OpSourceType.mem) ||
        (regReadAddress2 === lastInstruction2.writeAddress &&
          regReadAddress2 =/= 0.U &&
          (regSourceOp2 === OpSourceType.mem || regDataSource === OpSourceType.mem))
        ) &&
      lastInstruction2.memRead =/= 0.U) ||
      regSourceOp1 === OpSourceType.disabled ||
      regSourceOp2 === OpSourceType.disabled
    ) {
      stallFor1()
    } .otherwise {
      when (launched &&
        ((regReadAddress1 === lastInstruction.writeAddress &&
          regReadAddress1 =/= 0.U &&
          regSourceOp1 === OpSourceType.ex) ||
          (regReadAddress2 === lastInstruction.writeAddress &&
            regReadAddress2 =/= 0.U &&
            (regSourceOp2 === OpSourceType.ex || regDataSource === OpSourceType.ex))) &&
        lastInstruction.memRead =/= 0.U
            ) {
        stallFor2()
      }
    }
    when (decoder.controlSignals.memRead && lastInstruction.memRead && !stalled) {
      stallFor1()
    }
  }

  when (stopped) {
    io.stall := true.B
  }

  when (stalled && io.stall === 0.U && !stalled2) {
    stalled := false.B
  }

  def findOpSource(registerReadOut: UInt, op: UInt, regSourceOp: UInt, rs: UInt, opSelect: UInt, registerRs: UInt, immValue: UInt) = {
    op := registerReadOut
    regSourceOp := OpSourceType.reg
    when (opSelect =/= rs) {
      op := immValue
      regSourceOp := OpSourceType.imm
      when (opSelect === Op1Select.PC) {
        op := io.in.PC
        regSourceOp := OpSourceType.pc
      }
      when (opSelect === Op1Select.OP1_N || opSelect === Op2Select.OP2_N) {
        regSourceOp := OpSourceType.n
      }
      when (opSelect === Op1Select.OP1_DC || opSelect === Op2Select.OP2_DC) {
        regSourceOp := OpSourceType.disabled
      }
    } .otherwise {
      when (io.writeBack.writeEnable &&
            io.writeBack.writeAddress === registerRs) {
        op := io.writeBack.writeData
        regSourceOp := OpSourceType.writeBack
      }
      when (io.mem.writeReg &&
            io.mem.writeAddress === registerRs) {
        op := io.mem.writeData
        regSourceOp := OpSourceType.mem
        when (io.mem.readDelaySignal) {
          op := 0x55.U
          regSourceOp := OpSourceType.disabled
        }
      }
      when (io.ex.writeReg &&
            io.ex.writeAddress === registerRs) {
        op := io.ex.writeData
        regSourceOp := OpSourceType.ex
      }
      when (registerRs === 0.U) {
        op := 0.U
        regSourceOp := OpSourceType.reg
      }
    }
  }

  val op1SetValue = io.op1SetValue
  val op2SetValue = io.op2SetValue

  findOpSource(registers.io.out.readData1, op1SetValue, regSourceOp1, rs1, decoder.op1Select, decoder.instruction.registerRs1, immRealValue)
  findOpSource(registers.io.out.readData2, op2SetValue, regSourceOp2, rs2, decoder.op2Select, decoder.instruction.registerRs2, immRealValue)
  findOpSource(registers.io.out.readData2, io.out.regData, regDataSource, rs2, rs2, decoder.instruction.registerRs2, 0.U)

  val a = op1SetValue
  val b = op2SetValue

  io.out.op1 := a
  io.out.op2 := b

  val branchTypeMap = Array(
    branchType.beq -> (a === b),
    branchType.neq -> (a =/= b),
    branchType.gte -> (a.asSInt >= b.asSInt),
    branchType.gteu -> (a >= b),
    branchType.lt -> (a.asSInt < b.asSInt),
    branchType.ltu -> (a < b)
  )

  registers.io.in.readAddress1 := regReadAddressReal1
  registers.io.in.readAddress2 := regReadAddressReal2

  registers.io.in.writeEnable  := false.B
  registers.io.in.writeAddress := 0.U
  registers.io.in.writeData    := 0.U
  when (io.writeBack.writeEnable && io.writeBack.writeAddress =/= 0.U) {
    registers.io.in.writeEnable  := true.B
    registers.io.in.writeAddress := io.writeBack.writeAddress
    registers.io.in.writeData    := io.writeBack.writeData
  }
  io.out.writeAddress := writeAddress
  io.out.writeEnable  := decoder.controlSignals.memWrite
  io.out.readEnable   := decoder.controlSignals.memRead
  io.out.writeReg     := decoder.controlSignals.regWrite
  io.out.lswByte      := decoder.lswByte

  def clearSomeOutput() = {
    io.out.op1          := 0.U
    io.out.op2          := 0.U
    io.out.aluOp        := ALUOps.DC
    io.out.regData      := 0.U
    io.out.writeAddress := 0.U
    io.out.writeEnable  := false.B
    io.out.readEnable   := false.B
    io.out.writeReg     := false.B
    io.out.lswByte      := 0.U
  }

  val nextPC = sext(immediateValue) +
    Mux(regSourceOp1 === OpSourceType.pc || decoder.controlSignals.branch, io.in.PC, op1SetValue)
  //val nextPC = sext(immediateValue) + io.in.PC
  val needJump = decoder.controlSignals.jump && launched
  io.jumpNeedWriteRegister := needJump
  io.toJump := needJump
  io.nextPC := 0.U
  when (decoder.controlSignals.branch) {
    io.toJump := MuxLookup(decoder.branchType, false.B, branchTypeMap)
    io.jumpNeedWriteRegister := false.B
  }
  when (io.toJump) {
    io.nextPC := nextPC
    when (io.jumpNeedWriteRegister) {
      // 解决写入前后数据依赖冲突
      when (io.ex.writeReg || io.mem.writeReg || io.writeBack.writeEnable) {
        stallFor1()
      } .otherwise {
        registers.io.in.writeEnable  := true.B
        registers.io.in.writeAddress := decoder.instruction.registerRd
        registers.io.in.writeData    := io.in.PC + 4.U
        clearSomeOutput()
      }
    }
  }

  // 再跳到 <0 的地方也表示停机
  when (launched && io.nextPC.asTypeOf(SInt(32.W)) < 0.S) {
    stopped := true.B
  }

  when (io.stall || !launched) {
    clearSomeOutput()
  }

  when (launched && !stopped && !Constants.mute) {
    printf("[0x%x] imm: 0x%x, rs1S: %d, rs2S: %d, memRead: %d, memWrite: %d, op1src: %d, stall: %d, stalled: %d, INS: 0x%x\n",
            io.in.PC.asUInt, immediateValue, (decoder.op1Select === rs1).asUInt, (decoder.op2Select === rs2).asUInt,
            decoder.controlSignals.memRead.asUInt, decoder.controlSignals.memWrite.asUInt, regSourceOp1, io.stall, stalled,
            decoder.instruction.instruction)
  }

  io.out.PC := io.in.PC
}
