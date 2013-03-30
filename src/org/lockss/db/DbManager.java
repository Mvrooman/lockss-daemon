package org.lockss.db;

import org.lockss.app.BaseLockssDaemonManager;
import org.lockss.app.ConfigurableManager;

public abstract class DbManager extends BaseLockssDaemonManager implements ConfigurableManager {

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
