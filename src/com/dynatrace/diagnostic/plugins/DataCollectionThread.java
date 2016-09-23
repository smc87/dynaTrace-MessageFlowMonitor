package com.dynatrace.diagnostic.plugins;

import com.dynatrace.diagnostics.pdk.MonitorEnvironment;
import com.dynatrace.diagnostics.pdk.MonitorMeasure;
import com.ibm.mq.MQEnvironment;
import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.MQTopic;
import static com.ibm.mq.constants.CMQC.*;
import com.ibm.mq.constants.MQConstants;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class DataCollectionThread
  implements Runnable
{
  public static Object MODEL_LOCK = new Object();
  private static DataCollectionThread singleton;
  private String host;
  private int port;
  private String serverChannel;
  private String statsTopic;
  private String qMgrName;
  private String userId;
  private String passwd;
  private static final Logger log = Logger.getLogger(MessageFlowStatisticsMonitor.class.getName());
  private static boolean exceptionOccurred=false;

  private HashMap<String, HashMap<String, Long>> messageFlowMap = new HashMap<String, HashMap<String, Long>>();

  DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
  DocumentBuilder builder;
  XPathFactory factory = XPathFactory.newInstance();
  XPath xpath = this.factory.newXPath();

  private boolean KEEP_GATHERING = false;
  String messageData;
  MQQueueManager qMgr;
  MQQueue inQueue;
  MQTopic subscriber;
  int openOptions;

  public DataCollectionThread()
  {
    try
    {
      this.builder = this.domFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    }
  }

  public void setHost(String host) {
	  this.host = host;
  }
  
  public void setPort(int port) {
	  this.port = port;
  }
  
  public void setServerChannel(String serverChannel) {
	  this.serverChannel = serverChannel;
  }
  
  public void setStatsTopic(String statsTopic) {
	  this.statsTopic = statsTopic;
  }
  
  public void setQMgrName(String qMgrName) {
	  this.qMgrName = qMgrName;
  }
  
  public synchronized DataCollectionThread getInstance() {
    if (singleton == null) {
      singleton = new DataCollectionThread();
    }
    return singleton;
  }

  public boolean getExceptionOccurred() {
	  return exceptionOccurred;
  }
  
  public void setGathering(boolean b) {
	  this.KEEP_GATHERING = b;
  }
  
  public boolean getGathering() {
	  return this.KEEP_GATHERING;
  }
  
  public void setUserId(String userId) {
	  this.userId = userId;
  }
  
  public void setPasswd(String pass) {
	  this.passwd = pass;
  }

  @SuppressWarnings("unchecked")
public void setup() throws Exception 
  {
  	try {
		com.ibm.mq.MQEnvironment.hostname = host;
		com.ibm.mq.MQEnvironment.channel = serverChannel;
		com.ibm.mq.MQEnvironment.port = port;
		if ( userId != null) {
			com.ibm.mq.MQEnvironment.userID = userId;
		}

		if ( passwd != null) {
			com.ibm.mq.MQEnvironment.password = passwd;
		}
		
		Hashtable<String, String> props = (Hashtable<String, String>)MQEnvironment.properties;
		props.put(MQConstants.TRANSPORT_PROPERTY, MQConstants.TRANSPORT_MQSERIES);
		this.qMgr = new MQQueueManager(qMgrName);
		exceptionOccurred=false;
//		log.severe("In setup of dtcThread");
  	}
	catch (MQException e)
	{
		log.severe(e.getMessage());
		exceptionOccurred=true;
		shutdown();
		throw new Exception(e);
	}
  }
 
  public void run()
  {
//	  log.severe("In run of dct");
	  
    MQException.log = null;
    try {
    	this.openOptions = 1;
    	int count2033 = 0;

    	this.subscriber = this.qMgr.accessTopic(statsTopic, "", MQTOPIC_OPEN_AS_SUBSCRIPTION, MQSO_CREATE);

    	while (this.KEEP_GATHERING) {
/*    		MQGetMessageOptions gmo = new MQGetMessageOptions();
    		gmo.waitInterval = 20000;
    		gmo.options = MQGMO_WAIT + MQGMO_BROWSE_NEXT;

    		MQMessage recvMsg = new MQMessage();
    		try
    		{
    			this.subscriber.get(recvMsg, gmo);
    			this.messageData = recvMsg.readStringOfByteLength(recvMsg.getDataLength());
    			//System.out.println(this.messageData);

    			log.log(Level.FINER, "XML recvd=" + this.messageData);
    			processStatsString(this.messageData);
    			exceptionOccurred=false;
    			count2033=0;
    			gmo.options = MQGMO_WAIT + MQGMO_MSG_UNDER_CURSOR;
    			this.subscriber.get(recvMsg, gmo);
*/
    		MQGetMessageOptions gmo = new MQGetMessageOptions();
//    		gmo.options = 33;
    		gmo.waitInterval = MQWI_UNLIMITED;
    		gmo.options = MQGMO_WAIT;

    		MQMessage recvMsg = new MQMessage();
    		try
    		{
    			this.subscriber.get(recvMsg, gmo);
    			this.messageData = recvMsg.readStringOfByteLength(recvMsg.getDataLength());

    			log.log(Level.FINER, "XML recvd=" + this.messageData);
    			processStatsString(this.messageData);
    			exceptionOccurred=false;
    			//count2033=0;
//    			gmo.options = MQGMO_WAIT + MQGMO_MSG_UNDER_CURSOR;
//    			this.subscriber.get(recvMsg, gmo);
    		} catch (MQException mqe) {
    			if (mqe.getReason() == 2033) {
    				count2033++;
    				
    				//spit out message about 2033 every 5 attempts. This is done to reduce the messages in the log file.
    				if (count2033 >= 5) {
    					log.finer("2033 error occured. This means that no data is being published on/in the channel.");
    					count2033=0;
    				}
    				continue;
    			}
    			log.severe("ERROR: Message="+mqe.getMessage() + "Reason="+ mqe.getReason());
    	    	exceptionOccurred=true;
    			shutdown();
    	    	break;
    		}
    	}
    }
    catch (MQException e)
    {
    	log.severe(e.getMessage());
    	exceptionOccurred=true;
    	shutdown();
    } catch (EOFException e) {
    	log.severe(e.getMessage());
    	exceptionOccurred=true;
    	shutdown();
    } catch (IOException e) {
    	log.severe(e.getMessage());
    	exceptionOccurred=true;
    	shutdown();
    }
  }

  private void processStatsString(String statsString)
  {
	  try
	  {
		  Document doc = this.builder.parse(new InputSource(new ByteArrayInputStream(statsString.getBytes("utf-8"))));

		  XPathExpression nodesXPath = this.xpath.compile("//MessageFlow");
		  NodeList nodesNodes = (NodeList)nodesXPath.evaluate(doc, XPathConstants.NODESET);
		  Node messageFlowNode = nodesNodes.item(0);
		  NamedNodeMap messageFlowNodeAttributes = messageFlowNode.getAttributes();

		  String messageFlowNameLabel = messageFlowNodeAttributes.getNamedItem("MessageFlowName").getNodeValue();
		  //log.severe("MessageFlowNameLabel=" + messageFlowNameLabel);

		  String messageFlowName = "";
		  if (messageFlowNameLabel.indexOf('.') != -1)
		  {
			  messageFlowName = messageFlowNameLabel.substring(messageFlowNameLabel.lastIndexOf('.') + 1, messageFlowNameLabel.length());
		  } else {
			  messageFlowName = messageFlowNameLabel;
		  }

		  Node accountingOriginNode = messageFlowNodeAttributes.getNamedItem("AccountingOrigin");
		  String accountingOrigin = null;
		  if (accountingOriginNode != null)
			  accountingOrigin = accountingOriginNode.getNodeValue();
		  else {
			  log.severe("Could not find AccountingOrigin for flow");
			  System.err.println("Could not find AccountingOrigin for flow");
		  }

		  String accountingOriginToGraph = "Anonymous";
		  if ((accountingOrigin != null) && (accountingOriginToGraph.trim().length() > 0) && 
				  (!accountingOrigin.equalsIgnoreCase(accountingOriginToGraph))) {
			  System.out.println("Not processing Stats data for flow:" + messageFlowName + " AccountingOrigin:" + accountingOrigin);
			  return;
		  }


		  HashMap<String, Long> messageFlowStatistics = new HashMap<String, Long>();
		  FlowStatElements[] flowStatElements = FlowStatElements.values();
		  for (int p = 0; p < flowStatElements.length; p++) {
			  FlowStatElements flowStatElement = flowStatElements[p];
			  String statKey = flowStatElement.getKey();
			  if (flowStatElement.getIsCalculated()) {
				  continue;
			  }
			  Node statItemNode = messageFlowNodeAttributes.getNamedItem(statKey);
			  if (statItemNode != null)
				  messageFlowStatistics.put(statKey, Long.parseLong(statItemNode.getNodeValue()));
			  //					  statisticsDataItem.addStat(graphableFlowStat, Long.parseLong(statItemNode.getNodeValue()));
			  else {
				  System.out.println("Stat attribute " + statKey + " didnt exist");
			  }
		  }

		  long totalInputMessages = messageFlowStatistics.get(FlowStatElements.TOTAL_INPUT_MESSAGES.getKey()).longValue();
		  long totalCPUTime = messageFlowStatistics.get(FlowStatElements.TOTAL_CPU_TIME.getKey()).longValue();
		  long totalElapsedTime = messageFlowStatistics.get(FlowStatElements.TOTAL_ELAPSED_TIME.getKey()).longValue();

		  long cpuTimePerMessage = 0L;
		  long elapsedTimePerMessage = 0L;
		  if ((totalElapsedTime > 0L) && (totalInputMessages > 0L)) {
			  cpuTimePerMessage = totalCPUTime / totalInputMessages;
			  elapsedTimePerMessage = totalElapsedTime / totalInputMessages;
		  }
		  messageFlowStatistics.put(FlowStatElements.CPU_TIME_PER_MESSAGE.getKey(), new Long(cpuTimePerMessage));
		  messageFlowStatistics.put(FlowStatElements.ELAPSED_TIME_PER_MESSAGE.getKey(), new Long(elapsedTimePerMessage));

		  populate(messageFlowName, messageFlowStatistics);
	  } catch (SAXException e) {
		  e.printStackTrace();
	  } catch (IOException e) {
		  e.printStackTrace();
	  } catch (XPathExpressionException e) {
		  e.printStackTrace();
	  }
  }
  
/*  public static HashMap<String, Long> getMessageFlowData(String messageFlowName) {
	  synchronized (MODEL_LOCK) {
		  return messageFlowMap.get(messageFlowName);
	  }
  }
*/
  private void populate(String messageFlowName, HashMap<String, Long> statistics) {
	  synchronized(MODEL_LOCK) {
	  //log.severe("Putting info about " + messageFlowName + " into hashmap");
		  messageFlowMap.put(messageFlowName, statistics);
	  //log.severe("Size of HashMap in dtc=" + messageFlowMap.size());
	  }
  }
  
  public HashMap<String, HashMap<String, Long>> getMessageFlowMap() {
	  synchronized(MODEL_LOCK) {
		  HashMap<String, HashMap<String, Long>> tempMap = messageFlowMap;
		  messageFlowMap = new HashMap<String, HashMap<String, Long>>();
		  return tempMap;
	  }
	  //log.severe("Size of HashMap before Return in dtc=" + messageFlowMap.size());
  }
  
  public void populateDT(MonitorEnvironment env) {
	  FlowStatElements[] flowStatElements = FlowStatElements.values();
	  for (int p = 0; p < flowStatElements.length; p++) {
		  FlowStatElements flowStatElement = flowStatElements[p];
		  String metricKey = flowStatElement.getKey();
		  Set<String> keySet = messageFlowMap.keySet();
		  for (String messageFlowName: keySet) {
			  //log.info("Metric=" + metricKey + " flowName=" + messageFlowName);
			  Collection<MonitorMeasure> monitorMeasures = env.getMonitorMeasures("Message Flow Group", metricKey);
			  HashMap<String, Long> statistics = messageFlowMap.get(messageFlowName);
			  for (MonitorMeasure subscribedMonitorMeasure : monitorMeasures) {
				  MonitorMeasure dynamicMeasure = env.createDynamicMeasure(subscribedMonitorMeasure, "Message Flow Name", messageFlowName);
				  //log.info("Value=" + statistics.get(metricKey));
				  dynamicMeasure.setValue(statistics.get(metricKey));
			  }
		  }
	  }
	  
/*	  if ( stats != null && stats.size() > 0) {
		  for (Map.Entry<String, Long> entry: stats.entrySet()) {
			  String key = (String) entry.getKey();
			  long value = entry.getValue().longValue();

			  Collection<MonitorMeasure> measureFlowStatusMeasures = env
			  .getMonitorMeasures(METRIC_MESSAGE_FLOW_GROUP,
					  key);
			  for (MonitorMeasure mm : measureFlowStatusMeasures) {
				  mm.setValue(value);
			  }
		  }
	  }
*/	  
  }

  public void shutdown() {
	  try {
		  if (this.subscriber != null) {
			  this.subscriber.close();
		  }
		  if (this.qMgr != null)
			  this.qMgr.disconnect();
	  }
	  catch (MQException e) {
		  log.severe(e.getMessage());
		  e.printStackTrace();
	  }
  }

}