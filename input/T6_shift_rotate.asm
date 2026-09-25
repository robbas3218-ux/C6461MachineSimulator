; T6 - Shift and rotate instructions
; Tests: SRC logical left/right, SRC arithmetic right (sign extension),
;        RRC rotate right and left (bit wraps around)
; Start PC: 10 (000012 octal)
LOC 6
D3:     Data 3        ; loc 6
D1:     Data 1        ; loc 7
NEG16:  Data -16      ; loc 8: 177760
LOC 10
Start:  LDR 0,0,6     ; R0 <- 3
        SRC 0,2,1,1   ; logical left 2:      R0 <- 12 (000014)
        SRC 0,1,0,1   ; logical right 1:     R0 <- 6  (000006)
        LDR 1,0,8     ; R1 <- -16 (177760)
        SRC 1,2,0,0   ; arithmetic right 2:  R1 <- -4 (177774), sign kept
        LDR 2,0,7     ; R2 <- 1
        RRC 2,1,0,1   ; rotate right 1:      R2 <- 100000 (bit wraps to top)
        RRC 2,2,1,1   ; rotate left 2:       R2 <- 2 (000002)
        HLT           ; expected: R0=6 R1=177774 R2=2
