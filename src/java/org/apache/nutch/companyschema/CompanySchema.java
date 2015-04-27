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
    private String url;
    private String method;
    private String data;
    private String page_list_schema;
    private String page_list_pattern;
    private String page_list_increment;
    private String page_list_last;
    private String job_list_schema;
    private String job_link;
    private String job_title;
    private String job_location;
    private String job_date;

    /* for get info from Specific Job link page */
    private String job_abstract;
    private String job_description;

    public CompanySchema(String n) { name = n; method = "GET"; }
    public String name() { return name; }
    public void name(String n) { name = n; }

    public String url() { return url; }
    public void url(String u) { url = u; }

    public String method() { return method; }
    public void method(String m) {
        if ( m != null && m.equalsIgnoreCase("POST") )
            method = "POST";
        else
            method = "GET";
    }

    public String data() { return data; }
    public void data(String d) { data = d; }

    public String page_list_schema() { return page_list_schema; }
    public void page_list_schema(String schema) { page_list_schema = schema; }

    public String page_list_pattern() { return page_list_pattern; }
    public void page_list_pattern(String pattern) { page_list_pattern = pattern; }

    public String page_list_increment() { return page_list_increment; }
    public void page_list_increment(String inc) { page_list_increment = inc; }

    public String page_list_last() { return page_list_last; }
    public void page_list_last(String last) { page_list_last = last; }

    public String job_list_schema() { return job_list_schema; }
    public void job_list_schema(String schema) { job_list_schema = schema; }

    public String job_link() { return job_link; }
    public void job_link(String link) { job_link = link; }

    public String job_title() { return job_title; }
    public void job_title(String title) { job_title = title; }

    public String job_location() { return job_location; }
    public void job_location(String loc) { job_location = loc; }

    public String job_date() { return job_date; }
    public void job_date(String date) { job_date = date; }

    public String job_abstract() { return job_abstract; }
    public void job_abstract(String date) { job_abstract = date; }

    public String job_description() { return job_description; }
    public void job_description(String date) { job_description = date; }

    public void print(){
        System.out.println("name:"+name);
        System.out.println("url"+url);
        System.out.println("method"+method);
        System.out.println("page_list_schema:" + page_list_schema);
        System.out.println("page_list_pattern:" + page_list_pattern);
        System.out.println("page_list_incr:" + page_list_increment);
        System.out.println("page_list_last:" + page_list_last);
        System.out.println("job_list_schema:" + job_list_schema);
        System.out.println("job_link:" + job_link);
        System.out.println("job_title:" + job_title);
        System.out.println("job_location:" + job_location);
        System.out.println("job_date:" + job_date);
    }
}

