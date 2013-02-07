/*
 * $Id: HtmlFilterInputStream.java,v 1.19 2013/01/17 00:13:14 tlipkis Exp $
 */

/*

Copyright (c) 2000-2012 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.filter.html;

import org.apache.commons.io.*;
import org.htmlparser.*;
import org.htmlparser.lexer.*;
import org.htmlparser.util.*;
import org.htmlparser.scanners.ScriptScanner;

import org.lockss.config.*;
import org.lockss.repository.*;
import org.lockss.util.*;

import java.io.*;

/**
 * InputStream that parses HTML input, applies a user-supplied
 * transformation to the parse tree, then makes the regenerated HTML text
 * available to be read.  <i>Eg</i> to exclude all <code>div</code> nodes
 * with a certain attribute, (<i>ie</i> sections of the html input matching
 * <code>&lt;div someattr="someval" ...&gt; ... &lt;/div&gt;</code>):
 *
 * <pre>   NodeFilter filter = HtmlNodeFilters.divWithAttribute("someattr", "someval");
 *   HtmlTransform xform = HtmlNodeFilterTransform.exclude(filter);
 *   InputStream filtered = new HtmlFilterInputStream(reader, xform);</pre>

 * <p>Uses org.htmlparser.* to parse HTML.  Registers additional tags with
 * PrototypicalNodeFactory to cause them to be treated as a CompositeTag.
 * If you find that the construct you expected to be a subtree is instead
 * spliced into its parent nodelist as a sequence, you probably need to
 * create a new subclass of CompositeTag and register it.  <i>Eg</i>,
 * registering such a class for &lt;font&gt; would cause this:
 *
 * <pre>    Tag (35[0,35],41[0,41]): font
 *    Txt (44[0,44],48[0,48]): here
 *    End (52[0,52],59[0,59]): /font</pre>to instead parse as
 * <pre>    Tag (35[0,35],41[0,41]): font
 *      Txt (44[0,44],48[0,48]): here
 *      End (52[0,52],59[0,59]): /font</pre>
 *
 * In the first case, filtering out (excluding) nodes matching the
 * &lt;font&gt; tags would remove only the tag itself; in the second case
 * the tag, text and end tag would be removed.
 *
 * @see HtmlTransform
 * @see HtmlNodeFilterTransform
 * @see HtmlNodeFilters
 * @see HtmlTags
 */
public class HtmlFilterInputStream extends InputStream {
  private static Logger log = Logger.getLogger("HtmlFilterInputStream");

  /** Maximum offset into file of charset change (<code>&lt;META
   * HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=..."&gt;</code>)
   * that is guaranteed to be handled.  Due to various hard-to-control
   * internal buffering this should be set at least 16K higher than
   * desired. */
  public static final String PARAM_MARK_SIZE =
    Configuration.PREFIX + "filter.html.mark";
  public static final int DEFAULT_MARK_SIZE = 32 * 1024;

  /** Reader bufffer size.  This is one of the two buffers that
   * <code>org.lockss.filter.html.mark</code> must take into account.  The
   * other is the StreamDecoder beffer, for which HtmlParser doesn't
   * provide an API.  */
  public static final String PARAM_RDR_BUF_SIZE =
    Configuration.PREFIX + "filter.html.readerBufSize";
  public static final int DEFAULT_RDR_BUF_SIZE = 8 * 1024;

  /** Determines the behavior when a charset change occurs because of a
   * &lt;meta http-equiv ...&gt; tag, and the chars read under the new
   * encoding don't match those from the old encoding. <br>If nonzero,
   * limits the range within which the InputStreamSource will search to
   * establish a match and determine the correct current position.  I.e.,
   * the maximum allowable difference in number of bytes consumed reading
   * to the same character position in the stream, using the two different
   * character encodings. <br>If zero, any character mismatch causes an
   * error, and the stream fails to parse. */
  public static final String PARAM_ENCODING_MATCH_RANGE =
    Configuration.PREFIX + "filter.html.encodingMatchRange";
  public static final int DEFAULT_ENCODING_MATCH_RANGE = 100;

  /** If true, html output will be as close as possible to the input.  If
   * false, missing end tags will be inserted.  */
  public static final String PARAM_VERBATIM =
    Configuration.PREFIX + "filter.html.verbatim";
  public static final boolean DEFAULT_VERBATIM = true;

  /** Smaller than this and the stream is kept in memory. */
  public static final String PARAM_WRFILE_THRESH =
    Configuration.PREFIX + "filter.html.tempStreamThreshold";
  public static final int DEFAULT_WRFILE_THRESH = 1000*1024;

  /** Determines if a temp file should be used if the stream exceeds a 
    * a specific size */
  public static final String PARAM_USE_FILE =
    Configuration.PREFIX + "filter.html.useFile";
  public static final boolean DEFAULT_USE_FILE = true;

  private FeedbackLogger fl = new FeedbackLogger();

  private InputStream in;
  private String charset;
  private String outCharset;
  private InputStream out = null;
  private HtmlTransform xform;
  // config params
  private int encodingMatchRange = DEFAULT_ENCODING_MATCH_RANGE;
  private int markSize;
  private int rdrBufSize;
  private boolean verbatim;
  private int wrFileThresh;
  private boolean useFile;
  private File outFile;
  private PrototypicalNodeFactory nodeFact;

  /**
   * Create an HtmlFilterInputStream that applies the given transform
   * @param in InputStream to filter from
   * @param xform HtmlTransform to apply to parsed NodeList
   */
  public HtmlFilterInputStream(InputStream in, HtmlTransform xform) {
    this(in, null, xform);
  }

  /**
   * Create an HtmlFilterInputStream that applies the given transform
   * @param in InputStream to filter from
   * @param xform HtmlTransform to apply to parsed NodeList
   * @param charset the charset with which <code>in</code> is encoded
   */
  public HtmlFilterInputStream(InputStream in, String charset,
			       HtmlTransform xform) {
    this(in, charset, null, xform);
  }

  /**
   * Create an HtmlFilterInputStream that applies the given transform
   * @param in InputStream to filter from
   * @param xform HtmlTransform to apply to parsed NodeList
   * @param inCharset the charset in which <code>in</code> is encoded
   * @param outCharset the charset in which the resulting InputStream
   * should be encoded
   */
  public HtmlFilterInputStream(InputStream in,
			       String inCharset, String outCharset,
			       HtmlTransform xform) {
    if (in == null || xform == null) {
      throw new IllegalArgumentException("Called with a null argument");
    }
    this.in = in;
    this.charset = inCharset;
    this.xform = xform;
    this.outCharset = outCharset;
    setConfig();
  }

  // Called by constructor so setters (called before parse()) can override
  // these settings
  void setConfig() {
    Configuration config = ConfigManager.getCurrentConfig();
    markSize = config.getInt(PARAM_MARK_SIZE, DEFAULT_MARK_SIZE);
    rdrBufSize = config.getInt(PARAM_RDR_BUF_SIZE, DEFAULT_RDR_BUF_SIZE);
    setEncodingMatchRange(config.getInt(PARAM_ENCODING_MATCH_RANGE,
					DEFAULT_ENCODING_MATCH_RANGE));
    verbatim = config.getBoolean(PARAM_VERBATIM, DEFAULT_VERBATIM);
    wrFileThresh = config.getInt(PARAM_WRFILE_THRESH, DEFAULT_WRFILE_THRESH);
    useFile = config.getBoolean(PARAM_USE_FILE, DEFAULT_USE_FILE);
  }

  /** Parse the input, apply the transform, generate output string and
   * InputStream */
  void parse() throws IOException {
    try {

      Parser parser = makeParser();
      NodeList nl = parser.parse(null);
      if (nl.size() <= 0) {
        log.warning("nl.size(): " + nl.size());
        out = new ReaderInputStream(new StringReader(""));
      }
      if (log.isDebug3()) log.debug3("parsed (" + nl.size() + "):\n" +
                                       nodeString(nl));
      nl = xform.transform(nl);
      if (log.isDebug3()) log.debug3("xformed (" + nl.size() + "):\n" +
                                       nodeString(nl));
      if(useFile) {
        try {
          setOutToFileInputStream(nl);
        }
        catch (IOException ioe)
        {
          setOutToReaderInputStream(nl);
        }
      }
      else {
        setOutToReaderInputStream(nl);
      }
      IOUtil.safeClose(in);
      in = null;
    } catch (ParserException e) {
      IOException ioe = new IOException(e.toString());
      ioe.initCause(e);
      throw ioe;
    }
  }

  void setOutToReaderInputStream(NodeList nl)
  {
    String h = nl.toHtml(verbatim);
    //
    if (outCharset != null) {
      out = new ReaderInputStream(new StringReader(h), outCharset);
    } else {
      out = new ReaderInputStream(new StringReader(h));
    }
    if (CurrentConfig.getBooleanParam(RepositoryNodeImpl.PARAM_MONITOR_INPUT_STREAMS,
   					  RepositoryNodeImpl.DEFAULT_MONITOR_INPUT_STREAMS)) {
      out = new MonitoringInputStream(out,"HtmlFilterInputStream");
    }
  }

  void setOutToFileInputStream(NodeList nl)
      throws IOException {
    DeferredTempFileOutputStream dtfos =
      new DeferredTempFileOutputStream(wrFileThresh);
    // write the data to file or into a buffer by using the same reader
    // we formally used to read in the entire tree
    for(int i=0; i < nl.size(); i++)
    {
      int                 ch;
      ReaderInputStream ris;
      Reader r = new StringReader(nl.elementAt(i).toHtml(verbatim));
      if (outCharset != null) {
        ris = new ReaderInputStream(r, outCharset);
      } else {
        ris = new ReaderInputStream(r);
      }
      while ((ch = ris.read()) != -1)
      {
        dtfos.write(ch);
      }
      dtfos.flush();
    }
    dtfos.close();

    // return an input stream of either the in memory bytes
    // or the file
    if(dtfos.isInMemory()) {
      out = new ByteArrayInputStream(dtfos.getData());
    }
    else {
      outFile = dtfos.getFile();
      out = new BufferedInputStream(new FileInputStream(outFile));
    }
    if (CurrentConfig.getBooleanParam(RepositoryNodeImpl.PARAM_MONITOR_INPUT_STREAMS,
   					  RepositoryNodeImpl.DEFAULT_MONITOR_INPUT_STREAMS)) {
      out = new MonitoringInputStream(out,"HtmlFilterInputStream");
    }
  }


  /** Make a parser, register our extra nodes */
  protected Parser makeParser()
      throws UnsupportedEncodingException {
    // InputStreamSource may reset() the stream if it encounters a charset
    // change.  It expects the stream already to have been mark()ed.
    if (markSize > 0) {
      in.mark(markSize);
    }
    InputStreamSource is = new InputStreamSource(in, charset, rdrBufSize);
    if(encodingMatchRange > 0) {
    	is.setEncodingMatchRange(encodingMatchRange);
    }
    Page pg = new Page(is);
    setupHtmlParser();

    Lexer lx = new Lexer(pg);
    Parser parser = new Parser(lx, fl);

    NodeFactory factory = getNodeFactory();
    parser.setNodeFactory(factory);
    return parser;
  }

  protected PrototypicalNodeFactory makeNodeFactory() {
    PrototypicalNodeFactory factory = new PrototypicalNodeFactory();
    factory.registerTag(new HtmlTags.Iframe());
    factory.registerTag(new HtmlTags.Noscript());
    factory.registerTag(new HtmlTags.Font());
    factory.registerTag(new HtmlTags.MyTableRow());
    return factory;
  }

  protected PrototypicalNodeFactory getNodeFactory() {
    if (nodeFact == null) {
      nodeFact = makeNodeFactory();
    }
    return nodeFact;
  }

  /** Register the tag in this filter's Parser */
  public HtmlFilterInputStream registerTag(Tag tag) {
    getNodeFactory().registerTag(tag);
    return this;
  }

  void setupHtmlParser() {
    // Tell HtmlParser to accept common html comment variants.
    Lexer.STRICT_REMARKS = false;
    // Tell <script> scanner to accept common html script variants.  (Don't
    // stop scanning when find ETAGO ("</"), as is required by
    // http://www.w3.org/TR/html4/appendix/notes.html#notes-specifying-data)
    ScriptScanner.STRICT = false;
  }

  public static String nodeString(NodeList nl) {
    return StringUtil.separatedString(nl.toNodeArray(), "\n----------\n");
  }

  InputStream getOut() throws IOException {
    if (out == null) {
    if (in == null) {
      throw new IOException("Attempting to read from a closed InputStream");
    }
      parse();
    }
    return out;
  }

  public int getEncodingMatchRange(){
    return encodingMatchRange;
  }
  /** Override the default max encoding match range.  See {@link
   * #PARAM_ENCODING_MATCH_RANGE}
   */
  public void setEncodingMatchRange(int encodingMatchRange){
    this.encodingMatchRange = encodingMatchRange;
  }
  public int read() throws IOException {
    return getOut().read();
  }
  public int read(byte b[]) throws IOException {
    return read(b, 0, b.length);
  }
  public int read(byte b[], int off, int len) throws IOException {
    return getOut().read(b, off, len);
  }
  public long skip(long n) throws IOException {
    return getOut().skip(n);
  }
  public int available() throws IOException {
    return getOut().available();
  }
  public void mark(int readlimit) {
    try {
      getOut().mark(readlimit);
    } catch (IOException e) {
      throw new RuntimeException("", e);
    }
  }
  public void reset() throws IOException {
    getOut().reset();
  }

  public boolean markSupported() {
    try {
      return getOut().markSupported();
    } catch (IOException e) {
      throw new RuntimeException("", e);
    }
  }

  public void close() throws IOException {
    IOUtil.safeClose(in);
    in = null;
    IOUtil.safeClose(out);
    out = null;
    if(outFile != null)
    {
      FileUtils.deleteQuietly(outFile);
      outFile = null;
    }
  }

  static class FeedbackLogger implements ParserFeedback{
    public FeedbackLogger() {
    }
    public void warning(String message) {
      log.warning(message);
    }
    public void info(String message) {
      log.info(message);
    }
    public void error(String message, ParserException e) {
      log.error(message, e);
    }
  }
}
