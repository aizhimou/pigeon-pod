package top.asimov.pigeon.handler;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

/**
 * A custom resource handler that serves media files efficiently.
 * It leverages Spring's built-in support for Range requests, Etags, and zero-copy transfers.
 */
@Component
public class MediaFileResourceHandler extends ResourceHttpRequestHandler {

  @Override
  protected Resource getResource(HttpServletRequest request) {
    return (Resource) request.getAttribute("requestedResource");
  }

  @Override
  public List<Resource> getLocations() {
    return Collections.emptyList();
  }
}
