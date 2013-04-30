package org.lockss.db;

import static org.lockss.db.SqlDbManager.AUTHOR_NAME_COLUMN;
import static org.lockss.db.SqlDbManager.AUTHOR_TABLE;
import static org.lockss.db.SqlDbManager.AU_KEY_COLUMN;
import static org.lockss.db.SqlDbManager.AU_MD_SEQ_COLUMN;
import static org.lockss.db.SqlDbManager.AU_MD_TABLE;
import static org.lockss.db.SqlDbManager.AU_SEQ_COLUMN;
import static org.lockss.db.SqlDbManager.AU_TABLE;
import static org.lockss.db.SqlDbManager.BIB_ITEM_TABLE;
import static org.lockss.db.SqlDbManager.DATE_COLUMN;
import static org.lockss.db.SqlDbManager.DOI_COLUMN;
import static org.lockss.db.SqlDbManager.DOI_TABLE;
import static org.lockss.db.SqlDbManager.FEATURE_COLUMN;
import static org.lockss.db.SqlDbManager.ISBN_COLUMN;
import static org.lockss.db.SqlDbManager.ISBN_TABLE;
import static org.lockss.db.SqlDbManager.ISSN_COLUMN;
import static org.lockss.db.SqlDbManager.ISSN_TABLE;
import static org.lockss.db.SqlDbManager.ISSUE_COLUMN;
import static org.lockss.db.SqlDbManager.MD_ITEM_NAME_TABLE;
import static org.lockss.db.SqlDbManager.MD_ITEM_SEQ_COLUMN;
import static org.lockss.db.SqlDbManager.MD_ITEM_TABLE;
import static org.lockss.db.SqlDbManager.NAME_COLUMN;
import static org.lockss.db.SqlDbManager.PARENT_SEQ_COLUMN;
import static org.lockss.db.SqlDbManager.PLUGIN_ID_COLUMN;
import static org.lockss.db.SqlDbManager.PLUGIN_SEQ_COLUMN;
import static org.lockss.db.SqlDbManager.PLUGIN_TABLE;
import static org.lockss.db.SqlDbManager.PUBLICATION_TABLE;
import static org.lockss.db.SqlDbManager.START_PAGE_COLUMN;
import static org.lockss.db.SqlDbManager.URL_COLUMN;
import static org.lockss.db.SqlDbManager.URL_TABLE;
import static org.lockss.db.SqlDbManager.VOLUME_COLUMN;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lockss.daemon.OpenUrlResolver.noOpenUrlInfo;
import org.lockss.daemon.OpenUrlResolver.OpenUrlInfo;
import org.lockss.util.Logger;

public class SqlOpenUrlResolverDbManager implements OpenUrlResolverDbManager {

	private static Logger log = Logger.getLogger("OpenUrlResolver");
	private SqlDbManager sqlDbManager = null;

	public SqlOpenUrlResolverDbManager(SqlDbManager sqlDbManager) {
		this.sqlDbManager = sqlDbManager;
	}

	/**
	 * Return the article URL from a DOI using the MDB.
	 * @param doi the DOI
	 * @return the OpenUrlInfo
	 */
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

	/**
	 * Return article URL from an ISSN, date, volume, issue, spage, and author. 
	 * The first author will only be used when the starting page is not given.
	 * 
	 * @param issns a list of alternate ISSNs for the title
	 * @param date the publication date
	 * @param volume the volume
	 * @param issue the issue
	 * @param spage the starting page
	 * @param author the first author's full name
	 * @param atitle the article title 
	 * @return the article URL
	 */
	@Override
	public OpenUrlInfo resolveFromIssn(String[] issns, String date,
			String volume, String issue, String spage, String author,
			String atitle) {

		Connection conn = null;
		OpenUrlInfo resolved = noOpenUrlInfo;
		try {
			conn = sqlDbManager.getConnection();

			StringBuilder query = new StringBuilder();
			StringBuilder from = new StringBuilder();
			StringBuilder where = new StringBuilder();
			ArrayList<String> args = new ArrayList<String>();

			query.append("select distinct ");
			query.append("u." + URL_COLUMN);
			query.append(",");
			query.append("p." + PLUGIN_ID_COLUMN);
			query.append(",");
			query.append("a." + AU_KEY_COLUMN);

			from.append(URL_TABLE + " u");
			from.append("," + PLUGIN_TABLE + " p");
			from.append("," + AU_TABLE + " a");
			from.append("," + AU_MD_TABLE + " am");
			from.append("," + MD_ITEM_TABLE + " m");
			from.append("," + PUBLICATION_TABLE + " pu");
			from.append("," + ISSN_TABLE + " i");

			where.append("p." + PLUGIN_SEQ_COLUMN + " = ");
			where.append("a." + PLUGIN_SEQ_COLUMN);
			where.append(" and a." + AU_SEQ_COLUMN + " = ");
			where.append("am." + AU_SEQ_COLUMN);
			where.append(" and am." + AU_MD_SEQ_COLUMN + " = ");
			where.append("m." + AU_MD_SEQ_COLUMN);
			where.append(" and m." + MD_ITEM_SEQ_COLUMN + " = ");
			where.append("u." + MD_ITEM_SEQ_COLUMN);
			where.append(" and m." + PARENT_SEQ_COLUMN + " = ");
			where.append("pu." + MD_ITEM_SEQ_COLUMN);
			where.append(" and pu." + MD_ITEM_SEQ_COLUMN + " = ");
			where.append("i." + MD_ITEM_SEQ_COLUMN);

			where.append(" and i." + ISSN_COLUMN + " in (");

			String plaheholder = "?";
			for (String issn : issns) {
				where.append(plaheholder);
				args.add(issn.replaceAll("-", "")); // strip punctuation
				plaheholder = ",?";
			}
			where.append(")");

			// true if properties specify an article
			boolean hasArticleSpec = 
					(spage != null) || (author != null) || (atitle != null);

			// true if properties specified a journal item
			boolean hasJournalSpec =
					(date != null) || (volume != null) || (issue != null);

			if ((hasJournalSpec && (volume != null || issue != null)) ||
					(hasArticleSpec && (spage != null || atitle != null))) {
				from.append("," + BIB_ITEM_TABLE + " b");

				where.append(" and m." + MD_ITEM_SEQ_COLUMN + " = ");
				where.append("b." + MD_ITEM_SEQ_COLUMN);
			}

			if (hasJournalSpec) {
				// can specify an issue by a combination of date, volume and issue;
				// how these combine varies, so do the most liberal match possible
				// and filter based on multiple results
				if (date != null) {
					// enables query "2009" to match "2009-05-10" in database
					where.append(" and m." + DATE_COLUMN);
					where.append(" like ? escape '\\'");
					args.add(date.replace("\\","\\\\").replace("%","\\%") + "%");
				}

				if (volume != null) {
					where.append(" and b." + VOLUME_COLUMN + " = ?");
					args.add(volume);
				}

				if (issue != null) {
					where.append(" and b." + ISSUE_COLUMN + " = ?");
					args.add(issue);
				}
			}

			// handle start page, author, and article title as
			// equivalent ways to specify an article within an issue
			if (hasArticleSpec) {
				// accept any of the three
				where.append(" and ( ");

				if (spage != null) {
					where.append("b." + START_PAGE_COLUMN + " = ?");
					args.add(spage);
				}
				if (atitle != null) {
					if (spage != null) {
						where.append(" or ");
					}

					from.append("," + MD_ITEM_NAME_TABLE + " name");

					where.append("(m." + MD_ITEM_SEQ_COLUMN + " = ");
					where.append("name." + MD_ITEM_SEQ_COLUMN + " and ");
					where.append("upper(name." + NAME_COLUMN);
					where.append(") like ? escape '\\')");

					args.add(atitle.toUpperCase().replace("%","\\%") + "%");
				}
				if ( author != null) {
					if ((spage != null) || (atitle != null)) {
						where.append(" or ");
					}

					from.append("," + AUTHOR_TABLE + " aut");

					// add the author query to the query
					addAuthorQuery(author, where, args);
				}

				where.append(")");
			}

			// select the 'Access' url
			// (what if there is no access url?)
			where.append(" and u." + FEATURE_COLUMN + " = ");
			where.append("'Access'");


			String url =
					resolveFromQuery(conn, query.toString() + " from " + from.toString()
							+ " where " + where.toString(), args);
			return OpenUrlInfo.newInstance(url, null, OpenUrlInfo.ResolvedTo.ARTICLE);

		} catch (SQLException ex) {
			log.error("Getting ISSNs:" + Arrays.toString(issns), ex);

		} finally {
			SqlDbManager.safeRollbackAndClose(conn);
		}
		return resolved;
	}

	/**
	 * Add author query to the query buffer and argument list.  
	 * @param author the author
	 * @param where the query buffer
	 * @param args the argument list
	 */
	private void addAuthorQuery(String author, StringBuilder where,
			List<String> args) {
		where.append("m." + MD_ITEM_SEQ_COLUMN + " = ");
		where.append("aut." + MD_ITEM_SEQ_COLUMN + " and (");

		String authorUC = author.toUpperCase();
		// match single author
		where.append("upper(");
		where.append(AUTHOR_NAME_COLUMN);
		where.append(") = ?");
		args.add(authorUC);

		// escape escape character and then wildcard characters
		String authorEsc = authorUC.replace("\\", "\\\\").replace("%","\\%");

		// match last name of author 
		// (last, first name separated by ',')
		where.append(" or upper(");
		where.append(AUTHOR_NAME_COLUMN);
		where.append(") like ? escape '\\'");
		args.add(authorEsc+",%");

		// match last name of author
		// (first last name separated by ' ')
		where.append(" or upper(");
		where.append(AUTHOR_NAME_COLUMN);
		where.append(") like ? escape '\\'");
		args.add("% " + authorEsc);    

		where.append(")");
	}

	/** 
	 * Resolve query if a single URL matches.
	 * 
	 * @param conn the connection
	 * @param query the query
	 * @param args the args
	 * @return a single URL
	 * @throws SQLException
	 */
	private String resolveFromQuery(Connection conn, String query,
			List<String> args) throws SQLException {
		final String DEBUG_HEADER = "resolveFromQuery(): ";
		log.debug3(DEBUG_HEADER + "query: " + query);
		PreparedStatement stmt =
				sqlDbManager.prepareStatement(conn, query.toString());
		for (int i = 0; i < args.size(); i++) {
			log.debug3(DEBUG_HEADER + "  query arg:  " + args.get(i));      
			stmt.setString(i+1, args.get(i));
		}
		stmt.setMaxRows(2);  // only need 2 to to determine if unique
		ResultSet resultSet = sqlDbManager.executeQuery(stmt);
		String url = null;
		if (resultSet.next()) {
			url = resultSet.getString(1);
			if (resultSet.next()) {
				log.debug3(DEBUG_HEADER + "entry not unique: " + url + " "
						+ resultSet.getString(1));
				url = null;
			}
		}
		return url;
	}

	/**
	 * Return the article URL from an ISBN, edition, start page, author, and
	 * article title using the metadata database.
	 * <p>
	 * The algorithm matches the ISBN and optionally the edition, and either 
	 * the start page, author, or article title. The reason for matching on any
	 * of the three is that typos in author and article title are always 
	 * possible so we want to be more forgiving in matching an article.
	 * <p>
	 * If none of the three are specified, the URL for the book table of contents 
	 * is returned.
	 * 
	 * @param isbn the isbn
	 * @param String date the date
	 * @param String volumeName the volumeName
	 * @param edition the edition
	 * @param spage the start page
	 * @param author the first author
	 * @param atitle the chapter title
	 * @return the url
	 */
	public OpenUrlInfo resolveFromIsbn(String isbn, String date, String volume,
			String edition, String spage, String author, String atitle) {
		final String DEBUG_HEADER = "resolveFromIsbn(): ";
		OpenUrlInfo resolved = noOpenUrlInfo;
		Connection conn = null;

		try {
			conn = sqlDbManager.getConnection();
			// strip punctuation
			isbn = isbn.replaceAll("[- ]", "");

			StringBuilder query = new StringBuilder();
			StringBuilder from = new StringBuilder();
			StringBuilder where = new StringBuilder();
			ArrayList<String> args = new ArrayList<String>();

			query.append("select distinct ");
			query.append("u." + URL_COLUMN);
			query.append(",");
			query.append("p." + PLUGIN_ID_COLUMN);
			query.append(",");
			query.append("a." + AU_KEY_COLUMN);

			from.append(URL_TABLE + " u");
			from.append("," + PLUGIN_TABLE + " p");
			from.append("," + AU_TABLE + " a");
			from.append("," + AU_MD_TABLE + " am");
			from.append("," + MD_ITEM_TABLE + " m");
			from.append("," + PUBLICATION_TABLE + " pu");
			from.append("," + ISBN_TABLE + " i");

			where.append("p." + PLUGIN_SEQ_COLUMN + " = ");
			where.append("a." + PLUGIN_SEQ_COLUMN);
			where.append(" and a." + AU_SEQ_COLUMN + " = ");
			where.append("am." + AU_SEQ_COLUMN);
			where.append(" and am." + AU_MD_SEQ_COLUMN + " = ");
			where.append("m." + AU_MD_SEQ_COLUMN);
			where.append(" and m." + MD_ITEM_SEQ_COLUMN + " = ");
			where.append("u." + MD_ITEM_SEQ_COLUMN);
			where.append(" and m." + PARENT_SEQ_COLUMN + " = ");
			where.append("pu." + MD_ITEM_SEQ_COLUMN);
			where.append(" and pu." + MD_ITEM_SEQ_COLUMN + " = ");
			where.append("i." + MD_ITEM_SEQ_COLUMN);
			where.append(" and i." + ISBN_COLUMN + " = ?");

			String strippedIsbn = isbn.replaceAll("-", "");
			args.add(strippedIsbn); // strip punctuation

			boolean hasBookSpec = (date != null) || (volume != null)
					|| (edition != null);

			boolean hasArticleSpec = (spage != null) || (author != null)
					|| (atitle != null);

			if ((hasBookSpec && (volume != null || edition != null))
					|| (hasArticleSpec && (spage != null || atitle != null))) {
				from.append("," + BIB_ITEM_TABLE + " b");

				where.append(" and m." + MD_ITEM_SEQ_COLUMN + " = ");
				where.append("b." + MD_ITEM_SEQ_COLUMN);
			}

			if (hasBookSpec) {
				// can specify an issue by a combination of date, volume and
				// issue;
				// how these combine varies, so do the most liberal match
				// possible
				// and filter based on multiple results
				if (date != null) {
					// enables query "2009" to match "2009-05-10" in database
					where.append(" and m." + DATE_COLUMN);
					where.append(" like ? escape '\\'");
					args.add(date.replace("\\", "\\\\").replace("%", "\\%")
							+ "%");
				}

				if (volume != null) {
					where.append(" and b." + VOLUME_COLUMN + " = ?");
					args.add(volume);
				}

				if (edition != null) {
					where.append(" and b." + ISSUE_COLUMN + " = ?");
					args.add(edition);
				}
			}

			// handle start page, author, and article title as
			// equivalent ways to specify an article within an issue
			if (hasArticleSpec) {
				// accept any of the three
				where.append(" and ( ");

				if (spage != null) {
					where.append("b." + START_PAGE_COLUMN + " = ?");
					args.add(spage);
				}

				if (atitle != null) {
					if (spage != null) {
						where.append(" or ");
					}

					from.append("," + MD_ITEM_NAME_TABLE + " name");

					where.append("(m." + MD_ITEM_SEQ_COLUMN + " = ");
					where.append("name." + MD_ITEM_SEQ_COLUMN + " and ");
					where.append("upper(name." + NAME_COLUMN);
					where.append(") like ? escape '\\')");

					args.add(atitle.toUpperCase().replace("%", "\\%") + "%");
				}

				if (author != null) {
					if ((spage != null) || (atitle != null)) {
						where.append(" or ");
					}

					from.append("," + AUTHOR_TABLE + " aut");

					// add the author query to the query
					addAuthorQuery(author, where, args);
				}

				where.append(")");
			}

			String url = resolveFromQuery(conn, query.toString() + " from "
					+ from.toString() + " where " + where.toString(), args);
			log.debug3(DEBUG_HEADER + "url = " + url);
			resolved = OpenUrlInfo.newInstance(url, null,
					OpenUrlInfo.ResolvedTo.CHAPTER);

		} catch (SQLException ex) {
			log.error("Getting ISBN:" + isbn, ex);

		} finally {
			SqlDbManager.safeRollbackAndClose(conn);
		}
		return resolved;
	}
}
