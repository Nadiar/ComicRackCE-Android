; AutoHotKey script to automate pairing in ComicRack Desktop
; Expects the "Devices" window to be open.

#NoEnv
#SingleInstance Force
SendMode Input
SetWorkingDir %A_ScriptDir%
SetTitleMatchMode, 2 ; Match partial titles

; Target Window Titles - Use specific title to avoid matching Android Studio/emulator
ComicRackTitle := "ComicRack Community Edition"
DevicesTitle := "Devices"
SelectDeviceTitle := "Select Device"

; Debug: Show we're running
; TrayTip, AutoPair, Starting ComicRack pairing automation..., 1

; 0. Cleanup: Close "Select Device" if already open
IfWinExist, %SelectDeviceTitle%
{
    WinActivate, %SelectDeviceTitle%
    ; Try standard Cancel button
    ControlClick, Cancel, %SelectDeviceTitle%
    WinWaitClose, %SelectDeviceTitle%, , 1

    ; Setup Force Close fallback
    IfWinExist, %SelectDeviceTitle%
    {
        WinClose, %SelectDeviceTitle%
        WinWaitClose, %SelectDeviceTitle%, , 1
    }
}

; 1. Activate "Devices" Window (Open if needed)
IfWinNotExist, %DevicesTitle%
{
    ; Look specifically for ComicRack Community Edition to avoid Android Studio
    IfWinExist, %ComicRackTitle%
    {
        ; Force window to foreground
        WinActivate, %ComicRackTitle%
        WinWait, %ComicRackTitle%, , 3
        WinActivate, %ComicRackTitle%
        Sleep, 500

        ; Open "Devices" via keyboard shortcut (more reliable than menu)
        Send, !e ; Alt+E for Edit menu
        Sleep, 300
        Send, d  ; D for Devices
        Sleep, 300
    }
    else
    {
        MsgBox, ComicRack Community Edition not found. Please open the desktop app.
        ExitApp
    }
}

WinWait, %DevicesTitle%, , 10
if ErrorLevel
{
    MsgBox, Timeout waiting for "%DevicesTitle%" window.
    ExitApp
}
WinActivate, %DevicesTitle%

; 2. Click "Pair with Device..."
ControlClick, Pair with Device..., %DevicesTitle%
if ErrorLevel
{
    ; Fallback: Try finding it by text or standard button class if needed
    MsgBox, Failed to click "Pair with Device...". Ensure button is visible.
    ExitApp
}

; 3. Wait for "Select Device" Window
WinWait, %SelectDeviceTitle%, , 10
if ErrorLevel
{
    MsgBox, Timeout waiting for "%SelectDeviceTitle%" window.
    ExitApp
}
WinActivate, %SelectDeviceTitle%

; 4. Check for Devices Loop
Loop
{
    ; Check ListView valid items
    ; SysListView321 is standard for lists. Might differ in WPF.
    ControlGet, ItemCount, List, Count, SysListView321, %SelectDeviceTitle%
    
    if (ItemCount > 0)
    {
        ; 5. Select the first device
        Control, Choose, 1, SysListView321, %SelectDeviceTitle%
        
        ; 6. Click "OK"
        ControlClick, OK, %SelectDeviceTitle%
        break
    }
    
    Sleep, 1000 ; Wait 1s before retry
    
    ; Optional: Timeout loop
    if (A_Index > 30) ; 30 seconds timeout
    {
        ; Click Cancel/Close on Select Device dialog
        WinActivate, %SelectDeviceTitle%
        ControlClick, Cancel, %SelectDeviceTitle%
        Sleep, 500
        IfWinExist, %SelectDeviceTitle%
        {
            WinClose, %SelectDeviceTitle%
        }
        ExitApp 1
    }
}

; Pairing sequence completed.
