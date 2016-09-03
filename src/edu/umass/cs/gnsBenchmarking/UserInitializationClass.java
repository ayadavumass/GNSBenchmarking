package edu.umass.cs.gnsBenchmarking;

import java.util.Random;

import org.json.JSONObject;

public class UserInitializationClass extends AbstractRequestSendingClass
{
	// different random generator for each variable, as using one for all of them
	// doesn't give uniform properties.
	private final Random initRand;
	
	public UserInitializationClass()
	{
		super( SearchAndUpdateDriver.INSERT_LOSS_TOLERANCE );
		initRand = new Random(SearchAndUpdateDriver.myID*100);
	}
	
	private void sendAInitMessage(int guidNum) throws Exception
	{	
		JSONObject attrValJSON = new JSONObject();
		
		double attrDiff   = SearchAndUpdateDriver.ATTR_MAX-SearchAndUpdateDriver.ATTR_MIN;
		
		for( int i=0;i<SearchAndUpdateDriver.numAttrs;i++ )
		{
			String attrName = SearchAndUpdateDriver.attrPrefix+i;
			double attrVal  = SearchAndUpdateDriver.ATTR_MIN 
					+ attrDiff * initRand.nextDouble();
			attrValJSON.put(attrName, attrVal);
		}
		
		String accountAlias = SearchAndUpdateDriver.guidPrefix+guidNum+"@gmail.com";
		
		
		InitTask initTask = new InitTask(attrValJSON, accountAlias, this);
		SearchAndUpdateDriver.taskES.execute(initTask);
	}
	
	public void initializaRateControlledRequestSender() throws Exception
	{	
		this.startExpTime();
		double reqspms = SearchAndUpdateDriver.initRate/1000.0;
		long currTime = 0;
		
		// sleep for 100ms
		double numberShouldBeSentPerSleep = reqspms*100.0;
		
		double totalNumUsersSent = 0;
		
		while(  totalNumUsersSent < SearchAndUpdateDriver.numUsers  )
		{
			for(int i=0; i<numberShouldBeSentPerSleep; i++ )
			{
				sendAInitMessage((int)totalNumUsersSent);
				totalNumUsersSent++;
				numSent++;
				assert(numSent == totalNumUsersSent);
				if(totalNumUsersSent >= SearchAndUpdateDriver.numUsers)
				{
					break;
				}
			}
			if(totalNumUsersSent >= SearchAndUpdateDriver.numUsers)
			{
				break;
			}
			currTime = System.currentTimeMillis();
			
			double timeElapsed = ((currTime- expStartTime)*1.0);
			double numberShouldBeSentByNow = timeElapsed*reqspms;
			double needsToBeSentBeforeSleep = numberShouldBeSentByNow - numSent;
			if(needsToBeSentBeforeSleep > 0)
			{
				needsToBeSentBeforeSleep = Math.ceil(needsToBeSentBeforeSleep);
			}
			
			for(int i=0;i<needsToBeSentBeforeSleep;i++)
			{
				sendAInitMessage((int)totalNumUsersSent);
				totalNumUsersSent++;
				numSent++;
				assert(numSent == totalNumUsersSent);
				if(totalNumUsersSent >= SearchAndUpdateDriver.numUsers)
				{
					break;
				}
			}
			if(totalNumUsersSent >= SearchAndUpdateDriver.numUsers)
			{
				break;
			}
			Thread.sleep(100);
		}
		
		long endTime = System.currentTimeMillis();
		double timeInSec = ((double)(endTime - expStartTime))/1000.0;
		double sendingRate = (numSent * 1.0)/(timeInSec);
		System.out.println("UserInit eventual sending rate "+sendingRate);
		
		waitForFinish();
		//Thread.sleep(WAIT_TIME);
		double endTimeReplyRecvd = System.currentTimeMillis();
		double sysThrput= (numRecvd * 1000.0)/(endTimeReplyRecvd - expStartTime);
		
		System.out.println("UserInit result:Goodput "+sysThrput);	
		//System.exit(0);
	}
	
	@Override
	public void incrementUpdateNumRecvd(String userGUID, long timeTaken) 
	{
		synchronized(waitLock)
		{
			numRecvd++;
			System.out.println("UserInit reply recvd "+userGUID+" time taken "+timeTaken+
					" numSent "+numSent+" numRecvd "+numRecvd);
			//if(currNumReplyRecvd == currNumReqSent)
			if(checkForCompletionWithLossTolerance(numSent, numRecvd))
			{
				waitLock.notify();
			}
		}
	}

	@Override
	public void incrementSearchNumRecvd(int resultSize, long timeTaken)
	{
	}
}