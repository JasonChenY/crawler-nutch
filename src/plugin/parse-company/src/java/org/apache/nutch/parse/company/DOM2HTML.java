package org.apache.nutch.parse.company;

import org.w3c.dom.Node;
import java.util.Properties;

import javax.xml.transform.OutputKeys;  
import javax.xml.transform.Result;  
import javax.xml.transform.Transformer;  
import javax.xml.transform.TransformerConfigurationException;  
import javax.xml.transform.TransformerException;  
import javax.xml.transform.TransformerFactory;  
import javax.xml.transform.dom.DOMSource;  
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

public class DOM2HTML {
    private static Transformer transformer;
    public static String toString(Node node) {
        if ( transformer == null ) {
            transformer = newTransformer();
        }
        if (transformer != null) {
            try {
                StringWriter sw = new StringWriter();
                transformer.transform(new DOMSource(node),new StreamResult(sw));
                return sw.toString();
            } catch (TransformerException te) {
                throw new RuntimeException(te.getMessage());
            }
        }
        return "";
    }

    public static Transformer newTransformer() {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            Properties properties = transformer.getOutputProperties();
            properties.setProperty(OutputKeys.ENCODING, "gb2312");
            properties.setProperty(OutputKeys.METHOD, "html");
            properties.setProperty(OutputKeys.VERSION, "1.0");
            properties.setProperty(OutputKeys.INDENT, "yes");
            properties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            properties.setProperty(OutputKeys.STANDALONE, "yes");
            transformer.setOutputProperties(properties); 
            return transformer;
        } catch (TransformerConfigurationException tce) {
            throw new RuntimeException(tce.getMessage());
        }
    }
}

