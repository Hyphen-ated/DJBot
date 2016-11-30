; This is an AutoHotKey script to control the djbot
; I like using the numpad, feel free to change it to whatever keys you want
; If you want the djbot window to remain in the foreground, remove "Send, !{Esc}" before each return

SetTitleMatchMode, 1

Numpad6::
IfWinExist, DJbot interface
{
	WinActivate
	Sleep 30
	Send, !{Up}
	return
}

Numpad3::
IfWinExist, DJbot interface
{
	WinActivate
	Sleep 30
	Send, !{Down}
	return
}

Numpad9::
IfWinExist, DJbot interface
{
	WinActivate
	Sleep 30
	Send, !{Right}
	return
}

Numpad5::
IfWinExist, DJbot interface - Google Chrome
{
	WinActivate
	Sleep 30
	Send, ^!{Up}
	return
}


