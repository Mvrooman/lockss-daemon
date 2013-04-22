package org.lockss.plugin.internationalunionofcrystallography;

import static org.lockss.db.MongoDbManager.*;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.lockss.daemon.PluginException;
import org.lockss.db.DbManager;
import org.lockss.db.MongoDbManager;
import org.lockss.db.MongoHelper;
import org.lockss.extractor.JenaMetadataExtractor;
import org.lockss.metadata.ArticleMetadataBuffer.ArticleMetadataInfo;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.Logger;

import com.google.gson.Gson;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

public class InternationalUnionOfCrystallographyJenaMetadataExtractor implements
		JenaMetadataExtractor {
	static Logger log = Logger
			.getLogger(InternationalUnionOfCrystallographyJenaMetadataExtractor.class
					.getName());

	@Override
	public void extract(ArchivalUnit au, DbManager dbManager)
			throws IOException, PluginException {
		log.info("EXTRACTION STARTED!");
		
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
		
		// get the Jena store
		String directory = "db/jena/DB1";
		new File(directory).mkdirs();
		Dataset dataset = TDBFactory.createDataset(directory);
		Model m = dataset.getDefaultModel();
		
		// establish some properties
		String predicateBaseUri = "http://www.iucr.org/__data/iucr/cif/standard/cifstd7.html#";

        while (it.hasNext()) {
            DBObject obj = (DBObject) it.next();

            Gson gson = new Gson();
            ArticleMetadataInfo metadataJson = gson.fromJson(obj.toString(), ArticleMetadataInfo.class);
            
            Resource article = m.createResource(metadataJson.accessUrl);

            for( String key : obj.keySet()) {
            	Object value = obj.get(key);
            	if(value instanceof String){
//            		log.info("Storing " + key + " " + value);
            		Property property = m.getProperty(key);
                    if (property == null) {
                        property = m.createProperty(predicateBaseUri, key);
                    }
                    article.addProperty(property, (String) value);
            	}
            	else if (value instanceof BasicDBObject) {
            		BasicDBObject dbValue = (BasicDBObject) value;
//            		log.info("Non string values! " + dbValue.getClass() );
            		Map<String, String> dbValueMap = dbValue.toMap();
            		Iterator additionalMetadataIterator = dbValueMap.entrySet().iterator();
            		while (additionalMetadataIterator.hasNext()) {
            			Map.Entry<String, String> pair = (Map.Entry<String, String>) additionalMetadataIterator.next();
            			String pairKey = pair.getKey();
            			String pairValue = pair.getValue();
            			Property property = m.getProperty(pairKey);
            			if (property == null) {
            				property = m.createProperty(predicateBaseUri, pairKey);
            			}
            			article.addProperty(property, pairValue);
            		}
            	}
            	else if (value instanceof BasicDBList) {
            		BasicDBList dbValue = (BasicDBList) value;
            		Iterator listIt = dbValue.iterator();
            		StringBuilder str = new StringBuilder();
            		
            		// let's not save information about an empty list
            		if(!listIt.hasNext()){
            			continue;
            		}
            		
            		while(listIt.hasNext()) {
            			Object listObj = listIt.next();
            			if(!(listObj instanceof DBObject)){
            				str.append(listObj.toString() + ";");
            			}
            		}
            		
        			Property property = m.getProperty(key);
        			if (property == null) {
        				property = m.createProperty(predicateBaseUri, key);
        			}
//        			log.info("Generated Substring: " + str.substring(0, str.length() - 2));
        			// we don't want the trailing semicolon
        			article.addProperty(property, str.substring(0, str.length() - 2));
            		
            	} 
            	else {
            		log.info("Unhandled Type: " + value.getClass().toString());
            	}

            }       
        }

        //SimpleSelector Example for querying
        Property propertyForQuery = m.getProperty("_diffrn_radiation_monochromator");
        StmtIterator iter = m.listStatements(
                new SimpleSelector(null, propertyForQuery, (RDFNode) null) {
                    public boolean selects(Statement s) {
                        return s.getString().endsWith("ite");
                    }
                });
        log.info("Found Results!! - " + iter.toList().size());
        //QueryFactory Example for querying
		String queryString = "SELECT * WHERE { ?o <http://www.iucr.org/__data/iucr/cif/standard/cifstd7.html#_diffrn_radiation_monochromator> \"'silicon 111'\" }";
		Query qery = QueryFactory.create(queryString);
		
		QueryExecution qexec = QueryExecutionFactory.create(qery, m);

        try {
            ResultSet results = qexec.execSelect();
            for (; results.hasNext(); ) {
                QuerySolution soln = results.nextSolution();
                RDFNode n = soln.get("o");
                if (n.isLiteral()) {
                    log.info("" + ((Literal) n).getLexicalForm());
                } else {
                    Resource r = (Resource) n;
                    log.info("" + r.getURI());
                }
            }

        } finally {
            qexec.close();
        }
        dataset.close();
    }
	
	public void storeMetadata(Resource article, String key, Object value) {
		
	}
	
	public void storeList(Resource artcile, String key, BasicDBList value) {
		
	}
	
	public void storeString(Resource artcile, String key, String value) {
		
	}
	
	public void traverseMap(){
		
	}
}
