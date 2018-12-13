import {Terminal} from 'xterm'
var ws;

var xterm;

export function initWs (ip, port) {
	let path = 'ws://' + ip + ':' + port + '/ws'
	ws = new WebSocket(path)
}

export function getWs () {
	return ws
}

export function initXterm (cols, rows) {
	xterm = new Terminal({
		cols: cols,
		rows: rows,
		screenReaderMode: true,
		rendererType: 'canvas',
		convertEol: true
	})
}

export function getXterm () {
	return xterm
}