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
package org.apache.nutch.companyschema;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class CompanySchema {
    private String name;
    private String cookie;
    /* "static" always use cookied configured in schema,
    should update with cookie get dynamically (Microsoft)*/
    private String cookieType;

    private String l1_url;
    private String l1_method;
    private String l1_postdata;
    private String post_content_type;

    private String l2_content_type;
    private String l2_template_for_nextpage_url;
    private String l2_schema_for_nextpage_url;
    private String l2_nextpage_method;
    private String l2_template_for_nextpage_postdata;
    private String l2_nextpage_regex;
    private String l2_nextpage_increment;
    private String l2_last_page;
    /* Oracle case, where no last page, only total number of page in an special format string,
       Using regex to generate the Math string, then in code to calcuate the total page number.
     */
    private String l2_regex_matcher_for_jobnbr;

    /* fields for Microsoft kind of site */
    private String l2_nextpage_postdata_inherit_regex;
    private String l2_nextpage_endflag;

    /* Oracle Case, where we cannt get list of jobs via XPATH, instead via regex matcher */
    private String l2_regex_matcher_for_jobs;

    private String l2_schema_for_jobs;
    private String l2_template_for_joburl;
    private String l2_template_for_joburl_repr; /* For Huawei, one url for fetch json, this url for apply */
    private String l2_joburl_regex;
    private String l2_schema_for_joburl;
    private String l2_schema_for_joburl_repr; /* For Danone, one url for fetch pdf, another(repr) for apply */
    private String l2_job_title;
    private String l2_job_location;
    private String l2_job_date;
    /* field for Alibaba */
    private String l2_job_description;
    /* for Bayer, summary page fetched via webdriver */
    private String l2_summarypage_method;

    private String l3_job_title;
    /* field for Danone, job post date in l3 pdf file */
    private String l3_job_date;
    private String l3_job_description;
    /* Intel: many jobs have a ["multiple location"] in L2, should get job_location from L3*/
    private String l3_job_location;
    private String l3_regex_matcher_for_job; /* Oracle */

    /* field for dateformat, this will be applied to either l2(Ericsson) or l3(Danone)*/
    private String job_date_format;
    /* mainly for Huawei's  country-province-city or country-city- format */
    private String job_location_format_regex;
    /* for Oracle case: Difficult to handle it in one match-replacer statement:
    IN-IN,India-Hyderabad, SG-SG,Singapore-Singapore, CN-CN,China-Dalian, AU-AU,Australia-Sydney */
    private String job_regex_matcher_for_location;

    public CompanySchema(String n) {
        setName(n);
        setCookie("");
        setCookieType("");
        setL1_url("");
        setL1_method("GET");
        setL1_postdata("");
        setPost_content_type("");

        setL2_content_type("HTML");
        setL2_template_for_nextpage_url("");
        setL2_schema_for_nextpage_url("");
        setL2_nextpage_method("GET");
        setL2_template_for_nextpage_postdata("");
        setL2_nextpage_regex("");
        setL2_nextpage_increment("1");
        setL2_last_page("");
        setL2_regex_matcher_for_jobnbr("");

        setL2_nextpage_postdata_inherit_regex("");
        setL2_nextpage_endflag("");

        setL2_regex_matcher_for_jobs("");
        setL2_schema_for_jobs("");
        setL2_template_for_joburl("");
        setL2_template_for_joburl_repr("");
        setL2_joburl_regex("");
        setL2_schema_for_joburl("");
        setL2_schema_for_joburl_repr("");
        setL2_job_title("");
        setL2_job_location("");
        setL2_job_date("");
        setL2_job_description("");
        setL2_summarypage_method("GET");

        setL3_job_title("");
        setL3_job_date("");
        setL3_job_description("");
        setL3_job_location("");
        setL3_regex_matcher_for_job("");

        setJob_date_format("");
        setJob_location_format_regex("");
        setJob_regex_matcher_for_location("");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null) this.name = name;
    }

    public String getCookie() { return cookie; }

    public void setCookie(String cookie) {
        if (cookie != null) this.cookie = cookie;
    }

    public String getCookieType() { return cookieType; }

    public void setCookieType(String cookieType) {
        if (cookieType != null) this.cookieType = cookieType;
    }

    public String getL1_url() {
        return l1_url;
    }

    public void setL1_url(String l1_url) {
        if ( l1_url != null ) this.l1_url = l1_url;
    }

    public String getL1_method() {
        return l1_method;
    }

    public void setL1_method(String l1_method) {
        if ( l1_method != null && l1_method.equalsIgnoreCase("POST") )
            this.l1_method = "POST";
        else
            this.l1_method = "GET";
    }

    public String getL1_postdata() {
        return l1_postdata;
    }

    public void setL1_postdata(String l1_postdata) {
        if (l1_postdata != null) this.l1_postdata = l1_postdata;
    }

    public String getPost_content_type() {
        return post_content_type;
    }

    public void setPost_content_type(String post_content_type) {
        if (post_content_type != null) this.post_content_type = post_content_type;
    }

    public String getL2_content_type() {
        return l2_content_type;
    }

    public void setL2_content_type(String l2_content_type) {
        if (l2_content_type != null) this.l2_content_type = l2_content_type;
    }

    public String getL2_template_for_nextpage_url() {
        return l2_template_for_nextpage_url;
    }

    public void setL2_template_for_nextpage_url(String l2_template_for_nextpage_url) {
        if (l2_template_for_nextpage_url != null) this.l2_template_for_nextpage_url = l2_template_for_nextpage_url;
    }

    public String getL2_schema_for_nextpage_url() {
        return l2_schema_for_nextpage_url;
    }

    public void setL2_schema_for_nextpage_url(String l2_schema_for_nextpage_url) {
        if (l2_schema_for_nextpage_url != null) this.l2_schema_for_nextpage_url = l2_schema_for_nextpage_url;
    }

    public String getL2_nextpage_method() {
        return l2_nextpage_method;
    }

    public void setL2_nextpage_method(String l2_nextpage_method) {
        if ( l2_nextpage_method != null && l2_nextpage_method.equalsIgnoreCase("POST") )
            this.l2_nextpage_method = "POST";
        else
            this.l2_nextpage_method = "GET";
    }

    public String getL2_template_for_nextpage_postdata() {
        return l2_template_for_nextpage_postdata;
    }

    public void setL2_template_for_nextpage_postdata(String l2_template_for_nextpage_postdata) {
        if (l2_template_for_nextpage_postdata != null) this.l2_template_for_nextpage_postdata = l2_template_for_nextpage_postdata;
    }

    public String getL2_nextpage_regex() {
        return l2_nextpage_regex;
    }

    public void setL2_nextpage_regex(String l2_nextpage_regex) {
        if (l2_nextpage_regex != null) this.l2_nextpage_regex = l2_nextpage_regex;
    }

    public String getL2_nextpage_increment() {
        return l2_nextpage_increment;
    }

    public void setL2_nextpage_increment(String l2_nextpage_increment) {
        if (l2_nextpage_increment != null) this.l2_nextpage_increment = l2_nextpage_increment;
    }

    public String getL2_last_page() {
        return l2_last_page;
    }

    public void setL2_last_page(String l2_last_page) {
        if (l2_last_page != null) this.l2_last_page = l2_last_page;
    }

    public String getL2_regex_matcher_for_jobnbr() {
        return l2_regex_matcher_for_jobnbr;
    }

    public void setL2_regex_matcher_for_jobnbr(String l2_regex_matcher_for_jobnbr) {
        if (l2_regex_matcher_for_jobnbr != null) this.l2_regex_matcher_for_jobnbr = l2_regex_matcher_for_jobnbr;
    }

    public String getL2_nextpage_postdata_inherit_regex() {
        return l2_nextpage_postdata_inherit_regex;
    }

    public void setL2_nextpage_postdata_inherit_regex(String l2_nextpage_postdata_inherit_regex) {
        if (l2_nextpage_postdata_inherit_regex != null) this.l2_nextpage_postdata_inherit_regex = l2_nextpage_postdata_inherit_regex;
    }

    public String getL2_nextpage_endflag() {
        return l2_nextpage_endflag;
    }

    public void setL2_nextpage_endflag(String l2_nextpage_endflag) {
        if (l2_nextpage_endflag != null) this.l2_nextpage_endflag = l2_nextpage_endflag;
    }

    public String getL2_regex_matcher_for_jobs() {
        return l2_regex_matcher_for_jobs;
    }

    public void setL2_regex_matcher_for_jobs(String l2_regex_matcher_for_jobs) {
        if (l2_regex_matcher_for_jobs != null) this.l2_regex_matcher_for_jobs = l2_regex_matcher_for_jobs;
    }

    public String getL2_schema_for_jobs() {
        return l2_schema_for_jobs;
    }

    public void setL2_schema_for_jobs(String l2_schema_for_jobs) {
        if (l2_schema_for_jobs != null) this.l2_schema_for_jobs = l2_schema_for_jobs;
    }

    public String getL2_template_for_joburl() {
        return l2_template_for_joburl;
    }

    public void setL2_template_for_joburl(String l2_template_for_joburl) {
        if (l2_template_for_joburl != null) this.l2_template_for_joburl = l2_template_for_joburl;
    }

    public String getL2_template_for_joburl_repr() {
        return l2_template_for_joburl_repr;
    }

    public void setL2_template_for_joburl_repr(String l2_template_for_joburl_repr) {
        if (l2_template_for_joburl_repr != null) this.l2_template_for_joburl_repr = l2_template_for_joburl_repr;
    }

    public String getL2_joburl_regex() {
        return l2_joburl_regex;
    }

    public void setL2_joburl_regex(String l2_joburl_regex) {
        if (l2_joburl_regex != null) this.l2_joburl_regex = l2_joburl_regex;
    }

    public String getL2_schema_for_joburl() {
        return l2_schema_for_joburl;
    }

    public void setL2_schema_for_joburl(String l2_schema_for_joburl) {
        if (l2_schema_for_joburl != null) this.l2_schema_for_joburl = l2_schema_for_joburl;
    }

    public String getL2_schema_for_joburl_repr() {
        return l2_schema_for_joburl_repr;
    }

    public void setL2_schema_for_joburl_repr(String l2_schema_for_joburl_repr) {
        if (l2_schema_for_joburl_repr != null) this.l2_schema_for_joburl_repr = l2_schema_for_joburl_repr;
    }

    public String getL2_job_title() {
        return l2_job_title;
    }

    public void setL2_job_title(String l2_job_title) {
        if (l2_content_type != null) this.l2_job_title = l2_job_title;
    }

    public String getL2_job_location() {
        return l2_job_location;
    }

    public void setL2_job_location(String l2_job_location) {
        if (l2_job_location != null) this.l2_job_location = l2_job_location;
    }

    public String getL2_job_date() {
        return l2_job_date;
    }

    public void setL2_job_date(String l2_job_date) {
        if (l2_job_date != null) this.l2_job_date = l2_job_date;
    }

    public String getL2_job_description() {
        return l2_job_description;
    }

    public void setL2_job_description(String l2_job_description) {
        if (l2_job_description != null) this.l2_job_description = l2_job_description;
    }

    public String getL2_summarypage_method() {
        return l2_summarypage_method;
    }

    public void setL2_summarypage_method(String l2_summarypage_method) {
        if (l2_summarypage_method != null) this.l2_summarypage_method = l2_summarypage_method;
    }

    public String getL3_job_title() {
        return l3_job_title;
    }

    public void setL3_job_title(String l3_job_title) {
        if (l3_job_title != null) this.l3_job_title = l3_job_title;
    }

    public String getL3_job_date() {
        return l3_job_date;
    }

    public void setL3_job_date(String l3_job_date) {
        if (l3_job_date != null) this.l3_job_date = l3_job_date;
    }

    public String getL3_job_description() {
        return l3_job_description;
    }

    public void setL3_job_description(String l3_job_description) {
        if (l3_job_description != null) this.l3_job_description = l3_job_description;
    }

    public String getL3_job_location() {
        return l3_job_location;
    }

    public void setL3_job_location(String l3_job_location) {
        if (l3_job_location != null) this.l3_job_location = l3_job_location;
    }

    public String getL3_regex_matcher_for_job() {
        return l3_regex_matcher_for_job;
    }

    public void setL3_regex_matcher_for_job(String l3_regex_matcher_for_job) {
        if (l3_regex_matcher_for_job != null) this.l3_regex_matcher_for_job = l3_regex_matcher_for_job;
    }

    public String getJob_date_format() {
        return job_date_format;
    }

    public void setJob_date_format(String job_date_format) {
        if (job_date_format != null) this.job_date_format = job_date_format;
    }

    public String getJob_location_format_regex() {
        return job_location_format_regex;
    }

    public void setJob_location_format_regex(String job_location_format_regex) {
        if (job_location_format_regex != null) this.job_location_format_regex = job_location_format_regex;
    }

    public String getJob_regex_matcher_for_location() {
        return job_regex_matcher_for_location;
    }

    public void setJob_regex_matcher_for_location(String job_regex_matcher_for_location) {
        if (job_regex_matcher_for_location != null) this.job_regex_matcher_for_location = job_regex_matcher_for_location;
    }

    public void print() {
        Class cs = this.getClass();

        Field field[] = cs.getDeclaredFields();
        for (int i = 0; i < field.length; i++) {
            Field f = field[i];
            String fieldname = f.getName();
            String value = "";
            try {
                value = (String)f.get(this);
                System.out.println(fieldname + " = " + value);
            } catch ( IllegalAccessException e ) {
                System.out.println(fieldname + " access exception");
            }
        }

        Method[] method = cs.getDeclaredMethods();
        for ( int i = 0; i < method.length; i++ ) {
            Method m = method[i];
            String name = m.getName();
            try {
                if ( name.indexOf("get") != -1) {
                    Object returnobject = m.invoke(this, null);
                    String value = "";
                    if (returnobject.getClass().getSimpleName().equals("String")) {
                        value = "'" + returnobject.toString() + "'";
                    } else {
                        value = returnobject.toString();
                    }
                    System.out.println(name + "->" + value);
                }
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }
}

