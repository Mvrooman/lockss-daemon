package org.lockss.db;

import org.lockss.daemon.OpenUrlResolver.OpenUrlInfo;

public interface OpenUrlResolverDbManager {
	
	OpenUrlInfo resolveFromDoi(String doi);
	
	OpenUrlInfo resolveFromIssn(
			String[] issn, String date, String volume, String issue, 
		    String spage, String author, String atitle);

	OpenUrlInfo resolveFromIsbn(String isbn, String date, String volume,
			String edition, String spage, String author, String atitle);

}
