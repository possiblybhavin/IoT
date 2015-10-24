package com.elyxor.xeros.ldcs.reliagate;


import com.elyxor.xeros.ldcs.AppConfiguration;
import com.elyxor.xeros.ldcs.thingworx.ThingWorxClient;
import net.wimpi.modbus.Modbus;
import net.wimpi.modbus.io.ModbusTCPTransaction;
import net.wimpi.modbus.msg.ModbusRequest;
import net.wimpi.modbus.msg.ModbusResponse;
import net.wimpi.modbus.net.TCPMasterConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

/**
 * Created by will on 7/7/15.
 */
public class ReliagatePortManager implements ReliagatePortManagerInterface {
    /* The important instances of the classes mentioned before */
    TCPMasterConnection con = null; //the connection
    ModbusTCPTransaction trans = null; //the transaction
    ModbusRequest req = null; //the request
    ModbusResponse res = null; //the response

    private static final Logger logger = LoggerFactory.getLogger(ReliagatePortManager.class);
    Integer portCount = AppConfiguration.getReliagatePortCount();

    /* Variables for storing the parameters */
    InetAddress[] addresses = new InetAddress[portCount]; //the slave's address
    TCPMasterConnection[] connections = new TCPMasterConnection[portCount];
    ReliagatePort[] ports = new ReliagatePort[portCount];

    int port = Modbus.DEFAULT_PORT;

    public ThingWorxClient mClient;

    @Override public void init() {
        logger.info("Reliagate Port Manager Init: PortCount: " + portCount);

        try {
            for (int i = 0; i < portCount; i++) {
                logger.info("Reliagate Port Manager Init: Adding Port: " + i);

                int addr = 254 - i;
                addresses[i] = InetAddress.getByName("192.168.127."+addr);
                connections[i] = new TCPMasterConnection(addresses[i]);
                connections[i].setPort(port);
                logger.info("Reliagate Port Manager Init: Connecting Port with address: " + addresses[i]);
                logger.info("Reliagate Port Manager Init: Connecting Port with connection: " + connections[i]);
                logger.info("Reliagate Port Manager Init: Connecting Port with port: " + connections[i].getPort());

                connections[i].connect();

                ports[i] = new ReliagatePort(connections[i], i+1, mClient);
                logger.info("Reliagate Port Manager Init: Connected, beginning Polling");

                ports[i].startPolling(false);
            }
        } catch (Exception e) {
            logger.warn(e.getMessage() != null ? e.getMessage() : "");
        }
    }

    public void setThingWorxClient(ThingWorxClient client) {
        mClient = client;
    }

}
