<template>
  <div class="layout">
    <Layout>
      <Header>
        <Menu mode="horizontal" theme="dark" active-name="1">
          <div class="layout-logo">
            <Button type="info" icon="md-menu" @click="isConfigShow = true">Connection Config</Button>
          </div>
          <div class="layout-nav">
            <label style="color: white"><h1>{{msg}}</h1></label>
          </div>
        </Menu>
      </Header>
      <Content :style="{padding: '10px 10px'}">
        <Card id="terminal-card">
          <div style="min-height: 600px;height: 100%">
            <div id="terminal" ref="xtermPanel"></div>
          </div>
        </Card>
      </Content>
      <Drawer title="Basic Drawer" placement="left" :closable="false" v-model="isConfigShow">
        <Form :model="connectionConfig" :label-width="80">
          <FormItem label="IP">
            <Input v-model="connectionConfig.ip" placeholder="Please Enter IP address"/>
          </FormItem>
          <FormItem label="Port">
            <Input v-model="connectionConfig.port" placeholder="Please Enter Port"/>
          </FormItem>
          <FormItem>
            <Button type="primary" @click="startConnect">Connect</Button>
          </FormItem>
          <FormItem>
            <Button type="error" @click="disconnect">Disconnect</Button>
          </FormItem>
        </Form>
      </Drawer>
    </Layout>
    <div class="fullSc" v-show="isXtermShow">
      <Button shape="circle" icon="ios-expand" @click="xtermFullScreen"/>
    </div>
  </div>
</template>

<script>
import 'xterm/src/xterm.css'
import logo from '@/assets/logo.png'
import {initWs, getWs, initXterm, getXterm} from '@/utils/xterm-ws'
import jQuery from 'jquery'

export default {
  name: 'HelloWorld',
  props: {
    msg: String
  },
  data () {
    return {
      logo,
      connectionConfig: {
        ip: '127.0.0.1',
        port: 8563
      },
      connectionLoading: false,
      isConfigShow: false,
      isXtermShow: false
    }
  },
  methods: {
  	/** build connection **/
	startConnect () {
      console.log("connecting")
      if (this.connectionConfig.ip === '' || this.connectionConfig.port === '') {
      	this.$Message.error('ip or port can not be empty')
        return
      }
      this.connectionLoading = true
      // init webSocket
      initWs(this.connectionConfig.ip, this.connectionConfig.port)
      let ws = getWs()
      let that = this
      ws.onerror = function () {
      	that.$Message.error('connect error')
        this.connectionLoading = false
      }
      ws.onopen = function () {
      	console.log('open')
        this.connectionLoading = false
        that.isConfigShow = false
        that.isXtermShow = true
        that.$Message.success('connect successfully!')
        let terminalSize = that.getTerminalSize()
        console.log('terminalSize')
        console.log(terminalSize)
        // init xterm
        initXterm(terminalSize.cols, terminalSize.rows)
        // get xterm
        var xterm = getXterm()
        ws.onmessage = function (event) {
          if (event.type === 'message') {
            let data = event.data
            xterm.write(data)
          }
        }
        xterm.open(document.getElementById('terminal'))
        xterm.on('data', function (data) {
          ws.send(JSON.stringify({action: 'read', data: data}))
        })
        ws.send(JSON.stringify({action: 'resize', cols: terminalSize.cols, rows: terminalSize.rows}));
        window.setInterval(function () {
          ws.send(JSON.stringify({action: 'read', data: ""}));
        }, 30000)
      }
    },
    disconnect () {
      let ws = getWs()
      let xterm = getXterm()
      try {
        ws.onmessage = null
        ws.onclose = null
        xterm.destroy()
        this.$Message.success('connection was closed successfully!')
      } catch (e) {
        this.$Message.error('no connection!')
      } finally {
      	this.isConfigShow = false
      }
    },
    /** full screen show **/
    xtermFullScreen () {
      let ele = document.getElementById('terminal-card')
      this.requestFullScreen(ele)
    },
    /** resize handler when change size of window **/
    xtermResizeHandler () {
      let ws = getWs()
      let terminalSize = this.getTerminalSize()
      console.log('after')
      console.log(terminalSize)
      ws.send(JSON.stringify({action: 'resize', cols: terminalSize.cols, rows: terminalSize.rows}))
      let xterm = getXterm()
      xterm.resize(terminalSize.cols, terminalSize.rows)
    },
    requestFullScreen (element) {
      var requestMethod = element.requestFullScreen || element.webkitRequestFullScreen || element.mozRequestFullScreen || element.msRequestFullScreen;
      if (requestMethod) {
          requestMethod.call(element);
      } else if (typeof window.ActiveXObject !== "undefined") {
          var wscript = new ActiveXObject("WScript.Shell");
          if (wscript !== null) {
              wscript.SendKeys("{F11}");
          }
      }
    },
    getCharSize () {
      let tempDiv = jQuery('<div />').attr({'role': 'listitem'})
      let tempSpan = jQuery('<div />').html('qwertyuiopasdfghjklzxcvbnm')
      tempDiv.append(tempSpan)
      jQuery("html body").append(tempDiv)
      let size = {
        width: tempSpan.outerWidth() / 26,
        height: tempSpan.outerHeight(),
        left: tempDiv.outerWidth() - tempSpan.outerWidth(),
        top: tempDiv.outerHeight() - tempSpan.outerHeight(),
      }
      tempDiv.remove()
      return size
    },
    getWindowSize () {
      let e = window
      let a = 'inner'
      if (!('innerWidth' in window )) {
          a = 'client'
          e = document.documentElement || document.body
      }
      var terminalDiv = document.getElementById("terminal-card")
      var terminalDivRect = terminalDiv.getBoundingClientRect()
      return {
      	width: terminalDivRect.width,
        height: e[a + 'Height'] - terminalDivRect.top
      }
    },
    getTerminalSize () {
      let charSize = this.getCharSize()
      let windowSize = this.getWindowSize()
      console.log('charsize')
      console.log(charSize)
      console.log('windowSize')
      console.log(windowSize)
      return {
      	cols: Math.floor((windowSize.width - charSize.left) / 10),
        rows: Math.floor((windowSize.height - charSize.top) / 17)
      }
    }
  },
  /** event listener **/
  mounted () {
  	// add resize event listener
  	window.addEventListener('resize', this.xtermResizeHandler)
  }
}
</script>

<style scoped>
  .layout{
    border: 1px solid #d7dde4;
    background: #f5f7f9;
    position: relative;
    border-radius: 4px;
    overflow: hidden;
  }
  .layout-logo{
    width: 100px;
    height: 60px;
    /*background: #5b6270;*/
    border-radius: 3px;
    float: left;
    position: relative;
    top: 1px;
    left: 5px;
    vertical-align: middle;
  }
  .layout-nav{
    width: 820px;
    margin: 0 auto;
    /*margin-right: 20px;*/
  }
  .fullSc{
    z-index: 10000;
    position: fixed;
    top: 20%;
    left: 90%;
  }
  #xtermPanel:-webkit-full-screen{
    background-color: rgb(255, 255, 12);
  }
</style>
