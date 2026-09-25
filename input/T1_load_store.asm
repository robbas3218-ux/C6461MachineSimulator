; T1 - Load/Store instructions and all four addressing modes
; Tests: LDR (direct, indirect, indexed, indexed-indirect), STR, LDA, LDX, STX
; Start PC: 14 (000016 octal)
LOC 6
V10:    Data 10       ; loc 6:  test value 10
V20:    Data 20       ; loc 7:  test value 20
PTR:    Data V10      ; loc 8:  pointer to V10 (= 6)
BASE:   Data 6        ; loc 9:  index base value
SCR1:   Data 0        ; loc 10: scratch (STR target)
SCR2:   Data 0        ; loc 11: scratch (indexed STR target)
SCR3:   Data 0        ; loc 12: scratch (STX target)
LOC 14
Start:  LDR 0,0,6     ; direct:            R0 <- c(6) = 10
        LDR 1,0,8,1   ; indirect:          R1 <- c(c(8)) = c(6) = 10
        LDX 2,9       ; load index:        X2 <- c(9) = 6
        LDR 2,2,1     ; indexed:           R2 <- c(X2+1) = c(7) = 20
        LDR 3,2,2,1   ; indexed indirect:  R3 <- c(c(X2+2)) = c(c(8)) = 10
        LDA 3,0,31    ; load address:      R3 <- EA = 31
        STR 0,0,10    ; direct store:      c(10) <- 10
        STX 2,12      ; store index:       c(12) <- 6
        STR 1,2,5     ; indexed store:     c(X2+5) = c(11) <- 10
        HLT           ; expected: R0=10 R1=10 R2=20 R3=31 X2=6
