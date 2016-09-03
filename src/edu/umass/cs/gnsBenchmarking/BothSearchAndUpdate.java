package edu.umass.cs.gnsBenchmarking;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import org.json.JSONException;
import org.json.JSONObject;

import edu.umass.cs.gnsclient.client.util.GuidEntry;

public class BothSearchAndUpdate extends 
					AbstractRequestSendingClass implements Runnable
{
	private final Random generalRand;
	private Random searchQueryRand;
	private final Random updateRand;
	
	private double currUserGuidIndex   		= 0;
	
	private long sumResultSize				= 0;
	
	private long sumSearchLatency			= 0;
	private long sumUpdateLatency			= 0;
	
	private long numSearchesRecvd			= 0;
	private long numUpdatesRecvd			= 0;
	
	
	
	
	// we don't want to issue new search queries for the trigger exp.
	// so that the number of search queries in the experiment remains same.
	// so when number of search queries reaches threshold then we reset it to 
	// the beginning.
	//private long numberSearchesSent		= 0;
	
	public BothSearchAndUpdate()
	{
		super( SearchAndUpdateDriver.UPD_LOSS_TOLERANCE );
		generalRand = new Random(SearchAndUpdateDriver.myID);
		updateRand = new Random(SearchAndUpdateDriver.myID*100);
		
		searchQueryRand = new Random(SearchAndUpdateDriver.myID*200);
	}
	
	@Override
	public void run()
	{
		try
		{
			this.startExpTime();
			
			rateControlledRequestSender();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	private void rateControlledRequestSender() throws Exception
	{
		double reqsps = SearchAndUpdateDriver.requestRate;
		long currTime = 0;
		
		// sleep for 100ms
		double numberShouldBeSentPerSleep = reqsps;
		
		while( ( (System.currentTimeMillis() - expStartTime) < 
				SearchAndUpdateDriver.EXPERIMENT_TIME ) )
		{
			for( int i=0; i<numberShouldBeSentPerSleep; i++ )
			{
				sendRequest(numSent);
				numSent++;
			}
			currTime = System.currentTimeMillis();
			
			double timeElapsed = ((currTime- expStartTime)*1.0);
			double numberShouldBeSentByNow = (timeElapsed*reqsps)/1000.0;
			double needsToBeSentBeforeSleep = numberShouldBeSentByNow - numSent;
			if(needsToBeSentBeforeSleep > 0)
			{
				needsToBeSentBeforeSleep = Math.ceil(needsToBeSentBeforeSleep);
			}
			
			for(int i=0;i<needsToBeSentBeforeSleep;i++)
			{
				sendRequest(numSent);
				numSent++;
			}
			Thread.sleep(1000);
		}
		
		long endTime = System.currentTimeMillis();
		double timeInSec = ((double)(endTime - expStartTime))/1000.0;
		double sendingRate = (numSent * 1.0)/(timeInSec);
		System.out.println("Both eventual sending rate "+sendingRate);
		
		waitForFinish();
		double endTimeReplyRecvd = System.currentTimeMillis();
		double sysThrput= (numRecvd * 1000.0)/(endTimeReplyRecvd - expStartTime);
		
		double avgResultSize = 0;
		if( this.numSearchesRecvd > 0 )
		{
			avgResultSize = (sumResultSize/this.numSearchesRecvd);
		}
		
		System.out.println("Both result:Goodput "+sysThrput+" average resultsize "
										+avgResultSize);
	}
	
	private void sendRequest( long reqIdNum )
	{
		// send update
		if( generalRand.nextDouble() < SearchAndUpdateDriver.rhoValue )
		{
			sendQueryMessage(reqIdNum);
			//sendQueryMessageWithSmallRanges(reqIdNum);
		}
		else
		{
			sendUpdate(reqIdNum);
		}
	}
	
	private void sendUpdate(long reqIdNum)
	{
		sendALocMessage((int)currUserGuidIndex, reqIdNum);
		currUserGuidIndex++;
		currUserGuidIndex=((int)currUserGuidIndex)%SearchAndUpdateDriver.numUsers;
	}
	
	private void sendQueryMessage(long reqIdNum)
	{
		String gnsSearchQ = generateAGNSSelectQuery();
		System.out.println("gnsSearchQ "+gnsSearchQ);
		SearchTask searchTask = new SearchTask( gnsSearchQ, this );
		SearchAndUpdateDriver.taskES.execute(searchTask);
	}
	
	private String generateAGNSSelectQuery()
	{
		HashMap<String, Boolean> distinctAttrMap 
				= pickDistinctAttrs( SearchAndUpdateDriver.numAttrsInQuery, 
						SearchAndUpdateDriver.numAttrs, searchQueryRand );
		
		String gnsSearchQ = "";
		if( distinctAttrMap.size() > 1 )
		{
			gnsSearchQ = gnsSearchQ + "$and:[";
		}
		else
		{
			// nothing
		}
		
		Iterator<String> attrIter = distinctAttrMap.keySet().iterator();
		
		while( attrIter.hasNext() )
		{
			String attrName = attrIter.next();
			double attrMin = SearchAndUpdateDriver.ATTR_MIN
					+searchQueryRand.nextDouble()*
					(SearchAndUpdateDriver.ATTR_MAX - SearchAndUpdateDriver.ATTR_MIN);
		
			double predLength 
				= (SearchAndUpdateDriver.predicateLength
						*(SearchAndUpdateDriver.ATTR_MAX - SearchAndUpdateDriver.ATTR_MIN)) ;
		
			double attrMax = attrMin + predLength;
			//		double latitudeMax = latitudeMin 
			//					+WeatherAndMobilityBoth.percDomainQueried*(WeatherAndMobilityBoth.LATITUDE_MAX - WeatherAndMobilityBoth.LATITUDE_MIN);
			// making it curcular
			if( attrMax > SearchAndUpdateDriver.ATTR_MAX )
			{
				double diff = attrMax - SearchAndUpdateDriver.ATTR_MAX;
				attrMax = SearchAndUpdateDriver.ATTR_MIN + diff;
			}
			
			String predicate = getAPredicate( attrName, attrMin, 
					attrMax );
			
			// last so no AND
			if( !attrIter.hasNext() )
			{
				gnsSearchQ = gnsSearchQ + predicate+" ] ";
			}
			else
			{
				gnsSearchQ = gnsSearchQ + predicate+" , ";
			}
		}
		return gnsSearchQ;
	}
	
	private String getAPredicate(String attrName, double attrMin, double attrMax)
	{
		// normal case
		if(attrMin <= attrMax)
		{
			String query = "("+"\"~"+attrName+"\":($gt:"+attrMin+",$lt:"+attrMax+")"+")";
			return query;
		}
		else // circular query case
		{
			//$or:[("~hometown":"whoville"),("~money":($gt:0))]
			String pred1 = "("+"\"~"+attrName+"\":($gt:"+attrMin+",$lt:"
													+ SearchAndUpdateDriver.ATTR_MAX+")"+")";
			String pred2 = "("+"\"~"+attrName+"\":($gt:"+SearchAndUpdateDriver.ATTR_MIN
													+ ",$lt:"+attrMax+")"+")";
			
			String query = "("+"$or:["+pred1+","+pred2+"]" +")";
			return query;
		}
	}
	
	private HashMap<String, Boolean> pickDistinctAttrs( int numAttrsToPick, 
			int totalAttrs, Random randGen )
	{
		HashMap<String, Boolean> hashMap = new HashMap<String, Boolean>();
		int currAttrNum = 0;
		while(hashMap.size() != numAttrsToPick)
		{
			if(SearchAndUpdateDriver.numAttrs == SearchAndUpdateDriver.numAttrsInQuery)
			{
				String attrName = "attr"+currAttrNum;
				hashMap.put(attrName, true);
				currAttrNum++;
			}
			else
			{
				currAttrNum = randGen.nextInt(SearchAndUpdateDriver.numAttrs);
				String attrName = "attr"+currAttrNum;
				hashMap.put(attrName, true);
			}
		}
		return hashMap;
	}
	
	private void sendALocMessage( int currUserGuidNum, long reqIdNum )
	{
		GuidEntry guidEntry = SearchAndUpdateDriver.listOfGuidEntries.get(currUserGuidNum);
		
		int randomAttrNum = updateRand.nextInt(SearchAndUpdateDriver.numAttrs);
		double randVal = SearchAndUpdateDriver.ATTR_MIN 
				+updateRand.nextDouble()*(SearchAndUpdateDriver.ATTR_MAX - SearchAndUpdateDriver.ATTR_MIN);
		
		JSONObject attrValJSON = new JSONObject();
		try
		{
			attrValJSON.put(SearchAndUpdateDriver.attrPrefix+randomAttrNum, randVal);
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
		
		//SearchAndUpdateDriver.csClient.sendUpdate(userGUID, null, 
		//		attrValuePairs, -1);
		//System.out.println("Updating "+currUserGuidNum+" "+attrValJSON);
		UpdateTask updTask = new UpdateTask( attrValJSON, guidEntry, this );
		SearchAndUpdateDriver.taskES.execute(updTask);
	}
	
	public double getAverageUpdateLatency()
	{
		return (this.numUpdatesRecvd>0)?sumUpdateLatency/this.numUpdatesRecvd:0;
	}
	
	public double getAverageSearchLatency()
	{
		return (this.numSearchesRecvd>0)?sumSearchLatency/this.numSearchesRecvd:0;
	}
	
	public long getNumUpdatesRecvd()
	{	
		return this.numUpdatesRecvd;
	}
	
	
	public long getNumSearchesRecvd()
	{
		return this.numSearchesRecvd;
	}
	
	public long getAvgResultSize()
	{
		return (this.numSearchesRecvd>0)?this.sumResultSize/this.numSearchesRecvd:0;
	}
	
	
	@Override
	public void incrementUpdateNumRecvd(String userGUID, long timeTaken)
	{
		synchronized(waitLock)
		{
			numRecvd++;
			this.numUpdatesRecvd++;
//			System.out.println("LocUpd reply recvd "+userGUID+" time taken "+timeTaken+
//					" numSent "+numSent+" numRecvd "+numRecvd);
			//if(currNumReplyRecvd == currNumReqSent)
			this.sumUpdateLatency = this.sumUpdateLatency + timeTaken;
			if(checkForCompletionWithLossTolerance(numSent, numRecvd))
			{
				waitLock.notify();
			}
		}
	}
	
	
	@Override
	public void incrementSearchNumRecvd(int resultSize, long timeTaken)
	{
		synchronized(waitLock)
		{
			numRecvd++;
			this.numSearchesRecvd++;
			sumResultSize = sumResultSize + resultSize;
//			System.out.println("Search reply recvd size "+resultSize+" time taken "
//					+timeTaken+" numSent "+numSent+" numRecvd "+numRecvd);
			//if(currNumReplyRecvd == currNumReqSent)
			this.sumSearchLatency = this.sumSearchLatency + timeTaken;
			if( checkForCompletionWithLossTolerance(numSent, numRecvd) )
			{
				waitLock.notify();
			}
		}
	}
}