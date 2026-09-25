; T3 - Transfer instructions
; Tests: JZ, JNE, JMA, SOB (loop), JGE, TRR+JCC (condition code 3 = equal)
; Any HLT before location 28 means a branch failed - the failing address
; identifies which one.  Start PC: 12 (000014 octal)
LOC 6
ZERO:   Data 0        ; loc 6
ONE:    Data 1        ; loc 7
FIVE:   Data 5        ; loc 8
RES:    Data 0        ; loc 9: set to 1 only if all branch tests pass
LOC 12
Start:  LDR 0,0,6     ; 12: R0 <- 0
        JZ 0,0,15     ; 13: taken (R0=0) -> 15
        HLT           ; 14: FAIL - JZ not taken
        LDR 1,0,7     ; 15: R1 <- 1
        JNE 1,0,18    ; 16: taken (R1!=0) -> 18
        HLT           ; 17: FAIL - JNE not taken
        JMA 0,20      ; 18: unconditional -> 20
        HLT           ; 19: FAIL - JMA not taken
        LDR 2,0,8     ; 20: R2 <- 5 (loop counter)
Loop:   AIR 1,2       ; 21: R1 <- R1+2 (runs 5 times)
        SOB 2,0,21    ; 22: decrement R2, loop while > 0
        JGE 2,0,25    ; 23: taken (R2=0 >= 0) -> 25
        HLT           ; 24: FAIL - JGE not taken
        TRR 1,1       ; 25: R1==R1 so cc(4) EQUALORNOT <- 1
        JCC 3,0,28    ; 26: taken (cc bit 3 set) -> 28
        HLT           ; 27: FAIL - JCC not taken
        LDR 3,0,7     ; 28: R3 <- 1 (pass marker)
        STR 3,0,9     ; 29: RES <- 1
        HLT           ; 30: PASS halt: R1=11 R2=0 R3=1 c(9)=1
