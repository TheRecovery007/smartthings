/**
 *  Device Handler for Fibaro Roller Shutter 3 (FGR-223)
 *
 *  Based on the FGR-222 handler by Julien Bachmann
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

metadata {
    definition (name: "Fibaro FGR-223", namespace: "philh30", author: "philh30") {
        capability "Sensor"
        capability "Actuator"

        capability "Switch"
        capability "Switch Level"
        capability "Window Shade"

        capability "Polling"
        capability "Power Meter"
        capability "Energy Meter"
        capability "Refresh"
        capability "Configuration"

        attribute "syncStatus", "enum", ["syncing", "synced"]

        command "sync"
        command "stop"        
        command "up"   
        command "down"   

        fingerprint inClusters: "0x26,0x32"
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"mainTitle", type:"generic", width:6, height:4, canChangeIcon: true) {
            tileAttribute("device.windowShade", key: "PRIMARY_CONTROL") {
                attributeState "open", label:'Open', backgroundColor:"#ffa81e", action: "close", nextState: "closing"
                attributeState "partially open", label:'Partial', backgroundColor:"#d45614", action: "open", nextState: "opening"
                attributeState "closed", label:'Closed', backgroundColor:"#00a0dc", action: "open", nextState: "opening"
                attributeState "opening", label:'Opening', backgroundColor:"#ffa81e", action: "stop", nextState: "partially open"
                attributeState "closing", label:'Closing', backgroundColor:"#00a0dc", action: "stop", nextState: "partially open"
            }
            tileAttribute("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action:"setLevel", defaultState: true, icon:"st.Home.home9"
            }
        }
        valueTile("power", "device.power", width: 2, height: 2) {
            state "default", label:'${currentValue} W'
        }
        valueTile("energy", "device.energy", width: 2, height: 2) {
            state "default", label:'${currentValue} kWh'
        }
        standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:"Refresh", action:"refresh", icon:"st.secondary.refresh-icon"
        }
        standardTile(name: "calibrate", width: 2, height: 2, decoration: "flat") {
            state "default", action:"configure", label:"Calibrate", backgroundColor:"#0000a8"
        }
        standardTile(name: "up", width: 2, height: 2, decoration: "flat") {
            state "default", action:"up", icon:"https://raw.githubusercontent.com/julienbachmann/smartthings/master/fibaro_fgr_222/up.png?v=3"
        }
        standardTile(name: "down", width: 2, height: 2, decoration: "flat") {
            state "default", action:"down", icon:"https://raw.githubusercontent.com/julienbachmann/smartthings/master/fibaro_fgr_222/down.png?v=3"
        }
        standardTile("sync", "device.syncStatus", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", action:"sync" , label:"Sync", backgroundColor:"#00a800"
            state "synced", action:"sync" , label:"Sync", backgroundColor:"#00a800"
            state "syncing" , label:"Syncing", backgroundColor:"#a8a800"
        }
        main(["mainTitle"])
        details(["mainTitle", "up", "power", "refresh", "down", "sync", "calibrate"])
    }

    preferences {
        input name: "invert", type: "bool", title: "Invert up/down", description: "Invert up and down actions"
        input name: "openOffset", type: "decimal", title: "Open offset", description: "The percentage from which shutter is displayerd as open"
        input name: "closeOffset", type: "decimal", title: "Close offset", description: "The percentage from which shutter is displayerd as close"
        input name: "offset", type: "decimal", title: "offset", description: "This offset allow to correct the value returned by the device so it match real value"

        /*section {
            input (
                type: "paragraph",
                element: "paragraph",
                title: "DEVICE PARAMETERS:",
                description: "Device parameters are used to customise the physical device. " +
            "Refer to the product documentation for a full description of each parameter."
        )

            getParamsMd().findAll( {!it.readonly} ).each { // Exclude readonly parameters.

                def lb = (it.description.length() > 0) ? "\n" : ""

                switch(it.type) {
                    case "number":
                        input (
                            name: "configParam${it.id}",
                        title: "#${it.id}: ${it.name}: \n" + it.description + lb +"Default Value: ${it.defaultValue}",
                        type: it.type,
                        range: it.range,
                        required: it.required
                    )
                        break

                    case "enum":
                        input (
                            name: "configParam${it.id}",
                        title: "#${it.id}: ${it.name}: \n" + it.description + lb + "Default Value: ${it.defaultValue}",
                        type: it.type,
                        options: it.options,
                        required: it.required
                    )
                        break
                }
            }
        } // section*/
    }
}

def parse(String description) {
    log.debug("parse ${description}")
    def result = null
    def cmd = zwave.parse(description, [0x20: 1, 0x26: 3, 0x70: 1, 0x32:3])
    if (cmd) {
        result = zwaveEvent(cmd)
        if (result) {
            log.debug("Dispatch events ${result}")
        }
    } else {
        log.debug("Couldn't zwave.parse ${description}")
    }
    result
}

def correctLevel(value) {
    def result = value
    if (value == "off") {
        result = 0;
    }
    if (value == "on" ) {
        result = 100;
    }
    result = result - (offset ?: 0)
    if (invert) {
        result = 100 - result
    }
    return result
}

def createWindowShadeEvent(value) {
    def theWindowShade = "partially open"
    if (value >= (openOffset ?: 95)) {
        theWindowShade = "open"
    }
    if (value <= (closeOffset ?: 5)) {
        theWindowShade = "closed"
    }
    return createEvent(name: "windowShade", value: theWindowShade)
}

def createSwitchEvent(value) {
    def switchValue = "on"
    if (value >= (openOffset ?: 95)) {
        switchValue = "on"
    }
    if (value <= (closeOffset ?: 5)) {
        switchValue = "off"
    }
    return createEvent(name: "switch", value: switchValue)
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x26: 3, 0x70: 1, 0x32:3])
	state.sec = 1
    log.debug "Secure message"
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    logger.debug("basic report ${cmd}")
    def result = []
    if (cmd.value != null) {
        def level = correctLevel(cmd.value)
        result << createEvent(name: "level", value: level, unit: "%")  
        if (device.currentValue('windowShade') == "opening" || device.currentValue('windowShade') == "closing") {
        	result << response([zwave.meterV2.meterGet(scale: 2).format()])
        }
    }
    log.debug("basic result ${result}")
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
    log.debug("switch multi level report ${cmd.value}")
    def result = []
    if (cmd.value != null) {
        def level = correctLevel(cmd.value)
        result << createEvent(name: "level", value: level, unit: "%")   
        if (device.currentValue('windowShade') == "opening" || device.currentValue('windowShade') == "closing") {
        	result << response([zwave.meterV2.meterGet(scale: 2).format()])
        }
        else {
            result << createWindowShadeEvent(level) 
        }
    }
    log.debug("switch result ${result}")
    return result
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    log.debug("other event ${cmd}")
}

def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd) {
    if (cmd.meterType == 1) {
        if (cmd.scale == 2) {
            def result = []
            result << createEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W")
            if (cmd.scaledMeterValue < 1.0) {
              result << createWindowShadeEvent(device.currentValue('level'))
              result << response(["delay 500", zwave.switchMultilevelV3.switchMultilevelGet().format()])
            }
            else {
              result << response(["delay 2000", zwave.switchMultilevelV3.switchMultilevelGet().format()])
            }
            log.debug("power result ${result}")
            return result
        } else {
            return createEvent(name: "electric", value: cmd.scaledMeterValue, unit: ["pulses", "V", "A", "R/Z", ""][cmd.scale - 3])
        }
    }
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
    log.debug("zwaveEvent(): Configuration Report received: ${cmd}")
}

def updated() {
    setSynced();
}

def on() {
    open()
}

def off() {
    close()
}

def stop() {
    logger.debug("stop")
	secureSequence([
    	zwave.switchMultilevelV1.switchMultilevelStopLevelChange(),
    	zwave.switchMultilevelV3.switchMultilevelGet()
    ], 2000)
}

def up() {
    def currentWindowShade = device.currentValue('windowShade')
    if (currentWindowShade == "opening" || currentWindowShade == "closing") {      
        return stop()        
    }
    return open()
}

def down() {
    def currentWindowShade = device.currentValue('windowShade')
    if (currentWindowShade == "opening" || currentWindowShade == "closing") {
        return stop()        
    }
    return close()
}

def open() {
    logger.debug("open")
    sendEvent(name: "windowShade", value: "opening")
    if (invert) {
        return privateClose()
    }
    else {
        return privateOpen()
    }
}

def close() {
    logger.debug("close")
    sendEvent(name: "windowShade", value: "closing")    
    if (invert) {
        return privateOpen()
    }
    else {
        return privateClose()
    }
}

def privateOpen() {
    secureSequence([
    	zwave.basicV1.basicSet(value: 0xFF),
    	zwave.switchMultilevelV3.switchMultilevelGet()
    ], 2000)
}

def privateClose() {
    secureSequence([
        zwave.basicV1.basicSet(value: 0),
        zwave.switchMultilevelV3.switchMultilevelGet()
    ], 2000)
}

def presetPosition() {
    setLevel(50)
}

def poll() {
    secureSequence([
        zwave.meterV2.meterGet(scale: 0),
        zwave.meterV2.meterGet(scale: 2)
	], 1000)
}

def refresh() {
    log.debug("refresh")
    secureSequence([
        zwave.switchMultilevelV3.switchMultilevelGet(),
        zwave.meterV2.meterGet(scale: 2)
	], 500)
}

def setLevel(level) {
    if (invert) {
        level = 100 - level
    }
    if(level > 99) level = 99
    if (level <= (openOffset ?: 95) && level >= (closeOffset ?: 5)) {
        level = level - (offset ?: 0)
    }

    log.debug("set level ${level}")
    secureSequence([
        zwave.basicV1.basicSet(value: level),
        zwave.switchMultilevelV1.switchMultilevelGet()
	], 10000)
}

def configure() {
    log.debug("configure roller shutter")
    secureSequence([
        zwave.configurationV1.configurationSet(parameterNumber: 29, size: 1, scaledConfigurationValue: 1),  // start calibration
        zwave.switchMultilevelV1.switchMultilevelGet(),
        zwave.meterV2.meterGet(scale: 0),
        zwave.meterV2.meterGet(scale: 2),
	], 500)
}

def sync() {
    log.debug("sync roller shutter")
    def cmds = []
    sendEvent(name: "syncStatus", value: "syncing", isStateChange: true)
    getParamsMd().findAll( {!it.readonly} ).each { // Exclude readonly parameters.
        if (settings."configParam${it.id}" != null) {
            cmds << secure(zwave.configurationV1.configurationSet(parameterNumber: it.id, size: it.size, scaledConfigurationValue: settings."configParam${it.id}".toInteger()))
            cmds << secure(zwave.configurationV1.configurationGet(parameterNumber: it.id))
        }
    }
    log.debug("send cmds ${cmds}")
    runIn(0.5 * cmds.size(), setSynced)
    delayBetween(cmds, 500)
}

def setSynced() {
    log.debug("Synced")
    sendEvent(name: "syncStatus", value: "synced", isStateChange: true)
}

private secure(physicalgraph.zwave.Command cmd) {
	if (state.sec) {
		log.debug "Sending secure command"
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		cmd.format()
	}
}

private secureSequence(Collection commands, ...delayBetweenArgs) {
	log.debug "Sending secure sequence"
    delayBetween(commands.collect{ secure(it) }, *delayBetweenArgs)
}

private getParamsMd() {
    return [
        [id:  3, size: 1, type: "number", range: "0..1", defaultValue: 0, required: false, readonly: false,
        name: "Reports type",
        description: "0 – Blind position reports sent to the main controller using Z-Wave Command Class.\n" +
    "1 - Blind position reports sent to the main controller using Fibar Command Class.\n" +
    "Parameters value shoud be set to 1 if the module operates in Venetian Blind mode."],
    [id:  10, size: 1, type: "number", range: "0..4", defaultValue: 0, required: false, readonly: false,
        name: "Roller Shutter operating modes",
        description: "0 - Roller Blind Mode, without positioning\n" +
    "1 - Roller Blind Mode, with positioning\n" +
    "2 - Venetian Blind Mode, with positioning\n" +
    "3 - Gate Mode, without positioning\n" +
    "4 - Gate Mode, with positioning"],
    [id: 12, size:2, type: "number", range: "0..65535", defaultValue: 0, required: false, readonly: false,
        name: "Time of full turn of the slat",
        description: "In Venetian Blind mode (parameter 10 set to 2) the parameter determines time of full turn of the slats.\n" +
    "In Gate Mode (parameter 10 set to 3 or 4) the parameter defines the COUNTDOWN time, i.e. the time period after which an open gate starts closing. In any other operating mode the parameter value is irrelevant.\n" +
    "Value of 0 means the gate will not close automatically.\n" +
    "Available settings: 0-65535 (0 - 655,35s)\n" +
    "Default setting: 150 (1,5 s)"],
    [id: 13, size:1, type: "number", range: "0..2", defaultValue: 0, required: false, readonly: false,
        name: "Set slats back to previous position",
        description: "In Venetian Blind Mode (parameter 10 set to 2) the parameter influences slats positioning in various situations. In any other operating mode the parameter value is irrelevant.\n" +
    "0 - Slats return to previously set position only in case of the main controller operation\n" +
    "1 - Slats return to previously set position in case of the main controller operation, momentary switch operation, or when the limit switch is reached.\n" +
    "2 - Slats return to previously set position in case of the main controller operation, momentary switch operation, when the limit switch is reached or after " +
    " receiving a “STOP” control frame (Switch Multilevel Stop)."],
    [id: 14, size:1, type: "number", range: "0..2", defaultValue: 0, required: false, readonly: false,
        name: "Switch type",
        description: "The parameter settings are relevant for Roller Blind Mode and Venetian Blind Mode (parameter 10 set to 0, 1, 2).\n" +
    "0 - Momentary switches\n" +
    "1 - Toggle switches\n" +
    "2 - Single, momentary switch. (The switch should be connected to S1 terminal)"],
    [id: 18, size:1, type: "number", range: "0..255", defaultValue: 0, required: false, readonly: false,
        name: "Motor operation detection.",
        description: "Power threshold to be interpreted as reaching a limit switch. \n" +
    "Available settings: 0 - 255 (1-255 W)\n" +
    "The value of 0 means reaching a limit switch will not be detected \n" +
    "Default setting: 10 (10W)."],
    [id: 22, size:2, type: "number", range: "0..65535", defaultValue: 0, required: false, readonly: false,
        name: "Motor operation time.",
        description: "Time period for the motor to continue operation. \n" +
    "Available settings: 0 – 65535 (0 – 65535s)\n" +
    "The value of 0 means the function is disabled.\n" +
    "Default setting: 240 (240s. – 4 minutes)"],
    [id: 30, size:1, type: "number", range: "0..2", defaultValue: 0, required: false, readonly: false,
        name: "Response to general alarm",
        description: "0 - No reaction.\n" +
    "1 - Open blind.\n" +
    "2 - Close blind."],
    [id: 31, size:1, type: "number", range: "0..2", defaultValue: 0, required: false, readonly: false,
        name: "Response to flooding alarm",
        description: "0 - No reaction.\n" +
    "1 - Open blind.\n" +
    "2 - Close blind."],
    [id: 32, size:1, type: "number", range: "0..2", defaultValue: 0, required: false, readonly: false,
        name: "Response to smoke, CO or CO2 alarm",
        description: "0 - No reaction.\n" +
    "1 - Open blind.\n" +
    "2 - Close blind."],
    [id: 33, size:1, type: "number", range: "0..2", defaultValue: 0, required: false, readonly: false,
        name: "Response to temperature alarm",
        description: "0 - No reaction.\n" +
    "1 - Open blind.\n" +
    "2 - Close blind."],
    [id: 35, size:1, type: "number", range: "0..2", defaultValue: 0, required: false, readonly: false,
        name: "Managing slats in response to alarm.",
        description: "0 - Do not change slats position - slats return to the last set position\n" +
    "1 - Set slats to their extreme position"],
    [id: 40, size:1, type: "number", range: "0..2", defaultValue: 0, required: false, readonly: false,
        name: "Power reports",
        description: "Power level change that will result in new power value report being sent." +
    "The parameter defines a change that needs to occur in order to trigger the report. The value is a percentage of the previous report.\n" +
    "Power report threshold available settings: 1-100 (1-100%).\n" +
    "Value of 0 means the reports are turned off."]

]
}
