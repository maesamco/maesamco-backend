package com.maesamco.judge.global.security.hmac;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.util.StreamUtils;

/**
 * ⚠️ P0 리뷰로 발견 — ContentCachingRequestWrapper는 getInputStream()을 다시 호출해도
 * 캐시된 내용을 "재생"해주지 않는다(Javadoc: "only caches content as it is being read
 * but otherwise does not cause content to be read"). 즉 이 필터가 서명 검증용으로
 * 바디를 한 번 다 읽으면, 그 뒤 컨트롤러가 @RequestBody로 읽으려 할 때 항상 빈 바디를
 * 받는다(실제 MockHttpServletRequest 재현 테스트로 확인됨).
 *
 * 이 클래스는 바디를 byte[]로 직접 저장해두고, getInputStream()/getReader()를
 * 호출할 때마다 그 배열로 매번 새 스트림을 만들어 반환한다 — 몇 번을 읽어도 항상
 * 처음부터 다시 읽을 수 있다.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    public byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
    }

    private static class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream buffer;

        CachedBodyServletInputStream(byte[] contents) {
            this.buffer = new ByteArrayInputStream(contents);
        }

        @Override
        public int read() {
            return buffer.read();
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("비동기 읽기는 지원하지 않음");
        }
    }
}