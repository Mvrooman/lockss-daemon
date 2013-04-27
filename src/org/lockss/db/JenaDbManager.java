package org.lockss.db;

import java.io.File;

import org.lockss.config.Configuration;
import org.lockss.config.Configuration.Differences;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.resultset.ResultSetMem;
import com.hp.hpl.jena.tdb.TDBFactory;

public class JenaDbManager extends DbManager {
	
	public static final String jenaDirectory = "db/jena/DB1";
	
	private Dataset dataset = null;
	
	public JenaDbManager() {
		new File(jenaDirectory).mkdirs();
		dataset = TDBFactory.createDataset(jenaDirectory);
	}

	@Override
	public void setConfig(Configuration newConfig, Configuration prevConfig,
			Differences changedKeys) {
		throw new UnsupportedOperationException();
	}

	@Override
	public OpenUrlResolverDbManager getOpenUrlResolverDbManager() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isConnectionReady() throws Exception {
		throw new UnsupportedOperationException();
	}
	
	public Dataset getDataset() {
		return dataset;
	}
	
	public ResultSet query(String queryString) {
		Query query = QueryFactory.create(queryString);
		QueryExecution qexec = QueryExecutionFactory.create(query, dataset);
		
		// create a blank result set
		ResultSet results = new ResultSetMem();
		
		try {
			results = qexec.execSelect();
		} finally { 
			qexec.close();
		}
		
		return results;
	}

}
