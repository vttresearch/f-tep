package com.cgi.eoss.ftep.api.security.dev;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j
public class SessionUserAttributeInjectorFilter extends GenericFilterBean {

    private final String usernameRequestAttribute;

    public SessionUserAttributeInjectorFilter(@Value("${ftep.api.security.username-request-attribute:REMOTE_USER}") String usernameRequestAttribute) {
        this.usernameRequestAttribute = usernameRequestAttribute;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;

        Object userAttribute = httpServletRequest.getSession().getAttribute(usernameRequestAttribute);
        if (userAttribute != null) {
            String username = userAttribute.toString();
            LOG.info("Found username '{}' in session: {}", username, httpServletRequest.getSession().getId());
            if (!Strings.isNullOrEmpty(username)) {
                httpServletRequest.setAttribute(usernameRequestAttribute, username);
            }
        } else {
            LOG.warn("No username found in session: {}", httpServletRequest.getSession().getId());
        }

        chain.doFilter(request, response);
    }

}