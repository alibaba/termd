/**
 * Created by wangtao on 12/12/2016.
 */

function getCharSize() {
    var span = $('<span />').attr({'id': 'test'}).html('qwertyuiopasdfghjklzxcvbnm');
    $("html body").append(span);
    var size = {
        width: $("#test").outerWidth() / 26
        , height: $("#test").outerHeight()
    };
    $("#test").remove();
    return size;
}
function getwindowSize() {
    var e = window,
        a = 'inner';
    if (!('innerWidth' in window )) {
        a = 'client';
        e = document.documentElement || document.body;
    }
    return {width: e[a + 'Width'], height: e[a + 'Height']};
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

    var charSize = getCharSize();
    var windowSize = getwindowSize();
    var cols = Math.floor(windowSize.width / charSize.width);
    var rows = Math.floor(windowSize.height / charSize.height);

    socket.onopen = function () {
        term = new Terminal({cols: cols, rows: rows, screenKeys: true});
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
        term.open(document.body);
        socket.send(JSON.stringify({action: 'resize', cols: cols, rows: rows}));
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