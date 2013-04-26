package org.lockss.plugin.internationalunionofcrystallography;

import static org.lockss.db.MongoDbManager.AUS_COLLECTION;
import static org.lockss.db.MongoDbManager.PLUGIN_COLLECTION;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.apache.jena.atlas.logging.Log;
import org.lockss.daemon.PluginException;
import org.lockss.db.DbManager;
import org.lockss.db.MongoDbManager;
import org.lockss.db.MongoHelper;
import org.lockss.extractor.JenaInferenceEngine;
import org.lockss.metadata.ArticleMetadataBuffer.ArticleMetadataInfo;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArticleFiles;
import org.lockss.util.Logger;

import com.google.gson.Gson;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.SimpleSelector;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

public class InternationalUnionOfCrystallographyJenaInferenceEngine implements
		JenaInferenceEngine {

	static Logger log = Logger
			.getLogger(InternationalUnionOfCrystallographyJenaInferenceEngine.class
					.getName());

	private final String jenaDirectory = "db/jena/DB1";

	private Dataset dataset = null;
	private Model model = null;
	private Resource article = null;

	/**
	 * 
	 */
	public InternationalUnionOfCrystallographyJenaInferenceEngine() {
		new File(jenaDirectory).mkdirs();
		dataset = TDBFactory.createDataset(jenaDirectory);
		model = dataset.getDefaultModel();
	}

	@Override
	public void extract(ArchivalUnit au, DbManager dbManager)
			throws IOException, PluginException {
		log.info("--- INFERENCE STARTED ---");
		
		
		
		String auId = au.getAuId();
		String pluginId = au.getPluginId();
		
		DB mongoDatabase = ((MongoDbManager)dbManager).getDb();
		
		// find plugin sequence number
		DBCollection collection = mongoDatabase.getCollection(PLUGIN_COLLECTION);
		BasicDBObject query = new BasicDBObject("pluginId", pluginId);
		DBObject result = collection.findOne(query);
		Long pluginSeq = MongoHelper.readLong(result, "longId");
		
		// find raw au
		DBCollection auCollection = mongoDatabase.getCollection(AUS_COLLECTION);
		DBObject finalQuery = QueryBuilder.start().and(
                QueryBuilder.start("pluginSeq").is(pluginSeq).get(),
                QueryBuilder.start("auKey").is(auId).get()).get();
		DBObject auTarget = auCollection.findOne(finalQuery);
		
		// get the list of metadata
		BasicDBList articleMetadata = (BasicDBList) auTarget.get("articleMetadata");
		Iterator<Object> it = articleMetadata.iterator();

        while (it.hasNext()) {
            DBObject obj = (DBObject) it.next();

            Gson gson = new Gson();
            ArticleMetadataInfo metadataJson = gson.fromJson(obj.toString(), ArticleMetadataInfo.class);
            
            
			// Property propertyForQuery = model.getProperty("");
			article = model
					.getResource(metadataJson.accessUrl);
			StmtIterator iter = model.listStatements(new SimpleSelector(
					article, null, (RDFNode) null) {
				public boolean selects(Statement s) {
					return true;
				}
			});

			log.info("Found Results!! - " + iter.toList().size());
            
                   
        }


//		String query = "SELECT ?o ?p ?s WHERE { ?o ?p ?s . FILTER (contains (?o , 'bg2370')) }";
//				//"SELECT * WHERE { ?o ?p ?s . FILTER (contains(?o, 'http://scripts.iucr.org/cgi-bin/sendcifsu2236sup1')) }";
//		Query qery = QueryFactory.create(query);
//
//		QueryExecution qexec = QueryExecutionFactory.create(qery, model);
//
//		try {
//			ResultSet results = qexec.execSelect();
//			for (; results.hasNext();) {
//				QuerySolution soln = results.nextSolution();
//				RDFNode n = soln.get("o");
//				if (n.isLiteral()) {
//					log.info("" + ((Literal) n).getLexicalForm());
//				} else {
//					Resource r = (Resource) n;
//					log.info("" + r.getURI());
//		
//					log.info("" + r.getNameSpace());
//				}
//			}
//
//		} finally {
//			qexec.close();
//		}

	}

}
