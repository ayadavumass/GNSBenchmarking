package edu.umass.cs.bugreports;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.json.JSONObject;


import edu.umass.cs.gnsclient.client.GNSClient;
import edu.umass.cs.gnsclient.client.GNSCommand;
import edu.umass.cs.gnsclient.client.util.GuidEntry;
import edu.umass.cs.gnsclient.client.util.GuidUtils;
import edu.umass.cs.gnscommon.exceptions.client.ClientException;

public class GNSSelectBugReport
{
	public static final int NUMGUIDs							= 100;
	public static final int NUMATTRs							= 20;
	public static final int NUMATTRsINQUERY						= 4;
	public static final double PRED_LENGTH						= 0.5;
	public static final double ATTR_MIN							= 1.0;
	public static final double ATTR_MAX							= 1500.0;
	public static final String ATTR_PREFIX						= "attr";
	public static final String ACCOUNT_PREFIX					= "account";
	
	public static GNSClient gnsClient;
	public static List<GuidEntry> listOfGuidEntries				= null;
	
	public static Random initRand								= new Random();
		
	public static void performInsert()
	{
		for(int i=0; i<NUMGUIDs; i++)
		{
			try {
				sendAInitMessage(i);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void performSelectQueries()
	{	
		while(true)
		{
			//String query = "("+"\"~"+attrName+"\":($gt:"+ATTR_MIN+",$lt:"+ATTR_MAX+")"+")";
			String query = generateAGNSSelectQuery();
			System.out.println("Issuing query "+query+"\n");
			try
			{
				long start = System.currentTimeMillis();
				List<String> guidList  
					= (List<String>) gnsClient.execute
				(GNSCommand.selectQuery(query)).getResultList();
				long end = System.currentTimeMillis();
				System.out.println("select query time "+(end-start)+" num replies "
									+guidList.size());
			}
			catch (ClientException | IOException e) 
			{
				e.printStackTrace();
			}
			
			try 
			{
				Thread.sleep(100);
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
			}
		}
	}
	
	public static void sendAInitMessage(int guidNum) throws Exception
	{
		JSONObject attrValJSON = new JSONObject();
		
		double attrDiff   = ATTR_MAX-ATTR_MIN;
		
		for( int i=0;i<NUMATTRs;i++ )
		{
			String attrName = ATTR_PREFIX+i;
			double attrVal  = ATTR_MIN 
					+ attrDiff * initRand.nextDouble();
			attrValJSON.put(attrName, attrVal);
		}
		
		String accountAlias = ACCOUNT_PREFIX+guidNum+"@gmail.com";
		
		GuidEntry guidEntry = GuidUtils.lookupOrCreateAccountGuid
				( gnsClient, accountAlias,
				"password", true );
		System.out.println("Account GUID for alias "+accountAlias+" created");
		
		listOfGuidEntries.add(guidEntry);
		
		long start = System.currentTimeMillis();
		gnsClient.execute(GNSCommand.update(guidEntry, attrValJSON));
		long end = System.currentTimeMillis();
		System.out.println( "Attr val for alias "+accountAlias+" complete time "
									+(end-start) );
	}
	
	private static String generateAGNSSelectQuery()
	{
		HashMap<String, Boolean> distinctAttrMap 
				= pickDistinctAttrs( NUMATTRsINQUERY, 
						NUMATTRs, initRand );
		
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
			double attrMin = ATTR_MIN+initRand.nextDouble()*
					(ATTR_MAX - ATTR_MIN);
		
			// querying 10 % of domain
			double predLength 
				= (PRED_LENGTH
						*(ATTR_MAX - ATTR_MIN)) ;
		
			double attrMax = attrMin + predLength;
			
			String predicate = getAPredicate(attrName, ATTR_MIN, ATTR_MAX);
			
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
	
	private static String getAPredicate(String attrName, double attrMin, double attrMax)
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
													+ ATTR_MAX+")"+")";
			String pred2 = "("+"\"~"+attrName+"\":($gt:"+ATTR_MIN
													+ ",$lt:"+attrMax+")"+")";
			
			String query = "("+"$or:["+pred1+","+pred2+"]" +")";
			return query;
		}
	}
	
	private static HashMap<String, Boolean> pickDistinctAttrs( int numAttrsToPick, 
			int totalAttrs, Random randGen )
	{
		HashMap<String, Boolean> hashMap = new HashMap<String, Boolean>();
		int currAttrNum = 0;
		while(hashMap.size() != numAttrsToPick)
		{
			if(NUMATTRs == NUMATTRsINQUERY)
			{
				String attrName = "attr"+currAttrNum;
				hashMap.put(attrName, true);
				currAttrNum++;
			}
			else
			{
				currAttrNum = randGen.nextInt(NUMATTRs);
				String attrName = "attr"+currAttrNum;
				hashMap.put(attrName, true);
			}
		}
		return hashMap;
	}
	
	
	public static void main(String[] args) throws IOException
	{
		gnsClient = new GNSClient();
		listOfGuidEntries = new LinkedList<GuidEntry>();
		performInsert();
		performSelectQueries();
	}
}