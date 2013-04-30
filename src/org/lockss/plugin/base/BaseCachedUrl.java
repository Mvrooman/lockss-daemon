/*
 * $Id: BaseCachedUrl.java,v 1.47 2012/07/02 16:25:28 tlipkis Exp $
 */

/*

Copyright (c) 2000-2010 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.plugin.base;

import java.io.*;
import java.util.*;
import java.net.*;
import de.schlichtherle.truezip.file.*;
import de.schlichtherle.truezip.fs.*;

import org.lockss.app.*;
import org.lockss.config.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.truezip.*;
import org.lockss.repository.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.rewriter.*;
import org.lockss.extractor.*;

/** Base class for CachedUrls.  Expects the LockssRepository for storage.
 * Plugins may extend this to get some common CachedUrl functionality.
 */
public class BaseCachedUrl implements CachedUrl {
  protected ArchivalUnit au;
  protected String url;
  protected static Logger logger = Logger.getLogger("CachedUrl");

  private LockssRepository repository;
  private RepositoryNode leaf = null;
  protected RepositoryNode.RepositoryNodeContents rnc = null;

  private static final String PARAM_SHOULD_FILTER_HASH_STREAM =
    Configuration.PREFIX+"baseCachedUrl.filterHashStream";
  private static final boolean DEFAULT_SHOULD_FILTER_HASH_STREAM = true;

  public static final String PARAM_FILTER_USE_CHARSET =
    Configuration.PREFIX + "baseCachedUrl.filterUseCharset";
  public static final boolean DEFAULT_FILTER_USE_CHARSET = true;
  public static final String DEFAULT_METADATA_CONTENT_TYPE = "text/html";

  public BaseCachedUrl(ArchivalUnit owner, String url) {
    this.au = owner;
    this.url = url;
  }

  public String getUrl() {
    return url;
  }

  public int getType() {
    return CachedUrlSetNode.TYPE_CACHED_URL;
  }

  public boolean isLeaf() {
    return true;
  }

  /**
   * return a string "[BCU: <url>]"
   * @return the string form
   */
  public String toString() {
    return "[BCU: "+ getUrl() + "]";
  }

  /**
   * Return the ArchivalUnit to which this CachedUrl belongs.
   * @return the ArchivalUnit
   */
  public ArchivalUnit getArchivalUnit() {
    return au;
  }

  public RepositoryNodeVersion getNodeVersion() {
    ensureLeafLoaded();
    return leaf;
  }

  public CachedUrl getCuVersion(int version) {
    ensureLeafLoaded();
    return new Version(au, url, leaf.getNodeVersion(version));
  }

  public CachedUrl[] getCuVersions() {
    return getCuVersions(Integer.MAX_VALUE);
  }

  public CachedUrl[] getCuVersions(int maxVersions) {
    ensureLeafLoaded();
    RepositoryNodeVersion[] nodeVers = leaf.getNodeVersions(maxVersions);
    CachedUrl[] res = new CachedUrl[nodeVers.length];
    for (int ix = res.length - 1; ix >= 0; ix--) {
      res[ix] = new Version(au, url, nodeVers[ix]);
    }
    return res;
  }

  public int getVersion() {
    return getNodeVersion().getVersion();
  }

  /**
   * Return a stream suitable for hashing.  This may be a filtered stream.
   * @return an InputStream
   */
  public InputStream openForHashing() {
    if (CurrentConfig.getBooleanParam(PARAM_SHOULD_FILTER_HASH_STREAM,
				      DEFAULT_SHOULD_FILTER_HASH_STREAM)) {
      logger.debug3("Filtering on, returning filtered stream");
      return getFilteredStream();
    } else {
      logger.debug3("Filtering off, returning unfiltered stream");
      return getUnfilteredInputStream();
    }
  }

  public boolean hasContent() {
    if (repository==null) {
      getRepository();
    }
    if (leaf==null) {
      try {
        leaf = repository.getNode(url);
      } catch (MalformedURLException mue) {
	return false;
      }
    }
    return (leaf == null) ? false : leaf.hasContent();
  }

  public InputStream getUnfilteredInputStream() {
    ensureRnc();
    return rnc.getInputStream();
  }

  public String getContentType() {
    CIProperties props = getProperties();
    if (props != null) {
      return props.getProperty(PROPERTY_CONTENT_TYPE);
    }
    return null;
  }

  public String getEncoding() {
    String res = null;
    if (CurrentConfig.getBooleanParam(PARAM_FILTER_USE_CHARSET,
				      DEFAULT_FILTER_USE_CHARSET)) {
      res = HeaderUtil.getCharsetFromContentType(getContentType());
    }
    if (res == null) {
      res = Constants.DEFAULT_ENCODING;
    }
    return res;
  }

  public Reader openForReading() {
    try {
      return
	new BufferedReader(new InputStreamReader(getUnfilteredInputStream(),
						 getEncoding()));
    } catch (IOException e) {
      // XXX Wrong Exception.  Should this method be declared to throw
      // UnsupportedEncodingException?
      logger.error("Creating InputStreamReader for '" + getUrl() + "'", e);
      throw new LockssRepository.RepositoryStateException
	("Couldn't create InputStreamReader:" + e.toString());
    }
  }

  public LinkRewriterFactory getLinkRewriterFactory() {
    LinkRewriterFactory ret = null;
    String ctype = getContentType();
    if (ctype != null) {
      ret = au.getLinkRewriterFactory(ctype);
    }
    return ret;
  }

  public CIProperties getProperties() {
    ensureRnc();
    return CIProperties.fromProperties(rnc.getProperties());
  }

  public long getContentSize() {
    return getNodeVersion().getContentSize();
  }

  /**
   * Return a FileMetadataExtractor for the CachedUrl's content type, or
   * null if the plugin has no FileMetadataExtractor for that MIME type
   * @param target the purpose for which metadata is being extracted
   */
  public FileMetadataExtractor getFileMetadataExtractor(MetadataTarget target) {
    String ct = getContentType();
    FileMetadataExtractor ret = au.getFileMetadataExtractor(target, ct);
    return ret;
  }

  public void release() {
    if (rnc != null) {
      rnc.release();
      rnc = null;
    }
  }

  private void ensureRnc() {
    if (rnc == null) {
      rnc = getNodeVersion().getNodeContents();
    }
  }

  private LockssDaemon getDaemon() {
    return au.getPlugin().getDaemon();
  }

  private void getRepository() {
    repository = getDaemon().getLockssRepository(au);
  }

  private void ensureLeafLoaded() {
    if (repository==null) {
      getRepository();
    }
    if (leaf==null) {
      try {
        leaf = repository.createNewNode(url);
      } catch (MalformedURLException mue) {
        logger.error("Couldn't load node due to bad url: "+url);
        throw new IllegalArgumentException("Couldn't parse url properly.", mue);
      }
    }
  }

  protected InputStream getFilteredStream() {
    String contentType = getContentType();
    InputStream is = null;
    // first look for a FilterFactory
    FilterFactory fact = au.getHashFilterFactory(contentType);
    if (fact != null) {
      if (logger.isDebug3()) {
	logger.debug3("Filtering " + contentType +
		      " with " + fact.getClass().getName());
      }
      InputStream unfis = getUnfilteredInputStream();
      try {
	return fact.createFilteredInputStream(au, unfis, getEncoding());
      } catch (PluginException e) {
	IOUtil.safeClose(unfis);
	throw new RuntimeException(e);
      } catch (RuntimeException e) {
	IOUtil.safeClose(unfis);
	throw e;
      }
    }
    // then look for deprecated FilterRule
    FilterRule fr = au.getFilterRule(contentType);
    if (fr != null) {
      if (logger.isDebug3()) {
	logger.debug3("Filtering " + contentType +
		      " with " + fr.getClass().getName());
      }
      Reader unfrdr = openForReading();
      try {
	Reader rd = fr.createFilteredReader(unfrdr);
	return new ReaderInputStream(rd);
      } catch (PluginException e) {
	IOUtil.safeClose(unfrdr);
        throw new RuntimeException(e);
      }
    }
    if (logger.isDebug3()) logger.debug3("Not filtering " + contentType);
    return getUnfilteredInputStream();
  }

  public CachedUrl getArchiveMemberCu(ArchiveMemberSpec ams) {
    Member memb = new Member(au, url, this, ams);
    return memb;
  }

  CachedUrl getArchiveMemberCu(ArchiveMemberSpec ams, TFile memberTf) {
    Member memb = new Member(au, url, this, ams, memberTf);
    logger.critical("getAMC("+ams+", "+memberTf+"): " + memb);
    return memb;
  }

  boolean isArchiveMember() {
    return false;
  }

  /** A CachedUrl that's bound to a specific version. */
  static class Version extends BaseCachedUrl {
    private RepositoryNodeVersion nodeVer;

    public Version(ArchivalUnit owner, String url,
		   RepositoryNodeVersion nodeVer) {
      super(owner, url);
      this.nodeVer = nodeVer;
    }

    public RepositoryNodeVersion getNodeVersion() {
      return nodeVer;
    }

    public boolean hasContent() {
      return getNodeVersion().hasContent();
    }

    /**
     * return a string "[BCU: v=n <url>]"
     * @return the string form
     */
    public String toString() {
      return "[BCU: v=" + getVersion() + " " + url+"]";
    }
  }

  /** Special behavior for CUs that are archive members.  This isn't
   * logically a subtype of CachedUrl because not all places that accept a
   * CachedUrl can operate an archive member, but it's the convenient way
   * to implement it.  Perhaps it should be a supertype (interface)? */
  static class Member extends BaseCachedUrl {
    protected BaseCachedUrl bcu;
    protected ArchiveMemberSpec ams;
    protected TFileCache.Entry tfcEntry = null;
    protected TFile memberTf = null;
    protected CIProperties memberProps = null;

    Member(ArchivalUnit au, String url, BaseCachedUrl bcu,
	   ArchiveMemberSpec ams) {
      super(au, url);
      this.ams = ams;
      this.bcu = bcu;
    }

    Member(ArchivalUnit au, String url, BaseCachedUrl bcu,
	   ArchiveMemberSpec ams, TFile memberTf) {
      super(au, url);
      this.ams = ams;
      this.bcu = bcu;
      this.memberTf = memberTf;
    }

    @Override
    public String getUrl() {
      return ams.toUrl();
    }

    @Override
    /** True if the archive exists and the member exists */
    public boolean hasContent() {
      if (!super.hasContent()) {
	return false;
      }
      try {
	TFile tf = getTFile();
	if (tf == null) {
	  return false;
	}
	if (!tf.isDirectory()) {
	  return false;
	}
	return getMemberTFile().exists();
      } catch (Exception e) {
	String msg =
	  "Couldn't open member for which exists() was true: " + this;
	logger.error(msg);
	throw new LockssRepository.RepositoryStateException(msg, e);
      }
    }

    @Override
    public InputStream getUnfilteredInputStream() {
      if (!super.hasContent()) {
	return null;
      }
      try {
	TFile tf = getTFile();
	if (tf == null) {
	  return null;
	}
	if (!tf.isDirectory()) {
	  logger.error("tf.isDirectory() = false");
	  return null;
	}
	TFile membtf = getMemberTFile();
	if (!membtf.exists()) {
	  return null;
	}
	InputStream is = new TFileInputStream(membtf);
	if (CurrentConfig.getBooleanParam(RepositoryNodeImpl.PARAM_MONITOR_INPUT_STREAMS,
					  RepositoryNodeImpl.DEFAULT_MONITOR_INPUT_STREAMS)) {
	  is = new MonitoringInputStream(is, this.toString());
	}
	return is;
      } catch (Exception e) {
	String msg =
	  "Couldn't open member for which exists() was true: " + this;
	logger.error(msg);
	throw new LockssRepository.RepositoryStateException(msg, e);
      }
    }

    /** Properties of an archive member are synthesized from its size and
     * extension, and the enclosing archive's collection properties
     * (collection date, Last-Modified) */
    @Override
    public CIProperties getProperties() {
      if (memberProps == null) {
	memberProps = synthesizeProperties();
      }
      return memberProps;
    }

    private CIProperties synthesizeProperties() {
      CIProperties res = new CIProperties();
      try {
	TFileCache.Entry ent = getTFileCacheEntry();
	if (ent.getArcCuProps() != null) {
	  res.putAll(ent.getArcCuProps());
	}
      } catch (IOException e) {
	logger.warning("Couldn't copy archive props to member CU", e);
      }

      res.put(CachedUrl.PROPERTY_NODE_URL, getUrl());
      res.put("Length", getContentSize());

      try {
	// If member has last modified, overwrite any inherited from archive
	// props.
	TFile membtf = getMemberTFile();
	long lastMod = membtf.lastModified();
	if (lastMod > 0) {
	  res.put(CachedUrl.PROPERTY_LAST_MODIFIED,
		  DateTimeUtil.GMT_DATE_FORMATTER.format(new Date(lastMod)));
	}
      } catch (IOException e) {
	logger.warning("Couldn't get member Last-Modified", e);
      }

      String ctype = inferContentType();
      if (!StringUtil.isNullString(ctype)) {
	res.put("Content-Type", ctype);
	res.put(PROPERTY_CONTENT_TYPE, ctype);

      }
      return res;
    }

    private String inferContentType() {
      String ext = FileUtil.getExtension(ams.getName());
      if (ext == null) {
	return null;
      }
      return MimeUtil.getMimeTypeFromExtension(ext);
    }

    @Override
    public long getContentSize() {
      try {
	return getMemberTFile().length();
      } catch (IOException e) {
	throw new LockssRepository.RepositoryStateException
	  ("Couldn't get archive member length", e);
      }
    }


    // XXX Should release do something other than release the archive CU?
//     public void release() {
//       if (rnc != null) {
// 	rnc.release();
//       }
//     }

    private TFile getMemberTFile() throws IOException {
      checkValidTfcEntry();
      if (memberTf == null) {
	memberTf = new TFile(getTFile(), ams.getName());
      }
      return memberTf;
    }

    private TFile getTFile() throws IOException {
      TFileCache.Entry ent = getTFileCacheEntry();
      if (ent == null) {
	return null;
      }
      return ent.getTFile();
    }

    void checkValidTfcEntry() {
      if (tfcEntry != null && !tfcEntry.isValid()) {
	tfcEntry = null;
      }
    }

    private TFileCache.Entry getTFileCacheEntry() throws IOException {
      checkValidTfcEntry();
      if (tfcEntry == null) {
	TrueZipManager tzm = bcu.getDaemon().getTrueZipManager();
	tfcEntry = tzm.getCachedTFileEntry(au.makeCachedUrl(url));
      }
      return tfcEntry;
    }

    ArchiveMemberSpec getArchiveMemberSpec() {
      return ams;
    }

    @Override
    boolean isArchiveMember() {
      return true;
    }

    String getArchiveUrl() {
      return super.getUrl();
    }

    @Override
    public CachedUrl getArchiveMemberCu(ArchiveMemberSpec ams) {
      throw new UnsupportedOperationException("Can't create a CU member from a CU member: "
					      + this);
    }

    @Override
    public CachedUrl getCuVersion(int version) {
      throw new UnsupportedOperationException("Can't access versions of a CU member: "
					      + this);
    }

    @Override
    public CachedUrl[] getCuVersions(int maxVersions) {
      throw new UnsupportedOperationException("Can't access versions of a CU member: "
					      + this);
    }

    public String toString() {
      return "[BCUM: "+ getUrl() + "]";
    }

  }
}
