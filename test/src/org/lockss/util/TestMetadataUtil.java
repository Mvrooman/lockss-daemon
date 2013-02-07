/*
 * $Id: TestMetadataUtil.java,v 1.11 2012/01/16 18:03:14 pgust Exp $
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

package org.lockss.util;

import java.util.*;
import java.net.URLDecoder;
import org.apache.commons.lang.LocaleUtils;

import org.lockss.test.*;


/**
 * Created by IntelliJ IDEA.
 * User: dsferopo
 * Date: Dec 17, 2009
 * Time: 3:40:29 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestMetadataUtil extends LockssTestCase {

  void assertLocale(String lang, Locale l) {
    assertEquals("Language", lang, l.getLanguage());
  }

  void assertLocale(String lang, String country, Locale l) {
    assertEquals("Language", lang, l.getLanguage());
    assertEquals("Country", country, l.getCountry());
  }

  void assertLocale(String lang, String country, String variant, Locale l) {
    assertEquals("Language", lang, l.getLanguage());
    assertEquals("Country", country, l.getCountry());
    assertEquals("Variant", variant, l.getVariant());
  }

  static Set<Locale> testLocales =
    SetUtil.set(
      new Locale("aa"),
  		new Locale("bb"),
  		new Locale("bb", "XX"),
  		new Locale("cc"),
  		new Locale("cc", "XX"),
  		new Locale("cc", "YY"),
  		new Locale("cc", "XX", "V1"),
  
  		new Locale("dd", "WW"),
  		new Locale("ee", "WW", "V3")
		);
		  

  String findClosestLocale(String str) {
    Locale l =  MetadataUtil.findClosestLocale(LocaleUtils.toLocale(str),
					       testLocales);
    return l != null ? l.toString() : null;
  }

  String findClosestAvailableLocale(String str) {
    Locale l =
      MetadataUtil.findClosestAvailableLocale(LocaleUtils.toLocale(str));
    return l != null ? l.toString() : null;
  }

  public void testFindClosestLocale() {
    assertEquals("aa", findClosestLocale("aa"));
    assertEquals("aa", findClosestLocale("aa_XX"));
    assertEquals("aa", findClosestLocale("aa_XX_V1"));

    assertEquals("bb", findClosestLocale("bb"));
    assertEquals("bb_XX", findClosestLocale("bb_XX"));
    assertEquals("bb_XX", findClosestLocale("bb_XX_V1"));
    assertEquals("bb", findClosestLocale("bb_YY"));
    assertEquals("bb", findClosestLocale("bb_YY_V1"));

    assertEquals("cc", findClosestLocale("cc"));
    assertEquals("cc_XX", findClosestLocale("cc_XX"));
    assertEquals("cc_XX_V1", findClosestLocale("cc_XX_V1"));
    assertEquals("cc_XX", findClosestLocale("cc_XX_V2"));
    assertEquals("cc", findClosestLocale("cc_ZZ"));

    assertEquals("cc_YY", findClosestLocale("cc_YY"));
    assertEquals("cc_YY", findClosestLocale("cc_YY_V1"));
    assertEquals("cc_YY", findClosestLocale("cc_YY_V2"));

    assertEquals(null, findClosestLocale("xx"));
    assertEquals(null, findClosestLocale("xx_XX"));
    assertEquals(null, findClosestLocale("xx_XX_V1"));

    assertEquals("dd_WW", findClosestLocale("dd_WW"));
    assertEquals(null, findClosestLocale("dd_VV"));

    assertEquals("ee_WW_V3", findClosestLocale("ee_WW_V3"));
    assertEquals(null, findClosestLocale("ee_WW_V4"));
    assertEquals(null, findClosestLocale("ee_VV"));
  }

  // Java spec says Locale.US must exist; still not sure this will succeed
  // in all environments
  public void testFindClosestAvailableLocale() {
    assertEquals("en", findClosestAvailableLocale("en"));
    assertEquals("en", findClosestAvailableLocale("en_ZF"));
    assertEquals("en_US", findClosestAvailableLocale("en_US"));
  }

  static Locale DEF_LOC = MetadataUtil.DEFAULT_DEFAULT_LOCALE;

  public void testConfigDefaultLocale() {
    assertEquals(DEF_LOC, MetadataUtil.getDefaultLocale());
    ConfigurationUtil.setFromArgs(MetadataUtil.PARAM_DEFAULT_LOCALE, "fr_CA");
    assertLocale("fr", "CA", MetadataUtil.getDefaultLocale());
    ConfigurationUtil.setFromArgs(MetadataUtil.PARAM_DEFAULT_LOCALE, "");
    assertEquals(DEF_LOC, MetadataUtil.getDefaultLocale());
  }


  private String validISBN10s [] = {
      "99921-58-10-7",
      "9971-5-0210-0",
      "80-902734-1-6",
      "85-359-0277-5",
      "1-84356-028-3",
      "0-684-84328-5",
      "0-8044-2957-X",
      "0-85131-041-9",
      "0-943396-04-2"
  };
  
  private String invalidISBN10s[] = {
      "99921-48-10-7",
      "9971-5-0110-0",
      "80-902735-1-6",
      "85-359-0278-5",
      "1-84356-028-2",
      "0-684-83328-5",
      "0-8044-2757-X",
      "0-85131-042-9",
      "0-943396-14-2"
  };
  
  private String validISBN13s [] = {
      "978-99921-58-10-4",
      "978-9971-5-0210-2",
      "978-80-902734-1-2",
      "978-85-359-0277-8",
      "978-1-84356-028-9",
      "978-0-684-84328-5",
      "978-0-8044-2957-3",
      "978-0-85131-041-1",
      "978-0-943396-04-0"
  };

  private String invalidISBN13s[] = {
      "978-99931-58-10-4",
      "978-9971-5-0200-2",
      "978-80-901734-1-2",
      "978-85-359-1277-8",
      "978-1-84356-128-9",
      "978-0-682-84328-5",
      "978-0-8043-2957-3",
      "978-0-85130-041-1",
      "978-0-942396-04-0"
  };

  private String malformedISBNs[] = {
      "X78-99931-58-10-4",
      "X9921-48-10-7",
      "0-8044-2957-Z",
      "1234-5678",
      
  };
  
  private String validISSNS [] = {
          "1144-875X",
          "1543-8120",
          "1508-1109",
          "1733-5329",
          "0740-2783",
          "0097-4463",
          "1402-2001",
          "1523-0430",
          "1938-4246",
          "0006-3363"
  };

  private String invalidISSNS [] = {
          "1144-175X",
          "1144-8753",
          "1543-8122",
          "1541-8120",
          "1508-1409",
          "2740-2783"
  };
  
  private String malformedISSNS [] = {
      "140-42001",
      "15236-430",
      "1402-200",
      "1938/4246",
      "1402",
      "1402-",
      "-4246"
  };
  
  private String validDOIS [] = {
          "10.1095/biolreprod.106.054056",
          "10.2992/007.078.0301",
          "10.1206/606.1",
          "10.1640/0002-8444-99.2.61",
          "10.1663/0006-8101(2007)73[267:TPOSRI]2.0.CO;2",
          "10.1663/0006-8101(2007)73[267%3ATPOSRI]2.0.CO%3B2",
          "10.1640/0002-8444/99.2.61"
  };

  private String invalidDOIS [] = {
          "12.1095/biolreprod.106.054056",
          "10.2992-007.078.0301",
          "10.1206",
          "/0002-8444-99.2.61",
          "-0002-8444-99.2.61",
	  null
  };

  public void testISBN() {
    // valid with checksum calculations
    for(int i=0; i<validISBN10s.length;i++) {
      assertTrue(MetadataUtil.isIsbn(validISBN10s[i], true));
    }

    // malformed ISBN
    for(int i=0; i<malformedISBNs.length;i++){
      assertFalse(MetadataUtil.isIsbn(malformedISBNs[i], false));
    }

    // invalid with checksum calculations
    for(int i=0; i<invalidISBN10s.length;i++){
      assertFalse(MetadataUtil.isIsbn(invalidISBN10s[i], true));
    }

    // valid ignoring checksum calculations
    for(int i=0; i<invalidISBN10s.length;i++){
      assertTrue(MetadataUtil.isIsbn(invalidISBN10s[i], false));
    }

    // valid with checksum calculations
    for(int i=0; i<validISBN13s.length;i++){
      assertTrue(MetadataUtil.isIsbn(validISBN13s[i], true));
    }

    // invalid with checksum calculations
    for(int i=0; i<invalidISBN13s.length;i++){
      assertFalse(MetadataUtil.isIsbn(invalidISBN13s[i], true));
    }

    // valid ignoring checksum calculation
    for(int i=0; i<invalidISBN13s.length;i++){
      assertTrue(MetadataUtil.isIsbn(invalidISBN13s[i], false));
    }
    
    // Knuth vol 4A 1st edition
    
    // test ISBN conversions with correct check digit
    assertEquals("0201038048", MetadataUtil.toIsbn10("978-0-201-03804-0"));
    assertEquals("0201038048", MetadataUtil.toIsbn10("9780201038040"));
    assertEquals("0201038048", MetadataUtil.toIsbn10("0-201-03804-8"));
    assertEquals("0201038048", MetadataUtil.toIsbn10("0201038048"));

    assertEquals("9780201038040", MetadataUtil.toIsbn13("978-0-201-03804-0"));
    assertEquals("9780201038040", MetadataUtil.toIsbn13("9780201038040"));
    assertEquals("9780201038040", MetadataUtil.toIsbn13("0-201-03804-8"));
    assertEquals("9780201038040", MetadataUtil.toIsbn13("0201038048"));

    
    // test ISBN conversions with incorrect check digit
    assertEquals("0201038048", MetadataUtil.toIsbn10("978-0-201-03804-7"));
    assertEquals("0201038048", MetadataUtil.toIsbn10("9780201038047"));
    assertEquals("0201038048", MetadataUtil.toIsbn10("0-201-03804-X"));
    assertEquals("0201038048", MetadataUtil.toIsbn10("020103804X"));
    
    assertEquals("9780201038040", MetadataUtil.toIsbn13("978-0-201-03804-7"));
    assertEquals("9780201038040", MetadataUtil.toIsbn13("9780201038047"));
    assertEquals("9780201038040", MetadataUtil.toIsbn13("0-201-03804-X"));
    assertEquals("9780201038040", MetadataUtil.toIsbn13("020103804X"));
    
    // test ISBN conversions with invalid check digit
    assertNull(MetadataUtil.toIsbn10("978-0-201-03804-X"));
    assertNull(MetadataUtil.toIsbn10("978020103804X"));
    assertNull(MetadataUtil.toIsbn10("0-201-03804-Z"));
    assertNull(MetadataUtil.toIsbn10("020103804Z"));
  
    assertNull(MetadataUtil.toIsbn13("978-0-201-03804-X"));
    assertNull(MetadataUtil.toIsbn13("978020103804X"));
    assertNull(MetadataUtil.toIsbn13("0-201-03804-Z"));
    assertNull(MetadataUtil.toIsbn13("020103804Z"));
  
    // test ISBN conversions with invalid input issn
    assertNull(MetadataUtil.toIsbn10("978-X-201-03804-0"));
    assertNull(MetadataUtil.toIsbn10("9780X01038040"));
    assertNull(MetadataUtil.toIsbn10("0-2X1-03804-8"));
    assertNull(MetadataUtil.toIsbn10("02X1038048"));
    assertNull(MetadataUtil.toIsbn10("xyzzy"));

    assertNull(MetadataUtil.toIsbn13("978-X-201-03804-0"));
    assertNull(MetadataUtil.toIsbn13("9780X01038040"));
    assertNull(MetadataUtil.toIsbn13("0-2X1-03804-8"));
    assertNull(MetadataUtil.toIsbn13("02X1038048"));
    assertNull(MetadataUtil.toIsbn13("xyzzy"));
    
    // test ISBN formatting
    assertEquals("978-0-201-03804-0", MetadataUtil.formatIsbn("9780201038040"));
    assertEquals("0-201-03804-8", MetadataUtil.formatIsbn("0201038048"));
    assertEquals("xyzzy", MetadataUtil.formatIsbn("xyzzy"));
  }

  public void testISSN() {
    for(int i=0; i<validISSNS.length;i++){
      String validIssn = validISSNS[i];
      assertTrue(MetadataUtil.isIssn(validIssn));
      assertEquals(validIssn, MetadataUtil.validateIssn(validIssn));
    }

    for(int j=0; j<malformedISSNS.length;j++){
      String malformedIssn = malformedISSNS[j];
      assertFalse(MetadataUtil.isIssn(malformedIssn));
      assertEquals(null, MetadataUtil.validateIssn(malformedIssn));
    }

    // invalid with checksum calculations
    for(int j=0; j<invalidISSNS.length;j++){
      String invalidIssn = invalidISSNS[j];
      assertFalse(MetadataUtil.isIssn(invalidISSNS[j], true));
      assertEquals(null, MetadataUtil.validateIssn(invalidIssn, true));
    }

    // valid ignoring checksum calculations
    for(int j=0; j<invalidISSNS.length;j++){
      String invalidIssn = invalidISSNS[j];
      assertTrue(MetadataUtil.isIssn(invalidIssn, false));
      assertEquals(invalidIssn, MetadataUtil.validateIssn(invalidIssn, false));
    }
    
    // test ISSN formatting functions
    assertEquals("1234-5678", MetadataUtil.formatIssn("12345678"));
    assertEquals("xyzzy", MetadataUtil.formatIssn("xyzzy"));
  }

  public void testDOI() {
    for(int i=0; i<validDOIS.length;i++){
      assertTrue(MetadataUtil.isDoi(URLDecoder.decode(validDOIS[i])));
    }

    for(int j=0; j<invalidDOIS.length;j++){
      assertFalse(MetadataUtil.isDoi(invalidDOIS[j]));
    }
  }
}
