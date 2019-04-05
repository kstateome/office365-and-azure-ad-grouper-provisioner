package edu.internet2.middleware.grouper.changeLog.consumer;

import edu.internet2.middleware.grouper.exception.MemberAddAlreadyExistsException;
import edu.internet2.middleware.grouper.exception.MemberDeleteAlreadyDeletedException;
import okhttp3.*;
import okio.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class RetroFitInvokerUTest {
    @Mock
    private Office365ApiClient office365ApiClient;
    @Mock
    private retrofit2.Call<String> call;

    retrofit2.Response response400Error;
    retrofit2.Response response401Error;
    retrofit2.Response response404Error;
    retrofit2.Response responseError;
    retrofit2.Response successfull;
    RetroFitInvoker<String> retroFitInvoker;
    @Mock
    ResponseBody response;

    Request request;

    ResponseBody responseBody;

    @Before
    public void setup() {
        responseBody = new ResponseBody() {
            @Override
            public MediaType contentType() {
                return null;
            }

            @Override
            public long contentLength() {
                return 0;
            }

            @Override
            public BufferedSource source() {
                return new BufferedSource() {
                    @Override
                    public Buffer buffer() {
                        return null;
                    }

                    @Override
                    public boolean exhausted() throws IOException {
                        return false;
                    }

                    @Override
                    public void require(long l) throws IOException {

                    }

                    @Override
                    public boolean request(long l) throws IOException {
                        return false;
                    }

                    @Override
                    public byte readByte() throws IOException {
                        return 0;
                    }

                    @Override
                    public short readShort() throws IOException {
                        return 0;
                    }

                    @Override
                    public short readShortLe() throws IOException {
                        return 0;
                    }

                    @Override
                    public int readInt() throws IOException {
                        return 0;
                    }

                    @Override
                    public int readIntLe() throws IOException {
                        return 0;
                    }

                    @Override
                    public long readLong() throws IOException {
                        return 0;
                    }

                    @Override
                    public long readLongLe() throws IOException {
                        return 0;
                    }

                    @Override
                    public long readDecimalLong() throws IOException {
                        return 0;
                    }

                    @Override
                    public long readHexadecimalUnsignedLong() throws IOException {
                        return 0;
                    }

                    @Override
                    public void skip(long l) throws IOException {

                    }

                    @Override
                    public ByteString readByteString() throws IOException {
                        return null;
                    }

                    @Override
                    public ByteString readByteString(long l) throws IOException {
                        return null;
                    }

                    @Override
                    public byte[] readByteArray() throws IOException {
                        return "Hello world".getBytes();
                    }

                    @Override
                    public byte[] readByteArray(long l) throws IOException {
                        return new byte[0];
                    }

                    @Override
                    public int read(byte[] bytes) throws IOException {
                        return 0;
                    }

                    @Override
                    public void readFully(byte[] bytes) throws IOException {

                    }

                    @Override
                    public int read(byte[] bytes, int i, int i1) throws IOException {
                        return 0;
                    }

                    @Override
                    public void readFully(Buffer buffer, long l) throws IOException {

                    }

                    @Override
                    public long readAll(Sink sink) throws IOException {
                        return 0;
                    }

                    @Override
                    public String readUtf8() throws IOException {
                        return null;
                    }

                    @Override
                    public String readUtf8(long l) throws IOException {
                        return null;
                    }

                    @Override
                    public String readUtf8Line() throws IOException {
                        return null;
                    }

                    @Override
                    public String readUtf8LineStrict() throws IOException {
                        return null;
                    }

                    @Override
                    public int readUtf8CodePoint() throws IOException {
                        return 0;
                    }

                    @Override
                    public String readString(Charset charset) throws IOException {
                        return null;
                    }

                    @Override
                    public String readString(long l, Charset charset) throws IOException {
                        return null;
                    }

                    @Override
                    public long indexOf(byte b) throws IOException {
                        return 0;
                    }

                    @Override
                    public long indexOf(byte b, long l) throws IOException {
                        return 0;
                    }

                    @Override
                    public long indexOf(ByteString byteString) throws IOException {
                        return 0;
                    }

                    @Override
                    public long indexOf(ByteString byteString, long l) throws IOException {
                        return 0;
                    }

                    @Override
                    public long indexOfElement(ByteString byteString) throws IOException {
                        return 0;
                    }

                    @Override
                    public long indexOfElement(ByteString byteString, long l) throws IOException {
                        return 0;
                    }

                    @Override
                    public InputStream inputStream() {
                        return null;
                    }

                    @Override
                    public long read(Buffer buffer, long l) throws IOException {
                        return 0;
                    }

                    @Override
                    public Timeout timeout() {
                        return null;
                    }

                    @Override
                    public void close() throws IOException {

                    }
                };
            }
        };
        MockitoAnnotations.initMocks(this);
        response400Error = retrofit2.Response.error(responseBody, (new Response.Builder()).code(400).message("dfdfOne or more added object references already exist").protocol(Protocol.HTTP_1_1).request((new okhttp3.Request.Builder()).url("http://localhost/").build()).build());
        response401Error = retrofit2.Response.error(401, responseBody);
        response404Error = retrofit2.Response.error(responseBody,(new Response.Builder()).code(404).message("dfdfRequest_ResourceNotFound").protocol(Protocol.HTTP_1_1).request((new okhttp3.Request.Builder()).url("http://localhost/").build()).build());

        responseError = retrofit2.Response.error(500, responseBody);
        successfull = retrofit2.Response.success(responseBody);
        Request.Builder builder = new Request.Builder();
        request = builder.url("http://localhost").build();
        when(call.clone()).thenReturn(call);
        when(call.request()).thenReturn(request);
        retroFitInvoker = new RetroFitInvoker<>(office365ApiClient, call);

    }

    @Test
    public void testProcessErrorResponse401() throws Exception {
        retroFitInvoker.processErrorResponse(response401Error);
        verify(call, times(1)).clone();
    }

    @Test
    public void testProcessErrorResponse400() throws Exception {
        try {
            retroFitInvoker.processErrorResponse(response400Error);
            fail("shouldn't get here");
        } catch (MemberAddAlreadyExistsException me) {

        } catch (Exception e) {
            fail("shouldn't have thrown this exception");
        }
        try {
            response400Error = retrofit2.Response.error(responseBody, (new Response.Builder()).code(400).message("Hello world").protocol(Protocol.HTTP_1_1).request((new okhttp3.Request.Builder()).url("http://localhost/").build()).build());
            retroFitInvoker.processErrorResponse(response400Error);
            fail("shouldn't get here");
        } catch (IOException io) {

        } catch (Exception e) {
            fail("shouldn't have thrown this exception: " + e.getMessage());
        }
    }
    @Test
    public void testProcessErrorResponse404() throws Exception {
        try {
            retroFitInvoker.processErrorResponse(response404Error);
            fail("shouldn't get here");
        } catch (MemberDeleteAlreadyDeletedException  me) {

        } catch (Exception e) {
            fail("shouldn't have thrown this exception");
        }
        try {
            response404Error = retrofit2.Response.error(responseBody, (new Response.Builder()).code(404).message("Hello world").protocol(Protocol.HTTP_1_1).request((new okhttp3.Request.Builder()).url("http://localhost/").build()).build());
            retroFitInvoker.processErrorResponse(response404Error);
            fail("shouldn't get here");
        } catch (IOException io) {

        } catch (Exception e) {
            fail("shouldn't have thrown this exception: " + e.getMessage());
        }
    }

    @Test
    public void testInvokeSuccess() throws Exception {
       when(call.execute()).thenReturn(successfull);
       assertNotNull(retroFitInvoker.invoke());

    }
    @Test
    public void testInvokeIllegalState() throws Exception {
        when(call.execute()).thenThrow(new IllegalStateException("badone"));
        try{
            retroFitInvoker.invoke();
            fail("should have thrown IllegalStateException");
        }catch (IllegalStateException e){

        }catch (Exception e){
            fail("should have thrown IllegalStateException" );
        }
    }
    @Test
    public void testInvokeBadDelete() throws Exception {

        when(call.execute()).thenThrow(new IllegalStateException("sdfAlready executedfdsd"));
        try{
            retroFitInvoker.invoke();
            fail("should have thrown MemberDeleteAlreadyDeletedException");
        }catch (MemberDeleteAlreadyDeletedException e){

        }catch (Exception e){
            fail("should have thrown MemberDeleteAlreadyDeletedException" );
        }
    }

}
