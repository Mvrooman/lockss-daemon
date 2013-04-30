package org.lockss.db;

import org.lockss.app.BaseLockssDaemonManager;
import org.lockss.app.ConfigurableManager;

public abstract class DbManager extends BaseLockssDaemonManager implements ConfigurableManager {

	//
	// Maximum lengths of variable text length database columns.
	//
	/** Length of the article title column. */
	public static final int MAX_ARTICLE_TITLE_COLUMN = 512;

	/** Length of the author column. */
	public static final int OBSOLETE_MAX_AUTHOR_COLUMN = 512;

	/**
	 * Length of the plugin ID column. This column will be used as a horizontal
	 * partitioning column in the future, so its length must be compatible for
	 * that purpose for the database product used.
	 */
	public static final int OBSOLETE_MAX_PLUGIN_ID_COLUMN = 128;

	/** Length of the title column. */
	public static final int MAX_TITLE_COLUMN = 512;

	/**
	 * Length of the plugin ID column. This column will be used as a horizontal
	 * partitioning column in the future, so its length must be compatible for
	 * that purpose for the database product used.
	 */
	public static final int MAX_PLUGIN_ID_COLUMN = 256;

	/** Length of the publishing platform column. */
	public static final int MAX_PLATFORM_COLUMN = 64;

	/** Length of the AU key column. */
	public static final int MAX_AU_KEY_COLUMN = 512;

	/** Length of the coverage column. */
	public static final int MAX_COVERAGE_COLUMN = 16;

	/** Length of the type name column. */
	public static final int MAX_TYPE_NAME_COLUMN = 32;

	/** Length of the date column. */
	public static final int MAX_DATE_COLUMN = 16;

	/** Length of the name column. */
	public static final int MAX_NAME_COLUMN = 512;

	/** Length of the name type column. */
	public static final int MAX_NAME_TYPE_COLUMN = 16;

	/** Length of the key name column. */
	public static final int MAX_KEY_NAME_COLUMN = 128;

	/** Length of the metadata value column. */
	public static final int MAX_MD_VALUE_COLUMN = 128;

	/** Length of the volume column. */
	public static final int MAX_VOLUME_COLUMN = 16;

	/** Length of the issue column. */
	public static final int MAX_ISSUE_COLUMN = 16;

	/** Length of the start page column. */
	public static final int MAX_START_PAGE_COLUMN = 16;

	/** Length of the end page column. */
	public static final int MAX_END_PAGE_COLUMN = 16;

	/** Length of the item number column. */
	public static final int MAX_ITEM_NO_COLUMN = 16;

	/** Length of feature column. */
	public static final int MAX_FEATURE_COLUMN = 32;

	/** Length of the URL column. */
	public static final int MAX_URL_COLUMN = 4096;

	/** Length of the author column. */
	public static final int MAX_AUTHOR_COLUMN = 128;

	/** Length of the keyword column. */
	public static final int MAX_KEYWORD_COLUMN = 64;

	/** Length of the DOI column. */
	public static final int MAX_DOI_COLUMN = 256;

	/** Length of the ISSN column. */
	public static final int MAX_ISSN_COLUMN = 8;

	/** Length of the ISSN type column. */
	public static final int MAX_ISSN_TYPE_COLUMN = 16;

	/** Length of the ISBN column. */
	public static final int MAX_ISBN_COLUMN = 13;

	/** Length of the ISBN type column. */
	public static final int MAX_ISBN_TYPE_COLUMN = 16;

	/** Length of the publication proprietary identifier column. */
	public static final int MAX_PUBLICATION_ID_COLUMN = 32;

	/** Length of the system column. */
	public static final int MAX_SYSTEM_COLUMN = 16;
	
	/**
	 * The indicator to be inserted in the database at the end of truncated text
	 * values.
	 */
	public static final String TRUNCATION_INDICATOR = "\u0019";

	public abstract OpenUrlResolverDbManager getOpenUrlResolverDbManager();

	public abstract boolean isConnectionReady() throws Exception;

	/**
	 * Provides a version of a text truncated to a maximum length, if necessary,
	 * including an indication of the truncation.
	 * 
	 * @param original
	 *          A String with the original text to be truncated, if necessary.
	 * @param maxLength
	 *          An int with the maximum length of the truncated text to be
	 *          provided.
	 * @return a String with the original text if it is not longer than the
	 *         maximum length allowed or the truncated text including an
	 *         indication of the truncation.
	 */
	public static String truncateVarchar(String original, int maxLength) {
		if (original.length() <= maxLength) {
			return original;
		}

		return original.substring(0, maxLength - TRUNCATION_INDICATOR.length())
				+ TRUNCATION_INDICATOR;
	}
}
