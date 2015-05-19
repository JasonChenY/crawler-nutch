package org.apache.nutch.companyschema;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
//import org.apache.nutch.indexer.solr.SolrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.oro.text.perl.MalformedPerl5PatternException;
import org.apache.oro.text.perl.Perl5Util;

/* Purpose for this class:
 * - some company use json file, json-path is not powerful in string operation
 *   we need do some string operation via Perl5 regex to get the clean information
 * - different company use different city name, w/o "shi", this can be done in solr
 *   but it is better to commit same format toward solr in the very beginnng phase
 * - chinese and english city name as well.
 */
public class LocationUtils {
    private static List<NameValuePair> cities = initialize_cities();
    private static List<NameValuePair> initialize_cities() {
        List<NameValuePair> cities = new ArrayList<NameValuePair>();

        try {
            /* it is fortunate that all the china city won't have this word in base name */
            cities.add(new BasicNameValuePair(new String("市".getBytes(), "UTF-8"), ""));
            cities.add(new BasicNameValuePair(new String("'".getBytes(), "UTF-8"), ""));
            cities.add(new BasicNameValuePair(new String("hong kong".getBytes(), "UTF-8"), new String("香港".getBytes(), "UTF-8")));
            /* is it a good idea to translate all english to chinese city name here
             * will be too slow, need find a better way, find tokenized word from hash?
             */
        } catch (Exception e) {
        }
        return cities;
    }
    private static String format(String str) {
        /* Microsoft use some chinese word */
        for ( int i = 0; i < cities.size(); i++ ) {
            str = str.replaceAll(cities.get(i).getName(), cities.get(i).getValue());
        }
        return str;
    }

    public static String format(String d, String regex) {
        d = d.trim();
        //d = SolrUtils.stripNonCharCodepoints(d);
        if ( regex != null && !regex.isEmpty() ){
            try {
                Perl5Util plutil = new Perl5Util();
                d = plutil.substitute(regex, d);
            } catch (MalformedPerl5PatternException me ) {
                System.out.println("location " + d + " cant be matched with " + regex);
            }
        }
        return format(d);
    }

    public static void main(String args[]) {
        String    strs[] = {"hong kong", "上海市/南京市",  "中国 - 江苏 - 南京市 ", 
                  "中国 / 江苏 / 南京市 ",                 "中国 - 江苏 - 南京市  ",};
        String formats[] = {"",          "" ,              "s/[^-]+-[^-]+-\\s*(\\S*)/$1/g", 
                  "s/[^\\/]+\\/[^\\/]+\\/\\s*(\\S*)/$1/g", "s/(.*-)*\\s*(.*)(\\s*-\\s*)*$/$2/g", };

        /* Huawei */
        String reg = "s/(.*-)*\\s*([^-]+)(\\s*-\\s*)?$/$2/g";
        String tests[] = {"中国-江苏-南京市", "中国-江苏-", " 中国 - 江苏 - 南京市 ", " 中国 - 江苏 - "};
        try {
            for ( int i = 0; i < strs.length; i++ ) {
                System.out.println(strs[i] + " : " + format(strs[i], formats[i]));
            }
            for ( int i = 0; i < tests.length; i++ ) {
                System.out.println(tests[i] + " : " + format(tests[i], reg));
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }

        /* Honeywell */
        reg = "s/([^-,]*).*/$1/g";
        String tests2[] = {"Nanjing-Jiangsu-China", "China", "Shanghai,China"};
        try {
            for ( int i = 0; i < tests2.length; i++ ) {
                System.out.println(tests2[i] + " : " + format(tests2[i], reg));
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
}