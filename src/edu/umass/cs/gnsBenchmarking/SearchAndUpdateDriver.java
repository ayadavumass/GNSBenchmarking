package edu.umass.cs.gnsBenchmarking;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.umass.cs.gnsclient.client.GNSClient;
import edu.umass.cs.gnsclient.client.util.GuidEntry;

public class SearchAndUpdateDriver
{
	// 100 seconds, experiment runs for 100 seconds
	public static 	 long EXPERIMENT_TIME						= 100000;
	
	// 1% loss tolerance
	public static final double INSERT_LOSS_TOLERANCE			= 0.0;
	
	// 1% loss tolerance
	public static final double UPD_LOSS_TOLERANCE				= 0.5;
	
	// 1% loss tolerance
	public static final double SEARCH_LOSS_TOLERANCE			= 0.5;
	
	// after sending all the requests it waits for 100 seconds 
	public static final int WAIT_TIME							= 100000;
	
	public static final double ATTR_MIN 						= 1.0;
	public static final double ATTR_MAX 						= 1500.0;
	
	public static final String attrPrefix						= "attr";
	
	public static double numUsers 								= -1;
	
	//2% of domain queried
	//public static final double percDomainQueried				= 0.35;
	
	public static String guidPrefix								= "UserGUID";
	
	//public static HashMap<String, UserRecordInfo> userInfoHashMap;
	public static ExecutorService taskES;
	
	public static int myID;
	
	//public static ContextServiceClient<String> csClient;
	//public static GNSClient gnsClient;
	
	// per sec
	public static double initRate								= 1.0;
	public static double requestRate							= 1.0;
	
	public static int numAttrs									= 1;
	
	public static int numAttrsInQuery							= 1;
	
	public static double rhoValue								= 0.5;
	
	public static boolean userInitEnable						= true;
	
	public static double predicateLength						= 0.5;
	
	public static int threadPoolSize							= 1;
	
	public static List<GuidEntry> listOfGuidEntries				= null;
	public static final Object guidInsertLock					= new Object();
	
	public static Queue<GNSClient> gnsClientQueue				= new LinkedList<GNSClient>();
	public static final Object queueLock						= new Object();
	
	public static void main( String[] args ) throws Exception
	{
		numUsers 		  = Double.parseDouble(args[0]);
		myID 			  = Integer.parseInt(args[1]);
		initRate 		  = Double.parseDouble(args[2]);
		requestRate   	  = Double.parseDouble(args[3]);
		numAttrs 		  = Integer.parseInt(args[4]);
		numAttrsInQuery   = Integer.parseInt(args[5]);
		rhoValue 		  = Double.parseDouble(args[6]);
		
		userInitEnable	  = Boolean.parseBoolean(args[7]);
		predicateLength   = Double.parseDouble(args[8]);
		//queryExpiryTime   = Long.parseLong(args[20]);
		threadPoolSize    = Integer.parseInt(args[9]);
		
		
		System.out.println("Search and update client started ");
		guidPrefix = guidPrefix+myID;
		
		for(int i=0; i<threadPoolSize; i++)
		{
			GNSClient gnsClient = new GNSClient();
			gnsClientQueue.add(gnsClient);
		}
		
		System.out.println("[Client connected to GNS]\n");
		// per 1 ms
		//locationReqsPs = numUsers/granularityOfGeolocationUpdate;
		//userInfoHashMap = new HashMap<String, UserRecordInfo>();
		//taskES = Executors.newCachedThreadPool();
		
		
		taskES = Executors.newFixedThreadPool(threadPoolSize);
		
		listOfGuidEntries = new LinkedList<GuidEntry>();
		if( userInitEnable )
		{
			long start 	= System.currentTimeMillis();
			new UserInitializationClass().initializaRateControlledRequestSender();
			long end 	= System.currentTimeMillis();
			System.out.println(numUsers+" initialization complete "+(end-start));
		}
		
		BothSearchAndUpdate bothSearchAndUpdate = null;
		
		bothSearchAndUpdate = new BothSearchAndUpdate();
		new Thread(bothSearchAndUpdate).start();
		
		bothSearchAndUpdate.waitForThreadFinish();
		double avgUpdateLatency = bothSearchAndUpdate.getAverageUpdateLatency();
		double avgSearchLatency = bothSearchAndUpdate.getAverageSearchLatency();
		long numUpdates = bothSearchAndUpdate.getNumUpdatesRecvd();
		long numSearches = bothSearchAndUpdate.getNumSearchesRecvd();
		double avgResultSize = bothSearchAndUpdate.getAvgResultSize();
		System.out.println("avgUpdateLatency "+avgUpdateLatency
				+" avgSearchLatency "+avgSearchLatency
				+" numUpdates "+numUpdates
				+" numSearches "+numSearches
				+" avgResultSize "+avgResultSize);
		
		System.exit(0);
	}
	
	public static GNSClient getGNSClient()
	{
		synchronized(queueLock)
		{
			while(gnsClientQueue.size() == 0)
			{
				try {
					queueLock.wait();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return gnsClientQueue.poll();
		}
	}
	
	
	public static void returnGNSClient(GNSClient gnsClient)
	{
		synchronized(queueLock)
		{
			gnsClientQueue.add(gnsClient);
			queueLock.notify();
		}
	}
	
	public static String getSHA1(String stringToHash)
	{
		MessageDigest md = null;
		try
		{
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}
       
	   md.update(stringToHash.getBytes());
 
       byte byteData[] = md.digest();
 
       //convert the byte to hex format method 1
       StringBuffer sb = new StringBuffer();
       for (int i = 0; i < byteData.length; i++) 
       {
       		sb.append(Integer.toString
       				((byteData[i] & 0xff) + 0x100, 16).substring(1));
       }
       String returnGUID = sb.toString();
       return returnGUID.substring(0, 40);
	}
}