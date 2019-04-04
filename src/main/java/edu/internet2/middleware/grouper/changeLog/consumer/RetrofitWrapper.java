package edu.internet2.middleware.grouper.changeLog.consumer;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.CallAdapter;
import retrofit2.Converter;
import retrofit2.Retrofit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.Executor;

public class RetrofitWrapper {
    private final Retrofit retrofit;

    public RetrofitWrapper(Retrofit retrofit) {
        this.retrofit = retrofit;
    }

    public <T> T create(Class<T> service) {
        return retrofit.create(service);
    }

    public Call.Factory callFactory() {
        return retrofit.callFactory();
    }

    public HttpUrl baseUrl() {
        return retrofit.baseUrl();
    }

    public List<CallAdapter.Factory> callAdapterFactories() {
        return retrofit.callAdapterFactories();
    }

    public CallAdapter<?> callAdapter(Type returnType, Annotation[] annotations) {
        return retrofit.callAdapter(returnType, annotations);
    }

    public CallAdapter<?> nextCallAdapter(CallAdapter.Factory skipPast, Type returnType, Annotation[] annotations) {
        return retrofit.nextCallAdapter(skipPast, returnType, annotations);
    }

    public List<Converter.Factory> converterFactories() {
        return retrofit.converterFactories();
    }

    public <T> Converter<T, RequestBody> requestBodyConverter(Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations) {
        return retrofit.requestBodyConverter(type, parameterAnnotations, methodAnnotations);
    }

    public <T> Converter<T, RequestBody> nextRequestBodyConverter(Converter.Factory skipPast, Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations) {
        return retrofit.nextRequestBodyConverter(skipPast, type, parameterAnnotations, methodAnnotations);
    }

    public <T> Converter<ResponseBody, T> responseBodyConverter(Type type, Annotation[] annotations) {
        return retrofit.responseBodyConverter(type, annotations);
    }

    public <T> Converter<ResponseBody, T> nextResponseBodyConverter(Converter.Factory skipPast, Type type, Annotation[] annotations) {
        return retrofit.nextResponseBodyConverter(skipPast, type, annotations);
    }

    public <T> Converter<T, String> stringConverter(Type type, Annotation[] annotations) {
        return retrofit.stringConverter(type, annotations);
    }

    public Executor callbackExecutor() {
        return retrofit.callbackExecutor();
    }


}
