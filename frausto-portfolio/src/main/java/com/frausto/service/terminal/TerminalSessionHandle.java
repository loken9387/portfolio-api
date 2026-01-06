package com.frausto.service.terminal;

import com.github.dockerjava.api.async.ResultCallbackTemplate;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

public class TerminalSessionHandle implements Closeable {
    private final String execId;
    private final OutputStream stdinSink;
    private final ResultCallbackTemplate<?, ?> callback;

    public TerminalSessionHandle(String execId, OutputStream stdinSink, ResultCallbackTemplate<?, ?> callback) {
        this.execId = execId;
        this.stdinSink = stdinSink;
        this.callback = callback;
    }

    public String getExecId() {
        return execId;
    }

    public void forwardInput(byte[] payload) throws IOException {
        stdinSink.write(payload);
        stdinSink.flush();
    }

    public void close() throws IOException {
        try {
            callback.close();
        } catch (IOException ignored) {
            // ignore close errors for callback
        }
        stdinSink.close();
    }
}