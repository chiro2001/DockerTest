package FiveStage
import chisel3._
import chisel3.experimental.MultiIOModule

class InstructionFetch extends MultiIOModule {

  // Don't touch
  val testHarness = IO(
    new Bundle {
      val IMEMsetup = Input(new IMEMsetupSignals)
      val PC        = Output(UInt())
    }
  )


  /**
    * The instruction is of type Bundle, which means that you must
    * use the same syntax used in the testHarness for IMEM setup signals
    * further up.
    */
  val io = IO(
    new Bundle {
      val out = Output(new IFBarrierContents)
      val stall = Input(Bool())
      val jump = Input(Bool())
      val nextPC = Input(UInt(32.W))
      val stopped = Input(Bool())
    })

  val IMEM = Module(new IMEM)
  val PC   = RegInit(0.U(32.W))


  /**
    * Setup. You should not change this code
    */
  IMEM.testHarness.setupSignals := testHarness.IMEMsetup
  // 探测PC延迟n周期😫
  val testPC = IMEM.testHarness.requestedAddress - Constants.shiftEarlier
  testHarness.PC := Mux(testPC.asTypeOf(SInt(32.W)) < 0.S, 0.U, testPC)
  
  val PCInit = (-8).S(32.W).asTypeOf(UInt(32.W))
  val launched = PCInit =/= PC

  val nextPC = io.nextPC
  io.out.PC := PC
  val instructionReadAddress = Mux(io.jump, nextPC, PC + 4.U)
  val instructionReadAddressReg = RegInit(0.U(32.W))
  instructionReadAddressReg := instructionReadAddress
  IMEM.io.instructionAddress := instructionReadAddress
  val lastInstruction = RegInit(0.U(32.W))
  val stalled = RegInit(false.B)
  stalled := io.stall
  io.out.instruction := Mux(launched, IMEM.io.instruction, 0x33.U)
  when (stalled) {
    io.out.instruction := lastInstruction
  }
  lastInstruction := io.out.instruction

  val PCOffset = Mux(io.stall, 0.S(32.W), 4.S(32.W))
  when (io.stall && launched && !Constants.mute && !io.stopped) {
    printf("    ==== STALL : [0x%x] pcoffset: %d =====\n", PC, PCOffset)
  }
  // PC := Mux(io.jump, io.nextPC, (PC.asSInt + PCOffset).asUInt)
  PC := Mux(io.jump && !io.stall, io.nextPC, (PC.asSInt + PCOffset).asUInt)

  when (io.jump && launched && !Constants.mute && !io.stopped) {
    printf("   $$ IF jumping to 0x%x $$\n", nextPC)
  }

  /**
    * Setup. You should not change this code.
    */
  // 这里才是系统初始化位置
  when(testHarness.IMEMsetup.setup) {
    PC := PCInit
  }
}
