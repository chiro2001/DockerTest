main:
	addi x1, zero, 4
	addi x2, zero, 4
	addi x3, zero, 4
	addi x4, zero, 4
	lw x3, 0(x3)
	sw x3, 0(x1)
	lw x3, 0(x3)
	sw x1, 0(x2)
  lw x3, 0(x3)
	sw x2, 0(x2)
  lw x3, 0(x3)
	nop
	done
#memset 0x0,  4
#memset 0x4,  8
#memset 0x8,  12
#memset 0xc,  16
#memset 0x10, 20
