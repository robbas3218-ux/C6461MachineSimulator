; T8 - TRAP and machine-fault behavior
; Run 1 (start PC 6):  TRAP 1 -> saves PC+1 at location 2, vectors through
;                      the trap table whose base address is at location 0.
;                      Handler sets R3=7 and halts at 13.
; Run 2 (start PC 8):  STR to reserved location 3 -> machine fault id 0
;                      (MFR 0001); control transfers to handler at c(1),
;                      which sets R3=5 and halts at 15.
LOC 0
        Data TTab     ; loc 0: trap table base (= 10)
        Data FHand    ; loc 1: machine fault handler (= 14)
LOC 6
Start:  TRAP 1        ;  6: trap code 1 -> c(2)=7, PC <- c(TTab+1) = 12
        HLT           ;  7: not reached in run 1
Flt:    STR 0,0,3     ;  8: RUN 2 START: illegal write to reserved loc 3
        HLT           ;  9: not reached in run 2
LOC 10
TTab:   Data THand    ; 10: trap code 0 handler address
        Data THand    ; 11: trap code 1 handler address
THand:  AIR 3,7       ; 12: trap handler: R3 <- 7 marker
        HLT           ; 13
FHand:  AIR 3,5       ; 14: fault handler: R3 <- 5 marker
        HLT           ; 15
