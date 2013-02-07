/*
 * $Id: TestNodeFilterHtmlLinkRewriterFactory.java,v 1.22 2012/12/22 14:18:46 pgust Exp $
 */

/*

Copyright (c) 2000-2011 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.rewriter;

import java.io.*;
import java.util.*;

import junit.framework.Test;

import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.servlet.*;

public class TestNodeFilterHtmlLinkRewriterFactory extends LockssTestCase {
  static Logger log = Logger.getLogger("TestNodeFilterHtmlLinkRewriterFactory");

  /** 
   * The original HTML page; CPROTO is the protocol for the content server.
   */
  private static final String orig =
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\n" +
    "<html>\n" +
    "<head>\n" +
    "<title>example.com website</title>\n" +
    "<meta http-equiv=\"content-type\" " +
    "content=\"text/html; charset=ISO-8859-1\">\n" +
    "<meta http-equiv=\"refresh\" " +
    "content=\"1;url=CPROTO://www.example.com/page2.html\">\n" +
    "<meta http-equiv=\"refresh\" " +
    "content=\"1; url=/page3.html\">\n" +
    "<meta http-equiv=\"refresh\" " +
    "content=\"1; \turl=CPROTO://www.example.com/page4.html\">\n" +
    "<meta http-equiv=\"refresh\" " +
    "content=\"1;url=page5.html\">\n" +
    "<meta http-equiv=\"refresh\" " +
    "content=\"1;url=../page6.html\">\n" +
    "<meta http-equiv=\"refresh\" " +
    "content=\"1;url=CPROTO://www.content.org/page2.html\">\n" +
    "</head>\n" +
    "<body>\n" +
    "<h1 align=\"center\">example.com website</h1>\n" +
    "<br>\n" +
    "<a href=\"CPROTO://www.example.com/content/index.html\">abs link</a>\n" +
    "<br>\n" +
    "<a href=\"CPROTO://www.example.com/content/index.html#ref1\">abs link with ref</a>\n" +
    "<br>\n" +
    "<a href=\"path/index.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"path/index.html#ref2\">rel link with ref</a>\n" +
    "<br>\n" +
    "<a href=\"4path/index.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"%2fpath/index.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"(content)/index.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"/more/path/index.html\">rel link</a>\n" +
    "<br>\n" +
    "<A HREF=\"../more/path/index.html\">rel link</A>\n" +
    "<br>\n" +
    "<A HREF=\"./more/path/index.html\">rel link</A>\n" +
    "<br>\n" +
    "<a href=\"?issn=123456789X\">rel query</a>\n" +
    "<br>\n" +
    "<a href=\"CPROTO://www.content.org/index.html\">abs link no rewrite</a>\n" +
    "<br>\n" +
    "Rel script" +
    "<script type=\"text/javascript\" src=\"/javascript/ajax/utility.js\"></script>\n" +
    "<br>\n" +
    "Abs script" +
    "<script type=\"text/javascript\" src=\"CPROTO://www.example.com/javascript/utility.js\"></script>\n" +
    "<br>\n" +
    "Rel style" +
    "<style type=\"text/css\" src=\"/css/utility.css\"></style>\n" +
    "<br>\n" +
    "Abs style" +
    "<style type=\"text/css\" src=\"CPROTO://www.example.com/css/utility.css\"></style>\n" +
    "<br>\n" +
    "Rel stylesheet" +
    "<link rel=\"stylesheet\" href=\"/css/basic.css\" type=\"text/css\" media=\"all\">\n" +
    "<br>\n" +
    "Rel stylesheet" +
    "<link rel=\"stylesheet\" href=\"Basic.css\" type=\"text/css\" media=\"all\">\n" +
    "<br>\n" +
    "Abs stylesheet" +
    "<link rel=\"stylesheet\" href=\"CPROTO://www.example.com/css/extra.css\" type=\"text/css\" media=\"all\">\n" +
    "<br>\n" +
    "Rel img" +
    "<img src=\"/icons/logo.gif\" alt=\"BMJ 1\" title=\"BMJ 1\" />\n" +
    "<br>\n" +
    "Abs img" +
    "<img src=\"CPROTO://www.example.com/icons/logo2.gif\" alt=\"BMJ 2\" title=\"BMJ 2\" />\n" +
    "<br>\n" +

    // repeat above after changing the base URL

    "<base href=\"CPROTO://www.example.com/otherdir/\" />\n" +
    "<a href=\"CPROTO://www.example.com/content/index.html\">abs link</a>\n" +
    "<br>\n" +
    "<a href=\"path/index.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"4path/index.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"%2fpath/index.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"(content)/index.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"/more/path/index.html\">rel link</a>\n" +
    "<br>\n" +
    "<A HREF=\"../more/path/index.html\">rel link</A>\n" +
    "<br>\n" +
    "<A HREF=\"./more/path/index.html\">rel link</A>\n" +
    "<br>\n" +
    "<a href=\"?issn=123456789X\">rel query</a>\n" +
    "<br>\n" +
    "<a href=\"CPROTO://www.content.org/index.html\">abs link no rewrite</a>\n" +
    "<br>\n" +
    "Rel script" +
    "<script type=\"text/javascript\" src=\"/javascript/ajax/utility.js\"></script>\n" +
    "<br>\n" +
    "Abs script" +
    "<script type=\"text/javascript\" src=\"CPROTO://www.example.com/javascript/utility.js\"></script>\n" +
    "<br>\n" +
    "Rel style" +
    "<style type=\"text/css\" src=\"/css/utility.css\"></style>\n" +
    "<br>\n" +
    "Abs style" +
    "<style type=\"text/css\" src=\"CPROTO://www.example.com/css/utility.css\"></style>\n" +
    "<br>\n" +
    "Rel stylesheet" +
    "<link rel=\"stylesheet\" href=\"/css/basic.css\" type=\"text/css\" media=\"all\">\n" +
    "<br>\n" +
    "Rel stylesheet" +
    "<link rel=\"stylesheet\" href=\"Basic.css\" type=\"text/css\" media=\"all\">\n" +
    "<br>\n" +
    "Abs stylesheet" +
    "<link rel=\"stylesheet\" href=\"CPROTO://www.example.com/css/extra.css\" type=\"text/css\" media=\"all\">\n" +
    "<br>\n" +
    "Rel img" +
    "<img src=\"/icons/logo.gif\" alt=\"BMJ 1\" title=\"BMJ 1\" />\n" +
    "<br>\n" +
    "Abs img" +
    "<img src=\"CPROTO://www.example.com/icons/logo2.gif\" alt=\"BMJ 2\" title=\"BMJ 2\" />\n" +
    "<br>\n" +

    "</body>\n" +
    "</HTML>\n";

  /** 
   * The transformed HTML page; CPROTO is the protocol for the content server,
   * and LPROTO is the protocol for the LOCKSS server
   */
  static String xformed =
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\n" +
    "<html>\n" +
    "<head>\n" +
    "<title>example.com website</title>\n" +
    "<meta http-equiv=\"content-type\" content=\"text/html; charset=ISO-8859-1\">\n" +
    "<meta http-equiv=\"refresh\" content=\"1;url=LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fpage2.html\">\n" +
    "<meta http-equiv=\"refresh\" content=\"1; url=LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fpage3.html\">\n" +
    "<meta http-equiv=\"refresh\" content=\"1; 	url=LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fpage4.html\">\n" +
    "<meta http-equiv=\"refresh\" content=\"1;url=LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2Fpage5.html\">\n" +
    "<meta http-equiv=\"refresh\" content=\"1;url=LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2F..%2Fpage6.html\">\n" +
    "<meta http-equiv=\"refresh\" " +
    "content=\"1;url=CPROTO://www.content.org/page2.html\">\n" +
    "</head>\n" +
    "<body>\n" +
    "<h1 align=\"center\">example.com website</h1>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2Findex.html\">abs link</a>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2Findex.html#ref1\">abs link with ref</a>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2Fpath%2Findex.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2Fpath%2Findex.html#ref2\">rel link with ref</a>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2F4path%2Findex.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2F%252fpath%2Findex.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2F%28content%29%2Findex.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fmore%2Fpath%2Findex.html\">rel link</a>\n" +
    "<br>\n" +
    "<A HREF=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2F..%2Fmore%2Fpath%2Findex.html\">rel link</A>\n" +
    "<br>\n" +
    "<A HREF=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2F.%2Fmore%2Fpath%2Findex.html\">rel link</A>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2F%3Fissn%3D123456789X\">rel query</a>\n" +
    "<br>\n" +
    "<a href=\"CPROTO://www.content.org/index.html\">abs link no rewrite</a>\n" +
    "<br>\n" +
    "Rel script<script type=\"text/javascript\" src=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fjavascript%2Fajax%2Futility.js\"></script>\n" +
    "<br>\n" +
    "Abs script<script type=\"text/javascript\" src=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fjavascript%2Futility.js\"></script>\n" +
    "<br>\n" +
    "Rel style<style type=\"text/css\" src=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Futility.css\"></style>\n" +
    "<br>\n" +
    "Abs style<style type=\"text/css\" src=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Futility.css\"></style>\n" +
    "<br>\n" +
    "Rel stylesheet<link rel=\"stylesheet\" href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fbasic.css\" type=\"text/css\" media=\"all\">\n" +
    "<br>\n" +
    "Rel stylesheet<link rel=\"stylesheet\" href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2FBasic.css\" type=\"text/css\" media=\"all\">\n" +
    "<br>\n" +
    "Abs stylesheet<link rel=\"stylesheet\" href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fextra.css\" type=\"text/css\" media=\"all\">\n" +
    "<br>\n" +
    "Rel img<img src=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Ficons%2Flogo.gif\" alt=\"BMJ 1\" title=\"BMJ 1\" />\n" +
    "<br>\n" +
    "Abs img<img src=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Ficons%2Flogo2.gif\" alt=\"BMJ 2\" title=\"BMJ 2\" />\n" +
    "<br>\n" +

    // Base URL change
    "<base href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2F\" />\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2Findex.html\">abs link</a>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2Fpath%2Findex.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2F4path%2Findex.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2F%252fpath%2Findex.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2F%28content%29%2Findex.html\">rel link</a>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fmore%2Fpath%2Findex.html\">rel link</a>\n" +
    "<br>\n" +
    "<A HREF=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2F..%2Fmore%2Fpath%2Findex.html\">rel link</A>\n" +
    "<br>\n" +
    "<A HREF=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2F.%2Fmore%2Fpath%2Findex.html\">rel link</A>\n" +
    "<br>\n" +
    "<a href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2F%3Fissn%3D123456789X\">rel query</a>\n" +
    "<br>\n" +
    "<a href=\"CPROTO://www.content.org/index.html\">abs link no rewrite</a>\n" +
    "<br>\n" +
    "Rel script<script type=\"text/javascript\" src=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fjavascript%2Fajax%2Futility.js\"></script>\n" +
    "<br>\n" +
    "Abs script<script type=\"text/javascript\" src=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fjavascript%2Futility.js\"></script>\n" +
    "<br>\n" +
    "Rel style<style type=\"text/css\" src=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Futility.css\"></style>\n" +
    "<br>\n" +
    "Abs style<style type=\"text/css\" src=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Futility.css\"></style>\n" +
    "<br>\n" +
    "Rel stylesheet<link rel=\"stylesheet\" href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fbasic.css\" type=\"text/css\" media=\"all\">\n" +
    "<br>\n" +
    "Rel stylesheet<link rel=\"stylesheet\" href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2FBasic.css\" type=\"text/css\" media=\"all\">\n" +
    "<br>\n" +
    "Abs stylesheet<link rel=\"stylesheet\" href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fextra.css\" type=\"text/css\" media=\"all\">\n" +
    "<br>\n" +
    "Rel img<img src=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Ficons%2Flogo.gif\" alt=\"BMJ 1\" title=\"BMJ 1\" />\n" +
    "<br>\n" +
    "Abs img<img src=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Ficons%2Flogo2.gif\" alt=\"BMJ 2\" title=\"BMJ 2\" />\n" +
    "<br>\n" +

    "</body>\n" +
    "</HTML>\n";

  /** 
   * The original CSS page; CPROTO is the protocol for the content server.
   */
  private static final String origCss =
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\n" +
    "<html>\n" +
    "<head>\n" +
    "</head>\n" +
    "<body>\n" +
    "Rel path CSS import" +
    "<style type=\"text/css\" media=\"screen,print\">@import url(css/common.css) @import url(common2.css);</style>\n" +
    "<br>\n" +
    "Rel CSS import" +
    "<style type=\"text/css\" media=\"screen,print\">@import url(/css/common.css) @import url(/css/common2.css);</style>\n" +
    "<br>\n" +
    "Abs CSS import" +
    "<style type=\"text/css\" media=\"screen,print\">@import url(CPROTO://www.example.com/css/extra.css) @import url(CPROTO://www.example.com/css/extra2.css);</style>\n" +
    "<br>\n" +
    "Mixed CSS import" +
    "<style type=\"text/css\" media=\"screen,print\">@import url(CPROTO://www.example.com/css/extra3.css) @import url(EXTRA4.css) @import url(../extra5.css);</style>\n" +
    "<br>\n" +
    "Rel CSS import w/ bare url" +
    "<style type=\"text/css\" media=\"screen,print\">@import \"/css/common.css\"; @import 'common2.css';</style>\n" +
    "<br>\n" +

    // repeat above after changing the base URL

    "<base href=\"CPROTO://www.example.com/otherdir/\" />\n" +
    "Rel path CSS import" +
    "<style type=\"text/css\" media=\"screen,print\">@import url(css/common.css) @import url(common2.css);</style>\n" +
    "<br>\n" +
    "Rel CSS import" +
    "<style type=\"text/css\" media=\"screen,print\">@import url(/css/common.css) @import url(/css/common2.css);</style>\n" +
    "<br>\n" +
    "Abs CSS import" +
    "<style type=\"text/css\" media=\"screen,print\">@import url(CPROTO://www.example.com/css/extra.css) @import url(CPROTO://www.example.com/css/extra2.css);</style>\n" +
    "<br>\n" +
    "Mixed CSS import" +
    "<style type=\"text/css\" media=\"screen,print\">@import url(CPROTO://www.example.com/css/extra3.css) @import url(EXTRA4.css) @import url(../extra5.css);</style>\n" +
    "<br>\n" +
    "Rel CSS import w/ bare url" +
    "<style type=\"text/css\" media=\"screen,print\">@import \"/css/common.css\"; @import 'common2.css';</style>\n" +
    "<br>\n" +

    "</body>\n" +
    "</HTML>\n";

  /** 
   * The transformed full CSS page; CPROTO is the protocol for the content 
   * server, and LPROTO is the protocol of the LOCKSS server
   */
  static String xformedCssFull =
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\n" +
    "<html>\n" +
    "<head>\n" +
    "</head>\n" +
    "<body>\n" +
    "Rel path CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2Fcss%2Fcommon.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2Fcommon2.css');</style>\n" +
    "<br>\n" +
    "Rel CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fcommon.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fcommon2.css');</style>\n" +
    "<br>\n" +
    "Abs CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fextra.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fextra2.css');</style>\n" +
    "<br>\n" +
    "Mixed CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fextra3.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2FEXTRA4.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fextra5.css');</style>\n" +
    "<br>\n" +
    "Rel CSS import w/ bare url<style type=\"text/css\" media=\"screen,print\">@import 'LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fcommon.css'; @import 'LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcontent%2Fcommon2.css';</style>\n" +
    "<br>\n" +

    // Base URL change
    "<base href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2F\" />\n" +
    "Rel path CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2Fcss%2Fcommon.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2Fcommon2.css');</style>\n" +
    "<br>\n" +
    "Rel CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fcommon.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fcommon2.css');</style>\n" +
    "<br>\n" +
    "Abs CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fextra.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fextra2.css');</style>\n" +
    "<br>\n" +
    "Mixed CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fextra3.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2FEXTRA4.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fextra5.css');</style>\n" +
    "<br>\n" +
    "Rel CSS import w/ bare url<style type=\"text/css\" media=\"screen,print\">@import 'LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fcss%2Fcommon.css'; @import 'LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2Fcommon2.css';</style>\n" +
    "<br>\n" +

    "</body>\n" +
    "</HTML>\n";

  /** 
   * The transformed minimal page; CPROTO is the protocol for the content server
   * and LPROTO is the protocol of the LOCKSS server
   */
  static String xformedCssMinimal =
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\n" +
    "<html>\n" +
    "<head>\n" +
    "</head>\n" +
    "<body>\n" +
    "Rel path CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/content/css/common.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/content/common2.css');</style>\n" +
    "<br>\n" +
    "Rel CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/css/common.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/css/common2.css');</style>\n" +
    "<br>\n" +
    "Abs CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/css/extra.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/css/extra2.css');</style>\n" +
    "<br>\n" +
    "Mixed CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/css/extra3.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/content/EXTRA4.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/extra5.css');</style>\n" +
    "<br>\n" +
    "Rel CSS import w/ bare url<style type=\"text/css\" media=\"screen,print\">@import 'LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/css/common.css'; @import 'LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/content/common2.css';</style>\n" +
    "<br>\n" +

    // Base URL change
    "<base href=\"LPROTO://lockss.box:9524/ServeContent?url=CPROTO%3A%2F%2Fwww.example.com%2Fotherdir%2F\" />\n" +
    "Rel path CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/otherdir/css/common.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/otherdir/common2.css');</style>\n" +
    "<br>\n" +
    "Rel CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/css/common.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/css/common2.css');</style>\n" +
    "<br>\n" +
    "Abs CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/css/extra.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/css/extra2.css');</style>\n" +
    "<br>\n" +
    "Mixed CSS import<style type=\"text/css\" media=\"screen,print\">@import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/css/extra3.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/otherdir/EXTRA4.css') @import url('LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/extra5.css');</style>\n" +
    "<br>\n" +
    "Rel CSS import w/ bare url<style type=\"text/css\" media=\"screen,print\">@import 'LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/css/common.css'; @import 'LPROTO://lockss.box:9524/ServeContent?url=CPROTO://www.example.com/otherdir/common2.css';</style>\n" +
    "<br>\n" +

    "</body>\n" +
    "</HTML>\n";

  /** 
   * The original Javascript page.
   */
  private static final String origScript =
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\n" +
    "<html>\n" +
    "<head>\n" +
    "</head>\n" +
    "<body>\n" +
    "<script type=\"text/javascript\">script test</script>\n" +
    "<br>\n" +
    "<script language=\"javascript\">test script</script>\n" +
    "<br>\n" +
    "</body>\n" +
    "</HTML>\n";

  /** 
   * The transformed JavaScript page.
   */
  private static final String xformedScript =
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\n" +
    "<html>\n" +
    "<head>\n" +
    "</head>\n" +
    "<body>\n" +
    "<script type=\"text/javascript\">j123 script test 123j</script>\n" +
    "<br>\n" +
    "<script language=\"javascript\">j123 test script 123j</script>\n" +
    "<br>\n" +
    "</body>\n" +
    "</HTML>\n";

  static abstract class TestNodeFilterHtmlLinkRewriterFactoryItem
    extends TestNodeFilterHtmlLinkRewriterFactory {
    private MockArchivalUnit au;
    private NodeFilterHtmlLinkRewriterFactory nfhlrf;
    private String encoding = Constants.DEFAULT_ENCODING;
    
    private ServletUtil.LinkTransform xform = null;
    private String testPort = "9524";
  
    // The LOCKSS protocol (http or https)
    String lproto;
    // The content protocol (http or https)
    String cproto;

    // URL info for content protocol
    String urlStem;
    String urlSuffix;
    String url;

    // local test input and output for lockss and content protocols
    String orig;
    String xformed;
    String origCss;
    String xformedCssFull;
    String xformedCssMinimal;
    String origScript;
    String xformedScript;
    
    public void setUp() throws Exception {
      super.setUp();
      au = new MockArchivalUnit();
      au.setLinkRewriterFactory("text/css", new RegexpCssLinkRewriterFactory());
      au.setLinkRewriterFactory("text/javascript",
  			      new MyLinkRewriterFactory("j123 ", " 123j"));
  
      List<String> l = new ArrayList<String>();
      l.add(urlStem);
      au.setUrlStems(l);
      nfhlrf = new NodeFilterHtmlLinkRewriterFactory();
    }
  
    public void testRewriting(String msg,
  			    String src, String exp, boolean hostRel)
        throws Exception {
      if (hostRel) {
        xform = new ServletUtil.LinkTransform() {
  	  public String rewrite(String url) {
  	    return "/ServeContent?url=" + url;
  	  }
  	};
      } else {
        xform = new ServletUtil.LinkTransform() {
  	  public String rewrite(String url) {
  	    return lproto+"://lockss.box:" + testPort + "/ServeContent?url=" + url;
  	  }
  	};
      }
      InputStream is = nfhlrf.createLinkRewriter("text/html", au,
  					       new StringInputStream(src),
  					       encoding, url, xform);
      String out = StringUtil.fromInputStream(is);
      
      log.debug3(msg + " original:\n" + orig);
      log.debug3(msg + " transformed:\n" + out);
      if (hostRel) {
        String relExp =
  	StringUtil.replaceString(exp, lproto+"://lockss.box:" + testPort, "");
        assertEquals(relExp, out);
      } else {
        assertEquals(exp, out);
      }
    }
  
    public void testAbsRewriting() throws Exception {
      testRewriting("Abs", orig, xformed, false);
    }
  
    public void testHostRelativeRewriting() throws Exception {
      testRewriting("Hostrel", orig, xformed, true);
    }
  
    public void testCssRewritingMinimal() throws Exception {
      ConfigurationUtil.addFromArgs(RegexpCssLinkRewriterFactory.PARAM_URL_ENCODE,
  				  "Minimal");
      testRewriting("CSS abs minimal encoding", origCss, xformedCssMinimal,
  		  false);
    }
  
    public void testCssRewritingFull() throws Exception {
      ConfigurationUtil.addFromArgs(RegexpCssLinkRewriterFactory.PARAM_URL_ENCODE,
  				  "Full");
      testRewriting("CSS abs full encoding", origCss, xformedCssFull,
  		  false);
    }
  
    public void testScriptRewritingMinimal() throws Exception {
      testRewriting("CSS abs minimal encoding", origScript, xformedScript,
  		  false);
    }
   
    void setupProtos(String cproto, String lproto) {
      this.cproto = cproto;
      this.lproto = lproto;
      
      urlStem = cproto + "://www.example.com/";
      urlSuffix = "content/index.html";
      url = urlStem + urlSuffix;

      this.orig = TestNodeFilterHtmlLinkRewriterFactory.orig
          .replace("CPROTO", cproto);
      this.xformed = TestNodeFilterHtmlLinkRewriterFactory.xformed
          .replace("CPROTO", cproto).replace("LPROTO", lproto);
      this.origCss = TestNodeFilterHtmlLinkRewriterFactory.origCss
          .replace("CPROTO", cproto);
      this.xformedCssFull = TestNodeFilterHtmlLinkRewriterFactory.xformedCssFull
        .replace("CPROTO", cproto).replace("LPROTO", lproto);
      this.xformedCssMinimal = TestNodeFilterHtmlLinkRewriterFactory.xformedCssMinimal
          .replace("CPROTO", cproto).replace("LPROTO", lproto);
      this.origScript = TestNodeFilterHtmlLinkRewriterFactory.origScript
          .replace("CPROTO", cproto);
      this.xformedScript = TestNodeFilterHtmlLinkRewriterFactory.xformedScript
          .replace("CPROTO", cproto).replace("LPROTO", lproto);
    }
  }

  
  static class MyLinkRewriterFactory implements LinkRewriterFactory {

    private String prefix;
    private String suffix;

    public MyLinkRewriterFactory(String prefix, String suffix) {
      this.prefix = prefix;
      this.suffix = suffix;
      log.info("new MyLinkRewriterFactory(" + prefix + ", " + suffix + ")");
    }

    public InputStream createLinkRewriter(String mimeType,
					  ArchivalUnit au,
					  InputStream in,
					  String encoding,
					  String url,
					  ServletUtil.LinkTransform xform)
	throws PluginException {
      log.info("createLinkRewriter(" + prefix + ", " + suffix + ")");
      List<InputStream> lst = ListUtil.list(new StringInputStream(prefix),
					    in,
					    new StringInputStream(suffix));
      return new SequenceInputStream(Collections.enumeration(lst));
    }
  }

  static public class TestNodeFilterHtmlLinkRewriterFactoryItem1 
    extends TestNodeFilterHtmlLinkRewriterFactoryItem {
    { setupProtos("http", "http"); }
  };

  static public class TestNodeFilterHtmlLinkRewriterFactoryItem2 
    extends TestNodeFilterHtmlLinkRewriterFactoryItem {
    { setupProtos("https", "http"); }
  };

  static public class TestNodeFilterHtmlLinkRewriterFactoryItem3 
    extends TestNodeFilterHtmlLinkRewriterFactoryItem {
    { setupProtos("http", "https"); }
  };

  static public class TestNodeFilterHtmlLinkRewriterFactoryItem4
    extends TestNodeFilterHtmlLinkRewriterFactoryItem {
    { setupProtos("https", "https"); }
  };

  public static Test suite() {
    return variantSuites(new Class[] {
        TestNodeFilterHtmlLinkRewriterFactoryItem1.class,
        TestNodeFilterHtmlLinkRewriterFactoryItem2.class,
        TestNodeFilterHtmlLinkRewriterFactoryItem3.class,
        TestNodeFilterHtmlLinkRewriterFactoryItem4.class,
    });
  }


  public static void main(String[] argv) {
    String[] testCaseList = {
      TestNodeFilterHtmlLinkRewriterFactory.class.getName()
    };
    junit.textui.TestRunner.main(testCaseList);
  }

}
