; This is an AutoHotKey script to control the djbot
; I like using the numpad, feel free to change it to whatever keys you want

SetTitleMatchMode, 1

Numpad6::
IfWinExist, DJbot interface
{
	WinActivate
	Sleep 30
	Send, !{Up}
	Send, !{Esc}
	return
}

Numpad3::
IfWinExist, DJbot interface
{
	WinActivate
	Sleep 30
	Send, !{Down}
	Send, !{Esc}
	return
}

Numpad9::
IfWinExist, DJbot interface
{
	WinActivate
	Sleep 30
	Send, !{Right}
	Send, !{Esc}
	return
}

Numpad5::
IfWinExist, DJbot interface - Google Chrome
{
	WinActivate
	Sleep 30
	Send, ^!{Up}
	Send, !{Esc}
	return
}


