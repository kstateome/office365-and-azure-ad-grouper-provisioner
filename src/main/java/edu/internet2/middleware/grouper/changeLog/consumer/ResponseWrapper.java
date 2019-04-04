package edu.internet2.middleware.grouper.changeLog.consumer;

import okhttp3.Headers;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class ResponseWrapper<T> {
    final retrofit2.Response<T> response;

    public ResponseWrapper(Response<T> response) {
        this.response = response;
    }

    public static <T1> Response<T1> success(T1 body) {
        return Response.success(body);
    }

    public static <T1> Response<T1> success(T1 body, Headers headers) {
        return Response.success(body, headers);
    }

    public static <T1> Response<T1> success(T1 body, okhttp3.Response rawResponse) {
        return Response.success(body, rawResponse);
    }

    public static <T1> Response<T1> error(int code, ResponseBody body) {
        return Response.error(code, body);
    }

    public static <T1> Response<T1> error(ResponseBody body, okhttp3.Response rawResponse) {
        return Response.error(body, rawResponse);
    }

    public okhttp3.Response raw() {
        return response.raw();
    }

    public int code() {
        return response.code();
    }

    public String message() {
        return response.message();
    }

    public Headers headers() {
        return response.headers();
    }

    public boolean isSuccessful() {
        return response.isSuccessful();
    }

    public T body() {
        return response.body();
    }

    public ResponseBody errorBody() {
        return response.errorBody();
    }
}
