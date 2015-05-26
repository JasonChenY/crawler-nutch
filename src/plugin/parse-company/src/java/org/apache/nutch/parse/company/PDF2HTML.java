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
package org.apache.nutch.parse.company;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import org.xml.sax.InputSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.NetworkParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.ExpandedTitleContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedInputStream;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.Icu4jEncodingDetector;
/**
 * Simple command line interface for Apache Tika.
 */
public class PDF2HTML {
    public static final Logger logger = LoggerFactory
            .getLogger("org.apache.nutch.parse.company");

    public static void main(String[] args) throws Exception {

        PDF2HTML cli = new PDF2HTML();

        cli.process(args);
    }

    private class OutputType {

        public void process(
                InputStream input, OutputStream output, Metadata metadata)
                throws Exception {
            Parser p = parser;

            ContentHandler handler = getContentHandler(output, metadata);
            p.parse(input, handler, metadata, context);

            if (handler instanceof NoDocumentMetHandler){
                NoDocumentMetHandler metHandler = (NoDocumentMetHandler)handler;
                if(!metHandler.metOutput()){
                    metHandler.endDocument();
                }
            }
        }

        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) throws Exception {
            throw new UnsupportedOperationException();
        }

    }

    private final OutputType XML = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) throws Exception {
            return getTransformerHandler(output, "xml", encoding, prettyPrint);
        }
    };

    private final OutputType HTML = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) throws Exception {
            return new ExpandedTitleContentHandler(getTransformerHandler(output, "html", encoding, prettyPrint));
        }
    };

    private final OutputType TEXT = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) throws Exception {
            return new BodyContentHandler(getOutputWriter(output, encoding));
        }
    };

    private final OutputType NO_OUTPUT = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) {
            return new DefaultHandler();
        }
    };

    private ParseContext context;

    private Detector detector;

    private Parser parser;

    private String configFilePath;

    private OutputType type = XML;

    private String encoding = "UTF-8";

    /**
     * Password for opening encrypted documents, or <code>null</code>.
     */
    private String password = System.getenv("TIKA_PASSWORD");


    private String profileName = null;

    private boolean prettyPrint;

    public PDF2HTML() throws Exception {
        context = new ParseContext();
        detector = new DefaultDetector();
        parser = new AutoDetectParser(detector);
        context.set(Parser.class, parser);
        context.set(PasswordProvider.class, new PasswordProvider() {
            public String getPassword(Metadata metadata) {
                return password;
            }
        });
    }
    public void process(InputStream input, OutputStream output) throws Exception {
        Metadata metadata = new Metadata();
        type.process(input, output, metadata);
    }

    public void process(String[] args) throws Exception {
        URL url;
        File file = new File(args[0]);
        if (file.isFile()) {
            url = file.toURI().toURL();
        } else {
            url = new URL(args[0]);
        }

        Tika tika = new Tika();
        System.out.println("Content-Type: " + tika.detect(args[0]));

        EncodingDetector encodingDetector=new Icu4jEncodingDetector();
        Charset encode=encodingDetector.detect(new BufferedInputStream(new FileInputStream(args[0])), new Metadata());
	System.out.println("Encoding: " + encode.name());

        String input_encoding = "UTF-8";
        if ( args.length > 1 ) input_encoding = args[1]; 
/*
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_ENCODING, input_encoding); 
        InputStream input = TikaInputStream.get(url, metadata);
*/
        Metadata metadata = new Metadata();
        InputSource ins = new InputSource(new FileInputStream(args[0]));
        ins.setEncoding(input_encoding);
        InputStream input = ins.getByteStream(); 

        try {
            //type.process(input, System.out, metadata);
            OutputStream output = new FileOutputStream ("/tmp/test.html");
            type.process(input, output, metadata);
            System.out.println("html generated to /tmp/test.html");
        } finally {
            input.close();
            System.out.flush();
        }
    }

    private ContentHandlerFactory getContentHandlerFactory(OutputType type) {
        BasicContentHandlerFactory.HANDLER_TYPE handlerType = BasicContentHandlerFactory.HANDLER_TYPE.IGNORE;
        if (type.equals(HTML)) {
            handlerType = BasicContentHandlerFactory.HANDLER_TYPE.HTML;
        } else if (type.equals(XML)) {
            handlerType = BasicContentHandlerFactory.HANDLER_TYPE.XML;
        } else if (type.equals(TEXT)) {
            handlerType = BasicContentHandlerFactory.HANDLER_TYPE.TEXT;
        }
        return new BasicContentHandlerFactory(handlerType, -1);
    }



    private void configure(String configFilePath) throws Exception {
        this.configFilePath = configFilePath;
        TikaConfig config = new TikaConfig(new File(configFilePath));
        parser = new AutoDetectParser(config);
        detector = config.getDetector();
        context.set(Parser.class, parser);
    }


    private String indent(int indent) {
        return "                     ".substring(0, indent);
    }




    /**
     * Returns a output writer with the given encoding.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-277">TIKA-277</a>
     * @param output output stream
     * @param encoding output encoding,
     *                 or <code>null</code> for the platform default
     * @return output writer
     * @throws UnsupportedEncodingException
     *         if the given encoding is not supported
     */
    private static Writer getOutputWriter(OutputStream output, String encoding)
            throws UnsupportedEncodingException {
        if (encoding != null) {
            return new OutputStreamWriter(output, encoding);
        } else if (System.getProperty("os.name")
                .toLowerCase(Locale.ROOT).startsWith("mac os x")) {
            // TIKA-324: Override the default encoding on Mac OS X
            return new OutputStreamWriter(output, IOUtils.UTF_8);
        } else {
            return new OutputStreamWriter(output, Charset.defaultCharset());
        }
    }

    /**
     * Returns a transformer handler that serializes incoming SAX events
     * to XHTML or HTML (depending the given method) using the given output
     * encoding.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-277">TIKA-277</a>
     * @param output output stream
     * @param method "xml" or "html"
     * @param encoding output encoding,
     *                 or <code>null</code> for the platform default
     * @return {@link System#out} transformer handler
     * @throws TransformerConfigurationException
     *         if the transformer can not be created
     */
    private static TransformerHandler getTransformerHandler(
            OutputStream output, String method, String encoding, boolean prettyPrint)
            throws TransformerConfigurationException {
        SAXTransformerFactory factory = (SAXTransformerFactory)
                SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, method);
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, prettyPrint ? "yes" : "no");
        if (encoding != null) {
            handler.getTransformer().setOutputProperty(
                    OutputKeys.ENCODING, encoding);
        }
        handler.setResult(new StreamResult(output));
        return handler;
    }



    private class NoDocumentMetHandler extends DefaultHandler {

        protected final Metadata metadata;

        protected PrintWriter writer;

        private boolean metOutput;

        public NoDocumentMetHandler(Metadata metadata, PrintWriter writer){
            this.metadata = metadata;
            this.writer = writer;
            this.metOutput = false;
        }

        @Override
        public void endDocument() {
            String[] names = metadata.names();
            Arrays.sort(names);
            outputMetadata(names);
            writer.flush();
            this.metOutput = true;
        }

        public void outputMetadata(String[] names) {
            for (String name : names) {
                for(String value : metadata.getValues(name)) {
                    writer.println(name + ": " + value);
                }
            }
        }

        public boolean metOutput(){
            return this.metOutput;
        }

    }
}
