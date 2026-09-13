/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 */
package brut.xml;

import brut.common.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Iterator;

public final class XmlUtils {
    private static final String TAG = XmlUtils.class.getName();

    public static final String XML_PROLOG = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";
    public static final String XML_PREFIX = "xml";
    public static final String XML_URI = "http://www.w3.org/XML/1998/namespace";
    public static final String XMLNS_PREFIX = "xmlns";
    public static final String XMLNS_URI = "http://www.w3.org/2000/xmlns/";

    private static final String FEATURE_DISALLOW_DOCTYPE_DECL =
        "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES =
        "http://xml.org/sax/features/external-general-entities";
    private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES =
        "http://xml.org/sax/features/external-parameter-entities";
    private static final String FEATURE_LOAD_EXTERNAL_DTD =
        "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private XmlUtils() {
    }

    private static DocumentBuilder newDocumentBuilder(boolean nsAware)
            throws SAXException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(nsAware);
        // Keep Apktool secure but tolerant across Android's JAXP implementations.
        setFeatureIfSupported(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeatureIfSupported(factory, FEATURE_DISALLOW_DOCTYPE_DECL, true);
        setFeatureIfSupported(factory, FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        setFeatureIfSupported(factory, FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
        setFeatureIfSupported(factory, FEATURE_LOAD_EXTERNAL_DTD, false);
        try {
            factory.setXIncludeAware(false);
        } catch (UnsupportedOperationException ignored) {
            Log.d(TAG, "XML parser does not support XInclude toggle; continuing safely.");
        }
        factory.setExpandEntityReferences(false);
        setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder();
    }

    private static void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean value)
            throws ParserConfigurationException {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException ex) {
            // Android platform XML providers vary by API/device. Secure-processing
            // failures are fatal; optional XXE hardening features are best-effort.
            if (XMLConstants.FEATURE_SECURE_PROCESSING.equals(feature)) {
                throw ex;
            }
            Log.w(TAG, "XML parser feature unsupported on this Android runtime: " + feature);
        } catch (AbstractMethodError | NoSuchMethodError | UnsupportedOperationException ex) {
            Log.w(TAG, "XML parser feature unavailable on this Android runtime: " + feature);
        }
    }

    private static void setAttributeIfSupported(DocumentBuilderFactory factory, String attr, String value) {
        try {
            factory.setAttribute(attr, value);
        } catch (IllegalArgumentException | UnsupportedOperationException | AbstractMethodError | NoSuchMethodError ignored) {
            Log.d(TAG, "XML parser attribute unavailable on this Android runtime: " + attr);
        }
    }

    private static TransformerFactory newTransformerFactory() throws TransformerException {
        TransformerFactory factory = TransformerFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (TransformerException ex) {
            throw ex;
        } catch (RuntimeException ignored) {
            Log.w(TAG, "Transformer secure-processing feature unavailable on this Android runtime.");
        }
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
            Log.d(TAG, "Transformer external-access attributes unavailable on this Android runtime.");
        }
        return factory;
    }

    public static Document newDocument() throws SAXException, ParserConfigurationException {
        return newDocument(false);
    }

    public static Document newDocument(boolean nsAware) throws SAXException, ParserConfigurationException {
        return newDocumentBuilder(nsAware).newDocument();
    }

    public static Document parseDocument(String xml) throws IOException, SAXException, ParserConfigurationException {
        return parseDocument(xml, false);
    }

    public static Document parseDocument(String xml, boolean nsAware)
            throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilder builder = newDocumentBuilder(nsAware);
        StringReader reader = new StringReader(xml);
        return builder.parse(new InputSource(reader));
    }

    public static Document loadDocument(File file) throws IOException, SAXException, ParserConfigurationException {
        return loadDocument(file, false);
    }

    public static Document loadDocument(File file, boolean nsAware)
            throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilder builder = newDocumentBuilder(nsAware);
        try (InputStream in = Files.newInputStream(file.toPath())) {
            return builder.parse(new InputSource(in));
        }
    }

    public static void saveDocument(Document doc, File file)
            throws IOException, SAXException, ParserConfigurationException, TransformerException {
        Transformer transformer = newTransformerFactory().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        byte[] xmlDecl = XML_PROLOG.getBytes(StandardCharsets.US_ASCII);
        byte[] newLine = System.lineSeparator().getBytes(StandardCharsets.US_ASCII);

        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write(xmlDecl);
            out.write(newLine);
            transformer.transform(new DOMSource(doc), new StreamResult(out));
            out.write(newLine);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T evaluateXPath(Document doc, String expression, Class<T> returnType)
            throws XPathExpressionException {
        QName type;
        if (returnType == Node.class) {
            type = XPathConstants.NODE;
        } else if (returnType == NodeList.class) {
            type = XPathConstants.NODESET;
        } else if (returnType == String.class) {
            type = XPathConstants.STRING;
        } else if (returnType == Double.class) {
            type = XPathConstants.NUMBER;
        } else if (returnType == Boolean.class) {
            type = XPathConstants.BOOLEAN;
        } else {
            throw new IllegalArgumentException("Unexpected return type: " + returnType.getName());
        }

        XPath xPath = XPathFactory.newInstance().newXPath();
        xPath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return doc.lookupNamespaceURI(prefix);
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return doc.lookupPrefix(namespaceURI);
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI) {
                String prefix = getPrefix(namespaceURI);
                return prefix != null ? Collections.singleton(prefix).iterator() : Collections.emptyIterator();
            }
        });

        return (T) xPath.evaluate(expression, doc, type);
    }
}
