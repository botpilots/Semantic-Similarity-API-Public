package org.acme.semsim.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.NewCookie;
import org.acme.semsim.model.SessionData;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class SimilarityProcessingServiceTest {

	@Inject
	SimilarityProcessingService similarityProcessingService;

	@Inject
	SessionService sessionService;

	private static final String XML_SAMPLE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
            \t<title>Sample Document for Similarity Testing</title>
            \t<content>
            \t\t<paragraph>
                        This is a test paragraph with very similar content.\s
                        This is a test paragraph with very similar content.\s
                        This paragraph demonstrates similarity testing.
            \t\t</paragraph>
            \t</content>
            </document>""";

    @Test
	public void testStartAsyncProcessing() throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
		// Test that processing starts and returns a session ID
		NewCookie cookie = similarityProcessingService.startAsyncProcessing(XML_SAMPLE, "paragraph");

		assertNotNull(cookie.getValue(), "Session ID should not be null");
		assertFalse(cookie.getValue().isEmpty(), "Session ID should not be empty");

		// Verify that a session was created
		SessionData sessionData = sessionService.getSession(cookie.getValue());
		assertNotNull(sessionData, "Session data should exist");
	}

}