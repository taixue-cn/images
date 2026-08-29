package com.andavin.images.command;

import java.net.URI;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UrlImageLoaderTest {

    private static final String PREFIX = "http://172.21.1.46:8080/v1/internal/temporary-map/";

    @Test
    public void acceptsOnlyChildrenOfExactConfiguredOriginAndPath() throws Exception {
        assertTrue(UrlImageLoader.isTrusted(
                new URI(PREFIX + "123e4567-e89b-12d3-a456-426614174000"),
                Collections.singletonList(PREFIX)));
        assertFalse(UrlImageLoader.isTrusted(
                new URI("http://172.21.1.46:8080/v1/internal/temporary-maps/token"),
                Collections.singletonList(PREFIX)));
        assertFalse(UrlImageLoader.isTrusted(
                new URI("http://172.21.1.46:8081/v1/internal/temporary-map/token"),
                Collections.singletonList(PREFIX)));
        assertFalse(UrlImageLoader.isTrusted(
                new URI("http://172.21.1.46.evil.test:8080/v1/internal/temporary-map/token"),
                Collections.singletonList(PREFIX)));
    }

    @Test
    public void rejectsQueriesAndThePrefixItself() throws Exception {
        assertFalse(UrlImageLoader.isTrusted(
                new URI(PREFIX), Collections.singletonList(PREFIX)));
        assertFalse(UrlImageLoader.isTrusted(
                new URI(PREFIX + "token?next=/admin"), Collections.singletonList(PREFIX)));
    }
}
