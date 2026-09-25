; T2 - Arithmetic instructions
; Tests: AMR, SMR, AIR (incl. into empty register), SIR (negative result),
;        overflow behavior on AIR at +32767
; Start PC: 12 (000014 octal)
LOC 6
A:      Data 25       ; loc 6
B:      Data 7        ; loc 7
BIG:    Data 32767    ; loc 8: largest positive 16-bit value (077777)
SCR:    Data 0        ; loc 9
LOC 12
Start:  LDR 0,0,6     ; R0 <- 25
        AMR 0,0,7     ; R0 <- 25+7 = 32  (000040)
        SMR 0,0,7     ; R0 <- 32-7 = 25  (000031)
        AIR 0,10      ; R0 <- 25+10 = 35 (000043)
        SIR 0,31      ; R0 <- 35-31 = 4  (000004)
        AIR 1,15      ; R1 (empty) <- 15 (000017)
        SIR 2,5       ; R2 <- 0-5 = -5   (177773)
        LDR 3,0,8     ; R3 <- 32767
        AIR 3,1       ; R3 <- 32768: OVERFLOW, wraps to -32768 (100000)
        HLT           ; expected: R0=4 R1=15 R2=-5 R3=100000 CC OVF=1
