/*
 * $Id: TestHighWireJavaScriptLinkRewriterFactory.java,v 1.3 2012/09/26 20:59:28 alexandraohlson Exp $
 */

/*

Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.highwire;

import java.io.*;

import org.lockss.util.*;
import org.lockss.test.*;
import org.lockss.daemon.PluginException;
import org.lockss.plugin.highwire.*;

public class TestHighWireJavaScriptLinkRewriterFactory extends LockssTestCase {
  static String ENC = Constants.DEFAULT_ENCODING;

  private HighWireJavaScriptLinkRewriterFactory fact;
  private MockArchivalUnit mau;

  public void setUp() throws Exception {
    super.setUp();
    fact = new HighWireJavaScriptLinkRewriterFactory();
    mau = new MockArchivalUnit();
  }

  static final String input_1 = 
    "org/lockss/plugin/highwire/HighWireJavaScriptLinkRewriter_input_1.html";
  static final String output_1 = 
    "org/lockss/plugin/highwire/HighWireJavaScriptLinkRewriter_output_1.html";

  static final String input_2 = 
    "org/lockss/plugin/highwire/HighWireJavaScriptLinkRewriter_input_1.html";
  static final String output_2 = 
    "org/lockss/plugin/highwire/HighWireJavaScriptLinkRewriter_output_2.html";

  public void testCase1() throws Exception {
    InputStream input = null;
    InputStream filtered = null;
    InputStream expected = null;

    try {
    input = getClass().getClassLoader().getResourceAsStream(input_1);
    filtered = fact.createLinkRewriter("text/html", mau, input, "UTF-8", 
        "http://jid.sagepub.com/cgi/framedreprint/13/1/5", null);
    expected = getClass().getClassLoader().getResourceAsStream(output_1);
    String s_expected = StringUtil.fromInputStream(expected);
    String s_filtered = StringUtil.fromInputStream(filtered); 
    assertEquals(s_expected, s_filtered);
    } finally {
      IOUtil.safeClose(input);
      IOUtil.safeClose(filtered);
      IOUtil.safeClose(expected);
    }
  }

  public void testCase2() throws Exception {
    InputStream input = null;
    InputStream filtered = null;
    InputStream expected = null;

    try {
    input = getClass().getClassLoader().getResourceAsStream(input_2);
    filtered = fact.createLinkRewriter("text/html", mau, input, "UTF-8", 
        "http://jid.sagepub.com/xyzzy/plugh", null);
    expected = getClass().getClassLoader().getResourceAsStream(output_2);
    String s_expected = StringUtil.fromInputStream(expected);
    String s_filtered = StringUtil.fromInputStream(filtered); 
    assertEquals(s_expected, s_filtered);
    } finally {
      IOUtil.safeClose(input);
      IOUtil.safeClose(filtered);
      IOUtil.safeClose(expected);
    }
  }

}
