package org.basex.http.webdav;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Map.Entry;

import javax.servlet.http.*;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.servlet.*;
import org.basex.util.*;

import com.bradmcevoy.http.*;
import com.bradmcevoy.http.Response.ContentType;
import com.bradmcevoy.http.Cookie;

/**
 * Wrapper around {@link HttpServletRequest}, which in addition implements {@link Request}.<br/>
 * This implementation is the same as the implementation of {@code ServletRequest} found in
 * {@code milton-servlet}. Since this is one of the few classes which is needed from that library
 * the source is integrated into BaseX.
 *
 * @author Milton Development Team
 * @author BaseX Team 2005-15, BSD License
 * @author Rositsa Shadura
 * @author Dimitar Popov
 */
final class BXServletRequest extends AbstractRequest {
  /** HTTP servlet request. */
  private final HttpServletRequest req;
  /** Request method. */
  private final Method method;
  /** Request URL. */
  private final String url;
  /** Authentication. */
  private Auth auth;
  /** Content types map. */
  private static final Map<ContentType, String> CONTENT_TYPES = new EnumMap<>(ContentType.class);
  /** Type contents map. */
  private static final Map<String, ContentType> TYPE_CONTENTS = new HashMap<>();

  static {
    CONTENT_TYPES.put(ContentType.HTTP, Response.HTTP);
    CONTENT_TYPES.put(ContentType.MULTIPART, Response.MULTIPART);
    CONTENT_TYPES.put(ContentType.XML, Response.XML);
    for(final Entry<ContentType, String> entry : CONTENT_TYPES.entrySet())
      TYPE_CONTENTS.put(entry.getValue(), entry.getKey());
  }

  /**
   * Constructor.
   * @param req HTTP servlet request
   */
  BXServletRequest(final HttpServletRequest req) {
    this.req = req;
    method = Method.valueOf(req.getMethod());
    String u = req.getRequestURL().toString();
    // encoded characters: try to guess character set
    if(u.indexOf("%") != -1) {
      try {
        final String enc = req.getCharacterEncoding();
        final String ud = URLDecoder.decode(u, enc == null ? Strings.UTF8 : enc);
        u = ud.contains("\uFFFD") ? URLDecoder.decode(u, "ISO-8859-1") : ud;
      } catch(final Exception ignore) {
        Util.debug(ignore);
      }
    }
    url = u;
  }

  @Override
  public String getFromAddress() {
    return req.getRemoteHost();
  }

  @Override
  public String getRequestHeader(final Header header) {
    return req.getHeader(header.code);
  }

  @Override
  public Method getMethod() {
    return method;
  }

  @Override
  public String getAbsoluteUrl() {
    return url;
  }

  @Override
  public String getRemoteAddr() {
    return req.getRemoteAddr();
  }

  @Override
  public Auth getAuthorization() {
    if(auth == null) {
      final String enc = getRequestHeader(Header.AUTHORIZATION);
      if(enc != null && !enc.isEmpty()) auth = new Auth(enc);
    }
    return auth;
  }

  @Override
  public void setAuthorization(final Auth a) {
    auth = a;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return req.getInputStream();
  }

  @Override
  public Map<String, String> getHeaders() {
    final Map<String, String> map = new HashMap<>();
    final Enumeration<String> en = req.getHeaderNames();
    while(en.hasMoreElements()) {
      final String name = en.nextElement();
      final String val = req.getHeader(name);
      map.put(name, val);
    }
    return map;
  }

  @Override
  public Cookie getCookie(final String name) {
    for(final javax.servlet.http.Cookie c : req.getCookies()) {
      if(c.getName().equals(name)) return new BXServletCookie(c);
    }
    return null;
  }

  @Override
  public List<Cookie> getCookies() {
    final List<Cookie> list = new ArrayList<>();
    for(final javax.servlet.http.Cookie c : req.getCookies()) {
      list.add(new BXServletCookie(c));
    }
    return list;
  }

  @Override
  public void parseRequestParameters(final Map<String, String> params,
      final Map<String, com.bradmcevoy.http.FileItem> files) throws RequestParseException {
    try {
      if(isMultiPart()) {
        parseQueryString(params, req.getQueryString());
        final List<FileItem> items = new ServletFileUpload().parseRequest(req);
        for(final FileItem item : items) {
          if(item.isFormField())
            params.put(item.getFieldName(), item.getString());
          else
            files.put(item.getFieldName(), new FileItemWrapper(item));
        }
      } else {
        final Enumeration<String> en = req.getParameterNames();
        while(en.hasMoreElements()) {
          final String nm = en.nextElement();
          final String val = req.getParameter(nm);
          params.put(nm, val);
        }
      }
    } catch(final FileUploadException ex) {
      throw new RequestParseException("FileUploadException", ex);
    } catch(final Throwable ex) {
      throw new RequestParseException(ex.getMessage(), ex);
    }
  }

  /**
   * Parse the query string.
   * @param map parsed key-values will be stored here
   * @param qs query string
   */
  private static void parseQueryString(final Map<String, String> map, final String qs) {
    if(qs == null) return;
    for(final String nv : Strings.split(qs, '&')) {
      final String[] parts = Strings.split(nv, '=', 2);
      final String key = parts[0];
      String val = null;
      if(parts.length > 1) {
        try {
          val = URLDecoder.decode(parts[1], Strings.UTF8);
        } catch(final UnsupportedEncodingException ex) {
          throw new RuntimeException(ex);
        }
      }
      map.put(key, val);
    }
  }

  /**
   * Request content type.
   * @return the content type of the current request
   */
  private ContentType getRequestContentType() {
    final String s = req.getContentType();
    if(s == null) return null;
    if(s.contains(Response.MULTIPART)) return ContentType.MULTIPART;
    return TYPE_CONTENTS.get(s);
  }

  /**
   * Is the content type of the request a multi-part?
   * @return {@code true} if the request is multi-part
   */
  private boolean isMultiPart() {
    return getRequestContentType() == ContentType.MULTIPART;
  }
}

/**
 * Wrapper around {@link FileItem}, which in addition implements
 * {@link com.bradmcevoy.http.FileItem}.<br/>
 * This implementation is the same as the implementation of
 * {@code FileItemWrapper} found in {@code milton-servlet}. Since this is one of
 * the few classes which is needed from that library, the source is integrated
 * into BaseX.
 * @author Milton Development Team
 * @author BaseX Team 2005-15, BSD License
 * @author Rositsa Shadura
 * @author Dimitar Popov
 */
class FileItemWrapper implements com.bradmcevoy.http.FileItem {
  /** Wrapped file item. */
  private final FileItem file;
  /** File name. */
  private final String name;

  /**
   * Strip path information provided by IE.
   * @param string string
   * @return stripped string
   */
  private static String fixIEFileName(final String string) {
    final int pos = string.lastIndexOf('\\');
    return pos < 0 ? string : string.substring(pos + 1);
  }

  /**
   * Constructor.
   * @param file file item
   */
  FileItemWrapper(final FileItem file) {
    this.file = file;
    name = fixIEFileName(file.getName());
  }

  @Override
  public String getContentType() {
    return file.getContentType();
  }

  @Override
  public String getFieldName() {
    return file.getFieldName();
  }

  @Override
  public InputStream getInputStream() {
    try {
      return file.getInputStream();
    } catch(final IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public OutputStream getOutputStream() {
    try {
      return file.getOutputStream();
    } catch(final IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public long getSize() {
    return file.getSize();
  }
}
