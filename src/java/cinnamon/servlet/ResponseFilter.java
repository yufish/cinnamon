package cinnamon.servlet;


import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

/**
 * Based on: http://stackoverflow.com/questions/8933054/how-to-log-response-content-from-a-java-web-server
 */
public class ResponseFilter implements Filter {

    Logger log = LoggerFactory.getLogger(ResponseFilter.class);
    public static ThreadLocal<HttpServletResponseCopier> localResponseCopier = new ThreadLocal<HttpServletResponseCopier>();


    @Override
    public void init(FilterConfig config) throws ServletException {
        // NOOP.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (response.getCharacterEncoding() == null) {
            response.setCharacterEncoding("UTF-8"); // Or whatever default. UTF-8 is good for World Domination.
        }

        byte[] copy;

        if (response.getContentType() != null && response.getContentType().equals("application/octet-stream")) {
            log.debug("Not using responseCopier on binary output.");
            chain.doFilter(request, response);
            copy = "filtered: binary response".getBytes("UTF-8");
            copyHeaders(request, response, copy);
        }
        else {
            log.debug("non-binary response: using responseCopier");
            HttpServletResponseCopier responseCopier = new HttpServletResponseCopier((HttpServletResponse) response);
            localResponseCopier.set(responseCopier);
            chain.doFilter(request, responseCopier);
            responseCopier.flushBuffer();
            copy = responseCopier.getCopy();
            copyHeaders(request, responseCopier, copy);
        }

    }

    private void copyHeaders(ServletRequest request, ServletResponse response, byte[] copy) throws IOException {
        HttpServletResponse httpServletResponse = ((HttpServletResponse) response);
        for (String url : httpServletResponse.getHeaders("microservice")) {
            HttpClient httpClient = HttpClientBuilder.create().build();
            RequestBuilder requestCopy = RequestBuilder.create("POST");
            requestCopy.setUri(url);
            HttpServletRequest httpServletRequest = ((HttpServletRequest) request);
            Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (headerName.equals("microservice")) {
                    continue;
                }
                requestCopy.setHeader(headerName, httpServletRequest.getHeader(headerName));
            }
            for (Map.Entry<String, String[]> entry : httpServletRequest.getParameterMap().entrySet()) {
                for (String paramVal : entry.getValue()) {
                    requestCopy.addParameter(entry.getKey(), paramVal);
                }
            }

            requestCopy.addParameter("cinnamonResponse", new String(copy, "UTF-8"));
            HttpResponse httpResponse = httpClient.execute(requestCopy.build());
            log.debug("Microservice response status:" + httpResponse.getStatusLine());
        }
    }

    @Override
    public void destroy() {
        // NOOP.
    }
}
