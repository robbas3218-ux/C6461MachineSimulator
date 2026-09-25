; T7 - I/O instructions: echo three keyboard characters to the printer
; Tests: CHK (device 0 keyboard status), IN (device 0), OUT (device 1)
; Type e.g. "AB" + Enter in Line Input before/while running.
; Start PC: 8 (000010 octal)
LOC 6
THREE:  Data 3        ; loc 6: number of characters to echo
LOC 8
Start:  LDR 2,0,6     ;  8: R2 <- 3 (counter)
Loop:   CHK 0,0       ;  9: R0 <- keyboard status
        JZ 0,0,9      ; 10: busy-wait until a character is queued
        IN 1,0        ; 11: R1 <- next character code from keyboard
        OUT 1,1       ; 12: print it on the console printer
        SOB 2,0,9     ; 13: repeat for 3 characters
        HLT           ; 14: expected printer shows the 3 characters;
                      ;     R1 = last char code, R2 = 0
