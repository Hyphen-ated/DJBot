; This is an AutoHotKey script to control the djbot
; I like using the numpad, feel free to change it to whatever keys you want
; If you want the djbot window to stay in the background, add "Send, !{Esc}" before each return

SetTitleMatchMode, 1

Numpad6::
IfWinExist, DJbot interface
{
	WinActivate
	Send, !{Up}
	return
}

Numpad3::
IfWinExist, DJbot interface
{
	WinActivate
	Send, !{Down}
	return
}

Numpad9::
IfWinExist, DJbot interface
{
	WinActivate
	Send, !{Right}
	return
}