package org.lockss.db;

import static org.lockss.db.SqlDbManager.DOI_COLUMN;
import static org.lockss.db.SqlDbManager.DOI_TABLE;
import static org.lockss.db.SqlDbManager.MD_ITEM_SEQ_COLUMN;
import static org.lockss.db.SqlDbManager.URL_COLUMN;
import static org.lockss.db.SqlDbManager.URL_TABLE;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.lockss.daemon.OpenUrlResolver.OpenUrlInfo;
import org.lockss.util.Logger;

public class SqlOpenUrlResolverDbManager implements OpenUrlResolverDbManager {
	
	private static Logger log = Logger.getLogger("OpenUrlResolver");
	private SqlDbManager sqlDbManager = null;

	public SqlOpenUrlResolverDbManager(SqlDbManager sqlDbManager) {
		this.sqlDbManager = sqlDbManager;
		// TODO Auto-generated constructor stub
	}

	@Override
	public OpenUrlInfo resolveFromDoi(String doi) {
	    String url = null;
	    Connection conn = null;
	    try {
	      conn = sqlDbManager.getConnection();

	      String query = "select u." + URL_COLUMN
		  + " from " + URL_TABLE + " u,"
		  + DOI_TABLE + " d"
		  + " where u." + MD_ITEM_SEQ_COLUMN + " = d." + MD_ITEM_SEQ_COLUMN
		  + " and upper(d." + DOI_COLUMN + ") = ?";
	      
	      PreparedStatement stmt = sqlDbManager.prepareStatement(conn, query);
	      stmt.setString(1, doi.toUpperCase());
	      ResultSet resultSet = sqlDbManager.executeQuery(stmt);
	      if (resultSet.next()) {
	        url = resultSet.getString(1);
	      }
	    } catch (SQLException ex) {
	      log.error("Getting DOI:" + doi, ex);
	      
	    } finally {
	      SqlDbManager.safeRollbackAndClose(conn);
	    }
	    return new OpenUrlInfo(url, null, OpenUrlInfo.ResolvedTo.ARTICLE);
	}

}
