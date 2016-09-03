package edu.umass.cs.gnsBenchmarking;

import org.json.JSONObject;

import edu.umass.cs.gnsclient.client.GNSClient;
import edu.umass.cs.gnsclient.client.GNSCommand;
import edu.umass.cs.gnsclient.client.util.GuidEntry;
import edu.umass.cs.gnsclient.client.util.GuidUtils;

/**
 * Class implements the task used for 
 * update info in GNS, which is blocking so this 
 * class's object is passed in executor service
 * @author adipc
 */
public class InitTask implements Runnable
{
	private final JSONObject attrValuePairs;
	private final String accountGuidAlias;
	private final AbstractRequestSendingClass requestSendingTask;
	
	public InitTask( JSONObject attrValuePairs, String accountGuidAlias,
			AbstractRequestSendingClass requestSendingTask )
	{
		this.attrValuePairs = attrValuePairs;
		this.accountGuidAlias = accountGuidAlias;
		this.requestSendingTask = requestSendingTask;
	}
	
	@Override
	public void run()
	{
		try
		{

			GNSClient gnsClient = SearchAndUpdateDriver.getGNSClient();
			GuidEntry guidEntry = GuidUtils.lookupOrCreateAccountGuid
					( gnsClient, accountGuidAlias,
					"password", true );
			
			synchronized( SearchAndUpdateDriver.guidInsertLock )
			{
				SearchAndUpdateDriver.listOfGuidEntries.add(guidEntry);
			}
			long start = System.currentTimeMillis();
			gnsClient.execute(GNSCommand.update(guidEntry, attrValuePairs));
			long end = System.currentTimeMillis();
			requestSendingTask.incrementUpdateNumRecvd(guidEntry.guid, end-start);
			SearchAndUpdateDriver.returnGNSClient(gnsClient);
		} catch(Exception ex)
		{
			ex.printStackTrace();
		}
		catch(Error ex)
		{
			ex.printStackTrace();
		}
	}
}