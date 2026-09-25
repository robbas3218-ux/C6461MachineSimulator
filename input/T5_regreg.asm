; T5 - Register-to-register instructions
; Tests: MLT (result pair rx,rx+1), DVD (quotient/remainder), divide-by-zero
;        (DIVZERO cc), TRR (equal flag), AND, ORR, NOT
; Start PC: 14 (000016 octal)
LOC 6
D6:     Data 6        ; loc 6
D7:     Data 7        ; loc 7
D23:    Data 23       ; loc 8
D5:     Data 5        ; loc 9
MASK:   Data 240      ; loc 10: 0000000011110000
PATT:   Data 85       ; loc 11: 0000000001010101
ZERO:   Data 0        ; loc 12
LOC 14
Start:  LDR 0,0,6     ; R0 <- 6
        LDR 2,0,7     ; R2 <- 7
        MLT 0,2       ; R0:R1 <- 6*7 = 42: R0=0 (high) R1=42 (000052)
        LDR 0,0,8     ; R0 <- 23
        LDR 2,0,9     ; R2 <- 5
        DVD 0,2       ; R0 <- 4 (quotient), R1 <- 3 (remainder)
        LDR 2,0,10    ; R2 <- 240
        LDR 3,0,11    ; R3 <- 85
        AND 2,3       ; R2 <- 240 AND 85 = 80 (000120)
        ORR 2,3       ; R2 <- 80 OR 85 = 85  (000125)
        NOT 3         ; R3 <- NOT 85 = 177652
        TRR 2,3       ; 85 != NOT 85: cc(4) EQUALORNOT <- 0
        TRR 1,1       ; equal: cc(4) <- 1
        LDR 0,0,6     ; R0 <- 6
        LDR 2,0,12    ; R2 <- 0
        DVD 0,2       ; divide by zero: DIVZERO cc <- 1, R0/R1 unchanged
        HLT           ; expected: R0=6 R1=3 R2=0 R3=177652 CC DIVZ=1 EQ=1
