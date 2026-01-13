package org.aldousdev.dockflowbackend.document_edit.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        InputStream inputStream = request.getInputStream();
        this.cachedBody = inputStream.readAllBytes();
    }

    @Override
    public ServletInputStream getInputStream(){
        return new CachedBodyServletInputStream(this.cachedBody);
    }

    @Override
    public BufferedReader getReader(){
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
        return new BufferedReader(new InputStreamReader(byteArrayInputStream, StandardCharsets.UTF_8));
    }

    public String getBody(){
        return new String(this.cachedBody, StandardCharsets.UTF_8);
    }

    private static class CachedBodyServletInputStream extends ServletInputStream{
        private final InputStream cachedBodyInputStream;

        public CachedBodyServletInputStream(byte[] cachedBody){
            this.cachedBodyInputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished(){
            try{
                return cachedBodyInputStream.available() == 0;
            }
            catch (IOException e){
                return false;
            }
        }

        @Override
        public boolean isReady(){
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener){
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() throws IOException{
            return cachedBodyInputStream.read();
        }
    }
}
