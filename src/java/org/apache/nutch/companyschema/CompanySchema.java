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

public class CompanySchema {
    private String name;
    private String l1_url;
    private String l1_method;
    private String l1_postdata;

    private String l2_content_type;
    private String l2_prefix_for_nextpage_url;
    private String l2_schema_for_nextpage_url;
    private String l2_nextpage_method;
    private String l2_nextpage_postdata;
    private String l2_nextpage_pattern;
    private String l2_nextpage_increment;
    private String l2_last_page;

    private String l2_schema_for_jobs;
    private String l2_prefix_for_joburl;
    private String l2_schema_for_joburl;
    private String l2_job_title;
    private String l2_job_location;
    private String l2_job_date;
    private String l2_job_description;

    private String l3_job_title;
    private String l3_job_description;

    public CompanySchema(String n) {
	    setName(n);
        setL1_url("");
        setL1_method("GET");
        setL1_postdata("");

        setL2_content_type("HTML");
        setL2_prefix_for_nextpage_url("");
        setL2_schema_for_nextpage_url("");
        setL2_nextpage_method("GET");
        setL2_nextpage_postdata("");
        setL2_nextpage_pattern("");
        setL2_nextpage_increment("1");
        setL2_last_page("");

        setL2_schema_for_jobs("");
        setL2_prefix_for_joburl("");
        setL2_schema_for_joburl("");
        setL2_job_title("");
        setL2_job_location("");
        setL2_job_date("");
        setL2_job_description("");

        setL3_job_title("");
        setL3_job_description("");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null) this.name = name;
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

    public String getL2_content_type() {
        return l2_content_type;
    }

    public void setL2_content_type(String l2_content_type) {
        if (l2_content_type != null) this.l2_content_type = l2_content_type;
    }

    public String getL2_prefix_for_nextpage_url() {
        return l2_prefix_for_nextpage_url;
    }

    public void setL2_prefix_for_nextpage_url(String l2_prefix_for_nextpage_url) {
        if (l2_prefix_for_nextpage_url != null) this.l2_prefix_for_nextpage_url = l2_prefix_for_nextpage_url;
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

    public String getL2_nextpage_postdata() {
        return l2_nextpage_postdata;
    }

    public void setL2_nextpage_postdata(String l2_nextpage_postdata) {
        if (l2_nextpage_postdata != null) this.l2_nextpage_postdata = l2_nextpage_postdata;
    }

    public String getL2_nextpage_pattern() {
        return l2_nextpage_pattern;
    }

    public void setL2_nextpage_pattern(String l2_nextpage_pattern) {
        if (l2_nextpage_pattern != null) this.l2_nextpage_pattern = l2_nextpage_pattern;
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

    public String getL2_schema_for_jobs() {
        return l2_schema_for_jobs;
    }

    public void setL2_schema_for_jobs(String l2_schema_for_jobs) {
        if (l2_schema_for_jobs != null) this.l2_schema_for_jobs = l2_schema_for_jobs;
    }

    public String getL2_prefix_for_joburl() {
        return l2_prefix_for_joburl;
    }

    public void setL2_prefix_for_joburl(String l2_prefix_for_joburl) {
        if (l2_prefix_for_joburl != null) this.l2_prefix_for_joburl = l2_prefix_for_joburl;
    }

    public String getL2_schema_for_joburl() {
        return l2_schema_for_joburl;
    }

    public void setL2_schema_for_joburl(String l2_schema_for_joburl) {
        if (l2_schema_for_joburl != null) this.l2_schema_for_joburl = l2_schema_for_joburl;
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

    public String getL3_job_title() {
        return l3_job_title;
    }

    public void setL3_job_title(String l3_job_title) {
        if (l3_job_title != null) this.l3_job_title = l3_job_title;
    }

    public String getL3_job_description() {
        return l3_job_description;
    }

    public void setL3_job_description(String l3_job_description) {
        if (l3_job_description != null) this.l3_job_description = l3_job_description;
    }

    public void print() {
        System.out.println("name : " + name);
        System.out.println("l1_url : " + l1_url);
        System.out.println("l1_method : " + l1_method);
        System.out.println("l1_postdata : " + l1_postdata);
        System.out.println("  ");
        System.out.println("l2_content_type : " + l2_content_type);
        System.out.println("l2_prefix_for_nextpage_url : " + l2_prefix_for_nextpage_url);
        System.out.println("l2_schema_for_nextpage_url : " + l2_schema_for_nextpage_url);
        System.out.println("l2_nextpage_method : " + l2_nextpage_method);
        System.out.println("l2_nextpage_postdata : " + l2_nextpage_postdata);
        System.out.println("l2_nextpage_pattern : " + l2_nextpage_pattern);
        System.out.println("l2_nextpage_increment : " + l2_nextpage_increment);
        System.out.println("l2_last_page : " + l2_last_page);
        System.out.println("  ");
        System.out.println("l2_schema_for_jobs : " + l2_schema_for_jobs);
        System.out.println("l2_prefix_for_joburl : " + l2_prefix_for_joburl);
        System.out.println("l2_schema_for_joburl : " + l2_schema_for_joburl);
        System.out.println("l2_job_title : " + l2_job_title);
        System.out.println("l2_job_location : " + l2_job_location);
        System.out.println("l2_job_date : " + l2_job_date);
        System.out.println("l2_job_description : " + l2_job_description);
        System.out.println("  ");
        System.out.println("l3_job_title: " + l3_job_title);
        System.out.println("l3_job_description : " + l3_job_description);
    }
}

