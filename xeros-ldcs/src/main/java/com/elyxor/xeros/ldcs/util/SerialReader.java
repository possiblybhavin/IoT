package com.elyxor.xeros.ldcs.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elyxor.xeros.ldcs.dai.DaiPortInterface;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

public class SerialReader implements SerialPortEventListener, SerialReaderInterface {
	
	final static Logger logger = LoggerFactory.getLogger(SerialReader.class);
	private static final String EOT = new String(new char[] {'','',''}); 

	SerialPort serialPort;
	DaiPortInterface daiPort;
	
	public SerialReader(DaiPortInterface port)  {
    	daiPort = port;
		serialPort = port.getSerialPort();
	} 
	
//	public String sendStdRequest() {
//		String buffer = "";
//		
//		try {
//			serialPort.writeString("0 12\n");
//			Thread.sleep(1000);
//			while (serialPort.getInputBufferBytesCount() > 0) {
//				buffer += serialPort.readString(serialPort.getInputBufferBytesCount());
//				Thread.sleep(500);
//			}
//		} catch (Exception e) {
//			String msg = "Couldn't complete send std request. ";
//			logger.warn(msg, e);
//			buffer = msg + e.getMessage(); 
//		}
//		return buffer;
//	}
	
	public void serialEvent(SerialPortEvent event) {
		String eventBuffer = null;
		String logBuffer = null;
		
		if (event.getEventValue() == 3) {
			try {
				eventBuffer = serialPort.readString(3);
			} catch (SerialPortException e) {
				logger.warn("Unable to read port event", e);
			}
			if (eventBuffer != null && !eventBuffer.isEmpty() && eventBuffer.equals("***")) {
				logger.info("Log file incoming");
				logBuffer = daiPort.sendRequest();
				if (logBuffer!=null && logBuffer.endsWith(EOT)) {
					logger.info("Proper log file found, writing...");
					daiPort.writeLogFile(logBuffer);
				}
			}
		}
//		if (event.isRXCHAR()) {
//			if (event.getEventValue() > 1) {
//				try {
//					logBuffer += serialPort.readString(event.getEventValue());
//				} catch (SerialPortException e) {
//					logger.warn("unable to read port event", e);
//				}
//				if (logBuffer != null && !logBuffer.isEmpty() && logBuffer.startsWith("\n") && logBuffer.endsWith("\r\n")) {
//					daiPort.writeLogFile(logBuffer);
//				}
//			}
//		}
	}	
}
