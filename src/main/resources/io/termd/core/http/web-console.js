/**
 * Created by wangtao on 12/12/2016.
 */

function getCharSize() {
    var tempDiv = $('<div />').attr({'class': 'terminal'});
    var tempSpan = $('<div />').html('qwertyuiopasdfghjklzxcvbnm');
    tempDiv.append(tempSpan);
    $("html body").append(tempDiv);
    var size = {
        width: tempSpan.outerWidth() / 26,
        height: tempSpan.outerHeight(),
        left: tempDiv.outerWidth() - tempSpan.outerWidth(),
        top: tempDiv.outerHeight() - tempSpan.outerHeight(),
    };
    tempDiv.remove();
    return size;
}

function getwindowSize() {
    var e = window,
        a = 'inner';
    if (!('innerWidth' in window )) {
        a = 'client';
        e = document.documentElement || document.body;
    }
    var terminalDiv = document.getElementById("terminal");
    var terminalDivRect = terminalDiv.getBoundingClientRect();
    return {width: terminalDivRect.width, height: e[a + 'Height'] - terminalDivRect.top};
}

function getTerminalSize() {
    var charSize = getCharSize();
    var windowSize = getwindowSize();
    return {cols: Math.floor((windowSize.width - charSize.left) / charSize.width),
    rows: Math.floor((windowSize.height - charSize.top) / charSize.height)};
}

function connect() {
    var ip = document.getElementById("ip");
    if (!ip) {
        alert("Please input ip!");
    }
    var port = document.getElementById("port");
    if (!port) {
        alert("Please input port!");
    }
    socket = new WebSocket("ws://" + ip.value + ":" + port.value + "/ws");

    var terminalSize = getTerminalSize();

    socket.onopen = function () {
        term = new Terminal({cols: terminalSize.cols, rows: terminalSize.rows, screenKeys: true});
        socket.onmessage = function (event) {
            if (event.type === 'message') {
                var data = event.data;
                term.write(data);
            }
        };
        socket.onclose = function () {
            socket.onmessage = null;
            socket.onclose = null;
            term.destroy();
        };
        term.on('data', function (data) {
            socket.send(JSON.stringify({action: 'read', data: data}));
        });
        term.open(document.getElementById("terminal"));
        socket.send(JSON.stringify({action: 'resize', cols: terminalSize.cols, rows: terminalSize.rows}));
        window.setInterval(function () {
            socket.send(JSON.stringify({action: 'read', data: ""}));
        }, 30000)
    };
}

function disconnect() {
    socket.onmessage = null;
    socket.onclose = null;
    term.destroy();
}