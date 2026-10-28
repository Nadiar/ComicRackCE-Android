; AutoHotKey script to automate "Files > Synchronize Devices" in ComicRack Desktop
#NoEnv
#SingleInstance Force
SendMode Input
SetWorkingDir %A_ScriptDir%
SetTitleMatchMode, 2

ComicRackTitle := "ComicRack Community Edition"

; Debug: Notify in tray
; TrayTip, AutoSync, Starting ComicRack sync automation..., 1

IfWinExist, %ComicRackTitle%
{
    WinActivate, %ComicRackTitle%
    WinWaitActive, %ComicRackTitle%, , 5
    
    if ErrorLevel
    {
        MsgBox, Failed to activate ComicRack window.
        ExitApp 1
    }

    ; Close any open menus first to ensure a clean state
    Send, {Esc}{Esc}
    Sleep, 500

    ; Use WinMenuSelectItem for more robust interaction with standard menus
    WinMenuSelectItem, %ComicRackTitle%, , File, Synchronize Devices
    
    if ErrorLevel
    {
        Send, !f
        Sleep, 800
        Loop, 9
        {
            Send, {Down}
            Sleep, 100
        }
        Send, {Enter}
        Sleep, 1000
    }
    
    ; Optional: Click OK on any confirmation dialog if it appears
    IfWinExist, Synchronize
    {
        ControlClick, OK, Synchronize
    }
}
else
{
    MsgBox, ComicRack Community Edition not found.
    ExitApp 1
}

ExitApp 0
