package org.lockss.db;

import java.io.File;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import org.lockss.config.Configuration;
import org.lockss.config.Configuration.Differences;

import com.hp.hpl.jena.sparql.resultset.ResultSetMem;
import com.hp.hpl.jena.tdb.TDBFactory;
import org.lockss.util.Logger;

public class JenaDbManager extends DbManager {
	
	public static final String jenaDirectory = "db/jena/DB1";
	
	private Dataset dataset = null;
    static Logger log = Logger
            .getLogger(JenaDbManager.class
                    .getName());
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
        Model model = dataset.getDefaultModel();

        Query query = QueryFactory.create(queryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, model);
        // create a blank result set
        ResultSet results = new ResultSetMem();
        try {
            results = qexec.execSelect();
        } finally {
            //qexec.close(); //TODO: This needs to be closed cleanly.  Closing this results the ResultSet to be empty
        }
        return results;
    }

}
