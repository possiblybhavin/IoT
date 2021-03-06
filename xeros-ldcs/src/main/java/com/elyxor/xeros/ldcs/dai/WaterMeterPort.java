package com.elyxor.xeros.ldcs.dai;

import com.elyxor.xeros.ldcs.HttpFileUploader;
import com.elyxor.xeros.ldcs.thingworx.XerosWasherThing;
import com.elyxor.xeros.ldcs.util.FileLogWriter;
import com.elyxor.xeros.ldcs.util.LogWriterInterface;
import jssc.SerialPort;
import jssc.SerialPortException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class WaterMeterPort implements DaiPortInterface, WaterMeterPortInterface {
	final static Logger logger = LoggerFactory.getLogger(WaterMeterPort.class);
	
	private long prevMeter1;
	private long prevMeter2;
    private long[] prevMeters;
	private SerialPort serialPort;
	private int daiNum;
	private LogWriterInterface logWriter;
	private String daiPrefix;
	private String waterMeterId;
    private Path logFilePath;
    private LogWriterInterface waterMeterLogWriter;
    private boolean logSent;
    private String lastLogSentTime;
	
	final static String defaultId = "999999999999";
	final static int frontPadding = 2;
	final static int backPadding = 5;
	final static int idSize = 12;
	final static int idStartLocation = 4;
	final static int meterDataStartLocation = 203;
	final static int meterDataLength = 8;
	final static int responseLength = 255;

    final static private SimpleDateFormat dateAndTimeFormat = new SimpleDateFormat("dd-MM-yyyy kk : mm : ss");

    private long meterDiff1;
    private long meterDiff2;
	
	final static byte[] requestBytes = {(byte)0x2f,(byte)0x3f,(byte)0x30,(byte)0x30,(byte)0x30,
			(byte)0x30,(byte)0x30,(byte)0x30,(byte)0x30,(byte)0x30,(byte)0x30,(byte)0x30,
			(byte)0x30,(byte)0x30,(byte)0x30,(byte)0x30,(byte)0x21,(byte)0x0d,(byte)0x0a};

    final static byte[] passwordBytes = {(byte)0x01,(byte)0x50,(byte)0x31,(byte)0x02,(byte)0x28,
            (byte)0x30,(byte)0x30,(byte)0x30,(byte)0x30,(byte)0x30,(byte)0x30,(byte)0x30,(byte)0x30,
            (byte)0x29,(byte)0x03,(byte)0x32,(byte)0x44};

    final static byte[] timeBytes = {(byte)0x01,(byte)0x57,(byte)0x31,(byte)0x02,(byte)0x30,
            (byte)0x30,(byte)0x36,(byte)0x30,(byte)0x28,(byte)0x31,(byte)0x35,(byte)0x30,(byte)0x32,
            (byte)0x31,(byte)0x36,(byte)0x30,(byte)0x32,(byte)0x31,(byte)0x34,(byte)0x35,(byte)0x39,
            (byte)0x31,(byte)0x32,(byte)0x29,(byte)0x03,(byte)0x15,(byte)0x5E};

    final static byte[] closeBytes = {(byte)0x01,(byte)0x42,(byte)0x30,(byte)0x03,(byte)0x75};

    public WaterMeterPort(SerialPort sp, int num, LogWriterInterface lwi, String prefix, String waterMeterId) {
		this.serialPort = sp;
		this.daiNum = num;
		this.logWriter = lwi;
		this.daiPrefix = prefix;
        this.waterMeterId = waterMeterId;
	}
	
	public boolean openPort() {
		boolean result;
		try {
			result = this.serialPort.openPort();
			this.serialPort.setParams(9600, 7, 1, 2);
		} catch (Exception ex) {
			logger.warn("Could not open port", ex);
			result = false;
		}		
    	logger.info("Opened water meter port " + this.serialPort.getPortName());
		return result;
	}

	public boolean closePort() {
		String portAddress = this.serialPort.getPortName();
		boolean result;
		try {
			this.serialPort.removeEventListener();
			result = this.serialPort.closePort();
		}
		catch (SerialPortException ex) {
			logger.warn("Failed to close port "+portAddress);
			result = false;
		}
		return result;
	}
	
	public String initRequest() {
		byte[] buffer = null;
		byte[] request = createRequestString(this.parseStringToByteArray(defaultId));
		SerialPort port = this.getSerialPort();
		try {
            port.writeBytes(request);
			Thread.sleep(500);
			buffer = port.readBytes();
		} catch (Exception e) {
			String msg = "failed to send init request";
			logger.warn(msg, e);
		}
        if (buffer == null) {
            return null;
        }
        String meterId = parseIdFromResponse(buffer);
		this.setWaterMeterId(meterId);
        this.setLogFilePath(Paths.get(this.logWriter.getPath().getParent().toString(), "/waterMeters"));
        this.setWaterMeterLogWriter(new FileLogWriter(this.logFilePath, daiPrefix + waterMeterId + "meterLogging.txt"));
        long[] meters = parsePrevMetersFromFile();
        logSent = true;
        if (Arrays.equals(meters, new long[]{0,0})) {
            logSent = false;
            meters = parseMetersFromResponse(buffer);
            this.storePrevMeters(meters);
        }
        this.setPrevMeters(meters[0],meters[1]);

        return meterId;
    }

    public String sendRequest() {
		String result = "";
		byte[] buffer = null;
		byte[] request = this.createRequestString(this.parseStringToByteArray(this.getWaterMeterId()));
		SerialPort port = this.getSerialPort();
		long meter1;
		long meter2;
		long[] prevMeters = this.parsePrevMetersFromFile();

		try {
			port.writeBytes(request);
			Thread.sleep(500);
			buffer = port.readBytes(responseLength);
		} catch (Exception e) {
            String msg = "couldn't complete send request";
			logger.warn(msg, e);
			return msg + e;
		}
        if (buffer == null || buffer.length == 0) return result;

        long[] currentMeters = this.parseMetersFromResponse(buffer);
        logger.info("Captured log file, meter1: "+currentMeters[0]+", meter2: "+currentMeters[1]);
        meter1 = currentMeters[0] - prevMeters[0];
        meter2 = currentMeters[1] - prevMeters[1];

        if (meter1 == 0 && meter2 == 0) {
            if (!logSent) {
                lastLogSentTime = getSystemTimeAndDate();
                result = this.getWaterMeterId() + " , Std , \nFile Write Time: , "
                        + lastLogSentTime + "\n"
                        + "WM2: , 0 , 0 , "
                        + meterDiff1 + "\n"
                        + "WM3: , 0 , 0 , "
                        + meterDiff2 + "\n";
                meterDiff1 = meterDiff2 = 0;
                logSent = true;
                return result;
            }
            return result;
        }
        meterDiff1 += meter1;
        meterDiff2 += meter2;

        try {
            Files.delete(this.getWaterMeterLogWriter().getFile().toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        logSent = false;
        this.storePrevMeters(currentMeters);
        return result;
	}

    public long[] calculateWaterLog(String buffer) {
        return new long[0];
    }


    private void storePrevMeters(long[] meters) {
        for (int i = 0; i < meters.length; i++) {
            try {
                this.getWaterMeterLogWriter().write("meter"+i+","+meters[i] +","+ getSystemTime()+"\n");
                logger.info("successfully stored previous meters in log file, "+this.getWaterMeterLogWriter().getFilename());
            } catch (IOException e) {
                e.printStackTrace();
                logger.warn("failed to store previous meters",e);
            }
        }
    }

    private long[] parsePrevMetersFromFile() {
        byte[] inputData = null;
        try {
            inputData = IOUtils.toByteArray(new FileReader(this.getWaterMeterLogWriter().getFile()));
        } catch (Exception ex) {
            logger.warn("could not open meter log file",ex);
            return new long[]{0,0};
        }

        StringBuffer fString = new StringBuffer();
        for ( byte b : inputData ){
            if( (int)b<10 ) {
                continue;
            }
            fString.append((char)b);
        }
        String[] lines = fString.toString().split("\n");
        long[] prev = new long[lines.length];
        for (int i = 0; i < lines.length; i++) {
            if (lines[i]!=null) {
                String[] lineData = lines[i].split(",");
                prev[i] = Long.parseLong(lineData[1]);
            }
        }
        this.setPrevMetersArray(prev);
        return prev;
    }

    public void writeLogFile(String buffer) {
		try {
			this.logWriter.write(this.daiPrefix + buffer);		
		} catch (IOException e) {
			logger.warn("Failed to write '" + buffer + "' to log file", e);
		}
		logger.info("Wrote log to file");
    }

	public void setPrevMeters (long meter1, long meter2) {
		this.prevMeter1 = meter1;
		this.prevMeter2 = meter2;
	}
	public long[] getPrevMeters () {
		return new long[] {prevMeter1, prevMeter2};
	}
	public String getWaterMeterId () {
		if (!(waterMeterId == null)) {
			return waterMeterId;
		}
		return "";
	}
	public void setWaterMeterId (String id) {
		this.waterMeterId = id;
	}
	public SerialPort getSerialPort() {
		if (serialPort != null) {
			return this.serialPort;
		}
		return null;
	}
	public void setSerialPort(SerialPort port) {
		this.serialPort = port;
	}
	public int getDaiNum() {
		if (daiNum!=0) {
			return this.daiNum;
		}
		return -1;
	}
	public void setDaiNum(int num) {
		this.daiNum = num;
	}

    public LogWriterInterface getLogWriter() {
        return this.logWriter;
    }

    public void setLogWriter(LogWriterInterface lwi) {
        this.logWriter = lwi;
    }

    public Path getLogFilePath() {
        return logFilePath;
    }
    public void setLogFilePath(Path logFilePath) {
        this.logFilePath = logFilePath;
    }
    public LogWriterInterface getWaterMeterLogWriter() {
        return waterMeterLogWriter;
    }
    public void setWaterMeterLogWriter(LogWriterInterface waterMeterLogWriter) {
        this.waterMeterLogWriter = waterMeterLogWriter;
    }
    public void setPrevMetersArray (long[] prevMetersArray) {
        this.prevMeters = prevMetersArray;
    }
    public long[] getPrevMetersArray() {
        return prevMeters;
    }

    private long[] parseMetersFromResponse(byte[] response) {
		long[] result = new long[2];
		String meter1 = "";
		String meter2 = "";
		
		for (int i = 0; i < 16; i++) {
            int value = response[meterDataStartLocation+i];
            value = value > 0 ? value : value + 128;
			if (i < 8) meter1 += value - 48;
			else meter2 += value - 48;
		}
		result[0] = Long.parseLong(meter1);
		result[1] = Long.parseLong(meter2);
		return result;
	}
	private String parseIdFromResponse(byte[] response) {
		String result = "";
		int[] id = new int[idSize];
		
		for (int i = 0; i < id.length; i++) {
            int value = response[idStartLocation+i];
            value = value > 0 ? value : value + 128;
			result += value - 48;
		}
		return result;
	}
    private byte[] parseStringToByteArray(String in) {
        int i = idSize;
        byte[] idBytes = new byte[i];
        while (i > 0) {
            idBytes[i-1] = (byte) (0x00 + in.charAt(i-1));
            i--;
        }
        return idBytes;
    }
	private byte[] createRequestString (byte[] id) {
		byte[] request = Arrays.copyOf(requestBytes, requestBytes.length);
		int idLength = id.length;
		int requestLength = request.length;
		if (idLength + frontPadding + backPadding != requestLength) {
			String msg = "id size does not fit into request size";
			logger.warn(msg);
			return request;
		}
		for (int i = requestLength - backPadding; i > frontPadding; i--) {
			request[i-1] += id[idLength-1];
			idLength--;
		}
		return request;
	}
	private String getSystemTime() {
        return dateAndTimeFormat.format(System.currentTimeMillis());
	}
    private String getSystemTimeAndDate() {
        return dateAndTimeFormat.format(System.currentTimeMillis());
    }


    //unused stubs
	public String getRemoteDaiId() {
		return null;
	}
	public String setRemoteDaiId(int id) {
		return null;
	}
	public String clearPortBuffer() {
		return null;
	}
	public String readClock() {
		return null;
	}
	public String setClock() {
        byte[] request = this.createRequestString(this.parseStringToByteArray(this.getWaterMeterId()));
        SerialPort port = this.getSerialPort();
        byte[] buffer = null;
        String output = "";
        try {
            port.writeBytes(request);
            Thread.sleep(200);
            buffer = port.readBytes(responseLength);
            output = new String(buffer, "UTF-8");
            logger.info(output);
            Thread.sleep(80);
            port.writeBytes(passwordBytes);
            Thread.sleep(200);
            output = port.readHexString();
            logger.info(output);
            Thread.sleep(80);

            port.writeBytes(timeBytes);
            Thread.sleep(200);
            output = port.readHexString();
            logger.info(output);

            Thread.sleep(80);
            port.writeBytes(closeBytes);
            Thread.sleep(80);
            port.writeBytes(request);
            Thread.sleep(200);
            buffer = port.readBytes(responseLength);
            output = buffer!=null?new String(buffer, "UTF-8"):"";
            logger.info(output);
        } catch (Exception e) {
            String msg = "couldn't complete send request";
            logger.warn(msg, e);
            return msg + e;
        }
        return output;
	}
	public String sendStdRequest() {
		return null;
	}
	public String sendXerosRequest() {
		return null;
	}
	public String sendWaterRequest() {
		return null;
	}

    @Override
    public String sendXerosWaterRequest() throws Exception {
        return null;
    }

    public boolean ping() {
		return false;
	}

    @Override public void setXerosWasherThing(XerosWasherThing thing) {

    }

    @Override public String initXerosWaterRequest() {
        return null;
    }

    @Override public long[] calculateXerosWaterLog(String buffer) {
        return new long[0];
    }

    public String initWaterRequest() {return null;}
    public void writeWaterOnlyLog(long[] meters) {

    }
    public void writeWaterOnlyXerosLog(long[] meters) {

    }

    public String getConfig() {
        return null;
    }

    public boolean sendMachineStatus() {
        byte status;
        Date lastLogDate = new Date();
        if (lastLogSentTime == null) {
            lastLogSentTime = getSystemTimeAndDate();
        }
        try {
            lastLogDate = dateAndTimeFormat.parse(lastLogSentTime);
        } catch (ParseException e) {
            logger.warn("failed to parse last log date", e.getMessage());
        }
        Date activeMarginDate = new Date();
        activeMarginDate.setTime(System.currentTimeMillis() - 600000);

        if (lastLogDate.after(activeMarginDate))
            status = 1;
        else if (lastLogDate.before(activeMarginDate))
            status = 0;
        else
            status = -1;
        byte[] statusBytes = {0, status};
        int responseStatus = new HttpFileUploader().postMachineStatus(daiPrefix+this.getWaterMeterId(), statusBytes);
        if (responseStatus == 200) {
            logger.info("successfully sent machine status for meter: " + daiPrefix + this.getWaterMeterId());
            return true;
        }
        logger.info("failed to send machine status due to http response:" + responseStatus);
        return false;
    }
}
