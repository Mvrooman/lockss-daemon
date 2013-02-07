/*
 * $Id: TestProbePermissionChecker.java,v 1.10 2012/03/12 05:26:37 tlipkis Exp $
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.daemon;

import java.io.*;
import java.util.*;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.plugin.*;
import org.lockss.config.*;
import org.lockss.crawler.*;
import org.lockss.crawler.BaseCrawler.StorePermissionScheme;

public class TestProbePermissionChecker extends LockssTestCase {

  private ProbePermissionChecker pc;
  MockArchivalUnit mau;
  MyPermissionHelper helper;

  private static String htmlSourceWOLinkTag =
    "<html>\n"+
    "  <head>\n"+
    "    <title>\n"+
    "      Human Molecular Genetics Volume 14 LOCKSS Manifest Page\n"+
    "    </title>\n"+
    "  </head>\n"+
    "    <body>\n"+
    "      <h1>      Human Molecular Genetics Volume 14 LOCKSS Manifest Page</h1>\n"+
    "      <ul>\n"+
    "    </body>\n"+
    "</html>\n";

  private static String htmlSourceWProbe =
    "<html>\n"+
    "  <head>\n"+
    "    <title>\n"+
    "      Human Molecular Genetics Volume 14 LOCKSS Manifest Page\n"+
    "    </title>\n"+
    "    <link href=\"/cgi/content/full/14/9/1109\" lockss-probe=\"true\" />\n"+
    "  </head>\n"+
    "    <body>\n"+
    "      <h1>      Human Molecular Genetics Volume 14 LOCKSS Manifest Page</h1>\n"+
    "      <ul>\n"+
    "    </body>\n"+
    "</html>\n";

  private static String htmlSourceWOProbe =
    "<html>\n"+
    "  <head>\n"+
    "    <title>\n"+
    "      Human Molecular Genetics Volume 14 LOCKSS Manifest Page\n"+
    "    </title>\n"+
    "    <link href=\"/cgi/content/full/14/9/1109\" />\n"+
    "  </head>\n"+
    "    <body>\n"+
    "      <h1>      Human Molecular Genetics Volume 14 LOCKSS Manifest Page</h1>\n"+
    "      <ul>\n"+
    "    </body>\n"+
    "</html>\n";


  public void setUp() throws Exception {
    super.setUp();
    mau = new MockArchivalUnit();
    helper = new MyPermissionHelper(mau);
  }

  public void testConstructorNullArchivalUnit() {
    try {
      new ProbePermissionChecker(null);
      fail("Calling ProbePermissionChecker constructor with a null Archival Unit should throw");
    } catch (NullPointerException ex) {
    }
  }

  public void testNoLinkTag() {
    String url = "http://www.example.com";
    mau.addUrl(url, true, true);
    mau.addContent(url, htmlSourceWOProbe);

    pc = new ProbePermissionChecker(mau);
    assertFalse("Incorrectly gave permission when there was no probe",
		pc.checkPermission(helper,
				   new StringReader(htmlSourceWOLinkTag),
				   url));
  }

  public void testNoProbe() {
    String url = "http://www.example.com";
    mau.addUrl(url, true, true);
    mau.addContent(url, htmlSourceWOProbe);

    pc = new ProbePermissionChecker(mau);
    assertFalse("Incorrectly gave permission when there was no probe",
		pc.checkPermission(helper, new StringReader(htmlSourceWOProbe),
				   url));
  }

  StorePermissionScheme getConfigPermissionScheme() {
    Configuration config = ConfigManager.getCurrentConfig();
    return (StorePermissionScheme)
      config.getEnum(StorePermissionScheme.class,
		     BaseCrawler.PARAM_STORE_PERMISSION_SCHEME,
		     BaseCrawler.DEFAULT_STORE_PERMISSION_SCHEME);
  }

  public void testProbeHasPermission() {
    String probeUrl = "http://www.example.com/cgi/content/full/14/9/1109";

    String url = "http://www.example.com";
    mau.addUrl(url, true, true);
    mau.addContent(url, htmlSourceWProbe);
    mau.addUrl(probeUrl, true, true);
    mau.addContent(probeUrl, "");

    pc = new ProbePermissionChecker(mau);
    assertTrue("Didn't give permission when there was a probe",
		pc.checkPermission(helper, new StringReader(htmlSourceWProbe),
				   "http://www.example.com"));

    assertEquals(StorePermissionScheme.Legacy, getConfigPermissionScheme());
    assertEquals(0, helper.storeCnt);
  }

  public void testProbeHasPermissionWithStore() {
    ConfigurationUtil.addFromArgs(BaseCrawler.PARAM_STORE_PERMISSION_SCHEME,
				  StorePermissionScheme.StoreAllInSpec.toString());
    String probeUrl = "http://www.example.com/cgi/content/full/14/9/1109";

    String url = "http://www.example.com";
    mau.addUrl(url, true, true);
    mau.addContent(url, htmlSourceWProbe);
    mau.addUrl(probeUrl, true, true);
    mau.addContent(probeUrl, "");

    pc = new ProbePermissionChecker(mau);
    assertTrue("Didn't give permission when there was a probe",
		pc.checkPermission(helper, new StringReader(htmlSourceWProbe),
				   "http://www.example.com"));
    assertEquals(1, helper.storeCnt);
  }

  public void testProbeNoPermission() {
    ConfigurationUtil.addFromArgs(BaseCrawler.PARAM_STORE_PERMISSION_SCHEME,
				  StorePermissionScheme.StoreAllInSpec.toString());
    String probeUrl = "http://www.example.com/cgi/content/full/14/9/1109";

    String url = "http://www.example.com";
    mau.addUrl(url, true, true);
    mau.addContent(url, htmlSourceWProbe);
    mau.addUrl(probeUrl, true, true);
    MockUrlCacher muc = (MockUrlCacher)mau.makeUrlCacher(probeUrl);
    muc.setCachingException(new CacheException.PermissionException("test"),
			    1);

    pc = new ProbePermissionChecker(mau);
    assertFalse("Gave permission when the nested checker denied it",
	       pc.checkPermission(helper, new StringReader(htmlSourceWProbe),
				  "http://www.example.com"));
    assertEquals(0, helper.storeCnt);
  }

  static class MyPermissionHelper extends MockPermissionHelper {
    ArchivalUnit au;
    int storeCnt = 0;

    MyPermissionHelper(ArchivalUnit au) {
      this.au = au;
    }

    @Override
    public UrlCacher makePermissionUrlCacher(String url) {
      return au.makeUrlCacher(url);
    }

    @Override
    public void storePermissionPage(UrlCacher uc, BufferedInputStream is)
	throws IOException {
      storeCnt++;
    }
  }
}
