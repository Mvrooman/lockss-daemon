package org.lockss.metadata;

import org.lockss.plugin.ArchivalUnit;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;

/**
 * Writes to the database metadata related to an archival unit.
 */
public abstract class AuMetadataRecorder {

    /**
     * Constructor.
     *
     * @param task A ReindexingTaskwith the calling task.
     * @param mdManager A MetadataManager with the metadata manager.
     * @param au An ArchivalUnit with the archival unit.
     */
    public AuMetadataRecorder(SqlReindexingTask task, MetadataManager mdManager,
                              ArchivalUnit au)
    {

    }

    /**
     * Writes to the database metadata related to an archival unit.
     *
     * @param mditr An Iterator<ArticleMetadataInfo> with the metadata.
     * @throws Exception if any problem occurred accessing the database.
     */
    abstract void recordMetadata(Iterator<ArticleMetadataBuffer.ArticleMetadataInfo> mditr) throws Exception;
}
