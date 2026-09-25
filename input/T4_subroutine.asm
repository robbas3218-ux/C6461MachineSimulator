; T4 - Subroutine linkage
; Tests: JSR (saves PC+1 in R3), RFS (return code to R0, PC <- c(R3)),
;        LDA for argument pointer convention, indirect JSR target
; Start PC: 12 (000014 octal)
LOC 6
ARG1:   Data 11       ; loc 6
ARG2:   Data 22       ; loc 7
RES:    Data 0        ; loc 8
SUBP:   Data Sub      ; loc 9: pointer to subroutine (= 20)
LOC 12
Start:  LDA 0,0,6     ; 12: R0 -> argument list at 6 (convention)
        JSR 0,9,1     ; 13: indirect call: EA=c(9)=20; R3 <- 14
        STR 0,0,8     ; 14: (return here) RES <- RFS return code
        HLT           ; 15: expected R0=7 R1=33 R3=14 c(8)=7
LOC 20
Sub:    LDR 1,0,6     ; 20: R1 <- ARG1 = 11
        AMR 1,0,7     ; 21: R1 <- 11+22 = 33 (000041)
        RFS 7         ; 22: R0 <- 7, PC <- c(R3) = 14
